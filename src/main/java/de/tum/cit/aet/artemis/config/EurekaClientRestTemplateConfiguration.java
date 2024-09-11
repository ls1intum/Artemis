package de.tum.cit.aet.artemis.config;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.security.GeneralSecurityException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.configuration.SSLContextFactory;
import org.springframework.cloud.configuration.TlsProperties;
import org.springframework.cloud.netflix.eureka.http.EurekaClientHttpRequestFactorySupplier;
import org.springframework.cloud.netflix.eureka.http.RestTemplateDiscoveryClientOptionalArgs;
import org.springframework.cloud.netflix.eureka.http.RestTemplateTransportClientFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * This class is necessary to avoid using Jersey (which has an issue deserializing Eureka responses) after the spring boot upgrade.
 * It provides the RestTemplateTransportClientFactories and RestTemplateDiscoveryClientOptionalArgs that would normally not be instantiated
 * when Jersey is found by Eureka.
 */
@Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
@Configuration
public class EurekaClientRestTemplateConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EurekaClientRestTemplateConfiguration.class);

    /**
     * Configures and returns {@link RestTemplateDiscoveryClientOptionalArgs} for Eureka client communication,
     * with optional TLS/SSL setup based on provided configuration.
     * <p>
     * This method leverages the {@link EurekaClientHttpRequestFactorySupplier} to configure the RestTemplate
     * specifically for Eureka client interactions. If TLS is enabled in the provided {@link TlsProperties},
     * a custom SSLContext is set up to ensure secure communication.
     * </p>
     *
     * @param tlsProperties                          The TLS configuration properties, used to check if TLS is enabled and to configure it accordingly.
     * @param eurekaClientHttpRequestFactorySupplier Supplies the HTTP request factory for the Eureka client RestTemplate.
     * @return A configured instance of {@link RestTemplateDiscoveryClientOptionalArgs} for Eureka client,
     *         potentially with SSL/TLS enabled if specified in the {@code tlsProperties}.
     * @throws GeneralSecurityException If there's an issue with setting up the SSL/TLS context.
     * @throws IOException              If there's an I/O error during the setup.
     * @see TlsProperties
     * @see EurekaClientHttpRequestFactorySupplier
     */
    @Bean
    public RestTemplateDiscoveryClientOptionalArgs restTemplateDiscoveryClientOptionalArgs(TlsProperties tlsProperties,
            EurekaClientHttpRequestFactorySupplier eurekaClientHttpRequestFactorySupplier) throws GeneralSecurityException, IOException {
        log.debug("Using RestTemplate for the Eureka client.");
        // The Eureka DiscoveryClientOptionalArgsConfiguration invokes a private method setupTLS.
        // This code is taken from that method.
        var args = new RestTemplateDiscoveryClientOptionalArgs(eurekaClientHttpRequestFactorySupplier);
        if (tlsProperties.isEnabled()) {
            SSLContextFactory factory = new SSLContextFactory(tlsProperties);
            args.setSSLContext(factory.createSSLContext());
        }
        return args;
    }

    @Bean
    public RestTemplateTransportClientFactories restTemplateTransportClientFactories(RestTemplateDiscoveryClientOptionalArgs optionalArgs) {
        return new RestTemplateTransportClientFactories(optionalArgs);
    }
}
