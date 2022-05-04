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

    private List<String> modifiers = new ArrayList<>();

    private List<String> annotations = new ArrayList<>();

    @Override
    public String getSourceCode(StructuralClassElements structuralClassElements, JavaClass solutionClass) {
        JavaField solutionAttribute = getSolutionAttribute(solutionClass);
        String attributeCode = "";
        if (!this.getAnnotations().isEmpty()) {
            attributeCode += getAnnotationsString(this.getAnnotations(), solutionAttribute);
        }
        if (!this.getModifiers().isEmpty()) {
            attributeCode += formatModifiers(this.getModifiers()) + " ";
        }
        attributeCode += (solutionAttribute == null ? this.getType() : solutionAttribute.getType().getGenericValue()) + " ";
        attributeCode += this.getName();
        attributeCode += ";";
        return attributeCode;
    }

    private JavaField getSolutionAttribute(JavaClass solutionClass) {
        return solutionClass == null ? null : solutionClass.getFields().stream().filter(field -> field.getName().equals(this.getName())).findFirst().orElse(null);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public void setModifiers(List<String> modifiers) {
        this.modifiers = modifiers;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<String> annotations) {
        this.annotations = annotations;
    }
}
