package com.bcp.sipap.processor;

import com.bcp.sipap.util.Headers;
import com.bcp.sipap.validation.QrValidator;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.Map;

/**
 * Aplica las reglas de validación de negocio (Message Filter EIP) sobre el
 * mapa de tags ya parseado. Si alguna regla falla, lanza
 * {@link com.bcp.sipap.validation.ValidationException}, que el mediador
 * captura para enviar el mensaje al canal de rechazo (direct:rechazo) sin
 * que llegue a ningún consumidor bancario.
 */
public class QrValidationProcessor implements Processor {

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) {
        Map<String, String> tags = (Map<String, String>) exchange.getProperty(Headers.PROP_QR_TAGS);
        QrValidator.validar(tags);
    }
}
