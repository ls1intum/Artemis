package de.tum.in.www1.artemis.web.rest.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ResponseUtil implements io.github.jhipster.web.util.ResponseUtil {

    @Value("${jhipster.clientApp.name}")
    private static String applicationName;

    public static <X> ResponseEntity<X> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    public static <X> ResponseEntity<X> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    public static <X> ResponseEntity<X> forbidden(String entityName, String errorKey, String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).headers(HeaderUtil.createFailureAlert(applicationName, true, entityName, errorKey, message)).build();
    }

    public static <X> ResponseEntity<X> badRequest() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
