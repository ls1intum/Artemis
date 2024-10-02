package de.tum.cit.aet.artemis.iris.exception;

import java.util.HashMap;
import java.util.Map;

import org.zalando.problem.Status;

import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.core.exception.HttpStatusException;

public class IrisException extends HttpStatusException {

    protected final String translationKey;

    protected final Map<String, Object> translationParams;

    public IrisException(String translationKey, Map<String, Object> translationParams) {
        super(ErrorConstants.DEFAULT_TYPE, "An error within Iris has occured", Status.INTERNAL_SERVER_ERROR, "Iris", translationKey,
                getAlertParameters(translationKey, translationParams));
        this.translationKey = translationKey;
        this.translationParams = translationParams;
    }

    public IrisException(String defaultMessage, Status status, String entityName, String translationKey, Map<String, Object> translationParams) {
        super(ErrorConstants.DEFAULT_TYPE, defaultMessage, status, entityName, translationKey, getAlertParameters(translationKey, translationParams));
        this.translationKey = translationKey;
        this.translationParams = translationParams;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public Map<String, Object> getTranslationParams() {
        return translationParams;
    }

    protected static Map<String, Object> getAlertParameters(String translationKey, Map<String, Object> translationParams) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("message", translationKey);
        parameters.put("params", translationParams);
        parameters.put("skipAlert", true);
        return parameters;
    }
}
