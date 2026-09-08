# Estado del proyecto — TP1 Emulador de Terminal Cliente/Servidor

## 1. Archivos incluidos

* `server.c` — servidor TCP multiproceso. Utiliza `fork()` para atender clientes simultáneamente, `pipe()` para comunicar el proceso servidor con la shell remota, `dup2()` para redireccionar la entrada y salida de la shell, `execlp()` para ejecutar `/bin/bash`, y señales `SIGCHLD`, `SIGINT` y `SIGTERM` para administrar los procesos y el apagado.
* `client.c` — cliente interactivo. Utiliza `select()` para multiplexar la entrada estándar y el socket TCP.
* `Makefile` — permite compilar ambos programas mediante `make` y eliminar los binarios mediante `make clean`.
* `estado.md` — documento de seguimiento y estado actual del proyecto.

---

## 2. Compilación

El proyecto fue compilado en WSL2 Ubuntu utilizando:

```bash
make
```

El `Makefile` utiliza:

```bash
gcc -Wall -Wextra -O2 -std=gnu11
```

La compilación de `server.c` y `client.c` finalizó correctamente.

Se observó un warning de `make` relacionado con la diferencia de timestamps entre Windows y WSL2:

```text
make: Warning: File 'Makefile' has modification time ... in the future
make: warning: Clock skew detected.
```

Este warning corresponde al sistema de archivos compartido entre Windows y WSL2 y no impidió la compilación ni la ejecución del proyecto.

---

## 3. Ejecución

El servidor puede iniciarse mediante:

```bash
./server 2222 unix2025
```

También puede ejecutarse en segundo plano:

```bash
./server 2222 unix2025 &
```

El cliente se conecta mediante:

```bash
./client 127.0.0.1 2222 unix2025
```

El puerto utilizado durante las pruebas fue `2222`.

La contraseña utilizada para las pruebas fue:

```text
unix2025
```

---

## 4. Arquitectura del sistema

El sistema implementa una arquitectura cliente-servidor utilizando sockets TCP.

El servidor:

1. Crea un socket TCP.
2. Realiza `bind()` sobre el puerto configurado.
3. Ejecuta `listen()`.
4. Espera conexiones mediante `accept()`.
5. Por cada cliente aceptado realiza un `fork()`.
6. El proceso hijo encargado del cliente crea dos pipes.
7. El hijo crea un segundo proceso mediante `fork()` para ejecutar la shell.
8. La shell remota se ejecuta mediante:

```c
execlp("/bin/bash", "/bin/bash", "-i", (char *)NULL);
```

9. El proceso encargado de la conexión transmite los datos entre el socket y los pipes.
10. El cliente utiliza `select()` para manejar simultáneamente stdin y el socket.

La estructura general es:

```text
                    SERVIDOR
                       |
                    socket()
                       |
                    listen()
                       |
                    accept()
                       |
                    fork()
                       |
              +--------+--------+
              |                 |
           Cliente 1         Cliente 2
              |                 |
            fork()            fork()
              |                 |
            Bash              Bash
```

Cada conexión posee su propio proceso de atención y su propia shell remota.

---

## 5. Comunicación mediante pipes

Para cada cliente se crean dos pipes:

```c
int pipe_in[2];
int pipe_out[2];

pipe(pipe_in);
pipe(pipe_out);
```

### Pipe de entrada

```text
Cliente
   |
 socket
   |
 C1
   |
pipe_in[1]  --->  pipe_in[0]
                      |
                    stdin
                      |
                    Bash
```

`pipe_in[1]` es utilizado por el proceso C1 para escribir los datos provenientes del cliente.

`pipe_in[0]` es utilizado por Bash como entrada estándar.

### Pipe de salida

```text
Bash
  |
stdout/stderr
  |
pipe_out[1] ---> pipe_out[0]
                       |
                       C1
                       |
                     socket
                       |
                     Cliente
```

`pipe_out[1]` es utilizado por Bash para escribir su salida.

`pipe_out[0]` es leído por C1 y posteriormente enviado al cliente mediante el socket.

---

## 6. Mapeo de descriptores antes de `execlp()`

Antes de ejecutar Bash, el proceso hijo posee los siguientes descriptores relevantes:

```text
pipe_in[0]   -> lectura de entrada para Bash
pipe_in[1]   -> escritura de entrada
pipe_out[0]  -> lectura de salida
pipe_out[1]  -> escritura de salida para Bash
client_fd    -> socket TCP
```

En el proceso que ejecutará Bash se realiza:

