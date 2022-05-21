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
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.repository.hestia.UserExerciseHintActivationRepository;
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
    private UserExerciseHintActivationRepository userExerciseHintActivationRepository;

    private ProgrammingExercise exercise;

    private List<ProgrammingExerciseTask> sortedTasks;

    private User student;

    private ProgrammingExerciseStudentParticipation studentParticipation;

    private ProgrammingSubmission submission;

    private int timeOffset = 0;

    @BeforeEach
    public void initTestCase() {
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        database.addUsers(2, 2, 1, 2);

        student = userRepository.getUserWithGroupsAndAuthorities("student1");
        database.changeUser("student1");

        exercise = exerciseRepository.findAll().get(0);
        exercise = database.loadProgrammingExerciseWithEagerReferences(exercise);
        database.addHintsToExercise(exercise);
        database.addTasksToProgrammingExercise(exercise);

        sortedTasks = programmingExerciseTaskService.getSortedTasks(exercise);
        studentParticipation = database.addStudentParticipationForProgrammingExercise(exercise, student.getLogin());
        submission = database.createProgrammingSubmission(studentParticipation, false);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    public void testGetAvailableExerciseHints1() {
        var hint = exerciseHintRepository.findAll().stream().findFirst().orElseThrow();
        hint.setProgrammingExerciseTask(sortedTasks.get(0));
        exerciseHintRepository.save(hint);
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hint);
    }

    @Test
    public void testGetAvailableExerciseHints2() {
        var hint = exerciseHintRepository.findAll().stream().findFirst().orElseThrow();
        hint.setProgrammingExerciseTask(sortedTasks.get(0));
        exerciseHintRepository.save(hint);
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        addResultWithSuccessfulTestCases(exercise.getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).isEmpty();
    }

    @Test
    public void testGetAvailableExerciseHints3() {
        var hints = exerciseHintRepository.findAll();
        hints.get(0).setProgrammingExerciseTask(sortedTasks.get(0));
        hints.get(1).setProgrammingExerciseTask(sortedTasks.get(1));
        exerciseHintRepository.saveAll(hints);
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(exercise.getTestCases());
        addResultWithFailedTestCases(sortedTasks.get(0).getTestCases());
        var availableExerciseHints = exerciseHintService.getAvailableExerciseHints(exercise, student);
        assertThat(availableExerciseHints).containsExactly(hints.get(1));
    }

    private void addResultWithFailedTestCases(Collection<ProgrammingExerciseTestCase> failedTestCases) {
        var successfulTestCases = new ArrayList<>(exercise.getTestCases());
        successfulTestCases.removeAll(failedTestCases);
        addResultWithSuccessfulTestCases(successfulTestCases);
    }

    private void addResultWithSuccessfulTestCases(Collection<ProgrammingExerciseTestCase> successfulTestCases) {
        Result result = new Result().participation(submission.getParticipation()).assessmentType(AssessmentType.AUTOMATIC).resultString("3 out of 3 failed").score(0D).rated(true)
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
