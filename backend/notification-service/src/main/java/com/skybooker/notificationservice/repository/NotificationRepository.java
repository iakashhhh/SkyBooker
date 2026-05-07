package com.skybooker.notificationservice.repository;

import com.skybooker.notificationservice.entity.Notification;
import com.skybooker.notificationservice.entity.NotificationType;
import com.skybooker.notificationservice.entity.NotificationChannel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdAndChannelOrderByCreatedAtDesc(Long recipientId, NotificationChannel channel);

    List<Notification> findByRecipientIdAndChannelAndIsReadFalseOrderByCreatedAtDesc(Long recipientId, NotificationChannel channel);

    List<Notification> findByRecipientIdAndChannelOrderByCreatedAtDesc(Long recipientId, NotificationChannel channel, Pageable pageable);

    List<Notification> findByTypeAndCreatedAtAfter(NotificationType type, LocalDateTime threshold);
}
