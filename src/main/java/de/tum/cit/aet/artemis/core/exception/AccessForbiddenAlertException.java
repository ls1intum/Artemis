package de.tum.cit.aet.artemis.core.exception;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

public class AccessForbiddenAlertException extends HttpStatusException {

    public AccessForbiddenAlertException(String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, defaultMessage, entityName, errorKey, false);
    }

    public AccessForbiddenAlertException(URI type, String defaultMessage, String entityName, String errorKey, boolean skipAlert) {
        super(type, defaultMessage, HttpStatus.FORBIDDEN, entityName, errorKey, getAlertParameters(entityName, errorKey, skipAlert));
    }

    /**
     * Creates a 403 response whose {@code errorKey} translation takes placeholders, e.g. a date the client has to
     * render in the user's locale and time zone.
     *
     * @param defaultMessage        that will be displayed if the translation is not found in the client side i18n error.json files
     * @param entityName            of the component where the error occurred
     * @param errorKey              that matches a translation key in the client side i18n error.json files
     * @param translationParameters the values for the placeholders of that translation, sent to the client as {@code params}
     * @param skipAlert             if the client side intercepting alert service should stay silent because the component
     *                                  handling the error displays a more concrete message itself
     */
    public AccessForbiddenAlertException(String defaultMessage, String entityName, String errorKey, Map<String, Object> translationParameters, boolean skipAlert) {
        super(ErrorConstants.PARAMETERIZED_TYPE, defaultMessage, HttpStatus.FORBIDDEN, entityName, errorKey,
                alertParametersWithTranslationParameters(errorKey, translationParameters, skipAlert));
    }

    private static Map<String, Object> alertParametersWithTranslationParameters(String errorKey, Map<String, Object> translationParameters, boolean skipAlert) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("message", "error." + errorKey);
        if (skipAlert) {
            parameters.put("skipAlert", true);
        }
        // the client reads the translation placeholders from "params", see AlertService
        parameters.put("params", translationParameters);
        return parameters;
    }
}
