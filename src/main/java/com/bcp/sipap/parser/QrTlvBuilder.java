package com.bcp.sipap.parser;

/**
 * Utilidad para construir cadenas TLV válidas (o intencionalmente inválidas)
 * a partir de valores concretos. Se usa desde los productores de prueba
 * (timers) y desde los generadores de ejemplos, evitando el conteo manual
 * de longitudes.
 */
public final class QrTlvBuilder {

    private final StringBuilder sb = new StringBuilder();

    private QrTlvBuilder() {
    }

    public static QrTlvBuilder nuevo() {
        return new QrTlvBuilder();
    }

    /** Agrega un campo TAG+LONGITUD+VALOR, calculando la longitud real del valor. */
    public QrTlvBuilder campo(String tag, String value) {
        sb.append(tag)
          .append(String.format("%02d", value.length()))
          .append(value);
        return this;
    }

    /** Agrega un campo forzando una longitud declarada distinta a la real (para casos inválidos). */
    public QrTlvBuilder campoConLongitudForzada(String tag, int longitudDeclarada, String value) {
        sb.append(tag)
          .append(String.format("%02d", longitudDeclarada))
          .append(value);
        return this;
    }

    /** Agrega texto crudo, sin procesar (para construir casos deliberadamente corruptos). */
    public QrTlvBuilder crudo(String texto) {
        sb.append(texto);
        return this;
    }

    public String build() {
        return sb.toString();
    }

    /**
     * Construye el bloque anidado del tag 32 (Merchant Account Information)
     * a partir de sus tres sub-tags obligatorios.
     */
    public static String merchantAccountInformation(String gui, String codigoEntidad, String numeroCuenta) {
        String subTags = QrTlvBuilder.nuevo()
                .campo("00", gui)
                .campo("01", codigoEntidad)
                .campo("02", numeroCuenta)
                .build();
        return QrTlvBuilder.nuevo().campo("32", subTags).build();
    }
}
