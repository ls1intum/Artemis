package de.tum.in.www1.artemis.service.iris.exception;

import java.util.Map;

public class IrisForbiddenException extends IrisException {

    public IrisForbiddenException() {
        super("artemisApp.exerciseChatbot.errors.forbidden", Map.of());
    }
}
