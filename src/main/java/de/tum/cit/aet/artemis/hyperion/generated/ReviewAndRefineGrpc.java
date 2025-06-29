package de.tum.cit.aet.artemis.hyperion.generated;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * Exercise Creation Step 8: Review and Refine
 * </pre>
 */
@javax.annotation.Generated(value = "by gRPC proto compiler (version 1.73.0)", comments = "Source: de/tum/cit/aet/artemis/hyperion/proto/hyperion.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ReviewAndRefineGrpc {

    private ReviewAndRefineGrpc() {
    }

    public static final java.lang.String SERVICE_NAME = "de.tum.cit.aet.artemis.hyperion.ReviewAndRefine";

    // Static method descriptors that strictly reflect the proto.
    private static volatile io.grpc.MethodDescriptor<de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckRequest, de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckResponse> getCheckInconsistenciesMethod;

    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "CheckInconsistencies", requestType = de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckRequest.class, responseType = de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckResponse.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckRequest, de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckResponse> getCheckInconsistenciesMethod() {
        io.grpc.MethodDescriptor<de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckRequest, de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckResponse> getCheckInconsistenciesMethod;
        if ((getCheckInconsistenciesMethod = ReviewAndRefineGrpc.getCheckInconsistenciesMethod) == null) {
            synchronized (ReviewAndRefineGrpc.class) {
                if ((getCheckInconsistenciesMethod = ReviewAndRefineGrpc.getCheckInconsistenciesMethod) == null) {
                    ReviewAndRefineGrpc.getCheckInconsistenciesMethod = getCheckInconsistenciesMethod = io.grpc.MethodDescriptor.<de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckRequest, de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckResponse>newBuilder()
                            .setType(io.grpc.MethodDescriptor.MethodType.UNARY).setFullMethodName(generateFullMethodName(SERVICE_NAME, "CheckInconsistencies"))
                            .setSampledToLocalTracing(true)
                            .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckRequest.getDefaultInstance()))
                            .setResponseMarshaller(
                                    io.grpc.protobuf.ProtoUtils.marshaller(de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckResponse.getDefaultInstance()))
                            .setSchemaDescriptor(new ReviewAndRefineMethodDescriptorSupplier("CheckInconsistencies")).build();
                }
            }
        }
        return getCheckInconsistenciesMethod;
    }

    private static volatile io.grpc.MethodDescriptor<de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementRequest, de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementResponse> getRewriteProblemStatementMethod;

    @io.grpc.stub.annotations.RpcMethod(fullMethodName = SERVICE_NAME + '/'
            + "RewriteProblemStatement", requestType = de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementRequest.class, responseType = de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementResponse.class, methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
    public static io.grpc.MethodDescriptor<de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementRequest, de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementResponse> getRewriteProblemStatementMethod() {
        io.grpc.MethodDescriptor<de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementRequest, de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementResponse> getRewriteProblemStatementMethod;
        if ((getRewriteProblemStatementMethod = ReviewAndRefineGrpc.getRewriteProblemStatementMethod) == null) {
            synchronized (ReviewAndRefineGrpc.class) {
                if ((getRewriteProblemStatementMethod = ReviewAndRefineGrpc.getRewriteProblemStatementMethod) == null) {
                    ReviewAndRefineGrpc.getRewriteProblemStatementMethod = getRewriteProblemStatementMethod = io.grpc.MethodDescriptor.<de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementRequest, de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementResponse>newBuilder()
                            .setType(io.grpc.MethodDescriptor.MethodType.UNARY).setFullMethodName(generateFullMethodName(SERVICE_NAME, "RewriteProblemStatement"))
                            .setSampledToLocalTracing(true)
                            .setRequestMarshaller(
                                    io.grpc.protobuf.ProtoUtils.marshaller(de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementRequest.getDefaultInstance()))
                            .setResponseMarshaller(
                                    io.grpc.protobuf.ProtoUtils.marshaller(de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementResponse.getDefaultInstance()))
                            .setSchemaDescriptor(new ReviewAndRefineMethodDescriptorSupplier("RewriteProblemStatement")).build();
                }
            }
        }
        return getRewriteProblemStatementMethod;
    }

    /**
     * Creates a new async stub that supports all call types for the service
     */
    public static ReviewAndRefineStub newStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<ReviewAndRefineStub> factory = new io.grpc.stub.AbstractStub.StubFactory<ReviewAndRefineStub>() {

            @java.lang.Override
            public ReviewAndRefineStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new ReviewAndRefineStub(channel, callOptions);
            }
        };
        return ReviewAndRefineStub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports all types of calls on the service
     */
    public static ReviewAndRefineBlockingV2Stub newBlockingV2Stub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<ReviewAndRefineBlockingV2Stub> factory = new io.grpc.stub.AbstractStub.StubFactory<ReviewAndRefineBlockingV2Stub>() {

            @java.lang.Override
            public ReviewAndRefineBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new ReviewAndRefineBlockingV2Stub(channel, callOptions);
            }
        };
        return ReviewAndRefineBlockingV2Stub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports unary and streaming output calls on the service
     */
    public static ReviewAndRefineBlockingStub newBlockingStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<ReviewAndRefineBlockingStub> factory = new io.grpc.stub.AbstractStub.StubFactory<ReviewAndRefineBlockingStub>() {

            @java.lang.Override
            public ReviewAndRefineBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new ReviewAndRefineBlockingStub(channel, callOptions);
            }
        };
        return ReviewAndRefineBlockingStub.newStub(factory, channel);
    }

    /**
     * Creates a new ListenableFuture-style stub that supports unary calls on the service
     */
    public static ReviewAndRefineFutureStub newFutureStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<ReviewAndRefineFutureStub> factory = new io.grpc.stub.AbstractStub.StubFactory<ReviewAndRefineFutureStub>() {

            @java.lang.Override
            public ReviewAndRefineFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new ReviewAndRefineFutureStub(channel, callOptions);
            }
        };
        return ReviewAndRefineFutureStub.newStub(factory, channel);
    }

    /**
     * <pre>
     * Exercise Creation Step 8: Review and Refine
     * </pre>
     */
    public interface AsyncService {

        default void checkInconsistencies(de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckRequest request,
                io.grpc.stub.StreamObserver<de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckResponse> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCheckInconsistenciesMethod(), responseObserver);
        }

        default void rewriteProblemStatement(de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementRequest request,
                io.grpc.stub.StreamObserver<de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementResponse> responseObserver) {
            io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRewriteProblemStatementMethod(), responseObserver);
        }
    }

    /**
     * Base class for the server implementation of the service ReviewAndRefine.
     *
     * <pre>
     * Exercise Creation Step 8: Review and Refine
     * </pre>
     */
    public abstract static class ReviewAndRefineImplBase implements io.grpc.BindableService, AsyncService {

        @java.lang.Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return ReviewAndRefineGrpc.bindService(this);
        }
    }

    /**
     * A stub to allow clients to do asynchronous rpc calls to service ReviewAndRefine.
     *
     * <pre>
     * Exercise Creation Step 8: Review and Refine
     * </pre>
     */
    public static final class ReviewAndRefineStub extends io.grpc.stub.AbstractAsyncStub<ReviewAndRefineStub> {

        private ReviewAndRefineStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected ReviewAndRefineStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new ReviewAndRefineStub(channel, callOptions);
        }

        public void checkInconsistencies(de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckRequest request,
                io.grpc.stub.StreamObserver<de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckResponse> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(getChannel().newCall(getCheckInconsistenciesMethod(), getCallOptions()), request, responseObserver);
        }

        public void rewriteProblemStatement(de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementRequest request,
                io.grpc.stub.StreamObserver<de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementResponse> responseObserver) {
            io.grpc.stub.ClientCalls.asyncUnaryCall(getChannel().newCall(getRewriteProblemStatementMethod(), getCallOptions()), request, responseObserver);
        }
    }

    /**
     * A stub to allow clients to do synchronous rpc calls to service ReviewAndRefine.
     *
     * <pre>
     * Exercise Creation Step 8: Review and Refine
     * </pre>
     */
    public static final class ReviewAndRefineBlockingV2Stub extends io.grpc.stub.AbstractBlockingStub<ReviewAndRefineBlockingV2Stub> {

        private ReviewAndRefineBlockingV2Stub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected ReviewAndRefineBlockingV2Stub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new ReviewAndRefineBlockingV2Stub(channel, callOptions);
        }

        public de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckResponse checkInconsistencies(
                de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(getChannel(), getCheckInconsistenciesMethod(), getCallOptions(), request);
        }

        public de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementResponse rewriteProblemStatement(
                de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(getChannel(), getRewriteProblemStatementMethod(), getCallOptions(), request);
        }
    }

    /**
     * A stub to allow clients to do limited synchronous rpc calls to service ReviewAndRefine.
     *
     * <pre>
     * Exercise Creation Step 8: Review and Refine
     * </pre>
     */
    public static final class ReviewAndRefineBlockingStub extends io.grpc.stub.AbstractBlockingStub<ReviewAndRefineBlockingStub> {

        private ReviewAndRefineBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected ReviewAndRefineBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new ReviewAndRefineBlockingStub(channel, callOptions);
        }

        public de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckResponse checkInconsistencies(
                de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(getChannel(), getCheckInconsistenciesMethod(), getCallOptions(), request);
        }

        public de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementResponse rewriteProblemStatement(
                de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementRequest request) {
            return io.grpc.stub.ClientCalls.blockingUnaryCall(getChannel(), getRewriteProblemStatementMethod(), getCallOptions(), request);
        }
    }

    /**
     * A stub to allow clients to do ListenableFuture-style rpc calls to service ReviewAndRefine.
     *
     * <pre>
     * Exercise Creation Step 8: Review and Refine
     * </pre>
     */
    public static final class ReviewAndRefineFutureStub extends io.grpc.stub.AbstractFutureStub<ReviewAndRefineFutureStub> {

        private ReviewAndRefineFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected ReviewAndRefineFutureStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new ReviewAndRefineFutureStub(channel, callOptions);
        }

        public com.google.common.util.concurrent.ListenableFuture<de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckResponse> checkInconsistencies(
                de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(getChannel().newCall(getCheckInconsistenciesMethod(), getCallOptions()), request);
        }

        public com.google.common.util.concurrent.ListenableFuture<de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementResponse> rewriteProblemStatement(
                de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementRequest request) {
            return io.grpc.stub.ClientCalls.futureUnaryCall(getChannel().newCall(getRewriteProblemStatementMethod(), getCallOptions()), request);
        }
    }

    private static final int METHODID_CHECK_INCONSISTENCIES = 0;

    private static final int METHODID_REWRITE_PROBLEM_STATEMENT = 1;

    private static final class MethodHandlers<Req, Resp> implements io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>, io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
            io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>, io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {

        private final AsyncService serviceImpl;

        private final int methodId;

        MethodHandlers(AsyncService serviceImpl, int methodId) {
            this.serviceImpl = serviceImpl;
            this.methodId = methodId;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("unchecked")
        public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
            switch (methodId) {
                case METHODID_CHECK_INCONSISTENCIES:
                    serviceImpl.checkInconsistencies((de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckRequest) request,
                            (io.grpc.stub.StreamObserver<de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckResponse>) responseObserver);
                    break;
                case METHODID_REWRITE_PROBLEM_STATEMENT:
                    serviceImpl.rewriteProblemStatement((de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementRequest) request,
                            (io.grpc.stub.StreamObserver<de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementResponse>) responseObserver);
                    break;
                default:
                    throw new AssertionError();
            }
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("unchecked")
        public io.grpc.stub.StreamObserver<Req> invoke(io.grpc.stub.StreamObserver<Resp> responseObserver) {
            switch (methodId) {
                default:
                    throw new AssertionError();
            }
        }
    }

    public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
        return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor()).addMethod(getCheckInconsistenciesMethod(), io.grpc.stub.ServerCalls.asyncUnaryCall(
                new MethodHandlers<de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckRequest, de.tum.cit.aet.artemis.hyperion.generated.InconsistencyCheckResponse>(
                        service, METHODID_CHECK_INCONSISTENCIES)))
                .addMethod(getRewriteProblemStatementMethod(), io.grpc.stub.ServerCalls.asyncUnaryCall(
                        new MethodHandlers<de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementRequest, de.tum.cit.aet.artemis.hyperion.generated.RewriteProblemStatementResponse>(
                                service, METHODID_REWRITE_PROBLEM_STATEMENT)))
                .build();
    }

    private abstract static class ReviewAndRefineBaseDescriptorSupplier implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {

        ReviewAndRefineBaseDescriptorSupplier() {
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
            return de.tum.cit.aet.artemis.hyperion.generated.HyperionServiceProto.getDescriptor();
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
            return getFileDescriptor().findServiceByName("ReviewAndRefine");
        }
    }

    private static final class ReviewAndRefineFileDescriptorSupplier extends ReviewAndRefineBaseDescriptorSupplier {

        ReviewAndRefineFileDescriptorSupplier() {
        }
    }

    private static final class ReviewAndRefineMethodDescriptorSupplier extends ReviewAndRefineBaseDescriptorSupplier implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {

        private final java.lang.String methodName;

        ReviewAndRefineMethodDescriptorSupplier(java.lang.String methodName) {
            this.methodName = methodName;
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
            return getServiceDescriptor().findMethodByName(methodName);
        }
    }

    private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

    public static io.grpc.ServiceDescriptor getServiceDescriptor() {
        io.grpc.ServiceDescriptor result = serviceDescriptor;
        if (result == null) {
            synchronized (ReviewAndRefineGrpc.class) {
                result = serviceDescriptor;
                if (result == null) {
                    serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME).setSchemaDescriptor(new ReviewAndRefineFileDescriptorSupplier())
                            .addMethod(getCheckInconsistenciesMethod()).addMethod(getRewriteProblemStatementMethod()).build();
                }
            }
        }
        return result;
    }
}
