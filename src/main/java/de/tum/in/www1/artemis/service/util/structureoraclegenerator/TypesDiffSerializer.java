package de.tum.in.www1.artemis.service.util.structureoraclegenerator;

import org.json.JSONArray;
import org.json.JSONObject;
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
    public JSONObject serializeHierarchy() {
        JSONObject hierarchyJSON = new JSONObject();

        hierarchyJSON.put("name", typesDiff.name);
        hierarchyJSON.put("package", typesDiff.packageName);

        if(typesDiff.isInterface) {
            hierarchyJSON.put("isInterface", true);
        }
        if(typesDiff.isEnum) {
            hierarchyJSON.put("isEnum", true);
        }
        if(typesDiff.isAbstract) {
            hierarchyJSON.put("isAbstract", true);
        }
        if(!typesDiff.superClassName.isEmpty()){
            hierarchyJSON.put("superclass", typesDiff.superClassName);
        }
        if(typesDiff.superInterfaces.size() > 0) {
            JSONArray superInterfaces = new JSONArray();

            for(CtTypeReference<?> superInterface : typesDiff.superInterfaces) {
                if(!superInterface.isImplicit()) {
                    superInterfaces.put(superInterface.getSimpleName());
                }
            }

            hierarchyJSON.put("interfaces", superInterfaces);
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
    public JSONArray serializeMethods() {
        JSONArray methodsJSON = new JSONArray();

        for(CtMethod<?> method : typesDiff.methodsDiff) {
            JSONObject methodJSON = SerializerUtil.createJsonObject(method.getSimpleName(), method.getModifiers());
            if(!method.getParameters().isEmpty()) {
                methodJSON.put("parameters", SerializerUtil.serializeParameters(method.getParameters(), false));
            }
            methodJSON.put("returnType", method.getType().getSimpleName());
            methodsJSON.put(methodJSON);
        }

        return methodsJSON;
    }

}
