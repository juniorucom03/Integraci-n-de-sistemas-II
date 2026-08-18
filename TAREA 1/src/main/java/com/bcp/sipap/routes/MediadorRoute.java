package com.bcp.sipap.routes;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

import com.bcp.sipap.parser.QrParseException;
import com.bcp.sipap.processor.QrParseProcessor;
import com.bcp.sipap.processor.QrToTransferenciaTranslator;
import com.bcp.sipap.processor.QrValidationProcessor;
import com.bcp.sipap.processor.RechazoResultProcessor;
import com.bcp.sipap.util.BankCatalog;
import com.bcp.sipap.util.Headers;

/**
 * Mediador central. Implementa el flujo:
 * <p>
 * recepción -> auditoría (Wire Tap) -> parseo -> validación (Message Filter)
 * -> transformación (Message Translator) -> enrutamiento (Content-Based Router)
 * -> consumidor bancario o rechazo -> resultado.
 * <p>
 * Cada etapa vive en su propio canal interno (Message Channel: direct:*),
 * lo que evidencia el patrón Pipes and Filters: cada filtro es independiente,
 * testeable y reemplazable sin afectar a los demás.
 */
public class MediadorRoute extends RouteBuilder {

    @Override
    public void configure() {

        // Manejo global de errores no esperados.
        onException(Exception.class)
                .handled(true)
                .setHeader(
                        Headers.MOTIVO_RECHAZO,
                        simple("Error inesperado en el mediador: ${exception.message}")
                )
                .to("direct:dead-letter");

        // 1) Recepción -----------------------------------------------------
        from("direct:sipap-in")
                .routeId("mediador-recepcion")
                .log("[MEDIADOR] Recibido TX=${header.idTransaccion} de ${header.productorOrigen}")
                // Wire Tap: audita el mensaje crudo sin alterar el flujo principal.
                .wireTap("direct:audit")
                .to("direct:parseo");

        from("direct:audit")
                .routeId("auditoria-wiretap")
                .log("[AUDITORIA] TX=${header.idTransaccion} QR-crudo=${body}");

        // 2) Parseo TLV (Pipes and Filters - filtro 1) ----------------------
        from("direct:parseo")
                .routeId("mediador-parseo")
                .doTry()
                    .process(new QrParseProcessor())
                    .to("direct:validacion")
                .doCatch(QrParseException.class)
                    .setHeader(
                            Headers.MOTIVO_RECHAZO,
                            simple("Error de parseo TLV: ${exception.message}")
                    )
                    .to("direct:dead-letter")
                .end();

        // Dead Letter Channel: errores técnicos/estructurales de parseo.
        // recibe errores técnicos/estructurales de parseo y genera un resultado RECHAZADA
        from("direct:dead-letter")
                .routeId("dead-letter-channel")
                .log("[DEAD LETTER] TX=${header.idTransaccion} motivo=${header.motivoRechazo}")
                .process(new RechazoResultProcessor())
                .to("direct:resultado");

        // 3) Validación de negocio -----------------------------------------------
        // Message Filter:
        // solamente los mensajes que superan todas las reglas de validación
        // continúan hacia la transformación.
        // Los mensajes inválidos son derivados al canal de rechazo.
        from("direct:validacion")
                .routeId("mediador-validacion")
                .process(new QrValidationProcessor())

                // Message Filter: mensajes válidos.
                .filter(exchange ->
                        Boolean.TRUE.equals(
                                exchange.getProperty(Headers.PROP_VALIDACION_OK)
                        )
                )
                    .to("direct:transformacion")
                .end()

                // Message Filter: mensajes inválidos.
                .filter(exchange ->
                        Boolean.FALSE.equals(
                                exchange.getProperty(Headers.PROP_VALIDACION_OK)
                        )
                )
                    .to("direct:rechazo")
                .end();

        // Canal de rechazo de negocio.
        from("direct:rechazo")
                .routeId("rechazo-negocio")
                .log("[RECHAZO] TX=${header.idTransaccion} motivo=${header.motivoRechazo}")
                .process(new RechazoResultProcessor())
                .to("direct:resultado");

        // 4) Transformación (Message Translator, Pipes and Filters - filtro 3)
        from("direct:transformacion")
                .routeId("mediador-transformacion")
                .process(new QrToTransferenciaTranslator())
                .log("[TRANSFORMADO] TX=${header.idTransaccion} -> ${body}")
                .to("direct:enrutamiento");

        // 5) Enrutamiento (Content-Based Router) ---------------------------
        from("direct:enrutamiento")
                .routeId("mediador-enrutamiento")
                .choice()
                    .when(simple(
                            "${body.merchantAccountInformation.codigoEntidad} == '"
                                    + BankCatalog.ITAU + "'"
                    ))
                        .to("direct:itau")

                    .when(simple(
                            "${body.merchantAccountInformation.codigoEntidad} == '"
                                    + BankCatalog.ATLAS + "'"
                    ))
                        .to("direct:atlas")

                    .when(simple(
                            "${body.merchantAccountInformation.codigoEntidad} == '"
                                    + BankCatalog.FAMILIAR + "'"
                    ))
                        .to("direct:familiar")

                    .otherwise()
                        .setHeader(
                                Headers.MOTIVO_RECHAZO,
                                simple(
                                        "Banco destino '${body.merchantAccountInformation.codigoEntidad}' "
                                                + "reconocido por el BCP pero sin consumidor simulado "
                                                + "en este ejercicio"
                                )
                        )
                        .to("direct:rechazo")
                .end();

        // 6) Resultado final ----------------------------------------------
        from("direct:resultado")
                .routeId("resultado-final")
                .marshal().json(JsonLibrary.Jackson)
                .log("[RESULTADO] ${body}");
    }
}