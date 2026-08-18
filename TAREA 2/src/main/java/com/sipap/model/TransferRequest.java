package com.sipap.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO de entrada de la API REST (tal cual llega del cliente).
 */
public class TransferRequest {

    @JsonProperty("id_transaccion")
    private String idTransaccion;

    @JsonProperty("fecha_transaccion")
    private String fechaTransaccion;

    @JsonProperty("qr")
    private String qr;

    @JsonProperty("monto")
    private String monto;

    public String getIdTransaccion() {
        return idTransaccion;
    }

    public void setIdTransaccion(String idTransaccion) {
        this.idTransaccion = idTransaccion;
    }

    public String getFechaTransaccion() {
        return fechaTransaccion;
    }

    public void setFechaTransaccion(String fechaTransaccion) {
        this.fechaTransaccion = fechaTransaccion;
    }

    public String getQr() {
        return qr;
    }

    public void setQr(String qr) {
        this.qr = qr;
    }

    public String getMonto() {
        return monto;
    }

    public void setMonto(String monto) {
        this.monto = monto;
    }
}
