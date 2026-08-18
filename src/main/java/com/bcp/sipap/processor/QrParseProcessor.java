package com.bcp.sipap.processor;

import com.bcp.sipap.parser.QrTlvParser;
import com.bcp.sipap.util.Headers;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.Map;

/**
 * Interpreta la cadena QR TLV recibida en el body y deja el mapa de tags
 * (nivel superior) disponible como propiedad de Exchange para los siguientes
 * pasos del pipeline (Pipes and Filters).
 * <p>
 * Si la cadena está mal formada lanza {@link com.bcp.sipap.parser.QrParseException},
 * que el mediador captura para enviarla al Dead Letter Channel (direct:dead-letter).
 */
public class QrParseProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {
        String qr = exchange.getIn().getBody(String.class);
        Map<String, String> tags = QrTlvParser.parse(qr);
        exchange.setProperty(Headers.PROP_QR_TAGS, tags);
    }
}
