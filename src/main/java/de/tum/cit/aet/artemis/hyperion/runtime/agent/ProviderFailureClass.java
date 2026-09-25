package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;

import javax.net.ssl.SSLHandshakeException;

import com.openai.errors.OpenAIServiceException;

/**
 * What a failed provider request proves about its usage, and whether sending it again can help.
 * <p>
 * A client rejection or a request that never left the client can be accounted for without a completion. A server or gateway error cannot: model execution may have consumed
 * tokens before the error response was produced. The absence of a completion in an error body is not proof of zero usage.
 */
public enum ProviderFailureClass {

    /** The provider rejected the request with HTTP 429; a later request can succeed. */
    REJECTED_TRANSIENT,

    /** The provider rejected the request with a recognized client error, or the local cooldown refused the call. */
    REJECTED,

    /** The request never reached the provider (connection refused, unknown host, TLS handshake, connect timeout): nothing was sent, so sending it again is safe. */
    NOT_SENT,

    /** The request may have been processed (read timeout, connection reset, any unrecognised failure): its usage cannot be proved. */
    INDETERMINATE;

    private static final int MAX_CAUSE_DEPTH = 16;

    /**
     * @return whether the failure proves that the provider produced no completion for this request
     */
    public boolean provesNoUsage() {
        return this != INDETERMINATE;
    }

    /**
     * @return whether the same request can be sent again without duplicating usage
     */
    public boolean retryable() {
        return this == REJECTED_TRANSIENT || this == NOT_SENT;
    }

    /**
     * Classifies a provider failure from its cause chain.
     *
     * @param error the exception the provider call threw
     * @return the failure class; {@link #INDETERMINATE} for anything not recognised
     */
    public static ProviderFailureClass of(Throwable error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++, cause = cause.getCause()) {
            if (cause instanceof ProviderFailureCooldown.ProviderInCooldownException) {
                return REJECTED;
            }
            if (cause instanceof OpenAIServiceException serviceException) {
                int status = serviceException.statusCode();
                return switch (status) {
                    case 429 -> REJECTED_TRANSIENT;
                    case 400, 401, 403, 404, 405, 413, 415, 422 -> REJECTED;
                    default -> INDETERMINATE;
                };
            }
            if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException || cause instanceof UnknownHostException
                    || cause instanceof NoRouteToHostException || cause instanceof SSLHandshakeException) {
                return NOT_SENT;
            }
        }
        return INDETERMINATE;
    }

    /**
     * Describes a failure for a log line that says why a call failed. Messages are left out: a provider error body can echo the request.
     *
     * @param error the exception the provider call threw
     * @return the exception class chain, outermost to innermost, plus any provider status
     */
    public static String describe(Throwable error) {
        StringBuilder chain = new StringBuilder();
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++, cause = cause.getCause()) {
            if (depth > 0) {
                chain.append(" <- ");
            }
            chain.append(cause.getClass().getSimpleName());
            if (cause instanceof OpenAIServiceException serviceException) {
                chain.append(" HTTP ").append(serviceException.statusCode());
            }
        }
        return chain.toString();
    }
}
