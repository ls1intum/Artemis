package de.tum.in.www1.artemis.config;

import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.in.www1.artemis.versioning.VersionRequestMappingHandlerMapping;

/**
 * Overwrites the default RequestMappingHandlerMapping with a versioned one.
 */
@Configuration
public class RequestMappingConfiguration {

    /**
     * Registers the versioned request mapping handler mapping {@link VersionRequestMappingHandlerMapping}
     *
     * @return the versioned request mapping handler mapping {@link VersionRequestMappingHandlerMapping}
     */
    @Bean
    public WebMvcRegistrations webMvcRegistrations() {
        return new WebMvcRegistrations() {

            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new VersionRequestMappingHandlerMapping("api", "v");
            }
        };
    }
}
