package de.tum.in.www1.artemis.web.rest.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Deprecation: please throw exceptions instead of using the below methods,
 * use e.g. AccessForbiddenException, EntityNotFoundException, BadRequestAlertException, ConflictException
 */
public final class ResponseUtil implements tech.jhipster.web.util.ResponseUtil {

    private static final String applicationName = "artemisApp";

    // Replace with EntityNotFoundException
    @Deprecated(forRemoval = true, since = "5.2.0")
    public static <X> ResponseEntity<X> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // Replace with AccessForbiddenException
    @Deprecated(forRemoval = true, since = "5.2.0")
    public static <X> ResponseEntity<X> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // Replace with AccessForbiddenException
    @Deprecated(forRemoval = true, since = "5.2.0")
    public static <X> ResponseEntity<X> forbidden(String entityName, String errorKey, String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).headers(HeaderUtil.createFailureAlert(applicationName, true, entityName, errorKey, message)).build();
    }

    // Replace with AccessForbiddenException
    @Deprecated(forRemoval = true, since = "5.2.0")
    public static <X> ResponseEntity<X> forbidden(String applicationName, String entityName, String errorKey, String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).headers(HeaderUtil.createFailureAlert(applicationName, true, entityName, errorKey, message)).build();
    }

    // Replace with BadRequestAlertException
    @Deprecated(forRemoval = true, since = "5.2.0")
    public static <X> ResponseEntity<X> badRequest() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    // Replace with BadRequestAlertException
    @Deprecated(forRemoval = true, since = "5.2.0")
    public static <X> ResponseEntity<X> badRequest(String entityName, String errorKey, String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(HeaderUtil.createFailureAlert(applicationName, true, entityName, errorKey, message)).build();
    }

    // Replace with ConflictException
    @Deprecated(forRemoval = true, since = "5.2.0")
    public static <X> ResponseEntity<X> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    // Replace with ConflictException
    @Deprecated(forRemoval = true, since = "5.2.0")
    public static <X> ResponseEntity<X> conflict(String entityName, String errorKey, String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).headers(HeaderUtil.createFailureAlert(applicationName, true, entityName, errorKey, message)).build();
    }

    /**
     * Sends an OK response entity that contains a file. Returns a not found response
     * if the file doesn't exist.
     * @param file the file to send as a response
     * @return the response
     */
    public static ResponseEntity<Resource> ok(File file) {
        try {
            InputStreamResource resource = new InputStreamResource(new FileInputStream(file));
            return ResponseEntity.ok().contentLength(file.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", file.getName()).body(resource);
        }
        catch (FileNotFoundException e) {
            return notFound();
        }
    }
}
