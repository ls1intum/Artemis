package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import org.json.JSONArray;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;

import java.util.Collection;

/**
 * This class contains helper methods for serializing information on structural elements that we deal with repeatedly
 * throughout the other serializers in order to avoid code repetition.
 */
public abstract class SerializerUtil {

    /**
     * This method is used to serialize the string representations of each modifier into a JSON array.
     * @param modifiers A collection of modifiers that needs to get serialized.
     * @return The JSON array containing the string representations of the modifiers.
     */
    public static JSONArray serializeModifiers(Collection<ModifierKind> modifiers) {
        JSONArray modifiersArray = new JSONArray();

        for(ModifierKind modifier : modifiers) {
            modifiersArray.put(modifier.toString());
        }

        return modifiersArray;
    }

    /**
     * This method is used to serialize the string representations of each parameter into a JSON array.
     * @param parameters A collection of modifiers that needs to get serialized.
     * @param executableDeclaringTypeIsEnum Indicator if the executable that defines the given parameters is declared by an enum.
     *                                      This is because these executables implicitly have a String and int parameter that
     *                                      we need to add manually in order to be compatible with the Spoon Framework.
     * @return The JSON array containing the string representations of the parameter types.
     */
    public static JSONArray serializeParameters(Collection<CtParameter<?>> parameters,
                                                boolean executableDeclaringTypeIsEnum) {
        JSONArray parametersArray = new JSONArray();

        for(CtParameter<?> parameter : parameters) {
            if(parameter.isImplicit()) { continue; }

            parametersArray.put(parameter.getType().getSimpleName());

            if(executableDeclaringTypeIsEnum) {
                parametersArray.put("String");
                parametersArray.put("int");
            }
        }

        return parametersArray;
    }

}
