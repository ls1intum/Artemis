package de.tum.cit.aet.artemis.nebula.exception;

import java.util.Map;

import org.zalando.problem.Status;

public class NebulaForbiddenException extends NebulaException {

    public NebulaForbiddenException() {
        super("artemisApp.nebula.errors.unauthorized", Map.of(), Status.UNAUTHORIZED);
    }
}
