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
        headers.add("X-" + applicationName + "-message", defaultMessage);
        return headers;
    }
}
