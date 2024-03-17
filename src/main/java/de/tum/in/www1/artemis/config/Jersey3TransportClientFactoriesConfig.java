package de.tum.in.www1.artemis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.netflix.discovery.shared.transport.jersey3.Jersey3TransportClientFactories;

@Configuration
public class Jersey3TransportClientFactoriesConfig {

    @Bean
    public Jersey3TransportClientFactories jersey3TransportClientFactories() {
        return new Jersey3TransportClientFactories();
    }

}
