package de.tum.cit.aet.artemis.videosource.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class GocastIntegrationException extends RuntimeException {

    private final HttpStatusCode upstreamStatus;

    public GocastIntegrationException(String message, HttpStatusCode upstreamStatus) {
        super(message);
        this.upstreamStatus = upstreamStatus;
    }

    public GocastIntegrationException(String message, HttpStatusCode upstreamStatus, Throwable cause) {
        super(message);
        this.upstreamStatus = upstreamStatus;
    }

    public HttpStatusCode getUpstreamStatus() {
        return upstreamStatus;
    }
}
