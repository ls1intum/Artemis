package de.tum.cit.aet.artemis.buildagent.dto.testsuite;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CustomFeedback(@JsonProperty("name") String name, @JsonProperty("successful") boolean successful, @JsonProperty("message") String message) {

    public CustomFeedback {
        if (message != null && message.trim().isEmpty()) {
            message = null;
        }
    }
}