```c
dup2(pipe_in[0], STDIN_FILENO);
dup2(pipe_out[1], STDOUT_FILENO);
dup2(pipe_out[1], STDERR_FILENO);
```

Por lo tanto:

```text
STDIN_FILENO  (0) -> pipe_in[0]
STDOUT_FILENO (1) -> pipe_out[1]
STDERR_FILENO (2) -> pipe_out[1]
```

Después de realizar los `dup2()`, los descriptores originales que ya no son necesarios se cierran.

También se cierra `client_fd` dentro del proceso Bash, porque Bash no necesita acceder directamente al socket TCP.

---

## 7. Mapeo de descriptores después de `execlp()`

Después de:

```c
execlp("/bin/bash", "/bin/bash", "-i", (char *)NULL);
```

el proceso continúa utilizando los mismos descriptores abiertos, porque `exec` reemplaza la imagen del proceso pero conserva los descriptores que no fueron marcados con `FD_CLOEXEC`.

El resultado es:

```text
Bash stdin  (FD 0) -> pipe_in[0]
Bash stdout (FD 1) -> pipe_out[1]
Bash stderr (FD 2) -> pipe_out[1]
```

Por lo tanto, cualquier comando ejecutado por Bash recibe su entrada desde el pipe y envía su salida estándar y errores al pipe de salida.

Esto permite transportar la interacción de la shell mediante el socket TCP.

---

## 8. Ejecución interactiva de Bash

La shell se ejecuta mediante:

```c
execlp("/bin/bash", "/bin/bash", "-i", (char *)NULL);
```

El parámetro `-i` hace que Bash funcione como shell interactiva.

Además, el proceso ejecuta:

```c
setsid();
```

antes de realizar los `dup2()`.

Esto crea una nueva sesión para evitar que la shell remota quede asociada al terminal del servidor.

Durante las pruebas Bash muestra:

```text
bash: cannot set terminal process group (-1): Inappropriate ioctl for device
bash: no job control in this shell
```

Estos mensajes son esperables porque la shell está siendo ejecutada mediante pipes y sockets y no dispone de un TTY real.

No impiden la ejecución de comandos.

---

## 9. Multiplexación mediante `select()`

El proceso servidor encargado del cliente utiliza `select()` para esperar simultáneamente:

* datos provenientes del cliente;
* datos producidos por la shell remota.

Conceptualmente:

```text
              +----------------+
              |      C1        |
              +----------------+
                 /          \
                /            \
        client_fd          read_fd
            |                 |
          socket           pipe_out
            |                 |
            +-------+---------+
                    |
                 select()
```

Cuando existen datos provenientes del cliente, C1 los escribe en `pipe_in`.

Cuando existen datos provenientes de Bash, C1 los lee desde `pipe_out` y los envía mediante el socket.

Esto permite comunicación bidireccional.

---

## 10. Autenticación

El servidor solicita una contraseña al cliente antes de iniciar la shell.

La contraseña utilizada durante las pruebas fue:

```text
unix2025
```

### Prueba con contraseña incorrecta

Se ejecutó:

```bash
./client 127.0.0.1 2222 incorrecta
```

Resultado:

```text
[cliente] conectado a 127.0.0.1:2222
PASSWORD: Acceso denegado.

[cliente] conexion cerrada por el servidor.
```

Resultado: **APROBADO**.

### Prueba con contraseña correcta

Se ejecutó:

```bash
./client 127.0.0.1 2222 unix2025
```

Resultado:

```text
[cliente] conectado a 127.0.0.1:2222
PASSWORD: Autenticado. Iniciando shell remota...
```

Resultado: **APROBADO**.

---

## 11. Pruebas de ejecución de comandos

Se ejecutaron correctamente los siguientes comandos:

```bash
whoami
```

Resultado:

```text
gorito
```

También:

```bash
pwd
```

Resultado:

```text
/mnt/c/Users/Junior/Documents/INTEGRACION DE SISTEMAS II/TP 1
```

También:

```bash
echo "HOLA DESDE EL SERVIDOR"
```

Resultado:

```text
HOLA DESDE EL SERVIDOR
```

Y:

```bash
ls
```

Resultado observado:

```text
ESTADO.md
Makefile
client
client.c
server
server.c
```

Resultado: **APROBADO**.

---

## 12. Prueba de concurrencia

Se probaron múltiples clientes conectados simultáneamente.

En una prueba se mantuvo un cliente ejecutando:

```bash
sleep 30
```

