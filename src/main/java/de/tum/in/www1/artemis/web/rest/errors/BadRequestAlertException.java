package de.tum.in.www1.artemis.web.rest.errors;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

public class BadRequestAlertException extends AbstractThrowableProblem {

    private static final long serialVersionUID = 1L;

    private final String entityName;

    private final String errorKey;

    public BadRequestAlertException(String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, skipAlert);
    }

    public BadRequestAlertException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public BadRequestAlertException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        this(type, defaultMessage, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }

    public BadRequestAlertException(URI type, String defaultMessage, String entityName, String errorKey, Map<String, Object> parameters) {
        super(type, defaultMessage, Status.BAD_REQUEST, null, null, null, parameters);
        this.entityName = entityName;
        this.errorKey = errorKey;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getErrorKey() {
        return errorKey;
    }

    private static Map<String, Object> getAlertParameters(String entityName, String errorKey, boolean skipAlert) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("message", "error." + errorKey);
        if (skipAlert) {
            parameters.put("skipAlert", true);
        }
        parameters.put("params", entityName);
        return parameters;
    }
}
