package de.tum.cit.aet.artemis.hyperion.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.zalando.problem.Status;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.core.exception.HttpStatusException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableException;
import io.grpc.StatusRuntimeException;

/**
 * Base service for Hyperion gRPC operations providing common functionality.
 * This class provides shared utilities for all Hyperion service implementations.
 *
 * @see <a href="https://grpc.io/docs/guides/status-codes/">gRPC Status Codes</a>
 * @see <a href="https://tools.ietf.org/html/rfc7807">RFC 7807 Problem Details</a>
 */
@Profile(PROFILE_HYPERION)
@Service
@Lazy
public abstract class AbstractHyperionGrpcService {

    private static final Logger log = LoggerFactory.getLogger(AbstractHyperionGrpcService.class);

    /**
     * Handles common gRPC exceptions and converts them to appropriate HTTP status exceptions.
     * Maps gRPC status codes to corresponding HTTP status codes.
     *
     * <p>
     * Status Code Mapping (per gRPC ↔ HTTP standards):
     * <ul>
     * <li>INVALID_ARGUMENT, FAILED_PRECONDITION, OUT_OF_RANGE → 400 Bad Request</li>
     * <li>NOT_FOUND → 404 Not Found</li>
     * <li>ALREADY_EXISTS, ABORTED → 409 Conflict</li>
     * <li>PERMISSION_DENIED → 403 Forbidden</li>
     * <li>UNAUTHENTICATED → 401 Unauthorized</li>
     * <li>RESOURCE_EXHAUSTED → 429 Too Many Requests</li>
     * <li>UNIMPLEMENTED → 501 Not Implemented</li>
     * <li>UNAVAILABLE → 503 Service Unavailable</li>
     * <li>DEADLINE_EXCEEDED → 504 Gateway Timeout</li>
     * <li>CANCELLED → 499 Client Closed Request (or 400 as fallback)</li>
     * <li>INTERNAL, DATA_LOSS → 500 Internal Server Error</li>
     * </ul>
     *
     * @param operation description of the operation that failed (for logging and error context)
     * @param e         the gRPC exception to handle
     * @throws HttpStatusException with appropriate HTTP status code and structured error details
     * @see <a href="https://grpc.io/docs/guides/status-codes/">gRPC Status Codes Documentation</a>
     */
    protected void handleGrpcException(String operation, Exception e) {
        if (e instanceof StatusRuntimeException grpcException) {
            var status = grpcException.getStatus();
            var statusCode = status.getCode();
            var description = status.getDescription();
            var errorMessage = String.format("gRPC call failed for %s: %s", operation, description != null ? description : grpcException.getMessage());

            // Structured logging for observability and debugging
            log.error("gRPC call failed - operation: {}, status: {}, description: {}, message: {}", operation, statusCode, description, grpcException.getMessage(), grpcException);

            switch (statusCode) {
                case INVALID_ARGUMENT, FAILED_PRECONDITION, OUT_OF_RANGE -> {
                    // Client error - malformed request, invalid parameters, or preconditions not met
                    throw new BadRequestAlertException(errorMessage, "hyperion", "validation");
                }
                case NOT_FOUND -> {
                    // Resource not found
                    throw new EntityNotFoundException("hyperion", "url.not.found");
                }
                case ALREADY_EXISTS, ABORTED -> {
                    // Resource conflict or transaction aborted
                    throw new ConflictException(errorMessage, "hyperion", "concurrencyFailure");
                }
                case PERMISSION_DENIED -> {
                    // Access forbidden - user lacks required permissions
                    throw new AccessForbiddenAlertException("You are not authorized to perform this action.", "hyperion", "unknownAction");
                }
                case UNAUTHENTICATED -> {
                    // Authentication required - 401 Unauthorized
                    throw new HyperionAuthenticationException(errorMessage);
                }
                case RESOURCE_EXHAUSTED -> {
                    // Rate limiting or quota exceeded - 429 Too Many Requests
                    throw new HyperionRateLimitException(errorMessage);
                }
                case UNIMPLEMENTED -> {
                    // Feature not implemented - 501 Not Implemented
                    throw new HyperionNotImplementedException(errorMessage);
                }
                case UNAVAILABLE -> {
                    // Service temporarily unavailable - 503 Service Unavailable
                    throw new ServiceUnavailableException(errorMessage);
                }
                case DEADLINE_EXCEEDED -> {
                    // Request timeout - 504 Gateway Timeout
                    throw new HyperionTimeoutException(errorMessage);
                }
                case CANCELLED -> {
                    // Client cancelled request - 499 Client Closed Request (mapped to 400 as fallback)
                    throw new HyperionCancelledException(errorMessage);
                }
                case INTERNAL, DATA_LOSS -> {
                    // Server-side internal error or data corruption - 500 Internal Server Error
                    throw new HyperionInternalException(errorMessage, grpcException);
                }
                case UNKNOWN -> {
                    // Unknown error - likely indicates a programming error or unexpected condition
                    log.warn("Unknown gRPC status received for operation '{}': {} - this may indicate a bug", operation, grpcException.getMessage(), grpcException);
                    throw new HyperionInternalException("Unknown error during " + operation, grpcException);
                }
                default -> {
                    // Unexpected status code - should not happen with current gRPC versions
                    log.warn("Unexpected gRPC status code {} for operation '{}': {} - please review status code mapping", statusCode, operation, grpcException.getMessage(),
                            grpcException);
                    throw new HyperionInternalException("Unexpected gRPC error during " + operation, grpcException);
                }
            }
        }
        else {
            // Non-gRPC exception - unexpected but should be handled gracefully
            log.error("Unexpected non-gRPC error during operation '{}': {}", operation, e.getMessage(), e);
            throw new HyperionInternalException("Unexpected error during " + operation, e);
        }
    }

