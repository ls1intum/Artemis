package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import spoon.reflect.declaration.CtEnumValue;

import java.io.IOException;

public class EnumsDiffSerializer extends StdSerializer<EnumsDiff> {
	
    public EnumsDiffSerializer() { this(null); }

    public EnumsDiffSerializer(Class<EnumsDiff> enumsDiffClass) { super(enumsDiffClass); }

	@Override
    public void serialize(EnumsDiff enumsDiff, JsonGenerator jgen, SerializerProvider provider) {
        try {
            // Serialize enum values, if any are specified.
            if(!enumsDiff.enumValues.isEmpty()) {
                jgen.writeArrayFieldStart("enumValues");

                jgen.writeStartArray();

                for(CtEnumValue<?> enumValue : enumsDiff.enumValues) {
                    jgen.writeString(enumValue.getSimpleName());
                }

                jgen.writeEndArray();
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }

}
