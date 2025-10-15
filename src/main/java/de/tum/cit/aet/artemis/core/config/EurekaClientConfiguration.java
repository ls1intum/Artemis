package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.configuration.SSLContextFactory;
import org.springframework.cloud.configuration.TlsProperties;
import org.springframework.cloud.netflix.eureka.RestClientTimeoutProperties;
import org.springframework.cloud.netflix.eureka.http.DefaultEurekaClientHttpRequestFactorySupplier;
import org.springframework.cloud.netflix.eureka.http.EurekaClientHttpRequestFactorySupplier;
import org.springframework.cloud.netflix.eureka.http.RestClientDiscoveryClientOptionalArgs;
import org.springframework.cloud.netflix.eureka.http.RestClientTransportClientFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

/**
 * This class is necessary to avoid using Jersey (which has an issue deserializing Eureka responses) after the spring boot upgrade.
 * It provides the RestClientTransportClientFactories and RestClientDiscoveryClientOptionalArgs that would normally not be instantiated
 * when Jersey is found by Eureka.
 */
@Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
@Configuration
@Lazy(false)
public class EurekaClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EurekaClientConfiguration.class);

    /**
     * Configures and returns {@link RestClientDiscoveryClientOptionalArgs} for Eureka client communication,
     * with optional TLS/SSL setup based on provided configuration.
     * <p>
     * This method leverages the {@link EurekaClientHttpRequestFactorySupplier} to configure the RestClient
     * specifically for Eureka client interactions. If TLS is enabled in the provided {@link TlsProperties},
     * a custom SSLContext is set up to ensure secure communication.
     * </p>
     *
     * @param tlsProperties             The TLS configuration properties, used to check if TLS is enabled and to configure it accordingly.
     * @param restClientBuilderProvider The provider for the {@link RestClient.Builder} instance, if available.
     * @return A configured instance of {@link RestClientDiscoveryClientOptionalArgs} for Eureka client,
     *         potentially with SSL/TLS enabled if specified in the {@code tlsProperties}.
     * @throws GeneralSecurityException If there's an issue with setting up the SSL/TLS context.
     * @throws IOException              If there's an I/O error during the setup.
     * @see TlsProperties
     * @see EurekaClientHttpRequestFactorySupplier
     */
    @Bean
    public RestClientDiscoveryClientOptionalArgs restClientDiscoveryClientOptionalArgs(TlsProperties tlsProperties, ObjectProvider<RestClient.Builder> restClientBuilderProvider)
            throws GeneralSecurityException, IOException {
        log.debug("Using RestClient for the Eureka client.");
        // The Eureka DiscoveryClientOptionalArgsConfiguration invokes a private method setupTLS.
        // This code is taken from that method.
        var supplier = new DefaultEurekaClientHttpRequestFactorySupplier(new RestClientTimeoutProperties(), Set.of());
        var args = new RestClientDiscoveryClientOptionalArgs(supplier, () -> restClientBuilderProvider.getIfAvailable(RestClient::builder));
        if (tlsProperties.isEnabled()) {
            SSLContextFactory factory = new SSLContextFactory(tlsProperties);
            args.setSSLContext(factory.createSSLContext());
        }
        return args;
    }

    @Bean
    public RestClientTransportClientFactories restClientTransportClientFactories(RestClientDiscoveryClientOptionalArgs optionalArgs) {
        return new RestClientTransportClientFactories(optionalArgs);
    }
}
