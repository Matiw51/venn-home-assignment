package com.example.velocitylimits.repository;

import com.example.velocitylimits.model.LoadAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface LoadAttemptRepository extends JpaRepository<LoadAttempt, Long> {

    boolean existsByCustomerIdAndLoadId(String customerId, String loadId);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LoadAttempt l " +
           "WHERE l.customerId = :customerId AND l.accepted = true " +
           "AND l.loadTime >= :start AND l.loadTime < :end")
    BigDecimal sumAcceptedAmountBetween(
            @Param("customerId") String customerId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT COUNT(l) FROM LoadAttempt l " +
           "WHERE l.customerId = :customerId AND l.accepted = true " +
           "AND l.loadTime >= :start AND l.loadTime < :end")
    long countAcceptedBetween(
            @Param("customerId") String customerId,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
