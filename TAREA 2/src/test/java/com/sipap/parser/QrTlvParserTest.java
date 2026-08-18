package com.sipap.parser;

import com.sipap.model.CanonicalTransfer;
import com.sipap.model.TipoQr;
import com.sipap.util.QrTestFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class QrTlvParserTest {

    private final QrTlvParser parser = new QrTlvParser();

    @Test
    void parseaQrEstaticoUsandoMontoDeApi() {
        String qr = QrTestFixtures.qrEstatico("0001");
        CanonicalTransfer t = parser.parseToCanonical(qr, "TX000001", LocalDate.now(), new BigDecimal("10"));

        assertEquals(TipoQr.ESTATICO, t.getTipoQr());
        assertEquals("0001", t.getEntidadFinancieraDestino());
        assertEquals(0, new BigDecimal("10").compareTo(t.getMonto()));
        assertEquals("TX000001", t.getIdTransaccion());
        assertEquals("PAGO-SERVICIO-001", t.getReferencia());
    }

    @Test
    void parseaQrDinamicoUsandoMontoDelQr() {
        String qr = QrTestFixtures.qrDinamico("0002", "1500.50");
        CanonicalTransfer t = parser.parseToCanonical(qr, "TX000002", LocalDate.now(), null);

        assertEquals(TipoQr.DINAMICO, t.getTipoQr());
        assertEquals("0002", t.getEntidadFinancieraDestino());
        assertEquals(0, new BigDecimal("1500.50").compareTo(t.getMonto()));
    }

    @Test
    void qrEstaticoSinMontoDeApiFalla() {
        String qr = QrTestFixtures.qrEstatico("0001");
        assertThrows(QrTlvParser.QrParseException.class,
                () -> parser.parseToCanonical(qr, "TX1", LocalDate.now(), null));
    }

    @Test
    void qrMalformadoLanzaExcepcion() {
        assertThrows(QrTlvParser.QrParseException.class,
                () -> parser.parseToCanonical("00021z", "TX1", LocalDate.now(), BigDecimal.TEN));
    }

    @Test
    void qrSinEntidadLanzaExcepcion() {
        String qr = "000201" + "0102" + "11" + "5303600" + "6304ABCD";
        assertThrows(QrTlvParser.QrParseException.class,
                () -> parser.parseToCanonical(qr, "TX1", LocalDate.now(), BigDecimal.TEN));
    }
}
