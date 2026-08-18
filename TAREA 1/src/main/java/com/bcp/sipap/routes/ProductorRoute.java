package com.bcp.sipap.routes;

import com.bcp.sipap.util.Headers;
import com.bcp.sipap.util.QrSampleGenerator;
import com.bcp.sipap.util.TransaccionIdGenerator;
import org.apache.camel.builder.RouteBuilder;

/**
 * Dos productores independientes (Message Channel: ambos publican en el
 * mismo canal interno direct:sipap-in) que simulan distintos orígenes de
 * transferencias QR: una app móvil de un banco y una billetera electrónica.
 * <p>
 * Cada uno genera periódicamente (timer:) una cadena QR de prueba —mezclando
 * casos válidos e inválidos— y la envía al mediador junto con un identificador
 * de transacción único (Correlation Identifier).
 */
public class ProductorRoute extends RouteBuilder {

    @Override
    public void configure() {

        from("timer:productor-app-movil?period=4000&delay=1000")
                .routeId("productor-app-movil")
                .setHeader(Headers.PRODUCTOR_ORIGEN, constant("app-movil-banco"))
                .setHeader(Headers.ID_TRANSACCION, method(TransaccionIdGenerator.class, "siguiente"))
                .setBody(method(QrSampleGenerator.class, "aleatoria"))
                .log("[PRODUCTOR app-movil] TX=${header.idTransaccion} QR=${body}")
                .to("direct:sipap-in");

        from("timer:productor-billetera?period=6000&delay=2000")
                .routeId("productor-billetera")
                .setHeader(Headers.PRODUCTOR_ORIGEN, constant("billetera-electronica"))
                .setHeader(Headers.ID_TRANSACCION, method(TransaccionIdGenerator.class, "siguiente"))
                .setBody(method(QrSampleGenerator.class, "aleatoria"))
                .log("[PRODUCTOR billetera] TX=${header.idTransaccion} QR=${body}")
                .to("direct:sipap-in");
    }
}
