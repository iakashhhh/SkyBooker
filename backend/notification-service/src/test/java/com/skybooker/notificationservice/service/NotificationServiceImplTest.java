package com.skybooker.notificationservice.service;

import com.skybooker.notificationservice.dto.NotificationEvent;
import com.skybooker.notificationservice.dto.NotificationResponse;
import com.skybooker.notificationservice.entity.Notification;
import com.skybooker.notificationservice.entity.NotificationChannel;
import com.skybooker.notificationservice.entity.NotificationStatus;
import com.skybooker.notificationservice.entity.NotificationType;
import com.skybooker.notificationservice.repository.NotificationRepository;
import com.skybooker.notificationservice.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender javaMailSender;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(
            notificationRepository,
            javaMailSender,
            "http://booking",
            "http://auth",
            "http://flight",
            "noreply@skybooker.com",
            "skybooker.local"
        );
    }

    @Test
    void processEvent_shouldStoreAndSendInAppNotification() {
        NotificationEvent event = new NotificationEvent();
        event.setRecipientId(10L);
        event.setEventName("PAYMENT_SUCCESS");
        event.setType(NotificationType.PAYMENT);
        event.setChannel(NotificationChannel.APP);
        event.setRelatedBookingId("BKG-10");

        AtomicLong idSequence = new AtomicLong(1);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getNotificationId() == null) {
                notification.setNotificationId(idSequence.getAndIncrement());
            }
            return notification;
        });

        NotificationResponse response = notificationService.processEvent(event);

        assertEquals(NotificationChannel.APP, response.getChannel());
        assertEquals(NotificationStatus.SENT, response.getStatus());
        assertEquals(NotificationType.PAYMENT, response.getType());
        assertTrue(response.getTitle().contains("Payment"));
        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void processEvent_shouldMarkSmsChannelFailedWhenPhoneMissing() {
        NotificationEvent event = new NotificationEvent();
        event.setRecipientId(11L);
        event.setType(NotificationType.FLIGHT);
        event.setChannels(List.of(NotificationChannel.SMS));
        event.setMessage("Gate updated");
        event.setTitle("Flight update");

        AtomicLong idSequence = new AtomicLong(100);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getNotificationId() == null) {
                notification.setNotificationId(idSequence.getAndIncrement());
            }
            return notification;
        });

        notificationService.processEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(4)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();
        boolean hasFailedSms = saved.stream().anyMatch(notification ->
            notification.getChannel() == NotificationChannel.SMS && notification.getStatus() == NotificationStatus.FAILED
        );
        boolean hasSentApp = saved.stream().anyMatch(notification ->
            notification.getChannel() == NotificationChannel.APP && notification.getStatus() == NotificationStatus.SENT
        );

        assertTrue(hasFailedSms);
        assertTrue(hasSentApp);
    }

    @Test
    void processEvent_shouldRejectNullEvent() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> notificationService.processEvent(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void processEvent_shouldUseDefaultsWhenTypeAndChannelsAreMissing() {
        NotificationEvent event = new NotificationEvent();
        event.setRecipientId(22L);
        event.setEventName("BOOKING_CONFIRMED");
        event.setRelatedBookingId("BKG-22");

        AtomicLong idSequence = new AtomicLong(200);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getNotificationId() == null) {
                notification.setNotificationId(idSequence.getAndIncrement());
            }
            return notification;
        });

        NotificationResponse response = notificationService.processEvent(event);

        assertEquals(NotificationType.BOOKING, response.getType());
        assertTrue(response.getTitle().contains("Booking"));
        verify(notificationRepository, times(6)).save(any(Notification.class));
    }

    @Test
    void processEvent_shouldResolveFlightDelayTitleAndMessage() {
        NotificationEvent event = new NotificationEvent();
        event.setRecipientId(33L);
        event.setEventName("FLIGHT_DELAYED");
        event.setFlightNumber("SB333");
        event.setDelayMinutes(45);
        event.setRouteFrom("DEL");
        event.setRouteTo("BLR");
        event.setChannel(NotificationChannel.APP);

        AtomicLong idSequence = new AtomicLong(500);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getNotificationId() == null) {
                notification.setNotificationId(idSequence.getAndIncrement());
            }
            return notification;
        });

        NotificationResponse response = notificationService.processEvent(event);

        assertEquals(NotificationType.FLIGHT, response.getType());
        assertEquals("Flight Delay Alert", response.getTitle());
        assertTrue(response.getMessage().contains("45 minutes"));
    }

    @Test
    void processEvent_shouldResolveReminderTypeAndMessageFromEventName() {
        NotificationEvent event = new NotificationEvent();
        event.setRecipientId(34L);
        event.setEventName("CHECKIN_REMINDER");
        event.setFlightNumber("SB909");
        event.setDepartureDate("12 May 2026, 08:00 AM");
        event.setChannel(NotificationChannel.APP);

        AtomicLong idSequence = new AtomicLong(600);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getNotificationId() == null) {
                notification.setNotificationId(idSequence.getAndIncrement());
            }
            return notification;
        });

        NotificationResponse response = notificationService.processEvent(event);

        assertEquals(NotificationType.REMINDER, response.getType());
        assertEquals("Check-in Reminder", response.getTitle());
        assertTrue(response.getMessage().contains("Check-in is now open"));
    }

    @Test
    void processEvent_shouldMarkEmailAsFailedWhenRecipientEmailUnavailable() {
        NotificationEvent event = new NotificationEvent();
        event.setRecipientId(55L);
        event.setEventName("PAYMENT_SUCCESS");
        event.setType(NotificationType.PAYMENT);
        event.setChannels(List.of(NotificationChannel.EMAIL));
        event.setRecipientEmail("passenger@skybooker.com");
        event.setRelatedBookingId("BKG-55");

        AtomicLong idSequence = new AtomicLong(700);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getNotificationId() == null) {
                notification.setNotificationId(idSequence.getAndIncrement());
            }
            return notification;
        });

        notificationService.processEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(4)).save(captor.capture());
        boolean emailFailed = captor.getAllValues().stream().anyMatch(notification ->
            notification.getChannel() == NotificationChannel.EMAIL && notification.getStatus() == NotificationStatus.FAILED
        );
        boolean appSent = captor.getAllValues().stream().anyMatch(notification ->
            notification.getChannel() == NotificationChannel.APP && notification.getStatus() == NotificationStatus.SENT
        );
        assertTrue(emailFailed);
        assertTrue(appSent);
    }

    @Test
    void processEvent_shouldSendEmailWithPdfAttachmentWhenRecipientEmailIsValid() {
        NotificationEvent event = new NotificationEvent();
        event.setRecipientId(56L);
        event.setEventName("BOOKING_CONFIRMED");
        event.setChannels(List.of(NotificationChannel.EMAIL));
        event.setRecipientEmail("traveler56@gmail.com");
        event.setRelatedBookingId("BKG-56");
        event.setPnrCode("PNR56");
        event.setFlightNumber("SB-560");
        event.setRouteFrom("DEL");
        event.setRouteTo("GOI");

        when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));

        AtomicLong idSequence = new AtomicLong(800);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getNotificationId() == null) {
                notification.setNotificationId(idSequence.getAndIncrement());
            }
            return notification;
        });

        NotificationResponse response = notificationService.processEvent(event);

        assertEquals(NotificationChannel.APP, response.getChannel());
        verify(javaMailSender).createMimeMessage();
        verify(javaMailSender).send(any(MimeMessage.class));
        verify(notificationRepository, times(4)).save(any(Notification.class));
    }

    @Test
    void processEvent_shouldSendSmsWhenRecipientPhoneAvailable() {
        NotificationEvent event = new NotificationEvent();
        event.setRecipientId(57L);
        event.setEventName("BOOKING_CONFIRMED");
        event.setChannels(List.of(NotificationChannel.SMS));
        event.setRecipientPhone("+911234567890");
        event.setRelatedBookingId("BKG-57");

        AtomicLong idSequence = new AtomicLong(900);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getNotificationId() == null) {
                notification.setNotificationId(idSequence.getAndIncrement());
            }
            return notification;
        });

        notificationService.processEvent(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(4)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(notification ->
            notification.getChannel() == NotificationChannel.SMS && notification.getStatus() == NotificationStatus.SENT
        ));
    }

    @Test
    void processEvent_shouldFallbackToBookingUpdateTitleForUnknownEventNames() {
        NotificationEvent event = new NotificationEvent();
        event.setRecipientId(58L);
        event.setEventName("something_new");
        event.setType(NotificationType.BOOKING);
        event.setChannel(NotificationChannel.APP);

        AtomicLong idSequence = new AtomicLong(1000);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            if (notification.getNotificationId() == null) {
                notification.setNotificationId(idSequence.getAndIncrement());
            }
            return notification;
        });

        NotificationResponse response = notificationService.processEvent(event);

        assertEquals("Booking Update", response.getTitle());
        assertTrue(response.getMessage().contains("Your booking has been updated"));
    }

    @Test
    void getByRecipient_shouldValidateRecipientId() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> notificationService.getByRecipient(0L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void getUnreadByRecipient_shouldValidateRecipientId() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> notificationService.getUnreadByRecipient(-1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void getByRecipient_shouldReturnMappedNotifications() {
        Notification notification = baseNotification(31L, 8L, NotificationChannel.APP);
        when(notificationRepository.findByRecipientIdAndChannelOrderByCreatedAtDesc(8L, NotificationChannel.APP))
            .thenReturn(List.of(notification));

        List<NotificationResponse> responses = notificationService.getByRecipient(8L);

        assertEquals(1, responses.size());
        assertEquals(31L, responses.get(0).getNotificationId());
    }

    @Test
    void getUnreadByRecipient_shouldReturnOnlyUnreadAppNotifications() {
        Notification unread = baseNotification(32L, 8L, NotificationChannel.APP);
        when(notificationRepository.findByRecipientIdAndChannelAndIsReadFalseOrderByCreatedAtDesc(8L, NotificationChannel.APP))
            .thenReturn(List.of(unread));

        List<NotificationResponse> responses = notificationService.getUnreadByRecipient(8L);

        assertEquals(1, responses.size());
        assertEquals(NotificationChannel.APP, responses.get(0).getChannel());
    }

    @Test
    void getByRecipient_shouldReturnEmptyListWhenNothingExists() {
        when(notificationRepository.findByRecipientIdAndChannelOrderByCreatedAtDesc(101L, NotificationChannel.APP))
            .thenReturn(List.of());

        List<NotificationResponse> responses = notificationService.getByRecipient(101L);

        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    void markAsRead_shouldRejectInvalidNotificationId() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> notificationService.markAsRead(0L, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void markAsRead_shouldRejectWhenRequesterDoesNotOwnNotification() {
        Notification notification = baseNotification(41L, 7L, NotificationChannel.APP);
        when(notificationRepository.findById(41L)).thenReturn(Optional.of(notification));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> notificationService.markAsRead(41L, 99L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void markAsRead_shouldRejectWhenNotificationMissing() {
        when(notificationRepository.findById(400L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> notificationService.markAsRead(400L, 1L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void markAsRead_shouldPersistReadFlag() {
        Notification notification = baseNotification(51L, 15L, NotificationChannel.APP);
        when(notificationRepository.findById(51L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(51L, 15L);

        assertTrue(response.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsRead_shouldAllowNullRequesterAndMarkNotificationRead() {
        Notification notification = baseNotification(71L, 44L, NotificationChannel.APP);
        when(notificationRepository.findById(71L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(71L, null);

        assertTrue(response.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void getRecentByRecipient_shouldClampLimitAndMapResults() {
        Notification notification = baseNotification(88L, 20L, NotificationChannel.APP);
        when(notificationRepository.findByRecipientIdAndChannelOrderByCreatedAtDesc(eq(20L), eq(NotificationChannel.APP), any(Pageable.class)))
            .thenReturn(List.of(notification));

        List<NotificationResponse> responses = notificationService.getRecentByRecipient(20L, 500);

        assertEquals(1, responses.size());
        assertEquals(NotificationChannel.APP, responses.get(0).getChannel());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByRecipientIdAndChannelOrderByCreatedAtDesc(eq(20L), eq(NotificationChannel.APP), pageableCaptor.capture());
        assertEquals(50, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void getRecentByRecipient_shouldClampLowerBoundToOne() {
        Notification notification = baseNotification(99L, 25L, NotificationChannel.APP);
        when(notificationRepository.findByRecipientIdAndChannelOrderByCreatedAtDesc(eq(25L), eq(NotificationChannel.APP), any(Pageable.class)))
            .thenReturn(List.of(notification));

        notificationService.getRecentByRecipient(25L, 0);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByRecipientIdAndChannelOrderByCreatedAtDesc(eq(25L), eq(NotificationChannel.APP), pageableCaptor.capture());
        assertEquals(1, pageableCaptor.getValue().getPageSize());
    }

    private Notification baseNotification(Long notificationId, Long recipientId, NotificationChannel channel) {
        Notification notification = new Notification();
        notification.setNotificationId(notificationId);
        notification.setRecipientId(recipientId);
        notification.setType(NotificationType.BOOKING);
        notification.setTitle("Booking update");
        notification.setMessage("Ticket updated");
        notification.setChannel(channel);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now().minusHours(1));
        return notification;
    }
}
