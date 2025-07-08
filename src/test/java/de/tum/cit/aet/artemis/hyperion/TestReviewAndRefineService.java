package de.tum.cit.aet.artemis.hyperion;

import de.tum.cit.aet.artemis.hyperion.proto.InconsistencyCheckRequest;
import de.tum.cit.aet.artemis.hyperion.proto.InconsistencyCheckResponse;
import de.tum.cit.aet.artemis.hyperion.proto.ReviewAndRefineGrpc;
import de.tum.cit.aet.artemis.hyperion.proto.RewriteProblemStatementRequest;
import de.tum.cit.aet.artemis.hyperion.proto.RewriteProblemStatementResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * Mock implementation of ReviewAndRefine service for testing.
 *
 * Follows testing best practices:
 * - Single responsibility: Simple, predictable behavior
 * - Independent: No magic strings or hidden dependencies
 * - Realistic: Behaves like a real service would
 * - Maintainable: Easy to understand and modify
 *
 * For error scenario testing, use direct gRPC stub calls with deadline/timeout
 * or create specific test configurations when needed.
 */
@GrpcService
public class TestReviewAndRefineService extends ReviewAndRefineGrpc.ReviewAndRefineImplBase {

    @Override
    public void checkInconsistencies(InconsistencyCheckRequest request, StreamObserver<InconsistencyCheckResponse> responseObserver) {
        // Validate input
        if (request.getProblemStatement() == null || request.getProblemStatement().trim().isEmpty()) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Problem statement is required and cannot be empty").asRuntimeException());
            return;
        }

        // Simulate realistic validation - reject excessively long problem statements
        if (request.getProblemStatement().length() > 50000) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Problem statement too long: Maximum 50,000 characters allowed").asRuntimeException());
            return;
        }

        // Success response - realistic behavior
        var response = InconsistencyCheckResponse.newBuilder().setInconsistencies("No inconsistencies found").build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void rewriteProblemStatement(RewriteProblemStatementRequest request, StreamObserver<RewriteProblemStatementResponse> responseObserver) {
        // Validate input
        if (request.getText() == null) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Text field is required and cannot be null").asRuntimeException());
            return;
        }

        // Simulate realistic validation - reject excessively long text
        if (request.getText().length() > 10000) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Text too long: Maximum 10,000 characters allowed for rewrite operations").asRuntimeException());
            return;
        }

        // Success response - realistic behavior with actual enhancement
        var originalText = request.getText();
        var rewrittenText = originalText.isEmpty() ? "Enhanced: Please provide clear learning objectives and specific requirements for students."
                : "Enhanced: " + originalText
                        + "\n\nLearning Objectives:\n- Understand the problem requirements\n- Apply appropriate algorithms and data structures\n- Write clean, maintainable code";

        var response = RewriteProblemStatementResponse.newBuilder().setRewrittenText(rewrittenText).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
