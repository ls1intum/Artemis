package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * Unit tests for the template file name the build script is looked up under.
 */
class BuildScriptProviderServiceTemplateNameTest {

    private final BuildScriptProviderService service = new BuildScriptProviderService();

    @Test
    void blackboxProjectTypeUsesThePlainPrefix() {
        assertThat(service.buildTemplateName(Optional.of(ProjectType.MAVEN_BLACKBOX), false, false, "sh")).isEqualTo("plain_maven_blackbox.sh");
    }

    @Test
    void otherProjectTypeUsesItsOwnNameAndOptionSuffixes() {
        assertThat(service.buildTemplateName(Optional.of(ProjectType.PLAIN_GRADLE), true, true, "sh")).isEqualTo("plain_gradle_static_sequential.sh");
    }

    /**
     * The exemplary dependency (Maven/Gradle with dependency) only changes the exercise and solution repositories. The build plans of the plain project types have to be used,
     * otherwise such an exercise gets no build phases at all.
     */
    @ParameterizedTest
    @CsvSource({ "MAVEN_MAVEN,false,false,plain_maven.yaml", "MAVEN_MAVEN,true,false,plain_maven_static.yaml", "MAVEN_MAVEN,false,true,plain_maven_sequential.yaml",
            "GRADLE_GRADLE,false,false,plain_gradle.yaml", "GRADLE_GRADLE,true,false,plain_gradle_static.yaml", "GRADLE_GRADLE,false,true,plain_gradle_sequential.yaml" })
    void dependencyProjectTypesUseTheBuildPlansOfThePlainProjectTypes(ProjectType projectType, boolean staticAnalysis, boolean sequentialRuns, String expectedName) {
        assertThat(service.buildTemplateName(Optional.of(projectType), staticAnalysis, sequentialRuns, "yaml")).isEqualTo(expectedName);
    }

    @Test
    void missingProjectTypeFallsBackToDefault() {
        assertThat(service.buildTemplateName(Optional.empty(), false, false, "yaml")).isEqualTo("default.yaml");
    }
}
