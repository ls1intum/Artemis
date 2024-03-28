package de.tum.in.www1.artemis.service.hestia.structural;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.qdox.model.JavaClass;

/**
 * Element of the test.json file representing the properties of a class
 * Used for the generation of solution entries for structural test cases
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class StructuralClass implements StructuralElement {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty(value = "package", required = true)
    private String packageName;

    private String superclass;

    private final List<String> modifiers = new ArrayList<>();

    @JsonProperty(defaultValue = "false")
    private boolean isInterface;

    @JsonProperty(defaultValue = "false")
    private boolean isEnum;

    private final List<String> interfaces = new ArrayList<>();

    private final List<String> annotations = new ArrayList<>();

    @Override
    public String getSourceCode(StructuralClassElements structuralClassElements, JavaClass solutionClass) {
        StringBuilder classSolutionCode = new StringBuilder("package ").append(this.getPackageName()).append(";\n\n");

        if (!this.getAnnotations().isEmpty()) {
            classSolutionCode.append(getAnnotationsString(this.getAnnotations(), solutionClass));
        }
        classSolutionCode.append(getClassHeaderCode(solutionClass)).append("{\n").append(SINGLE_INDENTATION);  // Class Body
        if (this.isEnum()) {
            classSolutionCode.append(String.join(", ", structuralClassElements.getEnumValues()));
        }
        classSolutionCode.append("\n}");

        return classSolutionCode.toString();
    }

    private String getClassHeaderCode(JavaClass solutionClass) {
        StringBuilder classHeaderCode = new StringBuilder();
        if (!this.getModifiers().isEmpty()) {
            classHeaderCode.append(formatModifiers(this.getModifiers())).append(" ");
        }

        if (this.isInterface) {
            classHeaderCode.append("interface ");
        }
        else if (this.isEnum) {
            classHeaderCode.append("enum ");
        }
        else {
            classHeaderCode.append("class ");
        }
        classHeaderCode.append(this.getName());

        if (solutionClass != null && !solutionClass.getTypeParameters().isEmpty()) {
            classHeaderCode.append(getGenericTypesString(solutionClass.getTypeParameters()));
        }
        classHeaderCode.append(" ");
        classHeaderCode.append(getInheritanceCode());
        return classHeaderCode.toString();
    }

    private String getInheritanceCode() {
        StringBuilder inheritanceCode = new StringBuilder();
        if (this.getSuperclass() != null) {
            inheritanceCode.append("extends ").append(this.getSuperclass()).append(" ");
        }
        if (!this.getInterfaces().isEmpty()) {
            inheritanceCode.append("implements ").append(String.join(", ", this.getInterfaces())).append(" ");
        }
        return inheritanceCode.toString();
    }

    public String getName() {
        return name;
    }

    public String getSuperclass() {
        return superclass;
    }

    public String getPackageName() {
        return packageName;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public boolean isEnum() {
        return isEnum;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public List<String> getAnnotations() {
        return annotations;
    }
}
