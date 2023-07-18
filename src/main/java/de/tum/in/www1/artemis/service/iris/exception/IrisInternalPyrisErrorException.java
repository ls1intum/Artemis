package de.tum.in.www1.artemis.service.iris.exception;

import java.util.Map;

public class IrisInternalPyrisErrorException extends IrisException {

    public IrisInternalPyrisErrorException(String pyrisErrorMessage) {
        super("artemisApp.exerciseChatbot.errors.internalPyrisError", Map.of("pyrisErrorMessage", pyrisErrorMessage));
    }
}
