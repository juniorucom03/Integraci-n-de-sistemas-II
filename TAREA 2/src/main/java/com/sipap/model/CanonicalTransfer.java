package com.sipap.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Modelo canónico de una transferencia SIPAP, independiente de la
 * tecnología de mensajería (JMS/AMQP). Esta clase NO debe conocer nada
 * de Camel, JMS Message ni Artemis: solo representa el dominio.
 *
 * Se hereda/mantiene desde la Tarea 1 y se reutiliza sin cambios de
 * responsabilidad en la Tarea 2.
 */
public class CanonicalTransfer {

    private String idTransaccion;
    private LocalDate fechaTransaccion;

    /** ESTATICO o DINAMICO, según el campo 01 del QR (Point of Initiation Method). */
    private TipoQr tipoQr;

    /** Identificador de la entidad financiera destino, extraído del QR (tag 26-51, subcampo 00). */
    private String entidadFinancieraDestino;

    /** Moneda ISO 4217 numérica o alfabética (tag 53). */
    private String moneda;

    /** Monto final de la transferencia ya resuelto (QR dinámico -> tag 54; QR estático -> monto de la API). */
    private BigDecimal monto;

    /** Referencia / glosa (subcampo 05 del tag 62, "Additional Data Field"). */
    private String referencia;

    /** Cadena QR original, sin modificar, para trazabilidad. */
    private String qrOriginal;

    public CanonicalTransfer() {
    }

    public String getIdTransaccion() {
        return idTransaccion;
    }

    public void setIdTransaccion(String idTransaccion) {
        this.idTransaccion = idTransaccion;
    }

    public LocalDate getFechaTransaccion() {
        return fechaTransaccion;
    }

    public void setFechaTransaccion(LocalDate fechaTransaccion) {
        this.fechaTransaccion = fechaTransaccion;
    }

    public TipoQr getTipoQr() {
        return tipoQr;
    }

    public void setTipoQr(TipoQr tipoQr) {
        this.tipoQr = tipoQr;
    }

    public String getEntidadFinancieraDestino() {
        return entidadFinancieraDestino;
    }

    public void setEntidadFinancieraDestino(String entidadFinancieraDestino) {
        this.entidadFinancieraDestino = entidadFinancieraDestino;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public String getQrOriginal() {
        return qrOriginal;
    }

    public void setQrOriginal(String qrOriginal) {
        this.qrOriginal = qrOriginal;
    }

    @Override
    public String toString() {
        return "CanonicalTransfer{" +
                "idTransaccion='" + idTransaccion + '\'' +
                ", fechaTransaccion=" + fechaTransaccion +
                ", tipoQr=" + tipoQr +
                ", entidadFinancieraDestino='" + entidadFinancieraDestino + '\'' +
                ", moneda='" + moneda + '\'' +
                ", monto=" + monto +
                ", referencia='" + referencia + '\'' +
                '}';
    }
}
