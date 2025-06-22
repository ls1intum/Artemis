package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.io.IOException;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;

/**
 * Test configuration providing in-process gRPC server and channel.
 */
@TestConfiguration
@Profile(PROFILE_HYPERION)
public class HyperionTestServerConfiguration {

    private static final String TEST_SERVER_NAME = "hyperion-test-server";

    /**
     * In-process gRPC server for testing.
     */
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public Server hyperionTestServer(HyperionMockServiceConfiguration.MockReviewAndRefineService mockService, HyperionMockServiceConfiguration.MockHealthService mockHealthService)
            throws IOException {
        return InProcessServerBuilder.forName(TEST_SERVER_NAME).directExecutor().addService(mockService).addService(mockHealthService).build();
    }

    /**
     * In-process gRPC channel for testing.
     * Overrides the production channel during tests.
     */
    @Bean(destroyMethod = "shutdownNow")
    @DependsOn("hyperionTestServer")
    public ManagedChannel hyperionTestChannel() {
        return InProcessChannelBuilder.forName(TEST_SERVER_NAME).directExecutor().build();
    }
}
