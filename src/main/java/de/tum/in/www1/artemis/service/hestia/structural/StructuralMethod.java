package de.tum.in.www1.artemis.service.hestia.structural;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonProperty;

class StructuralMethod {

    @JsonProperty(required = true)
    private String name;

    private String[] modifiers;

    private String[] parameters;

    private String[] annotations;

    @JsonProperty(defaultValue = "void")
    private String returnType;

    public StructuralMethod(String name, String[] modifiers, String[] parameters, String[] annotations, String returnType) {
        this.name = name;
        this.modifiers = modifiers;
        this.parameters = parameters;
        this.annotations = annotations;
        this.returnType = returnType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String[] getModifiers() {
        return modifiers;
    }

    public void setModifiers(String[] modifiers) {
        this.modifiers = modifiers;
    }

    public String[] getParameters() {
        return parameters;
    }

    public void setParameters(String[] parameters) {
        this.parameters = parameters;
    }

    public String[] getAnnotations() {
        return annotations;
    }

    public void setAnnotations(String[] annotations) {
        this.annotations = annotations;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    @Override
    public String toString() {
        return "Method{" + "name='" + name + '\'' + ", modifiers=" + Arrays.toString(modifiers) + ", parameters=" + Arrays.toString(parameters) + ", annotations="
                + Arrays.toString(annotations) + ", returnType='" + returnType + '\'' + '}';
    }
}
