package de.tum.in.www1.artemis.web.rest.errors;

import java.io.Serial;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConflictException() {
        super();
    }
}
