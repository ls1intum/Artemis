package de.tum.cit.aet.artemis.hyperion.generated;

/**
 * <pre>
 * Exercise Creation Step 2: Create Draft Problem Statement
 * </pre>
 */
@javax.annotation.Generated(value = "by gRPC proto compiler (version 1.73.0)", comments = "Source: de/tum/cit/aet/artemis/hyperion/proto/hyperion.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class DraftProblemStatementGrpc {

    private DraftProblemStatementGrpc() {
    }

    public static final java.lang.String SERVICE_NAME = "de.tum.cit.aet.artemis.hyperion.DraftProblemStatement";

    // Static method descriptors that strictly reflect the proto.
    /**
     * Creates a new async stub that supports all call types for the service
     */
    public static DraftProblemStatementStub newStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<DraftProblemStatementStub> factory = new io.grpc.stub.AbstractStub.StubFactory<DraftProblemStatementStub>() {

            @java.lang.Override
            public DraftProblemStatementStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new DraftProblemStatementStub(channel, callOptions);
            }
        };
        return DraftProblemStatementStub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports all types of calls on the service
     */
    public static DraftProblemStatementBlockingV2Stub newBlockingV2Stub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<DraftProblemStatementBlockingV2Stub> factory = new io.grpc.stub.AbstractStub.StubFactory<DraftProblemStatementBlockingV2Stub>() {

            @java.lang.Override
            public DraftProblemStatementBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new DraftProblemStatementBlockingV2Stub(channel, callOptions);
            }
        };
        return DraftProblemStatementBlockingV2Stub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports unary and streaming output calls on the service
     */
    public static DraftProblemStatementBlockingStub newBlockingStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<DraftProblemStatementBlockingStub> factory = new io.grpc.stub.AbstractStub.StubFactory<DraftProblemStatementBlockingStub>() {

            @java.lang.Override
            public DraftProblemStatementBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new DraftProblemStatementBlockingStub(channel, callOptions);
            }
        };
        return DraftProblemStatementBlockingStub.newStub(factory, channel);
    }

    /**
     * Creates a new ListenableFuture-style stub that supports unary calls on the service
     */
    public static DraftProblemStatementFutureStub newFutureStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<DraftProblemStatementFutureStub> factory = new io.grpc.stub.AbstractStub.StubFactory<DraftProblemStatementFutureStub>() {

            @java.lang.Override
            public DraftProblemStatementFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new DraftProblemStatementFutureStub(channel, callOptions);
            }
        };
        return DraftProblemStatementFutureStub.newStub(factory, channel);
    }

    /**
     * <pre>
     * Exercise Creation Step 2: Create Draft Problem Statement
     * </pre>
     */
    public interface AsyncService {
    }

    /**
     * Base class for the server implementation of the service DraftProblemStatement.
     *
     * <pre>
     * Exercise Creation Step 2: Create Draft Problem Statement
     * </pre>
     */
    public abstract static class DraftProblemStatementImplBase implements io.grpc.BindableService, AsyncService {

        @java.lang.Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return DraftProblemStatementGrpc.bindService(this);
        }
    }

    /**
     * A stub to allow clients to do asynchronous rpc calls to service DraftProblemStatement.
     *
     * <pre>
     * Exercise Creation Step 2: Create Draft Problem Statement
     * </pre>
     */
    public static final class DraftProblemStatementStub extends io.grpc.stub.AbstractAsyncStub<DraftProblemStatementStub> {

        private DraftProblemStatementStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected DraftProblemStatementStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new DraftProblemStatementStub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do synchronous rpc calls to service DraftProblemStatement.
     *
     * <pre>
     * Exercise Creation Step 2: Create Draft Problem Statement
     * </pre>
     */
    public static final class DraftProblemStatementBlockingV2Stub extends io.grpc.stub.AbstractBlockingStub<DraftProblemStatementBlockingV2Stub> {

        private DraftProblemStatementBlockingV2Stub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected DraftProblemStatementBlockingV2Stub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new DraftProblemStatementBlockingV2Stub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do limited synchronous rpc calls to service DraftProblemStatement.
     *
     * <pre>
     * Exercise Creation Step 2: Create Draft Problem Statement
     * </pre>
     */
    public static final class DraftProblemStatementBlockingStub extends io.grpc.stub.AbstractBlockingStub<DraftProblemStatementBlockingStub> {

        private DraftProblemStatementBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected DraftProblemStatementBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new DraftProblemStatementBlockingStub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do ListenableFuture-style rpc calls to service DraftProblemStatement.
     *
     * <pre>
     * Exercise Creation Step 2: Create Draft Problem Statement
     * </pre>
     */
    public static final class DraftProblemStatementFutureStub extends io.grpc.stub.AbstractFutureStub<DraftProblemStatementFutureStub> {

        private DraftProblemStatementFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected DraftProblemStatementFutureStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new DraftProblemStatementFutureStub(channel, callOptions);
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

    private abstract static class DraftProblemStatementBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {

        DraftProblemStatementBaseDescriptorSupplier() {
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
            return de.tum.cit.aet.artemis.hyperion.generated.HyperionServiceProto.getDescriptor();
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
            return getFileDescriptor().findServiceByName("DraftProblemStatement");
        }
    }

    private static final class DraftProblemStatementFileDescriptorSupplier extends DraftProblemStatementBaseDescriptorSupplier {

        DraftProblemStatementFileDescriptorSupplier() {
        }
    }

    private static final class DraftProblemStatementMethodDescriptorSupplier extends DraftProblemStatementBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {

        private final java.lang.String methodName;

        DraftProblemStatementMethodDescriptorSupplier(java.lang.String methodName) {
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
            synchronized (DraftProblemStatementGrpc.class) {
                result = serviceDescriptor;
                if (result == null) {
                    serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME).setSchemaDescriptor(new DraftProblemStatementFileDescriptorSupplier()).build();
                }
            }
        }
        return result;
    }
}
