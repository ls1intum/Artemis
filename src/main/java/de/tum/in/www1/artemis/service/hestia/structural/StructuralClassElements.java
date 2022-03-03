package de.tum.in.www1.artemis.service.hestia.structural;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Root element of the test.json file
 * Used for the generation of solution entries for structural test cases
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class StructuralClassElements {

    @JsonProperty(value = "class", required = true)
    private StructuralClass structuralClass;

    private StructuralMethod[] methods;

    private StructuralAttribute[] attributes;

    private StructuralConstructor[] constructors;

    private String[] enumValues;

    public StructuralClass getStructuralClass() {
        return structuralClass;
    }

    public void setStructuralClass(StructuralClass structuralClass) {
        this.structuralClass = structuralClass;
    }

    public StructuralMethod[] getMethods() {
        return methods;
    }

    public void setMethods(StructuralMethod[] methods) {
        this.methods = methods;
    }

    public StructuralAttribute[] getAttributes() {
        return attributes;
    }

    public void setAttributes(StructuralAttribute[] attributes) {
        this.attributes = attributes;
    }

    public StructuralConstructor[] getConstructors() {
        return constructors;
    }

    public void setConstructors(StructuralConstructor[] constructors) {
        this.constructors = constructors;
    }

    public String[] getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(String[] enumValues) {
        this.enumValues = enumValues;
    }

    @Override
    public String toString() {
        return "ClassStructuralElements{" + "thisClass=" + structuralClass + ", methods=" + Arrays.toString(methods) + ", attributes=" + Arrays.toString(attributes)
                + ", constructors=" + Arrays.toString(constructors) + ", enumValues=" + Arrays.toString(enumValues) + '}';
    }
}
