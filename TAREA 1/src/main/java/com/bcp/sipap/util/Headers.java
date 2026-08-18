package com.bcp.sipap.util;

/**
 * Nombres centralizados de headers y properties de Exchange usados en las
 * rutas Camel, para evitar strings mágicos repetidos.
 */
public final class Headers {

    private Headers() {
    }

    /** Header: identificador de transacción (Correlation Identifier), presente en todo el flujo. */
    public static final String ID_TRANSACCION = "idTransaccion";

    /** Header: motivo de rechazo de negocio o técnico, usado por el canal de rechazo / dead letter. */
    public static final String MOTIVO_RECHAZO = "motivoRechazo";

    /** Header: nombre del productor que originó el mensaje (para trazabilidad/auditoría). */
    public static final String PRODUCTOR_ORIGEN = "productorOrigen";

    /** Exchange property: mapa de tags TLV de nivel superior ya parseados. */
    public static final String PROP_QR_TAGS = "qrTags";

    public static final String PROP_VALIDACION_OK = "validacionOk";
}
