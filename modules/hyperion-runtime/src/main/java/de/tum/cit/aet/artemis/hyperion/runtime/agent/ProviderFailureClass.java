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
 * The accounting rule is that an admitted request whose usage cannot be proved marks the run's accounting incomplete. That rule was written for the one failure that is
 * truly indeterminate: the request was sent and the connection died before a response arrived. A status response from the provider is not that case — the provider
 * answered, and an error status carries no completion — and neither is a request that never left the client. Treating those as indeterminate is what turned a two-minute
 * provider outage into a failed run per request.
 */
public enum ProviderFailureClass {

    /** The provider answered with a transient status (408, 429, 5xx): no completion was produced and a later request can succeed. */
    REJECTED_TRANSIENT,

    /** The provider answered with another error status, or the local cooldown refused the call: no completion was produced and the same request cannot succeed. */
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
                return isTransientStatus(serviceException.statusCode()) ? REJECTED_TRANSIENT : REJECTED;
            }
            if (cause instanceof ConnectException || cause instanceof HttpConnectTimeoutException || cause instanceof UnknownHostException
                    || cause instanceof NoRouteToHostException || cause instanceof SSLHandshakeException) {
                return NOT_SENT;
            }
        }
        return INDETERMINATE;
    }

    private static boolean isTransientStatus(int status) {
        return status == 408 || status == 429 || status >= 500;
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
