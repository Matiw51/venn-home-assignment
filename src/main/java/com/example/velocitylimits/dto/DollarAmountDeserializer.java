package com.example.velocitylimits.dto;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Deserializes dollar-prefixed amount strings (e.g. {@code "$123.45"}) into {@link BigDecimal}.
 * Strips the leading {@code $} and any surrounding whitespace before parsing.
 */
public class DollarAmountDeserializer extends StdDeserializer<BigDecimal> {

    public DollarAmountDeserializer() {
        super(BigDecimal.class);
    }

    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext context) throws IOException {
        String raw = p.getText().replace("$", "").trim();
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new JsonParseException(p, "Invalid amount format: " + p.getText());
        }
    }
}
