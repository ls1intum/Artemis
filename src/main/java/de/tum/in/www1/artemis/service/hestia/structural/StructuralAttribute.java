package de.tum.in.www1.artemis.service.hestia.structural;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaField;

/**
 * Element of the test.json file representing the properties of an attribute of a class
 * Used for the generation of solution entries for structural test cases
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class StructuralAttribute implements StructuralElement {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty(required = true)
    private String type;

    private final List<String> modifiers = new ArrayList<>();

    private final List<String> annotations = new ArrayList<>();

    @Override
    public String getSourceCode(StructuralClassElements structuralClassElements, JavaClass solutionClass) {
        JavaField solutionAttribute = getSolutionAttribute(solutionClass);
        StringBuilder attributeCode = new StringBuilder();
        if (!this.getAnnotations().isEmpty()) {
            attributeCode.append(getAnnotationsString(this.getAnnotations(), solutionAttribute));
        }
        if (!this.getModifiers().isEmpty()) {
            attributeCode.append(formatModifiers(this.getModifiers())).append(" ");
        }
        if (solutionAttribute != null) {
            attributeCode.append(solutionAttribute.getType().getGenericValue());
        }
        else {
            attributeCode.append(this.getType());
        }
        attributeCode.append(" ").append(this.getName()).append(";");
        return attributeCode.toString();
    }

    private JavaField getSolutionAttribute(JavaClass solutionClass) {
        if (solutionClass == null) {
            return null;
        }
        return solutionClass.getFields().stream().filter(field -> field.getName().equals(this.getName())).findFirst().orElse(null);
    }

    public String getName() {
        return name;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public String getType() {
        return type;
    }

    public List<String> getAnnotations() {
        return annotations;
    }
}
