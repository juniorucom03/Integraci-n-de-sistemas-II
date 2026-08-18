
package com.bcp.sipap.routes;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.bcp.sipap.util.BankCatalog;
import com.bcp.sipap.util.Headers;
import com.bcp.sipap.util.QrSampleGenerator;

/**
 * Prueba de integración extremo a extremo: envía cadenas QR directamente al
 * canal de entrada del mediador (direct:sipap-in) y verifica el resultado
 * final publicado en direct:resultado, sin pasar por los productores (timers).
 * <p>
 * Cubre los escenarios mínimos exigidos por la consigna.
 */
class MediadorRouteTest extends CamelTestSupport {

    @Override
    protected RouteBuilder[] createRouteBuilders() {
        return new RouteBuilder[]{ new MediadorRoute(), new ConsumidorRoute() };
    }

    @Override
    public boolean isUseAdviceWith() {
        // El contexto no se inicia automáticamente: se inicia manualmente
        // luego de aplicar adviceWith() sobre la ruta "resultado-final".
        return true;
    }

    @Produce("direct:sipap-in")
    ProducerTemplate productor;

    @EndpointInject("mock:resultado")
    MockEndpoint mockResultado;

    private void interceptarResultado() throws Exception {
        AdviceWith.adviceWith(context.getRouteDefinition("resultado-final"), context,
        new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveAddLast().to("mock:resultado");
            }
        });
        context.start();
    }

    private Map<String, Object> headersConTx(String tx) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Headers.ID_TRANSACCION, tx);
        return headers;
    }

    @Test
    void transferenciaValidaHaciaItauSeProcesa() throws Exception {
        interceptarResultado();
        mockResultado.expectedMessageCount(1);

        String qr = QrSampleGenerator.validoEstatico(BankCatalog.ITAU, "1234567890");
        productor.sendBodyAndHeaders(qr, headersConTx("TX-TEST-ITAU"));

        mockResultado.assertIsSatisfied();
        String json = mockResultado.getExchanges().get(0).getIn().getBody(String.class);
        assertEquals(true, json.contains("PROCESADA"));
    }

    @Test
    void transferenciaHaciaBancoDesconocidoEsRechazada() throws Exception {
        interceptarResultado();
        mockResultado.expectedMessageCount(1);

        String qr = QrSampleGenerator.invalidoBancoDesconocido();
        productor.sendBodyAndHeaders(qr, headersConTx("TX-TEST-DESCONOCIDO"));

        mockResultado.assertIsSatisfied();
        String json = mockResultado.getExchanges().get(0).getIn().getBody(String.class);
        assertEquals(true, json.contains("RECHAZADA"));
    }

    @Test
    void checksumInvalidoEsRechazado() throws Exception {
        interceptarResultado();
        mockResultado.expectedMessageCount(1);

        String qr = QrSampleGenerator.invalidoChecksum(BankCatalog.ATLAS, "1234567890");
        productor.sendBodyAndHeaders(qr, headersConTx("TX-TEST-CHECKSUM"));

        mockResultado.assertIsSatisfied();
        String json = mockResultado.getExchanges().get(0).getIn().getBody(String.class);
        assertEquals(true, json.contains("RECHAZADA"));
    }
}
