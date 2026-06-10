package com.example.velocitylimits.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Result of a fund load attempt, serialized as a single JSON output line.
 * Field order is fixed to match the expected output format: id, customer_id, accepted.
 */
@JsonPropertyOrder({"id", "customer_id", "accepted"})
public class LoadResponse {

    private String id;

    @JsonProperty("customer_id")
    private String customerId;

    private boolean accepted;

    protected LoadResponse() {}

    public LoadResponse(String id, String customerId, boolean accepted) {
        this.id = id;
        this.customerId = customerId;
        this.accepted = accepted;
    }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public boolean isAccepted() { return accepted; }
}
