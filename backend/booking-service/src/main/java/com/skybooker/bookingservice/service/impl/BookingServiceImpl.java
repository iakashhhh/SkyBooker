package com.skybooker.bookingservice.service.impl;

import com.skybooker.bookingservice.dto.BookingResponse;
import com.skybooker.bookingservice.dto.CancelBookingRequest;
import com.skybooker.bookingservice.dto.CreateBookingRequest;
import com.skybooker.bookingservice.dto.PaymentRequest;
import com.skybooker.bookingservice.dto.PaymentResponse;
import com.skybooker.bookingservice.dto.UpdateBookingFareRequest;
import com.skybooker.bookingservice.entity.Booking;
import com.skybooker.bookingservice.entity.BookingStatus;
import com.skybooker.bookingservice.exception.BadRequestException;
import com.skybooker.bookingservice.exception.ConflictException;
import com.skybooker.bookingservice.exception.ResourceNotFoundException;
import com.skybooker.bookingservice.integration.FlightServiceClient;
import com.skybooker.bookingservice.integration.PaymentServiceClient;
import com.skybooker.bookingservice.integration.SeatServiceClient;
import com.skybooker.bookingservice.integration.dto.FlightResponse;
import com.skybooker.bookingservice.integration.dto.SeatActionRequest;
import com.skybooker.bookingservice.integration.dto.SeatMapResponse;
import com.skybooker.bookingservice.repository.BookingRepository;
import com.skybooker.bookingservice.service.BookingService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Implements booking lifecycle and integrations for Day 5.
 */
@Service
public class BookingServiceImpl implements BookingService {

    private static final String PNR_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int SEAT_ACTION_MAX_ATTEMPTS = 3;
    private static final long SEAT_ACTION_RETRY_DELAY_MS = 200L;
    private static final DateTimeFormatter TICKET_DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final BaseColor BRAND_BLUE = new BaseColor(24, 77, 150);
    private static final BaseColor SKY_BLUE = new BaseColor(53, 120, 208);
    private static final BaseColor BORDER_LIGHT = new BaseColor(214, 227, 245);
    private static final BaseColor TEXT_PRIMARY = new BaseColor(27, 41, 64);
    private static final BaseColor TEXT_MUTED = new BaseColor(94, 115, 145);

    private final BookingRepository bookingRepository;
    private final FlightServiceClient flightServiceClient;
    private final SeatServiceClient seatServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final RabbitTemplate rabbitTemplate;
    private final BigDecimal defaultTaxRate;
    private final BigDecimal mealFee;
    private final BigDecimal luggageFeePerKg;
    private final String bookingEventExchange;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public BookingServiceImpl(BookingRepository bookingRepository,
                              FlightServiceClient flightServiceClient,
                              SeatServiceClient seatServiceClient,
                              PaymentServiceClient paymentServiceClient,
                              RabbitTemplate rabbitTemplate,
                              @Value("${booking.default-tax-rate:0.18}") BigDecimal defaultTaxRate,
                              @Value("${booking.meal-fee:350}") BigDecimal mealFee,
                              @Value("${booking.luggage-fee-per-kg:120}") BigDecimal luggageFeePerKg,
                              @Value("${booking.event-exchange:skybooker.events}") String bookingEventExchange) {
        this.bookingRepository = bookingRepository;
        this.flightServiceClient = flightServiceClient;
        this.seatServiceClient = seatServiceClient;
        this.paymentServiceClient = paymentServiceClient;
        this.rabbitTemplate = rabbitTemplate;
        this.defaultTaxRate = defaultTaxRate;
        this.mealFee = mealFee;
        this.luggageFeePerKg = luggageFeePerKg;
        this.bookingEventExchange = bookingEventExchange;
    }

    public BookingServiceImpl(BookingRepository bookingRepository,
                              FlightServiceClient flightServiceClient,
                              SeatServiceClient seatServiceClient,
                              PaymentServiceClient paymentServiceClient,
                              BigDecimal defaultTaxRate,
                              BigDecimal mealFee,
                              BigDecimal luggageFeePerKg) {
        this(
            bookingRepository,
            flightServiceClient,
            seatServiceClient,
            paymentServiceClient,
            null,
            defaultTaxRate,
            mealFee,
            luggageFeePerKg,
            "skybooker.events"
        );
    }

