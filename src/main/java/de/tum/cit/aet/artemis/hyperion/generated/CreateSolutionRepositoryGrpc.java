package de.tum.cit.aet.artemis.hyperion.generated;

/**
 * <pre>
 * Exercise Creation Step 3: Create Solution Repository
 * </pre>
 */
@javax.annotation.Generated(value = "by gRPC proto compiler (version 1.73.0)", comments = "Source: de/tum/cit/aet/artemis/hyperion/proto/hyperion.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class CreateSolutionRepositoryGrpc {

    private CreateSolutionRepositoryGrpc() {
    }

    public static final java.lang.String SERVICE_NAME = "de.tum.cit.aet.artemis.hyperion.CreateSolutionRepository";

    // Static method descriptors that strictly reflect the proto.
    /**
     * Creates a new async stub that supports all call types for the service
     */
    public static CreateSolutionRepositoryStub newStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CreateSolutionRepositoryStub> factory = new io.grpc.stub.AbstractStub.StubFactory<CreateSolutionRepositoryStub>() {

            @java.lang.Override
            public CreateSolutionRepositoryStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new CreateSolutionRepositoryStub(channel, callOptions);
            }
        };
        return CreateSolutionRepositoryStub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports all types of calls on the service
     */
    public static CreateSolutionRepositoryBlockingV2Stub newBlockingV2Stub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CreateSolutionRepositoryBlockingV2Stub> factory = new io.grpc.stub.AbstractStub.StubFactory<CreateSolutionRepositoryBlockingV2Stub>() {

            @java.lang.Override
            public CreateSolutionRepositoryBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new CreateSolutionRepositoryBlockingV2Stub(channel, callOptions);
            }
        };
        return CreateSolutionRepositoryBlockingV2Stub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports unary and streaming output calls on the service
     */
    public static CreateSolutionRepositoryBlockingStub newBlockingStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CreateSolutionRepositoryBlockingStub> factory = new io.grpc.stub.AbstractStub.StubFactory<CreateSolutionRepositoryBlockingStub>() {

            @java.lang.Override
            public CreateSolutionRepositoryBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new CreateSolutionRepositoryBlockingStub(channel, callOptions);
            }
        };
        return CreateSolutionRepositoryBlockingStub.newStub(factory, channel);
    }

    /**
     * Creates a new ListenableFuture-style stub that supports unary calls on the service
     */
    public static CreateSolutionRepositoryFutureStub newFutureStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CreateSolutionRepositoryFutureStub> factory = new io.grpc.stub.AbstractStub.StubFactory<CreateSolutionRepositoryFutureStub>() {

            @java.lang.Override
            public CreateSolutionRepositoryFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new CreateSolutionRepositoryFutureStub(channel, callOptions);
            }
        };
        return CreateSolutionRepositoryFutureStub.newStub(factory, channel);
    }

    /**
     * <pre>
     * Exercise Creation Step 3: Create Solution Repository
     * </pre>
     */
    public interface AsyncService {
    }

    /**
     * Base class for the server implementation of the service CreateSolutionRepository.
     *
     * <pre>
     * Exercise Creation Step 3: Create Solution Repository
     * </pre>
     */
    public abstract static class CreateSolutionRepositoryImplBase implements io.grpc.BindableService, AsyncService {

        @java.lang.Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return CreateSolutionRepositoryGrpc.bindService(this);
        }
    }

    /**
     * A stub to allow clients to do asynchronous rpc calls to service CreateSolutionRepository.
     *
     * <pre>
     * Exercise Creation Step 3: Create Solution Repository
     * </pre>
     */
    public static final class CreateSolutionRepositoryStub extends io.grpc.stub.AbstractAsyncStub<CreateSolutionRepositoryStub> {

        private CreateSolutionRepositoryStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CreateSolutionRepositoryStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CreateSolutionRepositoryStub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do synchronous rpc calls to service CreateSolutionRepository.
     *
     * <pre>
     * Exercise Creation Step 3: Create Solution Repository
     * </pre>
     */
    public static final class CreateSolutionRepositoryBlockingV2Stub extends io.grpc.stub.AbstractBlockingStub<CreateSolutionRepositoryBlockingV2Stub> {

        private CreateSolutionRepositoryBlockingV2Stub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CreateSolutionRepositoryBlockingV2Stub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CreateSolutionRepositoryBlockingV2Stub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do limited synchronous rpc calls to service CreateSolutionRepository.
     *
     * <pre>
     * Exercise Creation Step 3: Create Solution Repository
     * </pre>
     */
    public static final class CreateSolutionRepositoryBlockingStub extends io.grpc.stub.AbstractBlockingStub<CreateSolutionRepositoryBlockingStub> {

        private CreateSolutionRepositoryBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CreateSolutionRepositoryBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CreateSolutionRepositoryBlockingStub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do ListenableFuture-style rpc calls to service CreateSolutionRepository.
     *
     * <pre>
     * Exercise Creation Step 3: Create Solution Repository
     * </pre>
     */
    public static final class CreateSolutionRepositoryFutureStub extends io.grpc.stub.AbstractFutureStub<CreateSolutionRepositoryFutureStub> {

        private CreateSolutionRepositoryFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CreateSolutionRepositoryFutureStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CreateSolutionRepositoryFutureStub(channel, callOptions);
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

    private abstract static class CreateSolutionRepositoryBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {

        CreateSolutionRepositoryBaseDescriptorSupplier() {
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
            return de.tum.cit.aet.artemis.hyperion.generated.HyperionServiceProto.getDescriptor();
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
            return getFileDescriptor().findServiceByName("CreateSolutionRepository");
        }
    }

    private static final class CreateSolutionRepositoryFileDescriptorSupplier extends CreateSolutionRepositoryBaseDescriptorSupplier {

        CreateSolutionRepositoryFileDescriptorSupplier() {
        }
    }

    private static final class CreateSolutionRepositoryMethodDescriptorSupplier extends CreateSolutionRepositoryBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {

        private final java.lang.String methodName;

        CreateSolutionRepositoryMethodDescriptorSupplier(java.lang.String methodName) {
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
            synchronized (CreateSolutionRepositoryGrpc.class) {
                result = serviceDescriptor;
                if (result == null) {
                    serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME).setSchemaDescriptor(new CreateSolutionRepositoryFileDescriptorSupplier())
                            .build();
                }
            }
        }
        return result;
    }
}
