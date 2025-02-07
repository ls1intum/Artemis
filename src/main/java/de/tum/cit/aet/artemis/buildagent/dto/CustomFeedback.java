package de.tum.cit.aet.artemis.buildagent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CustomFeedback(@JsonProperty("name") String name, @JsonProperty("successful") boolean successful, @JsonProperty("message") String message) {

    public String getMessage() {
        return message != null ? message : "";
    }
}
