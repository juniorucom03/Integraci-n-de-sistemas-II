package com.bcp.sipap;

import com.bcp.sipap.routes.ConsumidorRoute;
import com.bcp.sipap.routes.MediadorRoute;
import com.bcp.sipap.routes.ProductorRoute;
import org.apache.camel.main.Main;

/**
 * Punto de entrada de la aplicación. Registra las rutas de productores,
 * mediador y consumidores, y arranca un CamelContext standalone
 * (sin Spring ni ningún broker externo).
 * <p>
 * Ejecución:
 *   mvn compile exec:java -Dexec.mainClass=com.bcp.sipap.MainApp
 *   (o)  mvn camel:run
 *   (o)  mvn package  &amp;&amp;  java -jar target/sipap-camel-integration.jar
 */
public final class MainApp {

    private MainApp() {
    }

    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new ProductorRoute());
        main.configure().addRoutesBuilder(new MediadorRoute());
        main.configure().addRoutesBuilder(new ConsumidorRoute());

        System.out.println("=================================================================");
        System.out.println(" SIPAP QR Integration - Apache Camel");
        System.out.println(" Mediador de transferencias QR (EMVCo / SIP Paraguay) - Practica");
        System.out.println(" Presione Ctrl+C para detener");
        System.out.println("=================================================================");

        main.run(args);
    }
}
