package de.tum.cit.aet.artemis.hyperion.generated;

/**
 * <pre>
 * Exercise Creation Step 4: Create Template Repository
 * </pre>
 */
@javax.annotation.Generated(value = "by gRPC proto compiler (version 1.73.0)", comments = "Source: de/tum/cit/aet/artemis/hyperion/proto/hyperion.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class CreateTemplateRepositoryGrpc {

    private CreateTemplateRepositoryGrpc() {
    }

    public static final java.lang.String SERVICE_NAME = "de.tum.cit.aet.artemis.hyperion.CreateTemplateRepository";

    // Static method descriptors that strictly reflect the proto.
    /**
     * Creates a new async stub that supports all call types for the service
     */
    public static CreateTemplateRepositoryStub newStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CreateTemplateRepositoryStub> factory = new io.grpc.stub.AbstractStub.StubFactory<CreateTemplateRepositoryStub>() {

            @java.lang.Override
            public CreateTemplateRepositoryStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new CreateTemplateRepositoryStub(channel, callOptions);
            }
        };
        return CreateTemplateRepositoryStub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports all types of calls on the service
     */
    public static CreateTemplateRepositoryBlockingV2Stub newBlockingV2Stub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CreateTemplateRepositoryBlockingV2Stub> factory = new io.grpc.stub.AbstractStub.StubFactory<CreateTemplateRepositoryBlockingV2Stub>() {

            @java.lang.Override
            public CreateTemplateRepositoryBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new CreateTemplateRepositoryBlockingV2Stub(channel, callOptions);
            }
        };
        return CreateTemplateRepositoryBlockingV2Stub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports unary and streaming output calls on the service
     */
    public static CreateTemplateRepositoryBlockingStub newBlockingStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CreateTemplateRepositoryBlockingStub> factory = new io.grpc.stub.AbstractStub.StubFactory<CreateTemplateRepositoryBlockingStub>() {

            @java.lang.Override
            public CreateTemplateRepositoryBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new CreateTemplateRepositoryBlockingStub(channel, callOptions);
            }
        };
        return CreateTemplateRepositoryBlockingStub.newStub(factory, channel);
    }

    /**
     * Creates a new ListenableFuture-style stub that supports unary calls on the service
     */
    public static CreateTemplateRepositoryFutureStub newFutureStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<CreateTemplateRepositoryFutureStub> factory = new io.grpc.stub.AbstractStub.StubFactory<CreateTemplateRepositoryFutureStub>() {

            @java.lang.Override
            public CreateTemplateRepositoryFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new CreateTemplateRepositoryFutureStub(channel, callOptions);
            }
        };
        return CreateTemplateRepositoryFutureStub.newStub(factory, channel);
    }

    /**
     * <pre>
     * Exercise Creation Step 4: Create Template Repository
     * </pre>
     */
    public interface AsyncService {
    }

    /**
     * Base class for the server implementation of the service CreateTemplateRepository.
     *
     * <pre>
     * Exercise Creation Step 4: Create Template Repository
     * </pre>
     */
    public abstract static class CreateTemplateRepositoryImplBase implements io.grpc.BindableService, AsyncService {

        @java.lang.Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return CreateTemplateRepositoryGrpc.bindService(this);
        }
    }

    /**
     * A stub to allow clients to do asynchronous rpc calls to service CreateTemplateRepository.
     *
     * <pre>
     * Exercise Creation Step 4: Create Template Repository
     * </pre>
     */
    public static final class CreateTemplateRepositoryStub extends io.grpc.stub.AbstractAsyncStub<CreateTemplateRepositoryStub> {

        private CreateTemplateRepositoryStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CreateTemplateRepositoryStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CreateTemplateRepositoryStub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do synchronous rpc calls to service CreateTemplateRepository.
     *
     * <pre>
     * Exercise Creation Step 4: Create Template Repository
     * </pre>
     */
    public static final class CreateTemplateRepositoryBlockingV2Stub extends io.grpc.stub.AbstractBlockingStub<CreateTemplateRepositoryBlockingV2Stub> {

        private CreateTemplateRepositoryBlockingV2Stub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CreateTemplateRepositoryBlockingV2Stub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CreateTemplateRepositoryBlockingV2Stub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do limited synchronous rpc calls to service CreateTemplateRepository.
     *
     * <pre>
     * Exercise Creation Step 4: Create Template Repository
     * </pre>
     */
    public static final class CreateTemplateRepositoryBlockingStub extends io.grpc.stub.AbstractBlockingStub<CreateTemplateRepositoryBlockingStub> {

        private CreateTemplateRepositoryBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CreateTemplateRepositoryBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CreateTemplateRepositoryBlockingStub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do ListenableFuture-style rpc calls to service CreateTemplateRepository.
     *
     * <pre>
     * Exercise Creation Step 4: Create Template Repository
     * </pre>
     */
    public static final class CreateTemplateRepositoryFutureStub extends io.grpc.stub.AbstractFutureStub<CreateTemplateRepositoryFutureStub> {

        private CreateTemplateRepositoryFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected CreateTemplateRepositoryFutureStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new CreateTemplateRepositoryFutureStub(channel, callOptions);
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

    private abstract static class CreateTemplateRepositoryBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {

        CreateTemplateRepositoryBaseDescriptorSupplier() {
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
            return de.tum.cit.aet.artemis.hyperion.generated.HyperionServiceProto.getDescriptor();
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
            return getFileDescriptor().findServiceByName("CreateTemplateRepository");
        }
    }

    private static final class CreateTemplateRepositoryFileDescriptorSupplier extends CreateTemplateRepositoryBaseDescriptorSupplier {

        CreateTemplateRepositoryFileDescriptorSupplier() {
        }
    }

    private static final class CreateTemplateRepositoryMethodDescriptorSupplier extends CreateTemplateRepositoryBaseDescriptorSupplier
            implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {

        private final java.lang.String methodName;

        CreateTemplateRepositoryMethodDescriptorSupplier(java.lang.String methodName) {
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
            synchronized (CreateTemplateRepositoryGrpc.class) {
                result = serviceDescriptor;
                if (result == null) {
                    serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME).setSchemaDescriptor(new CreateTemplateRepositoryFileDescriptorSupplier())
                            .build();
                }
            }
        }
        return result;
    }
}
