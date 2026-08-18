package com.bcp.sipap.processor;

import com.bcp.sipap.model.ResultadoTransferencia;
import com.bcp.sipap.model.Transferencia;
import com.bcp.sipap.util.Headers;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Simula el procesamiento definitivo de una transferencia válida dentro de
 * un banco destino. Cada consumidor bancario usa una instancia de este
 * processor con su propio nombre de banco.
 */
public class ProcesarTransferenciaProcessor implements Processor {

    private final String nombreBanco;

    public ProcesarTransferenciaProcessor(String nombreBanco) {
        this.nombreBanco = nombreBanco;
    }

    @Override
    public void process(Exchange exchange) {
        Transferencia transferencia = exchange.getIn().getBody(Transferencia.class);
        String idTransaccion = exchange.getIn().getHeader(Headers.ID_TRANSACCION, String.class);

        String mensaje = "Transferencia procesada exitosamente en " + nombreBanco
                + " (cuenta " + transferencia.getMerchantAccountInformation().getNumeroCuenta() + ")";

        ResultadoTransferencia resultado = ResultadoTransferencia.procesada(idTransaccion, mensaje);
        exchange.getIn().setBody(resultado);
    }
}
