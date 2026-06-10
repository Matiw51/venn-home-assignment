package com.example.velocitylimits.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persisted record of a fund load attempt, whether accepted or declined.
 * Both outcomes are stored so that velocity limit queries can correctly
 * aggregate only accepted loads.
 */
@Entity
@Table(
    name = "load_attempts",
    indexes = {
        @Index(name = "idx_customer_load", columnList = "customerId, loadId"),
        @Index(name = "idx_customer_time", columnList = "customerId, loadTime, accepted")
    }
)
public class LoadAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String loadId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant loadTime;

    @Column(nullable = false)
    private boolean accepted;

    public LoadAttempt() {}

    public LoadAttempt(String loadId, String customerId, BigDecimal amount, Instant loadTime, boolean accepted) {
        this.loadId = loadId;
        this.customerId = customerId;
        this.amount = amount;
        this.loadTime = loadTime;
        this.accepted = accepted;
    }

    public Long getId() { return id; }

    public String getLoadId() { return loadId; }
    public void setLoadId(String loadId) { this.loadId = loadId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Instant getLoadTime() { return loadTime; }
    public void setLoadTime(Instant loadTime) { this.loadTime = loadTime; }

    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
}
