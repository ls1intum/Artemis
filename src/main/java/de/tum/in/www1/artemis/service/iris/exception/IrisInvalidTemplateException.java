package de.tum.in.www1.artemis.service.iris.exception;

import java.util.Map;

public class IrisInvalidTemplateException extends IrisException {

    public IrisInvalidTemplateException(String pyrisErrorMessage) {
        super("artemisApp.iris.error.invalidTemplate", Map.of("pyrisErrorMessage", pyrisErrorMessage));
    }
}
