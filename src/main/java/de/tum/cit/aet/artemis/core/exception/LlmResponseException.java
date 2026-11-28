package de.tum.cit.aet.artemis.core.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.web.client.RestClientException;

/** The LLM provider returned a successful HTTP status with an empty or malformed response. */
public class LlmResponseException extends RestClientException {

    public LlmResponseException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
