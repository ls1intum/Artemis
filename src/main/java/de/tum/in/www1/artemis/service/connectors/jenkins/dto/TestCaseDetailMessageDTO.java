package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TestCaseDetailMessageDTO(String message, String type, String messageWithStackTrace) {

    public TestCaseDetailMessageDTO(String message) {
        this(message, null, null);
    }

    @JsonIgnore
    public String getMostInformativeMessage() {
        if (messageWithStackTrace != null && !messageWithStackTrace.isBlank()) {
            return messageWithStackTrace;
        }
        return message;
    }
}
