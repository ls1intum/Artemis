package de.tum.in.www1.artemis.service.connectors.jenkins.dto;

public class ErrorOrFailureDTO {

    private String message;

    private String type;

    private String messageWithStackTrace;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessageWithStackTrace() {
        return messageWithStackTrace;
    }

    public void setMessageWithStackTrace(String messageWithStackTrace) {
        this.messageWithStackTrace = messageWithStackTrace;
    }

    public String getMostInformativeMessage() {
        if (messageWithStackTrace != null && !messageWithStackTrace.isBlank())
            return messageWithStackTrace;
        return message;
    }
}
