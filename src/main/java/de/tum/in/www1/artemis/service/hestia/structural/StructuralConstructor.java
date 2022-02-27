package de.tum.in.www1.artemis.service.hestia.structural;

import java.util.Arrays;

class StructuralConstructor {

    private String[] modifiers;

    private String[] parameters;

    public StructuralConstructor(String[] modifiers, String[] parameters) {
        this.modifiers = modifiers;
        this.parameters = parameters;
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

    @Override
    public String toString() {
        return "Constructor{" + "modifiers=" + Arrays.toString(modifiers) + ", parameters=" + Arrays.toString(parameters) + '}';
    }
}
