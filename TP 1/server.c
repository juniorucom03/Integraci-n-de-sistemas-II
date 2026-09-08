/*
 * server.c - Emulador de terminal remota (estilo SSH simplificado)
 *
 * Trabajo Practico 1 - Programacion Unix-Linux II
 *
 * Flujo por cada cliente aceptado:
 *   1. El proceso padre hace fork() -> proceso "manejador" (C1)
 *   2. C1 valida una contrasena simple enviada por el cliente
 *   3. C1 crea dos pipes: pipe_in (cliente -> shell) y pipe_out (shell -> cliente)
 *   4. C1 hace un segundo fork() -> proceso "shell" (C2)
 *   5. C2 redirige fd 0,1,2 con dup2() sobre los pipes y hace execlp("/bin/bash", ...)
 *   6. C1 queda como puente: lee del socket y escribe al pipe_in; lee del pipe_out
 *      y escribe al socket, usando select() para multiplexar ambos descriptores.
 *   7. Cuando el cliente se desconecta o la shell termina, C1 limpia y termina.
 *   8. El proceso padre (servidor) reapea a C1 vía SIGCHLD para evitar zombies.
 */

#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <signal.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <sys/select.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define BACKLOG        16
#define BUF_SIZE       4096
#define MAX_CLIENTS    64
#define AUTH_LINE_MAX  128
#define DEFAULT_PASS   "unix2025"

/* ---- Estado global compartido entre el bucle principal y los handlers ---- */
static volatile sig_atomic_t g_shutdown_requested = 0;
static int g_listen_fd = -1;

/* Tabla de PIDs de los procesos "manejador" (C1) activos, para poder
 * notificarlos en un apagado ordenado (graceful shutdown). */
static pid_t g_active_children[MAX_CLIENTS];
static int   g_active_count = 0;

static void track_child(pid_t pid) {
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (g_active_children[i] == 0) {
            g_active_children[i] = pid;
            g_active_count++;
            return;
        }
    }
    /* Tabla llena: no es crítico, solo no podremos notificarlo en shutdown */
}

static void untrack_child(pid_t pid) {
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (g_active_children[i] == pid) {
            g_active_children[i] = 0;
            g_active_count--;
            return;
        }
    }
}

/* ---- Manejo de SIGCHLD: evita procesos zombies ---- */
static void sigchld_handler(int signo) {
    (void)signo;
    int saved_errno = errno;
    pid_t pid;
    int status;
    /* WNOHANG: reapear todos los hijos que ya terminaron, sin bloquear */
    while ((pid = waitpid(-1, &status, WNOHANG)) > 0) {
        untrack_child(pid);
    }
    errno = saved_errno;
}

/* ---- Manejo de SIGINT / SIGTERM: apagado ordenado ---- */
static void shutdown_handler(int signo) {
    (void)signo;
    g_shutdown_requested = 1;
}

static void install_signal_handlers(void) {
    struct sigaction sa_chld;
    memset(&sa_chld, 0, sizeof(sa_chld));
    sa_chld.sa_handler = sigchld_handler;
    sigemptyset(&sa_chld.sa_mask);
    sa_chld.sa_flags = SA_RESTART; /* reintentar syscalls interrumpidas por SIGCHLD */
    sigaction(SIGCHLD, &sa_chld, NULL);

    struct sigaction sa_term;
    memset(&sa_term, 0, sizeof(sa_term));
    sa_term.sa_handler = shutdown_handler;
    sigemptyset(&sa_term.sa_mask);
    sa_term.sa_flags = 0; /* SIN SA_RESTART: queremos que accept() devuelva EINTR */
    sigaction(SIGINT, &sa_term, NULL);
    sigaction(SIGTERM, &sa_term, NULL);

    /* Ignorar SIGPIPE: si el cliente cierra abruptamente, un write() sobre el
     * socket cerrado debe devolver error EPIPE en vez de matar el proceso. */
    signal(SIGPIPE, SIG_IGN);
}

/* Lee una linea terminada en '\n' desde un socket, de forma simple y acotada.
 * Se usa solo para el intercambio de autenticacion inicial. */
static ssize_t read_line(int fd, char *buf, size_t maxlen) {
    size_t i = 0;
    while (i < maxlen - 1) {
        char c;
        ssize_t n = read(fd, &c, 1);
        if (n <= 0) return n; /* error o cierre de conexion */
        if (c == '\n') break;
        if (c != '\r') buf[i++] = c;
    }
    buf[i] = '\0';
    return (ssize_t)i;
}

/* Bandera local del proceso C1: se activa si el servidor padre le manda
 * SIGTERM durante un apagado ordenado (graceful shutdown), incluso si en
 * ese momento C1 esta bloqueado en select() esperando datos de la shell. */
static volatile sig_atomic_t g_child_shutdown_requested = 0;

