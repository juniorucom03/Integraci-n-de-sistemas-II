package com.sipap.routes;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sipap.model.EstadoTransferencia;
import com.sipap.model.TransferRequest;
import com.sipap.model.TransferResponse;

@Component
public class ApiRestRoute extends RouteBuilder {

    @Override
    public void configure() {

        // ============================================================
        // Configuración de Jackson
        // ============================================================

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        JacksonDataFormat jacksonDataFormat =
                new JacksonDataFormat(objectMapper, null);


        // ============================================================
        // Manejo de errores
        // ============================================================

        onException(Exception.class)
                .handled(true)
                .log(
                        LoggingLevel.ERROR,
                        "ERROR REAL en la ruta: ${exception.stacktrace}"
                )
                .process(exchange -> {

                    TransferRequest req =
                            exchange.getIn().getBody(TransferRequest.class);

                    String id = req != null
                            ? req.getIdTransaccion()
                            : null;

                    TransferResponse resp =
                            new TransferResponse(
                                    id,
                                    EstadoTransferencia.ERROR_BANCO,
                                    "Error interno procesando la transferencia"
                            );

                    exchange.getIn().setBody(resp);
                })
                .marshal()
                .json(JsonLibrary.Jackson)
                .setHeader(
                        "CamelHttpResponseCode",
                        constant(500)
                );


        // ============================================================
        // Configuración REST
        // ============================================================

        restConfiguration()
                .component("platform-http")
                .bindingMode(
                        org.apache.camel.model.rest.RestBindingMode.off
                )
                .dataFormatProperty(
                        "prettyPrint",
                        "true"
                );


        // ============================================================
        // Endpoint REST
        // ============================================================

        rest("/api")
                .post("/transferencias")
                .consumes("application/json")
                .produces("application/json")
                .to("direct:recibirTransferencia");


        // ============================================================
        // Ruta principal
        // ============================================================

        from("direct:recibirTransferencia")
                .routeId("api-recibir-transferencia")

                .log("Nueva petición recibida: ${body}")


                // ----------------------------------------------------
                // JSON -> TransferRequest
                // ----------------------------------------------------

                .unmarshal()
                .json(
                        JsonLibrary.Jackson,
                        TransferRequest.class
                )


                // ----------------------------------------------------
                // Parser + validaciones
                // ----------------------------------------------------

                .process("parseAndValidateProcessor")


                // ----------------------------------------------------
                // Resultado de las validaciones
                // ----------------------------------------------------

                .choice()


                    // =================================================
                    // Transferencia rechazada
                    // =================================================

                    .when(
                            exchangeProperty("rechazado")
                                    .isEqualTo(true)
                    )

                        .log(
                                "Transferencia RECHAZADA antes de publicar. "
                                + "id=${header.idTransaccion}"
                        )

                        .marshal()
                        .json(JsonLibrary.Jackson)

                        .setHeader(
                                "CamelHttpResponseCode",
                                constant(422)
                        )


                    // =================================================
                    // Transferencia válida
                    // =================================================

                    .otherwise()

                        .log(
                                "Transferencia válida. "
                                + "Publicando en Artemis. "
                                + "id=${header.idTransaccion}"
                        )


                        // ------------------------------------------------
                        // Correlation Identifier
                        // ------------------------------------------------

                        .setHeader(
                                "JMSCorrelationID",
                                header("idTransaccion")
                        )


                        // ------------------------------------------------
                        // Obtener modelo canónico
                        // ------------------------------------------------

                        .process(exchange -> {

                            com.sipap.model.CanonicalTransfer transfer =
                                    exchange.getProperty(
                                            "canonicalTransfer",
                                            com.sipap.model.CanonicalTransfer.class
                                    );

                            exchange.getIn().setBody(transfer);
                        })


                        // ------------------------------------------------
                        // Modelo canónico -> JSON
                        //
                        // Se utiliza el JacksonDataFormat configurado
                        // con JavaTimeModule para soportar LocalDate.
                        // ------------------------------------------------

                        .marshal(jacksonDataFormat)


                        // ------------------------------------------------
                        // Publicación asíncrona en Artemis
                        // ------------------------------------------------

                        .to(
                                "jms:queue:sipap.transferencias.entrada"
                                + "?exchangePattern=InOnly"
                        )


                        // ------------------------------------------------
                        // Respuesta inmediata
                        // ------------------------------------------------

                        .process(exchange -> {

                            com.sipap.model.CanonicalTransfer transfer =
                                    exchange.getProperty(
                                            "canonicalTransfer",
                                            com.sipap.model.CanonicalTransfer.class
                                    );

                            TransferResponse resp =
                                    new TransferResponse(
                                            transfer.getIdTransaccion(),
                                            EstadoTransferencia
                                                    .ACEPTADA_PARA_PROCESAMIENTO,
                                            "Transferencia enviada a la cola"
                                    );

                            exchange.getIn().setBody(resp);
                        })


                        // ------------------------------------------------
                        // TransferResponse -> JSON
                        // ------------------------------------------------

                        .marshal()
                        .json(JsonLibrary.Jackson)


                        // ------------------------------------------------
                        // HTTP 202 Accepted
                        // ------------------------------------------------

                        .setHeader(
                                "CamelHttpResponseCode",
                                constant(202)
                        );

    }
}