package de.tum.in.www1.artemis.service.connectors.hades.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.service.dto.TestCaseBaseDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HadesTestCaseResultDTO(@JsonProperty("name") String name, @JsonProperty("message") String message, @JsonProperty("status") String status) implements TestCaseBaseDTO {

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<String> getTestMessages() {
        var list = new ArrayList<String>();
        list.add(message);
        return list;
    }

    public boolean isSuccessful() {
        return switch (status) {
            case "passed" -> true;
            case "failed" -> false;
            default -> throw new IllegalStateException("Unexpected value: " + status);
        };
    }

}
