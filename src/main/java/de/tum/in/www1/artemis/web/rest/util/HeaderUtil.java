package de.tum.in.www1.artemis.web.rest.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.Charset;
import java.util.Base64;

/**
 * Utility class for HTTP headers creation.
 */
public final class HeaderUtil {

    private static final Logger log = LoggerFactory.getLogger(HeaderUtil.class);

    private static final String APPLICATION_NAME = "arTeMiSApp";

    private HeaderUtil() {
    }

    public static HttpHeaders createAlert(String message, String param) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-arTeMiSApp-alert", message);
        headers.add("X-arTeMiSApp-params", param);
        return headers;
    }

    public static HttpHeaders createEntityCreationAlert(String entityName, String param) {
        return createAlert(APPLICATION_NAME + "." + entityName + ".created", param);
    }

    public static HttpHeaders createEntityUpdateAlert(String entityName, String param) {
        return createAlert(APPLICATION_NAME + "." + entityName + ".updated", param);
    }

    public static HttpHeaders createEntityDeletionAlert(String entityName, String param) {
        return createAlert(APPLICATION_NAME + "." + entityName + ".deleted", param);
    }

    public static HttpHeaders createFailureAlert(String entityName, String errorKey, String defaultMessage) {
        log.error("Entity processing failed, {}", defaultMessage);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-arTeMiSApp-error", "error." + errorKey);
        headers.add("X-arTeMiSApp-params", entityName);
        headers.add("X-arTeMiSApp-message", defaultMessage);
        return headers;
    }

    public static HttpHeaders createAuthorization(String username, String password) {
        HttpHeaders acceptHeaders = new HttpHeaders() {
            {
                set(com.google.common.net.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
                set(com.google.common.net.HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
            }
        };
        String authorization = username + ":" + password;
        String basic = new String(Base64.getEncoder().encode(authorization.getBytes(Charset.forName("UTF-8"))));
        acceptHeaders.set("Authorization", "Basic " + basic);

        return acceptHeaders;
    }

    public static HttpHeaders createPrivateTokenAuthorization(String privateToken) {
        HttpHeaders acceptHeaders = new HttpHeaders() {
            {
                set(com.google.common.net.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
                set(com.google.common.net.HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.toString());
            }
        };
        acceptHeaders.set("Private-Token", privateToken);

        return acceptHeaders;
    }
}
