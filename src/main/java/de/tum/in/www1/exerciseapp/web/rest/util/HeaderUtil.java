package de.tum.in.www1.exerciseapp.web.rest.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.Charset;
import java.util.Base64;

/**
 * Utility class for HTTP headers creation.
 *
 */
public class HeaderUtil {

    private static final Logger log = LoggerFactory.getLogger(HeaderUtil.class);

    public static HttpHeaders createAlert(String message, String param) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-exerciseApplicationApp-alert", message);
        headers.add("X-exerciseApplicationApp-params", param);
        return headers;
    }

    public static HttpHeaders createEntityCreationAlert(String entityName, String param) {
        return createAlert("exerciseApplicationApp." + entityName + ".created", param);
    }

    public static HttpHeaders createEntityUpdateAlert(String entityName, String param) {
        return createAlert("exerciseApplicationApp." + entityName + ".updated", param);
    }

    public static HttpHeaders createEntityDeletionAlert(String entityName, String param) {
        return createAlert("exerciseApplicationApp." + entityName + ".deleted", param);
    }

    public static HttpHeaders createFailureAlert(String entityName, String errorKey, String defaultMessage) {
        log.error("Entity creation failed, {}", defaultMessage);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-exerciseApplicationApp-error", "error." + errorKey);
        headers.add("X-exerciseApplicationApp-params", entityName);
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
}
