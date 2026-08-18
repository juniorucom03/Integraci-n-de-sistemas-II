package com.sipap.validation;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DateRuleTest {

    @Test
    void fechaActualEsValida() {
        LocalDate hoy = LocalDate.now(DateRule.ZONA_HORARIA);
        assertTrue(DateRule.esFechaActual(hoy));
    }

    @Test
    void fechaAnteriorEsInvalida() {
        LocalDate ayer = LocalDate.now(DateRule.ZONA_HORARIA).minusDays(1);
        assertFalse(DateRule.esFechaActual(ayer));
    }

    @Test
    void fechaPosteriorEsInvalida() {
        LocalDate manana = LocalDate.now(DateRule.ZONA_HORARIA).plusDays(1);
        assertFalse(DateRule.esFechaActual(manana));
    }

    @Test
    void fechaNulaEsInvalida() {
        assertFalse(DateRule.esFechaActual(null));
    }
}
