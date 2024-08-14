package de.tum.in.www1.artemis.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.web.server.ResponseStatusException;

public class NotFoundAlertException extends ResponseStatusException {

    public NotFoundAlertException(@Nullable String reason) {
        super(HttpStatus.NOT_FOUND, reason);
    }

}
