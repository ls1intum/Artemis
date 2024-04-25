package de.tum.in.www1.artemis.hestia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHint;
import de.tum.in.www1.artemis.domain.hestia.ExerciseHintActivation;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintActivationRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.service.hestia.ExerciseHintService;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;
import de.tum.in.www1.artemis.user.UserUtilService;

class ExerciseHintServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exercisehintservice";

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
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private ExerciseHintActivationRepository exerciseHintActivationRepository;

    @Autowired
    private ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    @Autowired
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private ProgrammingExercise exercise;

    private List<ProgrammingExerciseTask> sortedTasks;

    private List<ExerciseHint> hints;

    private ExerciseHint exerciseHint;

    private User student;

    private ProgrammingExerciseStudentParticipation studentParticipation;

    private int timeOffset = 0;

    @BeforeEach
    void initTestCase() {
        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        final ProgrammingExercise programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        userUtilService.addUsers(TEST_PREFIX, 2, 2, 1, 2);

        student = userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1");
        userUtilService.changeUser(TEST_PREFIX + "student1");

        var activatedTestCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()).stream().peek(testCase -> testCase.setActive(true)).toList();
        programmingExerciseTestCaseRepository.saveAll(activatedTestCases);
        exercise = exerciseRepository.findByIdElseThrow(programmingExercise.getId());
        exercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(exercise);
        programmingExerciseUtilService.addHintsToExercise(exercise);
        programmingExerciseUtilService.addTasksToProgrammingExercise(exercise);

        sortedTasks = programmingExerciseTaskService.getSortedTasks(exercise);

        hints = new ArrayList<>(exerciseHintRepository.findByExerciseId(exercise.getId()));
        exerciseHint = hints.get(0);
        exerciseHint.setProgrammingExerciseTask(sortedTasks.get(0));
        hints.get(1).setProgrammingExerciseTask(sortedTasks.get(1));
        hints.get(2).setProgrammingExerciseTask(sortedTasks.get(2));
        exerciseHintRepository.saveAll(hints);

        studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, student.getLogin());
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
        assertThat(availableExerciseHints).containsExactly(exerciseHint);
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
        assertThat(availableExerciseHints).containsExactly(exerciseHint);
    }

    @Test
    void testGetAvailableExerciseHints4() {
        addResultWithSuccessfulTestCases(sortedTasks.get(2).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(2).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(2).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(exerciseHint);
    }

    @Test
    void testGetAvailableExerciseHints5() {
        addResultWithSuccessfulTestCases(sortedTasks.get(1).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(2).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(1).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(exerciseHint);
    }

    @Test
    void testGetAvailableExerciseHintsWithZeroThreshold1() {
        exerciseHint.setDisplayThreshold((short) 0);
        exerciseHintRepository.save(exerciseHint);
        addResultWithFailedTestCases(exercise.getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(exerciseHint);
    }

    @Test
    void testGetAvailableExerciseHintsWithZeroThreshold2() {
        exerciseHint.setDisplayThreshold((short) 0);
        exerciseHintRepository.save(exerciseHint);
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(exerciseHint);
    }

    @Test
    void testGetAvailableExerciseHintsWithZeroThreshold3() {
        exerciseHint.setDisplayThreshold((short) 0);
        exerciseHintRepository.save(exerciseHint);
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(exerciseHint);
    }

    @Test
    void testGetAvailableExerciseHints_skippedTestsConsideredAsNegative() {
        // create result with feedbacks with "null" for attribute "positive"
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        var results = resultRepository.findAllByParticipationExerciseId(exercise.getId());
        var optionalResult = resultRepository.findWithBidirectionalSubmissionAndFeedbackAndAssessorAndTeamStudentsById(results.iterator().next().getId());
        assertThat(optionalResult).isPresent();

        var result = optionalResult.get();
        result.getFeedbacks().forEach(feedback -> feedback.setPositive(null));
        resultRepository.save(result);

        // create results with feedbacks with "false" for attribute "positive"
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());

        var availableHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableHints).containsExactly(exerciseHint);
    }

    @Test
    void testActivateExerciseHint1() {
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());

        assertThat(exerciseHintService.activateHint(hints.get(0), student)).isTrue();
        assertThat(exerciseHintService.activateHint(hints.get(1), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(2), student)).isFalse();
        Set<ExerciseHintActivation> exerciseHintActivations = exerciseHintActivationRepository.findByExerciseAndUserWithExerciseHintRelations(exercise.getId(), student.getId());
        assertThat(exerciseHintActivations).hasSize(1).allMatch(activation -> activation.getExerciseHint().getId().equals(exerciseHint.getId()));
    }

    @Test
    void testActivateExerciseHint2() {
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());

        assertThat(exerciseHintService.activateHint(hints.get(0), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(1), student)).isTrue();
        assertThat(exerciseHintService.activateHint(hints.get(2), student)).isFalse();
        Set<ExerciseHintActivation> exerciseHintActivations = exerciseHintActivationRepository.findByExerciseAndUserWithExerciseHintRelations(exercise.getId(), student.getId());
        assertThat(exerciseHintActivations).hasSize(1).allMatch(activation -> activation.getExerciseHint().getId().equals(hints.get(1).getId()));
    }

    @Test
    void testActivateExerciseHint3() {
        addResultWithFailedTestCases(sortedTasks.get(2).getTestCases());
        addResultWithFailedTestCases(sortedTasks.get(2).getTestCases());
        addResultWithFailedTestCases(sortedTasks.get(2).getTestCases());

        assertThat(exerciseHintService.activateHint(hints.get(0), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(1), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(2), student)).isTrue();
        Set<ExerciseHintActivation> exerciseHintActivations = exerciseHintActivationRepository.findByExerciseAndUserWithExerciseHintRelations(exercise.getId(), student.getId());
        assertThat(exerciseHintActivations).hasSize(1).allMatch(activation -> activation.getExerciseHint().getId().equals(hints.get(2).getId()));
    }

    @Test
    void testActivateExerciseHint4() {
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        addResultWithSuccessfulTestCases(exercise.getTestCases());

        assertThat(exerciseHintService.activateHint(hints.get(0), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(1), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(2), student)).isFalse();
        Set<ExerciseHintActivation> exerciseHintActivations = exerciseHintActivationRepository.findByExerciseAndUserWithExerciseHintRelations(exercise.getId(), student.getId());
        assertThat(exerciseHintActivations).isEmpty();
    }

    @Test
    void testActivateExerciseHintTwiceFails() {
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());

        assertThat(exerciseHintService.activateHint(exerciseHint, student)).isTrue();
        assertThat(exerciseHintService.activateHint(exerciseHint, student)).isFalse();
        Set<ExerciseHintActivation> exerciseHintActivations = exerciseHintActivationRepository.findByExerciseAndUserWithExerciseHintRelations(exercise.getId(), student.getId());
        assertThat(exerciseHintActivations).hasSize(1).allMatch(activation -> activation.getExerciseHint().getId().equals(exerciseHint.getId()));
    }

    private void addResultWithFailedTestCases(Collection<ProgrammingExerciseTestCase> failedTestCases) {
        var successfulTestCases = new ArrayList<>(exercise.getTestCases());
        successfulTestCases.removeAll(failedTestCases);
        addResultWithSuccessfulTestCases(successfulTestCases);
    }

    private void addResultWithSuccessfulTestCases(Collection<ProgrammingExerciseTestCase> successfulTestCases) {
        var submission = programmingExerciseUtilService.createProgrammingSubmission(studentParticipation, false);
        Result result = new Result().participation(submission.getParticipation()).assessmentType(AssessmentType.AUTOMATIC).score(0D).rated(true)
                .completionDate(ZonedDateTime.now().plusSeconds(timeOffset++));
        result = resultRepository.save(result);
        result.setSubmission(submission);
        submission.addResult(result);
        programmingSubmissionRepository.save(submission);

        for (ProgrammingExerciseTestCase testCase : exercise.getTestCases()) {
            var feedback = new Feedback();
            feedback.setPositive(successfulTestCases.contains(testCase));
            feedback.setTestCase(testCase);
            feedback.setVisibility(Visibility.ALWAYS);
            feedback.setType(FeedbackType.AUTOMATIC);
            participationUtilService.addFeedbackToResult(feedback, result);
        }
    }
}
