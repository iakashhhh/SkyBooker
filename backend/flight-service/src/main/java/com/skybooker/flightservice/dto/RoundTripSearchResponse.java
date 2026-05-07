package com.skybooker.flightservice.dto;

import java.util.List;

/**
 * This DTO groups outbound and return results for round-trip searches.
 * It mirrors common UX patterns used by travel booking platforms.
 */
public class RoundTripSearchResponse {

    private List<FlightResponse> outboundFlights;
    private List<FlightResponse> returnFlights;

    public RoundTripSearchResponse(List<FlightResponse> outboundFlights, List<FlightResponse> returnFlights) {
        this.outboundFlights = outboundFlights;
        this.returnFlights = returnFlights;
    }

    public List<FlightResponse> getOutboundFlights() {
        return outboundFlights;
    }

    public void setOutboundFlights(List<FlightResponse> outboundFlights) {
        this.outboundFlights = outboundFlights;
    }

    public List<FlightResponse> getReturnFlights() {
        return returnFlights;
    }

    public void setReturnFlights(List<FlightResponse> returnFlights) {
        this.returnFlights = returnFlights;
    }
}
