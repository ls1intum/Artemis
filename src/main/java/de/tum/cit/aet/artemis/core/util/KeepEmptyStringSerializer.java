package de.tum.cit.aet.artemis.core.util;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdScalarSerializer;

/**
 * Serializes a string that is only left out when it is null, even in a DTO that uses {@code @JsonInclude(NON_EMPTY)}: there, an empty string is
 * dropped like a missing one. Use it for a property whose empty value means something different from a missing one.
 */
public class KeepEmptyStringSerializer extends StdScalarSerializer<String> {

    public KeepEmptyStringSerializer() {
        super(String.class);
    }

    @Override
    public boolean isEmpty(SerializationContext context, String value) {
        return value == null;
    }

    @Override
    public void serialize(String value, JsonGenerator generator, SerializationContext context) {
        generator.writeString(value);
    }
}
