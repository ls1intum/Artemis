package de.tum.in.www1.artemis.service.connectors;

import java.util.*;

import org.springframework.boot.context.properties.ConfigurationProperties;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;

/**
 * This configuration file provides functions to get the configured Docker Images for {@link ProgrammingLanguage ProgrammingLanguages}.
 *
 * @author Dominik Fuchss
 */
@ConfigurationProperties(prefix = "artemis.continuous-integration.build")
public class ProgrammingLanguageConfiguration {

    // Maps ProgrammingLanguage to Docker image and {ProgrammingLanguage_ProjectType} to Docker images as override
    private Map<String, String> images = new HashMap<>();

    /**
     * Set the map of languages to build images.
     *
     * @param buildImages the map of languages to build images
     */
    public void setImages(Map<String, String> buildImages) {
        // Make sure all keys loaded by environment variables in SpringBoot are converted to UpperCase
        final Map<String, String> normalizedBuildImages = new HashMap<>();
        buildImages.forEach((key, value) -> normalizedBuildImages.put(key.toUpperCase(Locale.ROOT), value));
        // Get all language names ordered by its length (needed to be sure that check of project type works)
        var allLanguageNames = Arrays.stream(ProgrammingLanguage.values()).map(Enum::name).sorted(Comparator.comparing(String::length).reversed()).toList();
        var allProjectTypes = Arrays.stream(ProjectType.values()).map(Enum::name).toList();

        // Check that all programming languages are present
        if (!allLanguageNames.stream().allMatch(normalizedBuildImages::containsKey)) {
            var missing = allLanguageNames.stream().filter(programmingLanguage -> !normalizedBuildImages.containsKey(programmingLanguage)).toList();
            throw new IllegalArgumentException("Not all Build Images for Programming Languages are set in configuration. Missing: " + missing);
        }
        // Check that all defined project types do exist. Schema: {Language}_{ProjectType}
        for (var definedKey : normalizedBuildImages.keySet()) {
            if (allLanguageNames.contains(definedKey)) {
                continue;
            }
            // Extract the longest key of a matching language
            var matchedLanguage = allLanguageNames.stream().filter(definedKey::startsWith).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("No matching language has been found for " + definedKey));
            var projectType = definedKey.substring(matchedLanguage.length() + 1);
            if (!allProjectTypes.contains(projectType)) {
                throw new IllegalArgumentException("Could not find ProjectType: " + projectType + " in Definition: " + definedKey);
            }
        }
        this.images = normalizedBuildImages;
    }

    /**
     * Get the docker image for a certain programming language with a certain project type.<br>
     * First, it looks up the default image of the {@link ProgrammingLanguage}. <br>
     * Second, it looks up specific overrides for the {@link ProjectType} in the configuration.
     *
     * @param programmingLanguage the programming language
     * @param projectType         the project type that will be used to find specific overrides
     * @return the docker image name
     */
    public String getImage(ProgrammingLanguage programmingLanguage, Optional<ProjectType> projectType) {
        var image = this.images.get(programmingLanguage.name());
        if (projectType.isPresent()) {
            var languageWithType = programmingLanguage.name() + "_" + projectType.get().name();
            if (images.containsKey(languageWithType)) {
                image = images.get(languageWithType);
            }
        }
        return image;
    }
}
