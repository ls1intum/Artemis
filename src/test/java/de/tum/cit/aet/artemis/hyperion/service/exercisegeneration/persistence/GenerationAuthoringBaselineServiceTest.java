package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTaskTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;

class GenerationAuthoringBaselineServiceTest {

    private GenerationAuthoringBaselineService service;
    private ProgrammingExercise exercise;
    private ProgrammingExerciseBuildConfig configuration;
    private ProgrammingExerciseTestCase testCase;
    private ProgrammingExerciseTask task;

    @BeforeEach
    void setUp() {
        var exercises = mock(ProgrammingExerciseTestRepository.class);
        var configurations = mock(ProgrammingExerciseBuildConfigRepository.class);
        var tests = mock(ProgrammingExerciseTestCaseTestRepository.class);
        var tasks = mock(ProgrammingExerciseTaskTestRepository.class);
        service = new GenerationAuthoringBaselineService(exercises, configurations, tests, tasks);
        exercise = new ProgrammingExercise();
        exercise.setId(1L);
        exercise.setTitle("Original");
        exercise.setProblemStatement("Implement the stack.");
        configuration = new ProgrammingExerciseBuildConfig();
        configuration.setBranch("main");
        testCase = new ProgrammingExerciseTestCase();
        testCase.setId(2L);
        testCase.setTestName("push");
        testCase.setWeight(1.0);
        testCase.setBonusMultiplier(1.0);
        testCase.setBonusPoints(0.0);
        testCase.setActive(true);
        testCase.setVisibility(Visibility.ALWAYS);
        task = new ProgrammingExerciseTask();
        task.setId(3L);
        task.setTaskName("Push");
        task.setTestCases(Set.of(testCase));
        when(exercises.findWithAllParticipationsById(1L)).thenReturn(java.util.Optional.of(exercise));
        when(configurations.getProgrammingExerciseBuildConfigElseThrow(1L)).thenReturn(configuration);
        when(tests.findByExerciseId(1L)).thenReturn(Set.of(testCase));
        when(tasks.findByExerciseIdWithTestCases(1L)).thenReturn(Set.of(task));
    }

    @Test
    void acceptsUnchangedDetachedSnapshot() {
        var baseline = service.capture(exercise);
        service.assertMatches(1L, baseline);
        assertThat(baseline.metadata()).containsEntry("getTitle", "Original");
        assertThatThrownBy(() -> baseline.metadata().put("getTitle", "Changed")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> baseline.tasks().getFirst().testIds().add(4L)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingEvidence() {
        assertThatThrownBy(() -> service.assertMatches(1L, null)).isInstanceOf(GenerationAuthoringBaselineService.AuthoringConflictException.class);
    }

    @ParameterizedTest
    @MethodSource("metadataEdits")
    void rejectsInstructorMetadataEdits(Consumer<ProgrammingExercise> edit) {
        var baseline = service.capture(exercise);
        edit.accept(exercise);
        assertThatThrownBy(() -> service.assertMatches(1L, baseline)).isInstanceOf(GenerationAuthoringBaselineService.AuthoringConflictException.class);
        assertThat(baseline.metadata()).containsEntry("getTitle", "Original");
    }

    static Stream<Consumer<ProgrammingExercise>> metadataEdits() {
        return Stream.of(e -> e.setTitle("Changed"), e -> e.setProblemStatement("Different task"), e -> e.setMaxPoints(12.0),
                e -> e.setDueDate(ZonedDateTime.parse("2026-10-15T12:00:00Z")), e -> e.setPackageName("org.changed"), e -> e.setAllowOnlineEditor(false));
    }

    @ParameterizedTest
    @MethodSource("configurationEdits")
    void rejectsInstructorBuildConfigurationEdits(Consumer<ProgrammingExerciseBuildConfig> edit) {
        var baseline = service.capture(exercise);
        edit.accept(configuration);
        assertThatThrownBy(() -> service.assertMatches(1L, baseline)).isInstanceOf(GenerationAuthoringBaselineService.AuthoringConflictException.class);
    }

    static Stream<Consumer<ProgrammingExerciseBuildConfig>> configurationEdits() {
        return Stream.of(c -> c.setBranch("other"), c -> c.setBuildPlanConfiguration("changed"), c -> c.setTimeoutSeconds(123), c -> c.setDockerFlags("--changed"),
                c -> c.setAssignmentCheckoutPath("other"), c -> c.setSequentialTestRuns(true), c -> c.setBranchRegex("changed"));
    }

    @ParameterizedTest
    @MethodSource("testCaseEdits")
    void rejectsCompleteTestCaseEdits(Consumer<ProgrammingExerciseTestCase> edit) {
        var baseline = service.capture(exercise);
        edit.accept(testCase);
        assertThatThrownBy(() -> service.assertMatches(1L, baseline)).isInstanceOf(GenerationAuthoringBaselineService.AuthoringConflictException.class);
    }

    static Stream<Consumer<ProgrammingExerciseTestCase>> testCaseEdits() {
        return Stream.of(t -> t.setTestName("renamed"), t -> t.setWeight(2.0), t -> t.setBonusMultiplier(2.0), t -> t.setBonusPoints(3.0),
                t -> t.setVisibility(Visibility.AFTER_DUE_DATE), t -> t.setActive(false), t -> t.setType(ProgrammingExerciseTestCaseType.STRUCTURAL));
    }

    @Test
    void rejectsTaskBindingAndNameChanges() {
        var baseline = service.capture(exercise);
        task.setTestCases(Set.of());
        assertThatThrownBy(() -> service.assertMatches(1L, baseline)).isInstanceOf(GenerationAuthoringBaselineService.AuthoringConflictException.class);
        task.setTestCases(Set.of(testCase));
        task.setTaskName("Different");
        assertThatThrownBy(() -> service.assertMatches(1L, baseline)).isInstanceOf(GenerationAuthoringBaselineService.AuthoringConflictException.class);
        assertThat(baseline.tasks().getFirst().testIds()).isEqualTo(List.of(2L));
    }
}
