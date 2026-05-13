package com.skybooker.notificationservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class RabbitMessagingConfigTest {

    @Test
    void shouldCreateMessageConverterAndListenerFactory() {
        RabbitMessagingConfig config = new RabbitMessagingConfig();
        MessageConverter converter = config.messageConverter();
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

        SimpleRabbitListenerContainerFactory factory =
            config.rabbitListenerContainerFactory(connectionFactory, converter);

        assertNotNull(converter);
        assertNotNull(factory);
    }
}