Mientras tanto, un segundo cliente se conectó y ejecutó:

```bash
echo "SOY CLIENTE 2"
```

Resultado:

```text
SOY CLIENTE 2
```

También ejecutó:

```bash
whoami
```

Resultado:

```text
gorito
```

El primer cliente continuó ejecutando `sleep 30` sin bloquear al segundo.

Resultado: **APROBADO**.

---

## 13. Prueba de cinco clientes simultáneos

Se realizaron pruebas con cinco clientes conectados simultáneamente.

La observación mediante:

```bash
ps -eo pid,ppid,stat,%cpu,%mem,cmd | grep -E 'server|client|bash' | grep -v grep
```

mostró un servidor principal y cinco procesos hijos asociados a las conexiones.

Se observaron estructuras como:

```text
./server 2222 unix2025
    |
    +-- ./server 2222 unix2025
    |      |
    |      +-- /bin/bash -i
    |
    +-- ./server 2222 unix2025
    |      |
    |      +-- /bin/bash -i
    |
    +-- ./server 2222 unix2025
    |      |
    |      +-- /bin/bash -i
    |
    +-- ./server 2222 unix2025
    |      |
    |      +-- /bin/bash -i
    |
    +-- ./server 2222 unix2025
           |
           +-- /bin/bash -i
```

Durante la medición, los procesos del servidor, clientes y shells mostraron aproximadamente:

```text
%CPU = 0.0
```

en el instante de la observación.

Esto demuestra que el modelo multiproceso permite atender varias conexiones de manera simultánea.

Resultado: **APROBADO**.

---

## 14. Desconexión abrupta del cliente

Se conectó un cliente y se obtuvo su PID mediante:

```bash
ps aux | grep './client 127.0.0.1 2222'
```

Posteriormente se finalizó abruptamente utilizando:

```bash
kill -9 <PID>
```

Después se verificó:

```bash
ps -p <PID> -o pid,stat,cmd
```

El proceso ya no existía.

También se comprobó que el servidor continuaba funcionando:

```text
./server 2222 unix2025
```

Posteriormente se conectó un nuevo cliente y se ejecutó:

```bash
echo "SERVIDOR SIGUE VIVO"
```

Resultado:

```text
SERVIDOR SIGUE VIVO
```

Resultado: **APROBADO**.

El servidor no quedó bloqueado después de una desconexión abrupta.

---

## 15. Manejo de procesos zombie

Se comprobó la existencia de procesos zombie mediante:

```bash
ps -eo pid,ppid,stat,cmd | grep -E ' Z|<defunct>' | grep -v grep
```

No se obtuvo ninguna línea de salida.

La prueba se realizó tanto después de cerrar clientes como durante la prueba de múltiples conexiones.

El servidor utiliza `SIGCHLD` y `waitpid()` con `WNOHANG` para recolectar los procesos hijos terminados.

Resultado: **APROBADO**.

No se observaron procesos `Z` ni `<defunct>` durante las pruebas realizadas.

---

## 16. Apagado ordenado mediante SIGINT

El servidor puede recibir:

```text
Ctrl+C
```

lo que genera `SIGINT`.

Se probó el apagado sin clientes:

```text
[servidor] señal de apagado recibida. Cerrando 0 conexion(es) activa(s)...
[servidor] apagado completo.
```

Resultado: **APROBADO**.

---

## 17. Apagado con un cliente activo

Se conectó un cliente y se ejecutó:

```bash
sleep 30
```

Mientras el comando estaba ejecutándose se presionó `Ctrl+C` en el servidor.

Resultado observado:

```text
[servidor] señal de apagado recibida. Cerrando 1 conexion(es) activa(s)...
[servidor] apagado completo.
```

En el cliente:

```text
[servidor] apagando: cerrando esta sesion.

[cliente] conexion cerrada por el servidor.
```

El servidor terminó inmediatamente y no quedó bloqueado esperando a que terminara `sleep 30`.

Resultado: **APROBADO**.

---

## 18. Apagado con cinco clientes activos

Se realizó una prueba con cinco conexiones simultáneas.

El servidor mostró:

```text
[servidor] señal de apagado recibida. Cerrando 5 conexion(es) activa(s)...
[servidor] apagado completo.
```

Posteriormente se ejecutó:

```bash
ps -eo pid,ppid,stat,cmd | grep -E 'server|client|bash' | grep -v grep
```

No quedaron procesos `server`, `client` ni `/bin/bash -i` pertenecientes al TP.

Resultado: **APROBADO**.

---

