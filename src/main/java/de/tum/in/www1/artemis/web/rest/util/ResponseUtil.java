package de.tum.in.www1.artemis.web.rest.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;

public final class ResponseUtil implements io.github.jhipster.web.util.ResponseUtil {

    // TODO: This is always null because spring does not allow static field injection
    @Value("${jhipster.clientApp.name}")
    private static String applicationName;

    public static <X> ResponseEntity<X> ok() {
        return ResponseEntity.ok().build();
    }

    public static <X> ResponseEntity<X> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    public static <X> ResponseEntity<X> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    public static <X> ResponseEntity<X> forbidden(String entityName, String errorKey, String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).headers(HeaderUtil.createFailureAlert(applicationName, true, entityName, errorKey, message)).build();
    }

    public static <X> ResponseEntity<X> forbidden(String applicationName, String entityName, String errorKey, String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).headers(HeaderUtil.createFailureAlert(applicationName, true, entityName, errorKey, message)).build();
    }

    public static <X> ResponseEntity<X> badRequest() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    public static <X> ResponseEntity<X> badRequest(String entityName, String errorKey, String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(HeaderUtil.createFailureAlert(applicationName, true, entityName, errorKey, message)).build();
    }

    public static <X> ResponseEntity<X> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    public static <X> ResponseEntity<X> conflict(String entityName, String errorKey, String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).headers(HeaderUtil.createFailureAlert(applicationName, true, entityName, errorKey, message)).build();
    }
}
