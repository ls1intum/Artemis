package de.tum.in.www1.artemis.service.hestia.structural;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Element of the test.json file representing the properties of a constructor of a class
 * Used for the generation of solution entries for structural test cases
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class StructuralConstructor {

    private String[] modifiers;

    private String[] parameters;

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
}
