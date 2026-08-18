package com.bcp.sipap.validation;

import com.bcp.sipap.parser.QrParseException;
import com.bcp.sipap.parser.QrTlvParser;
import com.bcp.sipap.util.BankCatalog;

import java.util.Map;

/**
 * Reglas de validación de negocio sobre los campos ya interpretados (TLV) de
 * la cadena QR. Actúa como puerta (Message Filter): solo los mensajes que
 * pasan todas las reglas continúan hacia la transformación y el enrutamiento;
 * el resto produce una {@link ValidationException} con el motivo del rechazo.
 */
public final class QrValidator {

    public static final String CHECKSUM_DUMMY = "A1B2";
    public static final String MONEDA_PYG = "600";
    public static final long MONTO_MAXIMO = 10_000_000L;
    public static final String GUI_ESPERADO = "py.gov.bcp.sip";

    private QrValidator() {
    }

    /**
     * Valida el mapa de tags de nivel superior de una cadena QR ya parseada.
     *
     * @throws ValidationException si alguna regla de negocio no se cumple.
     */
    public static void validar(Map<String, String> tags) {
        // 1) Campos obligatorios de nivel superior
        requerirPresente(tags, "00", "Falta el campo obligatorio Payload Format Indicator (00)");
        requerirPresente(tags, "01", "Falta el campo obligatorio Point of Initiation Method (01)");
        requerirPresente(tags, "32", "Falta el campo obligatorio Merchant Account Information (32)");
        requerirPresente(tags, "52", "Falta el campo obligatorio Merchant Category Code (52)");
        requerirPresente(tags, "53", "Falta el campo obligatorio Transaction Currency (53)");
        requerirPresente(tags, "58", "Falta el campo obligatorio Country Code (58)");
        requerirPresente(tags, "59", "Falta el campo obligatorio Merchant Name (59)");
        requerirPresente(tags, "60", "Falta el campo obligatorio Merchant City (60)");
        requerirPresente(tags, "63", "Falta el campo obligatorio CRC (63)");

        // 2) Payload Format Indicator
        if (!"01".equals(tags.get("00"))) {
            throw new ValidationException("Payload Format Indicator inválido: se esperaba '01'");
        }

        // 3) Point of Initiation Method: 11 (estático) o 12 (dinámico)
        String pim = tags.get("01");
        boolean esDinamico;
        if ("11".equals(pim)) {
            esDinamico = false;
        } else if ("12".equals(pim)) {
            esDinamico = true;
        } else {
            throw new ValidationException("Point of Initiation Method inválido: '" + pim + "' (se esperaba 11 o 12)");
        }

        // 4) Merchant Account Information: debe ser TLV válido con sub-tags 00, 01, 02
        Map<String, String> subTags;
        try {
            subTags = QrTlvParser.parse(tags.get("32"));
        } catch (QrParseException e) {
            throw new ValidationException("Merchant Account Information (32) mal formado: " + e.getMessage());
        }
        requerirPresente(subTags, "00", "Falta el sub-tag Globally Unique Identifier (32/00)");
        requerirPresente(subTags, "01", "Falta el sub-tag Código de Entidad (32/01)");
        requerirPresente(subTags, "02", "Falta el sub-tag Número de Cuenta (32/02)");

        if (!GUI_ESPERADO.equals(subTags.get("00"))) {
            throw new ValidationException("Globally Unique Identifier no reconocido: '" + subTags.get("00") + "'");
        }

        String codigoEntidad = subTags.get("01");
        if (!BankCatalog.esCodigoReconocido(codigoEntidad)) {
            throw new ValidationException("Código de entidad no reconocido: '" + codigoEntidad + "'");
        }

        // 5) Moneda: debe ser 600 (PYG)
        if (!MONEDA_PYG.equals(tags.get("53"))) {
            throw new ValidationException("Moneda no soportada: '" + tags.get("53") + "' (se esperaba 600 = PYG)");
        }

        // 6) Monto: obligatorio y positivo si es QR dinámico; si viene, no debe superar el máximo
        String montoStr = tags.get("54");
        if (esDinamico) {
            if (montoStr == null || montoStr.isEmpty()) {
                throw new ValidationException("El monto (54) es obligatorio en un QR dinámico");
            }
        }
        if (montoStr != null && !montoStr.isEmpty()) {
            long monto;
            try {
                monto = Long.parseLong(montoStr);
            } catch (NumberFormatException e) {
                throw new ValidationException("El monto (54) no es numérico: '" + montoStr + "'");
            }
            if (monto <= 0) {
                throw new ValidationException("El monto debe ser positivo");
            }
            if (monto >= MONTO_MAXIMO) {
                throw new ValidationException("El monto supera el máximo permitido de " + MONTO_MAXIMO);
            }
        }

        // 7) Checksum dummy
        if (!CHECKSUM_DUMMY.equals(tags.get("63"))) {
            throw new ValidationException("Checksum inválido: '" + tags.get("63") + "' (se esperaba " + CHECKSUM_DUMMY + ")");
        }
    }

    private static void requerirPresente(Map<String, String> tags, String tag, String mensajeError) {
        String valor = tags.get(tag);
        if (valor == null || valor.isEmpty()) {
            throw new ValidationException(mensajeError);
        }
    }
}
