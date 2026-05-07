package com.skybooker.notificationservice.messaging;

import com.skybooker.notificationservice.dto.NotificationEvent;
import com.skybooker.notificationservice.entity.NotificationChannel;
import com.skybooker.notificationservice.entity.NotificationType;
import com.skybooker.notificationservice.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class NotificationEventListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "${notification.queues.booking-confirmed}")
    public void onBookingConfirmed(Map<String, Object> payload,
                                   @Header(value = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        consume(payload, routingKey, "BOOKING_CONFIRMED", NotificationType.BOOKING);
    }

    @RabbitListener(queues = "${notification.queues.payment-success}")
    public void onPaymentSuccess(Map<String, Object> payload,
                                 @Header(value = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        consume(payload, routingKey, "PAYMENT_SUCCESS", NotificationType.PAYMENT);
    }

    @RabbitListener(queues = "${notification.queues.payment-failed}")
    public void onPaymentFailed(Map<String, Object> payload,
                                @Header(value = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        consume(payload, routingKey, "PAYMENT_FAILED", NotificationType.PAYMENT);
    }

    @RabbitListener(queues = "${notification.queues.flight-updated}")
    public void onFlightUpdated(Map<String, Object> payload,
                                @Header(value = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        consume(payload, routingKey, "FLIGHT_UPDATED", NotificationType.FLIGHT);
    }

    @RabbitListener(queues = "${notification.queues.flight-delayed}")
    public void onFlightDelayed(Map<String, Object> payload,
                                @Header(value = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        consume(payload, routingKey, "FLIGHT_DELAYED", NotificationType.FLIGHT);
    }

    @RabbitListener(queues = "${notification.queues.flight-cancelled}")
    public void onFlightCancelled(Map<String, Object> payload,
                                  @Header(value = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        consume(payload, routingKey, "FLIGHT_CANCELLED", NotificationType.FLIGHT);
    }

    @RabbitListener(queues = "${notification.queues.booking-cancelled}")
    public void onBookingCancelled(Map<String, Object> payload,
                                   @Header(value = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        consume(payload, routingKey, "BOOKING_CANCELLED", NotificationType.BOOKING);
    }

    @RabbitListener(queues = "${notification.queues.checkin-reminder}")
    public void onCheckinReminder(Map<String, Object> payload,
                                  @Header(value = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        consume(payload, routingKey, "CHECKIN_REMINDER", NotificationType.REMINDER);
    }

    @RabbitListener(queues = "${notification.queues.boarding-reminder}")
    public void onBoardingReminder(Map<String, Object> payload,
                                   @Header(value = AmqpHeaders.RECEIVED_ROUTING_KEY, required = false) String routingKey) {
        consume(payload, routingKey, "BOARDING_REMINDER", NotificationType.REMINDER);
    }

    private void consume(Map<String, Object> payload,
                         String routingKey,
                         String defaultEventName,
                         NotificationType defaultType) {
        try {
            NotificationEvent event = fromPayload(payload);
            if (isBlank(event.getEventName())) {
                event.setEventName(defaultEventName);
            }
            if (event.getType() == null) {
                event.setType(defaultType);
            }
            if (isBlank(event.getEventName()) && routingKey != null) {
                event.setEventName(routingKey);
            }
            notificationService.processEvent(event);
        } catch (Exception exception) {
            LOGGER.warn("Skipping malformed notification event for routingKey={} reason={}", routingKey, exception.getMessage());
        }
    }

    private NotificationEvent fromPayload(Map<String, Object> payload) {
        NotificationEvent event = new NotificationEvent();
        event.setRecipientId(toLong(payload.get("recipientId")));
        event.setEventName(toString(payload.get("eventName")));
        event.setTitle(toString(payload.get("title")));
        event.setMessage(toString(payload.get("message")));
        event.setRecipientEmail(toString(payload.get("recipientEmail")));
        event.setRecipientPhone(toString(payload.get("recipientPhone")));
        event.setPnrCode(toString(payload.get("pnrCode")));
        event.setFlightNumber(toString(payload.get("flightNumber")));
        event.setRouteFrom(toString(payload.get("routeFrom")));
        event.setRouteTo(toString(payload.get("routeTo")));
        event.setDepartureDate(toString(payload.get("departureDate")));
        event.setDelayMinutes(toInteger(payload.get("delayMinutes")));
        event.setRelatedBookingId(toString(payload.get("relatedBookingId")));
        event.setRelatedFlightId(toLong(payload.get("relatedFlightId")));
        event.setType(toNotificationType(payload.get("type")));
        event.setChannel(toNotificationChannel(payload.get("channel")));
        event.setChannels(toNotificationChannels(payload.get("channels")));
        return event;
    }

    private NotificationType toNotificationType(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return NotificationType.valueOf(String.valueOf(value).trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private NotificationChannel toNotificationChannel(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return NotificationChannel.valueOf(String.valueOf(value).trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private List<NotificationChannel> toNotificationChannels(Object value) {
        if (value == null) {
            return List.of();
        }
        List<NotificationChannel> channels = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                NotificationChannel channel = toNotificationChannel(item);
                if (channel != null) {
                    channels.add(channel);
                }
            }
            return channels;
        }

        String raw = String.valueOf(value);
        for (String part : raw.split(",")) {
            NotificationChannel channel = toNotificationChannel(part);
            if (channel != null) {
                channels.add(channel);
            }
        }
        return channels;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String toString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
