package com.sipap.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdempotencyStoreTest {

    @Test
    void primerMensajeEsNuevo() {
        IdempotencyStore store = new IdempotencyStore();
        assertTrue(store.registrarSiEsNuevo("TX-1"));
    }

    @Test
    void mensajeDuplicadoNoSeReprocesa() {
        IdempotencyStore store = new IdempotencyStore();
        assertTrue(store.registrarSiEsNuevo("TX-2"));
        assertFalse(store.registrarSiEsNuevo("TX-2"), "El segundo intento con el mismo id debe detectarse como duplicado");
    }

    @Test
    void idsDistintosNoInterfierenEntreSi() {
        IdempotencyStore store = new IdempotencyStore();
        assertTrue(store.registrarSiEsNuevo("TX-A"));
        assertTrue(store.registrarSiEsNuevo("TX-B"));
    }
}
