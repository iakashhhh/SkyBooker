package com.skybooker.adminserver.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * This controller provides a simple admin status endpoint.
 * It helps verify that admin-server starts correctly and is routable.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminHealthController {

    /**
     * Returns a lightweight status response for basic infrastructure checks.
     */
    @GetMapping("/status")
    public Map<String, String> getStatus() {
        return Map.of("service", "admin-server", "status", "UP");
    }
}
