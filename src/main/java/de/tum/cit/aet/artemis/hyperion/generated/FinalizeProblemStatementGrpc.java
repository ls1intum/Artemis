package de.tum.cit.aet.artemis.hyperion.generated;

/**
 * <pre>
 * Exercise Creation Step 6: Finalize Problem Statement
 * </pre>
 */
@javax.annotation.Generated(value = "by gRPC proto compiler (version 1.73.0)", comments = "Source: de/tum/cit/aet/artemis/hyperion/proto/hyperion.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class FinalizeProblemStatementGrpc {

    private FinalizeProblemStatementGrpc() {
    }

    public static final java.lang.String SERVICE_NAME = "de.tum.cit.aet.artemis.hyperion.FinalizeProblemStatement";

    // Static method descriptors that strictly reflect the proto.
    /**
     * Creates a new async stub that supports all call types for the service
     */
    public static FinalizeProblemStatementStub newStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<FinalizeProblemStatementStub> factory = new io.grpc.stub.AbstractStub.StubFactory<FinalizeProblemStatementStub>() {

            @java.lang.Override
            public FinalizeProblemStatementStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new FinalizeProblemStatementStub(channel, callOptions);
            }
        };
        return FinalizeProblemStatementStub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports all types of calls on the service
     */
    public static FinalizeProblemStatementBlockingV2Stub newBlockingV2Stub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<FinalizeProblemStatementBlockingV2Stub> factory = new io.grpc.stub.AbstractStub.StubFactory<FinalizeProblemStatementBlockingV2Stub>() {

            @java.lang.Override
            public FinalizeProblemStatementBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new FinalizeProblemStatementBlockingV2Stub(channel, callOptions);
            }
        };
        return FinalizeProblemStatementBlockingV2Stub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports unary and streaming output calls on the service
     */
    public static FinalizeProblemStatementBlockingStub newBlockingStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<FinalizeProblemStatementBlockingStub> factory = new io.grpc.stub.AbstractStub.StubFactory<FinalizeProblemStatementBlockingStub>() {

            @java.lang.Override
            public FinalizeProblemStatementBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new FinalizeProblemStatementBlockingStub(channel, callOptions);
            }
        };
        return FinalizeProblemStatementBlockingStub.newStub(factory, channel);
    }

    /**
     * Creates a new ListenableFuture-style stub that supports unary calls on the service
     */
    public static FinalizeProblemStatementFutureStub newFutureStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<FinalizeProblemStatementFutureStub> factory = new io.grpc.stub.AbstractStub.StubFactory<FinalizeProblemStatementFutureStub>() {

            @java.lang.Override
            public FinalizeProblemStatementFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new FinalizeProblemStatementFutureStub(channel, callOptions);
            }
        };
        return FinalizeProblemStatementFutureStub.newStub(factory, channel);
    }

    /**
     * <pre>
     * Exercise Creation Step 6: Finalize Problem Statement
     * </pre>
     */
    public interface AsyncService {
    }

    /**
     * Base class for the server implementation of the service FinalizeProblemStatement.
     *
     * <pre>
     * Exercise Creation Step 6: Finalize Problem Statement
     * </pre>
     */
    public abstract static class FinalizeProblemStatementImplBase implements io.grpc.BindableService, AsyncService {

        @java.lang.Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return FinalizeProblemStatementGrpc.bindService(this);
        }
    }

    /**
     * A stub to allow clients to do asynchronous rpc calls to service FinalizeProblemStatement.
     *
     * <pre>
     * Exercise Creation Step 6: Finalize Problem Statement
     * </pre>
     */
    public static final class FinalizeProblemStatementStub extends io.grpc.stub.AbstractAsyncStub<FinalizeProblemStatementStub> {

        private FinalizeProblemStatementStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected FinalizeProblemStatementStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new FinalizeProblemStatementStub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do synchronous rpc calls to service FinalizeProblemStatement.
     *
     * <pre>
     * Exercise Creation Step 6: Finalize Problem Statement
     * </pre>
     */
    public static final class FinalizeProblemStatementBlockingV2Stub extends io.grpc.stub.AbstractBlockingStub<FinalizeProblemStatementBlockingV2Stub> {

        private FinalizeProblemStatementBlockingV2Stub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected FinalizeProblemStatementBlockingV2Stub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new FinalizeProblemStatementBlockingV2Stub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do limited synchronous rpc calls to service FinalizeProblemStatement.
     *
     * <pre>
     * Exercise Creation Step 6: Finalize Problem Statement
     * </pre>
     */
    public static final class FinalizeProblemStatementBlockingStub extends io.grpc.stub.AbstractBlockingStub<FinalizeProblemStatementBlockingStub> {

        private FinalizeProblemStatementBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected FinalizeProblemStatementBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new FinalizeProblemStatementBlockingStub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do ListenableFuture-style rpc calls to service FinalizeProblemStatement.
     *
     * <pre>
     * Exercise Creation Step 6: Finalize Problem Statement
     * </pre>
     */
    public static final class FinalizeProblemStatementFutureStub extends io.grpc.stub.AbstractFutureStub<FinalizeProblemStatementFutureStub> {

        private FinalizeProblemStatementFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected FinalizeProblemStatementFutureStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new FinalizeProblemStatementFutureStub(channel, callOptions);
        }
    }

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
        return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor()).build();
    }

    private abstract static class FinalizeProblemStatementBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {

        FinalizeProblemStatementBaseDescriptorSupplier() {
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
            return de.tum.cit.aet.artemis.hyperion.generated.HyperionServiceProto.getDescriptor();
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
            return getFileDescriptor().findServiceByName("FinalizeProblemStatement");
        }
    }

    private static final class FinalizeProblemStatementFileDescriptorSupplier extends FinalizeProblemStatementBaseDescriptorSupplier {

        FinalizeProblemStatementFileDescriptorSupplier() {
        }
    }

    private static final class FinalizeProblemStatementMethodDescriptorSupplier extends FinalizeProblemStatementBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {

        private final java.lang.String methodName;

        FinalizeProblemStatementMethodDescriptorSupplier(java.lang.String methodName) {
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
            synchronized (FinalizeProblemStatementGrpc.class) {
                result = serviceDescriptor;
                if (result == null) {
                    serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME).setSchemaDescriptor(new FinalizeProblemStatementFileDescriptorSupplier())
                            .build();
                }
            }
        }
        return result;
    }
}
