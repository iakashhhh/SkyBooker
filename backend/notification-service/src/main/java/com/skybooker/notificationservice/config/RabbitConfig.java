package com.skybooker.notificationservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange notificationExchange(@Value("${notification.exchange:skybooker.events}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue bookingConfirmedQueue(@Value("${notification.queues.booking-confirmed}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue paymentSuccessQueue(@Value("${notification.queues.payment-success}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue paymentFailedQueue(@Value("${notification.queues.payment-failed}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue flightUpdatedQueue(@Value("${notification.queues.flight-updated}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue flightDelayedQueue(@Value("${notification.queues.flight-delayed}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue flightCancelledQueue(@Value("${notification.queues.flight-cancelled}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue bookingCancelledQueue(@Value("${notification.queues.booking-cancelled}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue checkinReminderQueue(@Value("${notification.queues.checkin-reminder}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Queue boardingReminderQueue(@Value("${notification.queues.boarding-reminder}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding bookingConfirmedBinding(TopicExchange notificationExchange,
                                           Queue bookingConfirmedQueue,
                                           @Value("${notification.routing.booking-confirmed:booking.confirmed}") String routingKey) {
        return BindingBuilder.bind(bookingConfirmedQueue).to(notificationExchange).with(routingKey);
    }

    @Bean
    public Binding paymentSuccessBinding(TopicExchange notificationExchange,
                                         Queue paymentSuccessQueue,
                                         @Value("${notification.routing.payment-success:payment.success}") String routingKey) {
        return BindingBuilder.bind(paymentSuccessQueue).to(notificationExchange).with(routingKey);
    }

    @Bean
    public Binding paymentFailedBinding(TopicExchange notificationExchange,
                                        Queue paymentFailedQueue,
                                        @Value("${notification.routing.payment-failed:payment.failed}") String routingKey) {
        return BindingBuilder.bind(paymentFailedQueue).to(notificationExchange).with(routingKey);
    }

    @Bean
    public Binding flightUpdatedBinding(TopicExchange notificationExchange,
                                        Queue flightUpdatedQueue,
                                        @Value("${notification.routing.flight-updated:flight.updated}") String routingKey) {
        return BindingBuilder.bind(flightUpdatedQueue).to(notificationExchange).with(routingKey);
    }

    @Bean
    public Binding flightDelayedBinding(TopicExchange notificationExchange,
                                        Queue flightDelayedQueue,
                                        @Value("${notification.routing.flight-delayed:flight.delayed}") String routingKey) {
        return BindingBuilder.bind(flightDelayedQueue).to(notificationExchange).with(routingKey);
    }

    @Bean
    public Binding flightCancelledBinding(TopicExchange notificationExchange,
                                          Queue flightCancelledQueue,
                                          @Value("${notification.routing.flight-cancelled:flight.cancelled}") String routingKey) {
        return BindingBuilder.bind(flightCancelledQueue).to(notificationExchange).with(routingKey);
    }

    @Bean
    public Binding bookingCancelledBinding(TopicExchange notificationExchange,
                                           Queue bookingCancelledQueue,
                                           @Value("${notification.routing.booking-cancelled:booking.cancelled}") String routingKey) {
        return BindingBuilder.bind(bookingCancelledQueue).to(notificationExchange).with(routingKey);
    }

    @Bean
    public Binding checkinReminderBinding(TopicExchange notificationExchange,
                                          Queue checkinReminderQueue,
                                          @Value("${notification.routing.checkin-reminder:checkin.reminder}") String routingKey) {
        return BindingBuilder.bind(checkinReminderQueue).to(notificationExchange).with(routingKey);
    }

    @Bean
    public Binding boardingReminderBinding(TopicExchange notificationExchange,
                                           Queue boardingReminderQueue,
                                           @Value("${notification.routing.boarding-reminder:boarding.reminder}") String routingKey) {
        return BindingBuilder.bind(boardingReminderQueue).to(notificationExchange).with(routingKey);
    }
}
