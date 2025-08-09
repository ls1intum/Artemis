package de.tum.cit.aet.artemis.nebula.exception;

import java.util.Map;

public class NebulaInternalErrorException extends NebulaException {

    public NebulaInternalErrorException(String pyrisErrorMessage) {
        super("artemisApp.nebula.internalNebulaError", Map.of("nebulaErrorMessage", pyrisErrorMessage));
    }
}
