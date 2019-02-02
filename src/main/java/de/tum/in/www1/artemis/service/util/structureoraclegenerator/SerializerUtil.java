package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.fasterxml.jackson.core.JsonGenerator;
import spoon.reflect.declaration.ModifierKind;

import java.io.IOException;
import java.util.Collection;

/**
 * This class contains methods that are reused throughout the other serializers in order to avoid code repetition.
 */
public abstract class SerializerUtil {

    public static void serializeModifiers(JsonGenerator jsonGenerator, Collection<ModifierKind> modifiers) {
        try {
            jsonGenerator.writeArrayFieldStart("modifiers");
            jsonGenerator.writeStartArray();
            for(ModifierKind modifier : modifiers) {
                jsonGenerator.writeString(modifier.toString());
            }
            jsonGenerator.writeEndArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
