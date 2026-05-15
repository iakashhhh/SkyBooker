package com.skybooker.notificationservice.service.impl;

import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.skybooker.notificationservice.dto.NotificationEvent;
import com.skybooker.notificationservice.dto.NotificationResponse;
import com.skybooker.notificationservice.dto.SupportInquiryRequest;
import com.skybooker.notificationservice.dto.SupportInquiryResponse;
import com.skybooker.notificationservice.entity.Notification;
import com.skybooker.notificationservice.entity.NotificationChannel;
import com.skybooker.notificationservice.entity.NotificationStatus;
import com.skybooker.notificationservice.entity.NotificationType;
import com.skybooker.notificationservice.integration.dto.BookingSnapshot;
import com.skybooker.notificationservice.integration.dto.FlightSnapshot;
import com.skybooker.notificationservice.integration.dto.InternalUserEmailResponse;
import com.skybooker.notificationservice.repository.NotificationRepository;
import com.skybooker.notificationservice.service.NotificationService;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final int MAX_RECENT_LIMIT = 50;
    private static final BaseColor BRAND_BLUE = new BaseColor(24, 77, 150);
    private static final BaseColor SKY_BLUE = new BaseColor(53, 120, 208);
    private static final BaseColor BORDER_LIGHT = new BaseColor(214, 227, 245);
    private static final BaseColor TEXT_PRIMARY = new BaseColor(27, 41, 64);
    private static final BaseColor TEXT_MUTED = new BaseColor(94, 115, 145);
    private static final Pattern SIMPLE_EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final NotificationRepository notificationRepository;
    private final JavaMailSender javaMailSender;
    private final RestTemplate restTemplate;
    private final String emailFrom;
    private final String supportInboxEmail;
    private final String fallbackEmailDomain;
    private final String bookingServiceBaseUrl;
    private final String authServiceBaseUrl;
    private final String flightServiceBaseUrl;

    public NotificationServiceImpl(NotificationRepository notificationRepository,
                                   JavaMailSender javaMailSender,
                                   @Value("${notification.integration.booking-base-url:http://localhost:8080}") String bookingServiceBaseUrl,
                                   @Value("${notification.integration.auth-base-url:http://localhost:8080}") String authServiceBaseUrl,
                                   @Value("${notification.integration.flight-base-url:http://localhost:8080}") String flightServiceBaseUrl,
                                   @Value("${notification.delivery.email.from:noreply@skybooker.com}") String emailFrom,
                                   @Value("${notification.delivery.support.inbox:i.akashhhh@gmail.com}") String supportInboxEmail,
                                   @Value("${notification.delivery.email.fallback-domain:skybooker.local}") String fallbackEmailDomain) {
        this.notificationRepository = notificationRepository;
        this.javaMailSender = javaMailSender;
        this.restTemplate = new RestTemplate();
        this.bookingServiceBaseUrl = bookingServiceBaseUrl;
        this.authServiceBaseUrl = authServiceBaseUrl;
        this.flightServiceBaseUrl = flightServiceBaseUrl;
        this.emailFrom = emailFrom;
        this.supportInboxEmail = supportInboxEmail;
        this.fallbackEmailDomain = fallbackEmailDomain;
    }

    @Override
    @Transactional
    public NotificationResponse processEvent(NotificationEvent event) {
        validateEvent(event);
        NotificationType notificationType = resolveType(event);
        String title = resolveTitle(event, notificationType);
        String message = resolveMessage(event, notificationType);
        Set<NotificationChannel> channels = resolveChannels(event);

        List<Notification> deliveredNotifications = new ArrayList<>();
        for (NotificationChannel channel : channels) {
            Notification notification = buildNotification(event, notificationType, channel, title, message);
            Notification saved = notificationRepository.save(notification);
            deliver(saved, event);
            deliveredNotifications.add(notificationRepository.save(saved));
        }

        Notification selected = deliveredNotifications.stream()
            .filter(notification -> notification.getChannel() == NotificationChannel.APP)
            .findFirst()
            .orElse(deliveredNotifications.get(0));
        return toResponse(selected);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getByRecipient(Long recipientId) {
        validateRecipientId(recipientId);
        return notificationRepository.findByRecipientIdAndChannelOrderByCreatedAtDesc(recipientId, NotificationChannel.APP)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getUnreadByRecipient(Long recipientId) {
        validateRecipientId(recipientId);
        return notificationRepository.findByRecipientIdAndChannelAndIsReadFalseOrderByCreatedAtDesc(recipientId, NotificationChannel.APP)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long requesterId) {
        if (notificationId == null || notificationId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "notificationId must be greater than zero");
        }

        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        if (requesterId != null && !Objects.equals(requesterId, notification.getRecipientId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot read another user's notification");
        }

        notification.setRead(true);
        return toResponse(notificationRepository.save(notification));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getRecentByRecipient(Long recipientId, int limit) {
        validateRecipientId(recipientId);
        int safeLimit = Math.min(Math.max(limit, 1), MAX_RECENT_LIMIT);
        return notificationRepository.findByRecipientIdAndChannelOrderByCreatedAtDesc(
                recipientId,
                NotificationChannel.APP,
                PageRequest.of(0, safeLimit)
            )
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    public SupportInquiryResponse submitSupportInquiry(SupportInquiryRequest request) {
        String fullName = clean(request.getFullName());
        String email = clean(request.getEmail());
        String phone = clean(request.getPhone());
        String bookingId = clean(request.getBookingId());
        String category = clean(request.getCategory());
        String subject = clean(request.getSubject());
        String message = clean(request.getMessage());
        String ticketRef = "SUP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        String htmlBody = """
            <html>
              <body style="font-family:Arial,sans-serif;background:#f6f9ff;padding:18px;">
                <div style="max-width:640px;margin:0 auto;background:#ffffff;border-radius:14px;padding:18px;border:1px solid #d7e4f7;">
                  <h2 style="margin:0 0 10px;color:#184d96;">New Support Request - %s</h2>
                  <p style="margin:0 0 12px;color:#20344f;">Reference: <strong>%s</strong></p>
                  <div style="background:#eff6ff;border-radius:10px;padding:12px;">
                    <p style="margin:0 0 6px;color:#2f4a70;"><strong>Name:</strong> %s</p>
                    <p style="margin:0 0 6px;color:#2f4a70;"><strong>Email:</strong> %s</p>
                    <p style="margin:0 0 6px;color:#2f4a70;"><strong>Phone:</strong> %s</p>
                    <p style="margin:0 0 6px;color:#2f4a70;"><strong>Booking ID:</strong> %s</p>
                    <p style="margin:0;color:#2f4a70;"><strong>Category:</strong> %s</p>
                  </div>
                  <h3 style="margin:14px 0 6px;color:#163b70;">Subject</h3>
                  <p style="margin:0 0 12px;color:#20344f;">%s</p>
                  <h3 style="margin:14px 0 6px;color:#163b70;">Issue Details</h3>
                  <p style="margin:0;color:#20344f;white-space:pre-line;">%s</p>
                </div>
              </body>
            </html>
            """.formatted(
            escapeHtml(safeValue(category, "General")),
            escapeHtml(ticketRef),
            escapeHtml(safeValue(fullName, "N/A")),
            escapeHtml(safeValue(email, "N/A")),
            escapeHtml(safeValue(phone, "Not shared")),
            escapeHtml(safeValue(bookingId, "Not shared")),
            escapeHtml(safeValue(category, "General")),
            escapeHtml(safeValue(subject, "Support query")),
            escapeHtml(safeValue(message, "No details provided"))
        );

        try {
            MimeMessage mail = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mail, true, StandardCharsets.UTF_8.name());
            helper.setFrom(emailFrom);
            helper.setTo(safeValue(clean(supportInboxEmail), "i.akashhhh@gmail.com"));
            helper.setSubject("[SkyBooker Support] " + safeValue(subject, "Support query") + " | " + ticketRef);
            if (email != null) {
                helper.setReplyTo(email);
            }
            helper.setText(htmlBody, true);
            javaMailSender.send(mail);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to submit support request right now");
        }

        return new SupportInquiryResponse("Support request submitted successfully.", ticketRef);
    }

    private Notification buildNotification(NotificationEvent event,
                                           NotificationType notificationType,
                                           NotificationChannel channel,
                                           String title,
                                           String message) {
        Notification notification = new Notification();
        notification.setRecipientId(event.getRecipientId());
        notification.setType(notificationType);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setChannel(channel);
        notification.setRelatedBookingId(clean(event.getRelatedBookingId()));
        notification.setRelatedFlightId(event.getRelatedFlightId());
        notification.setStatus(NotificationStatus.PENDING);
        notification.setRead(channel != NotificationChannel.APP);
        notification.setCreatedAt(LocalDateTime.now());
        return notification;
    }

    private void deliver(Notification notification, NotificationEvent event) {
        try {
            switch (notification.getChannel()) {
                case APP -> deliverInApp(notification);
                case SMS -> deliverSms(notification, event);
                case EMAIL -> deliverEmail(notification, event);
                default -> throw new IllegalStateException("Unsupported notification channel");
            }
            notification.setStatus(NotificationStatus.SENT);
            notification.setDeliveredAt(LocalDateTime.now());
        } catch (Exception exception) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setDeliveredAt(null);
            LOGGER.warn("Notification delivery failed for channel={} recipientId={} reason={}",
                notification.getChannel(), notification.getRecipientId(), exception.getMessage());
        }
    }

    private void deliverInApp(Notification notification) {
        LOGGER.info("In-app notification ready for recipient={} title={}",
            notification.getRecipientId(), notification.getTitle());
    }

    private void deliverSms(Notification notification, NotificationEvent event) {
        String phone = clean(event.getRecipientPhone());
        if (phone == null) {
            throw new IllegalStateException("Recipient phone unavailable");
        }
        LOGGER.info("SMS dispatch -> to={} body={}", phone, toSmsMessage(notification));
    }

    private void deliverEmail(Notification notification, NotificationEvent event) throws Exception {
        String bookingId = clean(event.getRelatedBookingId());
        BookingSnapshot bookingSnapshot = fetchBookingSnapshot(bookingId);
        Long flightId = event.getRelatedFlightId() != null
            ? event.getRelatedFlightId()
            : bookingSnapshot == null ? null : bookingSnapshot.getFlightId();
        FlightSnapshot flightSnapshot = fetchFlightSnapshot(flightId);
        String to = resolveEmail(event, bookingSnapshot);
        LOGGER.info("Email notification dispatch -> recipientId={} to={} bookingId={}",
            event.getRecipientId(), to, bookingId);

        MimeMessage mail = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mail, true, StandardCharsets.UTF_8.name());
        helper.setFrom(emailFrom);
        helper.setTo(to);
        helper.setSubject(notification.getTitle());
        helper.setText(toEmailTemplate(notification, event, bookingSnapshot, flightSnapshot), true);

        byte[] ticketPdf = fetchTicketPdf(bookingId);
        if (ticketPdf != null && ticketPdf.length > 0) {
            LOGGER.info("Using booking-service A4 ticket PDF for bookingId={}", bookingId);
            String attachmentName = bookingId == null ? "ticket-card.pdf" : "ticket-" + bookingId + ".pdf";
            helper.addAttachment(attachmentName, new ByteArrayResource(ticketPdf));
        } else {
            LOGGER.warn("Booking ticket PDF unavailable for bookingId={}; email sent without PDF attachment", bookingId);
        }
        javaMailSender.send(mail);
    }

    private byte[] generateEcardPdf(Notification notification,
                                    NotificationEvent event,
                                    BookingSnapshot bookingSnapshot,
                                    FlightSnapshot flightSnapshot) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 32, 32, 36, 28);
        PdfWriter.getInstance(document, out);
        document.open();

        Font brandTagFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, SKY_BLUE);
        Font headingFont = new Font(Font.FontFamily.HELVETICA, 24, Font.BOLD, TEXT_PRIMARY);
        Font subTextFont = new Font(Font.FontFamily.HELVETICA, 11, Font.NORMAL, TEXT_MUTED);
        Font airlineFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD, BRAND_BLUE);
        Font routeFont = new Font(Font.FontFamily.HELVETICA, 17, Font.BOLD, TEXT_PRIMARY);
        Font labelFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD, TEXT_MUTED);
        Font valueFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, TEXT_PRIMARY);
        Font rightTitleFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD, BaseColor.WHITE);
        Font rightInfoFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.WHITE);
        Font footerFont = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, TEXT_MUTED);

        Paragraph tag = new Paragraph("SKYBOOKER E-TICKET", brandTagFont);
        tag.setSpacingAfter(4f);
        document.add(tag);

        Paragraph title = new Paragraph("Boarding Pass", headingFont);
        title.setSpacingAfter(6f);
        document.add(title);

        Paragraph subTitle = new Paragraph(notification.getTitle() + " | Generated " + DATE_TIME_FORMATTER.format(LocalDateTime.now()), subTextFont);
        subTitle.setSpacingAfter(18f);
        document.add(subTitle);

        PdfPTable cardTable = new PdfPTable(new float[]{7.5f, 2.5f});
        cardTable.setWidthPercentage(100f);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorderColor(BORDER_LIGHT);
        leftCell.setBorderWidth(1f);
        leftCell.setBorderWidthRight(0f);
        leftCell.setPadding(16f);
        leftCell.setMinimumHeight(370f);

        String from = safeValue(resolveRouteFrom(event, flightSnapshot), "FROM");
        String to = safeValue(resolveRouteTo(event, flightSnapshot), "TO");
        String bookingId = safeValue(notification.getRelatedBookingId(), "N/A");
        String pnr = safeValue(resolvePnr(event, bookingSnapshot), "N/A");
        String flightNo = safeValue(resolveFlightNumber(event, flightSnapshot), "SkyBooker Flight");
        String departureDate = safeValue(resolveDepartureDate(event, flightSnapshot), DATE_TIME_FORMATTER.format(LocalDateTime.now()));

        Paragraph airline = new Paragraph("SKYBOOKER AIRLINES", airlineFont);
        airline.setSpacingAfter(10f);
        leftCell.addElement(airline);

        Paragraph routeVisual = new Paragraph(from + "  --------  o  --------  " + to, routeFont);
        routeVisual.setSpacingAfter(14f);
        leftCell.addElement(routeVisual);

        PdfPTable detailsTable = new PdfPTable(new float[]{1f, 1f});
        detailsTable.setWidthPercentage(100f);
        detailsTable.setSpacingBefore(4f);
        detailsTable.setSpacingAfter(14f);
        detailsTable.getDefaultCell().setBorder(PdfPCell.NO_BORDER);

        detailsTable.addCell(detailCell("Passenger", "User " + notification.getRecipientId(), labelFont, valueFont));
        detailsTable.addCell(detailCell("Booking ID", bookingId, labelFont, valueFont));
        detailsTable.addCell(detailCell("PNR", pnr, labelFont, valueFont));
        detailsTable.addCell(detailCell("Flight", flightNo, labelFont, valueFont));
        detailsTable.addCell(detailCell("Departure", departureDate, labelFont, valueFont));
        detailsTable.addCell(detailCell("Status", notification.getStatus().name(), labelFont, valueFont));
        leftCell.addElement(detailsTable);

        Paragraph message = new Paragraph(notification.getMessage(), subTextFont);
        message.setSpacingAfter(20f);
        leftCell.addElement(message);

        Paragraph fareBlock = new Paragraph("Payment Status: " + notification.getStatus().name(), valueFont);
        fareBlock.setSpacingAfter(8f);
        leftCell.addElement(fareBlock);

        Paragraph note = new Paragraph("Please arrive at least 2 hours before departure and carry valid government ID.", footerFont);
        leftCell.addElement(note);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBackgroundColor(BRAND_BLUE);
        rightCell.setBorderColor(BORDER_LIGHT);
        rightCell.setBorderWidth(1f);
        rightCell.setBorderWidthLeft(0f);
        rightCell.setPadding(14f);
        rightCell.setMinimumHeight(370f);
        rightCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        rightCell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph qrTitle = new Paragraph("SCAN AT CHECK-IN", rightTitleFont);
        qrTitle.setAlignment(Element.ALIGN_CENTER);
        qrTitle.setSpacingAfter(12f);
        rightCell.addElement(qrTitle);

        Long resolvedFlightId = event.getRelatedFlightId();
        if (resolvedFlightId == null && flightSnapshot != null) {
            resolvedFlightId = flightSnapshot.getFlightId();
        }
        String qrPayload = "PNR=" + pnr + "|BOOKING=" + bookingId + "|FLIGHT=" + safeValue(resolvedFlightId, "N/A");
        BarcodeQRCode barcodeQRCode = new BarcodeQRCode(qrPayload, 200, 200, null);
        Image qrImage = barcodeQRCode.getImage();
        qrImage.scaleToFit(112, 112);
        qrImage.setAlignment(Element.ALIGN_CENTER);
        rightCell.addElement(qrImage);

        Paragraph pnrLabel = new Paragraph("PNR " + pnr, rightInfoFont);
        pnrLabel.setAlignment(Element.ALIGN_CENTER);
        pnrLabel.setSpacingBefore(10f);
        rightCell.addElement(pnrLabel);

        Paragraph boardLabel = new Paragraph("BOARDING PASS", rightInfoFont);
        boardLabel.setAlignment(Element.ALIGN_CENTER);
        boardLabel.setSpacingBefore(18f);
        rightCell.addElement(boardLabel);

        cardTable.addCell(leftCell);
        cardTable.addCell(rightCell);
        document.add(cardTable);

        Paragraph terms = new Paragraph(
            "Important: Keep this ticket and your ID ready at security. In case of payment disputes, contact SkyBooker support with your booking ID.",
            footerFont
        );
        terms.setSpacingBefore(14f);
        document.add(terms);

        document.close();
        return out.toByteArray();
    }

    private PdfPCell detailCell(String label, String value, Font labelFont, Font valueFont) {
        Paragraph text = new Paragraph();
        text.add(new Phrase(label.toUpperCase() + "\n", labelFont));
        text.add(new Phrase(safeValue(value, "N/A"), valueFont));

        PdfPCell cell = new PdfPCell(text);
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPaddingBottom(10f);
        return cell;
    }

    private String route(NotificationEvent event, FlightSnapshot flightSnapshot) {
        String from = resolveRouteFrom(event, flightSnapshot);
        String to = resolveRouteTo(event, flightSnapshot);
        if (from == null && to == null) {
            return "Route unavailable";
        }
        return safeValue(from, "---") + " -> " + safeValue(to, "---");
    }

    private String toEmailTemplate(Notification notification,
                                   NotificationEvent event,
                                   BookingSnapshot bookingSnapshot,
                                   FlightSnapshot flightSnapshot) {
        return """
            <html>
              <body style="font-family:Arial,sans-serif;background:#f6f9ff;padding:18px;">
                <div style="max-width:560px;margin:0 auto;background:#ffffff;border-radius:14px;padding:18px;border:1px solid #d7e4f7;">
                  <h2 style="margin:0 0 10px;color:#184d96;">%s</h2>
                  <p style="margin:0 0 12px;color:#20344f;line-height:1.5;">%s</p>
                  <div style="background:#eff6ff;border-radius:10px;padding:12px;">
                    <p style="margin:0 0 6px;color:#2f4a70;"><strong>PNR:</strong> %s</p>
                    <p style="margin:0 0 6px;color:#2f4a70;"><strong>Booking:</strong> %s</p>
                    <p style="margin:0 0 6px;color:#2f4a70;"><strong>Flight:</strong> %s</p>
                    <p style="margin:0;color:#2f4a70;"><strong>Route:</strong> %s</p>
                  </div>
                  <p style="margin:14px 0 0;color:#617b9f;font-size:12px;">Thank you for choosing SkyBooker.</p>
                </div>
              </body>
            </html>
            """.formatted(
            escapeHtml(notification.getTitle()),
            escapeHtml(notification.getMessage()),
            escapeHtml(safeValue(resolvePnr(event, bookingSnapshot), "N/A")),
            escapeHtml(safeValue(notification.getRelatedBookingId(), "N/A")),
            escapeHtml(safeValue(resolveFlightNumber(event, flightSnapshot), "SkyBooker Flight")),
            escapeHtml(route(event, flightSnapshot))
        );
    }

    private String toSmsMessage(Notification notification) {
        String bookingId = safeValue(notification.getRelatedBookingId(), "NA");
        return "SkyBooker: " + notification.getTitle() + ". Ref: " + bookingId;
    }

    private String resolveEmail(NotificationEvent event, BookingSnapshot bookingSnapshot) {
        String profileEmail = sanitizeRealEmail(fetchUserEmail(event.getRecipientId()));
        if (profileEmail != null) {
            return profileEmail;
        }

        String bookingEmail = sanitizeRealEmail(bookingSnapshot == null ? null : bookingSnapshot.getContactEmail());
        if (bookingEmail != null) {
            return bookingEmail;
        }

        String eventEmail = sanitizeRealEmail(event.getRecipientEmail());
        if (eventEmail != null) {
            return eventEmail;
        }

        throw new IllegalStateException("Recipient email unavailable for user " + event.getRecipientId());
    }

    private byte[] fetchTicketPdf(String bookingId) {
        if (bookingId == null) {
            return null;
        }
        String url = bookingServiceBaseUrl + "/api/v1/bookings/" + bookingId + "/ticket/pdf";
        try {
            return restTemplate.getForObject(url, byte[].class);
        } catch (RestClientException exception) {
            LOGGER.debug("Ticket PDF fetch failed for bookingId={} reason={}", bookingId, exception.getMessage());
            return null;
        }
    }

    private BookingSnapshot fetchBookingSnapshot(String bookingId) {
        if (bookingId == null) {
            return null;
        }
        String url = bookingServiceBaseUrl + "/api/v1/bookings/" + bookingId;
        try {
            return restTemplate.getForObject(url, BookingSnapshot.class);
        } catch (RestClientException exception) {
            LOGGER.debug("Booking lookup failed for bookingId={} reason={}", bookingId, exception.getMessage());
            return null;
        }
    }

    private FlightSnapshot fetchFlightSnapshot(Long flightId) {
        if (flightId == null || flightId <= 0) {
            return null;
        }
        String url = flightServiceBaseUrl + "/flights/" + flightId;
        try {
            return restTemplate.getForObject(url, FlightSnapshot.class);
        } catch (RestClientException exception) {
            LOGGER.debug("Flight lookup failed for flightId={} reason={}", flightId, exception.getMessage());
            return null;
        }
    }

    private String fetchUserEmail(Long recipientId) {
        if (recipientId == null || recipientId <= 0) {
            return null;
        }
        String url = authServiceBaseUrl + "/auth/internal/users/" + recipientId + "/email";
        try {
            InternalUserEmailResponse response = restTemplate.getForObject(url, InternalUserEmailResponse.class);
            return response == null ? null : response.getEmail();
        } catch (RestClientException exception) {
            LOGGER.debug("User email lookup failed for recipientId={} reason={}", recipientId, exception.getMessage());
            return null;
        }
    }

    private String sanitizeRealEmail(String rawEmail) {
        String email = clean(rawEmail);
        if (email == null) {
            return null;
        }
        String normalized = email.toLowerCase();
        if (!SIMPLE_EMAIL_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        if (isPlaceholderEmail(normalized)) {
            return null;
        }
        return normalized;
    }

    private boolean isPlaceholderEmail(String normalizedEmail) {
        if ("passenger@skybooker.com".equals(normalizedEmail) || "passenger@skybooker.local".equals(normalizedEmail)) {
            return true;
        }
        return normalizedEmail.startsWith("user-")
            && normalizedEmail.endsWith("@" + fallbackEmailDomain.toLowerCase());
    }

    private String resolveRouteFrom(NotificationEvent event, FlightSnapshot flightSnapshot) {
        String routeFrom = clean(event.getRouteFrom());
        if (routeFrom != null) {
            return routeFrom;
        }
        return flightSnapshot == null ? null : clean(flightSnapshot.getOriginAirportCode());
    }

    private String resolveRouteTo(NotificationEvent event, FlightSnapshot flightSnapshot) {
        String routeTo = clean(event.getRouteTo());
        if (routeTo != null) {
            return routeTo;
        }
        return flightSnapshot == null ? null : clean(flightSnapshot.getDestinationAirportCode());
    }

    private String resolveFlightNumber(NotificationEvent event, FlightSnapshot flightSnapshot) {
        String flightNumber = clean(event.getFlightNumber());
        if (flightNumber != null) {
            return flightNumber;
        }
        return flightSnapshot == null ? null : clean(flightSnapshot.getFlightNumber());
    }

    private String resolvePnr(NotificationEvent event, BookingSnapshot bookingSnapshot) {
        String pnr = clean(event.getPnrCode());
        if (pnr != null) {
            return pnr;
        }
        return bookingSnapshot == null ? null : clean(bookingSnapshot.getPnrCode());
    }

    private String resolveDepartureDate(NotificationEvent event, FlightSnapshot flightSnapshot) {
        String departureDate = clean(event.getDepartureDate());
        if (departureDate != null) {
            return departureDate;
        }
        if (flightSnapshot != null && flightSnapshot.getDepartureTime() != null) {
            return DATE_TIME_FORMATTER.format(flightSnapshot.getDepartureTime());
        }
        return null;
    }

    private String route(NotificationEvent event) {
        return route(event, null);
    }

    private Set<NotificationChannel> resolveChannels(NotificationEvent event) {
        Set<NotificationChannel> channels = new LinkedHashSet<>();
        if (event.getChannels() != null && !event.getChannels().isEmpty()) {
            channels.addAll(event.getChannels());
        }
        if (event.getChannel() != null) {
            channels.add(event.getChannel());
        }
        if (channels.isEmpty()) {
            channels.add(NotificationChannel.APP);
            channels.add(NotificationChannel.EMAIL);
            channels.add(NotificationChannel.SMS);
        } else if (!channels.contains(NotificationChannel.APP)) {
            channels.add(NotificationChannel.APP);
        }
        return channels;
    }

    private NotificationType resolveType(NotificationEvent event) {
        if (event.getType() != null) {
            return event.getType();
        }
        String eventName = normalizeEventName(event.getEventName());
        return switch (eventName) {
            case "PAYMENT_SUCCESS", "PAYMENT_FAILED" -> NotificationType.PAYMENT;
            case "FLIGHT_DELAY", "FLIGHT_DELAYED", "FLIGHT_UPDATED", "FLIGHT_CANCELLED" -> NotificationType.FLIGHT;
            case "BOOKING_CANCELLED" -> NotificationType.BOOKING;
            case "CHECKIN_REMINDER", "BOARDING_REMINDER" -> NotificationType.REMINDER;
            default -> NotificationType.BOOKING;
        };
    }

    private String resolveTitle(NotificationEvent event, NotificationType notificationType) {
        String title = clean(event.getTitle());
        if (title != null) {
            return title;
        }

        String eventName = normalizeEventName(event.getEventName());
        return switch (eventName) {
            case "BOOKING_CONFIRMED" -> "Booking Confirmed";
            case "BOOKING_CANCELLED" -> "Booking Cancelled";
            case "PAYMENT_SUCCESS" -> "Payment Successful";
            case "PAYMENT_FAILED" -> "Payment Failed";
            case "FLIGHT_DELAY", "FLIGHT_DELAYED" -> "Flight Delay Alert";
            case "FLIGHT_CANCELLED" -> "Flight Cancelled";
            case "CHECKIN_REMINDER" -> "Check-in Reminder";
            case "BOARDING_REMINDER" -> "Boarding Reminder";
            default -> switch (notificationType) {
                case BOOKING -> "Booking Update";
                case PAYMENT -> "Payment Update";
                case FLIGHT -> "Flight Update";
                case REMINDER -> "Travel Reminder";
            };
        };
    }

    private String resolveMessage(NotificationEvent event, NotificationType notificationType) {
        String customMessage = clean(event.getMessage());
        if (customMessage != null) {
            return customMessage;
        }

        String pnr = clean(event.getPnrCode());
        String route = route(event);
        String flightNo = safeValue(clean(event.getFlightNumber()), "SkyBooker Flight");
        String departureDate = safeValue(clean(event.getDepartureDate()), "your scheduled date");
        String eventName = normalizeEventName(event.getEventName());

        return switch (eventName) {
            case "BOOKING_CONFIRMED" ->
                "Your booking is confirmed. PNR " + safeValue(pnr, "will be shared soon") + ". Route: " + route + ".";
            case "BOOKING_CANCELLED" ->
                "Booking " + safeValue(pnr, "reference") + " for " + route + " has been cancelled. Please contact support for help with rebooking.";
            case "PAYMENT_SUCCESS" ->
                "Payment received successfully. Your ticket for " + route + " is now booked.";
            case "PAYMENT_FAILED" ->
                "Payment could not be completed. Please retry to confirm your booking for " + route + ".";
            case "FLIGHT_DELAY", "FLIGHT_DELAYED" ->
                flightNo + " is delayed by " + safeValue(event.getDelayMinutes(), 0) + " minutes. Please check updated departure details.";
            case "FLIGHT_CANCELLED" ->
                flightNo + " for " + route + " has been cancelled. Your booking has been updated accordingly.";
            case "CHECKIN_REMINDER" ->
                "Check-in is now open for your flight " + flightNo + " on " + departureDate + ".";
            case "BOARDING_REMINDER" ->
                "Boarding will begin soon for " + flightNo + ". Please proceed to your gate.";
            default -> switch (notificationType) {
                case BOOKING -> "Your booking has been updated. Route: " + route + ".";
                case PAYMENT -> "Your payment status has changed. Please review your booking.";
                case FLIGHT -> "Your flight details were updated. Please check the latest schedule.";
                case REMINDER -> "Travel reminder from SkyBooker: please review your trip details.";
            };
        };
    }

    private void validateEvent(NotificationEvent event) {
        if (event == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notification event is required");
        }
        validateRecipientId(event.getRecipientId());
    }

    private void validateRecipientId(Long recipientId) {
        if (recipientId == null || recipientId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recipientId must be greater than zero");
        }
    }

    private String normalizeEventName(String rawEventName) {
        if (rawEventName == null) {
            return "";
        }
        return rawEventName.trim().toUpperCase();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeValue(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String safeValue(Long value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private int safeValue(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private NotificationResponse toResponse(Notification notification) {
        NotificationResponse response = new NotificationResponse();
        response.setNotificationId(notification.getNotificationId());
        response.setRecipientId(notification.getRecipientId());
        response.setType(notification.getType());
        response.setTitle(notification.getTitle());
        response.setMessage(notification.getMessage());
        response.setChannel(notification.getChannel());
        response.setRelatedBookingId(notification.getRelatedBookingId());
        response.setRelatedFlightId(notification.getRelatedFlightId());
        response.setStatus(notification.getStatus());
        response.setRead(notification.isRead());
        response.setCreatedAt(notification.getCreatedAt());
        response.setDeliveredAt(notification.getDeliveredAt());
        return response;
    }
}
