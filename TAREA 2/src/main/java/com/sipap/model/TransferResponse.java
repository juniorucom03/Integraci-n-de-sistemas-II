package com.sipap.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de respuesta homogénea usado por la API y por los resultados finales
 * publicados por los consumidores (log / message store).
 */
public class TransferResponse {

    @JsonProperty("id_transaccion")
    private String idTransaccion;

    @JsonProperty("estado")
    private String estado;

    @JsonProperty("mensaje")
    private String mensaje;

    public TransferResponse() {
    }

    public TransferResponse(String idTransaccion, EstadoTransferencia estado, String mensaje) {
        this.idTransaccion = idTransaccion;
        this.estado = estado.name();
        this.mensaje = mensaje;
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
}
