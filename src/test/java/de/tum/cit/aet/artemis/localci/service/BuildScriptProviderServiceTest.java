package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tum.cit.aet.artemis.programming.domain.ProjectType;

class BuildScriptProviderServiceTest {

    private final BuildScriptProviderService buildScriptProviderService = new BuildScriptProviderService();

    /**
     * The exemplary dependency (Maven/Gradle with dependency) only changes the exercise and solution repositories. The build plans of the plain project types have to be used,
     * otherwise such an exercise gets no build phases at all.
     */
    @ParameterizedTest
    @CsvSource({ "MAVEN_MAVEN,false,false,plain_maven.yaml", "MAVEN_MAVEN,true,false,plain_maven_static.yaml", "MAVEN_MAVEN,false,true,plain_maven_sequential.yaml",
            "GRADLE_GRADLE,false,false,plain_gradle.yaml", "GRADLE_GRADLE,true,false,plain_gradle_static.yaml", "GRADLE_GRADLE,false,true,plain_gradle_sequential.yaml",
            "PLAIN_MAVEN,false,false,plain_maven.yaml", "PLAIN_GRADLE,true,false,plain_gradle_static.yaml", "MAVEN_BLACKBOX,false,false,plain_maven_blackbox.yaml" })
    void testBuildTemplateName(ProjectType projectType, boolean staticAnalysis, boolean sequentialRuns, String expectedName) {
        assertThat(buildScriptProviderService.buildTemplateName(Optional.of(projectType), staticAnalysis, sequentialRuns, "yaml")).isEqualTo(expectedName);
    }

    @Test
    void testBuildTemplateNameWithoutProjectType() {
        assertThat(buildScriptProviderService.buildTemplateName(Optional.empty(), false, false, "yaml")).isEqualTo("default.yaml");
    }
}
