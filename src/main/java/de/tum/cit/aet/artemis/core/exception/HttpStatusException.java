package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public abstract class HttpStatusException extends ErrorResponseException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String entityName;

    private final String errorKey;

    /**
     * @param type           of the error (e.g. URI.create("/problem-with-message"))
     * @param defaultMessage that will be displayed if the translation is not found in the client side i18n error.json files
     * @param status         of the http response (e.g. 400 BAD REQUEST)
     * @param entityName     of the component where the error occurred
     * @param errorKey       that matches a translation key in the client side i18n error.json files
     * @param parameters     contains additional information (e.g. the field 'skipAlert' to tell the client side alert service
     *                           interceptor that the error should not be handled by the interceptor - this is useful when the
     *                           component where the error occurred has information to display a more concrete error message)
     */
    public HttpStatusException(URI type, String defaultMessage, HttpStatus status, String entityName, String errorKey, Map<String, Object> parameters) {
        super(status, asProblemDetail(type, defaultMessage, status, entityName, errorKey, parameters), null);
        this.entityName = entityName;
        this.errorKey = errorKey;
    }

    private static ProblemDetail asProblemDetail(URI type, String title, HttpStatus status, String entityName, String errorKey, Map<String, Object> parameters) {
        ProblemDetail detail = ProblemDetail.forStatus(status);
        detail.setType(type);
        detail.setTitle(title);
        // Include entityName and errorKey in the response for backward compatibility with client-side error handling
        detail.setProperty("entityName", entityName);
        detail.setProperty("errorKey", errorKey);
        if (parameters != null) {
            parameters.forEach(detail::setProperty);
        }
        return detail;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getErrorKey() {
        return errorKey;
    }

    /**
     * Returns the title of the problem detail, preserving backward compatibility with the old Zalando API
     * where getMessage() returned just the title string.
     */
    @Override
    public String getMessage() {
        ProblemDetail body = getBody();
        return body.getTitle() != null ? body.getTitle() : super.getMessage();
    }

    /**
     * @param entityName of the component where the error occurred
     * @param errorKey   that matches a translation key in the client side i18n error.json files
     * @param skipAlert  if the error should not be handled by the client side intercepting alert service
     *                       (e.g. when a client side component has more information to display a more concrete error
     *                       message and handles the error instead of the interceptor)
     */
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
