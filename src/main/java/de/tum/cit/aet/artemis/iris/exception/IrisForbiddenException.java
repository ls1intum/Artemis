package de.tum.cit.aet.artemis.iris.exception;

import java.util.Map;

public class IrisForbiddenException extends IrisException {

    public IrisForbiddenException() {
        super("artemisApp.exerciseChatbot.errors.forbidden", Map.of());
    }
}
