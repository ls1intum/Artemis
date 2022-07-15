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
import de.tum.in.www1.artemis.service.hestia.ExerciseHintService;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseTaskService;

public class ExerciseHintServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private ProgrammingExercise exercise;

    private List<ProgrammingExerciseTask> sortedTasks;

    private List<ExerciseHint> hints;

    private User student;

    private ProgrammingExerciseStudentParticipation studentParticipation;

    private int timeOffset = 0;

    @BeforeEach
    public void initTestCase() {
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addUsers(2, 2, 1, 2);

        student = userRepository.getUserWithGroupsAndAuthorities("student1");
        database.changeUser("student1");

        programmingExerciseTestCaseRepository.saveAll(programmingExerciseTestCaseRepository.findAll().stream().peek(testCase -> testCase.setActive(true)).toList());
        exercise = exerciseRepository.findAll().get(0);
        exercise = database.loadProgrammingExerciseWithEagerReferences(exercise);
        database.addHintsToExercise(exercise);
        database.addTasksToProgrammingExercise(exercise);

        sortedTasks = programmingExerciseTaskService.getSortedTasks(exercise);

        hints = exerciseHintRepository.findAll();
        hints.get(0).setProgrammingExerciseTask(sortedTasks.get(0));
        hints.get(1).setProgrammingExerciseTask(sortedTasks.get(1));
        hints.get(2).setProgrammingExerciseTask(sortedTasks.get(2));
        exerciseHintRepository.saveAll(hints);

        studentParticipation = database.addStudentParticipationForProgrammingExercise(exercise, student.getLogin());
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    public void testGetAvailableExerciseHintsEmpty1() {
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).isEmpty();
    }

    @Test
    public void testGetAvailableExerciseHintsEmpty2() {
        addResultWithFailedTestCases(exercise.getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).isEmpty();
    }

    @Test
    public void testGetAvailableExerciseHintsEmpty3() {
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).isEmpty();
    }

    @Test
    public void testGetAvailableExerciseHintsEmpty4() {
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        addResultWithFailedTestCases(sortedTasks.get(2).getTestCases());
        addResultWithFailedTestCases(sortedTasks.get(2).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).isEmpty();
    }

    @Test
    public void testGetAvailableExerciseHints1() {
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hints.get(0));
    }

    @Test
    public void testGetAvailableExerciseHints2() {
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hints.get(1));
    }

    @Test
    public void testGetAvailableExerciseHints3() {
        addResultWithSuccessfulTestCases(sortedTasks.get(1).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(1).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(1).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hints.get(0));
    }

    @Test
    public void testGetAvailableExerciseHints4() {
        addResultWithSuccessfulTestCases(sortedTasks.get(2).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(2).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(2).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hints.get(0));
    }

    @Test
    public void testGetAvailableExerciseHints5() {
        addResultWithSuccessfulTestCases(sortedTasks.get(1).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(2).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(1).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hints.get(0));
    }

    @Test
    public void testGetAvailableExerciseHints_skippedTestsConsideredAsNegative() {
        // create result with feedbacks with "null" for attribute "positive"
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        var results = resultRepository.findAll();
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
    public void testActivateExerciseHint1() {
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());

        assertThat(exerciseHintService.activateHint(hints.get(0), student)).isTrue();
        assertThat(exerciseHintService.activateHint(hints.get(1), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(2), student)).isFalse();
        assertThat(exerciseHintActivationRepository.findAll()).hasSize(1).anyMatch(ueha -> ueha.getUser().equals(student) && ueha.getExerciseHint().equals(hints.get(0)));
    }

    @Test
    public void testActivateExerciseHint2() {
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());
        addResultWithSuccessfulTestCases(sortedTasks.get(0).getTestCases());

        assertThat(exerciseHintService.activateHint(hints.get(0), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(1), student)).isTrue();
        assertThat(exerciseHintService.activateHint(hints.get(2), student)).isFalse();
        assertThat(exerciseHintActivationRepository.findAll()).hasSize(1).anyMatch(ueha -> ueha.getUser().equals(student) && ueha.getExerciseHint().equals(hints.get(1)));
    }

    @Test
    public void testActivateExerciseHint3() {
        addResultWithFailedTestCases(sortedTasks.get(2).getTestCases());
        addResultWithFailedTestCases(sortedTasks.get(2).getTestCases());
        addResultWithFailedTestCases(sortedTasks.get(2).getTestCases());

        assertThat(exerciseHintService.activateHint(hints.get(0), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(1), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(2), student)).isTrue();
        assertThat(exerciseHintActivationRepository.findAll()).hasSize(1).anyMatch(ueha -> ueha.getUser().equals(student) && ueha.getExerciseHint().equals(hints.get(2)));
    }

    @Test
    public void testActivateExerciseHint4() {
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        addResultWithSuccessfulTestCases(exercise.getTestCases());

        assertThat(exerciseHintService.activateHint(hints.get(0), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(1), student)).isFalse();
        assertThat(exerciseHintService.activateHint(hints.get(2), student)).isFalse();
        assertThat(exerciseHintActivationRepository.findAll()).isEmpty();
    }

    @Test
    public void testActivateExerciseHintTwiceFails() {
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
