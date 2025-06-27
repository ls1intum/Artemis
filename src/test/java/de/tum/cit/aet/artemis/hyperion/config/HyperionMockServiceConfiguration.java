package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.hyperion.proto.InconsistencyCheckRequest;
import de.tum.cit.aet.artemis.hyperion.proto.InconsistencyCheckResponse;
import de.tum.cit.aet.artemis.hyperion.proto.ReviewAndRefineGrpc;
import de.tum.cit.aet.artemis.hyperion.proto.RewriteProblemStatementRequest;
import de.tum.cit.aet.artemis.hyperion.proto.RewriteProblemStatementResponse;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;

/**
 * Test configuration providing mock Hyperion gRPC services.
 */
@TestConfiguration
@Profile(PROFILE_HYPERION)
public class HyperionMockServiceConfiguration {

    /**
     * Mock Review and Refine service for testing.
     */
    @Bean
    public MockReviewAndRefineService mockReviewAndRefineService() {
        return new MockReviewAndRefineService();
    }

    /**
     * Mock Health service for testing.
     */
    @Bean
    public MockHealthService mockHealthService() {
        return new MockHealthService();
    }

    /**
     * Mock implementation of the ReviewAndRefine gRPC service.
     */
    public static class MockReviewAndRefineService extends ReviewAndRefineGrpc.ReviewAndRefineImplBase {

        @Override
        public void checkInconsistencies(InconsistencyCheckRequest request, StreamObserver<InconsistencyCheckResponse> responseObserver) {
            var response = InconsistencyCheckResponse.newBuilder().setInconsistencies("No inconsistencies found").build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void rewriteProblemStatement(RewriteProblemStatementRequest request, StreamObserver<RewriteProblemStatementResponse> responseObserver) {
            var rewrittenText = "Improved: " + request.getText() + " (Enhanced for clarity and pedagogy)";
            var response = RewriteProblemStatementResponse.newBuilder().setRewrittenText(rewrittenText).build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    /**
     * Mock implementation of the Health gRPC service.
     */
    public static class MockHealthService extends HealthGrpc.HealthImplBase {

        @Override
        public void check(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
            var response = HealthCheckResponse.newBuilder().setStatus(HealthCheckResponse.ServingStatus.SERVING).build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
