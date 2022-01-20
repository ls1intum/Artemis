package de.tum.in.www1.artemis.service.connectors;

import java.util.Arrays;
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
@ConfigurationProperties(prefix = "artemis.continuous-integration.build")
public class ProgrammingLanguageConfiguration {

    private Map<ProgrammingLanguage, String> images = new HashMap<>();

    /**
     * Set the map of languages to build images.
     *
     * @param buildImages the map of languages to build images
     */
    public void setImages(Map<ProgrammingLanguage, String> buildImages) {
        if (!Arrays.stream(ProgrammingLanguage.values()).allMatch(buildImages::containsKey)) {
            var missing = Arrays.stream(ProgrammingLanguage.values()).filter(programmingLanguage -> !buildImages.containsKey(programmingLanguage)).toList();
            throw new IllegalArgumentException("Not all Build Images for Programming Languages are set in configuration. Missing: " + missing);
        }
        this.images = buildImages;
    }

    /**
     * Get an unmodifiable map that connects a {@link ProgrammingLanguage} with its configured docker image.
     *
     * @return the map of languages to their docker images
     */
    public Map<ProgrammingLanguage, String> getImages() {
        return Collections.unmodifiableMap(images);
    }
}
