package com.skybooker.notificationservice.messaging;

import com.skybooker.notificationservice.dto.NotificationEvent;
import com.skybooker.notificationservice.entity.NotificationChannel;
import com.skybooker.notificationservice.entity.NotificationType;
import com.skybooker.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    void onPaymentSuccessShouldApplyDefaultsAndMapChannels() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", "42");
        payload.put("eventName", " ");
        payload.put("title", "Payment Received");
        payload.put("message", "Your payment is successful");
        payload.put("channels", List.of("app", "sms", "invalid"));
        payload.put("relatedFlightId", "901");

        listener.onPaymentSuccess(payload, "payment.success");

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationService).processEvent(captor.capture());

        NotificationEvent event = captor.getValue();
        assertEquals(42L, event.getRecipientId());
        assertEquals("PAYMENT_SUCCESS", event.getEventName());
        assertEquals(NotificationType.PAYMENT, event.getType());
        assertEquals(901L, event.getRelatedFlightId());
        assertEquals(List.of(NotificationChannel.APP, NotificationChannel.SMS), event.getChannels());
    }

    @Test
    void onFlightCancelledShouldPreserveExplicitEventNameAndType() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", 88L);
        payload.put("eventName", "CUSTOM_EVENT");
        payload.put("type", "BOOKING");
        payload.put("channel", "email");
        payload.put("relatedBookingId", "BKG-77");

        listener.onFlightCancelled(payload, "flight.cancelled");

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationService).processEvent(captor.capture());

        NotificationEvent event = captor.getValue();
        assertEquals("CUSTOM_EVENT", event.getEventName());
        assertEquals(NotificationType.BOOKING, event.getType());
        assertEquals(NotificationChannel.EMAIL, event.getChannel());
        assertEquals("BKG-77", event.getRelatedBookingId());
    }

    @Test
    void onBookingConfirmedShouldSkipMalformedPayload() {
        listener.onBookingConfirmed(null, "booking.confirmed");

        verify(notificationService, never()).processEvent(org.mockito.ArgumentMatchers.any(NotificationEvent.class));
    }

    @Test
    void onBoardingReminderShouldSetNullTypeWhenValueIsUnknown() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("recipientId", "51");
        payload.put("type", "unknown");
        payload.put("channels", "app,email");

        listener.onBoardingReminder(payload, "boarding.reminder");

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationService).processEvent(captor.capture());

        NotificationEvent event = captor.getValue();
        assertEquals("BOARDING_REMINDER", event.getEventName());
        assertEquals(NotificationType.REMINDER, event.getType());
        assertNull(event.getChannel());
        assertEquals(List.of(NotificationChannel.APP, NotificationChannel.EMAIL), event.getChannels());
    }
}
