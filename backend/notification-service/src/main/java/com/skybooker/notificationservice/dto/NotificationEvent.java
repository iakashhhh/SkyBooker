package com.skybooker.notificationservice.dto;

import com.skybooker.notificationservice.entity.NotificationChannel;
import com.skybooker.notificationservice.entity.NotificationType;

import java.util.List;

public class NotificationEvent {
    private Long recipientId;
    private String eventName;
    private NotificationType type;
    private String title;
    private String message;
    private NotificationChannel channel;
    private List<NotificationChannel> channels;
    private String recipientEmail;
    private String recipientPhone;
    private String pnrCode;
    private String flightNumber;
    private String routeFrom;
    private String routeTo;
    private String departureDate;
    private Integer delayMinutes;
    private String relatedBookingId;
    private Long relatedFlightId;

    public Long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
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

    public List<NotificationChannel> getChannels() {
        return channels;
    }

    public void setChannels(List<NotificationChannel> channels) {
        this.channels = channels;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
    }

    public String getPnrCode() {
        return pnrCode;
    }

    public void setPnrCode(String pnrCode) {
        this.pnrCode = pnrCode;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

    public String getRouteFrom() {
        return routeFrom;
    }

    public void setRouteFrom(String routeFrom) {
        this.routeFrom = routeFrom;
    }

    public String getRouteTo() {
        return routeTo;
    }

    public void setRouteTo(String routeTo) {
        this.routeTo = routeTo;
    }

    public String getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(String departureDate) {
        this.departureDate = departureDate;
    }

    public Integer getDelayMinutes() {
        return delayMinutes;
    }

    public void setDelayMinutes(Integer delayMinutes) {
        this.delayMinutes = delayMinutes;
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
}
