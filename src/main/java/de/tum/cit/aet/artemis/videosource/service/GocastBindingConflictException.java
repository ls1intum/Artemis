package de.tum.cit.aet.artemis.videosource.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class GocastBindingConflictException extends RuntimeException {

    public GocastBindingConflictException(String message) {
        super(message);
    }

    public GocastBindingConflictException(String message, Throwable cause) {
        super(message);
    }
}
