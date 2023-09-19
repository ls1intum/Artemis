package de.tum.in.www1.artemis.service.iris.exception;

import java.util.Map;

public class IrisNoResponseException extends IrisException {

    public IrisNoResponseException() {
        super("artemisApp.exerciseChatbot.errors.noResponse", Map.of());
    }
}