static void child_shutdown_handler(int signo) {
    (void)signo;
    g_child_shutdown_requested = 1;
}




static void terminate_shell_group(pid_t shell_pid) {
    int status;

    /*
     * shell_pid también es el PGID porque C2 creó
     * una nueva sesión mediante setsid().
     *
     * El signo negativo indica que enviamos la señal
     * a todo el grupo de procesos.
     */
    if (kill(-shell_pid, SIGTERM) < 0) {
        if (errno != ESRCH) {
            perror("kill SIGTERM process group");
        }
    }

    /*
     * Esperamos como máximo unos 2 segundos para que
     * Bash termine de forma ordenada.
     */
    for (int i = 0; i < 20; i++) {
        pid_t result = waitpid(shell_pid, &status, WNOHANG);

        if (result == shell_pid) {
            return;
        }

        if (result < 0) {
            if (errno == ECHILD) {
                return;
            }

            if (errno != EINTR) {
                perror("waitpid");
                return;
            }
        }

        usleep(100000); /* 100 ms */
    }

    /*
     * Si Bash no terminó después de 2 segundos,
     * lo terminamos forzosamente junto con su grupo.
     */
    if (kill(-shell_pid, SIGKILL) < 0) {
        if (errno != ESRCH) {
            perror("kill SIGKILL process group");
        }
    }

    /*
     * Recogemos definitivamente al proceso hijo.
     */
    while (waitpid(shell_pid, &status, 0) < 0) {
        if (errno == EINTR) {
            continue;
        }

        if (errno == ECHILD) {
            break;
        }

        perror("waitpid");
        break;
    }
}
/*
 * handle_client: corre dentro del proceso hijo C1 (post fork() del accept).
 * No retorna: siempre termina con exit().
 */

static void handle_client(int client_fd, const char *password) {
    /*
     * C1 recibe SIGTERM del servidor padre.
     * Este manejador permite que C1 interrumpa el select()
     * y cierre ordenadamente la shell hija.
     */
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = child_shutdown_handler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = 0; /* debe interrumpir el select() bloqueado */
    sigaction(SIGTERM, &sa, NULL);

    /*
     * C1 no debe heredar el manejador SIGCHLD del proceso servidor.
     * C1 necesita poder esperar directamente a su shell hija (C2)
     * mediante waitpid().
     */
    struct sigaction sa_chld_child;
    memset(&sa_chld_child, 0, sizeof(sa_chld_child));
    sa_chld_child.sa_handler = SIG_DFL;
    sigemptyset(&sa_chld_child.sa_mask);
    sa_chld_child.sa_flags = 0;
    sigaction(SIGCHLD, &sa_chld_child, NULL);

    char line[AUTH_LINE_MAX];

    const char *prompt = "PASSWORD: ";
    if (write(client_fd, prompt, strlen(prompt)) < 0) { close(client_fd); exit(1); }
    if (read_line(client_fd, line, sizeof(line)) <= 0) {
        close(client_fd);
        exit(0);
    }
    if (strcmp(line, password) != 0) {
        const char *deny = "Acceso denegado.\n";
        if (write(client_fd, deny, strlen(deny)) < 0) { /* cliente ya se fue, no importa */ }
        close(client_fd);
        exit(0);
    }
    const char *ok = "Autenticado. Iniciando shell remota...\n";
    if (write(client_fd, ok, strlen(ok)) < 0) { /* cliente ya se fue, no importa */ }

    /* --- Creacion de las tuberias IPC --- */
    int pipe_in[2];   /* pipe_in[0]=lectura (shell stdin) pipe_in[1]=escritura (C1 escribe) */
    int pipe_out[2];  /* pipe_out[0]=lectura (C1 lee) pipe_out[1]=escritura (shell stdout/stderr) */

    if (pipe(pipe_in) < 0 || pipe(pipe_out) < 0) {
        perror("pipe");
        close(client_fd);
        exit(1);
    }

    pid_t shell_pid = fork();
    if (shell_pid < 0) {
        perror("fork (shell)");
        close(client_fd);
        exit(1);
    }

    if (shell_pid == 0) {
        /*
        * Crear una nueva sesión para que Bash no quede
        * asociado a la terminal del servidor.
        */
        if (setsid() < 0) {
            perror("setsid");
            _exit(1);
        }

        dup2(pipe_in[0], STDIN_FILENO);
        dup2(pipe_out[1], STDOUT_FILENO);
        dup2(pipe_out[1], STDERR_FILENO);

        close(pipe_in[0]);
        close(pipe_in[1]);
        close(pipe_out[0]);
        close(pipe_out[1]);
        close(client_fd);

        setenv("PS1", "remote-shell:\\w\\$ ", 1);

        execlp("/bin/bash", "/bin/bash", "-i", (char *)NULL);

        perror("execlp");
        _exit(127);
    }

    /* ---- Seguimos en C1: cerramos los extremos que le corresponden a C2 ---- */
    close(pipe_in[0]);
    close(pipe_out[1]);

    int write_fd = pipe_in[1];   /* C1 escribe aca lo que llega del socket */
    int read_fd  = pipe_out[0];  /* C1 lee aca lo que produce la shell */

    /* ---- Bucle de reenvio bidireccional usando select() ---- */
    char buf[BUF_SIZE];
    int client_open = 1; /* se apaga si el cliente cierra su lado de escritura */

    for (;;) {
        fd_set rfds;
        FD_ZERO(&rfds);
        if (client_open) FD_SET(client_fd, &rfds);
        FD_SET(read_fd, &rfds);
        int maxfd = read_fd;
        if (client_open && client_fd > maxfd) maxfd = client_fd;
        maxfd += 1;

        int ready = select(maxfd, &rfds, NULL, NULL, NULL);
        if (ready < 0) {
            if (errno == EINTR) {
                if (g_child_shutdown_requested) break; /* apagado global: cortar */
                continue;
            }
            break;
        }

        if (client_open && FD_ISSET(client_fd, &rfds)) {
            ssize_t n = read(client_fd, buf, sizeof(buf));
            if (n <= 0) {
                /* El cliente cerro su lado de escritura (o se desconecto).
                 * Dejamos de escuchar el socket pero seguimos drenando la
                 * salida pendiente de la shell hasta que ella misma termine. */
                client_open = 0;
                close(write_fd); /* EOF para la shell: su stdin se cierra */
                write_fd = -1;   /* marcar como ya cerrado */
                continue;
            }
            if (write(write_fd, buf, (size_t)n) < 0) break;
        }

        if (FD_ISSET(read_fd, &rfds)) {
            ssize_t n = read(read_fd, buf, sizeof(buf));
            if (n <= 0) break; /* la shell termino (ej: el usuario hizo 'exit') */
            if (write(client_fd, buf, (size_t)n) < 0) break;
        }
    }

    if (g_child_shutdown_requested) {
        const char *msg = "\n[servidor] apagando: cerrando esta sesion.\n";
        if (write(client_fd, msg, strlen(msg)) < 0) { /* no importa si ya no hay quien lea */ }
    }

    terminate_shell_group(shell_pid);

    close(client_fd);
    if (write_fd >= 0) close(write_fd);
    close(read_fd);

    exit(0);
}

