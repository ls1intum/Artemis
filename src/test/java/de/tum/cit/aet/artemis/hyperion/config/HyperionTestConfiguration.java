package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;

import net.devh.boot.grpc.client.autoconfigure.GrpcClientAutoConfiguration;
import net.devh.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration;
import net.devh.boot.grpc.server.autoconfigure.GrpcServerFactoryAutoConfiguration;

/**
 * Test configuration for gRPC Hyperion services following official grpc-spring patterns.
 *
 * This configuration implements the exact pattern from the official grpc-spring documentation:
 * https://grpc-ecosystem.github.io/grpc-spring/en/client/testing.html#running-a-dummy-server
 * https://grpc-ecosystem.github.io/grpc-spring/en/server/testing.html#integration-tests
 */
@TestConfiguration
@Profile(PROFILE_HYPERION)
@ImportAutoConfiguration({ GrpcServerAutoConfiguration.class,        // Create required server beans
        GrpcServerFactoryAutoConfiguration.class, // Select server implementation
        GrpcClientAutoConfiguration.class         // Support @GrpcClient annotation
})
@ComponentScan("de.tum.cit.aet.artemis.hyperion") // Scan for @GrpcService implementations
public class HyperionTestConfiguration {

}
