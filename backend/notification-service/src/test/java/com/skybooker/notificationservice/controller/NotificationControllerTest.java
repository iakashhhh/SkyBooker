package com.skybooker.notificationservice.controller;

import com.skybooker.notificationservice.dto.NotificationEvent;
import com.skybooker.notificationservice.dto.NotificationResponse;
import com.skybooker.notificationservice.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationControllerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationController controller = new NotificationController(notificationService);

    @Test
    void shouldPublishAndReadNotifications() {
        NotificationEvent event = new NotificationEvent();
        event.setRecipientId(7L);

        NotificationResponse response = new NotificationResponse();
        response.setNotificationId(11L);

        when(notificationService.processEvent(event)).thenReturn(response);
        when(notificationService.getByRecipient(7L)).thenReturn(List.of(response));
        when(notificationService.getUnreadByRecipient(7L)).thenReturn(List.of(response));
        when(notificationService.markAsRead(11L, 7L)).thenReturn(response);
        when(notificationService.getRecentByRecipient(7L, 3)).thenReturn(List.of(response));

        assertSame(response, controller.publish(event));
        assertEquals(1, controller.getByUser(7L).size());
        assertEquals(1, controller.getUnreadByUser(7L).size());
        assertSame(response, controller.markRead(11L, 7L).getBody());
        assertEquals(1, controller.getRecent(7L, 3).size());

        verify(notificationService).processEvent(event);
        verify(notificationService).getByRecipient(7L);
        verify(notificationService).getUnreadByRecipient(7L);
        verify(notificationService).markAsRead(11L, 7L);
        verify(notificationService).getRecentByRecipient(7L, 3);
    }

    @Test
    void shouldRejectInvalidUserAndLimitInputs() {
        assertThrows(ResponseStatusException.class, () -> controller.getByUser(0L));
        assertThrows(ResponseStatusException.class, () -> controller.getUnreadByUser(null));
        assertThrows(ResponseStatusException.class, () -> controller.getRecent(7L, 0));
    }
}
