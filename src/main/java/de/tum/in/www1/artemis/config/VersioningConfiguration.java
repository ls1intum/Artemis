package de.tum.in.www1.artemis.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the available API versions. Gets replaced in testing. Don't add any more functionality or data.
 */
@Configuration
public class VersioningConfiguration {

    /**
     * List of existing versions. Extend this if new version is created.
     */
    public final static List<Integer> API_VERSIONS = List.of(1);

    @Bean
    public List<Integer> apiVersions() {
        return API_VERSIONS;
    }
}
