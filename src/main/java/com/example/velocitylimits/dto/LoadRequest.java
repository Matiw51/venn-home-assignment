package com.example.velocitylimits.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoadRequest {

    private String id;

    @JsonProperty("customer_id")
    private String customerId;

    @JsonProperty("load_amount")
    private String loadAmount;

    private String time;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getLoadAmount() { return loadAmount; }
    public void setLoadAmount(String loadAmount) { this.loadAmount = loadAmount; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
}
