package com.bcp.sipap.parser;

/**
 * Error técnico/estructural al interpretar la cadena TLV (longitudes que no
 * calzan, tags incompletos, etc.). Se maneja mediante un Dead Letter Channel
 * en el mediador, separado de los rechazos de negocio (ver ValidationException).
 */
public class QrParseException extends RuntimeException {

    public QrParseException(String message) {
        super(message);
    }

    public QrParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
