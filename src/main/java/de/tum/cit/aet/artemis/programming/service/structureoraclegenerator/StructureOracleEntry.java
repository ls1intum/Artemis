package de.tum.cit.aet.artemis.programming.service.structureoraclegenerator;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One class of the structure oracle (the {@code test.json} file of a Java exercise): what the solution declares and the template does not.
 * <p>
 * The oracle is read by the structural tests, which treat an absent key differently from an empty array, so the keys marked as nullable are left out when they are {@code null}
 * and written when they are empty. The {@link OracleGenerator} mapper therefore omits {@code null} values only.
 *
 * @param classProperties the properties of the class itself
 * @param methods         the methods the student has to write, absent when the diff contains none
 * @param attributes      the attributes the student has to add, absent when the diff contains none
 * @param enumValues      the enum values the student has to add, absent when the diff contains none
 * @param constructors    the constructors the student has to write, absent when the diff contains none
 */
@JsonPropertyOrder({ "class", "methods", "attributes", "enumValues", "constructors" })
record StructureOracleEntry(@JsonProperty("class") OracleClass classProperties, @Nullable List<OracleMethod> methods, @Nullable List<OracleAttribute> attributes,
        @Nullable List<String> enumValues, @Nullable List<OracleConstructor> constructors) {

    /**
     * The identity and hierarchy of a class in the oracle.
     *
     * @param name        the simple name of the type
     * @param packageName the package of the type
     * @param isInterface whether the interface stereotype differs from the template
     * @param isEnum      whether the enum stereotype differs from the template
     * @param isAbstract  whether the abstract modifier differs from the template
     * @param superclass  the simple name of the superclass, absent when there is none to check
     * @param interfaces  the simple names of the super interfaces, absent when there are none to check
     * @param annotations the simple names of the annotations, absent when there are none to check
     */
    @JsonPropertyOrder({ "name", "package", "isInterface", "isEnum", "isAbstract", "superclass", "interfaces", "annotations" })
    record OracleClass(String name, @JsonProperty("package") String packageName, @JsonProperty("isInterface") boolean isInterface, @JsonProperty("isEnum") boolean isEnum,
            @JsonProperty("isAbstract") boolean isAbstract, @Nullable String superclass, @Nullable List<String> interfaces, @Nullable List<String> annotations) {
    }

    /**
     * An attribute (field) of a class in the oracle.
     *
     * @param name        the name of the attribute
     * @param modifiers   the modifiers of the attribute
     * @param annotations the simple names of the annotations, absent when the attribute carries none
     * @param type        the simple type name of the attribute
     */
    @JsonPropertyOrder({ "name", "modifiers", "annotations", "type" })
    record OracleAttribute(String name, List<String> modifiers, @Nullable List<String> annotations, String type) {
    }

    /**
     * A constructor of a class in the oracle.
     *
     * @param modifiers   the modifiers of the constructor
     * @param parameters  the simple type names of the parameters
     * @param annotations the simple names of the annotations
     */
    @JsonPropertyOrder({ "modifiers", "parameters", "annotations" })
    record OracleConstructor(List<String> modifiers, List<String> parameters, List<String> annotations) {
    }

    /**
     * A method of a class in the oracle.
     *
     * @param name        the name of the method
     * @param modifiers   the modifiers of the method
     * @param annotations the simple names of the annotations, absent when the method carries none
     * @param parameters  the simple type names of the parameters
     * @param returnType  the simple type name of the return value
     */
    @JsonPropertyOrder({ "name", "modifiers", "annotations", "parameters", "returnType" })
    record OracleMethod(String name, List<String> modifiers, @Nullable List<String> annotations, List<String> parameters, String returnType) {
    }
}
