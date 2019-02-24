package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.google.gson.JsonArray;
import spoon.reflect.declaration.CtEnumValue;

/**
 * This class is used to serialize the elements that are unique to enums, e.g. enum values.
 */
public class EnumsDiffSerializer {

    private EnumsDiff enumsDiff;

    public EnumsDiffSerializer(EnumsDiff enumsDiff) {
        this.enumsDiff = enumsDiff;
    }

    /**
     * This method is used to serialize the string representations of each enum value into a JSON array.
     * @return The JSON array consisting of string representations of each enum value defined in the enums diff.
     */
    public JsonArray serializeEnumValues(EnumsDiff enumsDiff) {
        JsonArray enumValues = new JsonArray();

        for(CtEnumValue<?> enumValue : enumsDiff.enumValuesDiff) {
            enumValues.add(enumValue.getSimpleName());
        }

        return enumValues;
    }
}
