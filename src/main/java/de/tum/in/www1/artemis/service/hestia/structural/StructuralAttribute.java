package de.tum.in.www1.artemis.service.hestia.structural;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
class StructuralAttribute {

    @JsonProperty(required = true)
    private String name;

    private String[] modifiers;

    @JsonProperty(required = true)
    private String type;

    public StructuralAttribute() {
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "Attribute{" + "name='" + name + '\'' + ", modifiers=" + Arrays.toString(modifiers) + ", type='" + type + '\'' + '}';
    }
}
