package de.tum.cit.aet.artemis.hyperion.config;

import java.util.function.BiFunction;
import java.util.function.Function;

import de.tum.cit.aet.artemis.hyperion.proto.InconsistencyCheckRequest;
import de.tum.cit.aet.artemis.hyperion.proto.InconsistencyCheckResponse;
import de.tum.cit.aet.artemis.hyperion.proto.ReviewAndRefineGrpc;
import de.tum.cit.aet.artemis.hyperion.proto.RewriteProblemStatementRequest;
import de.tum.cit.aet.artemis.hyperion.proto.RewriteProblemStatementResponse;
import io.grpc.stub.StreamObserver;

/**
 * Mock implementation of the ReviewAndRefine gRPC service for integration testing.
 * Provides configurable behavior through lambda functions to simulate various scenarios.
 */
public class HyperionTestReviewAndRefineService extends ReviewAndRefineGrpc.ReviewAndRefineImplBase {

    private Function<RewriteProblemStatementRequest, RewriteProblemStatementResponse> rewriteFn = this::defaultRewriteBehavior;

    private BiFunction<InconsistencyCheckRequest, StreamObserver<InconsistencyCheckResponse>, Void> checkFn = this::defaultCheckBehavior;

    @Override
    public void rewriteProblemStatement(RewriteProblemStatementRequest request, StreamObserver<RewriteProblemStatementResponse> responseObserver) {
        try {
            RewriteProblemStatementResponse response = rewriteFn.apply(request);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
        catch (RuntimeException e) {
            responseObserver.onError(e);
        }
    }

    @Override
    public void checkInconsistencies(InconsistencyCheckRequest request, StreamObserver<InconsistencyCheckResponse> responseObserver) {
        try {
            checkFn.apply(request, responseObserver);
        }
        catch (RuntimeException e) {
            responseObserver.onError(e);
        }
    }

    /**
     * Configure custom behavior for rewrite operations.
     */
    public void setRewriteBehavior(Function<RewriteProblemStatementRequest, RewriteProblemStatementResponse> rewriteFn) {
        this.rewriteFn = rewriteFn;
    }

    /**
     * Configure custom behavior for consistency check operations.
     */
    public void setCheckBehavior(BiFunction<InconsistencyCheckRequest, StreamObserver<InconsistencyCheckResponse>, Void> checkFn) {
        this.checkFn = checkFn;
    }

    /**
     * Reset to default behavior.
     */
    public void reset() {
        this.rewriteFn = this::defaultRewriteBehavior;
        this.checkFn = this::defaultCheckBehavior;
    }

    private RewriteProblemStatementResponse defaultRewriteBehavior(RewriteProblemStatementRequest request) {
        return RewriteProblemStatementResponse.newBuilder().setRewrittenText("Enhanced: " + request.getText()).build();
    }

    private Void defaultCheckBehavior(InconsistencyCheckRequest request, StreamObserver<InconsistencyCheckResponse> responseObserver) {
        responseObserver.onNext(InconsistencyCheckResponse.newBuilder().setInconsistencies("No inconsistencies found").build());
        responseObserver.onCompleted();
        return null;
    }
}
