package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class AssessmentServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private ExerciseDateService exerciseDateService;

    private final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);

    private final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);

    private final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

    private Course course1 = new Course();

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @BeforeEach
    public void init() {
        database.addUsers(2, 2, 0, 1);
        course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course1.setRegistrationEnabled(true);
        courseRepository.save(course1);
    }

    public TextExercise createTextExerciseWithSGI(Course course) {
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setMaxPoints(7.0);
        database.addGradingInstructionsToExercise(textExercise);
        exerciseRepository.save(textExercise);
        textExercise.getCategories().add("Text");
        course.addExercises(textExercise);
        return textExercise;
    }

    public ModelingExercise createModelingExerciseWithSGI(Course course) {
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        modelingExercise.setMaxPoints(7.0);
        database.addGradingInstructionsToExercise(modelingExercise);
        exerciseRepository.save(modelingExercise);
        modelingExercise.getCategories().add("Modeling");
        course.addExercises(modelingExercise);
        return modelingExercise;
    }

    public FileUploadExercise createFileuploadExerciseWithSGI(Course course) {
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, "png", course);
        fileUploadExercise.setMaxPoints(7.0);
        database.addGradingInstructionsToExercise(fileUploadExercise);
        fileUploadExercise.getCategories().add("File");
        exerciseRepository.save(fileUploadExercise);
        course.addExercises(fileUploadExercise);
        return fileUploadExercise;
    }

    public List<Feedback> createFeedback(Exercise exercise) {

        var gradingInstructionNoLimit = exercise.getGradingCriteria().get(0).getStructuredGradingInstructions().get(0);
        var gradingInstructionLimited = exercise.getGradingCriteria().get(1).getStructuredGradingInstructions().get(0);
        var gradingInstructionBigLimit = exercise.getGradingCriteria().get(2).getStructuredGradingInstructions().get(0);

        var feedbacks = new ArrayList<Feedback>();
        var feedbackAppliedSGINoLimit = new Feedback();
        var feedbackAppliedSGINoLimit2 = new Feedback();
        var feedbackAppliedSGILimited = new Feedback();
        var feedbackAppliedSGILimited2 = new Feedback();
        var feedbackAppliedSGIBigLimit = new Feedback();
        var feedbackAppliedSGIBigLimit2 = new Feedback();
        var feedbackNoSGI = new Feedback();

        feedbackAppliedSGIBigLimit.setGradingInstruction(gradingInstructionBigLimit);
        feedbackAppliedSGIBigLimit.setCredits(gradingInstructionBigLimit.getCredits());

        feedbackAppliedSGIBigLimit2.setGradingInstruction(gradingInstructionBigLimit);
        feedbackAppliedSGIBigLimit2.setCredits(gradingInstructionBigLimit.getCredits());

        feedbackAppliedSGINoLimit.setGradingInstruction(gradingInstructionNoLimit);
        feedbackAppliedSGINoLimit.setCredits(gradingInstructionNoLimit.getCredits());

        feedbackAppliedSGILimited.setGradingInstruction(gradingInstructionLimited);
        feedbackAppliedSGILimited.setCredits(gradingInstructionLimited.getCredits());

        feedbackAppliedSGINoLimit2.setGradingInstruction(gradingInstructionNoLimit);
        feedbackAppliedSGINoLimit2.setCredits(gradingInstructionNoLimit.getCredits());

        feedbackAppliedSGILimited2.setGradingInstruction(gradingInstructionLimited);
        feedbackAppliedSGILimited2.setCredits(gradingInstructionLimited.getCredits());

        feedbackNoSGI.setCredits(1.0);
        feedbackNoSGI.setDetailText("This is a free text feedback");

        feedbacks.add(feedbackAppliedSGIBigLimit); // +1P
        feedbacks.add(feedbackAppliedSGIBigLimit2); // +1P limited but we did not pass the limit yet so point will be counted
        feedbacks.add(feedbackAppliedSGINoLimit); // +1P
        feedbacks.add(feedbackAppliedSGILimited); // +1P
        feedbacks.add(feedbackAppliedSGINoLimit2); // +1P
        feedbacks.add(feedbackAppliedSGILimited2); // +1P will not be counted, we passed the limit!!!
        feedbacks.add(feedbackNoSGI); // +1P
        return feedbacks; // so total score is 6P
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createFileUploadSubmissionAndCalculateScore() {
        FileUploadExercise exercise = createFileuploadExerciseWithSGI(course1);
        Submission submissionWithoutResult = new FileUploadSubmission();
        submissionWithoutResult.setSubmissionDate(pastTimestamp.plusMinutes(3L));
        submissionWithoutResult = database.addSubmission(exercise, submissionWithoutResult, "student1");
        database.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);

        List<Feedback> feedbacks = createFeedback(exercise);
        var result = new Result();
        result.setSubmission(submissionWithoutResult);
        result.setFeedbacks(feedbacks);
        result.setParticipation(submissionWithoutResult.getParticipation());
        submissionWithoutResult.addResult(result);

        resultRepository.submitResult(result, exercise, exerciseDateService.getDueDate(result.getParticipation()));
        resultRepository.save(result);

        assertThat(result.getScore()).isEqualTo(85.7); // 85.7 = 6/7 * 100
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createTextExerciseSubmissionAndCalculateScore() {
        TextExercise exercise = createTextExerciseWithSGI(course1);
        Submission submissionWithoutResult = new TextSubmission();
        submissionWithoutResult.setSubmissionDate(pastTimestamp.plusMinutes(3L));
        submissionWithoutResult = database.addSubmission(exercise, submissionWithoutResult, "student1");
        database.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);

        List<Feedback> feedbacks = createFeedback(exercise);
        var result = new Result();
        result.setSubmission(submissionWithoutResult);
        result.setFeedbacks(feedbacks);
        result.setParticipation(submissionWithoutResult.getParticipation());
        submissionWithoutResult.addResult(result);

        resultRepository.submitResult(result, exercise, exerciseDateService.getDueDate(result.getParticipation()));
        resultRepository.save(result);

        assertThat(result.getScore()).isEqualTo(85.7); // 85.7 = 6/7 * 100
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createModelingExerciseSubmissionAndCalculateScore() {
        ModelingExercise exercise = createModelingExerciseWithSGI(course1);
        Submission submissionWithoutResult = new ModelingSubmission();
        submissionWithoutResult.setSubmissionDate(pastTimestamp.plusMinutes(3L));
        submissionWithoutResult = database.addSubmission(exercise, submissionWithoutResult, "student1");
        database.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);

        List<Feedback> feedbacks = createFeedback(exercise);
        var result = new Result();
        result.setSubmission(submissionWithoutResult);
        result.setFeedbacks(feedbacks);
        result.setParticipation(submissionWithoutResult.getParticipation());
        submissionWithoutResult.addResult(result);

        resultRepository.submitResult(result, exercise, exerciseDateService.getDueDate(result.getParticipation()));
        resultRepository.save(result);

        assertThat(result.getScore()).isEqualTo(85.7); // 85.7 = 6/7 * 100
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testRatedAfterSubmitResultWithDueDateEqualsSubmissionDateOfResult(boolean isDueDateIndividual) {
        TextExercise exercise = createTextExerciseWithSGI(course1);
        Submission submissionWithoutResult = new TextSubmission();
        // comparison of dates including nanos would make this test flaky
        submissionWithoutResult.setSubmissionDate(futureTimestamp.truncatedTo(ChronoUnit.MILLIS));
        submissionWithoutResult = database.addSubmission(exercise, submissionWithoutResult, "student1");
        database.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);

        List<Feedback> feedbacks = createFeedback(exercise);
        var result = new Result();
        result.setSubmission(submissionWithoutResult);
        result.setFeedbacks(feedbacks);
        result.setParticipation(submissionWithoutResult.getParticipation());
        submissionWithoutResult.addResult(result);

        if (isDueDateIndividual) {
            // participation has exact same individual due date as submission time, submission should still be rated
            Participation participation = result.getParticipation();
            participation.setIndividualDueDate(submissionWithoutResult.getSubmissionDate());
            result.setParticipation(participationRepository.save(participation));

            // exercise due date itself should be before the submission time
            exercise.setDueDate(submissionWithoutResult.getSubmissionDate().minusHours(1));
        }
        else {
            // exercise has exact same due date as submission time, submission should still be rated
            exercise.setDueDate(submissionWithoutResult.getSubmissionDate());
        }
        exercise = exerciseRepository.save(exercise);

        resultRepository.submitResult(result, exercise, exerciseDateService.getDueDate(result.getParticipation()));
        resultRepository.save(result);

        assertThat(result.isRated()).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testNotRatedAfterSubmitResultWithDueDateBeforeSubmissionDateOfResult() {
        TextExercise exercise = createTextExerciseWithSGI(course1);
        Submission submissionWithoutResult = new TextSubmission();
        submissionWithoutResult.setSubmissionDate(futureFutureTimestamp);
        submissionWithoutResult = database.addSubmission(exercise, submissionWithoutResult, "student1");
        database.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);

        List<Feedback> feedbacks = createFeedback(exercise);
        var result = new Result();
        result.setSubmission(submissionWithoutResult);
        result.setFeedbacks(feedbacks);
        result.setParticipation(submissionWithoutResult.getParticipation());
        submissionWithoutResult.addResult(result);

        resultRepository.submitResult(result, exercise, exerciseDateService.getDueDate(result.getParticipation()));
        resultRepository.save(result);

        assertThat(result.isRated()).isFalse();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testRatedAfterSubmitResultWithDueDateBeforeSubmissionDateOfResult() {
        TextExercise exercise = createTextExerciseWithSGI(course1);
        Submission submissionWithoutResult = new TextSubmission();
        submissionWithoutResult.setSubmissionDate(pastTimestamp);
        submissionWithoutResult = database.addSubmission(exercise, submissionWithoutResult, "student1");
        database.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);

        List<Feedback> feedbacks = createFeedback(exercise);
        var result = new Result();
        result.setSubmission(submissionWithoutResult);
        result.setFeedbacks(feedbacks);
        result.setParticipation(submissionWithoutResult.getParticipation());
        submissionWithoutResult.addResult(result);

        resultRepository.submitResult(result, exercise, exerciseDateService.getDueDate(result.getParticipation()));
        resultRepository.save(result);

        assertThat(result.isRated()).isTrue();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testIsAllowedToCreateOrOverrideResult_withExamDueDateNotPassed() {
        ZonedDateTime visibleDate = ZonedDateTime.now().minusHours(2);
        ZonedDateTime startDate = ZonedDateTime.now().minusHours(1);
        ZonedDateTime endDate = ZonedDateTime.now().plusHours(1);

        Exam exam = database.addExam(course1, visibleDate, startDate, endDate);
        exam = database.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var exercise = exam.getExerciseGroups().get(0).getExercises().iterator().next();

        boolean isAllowed = assessmentService.isAllowedToCreateOrOverrideResult(null, exercise, null, null, false);
        assertThat(isAllowed).isFalse();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testIsAllowedToCreateOrOverrideResult_withExamPublishResultDatePassed() {
        ZonedDateTime visibleDate = ZonedDateTime.now().minusHours(3);
        ZonedDateTime startDate = ZonedDateTime.now().minusHours(2);
        ZonedDateTime endDate = ZonedDateTime.now().minusHours(1);
        ZonedDateTime publishResultDate = ZonedDateTime.now().minusMinutes(1);

        Exam exam = database.addExam(course1, visibleDate, startDate, endDate, publishResultDate);
        exam = database.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var exercise = exam.getExerciseGroups().get(0).getExercises().iterator().next();

        boolean isAllowed = assessmentService.isAllowedToCreateOrOverrideResult(null, exercise, null, null, false);
        assertThat(isAllowed).isFalse();
    }
}
