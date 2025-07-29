package de.tum.cit.aet.artemis.nebula.exception;

import java.util.Map;

public class NebulaForbiddenException extends NebulaException {

    public NebulaForbiddenException() {
        super("artemisApp.exerciseChatbot.errors.unauthorized", Map.of());
    }
}
