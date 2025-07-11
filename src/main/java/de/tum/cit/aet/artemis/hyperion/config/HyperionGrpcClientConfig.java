package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.GRPC_CHANNEL_HYPERION;
import static de.tum.cit.aet.artemis.core.config.Constants.GRPC_IDLE_TIMEOUT_MINUTES;
import static de.tum.cit.aet.artemis.core.config.Constants.GRPC_KEEP_ALIVE_TIMEOUT_SECONDS;
import static de.tum.cit.aet.artemis.core.config.Constants.GRPC_KEEP_ALIVE_TIME_SECONDS;
import static de.tum.cit.aet.artemis.core.config.Constants.GRPC_MAX_INBOUND_MESSAGE_SIZE;
import static de.tum.cit.aet.artemis.core.config.Constants.GRPC_MAX_RETRY_ATTEMPTS;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.hyperion.proto.ReviewAndRefineGrpc;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.TlsChannelCredentials;
import io.grpc.health.v1.HealthGrpc;

/**
 * gRPC client configuration for Hyperion services.
 *
 * Configures TLS/mTLS, connection settings, and creates gRPC stubs.
 * Excluded when the test profile is active to enable test overrides.
 */
@Configuration
@Lazy
@Profile(PROFILE_HYPERION + " & !" + SPRING_PROFILE_TEST)
public class HyperionGrpcClientConfig {

    private static final Logger log = LoggerFactory.getLogger(HyperionGrpcClientConfig.class);

    /**
     * Creates the gRPC channel with TLS configuration.
     */
    @Bean(name = GRPC_CHANNEL_HYPERION, destroyMethod = "shutdownNow")
    @Qualifier(GRPC_CHANNEL_HYPERION)
    ManagedChannel hyperionChannel(HyperionConfigurationProperties config) throws IOException {
        String host = config.getHost();
        int port = config.getPort();

        log.info("Creating gRPC channel for Hyperion at {}:{}", host, port);

        ManagedChannelBuilder<?> channelBuilder;

        if (config.isTlsEnabled()) {
            log.info("Configuring TLS for Hyperion channel");
            var tlsBuilder = TlsChannelCredentials.newBuilder().trustManager(config.getRootCa().toFile());

            if (config.isMutualTlsEnabled()) {
                log.info("Enabling mutual TLS with client certificates");
                tlsBuilder.keyManager(config.getClientCert().toFile(), config.getClientKey().toFile());
            }
            else {
                log.info("Mutual TLS is disabled");
            }
            channelBuilder = Grpc.newChannelBuilderForAddress(host, port, tlsBuilder.build());
        }
        else {
            log.warn("TLS is disabled");
            channelBuilder = ManagedChannelBuilder.forAddress(host, port).usePlaintext();
        }

        return channelBuilder.keepAliveTime(GRPC_KEEP_ALIVE_TIME_SECONDS, TimeUnit.SECONDS).keepAliveTimeout(GRPC_KEEP_ALIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true).idleTimeout(GRPC_IDLE_TIMEOUT_MINUTES, TimeUnit.MINUTES).maxInboundMessageSize(GRPC_MAX_INBOUND_MESSAGE_SIZE).enableRetry()
                .maxRetryAttempts(GRPC_MAX_RETRY_ATTEMPTS).build();
    }

    @Bean("hyperionHealthStub")
    HealthGrpc.HealthBlockingStub hyperionHealthStub(@Qualifier(GRPC_CHANNEL_HYPERION) ManagedChannel channel) {
        return HealthGrpc.newBlockingStub(channel);
    }

    @Bean
    ReviewAndRefineGrpc.ReviewAndRefineBlockingStub reviewAndRefineStub(@Qualifier(GRPC_CHANNEL_HYPERION) ManagedChannel channel) {
        return ReviewAndRefineGrpc.newBlockingStub(channel);
    }
}
