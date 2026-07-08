package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateBuildPlanConfigurationDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseBuildConfigDTO;

class ProgrammingExerciseBuildConfigResourceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "buildconfigresource";

    private static final String DOCKER_IMAGE = "ghcr.io/ls1intum/artemis-maven-template:latest";

    private static final String DOCKER_FLAGS = "{\"network\":\"none\",\"cpuCount\":2}";

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    private String buildConfigEndpoint() {
        return "/api/programming/programming-exercises/" + programmingExercise.getId() + "/build-config";
    }

    private static BuildPhaseDTO phase(String name) {
        return new BuildPhaseDTO(name, "echo " + name, null, false, List.of());
    }

    private static UpdateBuildPlanConfigurationDTO configurationWith(List<BuildPhaseDTO> phases, int timeoutSeconds) {
        return new UpdateBuildPlanConfigurationDTO(new BuildPlanPhasesDTO(phases, DOCKER_IMAGE), timeoutSeconds, DOCKER_FLAGS);
    }

    private void assertConfigurationPersisted() throws Exception {
        // The real trigger performs Git operations on the template/solution repositories, which is out of scope here and
        // covered by dedicated build-trigger tests; we only assert that saving in the editor triggers a rebuild.
        doNothing().when(programmingTriggerService).triggerTemplateAndSolutionBuild(anyLong());

        var dto = configurationWith(List.of(phase("compile"), phase("test")), 240);

        var response = request.putWithResponseBody(buildConfigEndpoint(), dto, UpdateProgrammingExerciseBuildConfigDTO.class, HttpStatus.OK);

        assertThat(response.timeoutSeconds()).isEqualTo(240);
        assertThat(response.buildPlanConfiguration()).contains("compile").contains("test").contains(DOCKER_IMAGE);
        assertThat(response.dockerFlags()).isEqualTo(DOCKER_FLAGS);

        var persisted = programmingExerciseBuildConfigRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        assertThat(persisted.getTimeoutSeconds()).isEqualTo(240);
        assertThat(persisted.getBuildPlanConfiguration()).contains("compile").contains("test").contains(DOCKER_IMAGE);
        assertThat(persisted.getDockerFlags()).isEqualTo(DOCKER_FLAGS);
        // the structured phases configuration supersedes any legacy build script
        assertThat(persisted.getBuildScript()).isNull();

        // analogous to the build plan editor for external CI systems, the template and solution build is triggered
        verify(programmingTriggerService).triggerTemplateAndSolutionBuild(programmingExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdateBuildConfigAsEditor() throws Exception {
        assertConfigurationPersisted();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateBuildConfigAsInstructor() throws Exception {
        assertConfigurationPersisted();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateBuildConfigForbiddenForStudent() throws Exception {
        request.put(buildConfigEndpoint(), configurationWith(List.of(phase("compile")), 0), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateBuildConfigForbiddenForTutor() throws Exception {
        request.put(buildConfigEndpoint(), configurationWith(List.of(phase("compile")), 0), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testRejectsDuplicatePhaseNames() throws Exception {
        request.put(buildConfigEndpoint(), configurationWith(List.of(phase("compile"), phase("compile")), 0), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testRejectsReservedPhaseName() throws Exception {
        request.put(buildConfigEndpoint(), configurationWith(List.of(phase("main")), 0), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testRejectsBlankPhaseName() throws Exception {
        request.put(buildConfigEndpoint(), configurationWith(List.of(phase("")), 0), HttpStatus.BAD_REQUEST);
    }
}
