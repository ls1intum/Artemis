package de.tum.cit.aet.artemis.hyperion.service.grpc;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionConfigurationProperties;
import io.grpc.StatusRuntimeException;

/**
 * Base service for Hyperion gRPC operations providing common functionality.
 * This class provides shared utilities for all Hyperion service implementations.
 */
@Profile(PROFILE_HYPERION)
@Service
@Lazy
public abstract class AbstractHyperionGrpcService {

    private static final Logger log = LoggerFactory.getLogger(AbstractHyperionGrpcService.class);

    protected final HyperionConfigurationProperties properties;

    protected AbstractHyperionGrpcService(HyperionConfigurationProperties properties) {
        this.properties = properties;
    }

    /**
     * Handles common gRPC exceptions and converts them to HyperionServiceException.
     *
     * @param operation description of the operation that failed
     * @param e         the gRPC exception
     * @throws HyperionServiceException with appropriate message
     */
    protected void handleGrpcException(String operation, Exception e) {
        if (e instanceof StatusRuntimeException grpcException) {
            log.error("gRPC call failed for {}: {} (Status: {})", operation, grpcException.getMessage(), grpcException.getStatus().getCode());
            throw new HyperionServiceException("Failed to execute " + operation, grpcException);
        }
        else {
            log.error("Unexpected error during {}: {}", operation, e.getMessage(), e);
            throw new HyperionServiceException("Unexpected error during " + operation, e);
        }
    }

    /**
     * Custom exception for Hyperion service errors.
     */
    public static class HyperionServiceException extends RuntimeException {

        public HyperionServiceException(String message) {
            super(message);
        }

        public HyperionServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
