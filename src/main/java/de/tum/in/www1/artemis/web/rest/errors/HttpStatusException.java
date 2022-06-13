package de.tum.in.www1.artemis.web.rest.errors;

import java.io.Serial;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

public abstract class HttpStatusException extends AbstractThrowableProblem {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String entityName;

    private final String errorKey;

    public HttpStatusException(URI type, String defaultMessage, Status status, String entityName, String errorKey, Map<String, Object> parameters) {
        super(type, defaultMessage, status, null, null, null, parameters);
        this.entityName = entityName;
        this.errorKey = errorKey;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getErrorKey() {
        return errorKey;
    }

    protected static Map<String, Object> getAlertParameters(String entityName, String errorKey, boolean skipAlert) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("message", "error." + errorKey);
        if (skipAlert) {
            parameters.put("skipAlert", true);
        }
        parameters.put("params", entityName);
        return parameters;
    }
}
