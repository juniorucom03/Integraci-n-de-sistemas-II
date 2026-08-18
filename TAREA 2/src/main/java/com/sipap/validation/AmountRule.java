package com.sipap.validation;

import java.math.BigDecimal;

public final class AmountRule {

    public static final BigDecimal MAXIMO_PERMITIDO = new BigDecimal("10000000");
    public static final String MENSAJE_RECHAZO = "El monto supera máximo permitido";

    private AmountRule() {
    }

    public static boolean superaMaximo(BigDecimal monto) {
        if (monto == null) {
            throw new IllegalArgumentException("El monto no puede ser nulo para esta validación");
        }
        return monto.compareTo(MAXIMO_PERMITIDO) > 0;
    }
}
