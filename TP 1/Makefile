CC      = gcc
CFLAGS  = -Wall -Wextra -O2 -std=gnu11
TARGETS = server client

.PHONY: all clean run-server

all: $(TARGETS)

server: server.c
	$(CC) $(CFLAGS) -o server server.c

client: client.c
	$(CC) $(CFLAGS) -o client client.c

# Levanta el servidor en background, en el puerto 2222 por defecto.
# Uso: make run-server PORT=2222 PASS=unix2025
PORT ?= 2222
PASS ?= unix2025
run-server: server
	./server $(PORT) $(PASS) &

clean:
	rm -f $(TARGETS) server.log
