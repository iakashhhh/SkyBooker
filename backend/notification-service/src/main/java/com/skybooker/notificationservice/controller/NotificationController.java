package com.skybooker.notificationservice.controller;

import com.skybooker.notificationservice.dto.NotificationEvent;
import com.skybooker.notificationservice.dto.NotificationResponse;
import com.skybooker.notificationservice.dto.SupportInquiryRequest;
import com.skybooker.notificationservice.dto.SupportInquiryResponse;
import com.skybooker.notificationservice.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/publish")
    public NotificationResponse publish(@Valid @RequestBody NotificationEvent event) {
        return notificationService.processEvent(event);
    }

    @PostMapping("/support")
    public ResponseEntity<SupportInquiryResponse> submitSupportInquiry(@Valid @RequestBody SupportInquiryRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(notificationService.submitSupportInquiry(request));
    }

    @GetMapping("/user/{userId}")
    public List<NotificationResponse> getByUser(@PathVariable("userId") Long userId) {
        validatePositive(userId, "userId");
        return notificationService.getByRecipient(userId);
    }

    @GetMapping("/unread/{userId}")
    public List<NotificationResponse> getUnreadByUser(@PathVariable("userId") Long userId) {
        validatePositive(userId, "userId");
        return notificationService.getUnreadByRecipient(userId);
    }

    @PutMapping("/read/{id}")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable("id") Long notificationId,
                                                         @RequestHeader(value = "X-User-Id", required = false) Long requesterId) {
        return ResponseEntity.ok(notificationService.markAsRead(notificationId, requesterId));
    }

    @GetMapping("/recent")
    public List<NotificationResponse> getRecent(@RequestParam("userId") Long recipientId,
                                                @RequestParam(value = "limit", defaultValue = "5") int limit) {
        validatePositive(recipientId, "userId");
        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be greater than zero");
        }
        return notificationService.getRecentByRecipient(recipientId, limit);
    }

    private void validatePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be greater than zero");
        }
    }
}
