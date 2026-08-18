package com.bcp.sipap.util;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Genera identificadores únicos de transacción (TX000001, TX000002, ...)
 * usados como Correlation Identifier a lo largo de todo el flujo: desde el
 * productor hasta el resultado final (procesado o rechazado).
 */
public final class TransaccionIdGenerator {

    private static final AtomicLong CONTADOR = new AtomicLong(0);

    private TransaccionIdGenerator() {
    }

    public static String siguiente() {
        long n = CONTADOR.incrementAndGet();
        return String.format("TX%06d", n);
    }
}
