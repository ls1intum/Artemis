package de.tum.cit.aet.artemis.core.config;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Jackson deserializer for {@link Long} fields that must hold a whole number, such as the id of another entity.
 * <p>
 * Unlike the default {@code Long} deserializer, this rejects fractional JSON numbers (e.g. {@code 1.9}) and textual values instead of silently truncating or coercing them,
 * because a truncated id would name a different entity than the client sent. A parse failure is reported as an input mismatch so Spring maps it to HTTP 400 (Bad Request).
 * An explicit JSON {@code null} (and an absent field) is preserved as {@code null}, because Jackson handles nulls before this deserializer runs.
 */
public class StrictLongDeserializer extends ValueDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            Number number = parser.getNumberValue();
            if (number instanceof Integer || number instanceof Long) {
                return number.longValue();
            }
        }
        return context.reportInputMismatch(Long.class, "Expected a whole number, but got: %s", parser.getValueAsString(""));
    }
}
