package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

class ProgrammingLanguageConfigurationTest {

    private static final String OVERRIDDEN_IMAGE_NAME = "overridden_image";

    private static final String C_DOCKER_IMAGE = "ls1tum/artemis-c-minimal-docker:1.0.0";

    private static final String FACT_DOCKER_IMAGE = "ls1tum/artemis-fact-minimal-docker:1.1.0";

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
    void testProductionConfigContainsFactImageOverride() throws IOException {
        var config = new ProgrammingLanguageConfiguration();
        config.setImages(readBuildImagesFromMainApplicationConfig());

        assertThat(config.getImage(ProgrammingLanguage.C, Optional.of(ProjectType.FACT))).isEqualTo(FACT_DOCKER_IMAGE);
    }

    @Test
    void testProductionConfigContainsDefaultCImage() throws IOException {
        var config = new ProgrammingLanguageConfiguration();
        config.setImages(readBuildImagesFromMainApplicationConfig());

        assertThat(config.getImage(ProgrammingLanguage.C, Optional.empty())).isEqualTo(C_DOCKER_IMAGE);
        assertThat(config.getImage(ProgrammingLanguage.C, Optional.of(ProjectType.GCC))).isEqualTo(C_DOCKER_IMAGE);
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

        for (ProgrammingLanguage language : ProgrammingLanguage.getEnabledLanguages()) {
            final Map<String, String> languageImages = new HashMap<>();
            languageImages.put("default", language.name());
            images.put(language.toString().toLowerCase(), languageImages);
        }

        return images;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> readBuildImagesFromMainApplicationConfig() throws IOException {
        var mainApplicationConfig = Collections.list(ProgrammingLanguageConfigurationTest.class.getClassLoader().getResources("config/application.yml")).stream()
                .filter(resource -> resource.toString().contains("/build/resources/main/config/application.yml")).findFirst()
                .orElseThrow(() -> new IOException("Could not find main config/application.yml on the classpath"));

        try (var inputStream = mainApplicationConfig.openStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(inputStream);
            Map<String, Object> artemis = (Map<String, Object>) root.get("artemis");
            Map<String, Object> continuousIntegration = (Map<String, Object>) artemis.get("continuous-integration");
            Map<String, Object> build = (Map<String, Object>) continuousIntegration.get("build");
            return (Map<String, Map<String, String>>) build.get("images");
        }
    }

    private List<ProgrammingLanguageConfiguration.DockerFlag> getDockerFlags() {
        return List.of(new ProgrammingLanguageConfiguration.DockerFlag("-v", "$(pwd):/working_dir"), new ProgrammingLanguageConfiguration.DockerFlag("--cpus", "2"),
                new ProgrammingLanguageConfiguration.DockerFlag("--env", "VAR='value with spaces'"), new ProgrammingLanguageConfiguration.DockerFlag("--env", "SECOND_VAR=42"));
    }
}
