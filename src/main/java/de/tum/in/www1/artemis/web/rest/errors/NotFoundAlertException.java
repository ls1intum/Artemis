package de.tum.in.www1.artemis.web.rest.errors;

import org.springframework.http.HttpStatusCode;
import org.springframework.lang.Nullable;
import org.springframework.web.server.ResponseStatusException;

public class NotFoundAlertException extends ResponseStatusException {

    public NotFoundAlertException(HttpStatusCode status, @Nullable String reason) {
        super(status, reason);
    }

}
