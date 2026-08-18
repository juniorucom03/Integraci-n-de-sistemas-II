package com.sipap.validation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AmountRuleTest {

    @Test
    void montoIgualAlMaximoNoSeRechaza() {
        assertFalse(AmountRule.superaMaximo(new BigDecimal("10000000")));
    }

    @Test
    void montoMenorAlMaximoNoSeRechaza() {
        assertFalse(AmountRule.superaMaximo(new BigDecimal("9999999.99")));
    }

    @Test
    void montoMayorAlMaximoSeRechaza() {
        assertTrue(AmountRule.superaMaximo(new BigDecimal("10000000.01")));
    }

    @Test
    void montoNuloLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> AmountRule.superaMaximo(null));
    }
}
