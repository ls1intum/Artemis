package de.tum.in.www1.artemis.service.hestia.structural;

import java.util.ArrayList;
import java.util.List;

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

    private List<StructuralMethod> methods = new ArrayList<>();

    private List<StructuralAttribute> attributes = new ArrayList<>();

    private List<StructuralConstructor> constructors = new ArrayList<>();

    private List<String> enumValues = new ArrayList<>();

    public StructuralClass getStructuralClass() {
        return structuralClass;
    }

    public void setStructuralClass(StructuralClass structuralClass) {
        this.structuralClass = structuralClass;
    }

    public List<StructuralMethod> getMethods() {
        return methods;
    }

    public void setMethods(List<StructuralMethod> methods) {
        this.methods = methods;
    }

    public List<StructuralAttribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<StructuralAttribute> attributes) {
        this.attributes = attributes;
    }

    public List<StructuralConstructor> getConstructors() {
        return constructors;
    }

    public void setConstructors(List<StructuralConstructor> constructors) {
        this.constructors = constructors;
    }

    public List<String> getEnumValues() {
        return enumValues;
    }

    public void setEnumValues(List<String> enumValues) {
        this.enumValues = enumValues;
    }
}
