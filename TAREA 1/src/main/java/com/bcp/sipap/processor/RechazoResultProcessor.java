package com.bcp.sipap.processor;

import com.bcp.sipap.model.ResultadoTransferencia;
import com.bcp.sipap.util.Headers;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Construye el resultado RECHAZADA a partir del motivo registrado en el
 * header {@link Headers#MOTIVO_RECHAZO}. Se usa tanto para rechazos de
 * negocio (validación) como para errores técnicos de parseo (dead letter).
 */
public class RechazoResultProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {
        String idTransaccion = exchange.getIn().getHeader(Headers.ID_TRANSACCION, String.class);
        String motivo = exchange.getIn().getHeader(Headers.MOTIVO_RECHAZO, String.class);
        if (motivo == null || motivo.isEmpty()) {
            motivo = "Rechazada por motivo no especificado";
        }
        ResultadoTransferencia resultado = ResultadoTransferencia.rechazada(idTransaccion, motivo);
        exchange.getIn().setBody(resultado);
    }
}
