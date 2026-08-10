package de.tum.cit.aet.artemis.core.util;

import org.springframework.http.HttpHeaders;

/**
 * Utility class for HTTP headers creation.
 */
public final class HeaderUtil {

    private HeaderUtil() {
    }

    public static HttpHeaders createAlert(String applicationName, String message, String param) {
        return de.tum.cit.aet.artemis.core.web.util.HeaderUtil.createAlert(applicationName, message, param);
    }

    public static HttpHeaders createEntityCreationAlert(String applicationName, boolean enableTranslation, String entityName, String param) {
        return de.tum.cit.aet.artemis.core.web.util.HeaderUtil.createEntityCreationAlert(applicationName, enableTranslation, entityName, param);
    }

    public static HttpHeaders createEntityUpdateAlert(String applicationName, boolean enableTranslation, String entityName, String param) {
        return de.tum.cit.aet.artemis.core.web.util.HeaderUtil.createEntityUpdateAlert(applicationName, enableTranslation, entityName, param);
    }

    public static HttpHeaders createEntityDeletionAlert(String applicationName, boolean enableTranslation, String entityName, String param) {
        return de.tum.cit.aet.artemis.core.web.util.HeaderUtil.createEntityDeletionAlert(applicationName, enableTranslation, entityName, param);
    }

    public static HttpHeaders createFailureAlert(String applicationName, boolean enableTranslation, String entityName, String errorKey, String defaultMessage) {
        HttpHeaders headers = de.tum.cit.aet.artemis.core.web.util.HeaderUtil.createFailureAlert(applicationName, enableTranslation, entityName, errorKey, defaultMessage);
        headers.add("X-" + applicationName + "-message", toHeaderValue(defaultMessage));
        return headers;
    }

    /**
     * Makes an arbitrary exception message safe to send as an HTTP header value.
     * <p>
     * Header values must not contain CR/LF (RFC 9110); when they do, the servlet container aborts writing the
     * response and the client receives the container's generic error page instead of the intended problem detail,
     * losing the response body entirely. Exception messages are free-form and occasionally multi-line
     * (e.g. the per-feedback correction errors of an example submission assessment), so they are folded here.
     *
     * @param message the message to place into a header, may be null
     * @return the message with all control characters replaced by single spaces, or null if the message was null
     */
    private static String toHeaderValue(String message) {
        if (message == null) {
            return null;
        }
        return message.replaceAll("[\\p{Cntrl}]+", " ");
    }
}
