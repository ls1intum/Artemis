package de.tum.cit.aet.artemis.hyperion.generated;

/**
 * <pre>
 * Exercise Creation Step 7: Configure Grading
 * </pre>
 */
@javax.annotation.Generated(value = "by gRPC proto compiler (version 1.73.0)", comments = "Source: de/tum/cit/aet/artemis/hyperion/proto/hyperion.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ConfigureGradingGrpc {

    private ConfigureGradingGrpc() {
    }

    public static final java.lang.String SERVICE_NAME = "de.tum.cit.aet.artemis.hyperion.ConfigureGrading";

    // Static method descriptors that strictly reflect the proto.
    /**
     * Creates a new async stub that supports all call types for the service
     */
    public static ConfigureGradingStub newStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<ConfigureGradingStub> factory = new io.grpc.stub.AbstractStub.StubFactory<ConfigureGradingStub>() {

            @java.lang.Override
            public ConfigureGradingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new ConfigureGradingStub(channel, callOptions);
            }
        };
        return ConfigureGradingStub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports all types of calls on the service
     */
    public static ConfigureGradingBlockingV2Stub newBlockingV2Stub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<ConfigureGradingBlockingV2Stub> factory = new io.grpc.stub.AbstractStub.StubFactory<ConfigureGradingBlockingV2Stub>() {

            @java.lang.Override
            public ConfigureGradingBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new ConfigureGradingBlockingV2Stub(channel, callOptions);
            }
        };
        return ConfigureGradingBlockingV2Stub.newStub(factory, channel);
    }

    /**
     * Creates a new blocking-style stub that supports unary and streaming output calls on the service
     */
    public static ConfigureGradingBlockingStub newBlockingStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<ConfigureGradingBlockingStub> factory = new io.grpc.stub.AbstractStub.StubFactory<ConfigureGradingBlockingStub>() {

            @java.lang.Override
            public ConfigureGradingBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new ConfigureGradingBlockingStub(channel, callOptions);
            }
        };
        return ConfigureGradingBlockingStub.newStub(factory, channel);
    }

    /**
     * Creates a new ListenableFuture-style stub that supports unary calls on the service
     */
    public static ConfigureGradingFutureStub newFutureStub(io.grpc.Channel channel) {
        io.grpc.stub.AbstractStub.StubFactory<ConfigureGradingFutureStub> factory = new io.grpc.stub.AbstractStub.StubFactory<ConfigureGradingFutureStub>() {

            @java.lang.Override
            public ConfigureGradingFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
                return new ConfigureGradingFutureStub(channel, callOptions);
            }
        };
        return ConfigureGradingFutureStub.newStub(factory, channel);
    }

    /**
     * <pre>
     * Exercise Creation Step 7: Configure Grading
     * </pre>
     */
    public interface AsyncService {
    }

    /**
     * Base class for the server implementation of the service ConfigureGrading.
     *
     * <pre>
     * Exercise Creation Step 7: Configure Grading
     * </pre>
     */
    public abstract static class ConfigureGradingImplBase implements io.grpc.BindableService, AsyncService {

        @java.lang.Override
        public final io.grpc.ServerServiceDefinition bindService() {
            return ConfigureGradingGrpc.bindService(this);
        }
    }

    /**
     * A stub to allow clients to do asynchronous rpc calls to service ConfigureGrading.
     *
     * <pre>
     * Exercise Creation Step 7: Configure Grading
     * </pre>
     */
    public static final class ConfigureGradingStub extends io.grpc.stub.AbstractAsyncStub<ConfigureGradingStub> {

        private ConfigureGradingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected ConfigureGradingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new ConfigureGradingStub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do synchronous rpc calls to service ConfigureGrading.
     *
     * <pre>
     * Exercise Creation Step 7: Configure Grading
     * </pre>
     */
    public static final class ConfigureGradingBlockingV2Stub extends io.grpc.stub.AbstractBlockingStub<ConfigureGradingBlockingV2Stub> {

        private ConfigureGradingBlockingV2Stub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected ConfigureGradingBlockingV2Stub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new ConfigureGradingBlockingV2Stub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do limited synchronous rpc calls to service ConfigureGrading.
     *
     * <pre>
     * Exercise Creation Step 7: Configure Grading
     * </pre>
     */
    public static final class ConfigureGradingBlockingStub extends io.grpc.stub.AbstractBlockingStub<ConfigureGradingBlockingStub> {

        private ConfigureGradingBlockingStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected ConfigureGradingBlockingStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new ConfigureGradingBlockingStub(channel, callOptions);
        }
    }

    /**
     * A stub to allow clients to do ListenableFuture-style rpc calls to service ConfigureGrading.
     *
     * <pre>
     * Exercise Creation Step 7: Configure Grading
     * </pre>
     */
    public static final class ConfigureGradingFutureStub extends io.grpc.stub.AbstractFutureStub<ConfigureGradingFutureStub> {

        private ConfigureGradingFutureStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            super(channel, callOptions);
        }

        @java.lang.Override
        protected ConfigureGradingFutureStub build(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
            return new ConfigureGradingFutureStub(channel, callOptions);
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

    private abstract static class ConfigureGradingBaseDescriptorSupplier implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {

        ConfigureGradingBaseDescriptorSupplier() {
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
            return de.tum.cit.aet.artemis.hyperion.generated.HyperionServiceProto.getDescriptor();
        }

        @java.lang.Override
        public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
            return getFileDescriptor().findServiceByName("ConfigureGrading");
        }
    }

    private static final class ConfigureGradingFileDescriptorSupplier extends ConfigureGradingBaseDescriptorSupplier {

        ConfigureGradingFileDescriptorSupplier() {
        }
    }

    private static final class ConfigureGradingMethodDescriptorSupplier extends ConfigureGradingBaseDescriptorSupplier implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {

        private final java.lang.String methodName;

        ConfigureGradingMethodDescriptorSupplier(java.lang.String methodName) {
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
            synchronized (ConfigureGradingGrpc.class) {
                result = serviceDescriptor;
                if (result == null) {
                    serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME).setSchemaDescriptor(new ConfigureGradingFileDescriptorSupplier()).build();
                }
            }
        }
        return result;
    }
}
