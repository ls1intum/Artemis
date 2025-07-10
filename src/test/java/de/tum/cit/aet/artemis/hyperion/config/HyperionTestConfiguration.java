package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.hyperion.proto.ReviewAndRefineGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;

/**
 * Test configuration for in-process gRPC testing of Hyperion services.
 *
 * Creates an embedded gRPC server and client for testing without external dependencies.
 * Uses @Primary beans to override the production gRPC configuration during tests.
 */
@Configuration
@Profile(PROFILE_HYPERION + " & " + SPRING_PROFILE_TEST)
public class HyperionTestConfiguration {

    private static HyperionTestReviewAndRefineService testReviewAndRefineService;

    private static HyperionTestHealthService testHealthService;

    private static String serverName;

    private static Server server;

    private static ManagedChannel channel;

    /**
     * Initialize the in-process gRPC server and channel when the class is loaded.
     */
    static {
        // Generate unique server name to avoid conflicts
        serverName = InProcessServerBuilder.generateName();
        testReviewAndRefineService = new HyperionTestReviewAndRefineService();
        testHealthService = new HyperionTestHealthService();

        try {
            // Start in-process server
            server = InProcessServerBuilder.forName(serverName).directExecutor().addService(testReviewAndRefineService).addService(testHealthService).build().start();

            // Create channel
            channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();

            // Add shutdown hook for graceful cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown()));

        }
        catch (Exception e) {
            throw new RuntimeException("Failed to start in-process gRPC server", e);
        }
    }

    private static void shutdown() {
        if (channel != null) {
            channel.shutdown();
            try {
                if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                    channel.shutdownNow();
                }
            }
            catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (server != null) {
            server.shutdown();
            try {
                if (!server.awaitTermination(5, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            }
            catch (InterruptedException e) {
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Bean
    @Primary
    public HyperionTestReviewAndRefineService testReviewAndRefineService() {
        return testReviewAndRefineService;
    }

    @Bean
    @Primary
    public HyperionTestHealthService testHealthService() {
        return testHealthService;
    }

    @Bean
    @Primary
    public ReviewAndRefineGrpc.ReviewAndRefineBlockingStub reviewAndRefineStub() {
        return ReviewAndRefineGrpc.newBlockingStub(channel);
    }

    @Bean
    @Primary
    public ReviewAndRefineGrpc.ReviewAndRefineStub reviewAndRefineAsyncStub() {
        return ReviewAndRefineGrpc.newStub(channel);
    }

    @Bean("hyperionHealthStub")
    @Primary
    public HealthGrpc.HealthBlockingStub healthStub() {
        return HealthGrpc.newBlockingStub(channel);
    }
}
