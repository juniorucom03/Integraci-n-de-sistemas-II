package com.bcp.sipap.parser;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parser TLV (Tag-Length-Value) simplificado, conforme a la estructura EMVCo
 * utilizada en los QR interoperables del SIP Paraguay.
 * <p>
 * Cada campo tiene la forma: TAG (2 chars) + LONGITUD (2 chars, numérica) + VALOR
 * (tantos caracteres como indique LONGITUD). Se usa tanto para el nivel superior
 * de la cadena QR como para el contenido anidado del tag 32
 * (Merchant Account Information), que internamente también es TLV.
 */
public final class QrTlvParser {

    private static final int TAG_LEN = 2;
    private static final int LENGTH_LEN = 2;

    private QrTlvParser() {
    }

    /**
     * Interpreta una cadena TLV y devuelve un mapa ordenado tag -&gt; valor.
     *
     * @throws QrParseException si la estructura está incompleta, la longitud
     *                          declarada no es numérica, o excede el tamaño
     *                          real de la cadena.
     */
    public static Map<String, String> parse(String tlv) {
        if (tlv == null || tlv.isEmpty()) {
            throw new QrParseException("La cadena QR está vacía o es nula");
        }

        Map<String, String> campos = new LinkedHashMap<>();
        int i = 0;
        int len = tlv.length();

        while (i < len) {
            if (i + TAG_LEN + LENGTH_LEN > len) {
                throw new QrParseException(
                        "Estructura TLV incompleta cerca de la posición " + i +
                                ": no hay suficientes caracteres para tag+longitud");
            }

            String tag = tlv.substring(i, i + TAG_LEN);
            String lengthStr = tlv.substring(i + TAG_LEN, i + TAG_LEN + LENGTH_LEN);

            int valueLength;
            try {
                valueLength = Integer.parseInt(lengthStr);
            } catch (NumberFormatException e) {
                throw new QrParseException(
                        "Longitud no numérica ('" + lengthStr + "') para el tag " + tag);
            }
            if (valueLength < 0) {
                throw new QrParseException("Longitud negativa para el tag " + tag);
            }

            int valueStart = i + TAG_LEN + LENGTH_LEN;
            int valueEnd = valueStart + valueLength;
            if (valueEnd > len) {
                throw new QrParseException(
                        "La longitud declarada (" + valueLength + ") para el tag " + tag +
                                " excede el tamaño restante de la cadena");
            }

            String value = tlv.substring(valueStart, valueEnd);

            if (campos.containsKey(tag)) {
                throw new QrParseException("Tag duplicado: " + tag);
            }
            campos.put(tag, value);

            i = valueEnd;
        }

        return campos;
    }
}
