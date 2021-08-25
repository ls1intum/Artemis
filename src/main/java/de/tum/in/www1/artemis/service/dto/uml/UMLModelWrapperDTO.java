package de.tum.in.www1.artemis.service.dto.uml;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude
public class UMLModelWrapperDTO {

    private String model;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
