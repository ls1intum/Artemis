package de.tum.in.www1.artemis.service.iris.exception;

import java.util.Map;

public class IrisParseResponseException extends IrisException {

    public IrisParseResponseException(String message) {
        super("artemisApp.exerciseChatbot.errors.parseResponse", Map.of("cause", message));
    }

    public IrisParseResponseException(Throwable cause) {
        this(cause.getMessage());
    }
}
