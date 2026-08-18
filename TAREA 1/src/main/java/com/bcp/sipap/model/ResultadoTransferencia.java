package com.bcp.sipap.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resultado común producido tanto por los consumidores bancarios (transferencias
 * procesadas) como por el canal de rechazo (transferencias inválidas).
 */
public class ResultadoTransferencia {

    public static final String ESTADO_PROCESADA = "PROCESADA";
    public static final String ESTADO_RECHAZADA = "RECHAZADA";

    @JsonProperty("id_transaccion")
    private String idTransaccion;

    @JsonProperty("estado")
    private String estado;

    @JsonProperty("mensaje")
    private String mensaje;

    public ResultadoTransferencia() {
    }

    public ResultadoTransferencia(String idTransaccion, String estado, String mensaje) {
        this.idTransaccion = idTransaccion;
        this.estado = estado;
        this.mensaje = mensaje;
    }

    public static ResultadoTransferencia procesada(String idTransaccion, String mensaje) {
        return new ResultadoTransferencia(idTransaccion, ESTADO_PROCESADA, mensaje);
    }

    public static ResultadoTransferencia rechazada(String idTransaccion, String motivo) {
        return new ResultadoTransferencia(idTransaccion, ESTADO_RECHAZADA, motivo);
    }

    public String getIdTransaccion() {
        return idTransaccion;
    }

    public void setIdTransaccion(String idTransaccion) {
        this.idTransaccion = idTransaccion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    @Override
    public String toString() {
        return "ResultadoTransferencia{" +
                "idTransaccion='" + idTransaccion + '\'' +
                ", estado='" + estado + '\'' +
                ", mensaje='" + mensaje + '\'' +
                '}';
    }
}
