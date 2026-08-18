package com.bcp.sipap.processor;

import com.bcp.sipap.util.Headers;
import com.bcp.sipap.validation.QrValidator;
import com.bcp.sipap.validation.ValidationException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.Map;

/**
 * Ejecuta las reglas de validación de negocio.
 *
 * El resultado de la validación se guarda en una Exchange Property para que
 * la ruta Camel pueda utilizar un Message Filter explícito y decidir si el
 * mensaje continúa hacia la transformación.
 */
public class QrValidationProcessor implements Processor {

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) {

        Map<String, String> tags =
                (Map<String, String>) exchange.getProperty(Headers.PROP_QR_TAGS);

        try {
            QrValidator.validar(tags);

            exchange.setProperty(Headers.PROP_VALIDACION_OK, true);

        } catch (ValidationException e) {

            exchange.setProperty(Headers.PROP_VALIDACION_OK, false);

            exchange.getIn().setHeader(
                    Headers.MOTIVO_RECHAZO,
                    e.getMessage()
            );
        }
    }
}