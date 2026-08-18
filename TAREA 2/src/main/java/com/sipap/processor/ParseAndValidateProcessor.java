package com.sipap.processor;

import com.sipap.model.CanonicalTransfer;
import com.sipap.model.EstadoTransferencia;
import com.sipap.model.TransferRequest;
import com.sipap.model.TransferResponse;
import com.sipap.parser.QrTlvParser;
import com.sipap.validation.AmountRule;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Procesador de entrada de la API: parsea el QR al modelo canónico y aplica
 * la regla de monto ANTES de publicar en Artemis (requisito funcional 2).
 *
 * Deja en las propiedades del Exchange:
 *  - "canonicalTransfer": CanonicalTransfer (si el parseo fue correcto)
 *  - "rechazoMonto": boolean
 *  - header "idTransaccion" (Correlation Identifier, se propaga desde acá)
 */
@Component
public class ParseAndValidateProcessor implements Processor {

    private final QrTlvParser parser = new QrTlvParser();
    private static final DateTimeFormatter FECHA_FORMATO = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public void process(Exchange exchange) {
        TransferRequest request = exchange.getIn().getBody(TransferRequest.class);

        if (request == null || isBlank(request.getIdTransaccion()) || isBlank(request.getQr())
                || isBlank(request.getFechaTransaccion())) {
            fallar(exchange, request, "Petición inválida: faltan campos obligatorios (id_transaccion, fecha_transaccion, qr)");
            return;
        }

        // Correlation Identifier: se fija aquí y se propaga en headers/logs/mock/resultado final
        exchange.getIn().setHeader("idTransaccion", request.getIdTransaccion());

        LocalDate fecha;
        try {
            fecha = LocalDate.parse(request.getFechaTransaccion(), FECHA_FORMATO);
        } catch (Exception e) {
            fallar(exchange, request, "fecha_transaccion inválida, formato esperado yyyy-MM-dd");
            return;
        }

        BigDecimal montoApi = null;
        if (request.getMonto() != null && !request.getMonto().isBlank()) {
            try {
                montoApi = new BigDecimal(request.getMonto());
            } catch (NumberFormatException e) {
                fallar(exchange, request, "monto inválido");
                return;
            }
        }

        CanonicalTransfer transfer;
        try {
            transfer = parser.parseToCanonical(request.getQr(), request.getIdTransaccion(), fecha, montoApi);
        } catch (QrTlvParser.QrParseException e) {
            fallar(exchange, request, "QR inválido: " + e.getMessage());
            return;
        }

        // Regla de negocio: control de monto ANTES de publicar en el broker
        if (AmountRule.superaMaximo(transfer.getMonto())) {
            TransferResponse response = new TransferResponse(
                    transfer.getIdTransaccion(),
                    EstadoTransferencia.RECHAZADA_MONTO,
                    AmountRule.MENSAJE_RECHAZO);
            exchange.getIn().setBody(response);
            exchange.setProperty("rechazado", Boolean.TRUE);
            return;
        }

        exchange.setProperty("canonicalTransfer", transfer);
        exchange.getIn().setHeader("entidadFinancieraDestino", transfer.getEntidadFinancieraDestino());
        exchange.setProperty("rechazado", Boolean.FALSE);
    }

    private void fallar(Exchange exchange, TransferRequest request, String motivo) {
        String id = request != null ? request.getIdTransaccion() : null;
        TransferResponse response = new TransferResponse(id, EstadoTransferencia.RECHAZADA, motivo);
        exchange.getIn().setBody(response);
        exchange.setProperty("rechazado", Boolean.TRUE);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
