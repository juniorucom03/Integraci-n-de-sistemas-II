package com.sipap.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Requerimiento funcional 3 + patrones EIP:
 *  - Message Channel: consume del canal externo "sipap.transferencias.entrada"
 *    (Artemis) y reenvía a canales externos individuales por banco.
 *  - Multicast: cada transferencia se envía simultáneamente a la cola del
 *    banco destino Y a una cola de auditoría ("sipap.auditoria"), sin que
 *    un fallo en la auditoría afecte el camino principal.
 *
 * Los nombres de cola de banco siguen el patrón: sipap.banco.<codigoEntidad>
 * Bancos de referencia usados en esta práctica: 0001 (Banco A), 0002 (Banco B),
 * 0003 (Banco C). Documentado también en README.
 */
@Component
public class RoutingToBankQueuesRoute extends RouteBuilder {

    @Override
    public void configure() {

        from("jms:queue:sipap.transferencias.entrada")
                .routeId("enrutamiento-a-colas-banco")
                .log("Enrutando transferencia id=${header.JMSCorrelationID} a entidad=${header.entidadFinancieraDestino}")
                // Multicast: banco destino + auditoría, en paralelo, sin detenerse ante errores de auditoría
                .multicast()
                    .stopOnException("false")
                    .to("direct:enviarAColaBanco", "direct:auditoria")
                .end();

        from("direct:enviarAColaBanco")
                .routeId("multicast-envio-banco")
                .toD("jms:queue:sipap.banco.${header.entidadFinancieraDestino}?exchangePattern=InOnly");

        from("direct:auditoria")
                .routeId("multicast-auditoria")
                .to("jms:queue:sipap.auditoria?exchangePattern=InOnly");
    }
}
