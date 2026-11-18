package de.tum.cit.aet.artemis.core.config;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Jackson deserializer for {@link Boolean} fields that must hold a JSON boolean.
 * <p>
 * Unlike the default {@code Boolean} deserializer, this rejects strings and numbers (e.g. {@code "true"} or {@code 1}) instead of coercing them. A mismatch is reported as an
 * input mismatch. An explicit JSON {@code null} (and an absent field) is preserved as {@code null}, because Jackson handles nulls before this deserializer runs.
 */
public class StrictBooleanDeserializer extends ValueDeserializer<Boolean> {

    @Override
    public Boolean deserialize(JsonParser parser, DeserializationContext context) {
        return switch (parser.currentToken()) {
            case VALUE_TRUE -> Boolean.TRUE;
            case VALUE_FALSE -> Boolean.FALSE;
            default -> context.reportInputMismatch(Boolean.class, "Expected a boolean, but got: %s", parser.getValueAsString(""));
        };
    }
}
