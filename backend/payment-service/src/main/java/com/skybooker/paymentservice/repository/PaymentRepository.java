package com.skybooker.paymentservice.repository;

import com.skybooker.paymentservice.entity.Payment;
import com.skybooker.paymentservice.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findByBookingId(String bookingId);

    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Payment> findByStatus(PaymentStatus status);

    Optional<Payment> findByTransactionId(String transactionId);

    List<Payment> findByPaidAtBetween(LocalDateTime from, LocalDateTime to);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.userId = :userId and p.status = 'PAID'")
    BigDecimal sumAmountByUserId(@Param("userId") Long userId);
}
