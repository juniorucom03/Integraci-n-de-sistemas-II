package com.bcp.sipap.parser;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QrTlvParserTest {

    @Test
    void parseaCadenaValidaConCamposSimples() {
        String qr = QrTlvBuilder.nuevo()
                .campo("00", "01")
                .campo("01", "11")
                .campo("58", "PY")
                .build();

        Map<String, String> tags = QrTlvParser.parse(qr);

        assertEquals("01", tags.get("00"));
        assertEquals("11", tags.get("01"));
        assertEquals("PY", tags.get("58"));
    }

    @Test
    void parseaBloqueAnidadoDeMerchantAccountInformation() {
        String bloque32 = QrTlvBuilder.merchantAccountInformation("py.gov.bcp.sip", "0015", "1234567890");
        Map<String, String> top = QrTlvParser.parse(bloque32);
        assertTrue(top.containsKey("32"));

        Map<String, String> sub = QrTlvParser.parse(top.get("32"));
        assertEquals("py.gov.bcp.sip", sub.get("00"));
        assertEquals("0015", sub.get("01"));
        assertEquals("1234567890", sub.get("02"));
    }

    @Test
    void lanzaExcepcionSiLaLongitudDeclaradaExcedeLaCadena() {
        String qr = QrTlvBuilder.nuevo()
                .campoConLongitudForzada("58", 10, "PY") // dice 10 pero solo hay 2
                .build();

        assertThrows(QrParseException.class, () -> QrTlvParser.parse(qr));
    }

    @Test
    void lanzaExcepcionSiFaltanCaracteresParaTagYLongitud() {
        assertThrows(QrParseException.class, () -> QrTlvParser.parse("58"));
    }

    @Test
    void lanzaExcepcionSiLaLongitudNoEsNumerica() {
        assertThrows(QrParseException.class, () -> QrTlvParser.parse("58XXPY"));
    }

    @Test
    void lanzaExcepcionConCadenaVacia() {
        assertThrows(QrParseException.class, () -> QrTlvParser.parse(""));
    }

    @Test
    void lanzaExcepcionConTagDuplicado() {
        String qr = QrTlvBuilder.nuevo()
                .campo("58", "PY")
                .campo("58", "AR")
                .build();
        assertThrows(QrParseException.class, () -> QrTlvParser.parse(qr));
    }
}
