package com.skybooker.notificationservice.service;

import com.skybooker.notificationservice.dto.NotificationEvent;
import com.skybooker.notificationservice.dto.NotificationResponse;

import java.util.List;

public interface NotificationService {

    NotificationResponse processEvent(NotificationEvent event);

    List<NotificationResponse> getByRecipient(Long recipientId);

    List<NotificationResponse> getUnreadByRecipient(Long recipientId);

    NotificationResponse markAsRead(Long notificationId, Long requesterId);

    List<NotificationResponse> getRecentByRecipient(Long recipientId, int limit);
}
