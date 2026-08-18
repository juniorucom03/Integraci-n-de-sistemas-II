package com.bcp.sipap.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Representa la información anidada del bloque "Merchant Account Information"
 * (tag 32), que contiene los sub-tags 00, 01 y 02 según el esquema SIP/EMVCo:
 *
 *  - 00: Globally Unique Identifier (dominio invertido del BCP: py.gov.bcp.sip)
 *  - 01: Código de la entidad financiera (asignado por el BCP)
 *  - 02: Número de cuenta bancaria del beneficiario
 */
public class MerchantAccountInformation {

    @JsonProperty("globally_unique_identifier")
    private String globallyUniqueIdentifier;

    @JsonProperty("codigo_entidad")
    private String codigoEntidad;

    @JsonProperty("numero_cuenta")
    private String numeroCuenta;

    public MerchantAccountInformation() {
    }

    public MerchantAccountInformation(String globallyUniqueIdentifier, String codigoEntidad, String numeroCuenta) {
        this.globallyUniqueIdentifier = globallyUniqueIdentifier;
        this.codigoEntidad = codigoEntidad;
        this.numeroCuenta = numeroCuenta;
    }

    public String getGloballyUniqueIdentifier() {
        return globallyUniqueIdentifier;
    }

    public void setGloballyUniqueIdentifier(String globallyUniqueIdentifier) {
        this.globallyUniqueIdentifier = globallyUniqueIdentifier;
    }

    public String getCodigoEntidad() {
        return codigoEntidad;
    }

    public void setCodigoEntidad(String codigoEntidad) {
        this.codigoEntidad = codigoEntidad;
    }

    public String getNumeroCuenta() {
        return numeroCuenta;
    }

    public void setNumeroCuenta(String numeroCuenta) {
        this.numeroCuenta = numeroCuenta;
    }

    @Override
    public String toString() {
        return "MerchantAccountInformation{" +
                "globallyUniqueIdentifier='" + globallyUniqueIdentifier + '\'' +
                ", codigoEntidad='" + codigoEntidad + '\'' +
                ", numeroCuenta='" + numeroCuenta + '\'' +
                '}';
    }
}
