package com.sipap.parser;

import com.sipap.model.CanonicalTransfer;
import com.sipap.model.TipoQr;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parseador TLV del QR SIPAP (formato tipo EMV: tag de 2 dígitos,
 * longitud de 2 dígitos, valor de esa longitud; algunos tags son
 * "templates" que contienen a su vez subcampos TLV anidados).
 *
 * Esta clase pertenece a la Tarea 1 y se conserva sin cambios de
 * responsabilidad: recibe una cadena QR y devuelve un modelo canónico.
 * No conoce nada de Camel, JMS ni Artemis.
 *
 * Tags relevantes (subconjunto EMV QR usado por SIPAP):
 *  - 00: Payload Format Indicator
 *  - 01: Point of Initiation Method (11 = estático, 12 = dinámico)
 *  - 26-51: Merchant Account Information (template). Subcampo 00 = GUID/entidad.
 *  - 53: Moneda (ISO 4217 numérico)
 *  - 54: Monto de la transacción (solo presente en QR dinámico)
 *  - 58: País
 *  - 59: Nombre del comercio/beneficiario
 *  - 60: Ciudad
 *  - 62: Additional Data Field Template. Subcampo 05 = referencia/glosa
 *  - 63: CRC
 */
public class QrTlvParser {

    public static class QrParseException extends RuntimeException {
        public QrParseException(String message) {
            super(message);
        }
    }

    /**
     * Parsea el nivel superior de la cadena TLV en un mapa tag -> valor crudo.
     */
    public Map<String, String> parseTopLevel(String qr) {
        if (qr == null || qr.length() < 4) {
            throw new QrParseException("QR vacío o demasiado corto");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        int i = 0;
        int len = qr.length();
        while (i < len) {
            if (i + 4 > len) {
                throw new QrParseException("QR malformado: encabezado TLV incompleto en posición " + i);
            }
            String tag = qr.substring(i, i + 2);
            String lengthStr = qr.substring(i + 2, i + 4);
            int valueLen;
            try {
                valueLen = Integer.parseInt(lengthStr);
            } catch (NumberFormatException e) {
                throw new QrParseException("Longitud TLV inválida para tag " + tag);
            }
            int valueStart = i + 4;
            int valueEnd = valueStart + valueLen;
            if (valueEnd > len) {
                throw new QrParseException("QR malformado: longitud declarada excede el tamaño de la cadena (tag " + tag + ")");
            }
            String value = qr.substring(valueStart, valueEnd);
            fields.put(tag, value);
            i = valueEnd;
        }
        return fields;
    }

    /**
     * Parsea un subcampo TLV anidado (usado dentro de los templates 26-51 y 62).
     */
    public Map<String, String> parseSubfields(String templateValue) {
        return parseTopLevel(templateValue);
    }

    /**
     * Punto de entrada principal: dado el QR crudo y datos de la API (id, fecha,
     * monto opcional para QR estático), produce el modelo canónico.
     */
    public CanonicalTransfer parseToCanonical(String qrRaw, String idTransaccion,
                                               java.time.LocalDate fechaTransaccion,
                                               BigDecimal montoApi) {
        Map<String, String> top = parseTopLevel(qrRaw);

        CanonicalTransfer transfer = new CanonicalTransfer();
        transfer.setQrOriginal(qrRaw);
        transfer.setIdTransaccion(idTransaccion);
        transfer.setFechaTransaccion(fechaTransaccion);

        // Point of Initiation Method: 11 = estático, 12 = dinámico
        String pim = top.get("01");
        TipoQr tipoQr = "12".equals(pim) ? TipoQr.DINAMICO : TipoQr.ESTATICO;
        transfer.setTipoQr(tipoQr);

        // Entidad financiera: buscar el primer template de cuenta de comercio (26-51)
        String entidad = null;
        for (Map.Entry<String, String> entry : top.entrySet()) {
            String tag = entry.getKey();
            if (isMerchantAccountTag(tag)) {
                Map<String, String> sub = parseSubfields(entry.getValue());
                entidad = sub.get("00");
                if (entidad != null) {
                    break;
                }
            }
        }
        if (entidad == null) {
            throw new QrParseException("No se pudo determinar la entidad financiera destino del QR");
        }
        transfer.setEntidadFinancieraDestino(entidad);

        transfer.setMoneda(top.getOrDefault("53", "600")); // 600 = PYG en ISO 4217 numérico

        // Monto: si es dinámico, viene en el tag 54; si es estático, se toma de la API
        if (tipoQr == TipoQr.DINAMICO) {
            String montoStr = top.get("54");
            if (montoStr == null) {
                throw new QrParseException("QR dinámico sin campo de monto (tag 54)");
            }
            transfer.setMonto(new BigDecimal(montoStr));
        } else {
            if (montoApi == null) {
                throw new QrParseException("QR estático requiere monto proporcionado en la API");
            }
            transfer.setMonto(montoApi);
        }

        // Referencia / glosa: subcampo 05 del template 62 (Additional Data Field)
        String addData = top.get("62");
        if (addData != null) {
            Map<String, String> addFields = parseSubfields(addData);
            transfer.setReferencia(addFields.get("05"));
        }

        return transfer;
    }

    private boolean isMerchantAccountTag(String tag) {
        try {
            int t = Integer.parseInt(tag);
            return t >= 26 && t <= 51;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
