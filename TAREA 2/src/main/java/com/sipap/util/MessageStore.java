package com.sipap.util;

import com.sipap.model.CanonicalTransfer;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementación simple del patrón EIP "Message Store": conserva una copia
 * de cada transferencia procesada (con su resultado) para trazabilidad,
 * auditoría o reenvío futuro. En memoria para esta práctica; en un
 * escenario real se reemplazaría por una tabla en base de datos.
 */
@Component
public class MessageStore {

    public static class Entrada {
        public final CanonicalTransfer transferencia;
        public final String estado;
        public final Instant timestamp;

        public Entrada(CanonicalTransfer transferencia, String estado) {
            this.transferencia = transferencia;
            this.estado = estado;
            this.timestamp = Instant.now();
        }
    }

    private final Map<String, Entrada> almacen = new ConcurrentHashMap<>();

    public void guardar(String idTransaccion, CanonicalTransfer transferencia, String estado) {
        almacen.put(idTransaccion, new Entrada(transferencia, estado));
    }

    public Entrada buscar(String idTransaccion) {
        return almacen.get(idTransaccion);
    }

    public Map<String, Entrada> todo() {
        return almacen;
    }
}
