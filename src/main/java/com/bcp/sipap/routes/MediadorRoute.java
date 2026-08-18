package com.bcp.sipap.routes;

import com.bcp.sipap.parser.QrParseException;
import com.bcp.sipap.processor.QrParseProcessor;
import com.bcp.sipap.processor.QrToTransferenciaTranslator;
import com.bcp.sipap.processor.QrValidationProcessor;
import com.bcp.sipap.processor.RechazoResultProcessor;
import com.bcp.sipap.util.BankCatalog;
import com.bcp.sipap.util.Headers;
import com.bcp.sipap.validation.ValidationException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;

/**
 * Mediador central. Implementa el flujo:
 * <p>
 * recepción -&gt; auditoría (Wire Tap) -&gt; parseo -&gt; validación (Message Filter)
 * -&gt; transformación (Message Translator) -&gt; enrutamiento (Content-Based Router)
 * -&gt; consumidor bancario o rechazo -&gt; resultado.
 * <p>
 * Cada etapa vive en su propio canal interno (Message Channel: direct:*),
 * lo que evidencia el patrón Pipes and Filters: cada filtro es independiente,
 * testeable y reemplazable sin afectar a los demás.
 */
public class MediadorRoute extends RouteBuilder {

    @Override
    public void configure() {

        // Manejo global de errores no esperados (red de seguridad adicional al doTry/doCatch explícito)
        onException(Exception.class)
                .handled(true)
                .setHeader(Headers.MOTIVO_RECHAZO, simple("Error inesperado en el mediador: ${exception.message}"))
                .to("direct:dead-letter");

        // 1) Recepción -----------------------------------------------------
        from("direct:sipap-in")
                .routeId("mediador-recepcion")
                .log("[MEDIADOR] Recibido TX=${header.idTransaccion} de ${header.productorOrigen}")
                // Wire Tap: audita el mensaje crudo sin alterar el flujo principal
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
                    .setHeader(Headers.MOTIVO_RECHAZO, simple("Error de parseo TLV: ${exception.message}"))
                    .to("direct:dead-letter")
                .end();

        // Dead Letter Channel: errores técnicos/estructurales de parseo
        from("direct:dead-letter")
                .routeId("dead-letter-channel")
                .log("[DEAD LETTER] TX=${header.idTransaccion} motivo=${header.motivoRechazo}")
                .process(new RechazoResultProcessor())
                .to("direct:resultado");

        // 3) Validación de negocio (Pipes and Filters - filtro 2 / Message Filter) ----
        from("direct:validacion")
                .routeId("mediador-validacion")
                .doTry()
                    .process(new QrValidationProcessor())
                    .to("direct:transformacion")
                .doCatch(ValidationException.class)
                    .setHeader(Headers.MOTIVO_RECHAZO, simple("${exception.message}"))
                    .to("direct:rechazo")
                .end();

        // Canal de rechazo de negocio: registra el motivo junto al id de transacción
        from("direct:rechazo")
                .routeId("rechazo-negocio")
                .log("[RECHAZO] TX=${header.idTransaccion} motivo=${header.motivoRechazo}")
                .process(new RechazoResultProcessor())
                .to("direct:resultado");

        // 4) Transformación (Message Translator, Pipes and Filters - filtro 3) --------
        from("direct:transformacion")
                .routeId("mediador-transformacion")
                .process(new QrToTransferenciaTranslator())
                .log("[TRANSFORMADO] TX=${header.idTransaccion} -> ${body}")
                .to("direct:enrutamiento");

        // 5) Enrutamiento (Content-Based Router) --------------------------------------
        from("direct:enrutamiento")
                .routeId("mediador-enrutamiento")
                .choice()
                    .when(simple("${body.merchantAccountInformation.codigoEntidad} == '" + BankCatalog.ITAU + "'"))
                        .to("direct:itau")
                    .when(simple("${body.merchantAccountInformation.codigoEntidad} == '" + BankCatalog.ATLAS + "'"))
                        .to("direct:atlas")
                    .when(simple("${body.merchantAccountInformation.codigoEntidad} == '" + BankCatalog.FAMILIAR + "'"))
                        .to("direct:familiar")
                    .otherwise()
                        .setHeader(Headers.MOTIVO_RECHAZO,
                                simple("Banco destino '${body.merchantAccountInformation.codigoEntidad}' reconocido por el BCP pero sin consumidor simulado en este ejercicio"))
                        .to("direct:rechazo")
                .end();

        // 6) Resultado final -----------------------------------------------------------
        from("direct:resultado")
                .routeId("resultado-final")
                .marshal().json(JsonLibrary.Jackson)
                .log("[RESULTADO] ${body}");
    }
}
