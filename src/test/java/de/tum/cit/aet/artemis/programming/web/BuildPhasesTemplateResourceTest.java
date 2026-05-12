package de.tum.cit.aet.artemis.programming.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class BuildPhasesTemplateResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "buildphasesintegration";

    private static final String C_DOCKER_IMAGE = "ls1tum/artemis-c-minimal-docker:1.0.0";

    private static final String FACT_DOCKER_IMAGE = "ls1tum/artemis-fact-minimal-docker:1.1.0";

    @Autowired
    private UserUtilService userUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
    }

    private record TestProvider(String templateKey, int expectedPhases) {
    }

    private static Stream<Arguments> templateProvider() {
        // @formatter:off
        return Stream.of(
            new TestProvider("JAVA/PLAIN_GRADLE", 1),
            new TestProvider("JAVA/PLAIN_GRADLE?sequentialRuns=true", 2),
            new TestProvider("JAVA/PLAIN_GRADLE?staticAnalysis=true", 2),
            new TestProvider("JAVA/PLAIN_MAVEN", 1),
            new TestProvider("JAVA/PLAIN_MAVEN?sequentialRuns=true", 2),
            new TestProvider("JAVA/PLAIN_MAVEN?staticAnalysis=true", 2),
            new TestProvider("JAVA/MAVEN_BLACKBOX", 7),
            new TestProvider("JAVA/MAVEN_BLACKBOX?staticAnalysis=true", 8),
            new TestProvider("ASSEMBLER", 4),
            new TestProvider("C/FACT", 2),
            new TestProvider("C/GCC", 3),
            new TestProvider("C/GCC?staticAnalysis=true", 3),
            new TestProvider("KOTLIN", 1),
            new TestProvider("KOTLIN?sequentialRuns=true", 3),
            new TestProvider("VHDL", 4),
            new TestProvider("HASKELL", 1),
            new TestProvider("HASKELL?sequentialRuns=true", 2),
            new TestProvider("OCAML", 2),
            new TestProvider("SWIFT/PLAIN", 1),
            new TestProvider("SWIFT/PLAIN?staticAnalysis=true", 2)
        ).map(provider -> Arguments.of(provider.templateKey(), provider.expectedPhases()));
        // @formatter:on
    }

    @ParameterizedTest
    @MethodSource("templateProvider")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBuildPhasesTemplateFile(String templateKey, int expectedPhases) throws Exception {
        BuildPlanPhasesDTO buildPlanPhases = request.get("/api/programming/phases/templates/" + templateKey, HttpStatus.OK, BuildPlanPhasesDTO.class);
        assertThat(buildPlanPhases).isNotNull();
        assertBuildPlanPhasesAreCorrect(buildPlanPhases, expectedPhases);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetNonExistingBuildPhasesTemplateFile() throws Exception {
        request.get("/api/programming/phases/templates/JAVA/PLAIN_GRADLE?staticAnalysis=true&sequentialRuns=true", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFactTemplateUsesConfiguredDockerImage() throws Exception {
        BuildPlanPhasesDTO buildPlanPhases = request.get("/api/programming/phases/templates/C/FACT", HttpStatus.OK, BuildPlanPhasesDTO.class);
        assertThat(buildPlanPhases).isNotNull();
        assertThat(buildPlanPhases.dockerImage()).isEqualTo(FACT_DOCKER_IMAGE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGccTemplateUsesConfiguredDockerImage() throws Exception {
        BuildPlanPhasesDTO buildPlanPhases = request.get("/api/programming/phases/templates/C/GCC", HttpStatus.OK, BuildPlanPhasesDTO.class);
        assertThat(buildPlanPhases).isNotNull();
        assertThat(buildPlanPhases.dockerImage()).isEqualTo(C_DOCKER_IMAGE);
    }

    void assertBuildPlanPhasesAreCorrect(BuildPlanPhasesDTO buildPlanPhases, int expectedPhases) {
        assertThat(buildPlanPhases.dockerImage()).isNotNull();
        assertThat(buildPlanPhases.phases()).hasSize(expectedPhases);
        for (var phase : buildPlanPhases.phases()) {
            assertThat(phase.name()).isNotBlank();
            assertThat(phase.script()).isNotBlank();
        }
    }
}
