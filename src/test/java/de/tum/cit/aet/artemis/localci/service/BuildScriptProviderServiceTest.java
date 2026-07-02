package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * Pure unit coverage for {@link BuildScriptProviderService#buildTemplateName}, locking in the project-type → build-template-family mapping.
 * <p>
 * The crucial invariant: every Java project type the platform can grade must resolve to a phases template that actually exists on the classpath. Before the mapping was added,
 * {@link ProjectType#MAVEN_MAVEN} and {@link ProjectType#GRADLE_GRADLE} resolved to a non-existent {@code maven_maven.yaml} / {@code gradle_gradle.yaml}, so
 * {@code BuildPhasesTemplateService} returned {@code null} phases, the generated build script ran no tests, and zero test cases were synchronised. This test fails loudly if that
 * regresses.
 */
class BuildScriptProviderServiceTest {

    private final BuildScriptProviderService buildScriptProviderService = new BuildScriptProviderService();

    @Test
    void mavenProjectTypesShareThePlainMavenPhasesFamily() {
        assertThat(buildScriptProviderService.buildTemplateName(Optional.of(ProjectType.PLAIN_MAVEN), false, false, "yaml")).isEqualTo("plain_maven.yaml");
        // MAVEN_MAVEN and MAVEN_BLACKBOX differ from PLAIN_MAVEN only in repository layout; the Maven build command is identical, so they reuse the plain_maven family.
        assertThat(buildScriptProviderService.buildTemplateName(Optional.of(ProjectType.MAVEN_MAVEN), false, false, "yaml")).isEqualTo("plain_maven.yaml");
        assertThat(buildScriptProviderService.buildTemplateName(Optional.of(ProjectType.MAVEN_BLACKBOX), false, false, "yaml")).isEqualTo("plain_maven_blackbox.yaml");
    }

    @Test
    void gradleProjectTypesShareThePlainGradlePhasesFamily() {
        assertThat(buildScriptProviderService.buildTemplateName(Optional.of(ProjectType.PLAIN_GRADLE), false, false, "yaml")).isEqualTo("plain_gradle.yaml");
        // GRADLE_GRADLE differs from PLAIN_GRADLE only in repository layout; the Gradle build command is identical, so it reuses the plain_gradle family.
        assertThat(buildScriptProviderService.buildTemplateName(Optional.of(ProjectType.GRADLE_GRADLE), false, false, "yaml")).isEqualTo("plain_gradle.yaml");
    }

    @Test
    void staticAndSequentialFlagsAreAppendedToTheResolvedFamily() {
        assertThat(buildScriptProviderService.buildTemplateName(Optional.of(ProjectType.MAVEN_MAVEN), true, false, "yaml")).isEqualTo("plain_maven_static.yaml");
        assertThat(buildScriptProviderService.buildTemplateName(Optional.of(ProjectType.GRADLE_GRADLE), false, true, "yaml")).isEqualTo("plain_gradle_sequential.yaml");
    }

    @Test
    void anAbsentProjectTypeResolvesToTheDefaultFamily() {
        assertThat(buildScriptProviderService.buildTemplateName(Optional.empty(), false, false, "yaml")).isEqualTo("default.yaml");
    }

    /**
     * Every Java project type that can be graded must resolve to a phases template that genuinely exists on the classpath. This is the regression guard for the missing-phases bug:
     * a project type whose resolved name has no backing {@code .yaml} produces an empty build script that synchronises no test cases.
     */
    @Test
    void everyGradableJavaProjectTypeResolvesToAnExistingPhasesTemplate() {
        for (ProjectType projectType : new ProjectType[] { ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_MAVEN, ProjectType.MAVEN_BLACKBOX, ProjectType.PLAIN_GRADLE,
                ProjectType.GRADLE_GRADLE }) {
            String templateFileName = buildScriptProviderService.buildTemplateName(Optional.of(projectType), false, false, "yaml");
            ClassPathResource resource = new ClassPathResource("templates/phases/java/" + templateFileName);
            assertThat(resource.exists()).as("phases template templates/phases/java/%s must exist for project type %s", templateFileName, projectType).isTrue();
        }
    }
}
