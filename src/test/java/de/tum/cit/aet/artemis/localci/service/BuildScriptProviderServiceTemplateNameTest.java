package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

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
        assertThat(service.buildTemplateName(Optional.of(ProjectType.GRADLE_GRADLE), true, true, "sh")).isEqualTo("gradle_gradle_static_sequential.sh");
    }

    @Test
    void missingProjectTypeFallsBackToDefault() {
        assertThat(service.buildTemplateName(Optional.empty(), false, false, "yaml")).isEqualTo("default.yaml");
    }
}
