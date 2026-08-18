package com.bcp.sipap.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Códigos internos de enrutamiento (asignados por el BCP) usados para validar
 * y generar cadenas QR de prueba. Coincide con la tabla de la consigna.
 */
public final class BankCatalog {

    public static final String ITAU = "0015";
    public static final String CONTINENTAL = "0017";
    public static final String SUDAMERIS = "0021";
    public static final String ATLAS = "0007";
    public static final String GNB = "0014";
    public static final String UENO = "0024";
    public static final String FAMILIAR = "0020";
    public static final String BILLETERAS_EMPES = "0900";

    private static final Map<String, String> CODIGO_A_NOMBRE = new LinkedHashMap<>();

    static {
        CODIGO_A_NOMBRE.put(ITAU, "Banco Itaú Paraguay");
        CODIGO_A_NOMBRE.put(CONTINENTAL, "Banco Continental");
        CODIGO_A_NOMBRE.put(SUDAMERIS, "Sudameris Banco");
        CODIGO_A_NOMBRE.put(ATLAS, "Banco Atlas");
        CODIGO_A_NOMBRE.put(GNB, "Banco GNB Paraguay");
        CODIGO_A_NOMBRE.put(UENO, "ueno bank");
        CODIGO_A_NOMBRE.put(FAMILIAR, "Banco Familiar");
        CODIGO_A_NOMBRE.put(BILLETERAS_EMPES, "Billeteras / EMPEs");
    }

    private BankCatalog() {
    }

    public static Map<String, String> catalogo() {
        return Collections.unmodifiableMap(CODIGO_A_NOMBRE);
    }

    public static boolean esCodigoReconocido(String codigo) {
        return codigo != null && CODIGO_A_NOMBRE.containsKey(codigo);
    }

    public static String nombre(String codigo) {
        return CODIGO_A_NOMBRE.getOrDefault(codigo, "Desconocido");
    }

    /** Bancos que sí tienen un consumidor simulado en este ejercicio. */
    public static boolean tieneConsumidorSimulado(String codigo) {
        return ITAU.equals(codigo) || ATLAS.equals(codigo) || FAMILIAR.equals(codigo);
    }
}
