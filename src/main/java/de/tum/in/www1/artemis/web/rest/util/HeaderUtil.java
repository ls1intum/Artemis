package de.tum.in.www1.artemis.web.rest.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Utility class for HTTP headers creation.
 */
public final class HeaderUtil {

    private HeaderUtil() {
    }

    public static HttpHeaders createAlert(String applicationName, String message, String param) {
        return tech.jhipster.web.util.HeaderUtil.createAlert(applicationName, message, param);
    }

    public static HttpHeaders createEntityCreationAlert(String applicationName, boolean enableTranslation, String entityName, String param) {
        return tech.jhipster.web.util.HeaderUtil.createEntityCreationAlert(applicationName, enableTranslation, entityName, param);
    }

    public static HttpHeaders createEntityUpdateAlert(String applicationName, boolean enableTranslation, String entityName, String param) {
        return tech.jhipster.web.util.HeaderUtil.createEntityUpdateAlert(applicationName, enableTranslation, entityName, param);
    }

    public static HttpHeaders createEntityDeletionAlert(String applicationName, boolean enableTranslation, String entityName, String param) {
        return tech.jhipster.web.util.HeaderUtil.createEntityDeletionAlert(applicationName, enableTranslation, entityName, param);
    }

    public static HttpHeaders createFailureAlert(String applicationName, boolean enableTranslation, String entityName, String errorKey, String defaultMessage) {
        HttpHeaders headers = tech.jhipster.web.util.HeaderUtil.createFailureAlert(applicationName, enableTranslation, entityName, errorKey, defaultMessage);
        headers.add("X-" + applicationName + "-message", defaultMessage);
        return headers;
    }

    /**
     * Creates authorization headers for a given username and password
     * @param username the username for the authentication
     * @param password the password for the authentication
     * @return the authorization header
     */
    public static HttpHeaders createAuthorization(String username, String password) {
        HttpHeaders authorizationHeaders = new HttpHeaders() {

            {
                set(com.google.common.net.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
                set(com.google.common.net.HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
            }
        };
        String authorization = username + ":" + password;
        String basic = new String(Base64.getEncoder().encode(authorization.getBytes(StandardCharsets.UTF_8)));
        authorizationHeaders.set("Authorization", "Basic " + basic);
        return authorizationHeaders;
    }
}
