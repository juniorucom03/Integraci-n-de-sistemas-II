package com.bcp.sipap.processor;

import com.bcp.sipap.model.MerchantAccountInformation;
import com.bcp.sipap.model.Transferencia;
import com.bcp.sipap.parser.QrTlvParser;
import com.bcp.sipap.util.Headers;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.Map;

/**
 * Message Translator: convierte el mapa de tags TLV (ya validado) al modelo
 * canónico {@link Transferencia}, que es lo único que verán los consumidores
 * bancarios. Este processor asume que la validación ya fue aplicada antes
 * (Pipes and Filters).
 */
public class QrToTransferenciaTranslator implements Processor {

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) {
        Map<String, String> tags = (Map<String, String>) exchange.getProperty(Headers.PROP_QR_TAGS);
        Map<String, String> subTags = QrTlvParser.parse(tags.get("32"));

        MerchantAccountInformation mai = new MerchantAccountInformation(
                subTags.get("00"),
                subTags.get("01"),
                subTags.get("02")
        );

        Transferencia transferencia = new Transferencia();
        transferencia.setIdTransaccion(exchange.getIn().getHeader(Headers.ID_TRANSACCION, String.class));
        transferencia.setPayloadFormatIndicator(tags.get("00"));
        transferencia.setPointOfInitiationMethod(tags.get("01").equals("11") ? "11" : "12");
        transferencia.setMerchantAccountInformation(mai);
        transferencia.setMerchantCategoryCode(tags.get("52"));
        transferencia.setTransactionCurrency(tags.get("53"));

        String monto = tags.get("54");
        transferencia.setTransactionAmount(monto == null || monto.isEmpty() ? null : Integer.valueOf(monto));

        transferencia.setCountryCode(tags.get("58"));
        transferencia.setMerchantName(tags.get("59"));
        transferencia.setMerchantCity(tags.get("60"));
        transferencia.setCrc(tags.get("63"));

        exchange.getIn().setBody(transferencia);
    }
}
