package de.tum.in.www1.artemis.web.rest.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ApollonConversionDTO implements Serializable {

    private String diagram;

    public void setDiagram(String diagram) {
        this.diagram = diagram;
    }

    public String getDiagram() {
        return diagram;
    }

}
