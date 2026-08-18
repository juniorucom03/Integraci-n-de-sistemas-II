package com.sipap.util;

/**
 * Constructor de cadenas QR válidas de prueba (formato TLV tipo EMV),
 * para no depender de un generador externo en los tests.
 */
public final class QrTestFixtures {

    private QrTestFixtures() {
    }

    private static String tlv(String tag, String value) {
        return tag + String.format("%02d", value.length()) + value;
    }

    /** QR estático: sin tag 54 (monto), point of initiation = 11. */
    public static String qrEstatico(String entidad) {
        String merchantAccount = tlv("00", "PY.SIPAP.QR") + tlv("00", entidad); // subcampo 00 = entidad (simplificado, GUID + cuenta)
        // Nota: para simplificar el fixture, el template 26 contiene un único subcampo 00 = entidad.
        String template26 = tlv("00", entidad);
        String additionalData = tlv("05", "PAGO-SERVICIO-001");
        StringBuilder sb = new StringBuilder();
        sb.append(tlv("00", "01"));               // Payload format
        sb.append(tlv("01", "11"));                // estático
        sb.append(tlv("26", template26));          // merchant account info -> entidad
        sb.append(tlv("53", "600"));                // moneda PYG
        sb.append(tlv("58", "PY"));
        sb.append(tlv("59", "COMERCIO DEMO"));
        sb.append(tlv("60", "ASUNCION"));
        sb.append(tlv("62", additionalData));
        sb.append(tlv("63", "ABCD"));               // CRC simulado
        return sb.toString();
    }

    /** QR dinámico: incluye tag 54 con el monto. */
    public static String qrDinamico(String entidad, String monto) {
        String template26 = tlv("00", entidad);
        String additionalData = tlv("05", "FACTURA-778899");
        StringBuilder sb = new StringBuilder();
        sb.append(tlv("00", "01"));
        sb.append(tlv("01", "12"));                 // dinámico
        sb.append(tlv("26", template26));
        sb.append(tlv("53", "600"));
        sb.append(tlv("54", monto));
        sb.append(tlv("58", "PY"));
        sb.append(tlv("59", "COMERCIO DEMO"));
        sb.append(tlv("60", "ASUNCION"));
        sb.append(tlv("62", additionalData));
        sb.append(tlv("63", "WXYZ"));
        return sb.toString();
    }
}
