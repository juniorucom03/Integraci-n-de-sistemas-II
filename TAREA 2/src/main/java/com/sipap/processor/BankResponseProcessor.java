package com.sipap.processor;

import com.sipap.model.CanonicalTransfer;
import com.sipap.model.EstadoTransferencia;
import com.sipap.model.TransferResponse;
import com.sipap.util.MessageStore;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Procesa la respuesta HTTP del banco mock (Request-Reply) y genera el
 * resultado final de la transacción. También persiste en el Message Store
 * para trazabilidad/auditoría.
 */
@Component
public class BankResponseProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(BankResponseProcessor.class);

    @Autowired
    private MessageStore messageStore;

    @Override
    public void process(Exchange exchange) {
        CanonicalTransfer transfer = exchange.getProperty("canonicalTransfer", CanonicalTransfer.class);
        Message in = exchange.getIn();
        Integer httpStatus = in.getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);

        TransferResponse resultado;
        if (httpStatus != null && httpStatus >= 200 && httpStatus < 300) {
            resultado = new TransferResponse(transfer.getIdTransaccion(), EstadoTransferencia.PROCESADA,
                    "Transferencia procesada y aceptada por el banco destino");
        } else {
            resultado = new TransferResponse(transfer.getIdTransaccion(), EstadoTransferencia.ERROR_BANCO,
                    "El banco destino rechazó o falló al procesar la transferencia (HTTP " + httpStatus + ")");
        }

        LOG.info("Respuesta del banco mock id={} httpStatus={} estadoFinal={}",
                transfer.getIdTransaccion(), httpStatus, resultado.getEstado());

        messageStore.guardar(transfer.getIdTransaccion(), transfer, resultado.getEstado());
        exchange.getIn().setBody(resultado);
    }
}
