package com.bcp.sipap.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Modelo canónico interno (Message Translator).
 * <p>
 * Es el resultado de parsear, validar y transformar la cadena QR TLV recibida.
 * Los consumidores (bancos) SOLO conocen esta representación; nunca vuelven a
 * interpretar la cadena TLV original.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Transferencia {

    @JsonProperty("id_transaccion")
    private String idTransaccion;

    @JsonProperty("payload_format_indicator")
    private String payloadFormatIndicator;

    @JsonProperty("point_of_initiation_method")
    private String pointOfInitiationMethod;

    @JsonProperty("merchant_account_information")
    private MerchantAccountInformation merchantAccountInformation;

    @JsonProperty("merchant_category_code")
    private String merchantCategoryCode;

    @JsonProperty("transaction_currency")
    private String transactionCurrency;

    @JsonProperty("transaction_amount")
    private Integer transactionAmount;

    @JsonProperty("country_code")
    private String countryCode;

    @JsonProperty("merchant_name")
    private String merchantName;

    @JsonProperty("merchant_city")
    private String merchantCity;

    @JsonProperty("crc")
    private String crc;

    public String getIdTransaccion() {
        return idTransaccion;
    }

    public void setIdTransaccion(String idTransaccion) {
        this.idTransaccion = idTransaccion;
    }

    public String getPayloadFormatIndicator() {
        return payloadFormatIndicator;
    }

    public void setPayloadFormatIndicator(String payloadFormatIndicator) {
        this.payloadFormatIndicator = payloadFormatIndicator;
    }

    public String getPointOfInitiationMethod() {
        return pointOfInitiationMethod;
    }

    public void setPointOfInitiationMethod(String pointOfInitiationMethod) {
        this.pointOfInitiationMethod = pointOfInitiationMethod;
    }

    public MerchantAccountInformation getMerchantAccountInformation() {
        return merchantAccountInformation;
    }

    public void setMerchantAccountInformation(MerchantAccountInformation merchantAccountInformation) {
        this.merchantAccountInformation = merchantAccountInformation;
    }

    public String getMerchantCategoryCode() {
        return merchantCategoryCode;
    }

    public void setMerchantCategoryCode(String merchantCategoryCode) {
        this.merchantCategoryCode = merchantCategoryCode;
    }

    public String getTransactionCurrency() {
        return transactionCurrency;
    }

    public void setTransactionCurrency(String transactionCurrency) {
        this.transactionCurrency = transactionCurrency;
    }

    public Integer getTransactionAmount() {
        return transactionAmount;
    }

    public void setTransactionAmount(Integer transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getMerchantCity() {
        return merchantCity;
    }

    public void setMerchantCity(String merchantCity) {
        this.merchantCity = merchantCity;
    }

    public String getCrc() {
        return crc;
    }

    public void setCrc(String crc) {
        this.crc = crc;
    }

    @Override
    public String toString() {
        return "Transferencia{" +
                "idTransaccion='" + idTransaccion + '\'' +
                ", payloadFormatIndicator='" + payloadFormatIndicator + '\'' +
                ", pointOfInitiationMethod='" + pointOfInitiationMethod + '\'' +
                ", merchantAccountInformation=" + merchantAccountInformation +
                ", merchantCategoryCode='" + merchantCategoryCode + '\'' +
                ", transactionCurrency='" + transactionCurrency + '\'' +
                ", transactionAmount=" + transactionAmount +
                ", countryCode='" + countryCode + '\'' +
                ", merchantName='" + merchantName + '\'' +
                ", merchantCity='" + merchantCity + '\'' +
                ", crc='" + crc + '\'' +
                '}';
    }
}
