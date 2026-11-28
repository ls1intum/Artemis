package de.tum.cit.aet.artemis.core.config;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Jackson deserializer for {@link Integer} fields that must hold a whole number.
 * <p>
 * Unlike the default {@code Integer} deserializer, this rejects fractional JSON numbers (e.g. {@code 10.5}) and textual values (e.g. {@code "10.5"}) instead of silently
 * truncating or coercing them. It is meant for domain fields such as point or score values where a decimal input is a client error, not something to round away. A parse
 * failure is reported as an input mismatch so Spring maps it to HTTP 400 (Bad Request). An explicit JSON {@code null} (and an absent field) is preserved as {@code null},
 * because Jackson handles nulls before this deserializer runs, so nullable "unset" fields keep working.
 */
public class StrictIntegerDeserializer extends ValueDeserializer<Integer> {

    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }
        // Require an integral JSON number in int range. VALUE_NUMBER_INT rejects fractional numbers (e.g. 10.5) that the default deserializer would truncate to 10, and also
        // rejects textual values; the range check guards the int column range. Reported as an input mismatch so Spring maps it to 400 Bad Request instead of 500.
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            Number number = parser.getNumberValue();
            if (number instanceof Integer value) {
                return value;
            }
            if (number instanceof Long value && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return value.intValue();
            }
        }
        return context.reportInputMismatch(Integer.class, "Expected a whole number, but got: %s", parser.getValueAsString(""));
    }
}
