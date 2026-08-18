package com.bcp.sipap.validation;

/**
 * Rechazo de negocio: la cadena QR se pudo interpretar (es TLV válido) pero
 * no cumple alguna regla de validación (moneda, monto, checksum, campos
 * obligatorios, enrutamiento reconocido, etc.).
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String motivo) {
        super(motivo);
    }
}
