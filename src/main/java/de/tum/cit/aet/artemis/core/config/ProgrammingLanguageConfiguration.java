package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * This configuration file provides functions to get the configured Docker Images for {@link ProgrammingLanguage ProgrammingLanguages}.
 */
@ConfigurationProperties(prefix = "artemis.continuous-integration.build")
@Profile(PROFILE_CORE)
public class ProgrammingLanguageConfiguration {

    public record ConfigurationEntry(Map<ProjectType, String> images, CheckoutPaths checkout_paths) {
    }

    public record CheckoutPaths(String assignment, String test, String solution) {
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

    private static final Logger log = LoggerFactory.getLogger(ProgrammingLanguageConfiguration.class);

    private static final ProjectType DEFAULT_PROJECT_TYPE = ProjectType.PLAIN;

    private static final ProjectType MAVEN_PROJECT_TYPE = ProjectType.MAVEN_MAVEN;

    private static final ProjectType GRADLE_PROJECT_TYPE = ProjectType.GRADLE_GRADLE;

    /**
     * Contains all the language specific configuration
     */
    private final Map<ProgrammingLanguage, ConfigurationEntry> configurationEntries;

    /**
     * Contains all the docker run arguments obtained from the spring properties
     */
    private final List<DockerFlag> defaultDockerFlags;

    /**
     * Sets the default docker run arguments based on the spring configuration
     * Set the map of languages to build images.
     * (Constructor implicitly called by spring with the yaml configs as parameter)
     *
     * @param defaultDockerFlags key value pairs of run arguments
     * @param images             legacy map of languages to build images
     */
    public ProgrammingLanguageConfiguration(final List<DockerFlag> defaultDockerFlags, final Map<ProgrammingLanguage, ConfigurationEntry> programmingLanguages,
            final Map<ProgrammingLanguage, Map<ProjectType, String>> images) {
        this.defaultDockerFlags = Objects.requireNonNullElseGet(defaultDockerFlags, List::of);
        log.debug("Set Docker flags to {}", defaultDockerFlags);

        this.configurationEntries = Objects.requireNonNullElseGet(programmingLanguages, HashMap::new);
        addImages(images);
        checkImageForAllProgrammingLanguagesDefined();
        log.debug("Loaded programming language configuration: {}", this.configurationEntries);
    }

    /**
     * Set the map of languages to build images.
     * (Method implicitly called by spring with the yaml configs as parameter)
     *
     * @param buildImages the map of languages to build images
     */
    private void addImages(final Map<ProgrammingLanguage, Map<ProjectType, String>> buildImages) {
        // checkImageForAllProgrammingLanguagesDefined(languageSpecificBuildImages);

        for (var entry : buildImages.entrySet()) {
            ConfigurationEntry configurationEntry = configurationEntries.get(entry.getKey());
            if (configurationEntry != null) {
                configurationEntry.images().putAll(entry.getValue());
            }
            else {
                configurationEntries.put(entry.getKey(), new ConfigurationEntry(entry.getValue(), null));
            }
        }
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
     * Makes sure that for each programming language at least a default image is defined.
     * <p>
     * Aborts server startup by throwing an exception if some definitions are missing.
     */
    private void checkImageForAllProgrammingLanguagesDefined() {
        for (ProgrammingLanguage language : ProgrammingLanguage.getEnabledLanguages()) {
            if (!configurationEntries.containsKey(language)) {
                throw new IllegalArgumentException("For each programming language a CI build image must be defined. Missing: " + language);
            }

            final ConfigurationEntry configurationEntry = configurationEntries.get(language);
            if (!configurationEntry.images().containsKey(DEFAULT_PROJECT_TYPE)) {
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
        final Map<ProjectType, String> languageImages = configurationEntries.get(programmingLanguage).images();
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

    @Component
    @ConfigurationPropertiesBinding
    public static class ProjectTypeConverter implements Converter<String, ProjectType> {

        /**
         * Tries to parse the given project type string.
         * <p>
         * Ignores the casing of the input string.
         * <p>
         * In addition to the representation of values as obtained by calling
         * {@link ProjectType#toString()}, a few shorthand aliases can be parsed:
         * <ul>
         * <li>{@code default} for {@link ProjectType#PLAIN}</li>
         * <li>{@code maven} for {@link ProjectType#MAVEN_MAVEN}</li>
         * <li>{@code gradle} for {@link ProjectType#GRADLE_GRADLE}</li>
         * </ul>
         *
         * @param projectType The project type string that should be parsed.
         * @return The project type represented by the given input.
         * @throws IllegalArgumentException Thrown if the input cannot be parsed as a project type.
         */
        @Override
        public ProjectType convert(String projectType) {
            final String input = projectType.toUpperCase(Locale.ROOT);

            try {
                return ProjectType.valueOf(input);
            }
            catch (IllegalArgumentException ex) {
                return switch (input) {
                    case "DEFAULT" -> ProjectType.PLAIN;
                    case "MAVEN" -> ProjectType.MAVEN_MAVEN;
                    case "GRADLE" -> ProjectType.GRADLE_GRADLE;
                    default -> throw new IllegalArgumentException("Unknown project type: " + projectType);
                };
            }
        }
    }

    @Component
    @ConfigurationPropertiesBinding
    public static class ProgrammingLanguageConverter implements Converter<String, ProgrammingLanguage> {

        /**
         * Tries to parse the given programming language.
         * <p>
         * Aborts server startup by throwing an exception if the language cannot be parsed.
         *
         * @param language The name of the language that should be parsed.
         * @return The given language, now parsed.
         */
        @Override
        public ProgrammingLanguage convert(String language) {
            try {
                return ProgrammingLanguage.valueOf(language.toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unknown programming language: " + language, ex);
            }
        }
    }
}
