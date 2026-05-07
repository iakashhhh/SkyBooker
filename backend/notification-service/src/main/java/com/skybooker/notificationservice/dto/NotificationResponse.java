package com.skybooker.notificationservice.dto;

import com.skybooker.notificationservice.entity.NotificationChannel;
import com.skybooker.notificationservice.entity.NotificationStatus;
import com.skybooker.notificationservice.entity.NotificationType;

import java.time.LocalDateTime;

public class NotificationResponse {
    private Long notificationId;
    private Long recipientId;
    private NotificationType type;
    private String title;
    private String message;
    private NotificationChannel channel;
    private String relatedBookingId;
    private Long relatedFlightId;
    private NotificationStatus status;
    private boolean read;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;

    public Long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(Long notificationId) {
        this.notificationId = notificationId;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(NotificationChannel channel) {
        this.channel = channel;
    }

    public String getRelatedBookingId() {
        return relatedBookingId;
    }

    public void setRelatedBookingId(String relatedBookingId) {
        this.relatedBookingId = relatedBookingId;
    }

    public Long getRelatedFlightId() {
        return relatedFlightId;
    }

    public void setRelatedFlightId(Long relatedFlightId) {
        this.relatedFlightId = relatedFlightId;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }
}
