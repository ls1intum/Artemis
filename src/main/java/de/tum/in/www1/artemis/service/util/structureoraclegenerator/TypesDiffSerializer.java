package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;

/**
 * This class is used to serialize the elements that are generally defined in types, e.g. methods and various properties
 * of the types. These properties are defined as the hierarchy of the types.
 */
public class TypesDiffSerializer {

    private TypesDiff typesDiff;

    public TypesDiffSerializer(TypesDiff typesDiff) {
        this.typesDiff = typesDiff;
    }

    /**
     * This method is used to serialize the hierarchy properties of each type defined in the types diff
     * into a JSON object containing the following information:
     * - Name of the type
     * - Package of the type
     * - Interface stereotype
     * - Enum stereotype
     * - Abstract stereotype
     * - Superclass (if any)
     * - Super interfaces (if any)
     * @return The JSON object consisting of JSON objects representation for the wanted hierarchy properties of a type
     * defined in the types diff.
     */
    public JsonObject serializeHierarchy() {
        JsonObject hierarchyJSON = new JsonObject();

        hierarchyJSON.addProperty("name", typesDiff.name);
        hierarchyJSON.addProperty("package", typesDiff.packageName);

        if(typesDiff.isInterface) {
            hierarchyJSON.addProperty("isInterface", true);
        }
        if(typesDiff.isEnum) {
            hierarchyJSON.addProperty("isEnum", true);
        }
        if(typesDiff.isAbstract) {
            hierarchyJSON.addProperty("isAbstract", true);
        }
        if(!typesDiff.superClassName.isEmpty()){
            hierarchyJSON.addProperty("superclass", typesDiff.superClassName);
        }
        if(typesDiff.superInterfaces.size() > 0) {
            JsonArray superInterfaces = new JsonArray();

            for(CtTypeReference<?> superInterface : typesDiff.superInterfaces) {
                if(!superInterface.isImplicit()) {
                    superInterfaces.add(superInterface.getSimpleName());
                }
            }
            hierarchyJSON.add("interfaces", superInterfaces);
        }
        return hierarchyJSON;
    }

    /**
     * This method is used to serialize the methods of a type into a JSON array containing the following information for each
     * method defined in the classes packed into a JSON object:
     * - Name
     * - Modifiers (if any)
     * - Parameter types (if any)
     * - Return type
     * @return The JSON array consisting of JSON objects representation for each method defined in the types diff.
     */
    public JsonArray serializeMethods() {
        JsonArray methodsJSON = new JsonArray();

        for(CtMethod<?> method : typesDiff.methodsDiff) {
            JsonObject methodJSON = SerializerUtil.createJsonObject(method.getSimpleName(), method.getModifiers());
            if(!method.getParameters().isEmpty()) {
                methodJSON.add("parameters", SerializerUtil.serializeParameters(method.getParameters(), false));
            }
            methodJSON.addProperty("returnType", method.getType().getSimpleName());
            methodsJSON.add(methodJSON);
        }
        return methodsJSON;
    }
}
