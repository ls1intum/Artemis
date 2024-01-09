package de.tum.in.www1.artemis.service.connectors.hades.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.service.dto.TestCaseDTOInterface;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HadesTestCaseResultDTO implements TestCaseDTOInterface {

    @JsonProperty("name")
    private String name;

    @JsonProperty("message")
    private String message;

    @JsonProperty("status")
    private String status;

    // empty constructor needed for Jackson
    public HadesTestCaseResultDTO() {
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public List<String> getMessage() {
        var list = new ArrayList<String>();
        list.add(message);
        return list;
    }

    public boolean isSuccessful() {
        switch (status) {
            case "SUCCESS": // TODO check if this is correct
                return true;
            case "failed":
                return false;
            default:
                throw new IllegalStateException("Unexpected value: " + status);
        }

    }

    public void setName(String name) {
        this.name = name;
    }

}
