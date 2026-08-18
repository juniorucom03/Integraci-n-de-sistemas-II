package com.sipap.processor;

import com.sipap.model.EstadoTransferencia;
import com.sipap.model.TransferResponse;
import com.sipap.validation.DateRule;
import com.sipap.validation.IdempotencyStore;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.jms.Message;

/**
 * Requerimiento funcional 4: validación de fecha en el consumidor +
 * patrón EIP Idempotent Receiver.
 *
 * Deja en el Exchange la propiedad "detenerFlujo" = true cuando la
 * transferencia debe rechazarse (duplicada o fecha inválida), en cuyo
 * caso el consumidor NO debe invocar al banco mock.
 */
@Component
public class DateAndIdempotencyProcessor implements Processor {

    @Autowired
    private IdempotencyStore idempotencyStore;

    @Override
    public void process(Exchange exchange) throws Exception {
        var transfer = exchange.getIn().getBody(com.sipap.model.CanonicalTransfer.class);
        String idTransaccion = transfer.getIdTransaccion();
        exchange.getIn().setHeader("idTransaccion", idTransaccion); // se conserva la correlación

        // Idempotent Receiver: si ya se vio este id_transaccion, se descarta sin
        // reprocesar (no se invoca de nuevo al banco mock).
        boolean esNuevo = idempotencyStore.registrarSiEsNuevo(idTransaccion);
        if (!esNuevo) {
            log(exchange, "DUPLICADA: id=" + idTransaccion + " ya fue procesado anteriormente, se descarta.");
            TransferResponse resp = new TransferResponse(idTransaccion, EstadoTransferencia.DUPLICADA,
                    "Transferencia duplicada, ya fue procesada previamente");
            exchange.getIn().setBody(resp);
            exchange.setProperty("detenerFlujo", Boolean.TRUE);
            return;
        }

        // Validación de fecha: debe coincidir con la fecha actual de ejecución
        // en la zona horaria documentada (America/Asuncion).
        boolean fechaValida = DateRule.esFechaActual(transfer.getFechaTransaccion());
        log(exchange, "Validación de fecha id=" + idTransaccion + " fechaTransaccion="
                + transfer.getFechaTransaccion() + " valido=" + fechaValida);

        if (!fechaValida) {
            TransferResponse resp = new TransferResponse(idTransaccion, EstadoTransferencia.RECHAZADA_FECHA,
                    "La fecha de la transacción no coincide con la fecha actual de ejecución");
            exchange.getIn().setBody(resp);
            exchange.setProperty("detenerFlujo", Boolean.TRUE);
            return;
        }

        exchange.setProperty("canonicalTransfer", transfer);
        exchange.setProperty("detenerFlujo", Boolean.FALSE);
    }

    private void log(Exchange exchange, String msg) {
        org.slf4j.LoggerFactory.getLogger(DateAndIdempotencyProcessor.class).info(msg);
    }
}
