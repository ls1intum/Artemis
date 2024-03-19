package de.tum.in.www1.artemis.config;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.netflix.discovery.shared.transport.jersey3.Jersey3TransportClientFactories;

/**
 * This class is only necessary since Spring Boot 3. If it is not available, the application server would not start in a configuration for a multi-node setup
 * The app would crash during startup when trying to connect to the registry, if this configuration and bean is not available
 */
@Profile({ PROFILE_CORE, PROFILE_BUILDAGENT })
@Configuration
public class Jersey3TransportClientFactoriesConfig {

    @Bean
    public Jersey3TransportClientFactories jersey3TransportClientFactories() {
        return new Jersey3TransportClientFactories();
    }

}
