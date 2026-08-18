package com.sipap.routes;

import com.sipap.model.EstadoTransferencia;
import com.sipap.model.TransferRequest;
import com.sipap.model.TransferResponse;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

/**
 * Requerimiento funcional 1: API REST de productores.
 *
 * POST /api/transferencias
 * Body: { "id_transaccion", "fecha_transaccion", "qr", "monto" }
 *
 * Documentación completa de endpoints, ejemplos y respuestas: ver README.md
 */
@Component
public class ApiRestRoute extends RouteBuilder {

    @Override
    public void configure() {

        // Manejo de errores homogéneo: cualquier excepción no controlada
        // se traduce en una respuesta RECHAZADA con código 500.
        onException(Exception.class)
                .handled(true)
                .process(exchange -> {
                    TransferRequest req = exchange.getIn().getBody(TransferRequest.class);
                    String id = req != null ? req.getIdTransaccion() : null;
                    TransferResponse resp = new TransferResponse(id, EstadoTransferencia.ERROR_BANCO,
                            "Error interno procesando la transferencia");
                    exchange.getIn().setBody(resp);
                })
                .marshal().json(JsonLibrary.Jackson)
                .setHeader("CamelHttpResponseCode", constant(500));

        restConfiguration()
                .component("platform-http")
                .bindingMode(org.apache.camel.model.rest.RestBindingMode.off)
                .dataFormatProperty("prettyPrint", "true");

        rest("/api")
                .post("/transferencias")
                .consumes("application/json")
                .produces("application/json")
                .to("direct:recibirTransferencia");

        from("direct:recibirTransferencia")
                .routeId("api-recibir-transferencia")
                .log("Nueva petición recibida: ${body}")
                .unmarshal().json(JsonLibrary.Jackson, TransferRequest.class)
                .process("parseAndValidateProcessor")
                .choice()
                    .when(exchangeProperty("rechazado").isEqualTo(true))
                        .log("Transferencia RECHAZADA antes de publicar. id=${header.idTransaccion}")
                        .marshal().json(JsonLibrary.Jackson)
                        .setHeader("CamelHttpResponseCode", constant(422))
                    .otherwise()
                        // Message Channel externo: publicación en la cola de entrada de Artemis.
                        // Se serializa el modelo canónico (no el DTO crudo de la API) como
                        // cuerpo del mensaje JMS.
                        .setHeader("JMSCorrelationID", header("idTransaccion"))
                        .process(exchange -> {
                            var transfer = exchange.getProperty("canonicalTransfer", com.sipap.model.CanonicalTransfer.class);
                            exchange.getIn().setBody(transfer);
                        })
                        .marshal().json(JsonLibrary.Jackson)
                        .to("jms:queue:sipap.transferencias.entrada?exchangePattern=InOnly")
                        .process(exchange -> {
                            var transfer = exchange.getProperty("canonicalTransfer", com.sipap.model.CanonicalTransfer.class);
                            TransferResponse resp = new TransferResponse(
                                    transfer.getIdTransaccion(),
                                    EstadoTransferencia.ACEPTADA_PARA_PROCESAMIENTO,
                                    "Transferencia enviada a la cola");
                            exchange.getIn().setBody(resp);
                        })
                        .marshal().json(JsonLibrary.Jackson)
                        .setHeader("CamelHttpResponseCode", constant(202))
                .end();
    }
}
