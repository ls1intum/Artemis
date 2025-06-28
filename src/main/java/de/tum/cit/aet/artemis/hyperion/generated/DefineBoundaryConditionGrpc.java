package de.tum.cit.aet.artemis.hyperion.generated;

/**
 * <pre>
 * Exercise Creation Step 1: Define Boundary Conditions
 * </pre>
 */
@javax.annotation.Generated(value = "by gRPC proto compiler (version 1.73.0)", comments = "Source: de/tum/cit/aet/artemis/hyperion/proto/hyperion.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class DefineBoundaryConditionGrpc {

    private DefineBoundaryConditionGrpc() {
    }

    public static final java.lang.String SERVICE_NAME = "de.tum.cit.aet.artemis.hyperion.DefineBoundaryCondition";

    // Static method descriptors that strictly reflect the proto.
    /**
     * Creates a new async stub that supports all call types for the service
     */
    public static DefineBoundaryConditionStub newStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<DefineBoundaryConditionStub> factory = new io.grpc.stub.AbstractStub.StubFactory<DefineBoundaryConditionStub>() {

            @java.lang.Override
            public DefineBoundaryConditionStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new DefineBoundaryConditionStub(channel, callOptions);
            }
        };
        return DefineBoundaryConditionStub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports all types of calls on the service
     */
    public static DefineBoundaryConditionBlockingV2Stub newBlockingV2Stub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<DefineBoundaryConditionBlockingV2Stub> factory = new io.grpc.stub.AbstractStub.StubFactory<DefineBoundaryConditionBlockingV2Stub>() {

            @java.lang.Override
            public DefineBoundaryConditionBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new DefineBoundaryConditionBlockingV2Stub(channel, callOptions);
            }
        };
        return DefineBoundaryConditionBlockingV2Stub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports unary and streaming output calls on the service
     */
    public static DefineBoundaryConditionBlockingStub newBlockingStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<DefineBoundaryConditionBlockingStub> factory = new io.grpc.stub.AbstractStub.StubFactory<DefineBoundaryConditionBlockingStub>() {

            @java.lang.Override
            public DefineBoundaryConditionBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new DefineBoundaryConditionBlockingStub(channel, callOptions);
            }
        };
        return DefineBoundaryConditionBlockingStub.newStub(factory, channel);
    }

    /**
     * Creates a new ListenableFuture-style stub that supports unary calls on the service
     */
    public static DefineBoundaryConditionFutureStub newFutureStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<DefineBoundaryConditionFutureStub> factory = new io.grpc.stub.AbstractStub.StubFactory<DefineBoundaryConditionFutureStub>() {

            @java.lang.Override
            public DefineBoundaryConditionFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new DefineBoundaryConditionFutureStub(channel, callOptions);
            }
        };
        return DefineBoundaryConditionFutureStub.newStub(factory, channel);
    }

    /**
     * <pre>
     * Exercise Creation Step 1: Define Boundary Conditions
     * </pre>
     */
    public interface AsyncService {
    }

    /**
     * Base class for the server implementation of the service DefineBoundaryCondition.
     *
     * <pre>
     * Exercise Creation Step 1: Define Boundary Conditions
     * </pre>
     */
    public abstract static class DefineBoundaryConditionImplBase implements io.grpc.BindableService, AsyncService {

        @java.lang.Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return DefineBoundaryConditionGrpc.bindService(this);
        }
    }

    /**
     * A stub to allow clients to do asynchronous rpc calls to service DefineBoundaryCondition.
     *
     * <pre>
     * Exercise Creation Step 1: Define Boundary Conditions
     * </pre>
     */
    public static final class DefineBoundaryConditionStub extends io.grpc.stub.AbstractAsyncStub<DefineBoundaryConditionStub> {

        private DefineBoundaryConditionStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected DefineBoundaryConditionStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new DefineBoundaryConditionStub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do synchronous rpc calls to service DefineBoundaryCondition.
     *
     * <pre>
     * Exercise Creation Step 1: Define Boundary Conditions
     * </pre>
     */
    public static final class DefineBoundaryConditionBlockingV2Stub extends io.grpc.stub.AbstractBlockingStub<DefineBoundaryConditionBlockingV2Stub> {

        private DefineBoundaryConditionBlockingV2Stub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected DefineBoundaryConditionBlockingV2Stub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new DefineBoundaryConditionBlockingV2Stub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do limited synchronous rpc calls to service DefineBoundaryCondition.
     *
     * <pre>
     * Exercise Creation Step 1: Define Boundary Conditions
     * </pre>
     */
    public static final class DefineBoundaryConditionBlockingStub extends io.grpc.stub.AbstractBlockingStub<DefineBoundaryConditionBlockingStub> {

        private DefineBoundaryConditionBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected DefineBoundaryConditionBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new DefineBoundaryConditionBlockingStub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do ListenableFuture-style rpc calls to service DefineBoundaryCondition.
     *
     * <pre>
     * Exercise Creation Step 1: Define Boundary Conditions
     * </pre>
     */
    public static final class DefineBoundaryConditionFutureStub extends io.grpc.stub.AbstractFutureStub<DefineBoundaryConditionFutureStub> {

        private DefineBoundaryConditionFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected DefineBoundaryConditionFutureStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new DefineBoundaryConditionFutureStub(channel, callOptions);
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

    private abstract static class DefineBoundaryConditionBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {

        DefineBoundaryConditionBaseDescriptorSupplier() {
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
            return de.tum.cit.aet.artemis.hyperion.generated.HyperionServiceProto.getDescriptor();
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
            return getFileDescriptor().findServiceByName("DefineBoundaryCondition");
        }
    }

    private static final class DefineBoundaryConditionFileDescriptorSupplier extends DefineBoundaryConditionBaseDescriptorSupplier {

        DefineBoundaryConditionFileDescriptorSupplier() {
        }
    }

    private static final class DefineBoundaryConditionMethodDescriptorSupplier extends DefineBoundaryConditionBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {

        private final java.lang.String methodName;

        DefineBoundaryConditionMethodDescriptorSupplier(java.lang.String methodName) {
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
            synchronized (DefineBoundaryConditionGrpc.class) {
                result = serviceDescriptor;
                if (result == null) {
                    serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME).setSchemaDescriptor(new DefineBoundaryConditionFileDescriptorSupplier())
                            .build();
                }
            }
        }
        return result;
    }
}
