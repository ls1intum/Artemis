package de.tum.in.www1.artemis.config;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

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

    @Bean
    public RestTemplateDiscoveryClientOptionalArgs restTemplateDiscoveryClientOptionalArgs(TlsProperties tlsProperties,
            EurekaClientHttpRequestFactorySupplier eurekaClientHttpRequestFactorySupplier) throws GeneralSecurityException, IOException {
        log.info("Using RestTemplate for the Eureka client.");
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
