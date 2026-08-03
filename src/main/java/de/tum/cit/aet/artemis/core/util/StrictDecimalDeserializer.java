package de.tum.cit.aet.artemis.core.util;

import java.io.IOException;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

/**
 * Jackson deserializer for {@link Double} fields that must be plain decimal numbers.
 * <p>
 * Rejects scientific notation (e.g. {@code 1e-3}) at the JSON-token level, before it is converted to a
 * {@code Double}. Once parsed, a value like {@code 1e-3} is bit-identical to {@code 0.001} and can no longer be
 * distinguished from an ordinary decimal - any validation performed after parsing is fundamentally unable to
 * reject the input format itself (see <a href="https://github.com/ls1intum/Artemis/issues/12451">issue #12451</a>).
 */
public class StrictDecimalDeserializer extends JsonDeserializer<Double> {

    private static final Pattern PLAIN_DECIMAL_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    @Override
    public Double deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return null;
        }

        String rawValue = parser.getText();
        if (!PLAIN_DECIMAL_PATTERN.matcher(rawValue).matches()) {
            throw InvalidFormatException.from(parser, "Value must be a plain decimal number without scientific notation", rawValue, Double.class);
        }

        return Double.parseDouble(rawValue);
    }
}
