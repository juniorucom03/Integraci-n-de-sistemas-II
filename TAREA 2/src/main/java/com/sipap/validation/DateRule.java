package com.sipap.validation;

import java.time.LocalDate;
import java.time.ZoneId;

public final class DateRule {

    public static final ZoneId ZONA_HORARIA = ZoneId.of("America/Asuncion");

    private DateRule() {
    }

    public static boolean esFechaActual(LocalDate fechaTransaccion) {
        if (fechaTransaccion == null) {
            return false;
        }
        LocalDate hoy = LocalDate.now(ZONA_HORARIA);
        return fechaTransaccion.isEqual(hoy);
    }
}
