package com.bcp.sipap.validation;

import com.bcp.sipap.parser.QrTlvParser;
import com.bcp.sipap.util.BankCatalog;
import com.bcp.sipap.util.QrSampleGenerator;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QrValidatorTest {

    private Map<String, String> parse(String qr) {
        return QrTlvParser.parse(qr);
    }

    @Test
    void transferenciaValidaHaciaItauNoLanzaExcepcion() {
        String qr = QrSampleGenerator.validoEstatico(BankCatalog.ITAU, "1234567890");
        assertDoesNotThrow(() -> QrValidator.validar(parse(qr)));
    }

    @Test
    void transferenciaValidaHaciaAtlasNoLanzaExcepcion() {
        String qr = QrSampleGenerator.validoDinamico(BankCatalog.ATLAS, "1234567890", 50000);
        assertDoesNotThrow(() -> QrValidator.validar(parse(qr)));
    }

    @Test
    void transferenciaValidaHaciaFamiliarNoLanzaExcepcion() {
        String qr = QrSampleGenerator.validoEstatico(BankCatalog.FAMILIAR, "9876543210");
        assertDoesNotThrow(() -> QrValidator.validar(parse(qr)));
    }

    @Test
    void bancoDestinoDesconocidoEsRechazado() {
        String qr = QrSampleGenerator.invalidoBancoDesconocido();
        ValidationException ex = assertThrows(ValidationException.class, () -> QrValidator.validar(parse(qr)));
        assertTrue(ex.getMessage().toLowerCase().contains("no reconocido"));
    }

    @Test
    void campoObligatorioAusenteEsRechazado() {
        String qr = QrSampleGenerator.invalidoCampoFaltante(BankCatalog.ITAU, "1234567890");
        assertThrows(ValidationException.class, () -> QrValidator.validar(parse(qr)));
    }

    @Test
    void montoMayorOIgualAlMaximoEsRechazado() {
        String qr = QrSampleGenerator.invalidoMontoExcesivo(BankCatalog.ATLAS, "1234567890");
        ValidationException ex = assertThrows(ValidationException.class, () -> QrValidator.validar(parse(qr)));
        assertTrue(ex.getMessage().toLowerCase().contains("máximo") || ex.getMessage().toLowerCase().contains("maximo"));
    }

    @Test
    void checksumDistintoDeDummyEsRechazado() {
        String qr = QrSampleGenerator.invalidoChecksum(BankCatalog.FAMILIAR, "1234567890");
        ValidationException ex = assertThrows(ValidationException.class, () -> QrValidator.validar(parse(qr)));
        assertTrue(ex.getMessage().toLowerCase().contains("checksum"));
    }

    @Test
    void montoObligatorioEnQrDinamicoSinMontoEsRechazado() {
        String qrSinMonto = com.bcp.sipap.parser.QrTlvBuilder.nuevo()
                .campo("00", "01")
                .campo("01", "12") // dinámico
                .crudo(com.bcp.sipap.parser.QrTlvBuilder.merchantAccountInformation("py.gov.bcp.sip", BankCatalog.ITAU, "1234567890"))
                .campo("52", "5731")
                .campo("53", "600")
                .campo("58", "PY")
                .campo("59", "COMERCIO")
                .campo("60", "ASUNCION")
                .campo("63", "A1B2")
                .build();

        assertThrows(ValidationException.class, () -> QrValidator.validar(parse(qrSinMonto)));
    }

    @Test
    void monedaDistintaDePygEsRechazada() {
        String qr = com.bcp.sipap.parser.QrTlvBuilder.nuevo()
                .campo("00", "01")
                .campo("01", "11")
                .crudo(com.bcp.sipap.parser.QrTlvBuilder.merchantAccountInformation("py.gov.bcp.sip", BankCatalog.ITAU, "1234567890"))
                .campo("52", "5731")
                .campo("53", "840") // USD, no soportado
                .campo("58", "PY")
                .campo("59", "COMERCIO")
                .campo("60", "ASUNCION")
                .campo("63", "A1B2")
                .build();

        assertThrows(ValidationException.class, () -> QrValidator.validar(parse(qr)));
    }
}
