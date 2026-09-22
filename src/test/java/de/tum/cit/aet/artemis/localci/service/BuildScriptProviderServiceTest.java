package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.tum.cit.aet.artemis.programming.domain.ProjectType;

class BuildScriptProviderServiceTest {

    @ParameterizedTest
    @CsvSource({ "MAVEN_MAVEN,plain_maven", "GRADLE_GRADLE,plain_gradle", "PLAIN_MAVEN,plain_maven", "PLAIN_GRADLE,plain_gradle", "MAVEN_BLACKBOX,plain_maven_blackbox" })
    void assignmentWrapperDoesNotChangeTheTestRepositoryBuildTemplate(ProjectType projectType, String template) {
        var service = new BuildScriptProviderService();
        assertThat(service.buildTemplateName(Optional.of(projectType), false, false, "sh")).isEqualTo(template + ".sh");
        assertThat(service.buildTemplateName(Optional.of(projectType), true, true, "yaml")).isEqualTo(template + "_static_sequential.yaml");
    }
}
