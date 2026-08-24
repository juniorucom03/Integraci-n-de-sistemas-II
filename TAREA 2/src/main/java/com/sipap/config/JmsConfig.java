package com.sipap.config;

import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.camel.component.jms.JmsComponent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.jms.ConnectionFactory;

@Configuration
public class JmsConfig {

    @Value("${sipap.artemis.broker-url:tcp://localhost:61616}")
    private String brokerUrl;

    @Value("${sipap.artemis.user:artemis}")
    private String user;

    @Value("${sipap.artemis.password:artemis}")
    private String password;

    @Bean
    public ConnectionFactory jmsConnectionFactory() {

        ActiveMQJMSConnectionFactory factory =
                new ActiveMQJMSConnectionFactory(brokerUrl);

        factory.setUser(user);
        factory.setPassword(password);

        return factory;
    }

    @Bean
    public JmsComponent jms(ConnectionFactory jmsConnectionFactory) {

        JmsComponent component = new JmsComponent();

        component.setConnectionFactory(jmsConnectionFactory);

        component.getConfiguration()
                .setConcurrentConsumers(3);

        return component;
    }
}