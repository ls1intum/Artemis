package de.tum.in.www1.artemis.service.hestia.structural;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaMethod;
import com.thoughtworks.qdox.model.JavaParameter;

/**
 * Element of the test.json file representing the properties of a method of a class
 * Used for the generation of solution entries for structural test cases
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class StructuralMethod implements StructuralElement {

    @JsonProperty(required = true)
    private String name;

    private List<String> modifiers = new ArrayList<>();

    private List<String> parameters = new ArrayList<>();

    private List<String> annotations = new ArrayList<>();

    @JsonProperty(defaultValue = "void")
    private String returnType;

    @Override
    public String getSourceCode(StructuralClassElements structuralClassElements, JavaClass solutionClass) {
        JavaMethod solutionMethod = getSolutionMethod(solutionClass);
        String methodSolutionCode = "";

        if (!this.getAnnotations().isEmpty()) {
            methodSolutionCode += getAnnotationsString(this.getAnnotations(), solutionMethod != null ? solutionMethod.getAnnotations() : null);
        }

        var modifiers = Lists.newArrayList(this.getModifiers());
        var isAbstract = modifiers.contains("abstract");
        // Adjust modifiers for interfaces
        if (structuralClassElements.getStructuralClass().isInterface()) {
            if (isAbstract) {
                modifiers.remove("abstract");
            }
            else {
                modifiers.add(0, "default");
            }
        }
        if (!modifiers.isEmpty()) {
            methodSolutionCode += formatModifiers(modifiers) + " ";
        }

        List<JavaParameter> solutionParameters = solutionMethod == null ? Collections.emptyList() : solutionMethod.getParameters();
        if (solutionMethod != null && !solutionMethod.getTypeParameters().isEmpty()) {
            methodSolutionCode += getGenericTypesString(solutionMethod.getTypeParameters()) + " ";
        }

        if (solutionMethod != null) {
            methodSolutionCode += solutionMethod.getReturnType().getGenericValue() + " ";
        }
        else {
            methodSolutionCode += this.getReturnType() + " ";
        }

        methodSolutionCode += this.getName();

        methodSolutionCode += generateParametersString(this.getParameters(), solutionParameters);
        // Remove the method body if the method is abstract
        if (isAbstract) {
            methodSolutionCode += ";";
        }
        else {
            methodSolutionCode += " {\n" + SINGLE_INDENTATION + "\n}";
        }

        return methodSolutionCode;
    }

    /**
     * Finds the QDox method in a given class by its test.json specification
     *
     * @param solutionClass The QDox class instance
     * @return The QDox method instance or null if not found
     */
    private JavaMethod getSolutionMethod(JavaClass solutionClass) {
        if (solutionClass == null) {
            return null;
        }
        return solutionClass.getMethods().stream().filter(javaMethod -> javaMethod.getName().equals(this.getName())).filter(javaMethod -> {
            var genericTypes = new ArrayList<>(solutionClass.getTypeParameters());
            genericTypes.addAll(javaMethod.getTypeParameters());
            return doParametersMatch(this.getParameters(), javaMethod.getParameters(), genericTypes);
        }).findFirst().orElse(null);
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

    public List<String> getParameters() {
        return parameters;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<String> annotations) {
        this.annotations = annotations;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }
}
