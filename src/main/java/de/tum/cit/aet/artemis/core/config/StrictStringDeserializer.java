package de.tum.cit.aet.artemis.core.config;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Jackson deserializer for {@link String} fields that must hold a JSON string.
 * <p>
 * Unlike the default {@code String} deserializer, this rejects numbers and booleans instead of coercing them to their text. A mismatch is reported as an input mismatch.
 * An explicit JSON {@code null} (and an absent field) is preserved as {@code null}, because Jackson handles nulls before this deserializer runs.
 */
public class StrictStringDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            return parser.getString();
        }
        return context.reportInputMismatch(String.class, "Expected a string, but got: %s", parser.getValueAsString(""));
    }
}
