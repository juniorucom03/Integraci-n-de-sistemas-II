package com.bcp.sipap.util;

import com.bcp.sipap.parser.QrTlvBuilder;

import java.util.List;
import java.util.Random;

/**
 * Genera cadenas QR de prueba (válidas e inválidas) para alimentar los
 * productores basados en timers. Cubre los distintos escenarios exigidos
 * por la consigna: transferencias válidas hacia cada banco simulado, banco
 * desconocido, campo faltante/longitud incorrecta, monto excesivo y
 * checksum inválido.
 */
public final class QrSampleGenerator {

    private static final Random RANDOM = new Random();

    private QrSampleGenerator() {
    }

    private static String base(String pim, String codigoEntidad, String cuenta, String monto, String checksum) {
        String maiBlock = QrTlvBuilder.merchantAccountInformation("py.gov.bcp.sip", codigoEntidad, cuenta);

        QrTlvBuilder b = QrTlvBuilder.nuevo()
                .campo("00", "01")
                .campo("01", pim)
                .crudo(maiBlock)
                .campo("52", "5731")
                .campo("53", "600");

        if (monto != null) {
            b.campo("54", monto);
        }

        b.campo("58", "PY")
         .campo("59", "COMERCIO DE PRUEBA")
         .campo("60", "ASUNCION")
         .campo("63", checksum);

        return b.build();
    }

    /** QR estático válido dirigido a un banco dado. */
    public static String validoEstatico(String codigoEntidad, String cuenta) {
        return base("11", codigoEntidad, cuenta, null, "A1B2");
    }

    /** QR dinámico válido (con monto) dirigido a un banco dado. */
    public static String validoDinamico(String codigoEntidad, String cuenta, int monto) {
        return base("12", codigoEntidad, cuenta, String.valueOf(monto), "A1B2");
    }

    /** Inválido: código de entidad no reconocido por el catálogo del BCP. */
    public static String invalidoBancoDesconocido() {
        return base("11", "9999", "1234567890", null, "A1B2");
    }

    /** Inválido: monto igual o mayor al máximo permitido (10.000.000). */
    public static String invalidoMontoExcesivo(String codigoEntidad, String cuenta) {
        return base("12", codigoEntidad, cuenta, "10000000", "A1B2");
    }

    /** Inválido: checksum distinto del dummy A1B2. */
    public static String invalidoChecksum(String codigoEntidad, String cuenta) {
        return base("11", codigoEntidad, cuenta, null, "9999");
    }

    /**
     * Inválido: longitud declarada del país (58) no coincide con el valor real
     * (se declara longitud 4 pero el valor real tiene 2 caracteres), generando
     * un error de parseo TLV (Dead Letter Channel).
     */
    public static String invalidoLongitudIncorrecta(String codigoEntidad, String cuenta) {
        String maiBlock = QrTlvBuilder.merchantAccountInformation("py.gov.bcp.sip", codigoEntidad, cuenta);
        return QrTlvBuilder.nuevo()
                .campo("00", "01")
                .campo("01", "11")
                .crudo(maiBlock)
                .campo("52", "5731")
                .campo("53", "600")
                .campoConLongitudForzada("58", 4, "PY")
                .campo("59", "COMERCIO DE PRUEBA")
                .campo("60", "ASUNCION")
                .campo("63", "A1B2")
                .build();
    }

    /** Inválido: falta el campo obligatorio Merchant Name (59). */
    public static String invalidoCampoFaltante(String codigoEntidad, String cuenta) {
        String maiBlock = QrTlvBuilder.merchantAccountInformation("py.gov.bcp.sip", codigoEntidad, cuenta);
        return QrTlvBuilder.nuevo()
                .campo("00", "01")
                .campo("01", "11")
                .crudo(maiBlock)
                .campo("52", "5731")
                .campo("53", "600")
                .campo("58", "PY")
                .campo("60", "ASUNCION")
                .campo("63", "A1B2")
                .build();
    }

    private static final List<String> BANCOS_SIMULADOS = List.of(
            BankCatalog.ITAU, BankCatalog.ATLAS, BankCatalog.FAMILIAR);

    /**
     * Devuelve una cadena QR aleatoria, mezclando casos válidos e inválidos,
     * para que los productores generen tráfico variado en cada tick del timer.
     */
    public static String aleatoria() {
        int caso = RANDOM.nextInt(10);
        String banco = BANCOS_SIMULADOS.get(RANDOM.nextInt(BANCOS_SIMULADOS.size()));
        String cuenta = String.valueOf(1000000000L + RANDOM.nextInt(900000000));

        switch (caso) {
            case 0, 1, 2, 3:
                return validoEstatico(banco, cuenta);
            case 4, 5:
                return validoDinamico(banco, cuenta, 5000 + RANDOM.nextInt(500000));
            case 6:
                return invalidoBancoDesconocido();
            case 7:
                return invalidoMontoExcesivo(banco, cuenta);
            case 8:
                return invalidoChecksum(banco, cuenta);
            default:
                return invalidoCampoFaltante(banco, cuenta);
        }
    }
}
