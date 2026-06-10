package com.example.velocitylimits.repository;

import com.example.velocitylimits.model.LoadAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persistence layer for fund load attempts.
 * Provides duplicate detection and aggregation queries used by velocity limit checks.
 */
public interface LoadAttemptRepository extends JpaRepository<LoadAttempt, Long> {

    /**
     * Returns true if a load with the given ID has already been processed for this customer.
     * Used to detect and ignore duplicate submissions per the spec.
     */
    boolean existsByCustomerIdAndLoadId(String customerId, String loadId);

    /**
     * Returns the total amount of accepted loads for a customer within a time window.
     * Returns zero if no accepted loads exist in the window.
     */
    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LoadAttempt l " +
           "WHERE l.customerId = :customerId AND l.accepted = true " +
           "AND l.loadTime >= :start AND l.loadTime < :end")
    BigDecimal sumAcceptedAmountBetween(
            @Param("customerId") String customerId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Returns the number of accepted loads for a customer within a time window.
     */
    @Query("SELECT COUNT(l) FROM LoadAttempt l " +
           "WHERE l.customerId = :customerId AND l.accepted = true " +
           "AND l.loadTime >= :start AND l.loadTime < :end")
    long countAcceptedBetween(
            @Param("customerId") String customerId,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
