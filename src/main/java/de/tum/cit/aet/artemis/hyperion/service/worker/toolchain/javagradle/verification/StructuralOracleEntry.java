package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One class of the structure oracle ({@code test.json}) that Ares' structural tests read, as {@link ApprovedStructuralContract} writes it.
 * <p>
 * Ares reads an absent key differently from an empty array, so a component that may be absent is {@code null} when the key must be left out and carries a list, possibly an empty
 * one, when the key must be written. {@link ApprovedStructuralContract} writes the oracle with a mapper that omits only {@code null} values.
 *
 * @param classProperties the identity and hierarchy of the class
 * @param genericApi      the generic shape of the class, absent when the class has no generic or throwing member
 * @param methods         the contract-visible methods, absent when there are none
 * @param attributes      the contract-visible attributes, absent when there are none
 * @param constructors    the contract-visible constructors, absent when there are none
 * @param enumValues      the enum constants, absent when there are none
 */
@JsonPropertyOrder({ "class", "genericApi", "methods", "attributes", "constructors", "enumValues" })
record StructuralOracleEntry(@JsonProperty("class") OracleClass classProperties, @Nullable GenericApi genericApi, @Nullable List<OracleMethod> methods,
        @Nullable List<OracleAttribute> attributes, @Nullable List<OracleConstructor> constructors, @Nullable List<String> enumValues) {

    /**
     * The identity and hierarchy of a class.
     *
     * @param name        the simple name of the type
     * @param packageName the package of the type, empty for the default package
     * @param modifiers   the modifiers Ares matches exactly, absent when the approved type is not public
     * @param isInterface whether the type is an interface
     * @param isEnum      whether the type is an enum
     * @param isAbstract  whether the type is an abstract class
     * @param superclass  the simple name of the superclass, absent when it is {@code Object}
     * @param interfaces  the sorted simple names of the implemented interfaces, absent when there are none
     */
    @JsonPropertyOrder({ "name", "package", "modifiers", "isInterface", "isEnum", "isAbstract", "superclass", "interfaces" })
    record OracleClass(String name, @JsonProperty("package") String packageName, @Nullable List<String> modifiers, @JsonProperty("isInterface") boolean isInterface,
            @JsonProperty("isEnum") boolean isEnum, @JsonProperty("isAbstract") boolean isAbstract, @Nullable String superclass, @Nullable List<String> interfaces) {
    }

    /**
     * The generic shape of a class, which the generic structural test compares exactly.
     *
     * @param parameterCount the number of type parameters of the class
     * @param exerciseTypes  the sorted names of the exercise types, which are reduced to their simple names in the signatures
     * @param signatures     the canonical signatures of the supertypes and contract-visible members
     */
    @JsonPropertyOrder({ "parameterCount", "exerciseTypes", "signatures" })
    record GenericApi(int parameterCount, List<String> exerciseTypes, List<String> signatures) {
    }

    /**
     * A contract-visible method.
     *
     * @param name       the name of the method
     * @param modifiers  the effective modifiers of the method
     * @param parameters the erased simple type names of the parameters, absent when there are none
     * @param returnType the erased simple type name of the return value
     */
    @JsonPropertyOrder({ "name", "modifiers", "parameters", "returnType" })
    record OracleMethod(String name, List<String> modifiers, @Nullable List<String> parameters, String returnType) {
    }

    /**
     * A contract-visible attribute.
     *
     * @param name      the name of the attribute
     * @param modifiers the effective modifiers of the attribute
     * @param type      the erased simple type name of the attribute
     */
    @JsonPropertyOrder({ "name", "modifiers", "type" })
    record OracleAttribute(String name, List<String> modifiers, String type) {
    }

    /**
     * A contract-visible constructor.
     *
     * @param modifiers  the modifiers of the constructor
     * @param parameters the erased simple type names of the parameters, absent when there are none
     */
    @JsonPropertyOrder({ "modifiers", "parameters" })
    record OracleConstructor(List<String> modifiers, @Nullable List<String> parameters) {
    }
}
