package de.tum.cit.aet.artemis.core.config;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Jackson deserializer for {@link Number} fields whose owner validates the value itself and must tell an absent field from an unusable one.
 * <p>
 * A JSON number is read as the narrowest of {@link Integer}, {@link Long} and {@code BigInteger} for integral values, and as a {@link Double} otherwise. Anything
 * that is not a number, including an explicit JSON {@code null} and a textual number such as {@code "42"}, is read as {@link Double#NaN}. NaN passes no range or integrality
 * check, so the owner rejects it, while an absent field stays {@code null}. This keeps a malformed value from failing the whole request, so the owner can answer it itself.
 */
public class NumberOrNaNDeserializer extends ValueDeserializer<Number> {

    @Override
    public Number deserialize(JsonParser parser, DeserializationContext context) {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT || token == JsonToken.VALUE_NUMBER_FLOAT) {
            return parser.getNumberValue();
        }
        parser.skipChildren();
        return Double.NaN;
    }

    @Override
    public Number getNullValue(DeserializationContext context) {
        return Double.NaN;
    }

    @Override
    public Number getAbsentValue(DeserializationContext context) {
        return null;
    }
}
