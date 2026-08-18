package com.sipap.validation;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class IdempotencyStore {

    private final Map<String, Instant> vistos = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofHours(24);

    public synchronized boolean registrarSiEsNuevo(String idTransaccion) {
        purgarExpirados();
        if (vistos.containsKey(idTransaccion)) {
            return false;
        }
        vistos.put(idTransaccion, Instant.now());
        return true;
    }

    public boolean fueVisto(String idTransaccion) {
        purgarExpirados();
        return vistos.containsKey(idTransaccion);
    }

    private void purgarExpirados() {
        Instant limite = Instant.now().minus(ttl);
        vistos.entrySet().removeIf(e -> e.getValue().isBefore(limite));
    }

    public void limpiar() {
        vistos.clear();
    }
}
