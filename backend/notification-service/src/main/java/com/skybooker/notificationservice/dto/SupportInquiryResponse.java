package com.skybooker.notificationservice.dto;

public class SupportInquiryResponse {

    private String message;
    private String ticketRef;

    public SupportInquiryResponse() {
    }

    public SupportInquiryResponse(String message, String ticketRef) {
        this.message = message;
        this.ticketRef = ticketRef;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTicketRef() {
        return ticketRef;
    }

    public void setTicketRef(String ticketRef) {
        this.ticketRef = ticketRef;
    }
}
