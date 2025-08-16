package de.tum.cit.aet.artemis.nebula.exception;

import java.util.Map;

import org.zalando.problem.Status;

public class NebulaInternalErrorException extends NebulaException {

    public NebulaInternalErrorException(String pyrisErrorMessage) {
        super("artemisApp.nebula.internalNebulaError", Map.of("nebulaErrorMessage", pyrisErrorMessage), Status.INTERNAL_SERVER_ERROR);
    }
}
