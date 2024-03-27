package de.tum.in.www1.artemis.config;

import java.util.List;

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

    private final List<Integer> apiVersions;

    public RequestMappingConfiguration(List<Integer> apiVersions) {
        this.apiVersions = apiVersions;
    }

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
                return new VersionRequestMappingHandlerMapping(apiVersions, "api", "v");
            }
        };
    }
}
