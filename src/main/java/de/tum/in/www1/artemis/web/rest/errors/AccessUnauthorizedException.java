package de.tum.in.www1.artemis.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Generic unchecked exception for access unauthorized (i.e. 401) errors when a user tries to access their repository.
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class AccessUnauthorizedException extends RuntimeException {
    // Only default no-args constructor required.
}
