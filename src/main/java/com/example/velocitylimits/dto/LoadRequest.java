package com.example.velocitylimits.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Incoming fund load attempt deserialized from a single JSON input line.
 * The {@code load_amount} field is a dollar-prefixed string (e.g. {@code "$123.45"})
 * and is parsed into a {@link BigDecimal} at deserialization time.
 * The {@code time} field is an ISO-8601 UTC instant (e.g. {@code "2018-01-01T00:00:00Z"}).
 */
public class LoadRequest {

    private String id;

    @JsonProperty("customer_id")
    private String customerId;

    @JsonProperty("load_amount")
    @JsonDeserialize(using = DollarAmountDeserializer.class)
    private BigDecimal loadAmount;

    private Instant time;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public BigDecimal getLoadAmount() { return loadAmount; }
    public void setLoadAmount(BigDecimal loadAmount) { this.loadAmount = loadAmount; }

    public Instant getTime() { return time; }
    public void setTime(Instant time) { this.time = time; }
}
