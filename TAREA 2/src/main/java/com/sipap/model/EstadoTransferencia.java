package com.sipap.model;

/**
 * Estados posibles a lo largo del flujo. Documentado también en el README.
 */
public enum EstadoTransferencia {
    ACEPTADA_PARA_PROCESAMIENTO,
    RECHAZADA,
    RECHAZADA_MONTO,
    RECHAZADA_FECHA,
    DUPLICADA,
    PROCESADA,
    ERROR_BANCO
}
