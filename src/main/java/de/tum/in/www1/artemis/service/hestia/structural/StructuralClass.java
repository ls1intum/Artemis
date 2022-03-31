package de.tum.in.www1.artemis.service.hestia.structural;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Element of the test.json file representing the properties of a class
 * Used for the generation of solution entries for structural test cases
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class StructuralClass {

    @JsonProperty(required = true)
    private String name;

    @JsonProperty(value = "package", required = true)
    private String packageName;

    private String superclass;

    private String[] modifiers;

    @JsonProperty(defaultValue = "false")
    private boolean isInterface;

    @JsonProperty(defaultValue = "false")
    private boolean isEnum;

    private String[] interfaces;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getSuperclass() {
        return superclass;
    }

    public void setSuperclass(String superclass) {
        this.superclass = superclass;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public void setIsInterface(boolean anInterface) {
        isInterface = anInterface;
    }

    public boolean isEnum() {
        return isEnum;
    }

    public void setIsEnum(boolean anEnum) {
        isEnum = anEnum;
    }

    public String[] getInterfaces() {
        return interfaces;
    }

    public void setInterfaces(String[] interfaces) {
        this.interfaces = interfaces;
    }

    public String[] getModifiers() {
        return modifiers;
    }

    public void setModifiers(String[] modifiers) {
        this.modifiers = modifiers;
    }
}
