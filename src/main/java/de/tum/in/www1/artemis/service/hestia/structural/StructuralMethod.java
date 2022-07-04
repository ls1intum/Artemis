package de.tum.in.www1.artemis.service.hestia.structural;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaMethod;

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
        boolean isAbstract = this.getModifiers().contains("abstract");

        if (!this.getAnnotations().isEmpty()) {
            methodSolutionCode += getAnnotationsString(this.getAnnotations(), solutionMethod);
        }

        methodSolutionCode += formatModifiers(structuralClassElements, isAbstract);

        // Generics
        if (solutionMethod != null && !solutionMethod.getTypeParameters().isEmpty()) {
            methodSolutionCode += getGenericTypesString(solutionMethod.getTypeParameters()) + " ";
        }

        // Return type
        methodSolutionCode += solutionMethod != null ? solutionMethod.getReturnType().getGenericValue() + " " : this.getReturnType() + " ";
        // Name
        methodSolutionCode += this.getName();
        // Parameters
        methodSolutionCode += generateParametersString(this.getParameters(), solutionMethod);
        // Body
        methodSolutionCode += isAbstract ? ";" : " {\n" + SINGLE_INDENTATION + "\n}";

        return methodSolutionCode;
    }

    /**
     * Formats the modifiers of this method.
     *
     * @param structuralClassElements The elements of the class from the test.json
     * @param isAbstract Whether the method is abstract
     * @return The modifiers as Java code
     */
    private String formatModifiers(StructuralClassElements structuralClassElements, boolean isAbstract) {
        var modifiers = Lists.newArrayList(this.getModifiers());
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
            return formatModifiers(modifiers) + " ";
        }
        return "";
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
