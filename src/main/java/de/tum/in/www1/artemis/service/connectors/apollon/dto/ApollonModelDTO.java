package de.tum.in.www1.artemis.service.connectors.apollon.dto;

import java.io.Serializable;

public class ApollonModelDTO implements Serializable {

    private String model;

    public void setModel(String model) {
        this.model = model;
    }

    public String getModel() {
        return model;
    }
}
