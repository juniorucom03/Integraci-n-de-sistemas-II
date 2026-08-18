package com.bcp.sipap.routes;

import com.bcp.sipap.processor.ProcesarTransferenciaProcessor;
import com.bcp.sipap.util.BankCatalog;
import org.apache.camel.builder.RouteBuilder;

/**
 * Consumidores bancarios simulados. Cada uno escucha su propio canal interno
 * (Message Channel) y solo trabaja con el modelo canónico {@code Transferencia};
 * nunca vuelven a interpretar la cadena QR original.
 */
public class ConsumidorRoute extends RouteBuilder {

    @Override
    public void configure() {

        from("direct:itau")
                .routeId("consumidor-itau")
                .log("[ITAU] Transferencia recibida: ${body}")
                .process(new ProcesarTransferenciaProcessor(BankCatalog.nombre(BankCatalog.ITAU)))
                .to("direct:resultado");

        from("direct:atlas")
                .routeId("consumidor-atlas")
                .log("[ATLAS] Transferencia recibida: ${body}")
                .process(new ProcesarTransferenciaProcessor(BankCatalog.nombre(BankCatalog.ATLAS)))
                .to("direct:resultado");

        from("direct:familiar")
                .routeId("consumidor-familiar")
                .log("[FAMILIAR] Transferencia recibida: ${body}")
                .process(new ProcesarTransferenciaProcessor(BankCatalog.nombre(BankCatalog.FAMILIAR)))
                .to("direct:resultado");
    }
}
