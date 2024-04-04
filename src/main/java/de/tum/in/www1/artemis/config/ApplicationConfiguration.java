package de.tum.in.www1.artemis.config;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;

@Configuration
public class ApplicationConfiguration {

    // this prevents default authentication when the build agent is enabled without the core profile
    @Profile({ "!" + PROFILE_CORE, PROFILE_BUILDAGENT })
    @Bean
    public AuthenticationManager noopAuthenticationManager() {
        return authentication -> {
            throw new AuthenticationServiceException("Authentication is disabled");
        };
    }
}
