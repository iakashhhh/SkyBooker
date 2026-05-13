package com.skybooker.paymentservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class RabbitMessagingConfigTest {

    @Test
    void shouldCreateRabbitTemplateWithMessageConverter() {
        RabbitMessagingConfig config = new RabbitMessagingConfig();
        MessageConverter converter = config.messageConverter();
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);

        RabbitTemplate template = config.rabbitTemplate(connectionFactory, converter);

        assertNotNull(converter);
        assertSame(converter, template.getMessageConverter());
    }
}
