/*
 * client.c - Cliente de terminal remota
 *
 * Se conecta al servidor, envia la contrasena de autenticacion y luego
 * multiplexa stdin del usuario y el socket usando select(), de forma que
 * lo que el usuario tipea viaja al servidor en tiempo real, y lo que el
 * servidor responde (prompt, salida de comandos) se imprime inmediatamente
 * en la terminal local.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define BUF_SIZE 4096

int main(int argc, char *argv[]) {
    if (argc < 3) {
        fprintf(stderr, "Uso: %s <host> <puerto> [password]\n", argv[0]);
        return 1;
    }

    const char *host = argv[1];
    int port = atoi(argv[2]);
    const char *password = (argc >= 4) ? argv[3] : NULL;

    int sock_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (sock_fd < 0) { perror("socket"); return 1; }

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons((uint16_t)port);

    if (inet_pton(AF_INET, host, &addr.sin_addr) <= 0) {
        fprintf(stderr, "Direccion invalida: %s\n", host);
        return 1;
    }

    if (connect(sock_fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        perror("connect");
        return 1;
    }

    fprintf(stderr, "[cliente] conectado a %s:%d\n", host, port);

    /* Si se paso password por argumento (util para pruebas automatizadas /
     * Google Colab, donde no hay una terminal interactiva real), lo mandamos
     * apenas conectamos. Si no, se asume que el usuario lo va a tipear cuando
     * vea el prompt "PASSWORD:" que manda el servidor. */
    if (password != NULL) {
        char line[256];
        snprintf(line, sizeof(line), "%s\n", password);
        if (write(sock_fd, line, strlen(line)) < 0) { perror("write password"); }
    }

    char buf[BUF_SIZE];
    int stdin_open = 1; /* se apaga cuando stdin llega a EOF (ej: pipe agotado, Ctrl+D) */

    for (;;) {
        fd_set rfds;
        FD_ZERO(&rfds);
        if (stdin_open) FD_SET(STDIN_FILENO, &rfds);
        FD_SET(sock_fd, &rfds);
        int maxfd = sock_fd;
        if (stdin_open && STDIN_FILENO > maxfd) maxfd = STDIN_FILENO;
        maxfd += 1;

        int ready = select(maxfd, &rfds, NULL, NULL, NULL);
        if (ready < 0) {
            if (errno == EINTR) continue;
            perror("select");
            break;
        }

        if (FD_ISSET(sock_fd, &rfds)) {
            ssize_t n = read(sock_fd, buf, sizeof(buf));
            if (n <= 0) {
                fprintf(stderr, "\n[cliente] conexion cerrada por el servidor.\n");
                break; /* el servidor cerro: ahi si terminamos */
            }
            if (write(STDOUT_FILENO, buf, (size_t)n) < 0) break;
        }

        if (stdin_open && FD_ISSET(STDIN_FILENO, &rfds)) {
            ssize_t n = read(STDIN_FILENO, buf, sizeof(buf));
            if (n <= 0) {
                /* EOF local (Ctrl+D o pipe agotado): dejamos de escuchar stdin
                 * pero seguimos leyendo la salida pendiente del servidor
                 * hasta que el sea el que cierre la conexion. */
                stdin_open = 0;
                shutdown(sock_fd, SHUT_WR);
                continue;
            }
            if (write(sock_fd, buf, (size_t)n) < 0) break;
        }
    }

    close(sock_fd);
    return 0;
}
