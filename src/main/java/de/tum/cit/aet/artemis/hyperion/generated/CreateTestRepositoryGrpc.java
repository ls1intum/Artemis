package de.tum.cit.aet.artemis.hyperion.generated;

/**
 * <pre>
 * Exercise Creation Step 5: Create Test Repository
 * </pre>
 */
@javax.annotation.Generated(value = "by gRPC proto compiler (version 1.73.0)", comments = "Source: de/tum/cit/aet/artemis/hyperion/proto/hyperion.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class CreateTestRepositoryGrpc {

    private CreateTestRepositoryGrpc() {
    }

    public static final java.lang.String SERVICE_NAME = "de.tum.cit.aet.artemis.hyperion.CreateTestRepository";

    // Static method descriptors that strictly reflect the proto.
    /**
     * Creates a new async stub that supports all call types for the service
     */
    public static CreateTestRepositoryStub newStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CreateTestRepositoryStub> factory = new io.grpc.stub.AbstractStub.StubFactory<CreateTestRepositoryStub>() {

            @java.lang.Override
            public CreateTestRepositoryStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new CreateTestRepositoryStub(channel, callOptions);
            }
        };
        return CreateTestRepositoryStub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports all types of calls on the service
     */
    public static CreateTestRepositoryBlockingV2Stub newBlockingV2Stub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CreateTestRepositoryBlockingV2Stub> factory = new io.grpc.stub.AbstractStub.StubFactory<CreateTestRepositoryBlockingV2Stub>() {

            @java.lang.Override
            public CreateTestRepositoryBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new CreateTestRepositoryBlockingV2Stub(channel, callOptions);
            }
        };
        return CreateTestRepositoryBlockingV2Stub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports unary and streaming output calls on the service
     */
    public static CreateTestRepositoryBlockingStub newBlockingStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CreateTestRepositoryBlockingStub> factory = new io.grpc.stub.AbstractStub.StubFactory<CreateTestRepositoryBlockingStub>() {

            @java.lang.Override
            public CreateTestRepositoryBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new CreateTestRepositoryBlockingStub(channel, callOptions);
            }
        };
        return CreateTestRepositoryBlockingStub.newStub(factory, channel);
    }

    /**
     * Creates a new ListenableFuture-style stub that supports unary calls on the service
     */
    public static CreateTestRepositoryFutureStub newFutureStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CreateTestRepositoryFutureStub> factory = new io.grpc.stub.AbstractStub.StubFactory<CreateTestRepositoryFutureStub>() {

            @java.lang.Override
            public CreateTestRepositoryFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new CreateTestRepositoryFutureStub(channel, callOptions);
            }
        };
        return CreateTestRepositoryFutureStub.newStub(factory, channel);
    }

    /**
     * <pre>
     * Exercise Creation Step 5: Create Test Repository
     * </pre>
     */
    public interface AsyncService {
    }

    /**
     * Base class for the server implementation of the service CreateTestRepository.
     *
     * <pre>
     * Exercise Creation Step 5: Create Test Repository
     * </pre>
     */
    public abstract static class CreateTestRepositoryImplBase implements io.grpc.BindableService, AsyncService {

        @java.lang.Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return CreateTestRepositoryGrpc.bindService(this);
        }
    }

    /**
     * A stub to allow clients to do asynchronous rpc calls to service CreateTestRepository.
     *
     * <pre>
     * Exercise Creation Step 5: Create Test Repository
     * </pre>
     */
    public static final class CreateTestRepositoryStub extends io.grpc.stub.AbstractAsyncStub<CreateTestRepositoryStub> {

        private CreateTestRepositoryStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CreateTestRepositoryStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CreateTestRepositoryStub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do synchronous rpc calls to service CreateTestRepository.
     *
     * <pre>
     * Exercise Creation Step 5: Create Test Repository
     * </pre>
     */
    public static final class CreateTestRepositoryBlockingV2Stub extends io.grpc.stub.AbstractBlockingStub<CreateTestRepositoryBlockingV2Stub> {

        private CreateTestRepositoryBlockingV2Stub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CreateTestRepositoryBlockingV2Stub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CreateTestRepositoryBlockingV2Stub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do limited synchronous rpc calls to service CreateTestRepository.
     *
     * <pre>
     * Exercise Creation Step 5: Create Test Repository
     * </pre>
     */
    public static final class CreateTestRepositoryBlockingStub extends io.grpc.stub.AbstractBlockingStub<CreateTestRepositoryBlockingStub> {

        private CreateTestRepositoryBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CreateTestRepositoryBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CreateTestRepositoryBlockingStub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do ListenableFuture-style rpc calls to service CreateTestRepository.
     *
     * <pre>
     * Exercise Creation Step 5: Create Test Repository
     * </pre>
     */
    public static final class CreateTestRepositoryFutureStub extends io.grpc.stub.AbstractFutureStub<CreateTestRepositoryFutureStub> {

        private CreateTestRepositoryFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CreateTestRepositoryFutureStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CreateTestRepositoryFutureStub(channel, callOptions);
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

    private abstract static class CreateTestRepositoryBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {

        CreateTestRepositoryBaseDescriptorSupplier() {
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
            return de.tum.cit.aet.artemis.hyperion.generated.HyperionServiceProto.getDescriptor();
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
            return getFileDescriptor().findServiceByName("CreateTestRepository");
        }
    }

    private static final class CreateTestRepositoryFileDescriptorSupplier extends CreateTestRepositoryBaseDescriptorSupplier {

        CreateTestRepositoryFileDescriptorSupplier() {
        }
    }

    private static final class CreateTestRepositoryMethodDescriptorSupplier extends CreateTestRepositoryBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {

        private final java.lang.String methodName;

        CreateTestRepositoryMethodDescriptorSupplier(java.lang.String methodName) {
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
            synchronized (CreateTestRepositoryGrpc.class) {
                result = serviceDescriptor;
                if (result == null) {
                    serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME).setSchemaDescriptor(new CreateTestRepositoryFileDescriptorSupplier()).build();
                }
            }
        }
        return result;
    }
}
