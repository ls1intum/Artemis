package de.tum.in.www1.artemis.config;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;

class ProgrammingLanguageConfigurationTest {

    private static final String OVERRIDDEN_IMAGE_NAME = "overridden_image";

    private Map<String, Map<String, String>> defaultConfig;

    @BeforeEach
    void setup() {
        defaultConfig = getDefaultImages();
    }

    @Test
    void testLanguageMissing() {
        defaultConfig.remove("swift");
        var config = new ProgrammingLanguageConfiguration();

        assertThatIllegalArgumentException().isThrownBy(() -> config.setImages(defaultConfig)).withMessageContaining("Missing: SWIFT");
    }

    @Test
    void testDefaultImageMissing() {
        defaultConfig.put("kotlin", Map.of());
        var config = new ProgrammingLanguageConfiguration();

        assertThatIllegalArgumentException().isThrownBy(() -> config.setImages(defaultConfig)).withMessageContaining("Missing: KOTLIN");
    }

    @Test
    void testUnknownProgrammingLanguageConfigured() {
        defaultConfig.put("whitespace", Map.of("default", OVERRIDDEN_IMAGE_NAME));
        var config = new ProgrammingLanguageConfiguration();

        assertThatIllegalArgumentException().isThrownBy(() -> config.setImages(defaultConfig)).withMessageContaining("Unknown programming language: whitespace");
    }

    @Test
    void testUnknownProjectTypeConfigured() {
        defaultConfig.get("java").put("sbt", OVERRIDDEN_IMAGE_NAME);
        var config = new ProgrammingLanguageConfiguration();

        assertThatIllegalArgumentException().isThrownBy(() -> config.setImages(defaultConfig)).withMessageContaining("Unknown project type for JAVA: sbt");
    }

    @Test
    void testDefaultImageConfigurationOnly() {
        var config = new ProgrammingLanguageConfiguration();
        config.setImages(defaultConfig);

        assertThat(config.getImage(ProgrammingLanguage.JAVA, Optional.of(ProjectType.PLAIN_MAVEN))).isEqualTo("JAVA");
    }

    @Test
    void testMultipleOverridesSameLanguage() {
        defaultConfig.put("c", Map.of("default", "default_image", "gcc", "gcc_image", "fact", "fact_image"));
        var config = new ProgrammingLanguageConfiguration();
        config.setImages(defaultConfig);

        assertThat(config.getImage(ProgrammingLanguage.C, Optional.of(ProjectType.GCC))).isEqualTo("gcc_image");
        assertThat(config.getImage(ProgrammingLanguage.C, Optional.of(ProjectType.FACT))).isEqualTo("fact_image");
    }

    @Test
    void testOverriddenImageConfigurationMaven() {
        defaultConfig.get("java").put("maven", OVERRIDDEN_IMAGE_NAME);
        testOverriddenImageConfiguration(ProgrammingLanguage.JAVA, List.of(ProjectType.MAVEN_MAVEN, ProjectType.PLAIN_MAVEN));
    }

    @Test
    void testOverriddenImageConfigurationGradle() {
        defaultConfig.get("java").put("gradle", OVERRIDDEN_IMAGE_NAME);
        testOverriddenImageConfiguration(ProgrammingLanguage.JAVA, List.of(ProjectType.GRADLE_GRADLE, ProjectType.PLAIN_GRADLE));
    }

    @Test
    void testOverriddenImageConfigurationSwift() {
        defaultConfig.get("swift").put("xcode", OVERRIDDEN_IMAGE_NAME);
        testOverriddenImageConfiguration(ProgrammingLanguage.SWIFT, List.of(ProjectType.XCODE));
    }

    @Test
    void testDockerRunFlags() {
        final var config = new ProgrammingLanguageConfiguration();
        config.setDefaultDockerFlags(getDockerFlags());

        final List<String> expectedFlags = List.of("-v", "\"$(pwd):/working_dir\"", "--cpus", "\"2\"", "--env", "\"VAR='value with spaces'\"", "--env", "\"SECOND_VAR=42\"");
        assertThat(config.getDefaultDockerFlags()).containsExactlyElementsOf(expectedFlags);
    }

    void testOverriddenImageConfiguration(final ProgrammingLanguage language, final List<ProjectType> overriddenProjectTypes) {
        var config = new ProgrammingLanguageConfiguration();
        config.setImages(defaultConfig);

        for (ProjectType projectType : overriddenProjectTypes) {
            assertThat(config.getImage(language, Optional.of(projectType))).isEqualTo(OVERRIDDEN_IMAGE_NAME);
        }

        final List<ProjectType> nonOverriddenProjectTypes = Arrays.stream(ProjectType.values()).filter(Predicate.not(overriddenProjectTypes::contains)).toList();
        for (ProjectType projectType : nonOverriddenProjectTypes) {
            assertThat(config.getImage(language, Optional.of(projectType))).isEqualTo(language.toString());
        }

        assertThat(config.getImage(language, Optional.empty())).isEqualTo(language.toString());
    }

    private Map<String, Map<String, String>> getDefaultImages() {
        final Map<String, Map<String, String>> images = new HashMap<>();

        for (ProgrammingLanguage language : ProgrammingLanguage.values()) {
            final Map<String, String> languageImages = new HashMap<>();
            languageImages.put("default", language.name());
            images.put(language.toString().toLowerCase(), languageImages);
        }

        return images;
    }

    private List<ProgrammingLanguageConfiguration.DockerFlag> getDockerFlags() {
        return List.of(new ProgrammingLanguageConfiguration.DockerFlag("-v", "$(pwd):/working_dir"), new ProgrammingLanguageConfiguration.DockerFlag("--cpus", "2"),
                new ProgrammingLanguageConfiguration.DockerFlag("--env", "VAR='value with spaces'"), new ProgrammingLanguageConfiguration.DockerFlag("--env", "SECOND_VAR=42"));
    }
}
