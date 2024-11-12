package de.tum.cit.aet.artemis.buildagent.dto.testsuite;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CustomFeedback {

    @JsonProperty("name")
    String name;

    @JsonProperty("successful")
    boolean successful;

    @JsonProperty("message")
    String message;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