    @Override
    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        List<Long> seatIds = List.copyOf(request.getSeatIds());
        validateSeatSelection(request, seatIds);
        Map<Long, SeatMapResponse.SeatView> selectedSeatViews = validateSeatsAreHeld(request.getFlightId(), seatIds);

        FlightResponse flight = flightServiceClient.getFlightById(request.getFlightId());
        if (flight == null || flight.getFlightId() == null) {
            throw new ResourceNotFoundException("Flight not found with id: " + request.getFlightId());
        }

        int seatsRequested = seatIds.size();
        if (flight.getAvailableSeats() == null || flight.getAvailableSeats() < seatsRequested) {
            throw new ConflictException("Not enough seats available for this flight");
        }

        if (request.getBaseFare() == null || request.getBaseFare().compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Base fare must be zero or greater");
        }

        BigDecimal baseFarePerSeat = request.getBaseFare().setScale(2, RoundingMode.HALF_UP);
        BigDecimal baseFare = baseFarePerSeat.multiply(BigDecimal.valueOf(seatsRequested)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal seatInclusiveFare = calculateSeatInclusiveFare(baseFarePerSeat, seatIds, selectedSeatViews);
        BigDecimal seatCharge = seatInclusiveFare.subtract(baseFare).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxes = baseFare.multiply(defaultTaxRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal mealCost = resolveMealCost(request.getMealPreference()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal luggageCost = luggageFeePerKg.multiply(BigDecimal.valueOf(Math.max(request.getLuggageKg(), 0L)))
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalFare = baseFare
            .add(seatCharge)
            .add(taxes)
            .add(mealCost)
            .add(luggageCost)
            .setScale(2, RoundingMode.HALF_UP);

        Booking booking = new Booking();
        booking.setUserId(request.getUserId());
        booking.setFlightId(request.getFlightId());
        booking.setSelectedSeatIds(serializeSeatIds(seatIds));
        booking.setPnrCode(generateUniquePnr());
        booking.setTripType(request.getTripType());
        booking.setStatus(BookingStatus.PENDING);
        booking.setBaseFare(baseFare);
        booking.setSeatCharge(seatCharge);
        booking.setBaggageCharge(luggageCost);
        booking.setMealCharge(mealCost);
        booking.setTaxes(taxes);
        booking.setTotalFare(totalFare);
        booking.setMealPreference(request.getMealPreference());
        booking.setLuggageKg(request.getLuggageKg());
        booking.setContactEmail(request.getContactEmail());
        booking.setContactPhone(request.getContactPhone());

        Booking saved = bookingRepository.save(booking);
        PaymentResponse paymentResponse = initiatePayment(saved);
        saved.setPaymentId(paymentResponse.getPaymentId());
        saved = bookingRepository.save(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(String bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
        return toResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getAllBookings() {
        return bookingRepository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByUser(Long userId) {
        return bookingRepository.findByUserIdOrderByBookedAtDesc(userId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingByPnr(String pnrCode) {
        Booking booking = bookingRepository.findByPnrCode(pnrCode)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found for PNR: " + pnrCode));
        return toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(CancelBookingRequest request) {
        Booking booking = bookingRepository.findById(request.getBookingId())
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + request.getBookingId()));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Booking is already cancelled");
        }
        if (booking.getStatus() == BookingStatus.COMPLETED || booking.getStatus() == BookingStatus.NO_SHOW) {
            throw new BadRequestException("Completed or no-show booking cannot be cancelled");
        }

        updateBookingStatus(booking.getBookingId(), BookingStatus.CANCELLED);
        return getBookingById(booking.getBookingId());
    }

    @Override
    @Transactional
    public BookingResponse updateBookingFare(String bookingId, UpdateBookingFareRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BadRequestException("Cannot update fare for a cancelled booking");
        }

        int luggageKg = request.getLuggageKg() == null ? 0 : Math.max(request.getLuggageKg(), 0);
        BigDecimal seatCharge = safeAmount(booking.getSeatCharge());
        BigDecimal mealCost = resolveMealCost(request.getMealSelections()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal luggageCost = luggageFeePerKg.multiply(BigDecimal.valueOf(luggageKg)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalFare = safeAmount(booking.getBaseFare())
            .add(seatCharge)
            .add(safeAmount(booking.getTaxes()))
            .add(mealCost)
            .add(luggageCost)
            .setScale(2, RoundingMode.HALF_UP);

        booking.setLuggageKg(luggageKg);
        booking.setMealPreference(resolveMealPreference(request.getMealSelections()));
        booking.setSeatCharge(seatCharge);
        booking.setBaggageCharge(luggageCost);
        booking.setMealCharge(mealCost);
        booking.setTotalFare(totalFare);

        Booking saved = bookingRepository.save(booking);
        if (saved.getStatus() == BookingStatus.PENDING) {
            PaymentResponse paymentResponse = initiatePayment(saved);
            saved.setPaymentId(paymentResponse.getPaymentId());
            saved = bookingRepository.save(saved);
        }
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void updateBookingStatus(String bookingId, BookingStatus status) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        BookingStatus previousStatus = booking.getStatus();
        if (previousStatus == status) {
            return;
        }

        SeatActionRequest seatActionRequest = buildSeatActionRequest(booking);
        if (!seatActionRequest.getSeatIds().isEmpty()) {
            if (status == BookingStatus.CONFIRMED) {
                executeSeatActionWithRetry(
                    () -> seatServiceClient.confirmSeats(seatActionRequest),
                    "confirm"
                );
            } else if (status == BookingStatus.CANCELLED) {
                executeSeatActionWithRetry(
                    () -> seatServiceClient.releaseSeats(seatActionRequest),
                    "release"
                );
            }
        }

        int seatCount = seatActionRequest.getSeatIds().size();
        int availabilityDelta = resolveFlightAvailabilityDelta(previousStatus, status, seatCount);
        if (availabilityDelta != 0) {
            flightServiceClient.adjustAvailableSeats(booking.getFlightId(), availabilityDelta);
        }

        booking.setStatus(status);
        bookingRepository.save(booking);

        if (status == BookingStatus.CONFIRMED) {
            publishBookingConfirmedEvent(booking);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateTicketPdf(String bookingId) {
        BookingResponse booking = getBookingById(bookingId);
        FlightResponse flight = fetchFlightForTicket(booking.getFlightId());

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 32, 32, 36, 28);
            PdfWriter.getInstance(document, outputStream);
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

            String generatedAt = booking.getBookedAt() == null
                ? TICKET_DATE_TIME.format(LocalDateTime.now())
                : TICKET_DATE_TIME.format(booking.getBookedAt());
            Paragraph subTitle = new Paragraph("Payment Successful | Generated " + generatedAt, subTextFont);
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

            String from = flight == null ? null : flight.getOriginAirportCode();
            String to = flight == null ? null : flight.getDestinationAirportCode();
            String bookingRef = safeText(booking.getBookingId(), "N/A");
            String pnr = safeText(booking.getPnrCode(), "N/A");
            String flightNo = flight == null ? null : flight.getFlightNumber();
            String departureDate = formatDepartureTime(flight == null ? null : flight.getDepartureTime(), booking.getBookedAt());

            Paragraph airline = new Paragraph("SKYBOOKER AIRLINES", airlineFont);
            airline.setSpacingAfter(10f);
            leftCell.addElement(airline);

            Paragraph routeVisual = new Paragraph(
                safeText(from, "---") + "  --------  o  --------  " + safeText(to, "---"),
                routeFont
            );
            routeVisual.setSpacingAfter(14f);
            leftCell.addElement(routeVisual);

            PdfPTable detailsTable = new PdfPTable(new float[]{1f, 1f});
            detailsTable.setWidthPercentage(100f);
            detailsTable.setSpacingBefore(4f);
            detailsTable.setSpacingAfter(14f);
            detailsTable.getDefaultCell().setBorder(PdfPCell.NO_BORDER);

            detailsTable.addCell(ticketDetailCell("Passenger", "User " + safeText(booking.getUserId(), "N/A"), labelFont, valueFont));
            detailsTable.addCell(ticketDetailCell("Booking ID", bookingRef, labelFont, valueFont));
            detailsTable.addCell(ticketDetailCell("PNR", pnr, labelFont, valueFont));
            detailsTable.addCell(ticketDetailCell("Flight", safeText(flightNo, "SkyBooker Flight"), labelFont, valueFont));
            detailsTable.addCell(ticketDetailCell("Departure", departureDate, labelFont, valueFont));
            detailsTable.addCell(ticketDetailCell("Status", safeText(booking.getStatus(), BookingStatus.PENDING.name()), labelFont, valueFont));
            leftCell.addElement(detailsTable);

            Paragraph message = new Paragraph("Payment received. Your ticket is booked.", subTextFont);
            message.setSpacingAfter(20f);
            leftCell.addElement(message);

            Paragraph fareBlock = new Paragraph("Payment Status: " + safeText(booking.getStatus(), BookingStatus.PENDING.name()), valueFont);
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

            String qrPayload = "PNR=" + pnr + "|BOOKING=" + bookingRef + "|FLIGHT=" + safeText(booking.getFlightId(), "N/A");
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
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new ConflictException("Unable to generate ticket PDF");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateTicketQr(String bookingId) {
        BookingResponse booking = getBookingById(bookingId);
        String qrPayload = "PNR=" + booking.getPnrCode() + "|BOOKING=" + booking.getBookingId() + "|FLIGHT=" + booking.getFlightId();

        try {
            BitMatrix matrix = new MultiFormatWriter().encode(qrPayload, BarcodeFormat.QR_CODE, 300, 300);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (Exception exception) {
            throw new ConflictException("Unable to generate ticket QR");
        }
    }

    private FlightResponse fetchFlightForTicket(Long flightId) {
        if (flightId == null || flightId <= 0) {
            return null;
        }
        try {
            return flightServiceClient.getFlightById(flightId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private PdfPCell ticketDetailCell(String label, String value, Font labelFont, Font valueFont) {
        Paragraph text = new Paragraph();
        text.add(new Phrase(label.toUpperCase() + "\n", labelFont));
        text.add(new Phrase(safeText(value, "N/A"), valueFont));

        PdfPCell cell = new PdfPCell(text);
        cell.setBorder(PdfPCell.NO_BORDER);
        cell.setPaddingBottom(10f);
        return cell;
    }

    private String formatDepartureTime(LocalDateTime departureTime, LocalDateTime bookedAt) {
        if (departureTime != null) {
            return TICKET_DATE_TIME.format(departureTime);
        }
        if (bookedAt != null) {
            return TICKET_DATE_TIME.format(bookedAt);
        }
        return TICKET_DATE_TIME.format(LocalDateTime.now());
    }

    private void validateSeatSelection(CreateBookingRequest request, List<Long> seatIds) {
        if (seatIds == null || seatIds.isEmpty()) {
            throw new BadRequestException("At least one seat is required");
        }

        if (seatIds.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("Seat IDs cannot be null");
        }

        long uniqueSeatCount = seatIds.stream().distinct().count();
        if (uniqueSeatCount != seatIds.size()) {
            throw new BadRequestException("Duplicate seat IDs are not allowed");
        }
    }

    private BigDecimal resolveMealCost(String mealPreference) {
        if (mealPreference == null || mealPreference.isBlank()) {
            return BigDecimal.ZERO;
        }
        return mealFee;
    }

    private BigDecimal resolveMealCost(List<UpdateBookingFareRequest.MealSelection> mealSelections) {
        if (mealSelections == null || mealSelections.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return mealSelections.stream()
            .map(UpdateBookingFareRequest.MealSelection::getMealPrice)
            .filter(Objects::nonNull)
            .map(price -> price.max(BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveMealPreference(List<UpdateBookingFareRequest.MealSelection> mealSelections) {
        if (mealSelections == null || mealSelections.isEmpty()) {
            return "";
        }

        Set<String> selectedMeals = new LinkedHashSet<>();
        for (UpdateBookingFareRequest.MealSelection selection : mealSelections) {
            if (selection == null || selection.getMealName() == null) {
                continue;
            }
            String mealName = selection.getMealName().trim();
            if (mealName.isEmpty() || "no meal".equalsIgnoreCase(mealName)) {
                continue;
            }
            selectedMeals.add(mealName);
        }

        return String.join(", ", selectedMeals);
    }

    private SeatActionRequest buildSeatActionRequest(Booking booking) {
        return new SeatActionRequest(booking.getFlightId(), deserializeSeatIds(booking.getSelectedSeatIds()));
    }

    private Map<Long, SeatMapResponse.SeatView> validateSeatsAreHeld(Long flightId, List<Long> requestedSeatIds) {
        SeatMapResponse seatMap = seatServiceClient.getSeatMap(flightId);
        if (seatMap == null || seatMap.getSeats() == null || seatMap.getSeats().isEmpty()) {
            throw new ConflictException("Unable to validate held seats for flight " + flightId);
        }

        Set<Long> missingSeatIds = new HashSet<>(requestedSeatIds);
        Map<Long, String> statusBySeatId = new HashMap<>();
        Map<Long, SeatMapResponse.SeatView> seatViewById = new HashMap<>();
        for (SeatMapResponse.SeatView seat : seatMap.getSeats()) {
            if (seat.getSeatId() == null) {
                continue;
            }
            statusBySeatId.put(seat.getSeatId(), seat.getStatus());
            seatViewById.put(seat.getSeatId(), seat);
            missingSeatIds.remove(seat.getSeatId());
        }

        if (!missingSeatIds.isEmpty()) {
            throw new BadRequestException("Some selected seats are invalid for flight " + flightId);
        }

        List<Long> notHeld = requestedSeatIds.stream()
            .filter(seatId -> !"HELD".equalsIgnoreCase(statusBySeatId.getOrDefault(seatId, "")))
            .toList();
        if (!notHeld.isEmpty()) {
            throw new ConflictException("Selected seats are not on hold or have expired: " + notHeld);
        }

        return seatViewById;
    }

    private BigDecimal calculateSeatInclusiveFare(BigDecimal baseFarePerSeat,
                                                  List<Long> seatIds,
                                                  Map<Long, SeatMapResponse.SeatView> seatViewsById) {
        return seatIds.stream()
            .map(seatId -> {
                BigDecimal multiplier = Optional.ofNullable(seatViewsById.get(seatId))
                    .map(SeatMapResponse.SeatView::getPriceMultiplier)
                    .filter(value -> value.compareTo(BigDecimal.ZERO) > 0)
                    .orElse(BigDecimal.ONE);
                return baseFarePerSeat.multiply(multiplier);
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private int resolveFlightAvailabilityDelta(BookingStatus previousStatus, BookingStatus nextStatus, int seatCount) {
        if (seatCount <= 0) {
            return 0;
        }
        if (previousStatus != BookingStatus.CONFIRMED && nextStatus == BookingStatus.CONFIRMED) {
            return -seatCount;
        }
        if (previousStatus == BookingStatus.CONFIRMED && nextStatus == BookingStatus.CANCELLED) {
            return seatCount;
        }
        return 0;
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String safeText(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private PaymentResponse initiatePayment(Booking booking) {
        PaymentRequest paymentRequest = new PaymentRequest();
        paymentRequest.setBookingId(booking.getBookingId());
        paymentRequest.setBookingReference(booking.getBookingId());
        paymentRequest.setUserId(booking.getUserId());
        paymentRequest.setAmount(booking.getTotalFare());
        paymentRequest.setCurrency("INR");
        return paymentServiceClient.createPayment(paymentRequest);
    }

    public String generateUniquePnr() {
        for (int attempt = 0; attempt < 100; attempt++) {
            String candidate = randomPnr();
            if (!bookingRepository.existsByPnrCode(candidate)) {
                return candidate;
            }
        }
        throw new ConflictException("Unable to generate a unique PNR");
    }

    private String randomPnr() {
        StringBuilder pnr = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            int index = secureRandom.nextInt(PNR_CHARS.length());
            pnr.append(PNR_CHARS.charAt(index));
        }
        return pnr.toString();
    }

    private BookingResponse toResponse(Booking booking) {
        BookingResponse response = new BookingResponse();
        response.setBookingId(booking.getBookingId());
        response.setUserId(booking.getUserId());
        response.setFlightId(booking.getFlightId());
        response.setSeatIds(deserializeSeatIds(booking.getSelectedSeatIds()));
        response.setPnrCode(booking.getPnrCode());
        response.setTripType(booking.getTripType());
        response.setStatus(booking.getStatus());
        response.setTotalFare(safeAmount(booking.getTotalFare()));
        response.setBaseFare(safeAmount(booking.getBaseFare()));
        response.setSeatCharge(safeAmount(booking.getSeatCharge()));
        response.setBaggageCharge(safeAmount(booking.getBaggageCharge()));
        response.setMealCharge(safeAmount(booking.getMealCharge()));
        response.setTaxes(safeAmount(booking.getTaxes()));
        response.setMealPreference(booking.getMealPreference());
        response.setLuggageKg(booking.getLuggageKg());
        response.setContactEmail(booking.getContactEmail());
        response.setContactPhone(booking.getContactPhone());
        response.setBookedAt(booking.getBookedAt());
        response.setPaymentId(booking.getPaymentId());
        return response;
    }

    private String serializeSeatIds(List<Long> seatIds) {
        return seatIds.stream()
            .map(String::valueOf)
            .reduce((left, right) -> left + "," + right)
            .orElse("");
    }

    private List<Long> deserializeSeatIds(String serializedSeatIds) {
        if (serializedSeatIds == null || serializedSeatIds.isBlank()) {
            return List.of();
        }

        return Arrays.stream(serializedSeatIds.split(","))
            .filter(value -> !value.isBlank())
            .map(Long::valueOf)
            .toList();
    }

    private void publishBookingConfirmedEvent(Booking booking) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("recipientId", booking.getUserId());
            payload.put("eventName", "BOOKING_CONFIRMED");
            payload.put("type", "BOOKING");
            payload.put("title", "Booking Confirmed");
            payload.put("message", "Your booking is confirmed. PNR: " + booking.getPnrCode());
            payload.put("channel", "APP");
            payload.put("channels", List.of("APP"));
            payload.put("relatedBookingId", booking.getBookingId());
            payload.put("relatedFlightId", booking.getFlightId());
            payload.put("pnrCode", booking.getPnrCode());
            if (booking.getContactEmail() != null && !booking.getContactEmail().isBlank()) {
                payload.put("recipientEmail", booking.getContactEmail());
            }
            if (booking.getContactPhone() != null && !booking.getContactPhone().isBlank()) {
                payload.put("recipientPhone", booking.getContactPhone());
            }
            rabbitTemplate.convertAndSend(bookingEventExchange, "booking.confirmed", payload);
        } catch (Exception ignored) {
            // Event publishing failure should not block booking confirmation.
        }
    }

    private void executeSeatActionWithRetry(Runnable seatAction, String action) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= SEAT_ACTION_MAX_ATTEMPTS; attempt++) {
            try {
                seatAction.run();
                return;
            } catch (RuntimeException exception) {
                lastException = exception;
                if (!isRetryableSeatActionError(exception) || attempt == SEAT_ACTION_MAX_ATTEMPTS) {
                    break;
                }
                sleepBeforeSeatRetry(attempt);
            }
        }
        throw toSeatActionConflict(action, lastException);
    }

    private boolean isRetryableSeatActionError(RuntimeException exception) {
        if (exception instanceof HttpStatusCodeException statusCodeException) {
            return statusCodeException.getStatusCode().is5xxServerError();
        }
        return exception instanceof RestClientException;
    }

    private ConflictException toSeatActionConflict(String action, RuntimeException exception) {
        String fallbackMessage = "Unable to " + action + " seats right now. Please retry.";
        if (exception == null) {
            return new ConflictException(fallbackMessage);
        }

        if (exception instanceof HttpStatusCodeException statusCodeException) {
            String body = statusCodeException.getResponseBodyAsString();
            String serviceMessage = extractJsonMessage(body);
            if (serviceMessage != null) {
                return new ConflictException(serviceMessage);
            }
        }

        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return new ConflictException(fallbackMessage);
        }
        return new ConflictException(exception.getMessage());
    }

    private String extractJsonMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        String token = "\"message\":\"";
        int start = responseBody.indexOf(token);
        if (start < 0) {
            return null;
        }
        int valueStart = start + token.length();
        int valueEnd = responseBody.indexOf('"', valueStart);
        if (valueEnd <= valueStart) {
            return null;
        }
        String message = responseBody.substring(valueStart, valueEnd).trim();
        return message.isEmpty() ? null : message;
    }

    private void sleepBeforeSeatRetry(int attempt) {
        try {
            Thread.sleep(SEAT_ACTION_RETRY_DELAY_MS * attempt);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
