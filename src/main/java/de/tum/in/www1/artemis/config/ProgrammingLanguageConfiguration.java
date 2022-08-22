package de.tum.in.www1.artemis.config;

import java.util.*;

import org.springframework.boot.context.properties.ConfigurationProperties;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;

/**
 * This configuration file provides functions to get the configured Docker Images for {@link ProgrammingLanguage ProgrammingLanguages}.
 */
@ConfigurationProperties(prefix = "artemis.continuous-integration.build")
public class ProgrammingLanguageConfiguration {

    private static final ProjectType DEFAULT_PROJECT_TYPE = ProjectType.PLAIN;

    private static final ProjectType MAVEN_PROJECT_TYPE = ProjectType.MAVEN_MAVEN;

    private static final ProjectType GRADLE_PROJECT_TYPE = ProjectType.GRADLE_GRADLE;

    private Map<ProgrammingLanguage, Map<ProjectType, String>> images = new EnumMap<>(ProgrammingLanguage.class);

    /**
     * Set the map of languages to build images.
     *
     * @param buildImages the map of languages to build images
     */
    public void setImages(final Map<String, Map<String, String>> buildImages) {
        final var languageSpecificBuildImages = loadImages(buildImages);
        checkImageForAllProgrammingLanguagesDefined(languageSpecificBuildImages);
        images = languageSpecificBuildImages;
    }

    /**
     * Parses the configuration contents into a properly typed map.
     *
     * @param imageConfig the config as it was received on server startup.
     * @return A map of programming languages to the CI build images that are defined for it.
     */
    private Map<ProgrammingLanguage, Map<ProjectType, String>> loadImages(final Map<String, Map<String, String>> imageConfig) {
        final Map<ProgrammingLanguage, Map<ProjectType, String>> buildImages = new EnumMap<>(ProgrammingLanguage.class);

        for (var entry : imageConfig.entrySet()) {
            final ProgrammingLanguage programmingLanguage = tryParseProgrammingLanguage(entry.getKey());
            final Map<ProjectType, String> projectTypeImages = tryParseProjectTypeBuildImages(programmingLanguage, entry.getValue());

            buildImages.put(programmingLanguage, projectTypeImages);
        }

        return buildImages;
    }

    /**
     * Tries to parse the given programming language.
     * <p>
     * Aborts server startup by throwing an exception if the language cannot be parsed.
     *
     * @param language The name of the language that should be parsed.
     * @return The given language, now parsed.
     */
    private ProgrammingLanguage tryParseProgrammingLanguage(final String language) {
        try {
            return ProgrammingLanguage.valueOf(language.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown programming language: " + language, ex);
        }
    }

    /**
     * Tries to parse key-value pairs of project types and their build images.
     * <p>
     * Aborts server startup by throwing an exception if the project type cannot be parsed.
     *
     * @param buildImageConfig A map of project types to CI build images.
     * @return A map of project types to CI build images.
     */
    private Map<ProjectType, String> tryParseProjectTypeBuildImages(final ProgrammingLanguage language, final Map<String, String> buildImageConfig) {
        final Map<ProjectType, String> projectTypeImages = new EnumMap<>(ProjectType.class);

        for (var entry : buildImageConfig.entrySet()) {
            final ProjectType projectType = tryParseProjectType(language, entry.getKey());
            final String image = entry.getValue();

            projectTypeImages.put(projectType, image);
        }

        return projectTypeImages;
    }

    private ProjectType tryParseProjectType(final ProgrammingLanguage language, final String projectType) {
        try {
            return ProjectType.tryFromString(projectType);
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown project type for " + language + ": " + projectType);
        }
    }

    /**
     * Makes sure that for each programming language at least a default image is defined.
     * <p>
     * Aborts server startup by throwing an exception if some definitions are missing.
     *
     * @param buildImages The build images as defined in the configuration.
     */
    private void checkImageForAllProgrammingLanguagesDefined(final Map<ProgrammingLanguage, Map<ProjectType, String>> buildImages) {
        for (ProgrammingLanguage language : ProgrammingLanguage.values()) {
            if (!buildImages.containsKey(language)) {
                throw new IllegalArgumentException("For each programming language a CI build image must be defined. Missing: " + language);
            }

            final Map<ProjectType, String> languageImages = buildImages.get(language);
            if (!languageImages.containsKey(DEFAULT_PROJECT_TYPE)) {
                throw new IllegalArgumentException("For each programming language a default image must be defined. Missing: " + language);
            }
        }
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
        final Map<ProjectType, String> languageImages = images.get(programmingLanguage);
        final ProjectType configuredProjectType = projectType.map(this::getConfiguredProjectType).orElse(DEFAULT_PROJECT_TYPE);

        if (languageImages.containsKey(configuredProjectType)) {
            return languageImages.get(configuredProjectType);
        }
        else {
            return languageImages.get(DEFAULT_PROJECT_TYPE);
        }
    }

    private ProjectType getConfiguredProjectType(final ProjectType actualProjectType) {
        return switch (actualProjectType) {
            case MAVEN_MAVEN, PLAIN_MAVEN -> MAVEN_PROJECT_TYPE;
            case PLAIN_GRADLE, GRADLE_GRADLE -> GRADLE_PROJECT_TYPE;
            case PLAIN -> ProjectType.PLAIN;
            case XCODE -> ProjectType.XCODE;
            case FACT -> ProjectType.FACT;
            case GCC -> ProjectType.GCC;
        };
    }
}
