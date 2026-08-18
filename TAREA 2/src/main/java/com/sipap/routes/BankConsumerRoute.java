package com.sipap.routes;

import com.sipap.model.CanonicalTransfer;
import com.sipap.model.EstadoTransferencia;
import com.sipap.model.TransferResponse;
import com.sipap.util.MessageStore;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Requerimientos funcionales 4 y 5 + patrones EIP Request-Reply y Message Store.
 *
 * Se crea una ruta de consumidor independiente por cada banco configurado en
 * "sipap.bancos" (application.yml), cada una escuchando su propia cola
 * punto a punto "sipap.banco.<codigo>". Esto satisface el requisito de que
 * cada transferencia sea procesada por un único consumidor destinatario.
 */
@Component
public class BankConsumerRoute extends RouteBuilder {

    @Value("${sipap.bancos.codigos:0001,0002,0003}")
    private String codigosBancosCsv;

    @Value("${sipap.mock.baseUrl:http://localhost:9561}")
    private String mockBaseUrl;

    @Autowired
    private MessageStore messageStore;

    @Override
    public void configure() {

        for (String codigo : codigosBancosCsv.split(",")) {
            String c = codigo.trim();
            String queue = "sipap.banco." + c;
            String routeId = "consumidor-banco-" + c;
            String mockUri = mockBaseUrl + "/banco/" + c + "/transferencias";

            from("jms:queue:" + queue)
                    .routeId(routeId)
                    .log("Consumidor banco " + c + " recibió mensaje id=${header.JMSCorrelationID}")
                    .unmarshal().json(JsonLibrary.Jackson, CanonicalTransfer.class)
                    .process("dateAndIdempotencyProcessor")
                    .choice()
                        .when(exchangeProperty("detenerFlujo").isEqualTo(true))
                            .process(exchange -> {
                                TransferResponse resp = exchange.getIn().getBody(TransferResponse.class);
                                messageStore.guardar(resp.getIdTransaccion(), null, resp.getEstado());
                                org.slf4j.LoggerFactory.getLogger(getClass())
                                        .info("Resultado final id={} estado={} mensaje={}",
                                                resp.getIdTransaccion(), resp.getEstado(), resp.getMensaje());
                            })
                        .otherwise()
                            // Request-Reply: se invoca al banco mock y se espera la respuesta HTTP
                            // antes de continuar. La correlación se mantiene vía id_transaccion,
                            // tanto en el path del mock como en un header explícito.
                            .setProperty("canonicalTransfer", body())
                            .marshal().json(JsonLibrary.Jackson)
                            .setHeader("Content-Type", constant("application/json"))
                            .setHeader("X-Id-Transaccion", header("idTransaccion"))
                            .to("log:com.sipap.bankcall?showHeaders=true&showBody=true&level=DEBUG")
                            .doTry()
                                .toD(mockUri + "?bridgeEndpoint=true&throwExceptionOnFailure=false")
                                .process("bankResponseProcessor")
                            .doCatch(Exception.class)
                                .process(exchange -> {
                                    CanonicalTransfer t = exchange.getProperty("canonicalTransfer", CanonicalTransfer.class);
                                    TransferResponse resp = new TransferResponse(t.getIdTransaccion(),
                                            EstadoTransferencia.ERROR_BANCO,
                                            "No se pudo contactar al banco destino");
                                    exchange.getIn().setBody(resp);
                                    messageStore.guardar(t.getIdTransaccion(), t, resp.getEstado());
                                })
                            .end()
                    .end();
        }
    }
}