    /**
     * Base exception for Hyperion service internal errors.
     * Maps to HTTP 500 Internal Server Error for unexpected server-side issues.
     *
     * <p>
     * Used for INTERNAL, DATA_LOSS, and UNKNOWN gRPC status codes.
     */
    public static class HyperionInternalException extends HttpStatusException {

        public HyperionInternalException(String message, Throwable cause) {
            super(ErrorConstants.DEFAULT_TYPE, message, Status.INTERNAL_SERVER_ERROR, "hyperion", "internalServerError",
                    getAlertParameters("hyperion", "internalServerError", false));
        }
    }

    /**
     * Exception for authentication failures from Hyperion service.
     * Maps to HTTP 401 Unauthorized.
     *
     * <p>
     * Used for UNAUTHENTICATED gRPC status code.
     * Indicates the request lacks valid authentication credentials.
     */
    public static class HyperionAuthenticationException extends HttpStatusException {

        public HyperionAuthenticationException(String message) {
            // For authentication errors, we use the standard error message for 401 unauthorized access
            super(ErrorConstants.DEFAULT_TYPE, message, Status.UNAUTHORIZED, "hyperion", "server.not.reachable", getAlertParameters("hyperion", "server.not.reachable", false));
        }
    }

    /**
     * Exception for rate limiting from Hyperion service.
     * Maps to HTTP 429 Too Many Requests (using SERVICE_UNAVAILABLE as fallback).
     *
     * <p>
     * Used for RESOURCE_EXHAUSTED gRPC status code.
     * Indicates rate limits, quotas, or resource exhaustion.
     * Clients should implement exponential backoff retry logic.
     *
     * @see <a href="https://tools.ietf.org/html/rfc6585#section-4">RFC 6585 Section 4</a>
     */
    public static class HyperionRateLimitException extends HttpStatusException {

        public HyperionRateLimitException(String message) {
            // Note: Using SERVICE_UNAVAILABLE as Zalando Problem doesn't define TOO_MANY_REQUESTS
            // In a production environment, consider extending to support custom status codes
            super(ErrorConstants.DEFAULT_TYPE, "Rate limit exceeded: " + message, Status.SERVICE_UNAVAILABLE, "hyperion", "server.not.reachable",
                    getAlertParameters("hyperion", "server.not.reachable", false));
        }
    }

    /**
     * Exception for unimplemented features in Hyperion service.
     * Maps to HTTP 501 Not Implemented (using INTERNAL_SERVER_ERROR as fallback).
     *
     * <p>
     * Used for UNIMPLEMENTED gRPC status code.
     * Indicates the requested operation is not implemented or not supported.
     */
    public static class HyperionNotImplementedException extends HttpStatusException {

        public HyperionNotImplementedException(String message) {
            // Note: Using INTERNAL_SERVER_ERROR as Zalando Problem doesn't define NOT_IMPLEMENTED
            super(ErrorConstants.DEFAULT_TYPE, "Feature not implemented: " + message, Status.INTERNAL_SERVER_ERROR, "hyperion", "unknownAction",
                    getAlertParameters("hyperion", "unknownAction", false));
        }
    }

    /**
     * Exception for timeout errors from Hyperion service.
     * Maps to HTTP 504 Gateway Timeout (using INTERNAL_SERVER_ERROR as fallback).
     *
     * <p>
     * Used for DEADLINE_EXCEEDED gRPC status code.
     * Indicates the request exceeded the configured deadline/timeout.
     * Clients may retry with a longer timeout if appropriate.
     *
     * @see <a href="https://grpc.io/docs/guides/deadlines/">gRPC Deadlines</a>
     */
    public static class HyperionTimeoutException extends HttpStatusException {

        public HyperionTimeoutException(String message) {
            // Note: Using INTERNAL_SERVER_ERROR as Zalando Problem doesn't define GATEWAY_TIMEOUT
            super(ErrorConstants.DEFAULT_TYPE, "Request timeout: " + message, Status.INTERNAL_SERVER_ERROR, "hyperion", "server.not.reachable",
                    getAlertParameters("hyperion", "server.not.reachable", false));
        }
    }

    /**
     * Exception for cancelled operations in Hyperion service.
     * Maps to HTTP 400 Bad Request (as 499 Client Closed Request is not standard).
     *
     * <p>
     * Used for CANCELLED gRPC status code.
     * Indicates the operation was cancelled, typically by the caller.
     *
     * <p>
     * Note: HTTP 499 would be more semantically correct but is not widely standardized.
     * Some environments (Nginx, Sleuth) use 499 for client-closed connections.
     */
    public static class HyperionCancelledException extends HttpStatusException {

        public HyperionCancelledException(String message) {
            super(ErrorConstants.DEFAULT_TYPE, "Request was cancelled: " + message, Status.BAD_REQUEST, "hyperion", "validation",
                    getAlertParameters("hyperion", "validation", false));
        }
    }
}
