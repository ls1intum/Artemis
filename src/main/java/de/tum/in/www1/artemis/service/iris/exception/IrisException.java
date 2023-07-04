package de.tum.in.www1.artemis.service.iris.exception;

import java.util.HashMap;
import java.util.Map;

import org.zalando.problem.Status;

import de.tum.in.www1.artemis.web.rest.errors.ErrorConstants;
import de.tum.in.www1.artemis.web.rest.errors.HttpStatusException;

public class IrisException extends HttpStatusException {

    protected String translationKey;

    protected Map<String, Object> translationParams;

    public IrisException(String translationKey, Map<String, Object> translationParams) {
        super(ErrorConstants.DEFAULT_TYPE, "An error within Iris has occured", Status.INTERNAL_SERVER_ERROR, "Iris", translationKey,
                getAlertParameters(translationKey, translationParams));
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
