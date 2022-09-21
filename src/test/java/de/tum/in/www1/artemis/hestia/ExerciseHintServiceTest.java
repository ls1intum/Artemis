package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintActivationRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.service.hestia.ExerciseHintService;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;

class ExerciseHintServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private ExerciseHintService exerciseHintService;

    @Autowired
    private ExerciseHintRepository exerciseHintRepository;

    @Autowired
    private ProgrammingExerciseRepository exerciseRepository;

    @Autowired
    private ProgrammingExerciseTaskService programmingExerciseTaskService;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    private ExerciseHintActivationRepository exerciseHintActivationRepository;

    @Autowired
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private ProgrammingExercise exercise;

    private List<ProgrammingExerciseTask> sortedTasks;

    private List<ExerciseHint> hints;

    private User student;

    private ProgrammingExerciseStudentParticipation studentParticipation;

    private int timeOffset = 0;

    @BeforeEach
    void initTestCase() {
        final Course course = database.addCourseWithOneProgrammingExerciseAndTestCases();
        final ProgrammingExercise programmingExercise = (ProgrammingExercise) course.getExercises().stream().findFirst().orElseThrow();

        database.addUsers(2, 2, 1, 2);

        student = userRepository.getUserWithGroupsAndAuthorities("student1");
        database.changeUser("student1");

        var activatedTestCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()).stream().peek(testCase -> testCase.setActive(true)).toList();
        programmingExerciseTestCaseRepository.saveAll(activatedTestCases);
        exercise = exerciseRepository.findByIdElseThrow(programmingExercise.getId());
        exercise = database.loadProgrammingExerciseWithEagerReferences(exercise);
        database.addHintsToExercise(exercise);
        database.addTasksToProgrammingExercise(exercise);

        sortedTasks = programmingExerciseTaskService.getSortedTasks(exercise);

        hints = new ArrayList<>(exerciseHintRepository.findByExerciseId(exercise.getId()));
        hints.get(0).setProgrammingExerciseTask(sortedTasks.get(0));
        hints.get(1).setProgrammingExerciseTask(sortedTasks.get(1));
        hints.get(2).setProgrammingExerciseTask(sortedTasks.get(2));
        exerciseHintRepository.saveAll(hints);

        studentParticipation = database.addStudentParticipationForProgrammingExercise(exercise, student.getLogin());
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
        exerciseHintRepository.deleteAll(hints);
    }

    @Test
    void testGetAvailableExerciseHintsTasksWithoutTestCases() {
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        for (ProgrammingExerciseTask sortedTask : sortedTasks) {
            sortedTask.getTestCases().clear();
            programmingExerciseTaskRepository.save(sortedTask);
        }
        exercise.setProblemStatement(exercise.getProblemStatement().replaceAll("\\([^()]+\\)", "()"));
        exerciseRepository.save(exercise);
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).isEmpty();
    }

    @Test
    void testGetAvailableExerciseHintsEmpty1() {
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).isEmpty();
    }

    @Test
    void testGetAvailableExerciseHintsEmpty2() {
        addResultWithFailedTestCases(exercise.getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).isEmpty();
    }

    @Test
    void testGetAvailableExerciseHintsEmpty3() {
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).isEmpty();
    }

    @Test
    void testGetAvailableExerciseHintsEmpty4() {
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        addResultWithFailedTestCases(sortedTasks.get(2).getTestCases());
        addResultWithFailedTestCases(sortedTasks.get(2).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).isEmpty();
    }

    @Test
    void testGetAvailableExerciseHints1() {
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hints.get(0));
    }

    @Test
    void testGetAvailableExerciseHints2() {
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hints.get(1));
    }

    @Test
    void testGetAvailableExerciseHints3() {
        addResultWithSuccessfulTestCases(sortedTasks.get(1).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(1).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(1).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hints.get(0));
    }

    @Test
    void testGetAvailableExerciseHints4() {
        addResultWithSuccessfulTestCases(sortedTasks.get(2).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(2).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(2).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hints.get(0));
    }

    @Test
    void testGetAvailableExerciseHints5() {
        addResultWithSuccessfulTestCases(sortedTasks.get(1).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(2).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(1).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hints.get(0));
    }

    @Test
    void testGetAvailableExerciseHintsWithZeroThreshold1() {
        hints.get(0).setDisplayThreshold((short) 0);
        exerciseHintRepository.save(hints.get(0));
        addResultWithFailedTestCases(exercise.getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hints.get(0));
    }

    @Test
    void testGetAvailableExerciseHintsWithZeroThreshold2() {
        hints.get(0).setDisplayThreshold((short) 0);
        exerciseHintRepository.save(hints.get(0));
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hints.get(0));
    }

    @Test
    void testGetAvailableExerciseHintsWithZeroThreshold3() {
        hints.get(0).setDisplayThreshold((short) 0);
        exerciseHintRepository.save(hints.get(0));
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hints.get(0));
    }

    @Test
    void testGetAvailableExerciseHints_skippedTestsConsideredAsNegative() {
        // create result with feedbacks with "null" for attribute "positive"
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        var results = resultRepository.findAllByExerciseId(exercise.getId());
        var optionalResult = resultRepository.findWithEagerSubmissionAndFeedbackAndAssessorById(results.get(0).getId());
        assertThat(optionalResult).isPresent();

        var result = optionalResult.get();
        result.getFeedbacks().forEach(feedback -> feedback.setPositive(null));
        resultRepository.save(result);

        // create results with feedbacks with "false" for attribute "positive"
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());

        var availableHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableHints).containsExactly(hints.get(0));
    }

    @Test
    void testActivateExerciseHint1() {
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());

        assertThat(exerciseHintService.activateHint(hints.get(0), student)).isTrue();
        assertThat(exerciseHintService.activateHint(hints.get(1), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(2), student)).isFalse();
        assertThat(exerciseHintActivationRepository.findAll()).hasSize(1).anyMatch(ueha -> ueha.getUser().equals(student) && ueha.getExerciseHint().equals(hints.get(0)));
    }

    @Test
    void testActivateExerciseHint2() {
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());

        assertThat(exerciseHintService.activateHint(hints.get(0), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(1), student)).isTrue();
        assertThat(exerciseHintService.activateHint(hints.get(2), student)).isFalse();
        assertThat(exerciseHintActivationRepository.findAll()).hasSize(1).anyMatch(ueha -> ueha.getUser().equals(student) && ueha.getExerciseHint().equals(hints.get(1)));
    }

    @Test
    void testActivateExerciseHint3() {
        addResultWithFailedTestCases(sortedTasks.get(2).getTestCases());
        addResultWithFailedTestCases(sortedTasks.get(2).getTestCases());
        addResultWithFailedTestCases(sortedTasks.get(2).getTestCases());

        assertThat(exerciseHintService.activateHint(hints.get(0), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(1), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(2), student)).isTrue();
        assertThat(exerciseHintActivationRepository.findAll()).hasSize(1).anyMatch(ueha -> ueha.getUser().equals(student) && ueha.getExerciseHint().equals(hints.get(2)));
    }

    @Test
    void testActivateExerciseHint4() {
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        addResultWithSuccessfulTestCases(exercise.getTestCases());

        assertThat(exerciseHintService.activateHint(hints.get(0), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(1), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(2), student)).isFalse();
        assertThat(exerciseHintActivationRepository.findAll()).isEmpty();
    }

    @Test
    void testActivateExerciseHintTwiceFails() {
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());

        assertThat(exerciseHintService.activateHint(hints.get(0), student)).isTrue();
        assertThat(exerciseHintService.activateHint(hints.get(0), student)).isFalse();
        assertThat(exerciseHintActivationRepository.findAll()).hasSize(1).anyMatch(ueha -> ueha.getUser().equals(student) && ueha.getExerciseHint().equals(hints.get(0)));
    }

    private void addResultWithFailedTestCases(Collection<ProgrammingExerciseTestCase> failedTestCases) {
        var successfulTestCases = new ArrayList<>(exercise.getTestCases());
        successfulTestCases.removeAll(failedTestCases);
        addResultWithSuccessfulTestCases(successfulTestCases);
    }

    private void addResultWithSuccessfulTestCases(Collection<ProgrammingExerciseTestCase> successfulTestCases) {
        var submission = database.createProgrammingSubmission(studentParticipation, false);
        Result result = new Result().participation(submission.getParticipation()).assessmentType(AssessmentType.AUTOMATIC).score(0D).rated(true)
                .completionDate(ZonedDateTime.now().plusSeconds(timeOffset++));
        result = resultRepository.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        programmingSubmissionRepository.save(submission);

        for (ProgrammingExerciseTestCase testCase : exercise.getTestCases()) {
            var feedback = new Feedback();
            feedback.setPositive(successfulTestCases.contains(testCase));
            feedback.setText(testCase.getTestName());
            feedback.setVisibility(Visibility.ALWAYS);
            feedback.setType(FeedbackType.AUTOMATIC);
            database.addFeedbackToResult(feedback, result);
        }
    }
}
