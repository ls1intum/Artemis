package de.tum.in.www1.artemis.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class AccessUnauthorizedException extends RuntimeException {
    // Only default no-args constructor required.
}
