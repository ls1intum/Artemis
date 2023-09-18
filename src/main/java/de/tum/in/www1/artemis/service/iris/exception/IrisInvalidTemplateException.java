package de.tum.in.www1.artemis.service.iris.exception;

import java.util.Map;

public class IrisInvalidTemplateException extends IrisException {

    public IrisInvalidTemplateException(String pyrisErrorMessage) {
        super("artemisApp.exerciseChatbot.errors.invalidTemplate", Map.of("pyrisErrorMessage", pyrisErrorMessage));
    }
}
