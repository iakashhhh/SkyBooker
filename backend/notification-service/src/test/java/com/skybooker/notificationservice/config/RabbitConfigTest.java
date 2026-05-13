package com.skybooker.notificationservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitConfigTest {

    private final RabbitConfig config = new RabbitConfig();

    @Test
    void shouldCreateDurableExchangeQueuesAndBindings() {
        TopicExchange exchange = config.notificationExchange("skybooker.events");
        Queue bookingConfirmed = config.bookingConfirmedQueue("q.booking.confirmed");
        Queue paymentSuccess = config.paymentSuccessQueue("q.payment.success");
        Queue paymentFailed = config.paymentFailedQueue("q.payment.failed");
        Queue flightUpdated = config.flightUpdatedQueue("q.flight.updated");
        Queue flightDelayed = config.flightDelayedQueue("q.flight.delayed");
        Queue flightCancelled = config.flightCancelledQueue("q.flight.cancelled");
        Queue bookingCancelled = config.bookingCancelledQueue("q.booking.cancelled");
        Queue checkinReminder = config.checkinReminderQueue("q.checkin");
        Queue boardingReminder = config.boardingReminderQueue("q.boarding");

        assertEquals("skybooker.events", exchange.getName());
        assertTrue(exchange.isDurable());
        assertTrue(bookingConfirmed.isDurable());

        Binding bookingBinding = config.bookingConfirmedBinding(exchange, bookingConfirmed, "booking.confirmed");
        Binding paymentSuccessBinding = config.paymentSuccessBinding(exchange, paymentSuccess, "payment.success");
        Binding paymentFailedBinding = config.paymentFailedBinding(exchange, paymentFailed, "payment.failed");
        Binding flightUpdatedBinding = config.flightUpdatedBinding(exchange, flightUpdated, "flight.updated");
        Binding flightDelayedBinding = config.flightDelayedBinding(exchange, flightDelayed, "flight.delayed");
        Binding flightCancelledBinding = config.flightCancelledBinding(exchange, flightCancelled, "flight.cancelled");
        Binding bookingCancelledBinding = config.bookingCancelledBinding(exchange, bookingCancelled, "booking.cancelled");
        Binding checkinBinding = config.checkinReminderBinding(exchange, checkinReminder, "checkin.reminder");
        Binding boardingBinding = config.boardingReminderBinding(exchange, boardingReminder, "boarding.reminder");

        assertEquals("booking.confirmed", bookingBinding.getRoutingKey());
        assertEquals("payment.success", paymentSuccessBinding.getRoutingKey());
        assertEquals("payment.failed", paymentFailedBinding.getRoutingKey());
        assertEquals("flight.updated", flightUpdatedBinding.getRoutingKey());
        assertEquals("flight.delayed", flightDelayedBinding.getRoutingKey());
        assertEquals("flight.cancelled", flightCancelledBinding.getRoutingKey());
        assertEquals("booking.cancelled", bookingCancelledBinding.getRoutingKey());
        assertEquals("checkin.reminder", checkinBinding.getRoutingKey());
        assertEquals("boarding.reminder", boardingBinding.getRoutingKey());
    }
}