int main(int argc, char *argv[]) {
    int port = 2222;
    const char *password = DEFAULT_PASS;

    if (argc >= 2) port = atoi(argv[1]);
    if (argc >= 3) password = argv[2];

    install_signal_handlers();
    memset(g_active_children, 0, sizeof(g_active_children));

    g_listen_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (g_listen_fd < 0) { perror("socket"); exit(1); }

    int opt = 1;
    setsockopt(g_listen_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons((uint16_t)port);

    if (bind(g_listen_fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        perror("bind"); exit(1);
    }
    if (listen(g_listen_fd, BACKLOG) < 0) {
        perror("listen"); exit(1);
    }

    printf("[servidor] escuchando en puerto %d (PID %d)\n", port, getpid());
    fflush(stdout);

    while (!g_shutdown_requested) {
        struct sockaddr_in cli_addr;
        socklen_t cli_len = sizeof(cli_addr);
        int client_fd = accept(g_listen_fd, (struct sockaddr *)&cli_addr, &cli_len);

        if (client_fd < 0) {
            if (errno == EINTR) {
                /* interrumpido por SIGINT/SIGTERM o SIGCHLD -> revisar flag y continuar */
                continue;
            }
            perror("accept");
            continue;
        }

        printf("[servidor] cliente conectado: %s:%d\n",
               inet_ntoa(cli_addr.sin_addr), ntohs(cli_addr.sin_port));
        fflush(stdout);

        pid_t pid = fork();
        if (pid < 0) {
            perror("fork (cliente)");
            close(client_fd);
            continue;
        }

        if (pid == 0) {
            /* Proceso C1: no necesita el socket de escucha */
            close(g_listen_fd);
            handle_client(client_fd, password);
            /* no retorna */
        }

        /* Proceso padre (servidor): no necesita el fd del cliente */
        close(client_fd);
        track_child(pid);
    }

    /* ---- Apagado ordenado (graceful shutdown) ---- */
    printf("\n[servidor] señal de apagado recibida. Cerrando %d conexion(es) activa(s)...\n",
           g_active_count);
    fflush(stdout);

    close(g_listen_fd);

    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (g_active_children[i] != 0) {
            kill(g_active_children[i], SIGTERM);
        }
    }
    /* Esperar a que todos terminen (bloqueante, ya no hay mas trabajo que hacer) */
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (g_active_children[i] != 0) {
            int status;
            waitpid(g_active_children[i], &status, 0);
        }
    }

    printf("[servidor] apagado completo.\n");
    return 0;
}
