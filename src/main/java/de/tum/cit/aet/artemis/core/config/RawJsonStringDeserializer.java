package de.tum.cit.aet.artemis.core.config;

import java.io.StringWriter;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Jackson deserializer that reads any JSON value into its compact JSON text.
 * <p>
 * It complements {@code @JsonRawValue}, which only governs serialization: a field marked with both round-trips an embedded JSON value through
 * a {@link String}. The value is copied token by token, so it is never modelled as a tree.
 */
public class RawJsonStringDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = context.tokenStreamFactory().createGenerator(writer)) {
            generator.copyCurrentStructure(parser);
        }
        return writer.toString();
    }
}