## 19. Manejo de SIGTERM

El servidor utiliza `SIGTERM` para solicitar el cierre ordenado de los procesos encargados de las conexiones activas.

El proceso encargado de cada cliente posee un manejador de `SIGTERM` que permite interrumpir su espera y comenzar el cierre.

Posteriormente se utiliza una rutina de terminación de la shell remota.

Primero se intenta:

```c
kill(-shell_pid, SIGTERM);
```

Si el proceso no termina dentro del tiempo establecido, se utiliza:

```c
kill(-shell_pid, SIGKILL);
```

Finalmente se utiliza `waitpid()` para garantizar la recolección del proceso.

Esto permite evitar procesos remanentes durante el apagado.

---

## 20. Señal SIGCHLD

El proceso servidor instala un manejador para `SIGCHLD`.

El manejador utiliza:

```c
waitpid(-1, &status, WNOHANG)
```

para recolectar los procesos hijos terminados sin bloquear el servidor.

Esto permite que el servidor continúe atendiendo nuevas conexiones mientras administra los procesos hijos finalizados.

La ausencia de procesos zombie fue comprobada mediante `ps`.

---

## 21. Resultado general de las pruebas

| Prueba                                 | Resultado |
| -------------------------------------- | --------- |
| Compilación                            | APROBADO  |
| Makefile                               | APROBADO  |
| Conexión TCP                           | APROBADO  |
| Autenticación incorrecta               | APROBADO  |
| Autenticación correcta                 | APROBADO  |
| Ejecución de `whoami`                  | APROBADO  |
| Ejecución de `pwd`                     | APROBADO  |
| Ejecución de `echo`                    | APROBADO  |
| Ejecución de `ls`                      | APROBADO  |
| Comunicación bidireccional             | APROBADO  |
| Dos clientes simultáneos               | APROBADO  |
| Cinco clientes simultáneos             | APROBADO  |
| Cliente ocupado con `sleep 30`         | APROBADO  |
| Desconexión abrupta mediante `kill -9` | APROBADO  |
| Servidor continúa después de `kill -9` | APROBADO  |
| Ausencia de procesos zombie            | APROBADO  |
| SIGINT sin clientes                    | APROBADO  |
| SIGINT con cliente activo              | APROBADO  |
| SIGINT durante `sleep 30`              | APROBADO  |
| SIGINT con cinco clientes              | APROBADO  |
| Limpieza de procesos al apagar         | APROBADO  |

---

## 22. Estado actual

La implementación funcional principal del TP1 se encuentra terminada y fue probada en WSL2 Ubuntu.

Las pruebas realizadas confirman:

* comunicación TCP cliente-servidor;
* autenticación básica;
* ejecución remota de comandos;
* utilización de `fork()`;
* utilización de `pipe()`;
* redireccionamiento mediante `dup2()`;
* ejecución de `/bin/bash` mediante `execlp()`;
* multiplexación mediante `select()`;
* concurrencia de múltiples clientes;
* recuperación ante desconexiones abruptas;
* manejo de `SIGCHLD`;
* ausencia de procesos zombie;
* apagado ordenado mediante `SIGINT` y `SIGTERM`;
* terminación correcta de shells remotas;
* limpieza de procesos durante el apagado.

No quedan bugs funcionales conocidos relacionados con las pruebas realizadas.

---

## 23. Comandos básicos para compilar y ejecutar

### Compilar

```bash
make
```

### Limpiar

```bash
make clean
```

### Ejecutar servidor

```bash
./server 2222 unix2025
```

### Ejecutar servidor en segundo plano

```bash
./server 2222 unix2025 &
```

### Ejecutar cliente

```bash
./client 127.0.0.1 2222 unix2025
```

### Ver procesos del TP

```bash
ps -eo pid,ppid,stat,%cpu,%mem,cmd | grep -E 'server|client|bash' | grep -v grep
```

### Buscar procesos zombie

```bash
ps -eo pid,ppid,stat,cmd | grep -E ' Z|<defunct>' | grep -v grep
```

---

## 24. Nota para la entrega

El proyecto fue desarrollado y probado en un entorno Linux mediante WSL2 sobre Windows.

Para la entrega final se debe incluir este documento junto con:

```text
server.c
client.c
Makefile
estado.md
```

También se recomienda incluir capturas de las pruebas de:

* múltiples clientes;
* ausencia de zombies;
* ejecución de comandos;
* desconexión abrupta;
* apagado ordenado con conexiones activas;
* observación de procesos mediante `ps` o `htop`.
