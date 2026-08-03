package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateBuildPlanConfigurationDTO;
import de.tum.cit.aet.artemis.programming.dto.UpdateProgrammingExerciseBuildConfigDTO;

class ProgrammingExerciseBuildConfigResourceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "buildconfigresource";

    private static final String DOCKER_IMAGE = "ghcr.io/ls1intum/artemis-maven-template:latest";

    // A valid Docker flags payload: allowed network, and resource limits within the accepted bounds. It has to pass the
    // same validation the full programming exercise update applies, which the build config editor now also enforces.
    private static final String DOCKER_FLAGS = "{\"network\":\"none\",\"cpuCount\":2,\"memory\":1024,\"memorySwap\":0}";

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        // the users are created with prefixed groups, so the course must enroll exactly those users, otherwise the
        // editor and instructor have no access to the exercise and every request is rejected with 403 before validation
        var course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    private String buildConfigEndpoint() {
        return "/api/programming/programming-exercises/" + programmingExercise.getId() + "/build-config";
    }

    private static BuildPhaseDTO phase(String name) {
        return new BuildPhaseDTO(name, "echo " + name, null, false, List.of());
    }

    private static BuildPhaseDTO afterDueDatePhase(String name) {
        // a phase that only runs after the due date has to produce results, otherwise it is not relevant for the rebuild
        return new BuildPhaseDTO(name, "echo " + name, BuildPhaseCondition.AFTER_DUE_DATE, false, List.of("results/*.xml"));
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testRejectsEmptyPhases() throws Exception {
        request.put(buildConfigEndpoint(), configurationWith(List.of(), 0), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testRejectsMalformedDockerFlags() throws Exception {
        assertDockerFlagsRejectedAndConfigUnchanged("this is not valid json");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testRejectsDisallowedDockerNetwork() throws Exception {
        assertDockerFlagsRejectedAndConfigUnchanged("{\"network\":\"host\",\"cpuCount\":2,\"memory\":1024}");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testRejectsInvalidDockerMemory() throws Exception {
        assertDockerFlagsRejectedAndConfigUnchanged("{\"network\":\"none\",\"cpuCount\":2,\"memory\":1}");
    }

    /**
     * Sends a build config update with the given (invalid) Docker flags and asserts that it is rejected with 400 and
     * that neither the Docker flags nor the build plan configuration of the stored build config were changed.
     */
    private void assertDockerFlagsRejectedAndConfigUnchanged(String invalidDockerFlags) throws Exception {
        var before = programmingExerciseBuildConfigRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        String originalDockerFlags = before.getDockerFlags();
        String originalBuildPlanConfiguration = before.getBuildPlanConfiguration();

        var dto = new UpdateBuildPlanConfigurationDTO(new BuildPlanPhasesDTO(List.of(phase("compile")), DOCKER_IMAGE), 60, invalidDockerFlags);
        request.put(buildConfigEndpoint(), dto, HttpStatus.BAD_REQUEST);

        var after = programmingExerciseBuildConfigRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        assertThat(after.getDockerFlags()).isEqualTo(originalDockerFlags);
        assertThat(after.getBuildPlanConfiguration()).isEqualTo(originalBuildPlanConfiguration);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testRejectsBlankDockerImage() throws Exception {
        var before = programmingExerciseBuildConfigRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        String originalBuildPlanConfiguration = before.getBuildPlanConfiguration();

        // a blank image is not null, so it would be persisted verbatim and leave the exercise with an unusable image
        var dto = new UpdateBuildPlanConfigurationDTO(new BuildPlanPhasesDTO(List.of(phase("compile")), "   "), 60, DOCKER_FLAGS);
        request.put(buildConfigEndpoint(), dto, HttpStatus.BAD_REQUEST);

        var after = programmingExerciseBuildConfigRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        assertThat(after.getBuildPlanConfiguration()).isEqualTo(originalBuildPlanConfiguration);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAcceptsNullDockerImageAsDefault() throws Exception {
        doNothing().when(programmingTriggerService).triggerTemplateAndSolutionBuild(anyLong());

        // a null image means the exercise default image is used, so the editor must accept it
        var dto = new UpdateBuildPlanConfigurationDTO(new BuildPlanPhasesDTO(List.of(phase("compile")), null), 120, DOCKER_FLAGS);
        var response = request.putWithResponseBody(buildConfigEndpoint(), dto, UpdateProgrammingExerciseBuildConfigDTO.class, HttpStatus.OK);

        assertThat(response.timeoutSeconds()).isEqualTo(120);
        var persisted = programmingExerciseBuildConfigRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        assertThat(persisted.getBuildPlanConfiguration()).contains("compile");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAddingAfterDueDatePhaseSchedulesTheRebuild() throws Exception {
        doNothing().when(programmingTriggerService).triggerTemplateAndSolutionBuild(anyLong());
        programmingExercise.setDueDate(ZonedDateTime.now().plusDays(1));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        programmingExerciseRepository.save(programmingExercise);

        request.put(buildConfigEndpoint(), configurationWith(List.of(phase("compile"), afterDueDatePhase("test")), 240), HttpStatus.OK);

        // adding a phase that runs after the due date has to schedule the rebuild, exactly as the full exercise update does
        var updated = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());
        assertThat(updated.getBuildAndTestStudentSubmissionsAfterDueDate()).isNotNull();
        assertThat(updated.getBuildAndTestStudentSubmissionsAfterDueDate()).isAfter(programmingExercise.getDueDate());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testRemovingAfterDueDatePhaseClearsTheRebuildDate() throws Exception {
        doNothing().when(programmingTriggerService).triggerTemplateAndSolutionBuild(anyLong());
        programmingExercise.setDueDate(ZonedDateTime.now().plusDays(1));
        programmingExerciseRepository.save(programmingExercise);
        request.put(buildConfigEndpoint(), configurationWith(List.of(phase("compile"), afterDueDatePhase("test")), 240), HttpStatus.OK);
        assertThat(programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId()).getBuildAndTestStudentSubmissionsAfterDueDate()).isNotNull();

        request.put(buildConfigEndpoint(), configurationWith(List.of(phase("compile"), phase("test")), 240), HttpStatus.OK);

        // without a phase that runs after the due date, the exercise must not keep a stale rebuild date
        var updated = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());
        assertThat(updated.getBuildAndTestStudentSubmissionsAfterDueDate()).isNull();
    }
}
