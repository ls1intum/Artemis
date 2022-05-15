package de.tum.in.www1.artemis.service.hestia.structural;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.thoughtworks.qdox.model.JavaClass;
import com.thoughtworks.qdox.model.JavaConstructor;

/**
 * Element of the test.json file representing the properties of a constructor of a class
 * Used for the generation of solution entries for structural test cases
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class StructuralConstructor implements StructuralElement {

    private List<String> modifiers = new ArrayList<>();

    private List<String> parameters = new ArrayList<>();

    private List<String> annotations = new ArrayList<>();

    @Override
    public String getSourceCode(StructuralClassElements structuralClassElements, JavaClass solutionClass) {
        JavaConstructor solutionConstructor = getSolutionConstructor(solutionClass);
        String constructorSolutionCode = "";
        if (!this.getAnnotations().isEmpty()) {
            constructorSolutionCode += getAnnotationsString(this.getAnnotations(), solutionConstructor);
        }
        if (!this.getModifiers().isEmpty()) {
            constructorSolutionCode += formatModifiers(this.getModifiers()) + " ";
        }
        constructorSolutionCode += structuralClassElements.getStructuralClass().getName();
        constructorSolutionCode += generateParametersString(this.getParameters(), solutionConstructor) + " ";
        constructorSolutionCode += "{\n" + SINGLE_INDENTATION + "\n}";
        return constructorSolutionCode;
    }

    /**
     * Extracts the parameters from a constructor
     *
     * @param solutionClass The QDox class instance
     * @return The parameters of the constructor
     */
    private JavaConstructor getSolutionConstructor(JavaClass solutionClass) {
        if (solutionClass == null) {
            return null;
        }
        return solutionClass.getConstructors().stream()
                .filter(javaConstructor -> doParametersMatch(this.getParameters(), javaConstructor.getParameters(), solutionClass.getTypeParameters())).findFirst().stream()
                .findFirst().orElse(null);
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
}
