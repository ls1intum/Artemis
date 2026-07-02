package de.tum.cit.aet.artemis.programming.service.hades;

public enum ScriptType {

    SHELL("shell"), GROOVY("groovy");

    private final String value;

    ScriptType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
