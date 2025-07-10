package de.tum.cit.aet.artemis.hyperion.config;

import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;

/**
 * Mock implementation of the gRPC Health service for integration testing.
 * Provides configurable health check responses to test various service states.
 */
public class HyperionTestHealthService extends HealthGrpc.HealthImplBase {

    private HealthCheckResponse.ServingStatus servingStatus = HealthCheckResponse.ServingStatus.SERVING;

    @Override
    public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        responseObserver.onNext(HealthCheckResponse.newBuilder().setStatus(servingStatus).build());
        responseObserver.onCompleted();
    }

    @Override
    public void watch(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
        // For integration testing, just send current status and complete
        responseObserver.onNext(HealthCheckResponse.newBuilder().setStatus(servingStatus).build());
        responseObserver.onCompleted();
    }

    /**
     * Configure the health status for testing.
     */
    public void setServingStatus(HealthCheckResponse.ServingStatus status) {
        this.servingStatus = status;
    }

    /**
     * Reset to healthy state.
     */
    public void reset() {
        this.servingStatus = HealthCheckResponse.ServingStatus.SERVING;
    }
}
