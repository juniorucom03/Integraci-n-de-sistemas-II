package com.sipap.routes;

import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;

/**
 * Utilidad para levantar un broker Artemis embebido durante las pruebas de
 * integración, evitando depender de Docker en el entorno de CI/local.
 * Permite validar el flujo real (publicación, enrutamiento, consumo) contra
 * un broker de verdad en lugar de mocks del componente JMS.
 */
public class EmbeddedArtemisSupport {

    private final EmbeddedActiveMQ server = new EmbeddedActiveMQ();

    public void start() throws Exception {
        ConfigurationImpl config = new ConfigurationImpl();
        config.setPersistenceEnabled(false);
        config.setSecurityEnabled(false);
        config.addAcceptorConfiguration("in-vm", "vm://0");
        config.addAcceptorConfiguration("tcp", "tcp://localhost:61616");
        config.setAddressSettings(java.util.Map.of("#", new AddressSettings()));
        server.setConfiguration(config);
        server.start();
    }

    public void stop() throws Exception {
        server.stop();
    }
}
