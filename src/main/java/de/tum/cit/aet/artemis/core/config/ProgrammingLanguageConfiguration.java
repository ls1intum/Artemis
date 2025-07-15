package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * This configuration file provides functions to get the configured Docker Images for {@link ProgrammingLanguage ProgrammingLanguages}.
 */
@ConfigurationProperties(prefix = "artemis.continuous-integration.build")
@Profile(PROFILE_CORE)
public class ProgrammingLanguageConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingLanguageConfiguration.class);

    private static final ProjectType DEFAULT_PROJECT_TYPE = ProjectType.PLAIN;

    private static final ProjectType MAVEN_PROJECT_TYPE = ProjectType.MAVEN_MAVEN;

    private static final ProjectType GRADLE_PROJECT_TYPE = ProjectType.GRADLE_GRADLE;

    private Map<ProgrammingLanguage, Map<ProjectType, String>> images = new EnumMap<>(ProgrammingLanguage.class);

    /**
     * Contains all the docker run arguments obtained from the spring properties
     */
    private List<DockerFlag> defaultDockerFlags;

    /**
     * Set the map of languages to build images.
     * (Method implicitly called by spring with the yaml configs as parameter)
     *
     * @param buildImages the map of languages to build images
     */
    public void setImages(final Map<String, Map<String, String>> buildImages) {
        final var languageSpecificBuildImages = loadImages(buildImages);
        checkImageForAllProgrammingLanguagesDefined(languageSpecificBuildImages);
        images = languageSpecificBuildImages;
        log.debug("Loaded Docker image configuration: {}", images);
    }

    /**
     * Returns a list of CLI parameters that should be passed to a {@code docker run} command.
     * <p>
     * Options and their value are two individual CLI parameters in this representation.
     * E.g., {@code --cpus 2 -p 8080:80} is represented as {@code [--cpus, "2", -p, "8080:80"]}.
     * All option values are quoted to prevent accidental splitting.
     *
     * @return The list of CLI parameters.
     */
    public List<String> getDefaultDockerFlags() {
        return defaultDockerFlags.stream().flatMap(this::buildDockerFlag).toList();
    }

    private Stream<String> buildDockerFlag(final DockerFlag dockerFlag) {
        // the flag value might contain spaces, quoting to prevent splitting
        return Stream.of(dockerFlag.flag(), "\"" + dockerFlag.value() + "\"");
    }

    /**
     * Sets the default docker run arguments based on the spring configuration
     * (Method implicitly called by spring with the yaml configs as parameter)
     *
     * @param dockerFlags key value pairs of run arguments
     */
    public void setDefaultDockerFlags(final List<DockerFlag> dockerFlags) {
        log.debug("Set Docker flags to {}", dockerFlags);
        this.defaultDockerFlags = dockerFlags;
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
        for (ProgrammingLanguage language : ProgrammingLanguage.getEnabledLanguages()) {
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
            return languageImages.get(configuredProjectType).trim();
        }
        else {
            return languageImages.get(DEFAULT_PROJECT_TYPE).trim();
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
            case MAVEN_BLACKBOX -> ProjectType.MAVEN_BLACKBOX;
        };
    }

    /**
     * A key-value pair of a CLI flag and its value for a {@code docker run} command.
     * <p>
     * E.g., the CLI option {@code --cpus 2} is represented as flag {@code --cpus} and value {@code 2}.
     *
     * @param flag  The option name.
     * @param value The option value.
     */
    public record DockerFlag(String flag, String value) {
    }
}
