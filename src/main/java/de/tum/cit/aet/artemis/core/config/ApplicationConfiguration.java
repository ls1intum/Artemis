package de.tum.cit.aet.artemis.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;

@Configuration
public class ApplicationConfiguration {

    /**
     * Provides a no-op AuthenticationManager that throws an exception, effectively disabling authentication, when the 'buildagent' profile is active
     * and the 'core' profile is not active.
     *
     * @return the no-op AuthenticationManager
     */
    @Bean
    @Conditional(BuildAgentWithoutCoreCondition.class)
    public AuthenticationManager noopAuthenticationManager() {
        return authentication -> {
            throw new AuthenticationServiceException("Authentication is disabled");
        };
    }
}
