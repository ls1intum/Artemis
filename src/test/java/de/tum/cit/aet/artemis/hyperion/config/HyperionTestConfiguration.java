package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.hyperion.proto.ReviewAndRefineGrpc;
import io.grpc.ManagedChannel;

/**
 * Main test configuration for Hyperion integration tests.
 * Imports mock services and test server configuration.
 */
@TestConfiguration
@Profile(PROFILE_HYPERION)
@Import({ HyperionMockServiceConfiguration.class, HyperionTestServerConfiguration.class })
public class HyperionTestConfiguration {

    /**
     * Test stub using the in-process channel.
     * Overrides the production stub during tests.
     */
    @Bean
    @Primary
    public ReviewAndRefineGrpc.ReviewAndRefineBlockingStub hyperionReviewAndRefineStub(ManagedChannel hyperionTestChannel) {
        return ReviewAndRefineGrpc.newBlockingStub(hyperionTestChannel);
    }
}
