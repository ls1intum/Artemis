package de.tum.in.www1.artemis.service.connectors;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

/**
 * This configuration file provides functions to get the configured Docker Images for {@link ProgrammingLanguage ProgrammingLanguages}.
 *
 * @author Dominik Fuchss
 */
@ConfigurationProperties(prefix = "artemis.continuous-integration")
public class ProgrammingLanguageConfiguration {

    private Map<ProgrammingLanguage, String> buildImages = new HashMap<>();

    public void setBuildImages(Map<ProgrammingLanguage, String> buildImages) {
        this.buildImages = buildImages;
    }

    /**
     * Get an unmodifiable map that connects a {@link ProgrammingLanguage} with its configured docker image.
     *
     * @return the map of languages to their docker images
     */
    public Map<ProgrammingLanguage, String> getBuildImages() {
        return Collections.unmodifiableMap(buildImages);
    }

    /**
     * Check whether the image for some {@link ProgrammingLanguage} is defined.
     *
     * @param programmingLanguage the language to check
     * @return indicator whether the image for the language is configured
     */
    public boolean containsLanguage(ProgrammingLanguage programmingLanguage) {
        return buildImages.containsKey(programmingLanguage);
    }
}
