package com.skybooker.bookingservice.dto;

/**
 * This DTO models a small service status response object.
 * It is used to quickly validate service availability.
 */
public class ServiceStatusResponse {

    private String service;
    private String status;

    public ServiceStatusResponse(String service, String status) {
        this.service = service;
        this.status = status;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
