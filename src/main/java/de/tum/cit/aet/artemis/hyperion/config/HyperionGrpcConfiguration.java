package de.tum.cit.aet.artemis.hyperion.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.edutelligence.hyperion.ReviewAndRefineGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

/**
 * Configuration for Hyperion gRPC client.
 */
@Profile(PROFILE_HYPERION)
@Configuration
@Lazy
public class HyperionGrpcConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HyperionGrpcConfiguration.class);

    private final HyperionConfigurationProperties properties;

    public HyperionGrpcConfiguration(HyperionConfigurationProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates a gRPC managed channel for communication with Hyperion.
     * Only created if no test channel is available.
     *
     * @return the managed channel
     */
    @Bean(destroyMethod = "shutdownNow")
    @ConditionalOnMissingBean(name = "hyperionTestChannel")
    public ManagedChannel hyperionGrpcChannel() {
        log.info("Creating Hyperion gRPC channel to {}:{} (TLS: {})", properties.getHost(), properties.getPort(), properties.isUseTls());

        // Validate TLS configuration
        if (properties.isUseTls() && !properties.isValidTlsConfiguration()) {
            throw new IllegalStateException("When TLS is enabled, all certificate paths must be configured for mTLS: " + "client-cert-path, client-key-path, and server-ca-path");
        }

        var channelBuilder = ManagedChannelBuilder.forAddress(properties.getHost(), properties.getPort()).keepAliveTime(30, TimeUnit.SECONDS).keepAliveTimeout(5, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true).maxInboundMessageSize(16 * 1024 * 1024); // 16MB max message size

        // Configure TLS based on settings
        if (properties.isUseTls()) {
            configureSecureChannel(channelBuilder);
        }
        else {
            channelBuilder.usePlaintext();
            log.info("Hyperion gRPC channel configured without TLS (development mode)");
        }

        return channelBuilder.build();
    }

    /**
     * Configures TLS for the gRPC channel with client certificate authentication.
     */
    private void configureSecureChannel(ManagedChannelBuilder<?> channelBuilder) {
        try {
            if (channelBuilder instanceof NettyChannelBuilder nettyBuilder) {
                var sslContextBuilder = GrpcSslContexts.forClient();

                // Configure server CA if provided (for server certificate validation)
                if (properties.getServerCaPath() != null && !properties.getServerCaPath().isEmpty()) {
                    var serverCaCert = Files.newInputStream(Paths.get(properties.getServerCaPath()));
                    sslContextBuilder.trustManager(serverCaCert);
                    log.info("Configured server CA certificate: {}", properties.getServerCaPath());
                }

                // Configure client certificate if provided (for mutual TLS authentication)
                if (properties.getClientCertPath() != null && !properties.getClientCertPath().isEmpty() && properties.getClientKeyPath() != null
                        && !properties.getClientKeyPath().isEmpty()) {

                    var clientCert = Files.newInputStream(Paths.get(properties.getClientCertPath()));
                    var clientKey = Files.newInputStream(Paths.get(properties.getClientKeyPath()));
                    sslContextBuilder.keyManager(clientCert, clientKey);
                    log.info("Configured client certificate authentication: {}", properties.getClientCertPath());
                }

                nettyBuilder.sslContext(sslContextBuilder.build());
                log.info("Hyperion gRPC channel configured with TLS");
            }
            else {
                log.error("Hyperion gRPC channel builder is not an instance of NettyChannelBuilder, cannot configure TLS");
                throw new IllegalStateException("Hyperion gRPC channel builder is not compatible with TLS configuration");
            }
        }
        catch (Exception e) {
            log.error("Failed to configure TLS for Hyperion gRPC channel", e);
            throw new RuntimeException("TLS configuration failed", e);
        }
    }

    /**
     * Creates a review and refine stub for consistency checks and problem statement rewriting.
     * Only created if no test stub is available.
     *
     * @param hyperionGrpcChannel the production gRPC channel
     * @return the review and refine stub
     */
    @Bean
    @ConditionalOnMissingBean(ReviewAndRefineGrpc.ReviewAndRefineBlockingStub.class)
    public ReviewAndRefineGrpc.ReviewAndRefineBlockingStub hyperionReviewAndRefineStub(ManagedChannel hyperionGrpcChannel) {
        return ReviewAndRefineGrpc.newBlockingStub(hyperionGrpcChannel).withDeadlineAfter(properties.getDefaultTimeoutSeconds(), TimeUnit.SECONDS);
    }
}
