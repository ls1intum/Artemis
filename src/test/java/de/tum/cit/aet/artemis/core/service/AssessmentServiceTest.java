package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.service.AssessmentService;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.assessment.util.GradingCriterionUtil;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseFactory;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

class AssessmentServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "assessmentservice";

    @Autowired
    private ResultTestRepository resultRepository;

    @Autowired
    private ParticipationTestRepository participationRepository;

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);

    private final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);

    private final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(8);

    private Course course1 = new Course();

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 0, 1);
        course1 = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>());
        course1.setEnrollmentEnabled(true);
        courseRepository.save(course1);
    }

    private TextExercise createTextExerciseWithSGI(Course course) {
        TextExercise textExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setMaxPoints(7.0);
        exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        exerciseRepository.save(textExercise);
        textExercise.getCategories().add("Text");
        course.addExercises(textExercise);
        return textExercise;
    }

    private ModelingExercise createModelingExerciseWithSGI(Course course) {
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram,
                course);
        modelingExercise.setMaxPoints(7.0);
        exerciseUtilService.addGradingInstructionsToExercise(modelingExercise);
        exerciseRepository.save(modelingExercise);
        modelingExercise.getCategories().add("Modeling");
        course.addExercises(modelingExercise);
        return modelingExercise;
    }

    private FileUploadExercise createFileuploadExerciseWithSGI(Course course) {
        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, "png", course);
        fileUploadExercise.setMaxPoints(7.0);
        exerciseUtilService.addGradingInstructionsToExercise(fileUploadExercise);
        fileUploadExercise.getCategories().add("File");
        exerciseRepository.save(fileUploadExercise);
        course.addExercises(fileUploadExercise);
        return fileUploadExercise;
    }

    private List<Feedback> createFeedback(Exercise exercise) {
        var gradingInstructionNoLimit = GradingCriterionUtil.findInstructionByMaxUsageCount(exercise.getGradingCriteria(), 0);
        var gradingInstructionLimited = GradingCriterionUtil.findInstructionByMaxUsageCount(exercise.getGradingCriteria(), 1);
        var gradingInstructionBigLimit = GradingCriterionUtil.findInstructionByMaxUsageCount(exercise.getGradingCriteria(), 4);

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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadSubmissionAndCalculateScore() {
        FileUploadExercise exercise = createFileuploadExerciseWithSGI(course1);
        Submission submissionWithoutResult = new FileUploadSubmission();
        submissionWithoutResult.setSubmissionDate(pastTimestamp.plusMinutes(3L));
        submissionWithoutResult = participationUtilService.addSubmission(exercise, submissionWithoutResult, TEST_PREFIX + "student1");
        participationUtilService.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);

        List<Feedback> feedbacks = createFeedback(exercise);
        var result = new Result();
        result.setSubmission(submissionWithoutResult);
        result.setFeedbacks(feedbacks);
        result.setExerciseId(exercise.getId());
        submissionWithoutResult.addResult(result);

        resultRepository.submitResult(result, exercise);
        resultRepository.save(result);

        assertThat(result.getScore()).isEqualTo(85.7); // 85.7 = 6/7 * 100
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createTextExerciseSubmissionAndCalculateScore() {
        TextExercise exercise = createTextExerciseWithSGI(course1);
        Submission submissionWithoutResult = new TextSubmission();
        submissionWithoutResult.setSubmissionDate(pastTimestamp.plusMinutes(3L));
        submissionWithoutResult = participationUtilService.addSubmission(exercise, submissionWithoutResult, TEST_PREFIX + "student1");
        participationUtilService.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);

        List<Feedback> feedbacks = createFeedback(exercise);
        var result = new Result();
        result.setSubmission(submissionWithoutResult);
        result.setFeedbacks(feedbacks);
        result.setExerciseId(exercise.getId());
        submissionWithoutResult.addResult(result);

        resultRepository.submitResult(result, exercise);
        resultRepository.save(result);

        assertThat(result.getScore()).isEqualTo(85.7); // 85.7 = 6/7 * 100
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createModelingExerciseSubmissionAndCalculateScore() {
        ModelingExercise exercise = createModelingExerciseWithSGI(course1);
        Submission submissionWithoutResult = new ModelingSubmission();
        submissionWithoutResult.setSubmissionDate(pastTimestamp.plusMinutes(3L));
        submissionWithoutResult = participationUtilService.addSubmission(exercise, submissionWithoutResult, TEST_PREFIX + "student1");
        participationUtilService.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);

        List<Feedback> feedbacks = createFeedback(exercise);
        var result = new Result();
        result.setSubmission(submissionWithoutResult);
        result.setFeedbacks(feedbacks);
        result.setExerciseId(exercise.getId());
        submissionWithoutResult.addResult(result);

        resultRepository.submitResult(result, exercise);
        resultRepository.save(result);

        assertThat(result.getScore()).isEqualTo(85.7); // 85.7 = 6/7 * 100
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRatedAfterSubmitResultWithDueDateEqualsSubmissionDateOfResult(boolean isDueDateIndividual) {
        TextExercise exercise = createTextExerciseWithSGI(course1);
        Submission submissionWithoutResult = new TextSubmission();
        // comparison of dates including nanos would make this test flaky
        submissionWithoutResult.setSubmissionDate(futureTimestamp.truncatedTo(ChronoUnit.MILLIS));
        submissionWithoutResult = participationUtilService.addSubmission(exercise, submissionWithoutResult, TEST_PREFIX + "student1");
        participationUtilService.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);

        List<Feedback> feedbacks = createFeedback(exercise);
        var result = new Result();
        result.setSubmission(submissionWithoutResult);
        result.setFeedbacks(feedbacks);
        result.setExerciseId(exercise.getId());
        submissionWithoutResult.addResult(result);

        if (isDueDateIndividual) {
            // participation has exact same individual due date as submission time, submission should still be rated
            Participation participation = result.getSubmission().getParticipation();
            participation.setIndividualDueDate(submissionWithoutResult.getSubmissionDate());
            participationRepository.save(participation);

            // exercise due date itself should be before the submission time
            exercise.setDueDate(submissionWithoutResult.getSubmissionDate().minusHours(1));
        }
        else {
            // exercise has exact same due date as submission time, submission should still be rated
            exercise.setDueDate(submissionWithoutResult.getSubmissionDate());
        }
        exercise = exerciseRepository.save(exercise);

        resultRepository.submitResult(result, exercise);
        resultRepository.save(result);

        assertThat(result.isRated()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testNotRatedAfterSubmitResultWithDueDateBeforeSubmissionDateOfResult() {
        TextExercise exercise = createTextExerciseWithSGI(course1);
        Submission submissionWithoutResult = new TextSubmission();
        submissionWithoutResult.setSubmissionDate(futureFutureTimestamp);
        submissionWithoutResult = participationUtilService.addSubmission(exercise, submissionWithoutResult, TEST_PREFIX + "student1");
        participationUtilService.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);

        List<Feedback> feedbacks = createFeedback(exercise);
        var result = new Result();
        result.setSubmission(submissionWithoutResult);
        result.setFeedbacks(feedbacks);
        result.setExerciseId(exercise.getId());
        submissionWithoutResult.addResult(result);

        resultRepository.submitResult(result, exercise);
        resultRepository.save(result);

        assertThat(result.isRated()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRatedAfterSubmitResultWithDueDateBeforeSubmissionDateOfResult() {
        TextExercise exercise = createTextExerciseWithSGI(course1);
        Submission submissionWithoutResult = new TextSubmission();
        submissionWithoutResult.setSubmissionDate(pastTimestamp);
        submissionWithoutResult = participationUtilService.addSubmission(exercise, submissionWithoutResult, TEST_PREFIX + "student1");
        participationUtilService.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);

        List<Feedback> feedbacks = createFeedback(exercise);
        var result = new Result();
        result.setSubmission(submissionWithoutResult);
        result.setFeedbacks(feedbacks);
        result.setExerciseId(exercise.getId());
        submissionWithoutResult.addResult(result);

        resultRepository.submitResult(result, exercise);
        resultRepository.save(result);

        assertThat(result.isRated()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testIsAllowedToCreateOrOverrideResult_withExamDueDateNotPassed() {
        ZonedDateTime visibleDate = ZonedDateTime.now().minusHours(2);
        ZonedDateTime startDate = ZonedDateTime.now().minusHours(1);
        ZonedDateTime endDate = ZonedDateTime.now().plusHours(1);

        Exam exam = examUtilService.addExam(course1, visibleDate, startDate, endDate);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var exercise = exam.getExerciseGroups().getFirst().getExercises().iterator().next();

        boolean isAllowed = assessmentService.isAllowedToCreateOrOverrideResult(null, exercise, null, null, false);
        assertThat(isAllowed).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testIsAllowedToCreateOrOverrideResult_withExamPublishResultDatePassed() {
        ZonedDateTime visibleDate = ZonedDateTime.now().minusHours(3);
        ZonedDateTime startDate = ZonedDateTime.now().minusHours(2);
        ZonedDateTime endDate = ZonedDateTime.now().minusHours(1);
        ZonedDateTime publishResultDate = ZonedDateTime.now().minusMinutes(1);

        Exam exam = examUtilService.addExam(course1, visibleDate, startDate, endDate, publishResultDate);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var exercise = exam.getExerciseGroups().getFirst().getExercises().iterator().next();

        boolean isAllowed = assessmentService.isAllowedToCreateOrOverrideResult(null, exercise, null, null, false);
        assertThat(isAllowed).isFalse();
    }

    /**
     * A student who practices a team exercise on their own has a participation without a team, so there is no team tutor. The check must not fail on it, and the tutor of the
     * course can assess the practice work like any other.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testIsAllowedToCreateOrOverrideResult_practiceParticipationOfTeamExercise() {
        TextExercise teamExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, pastTimestamp, pastTimestamp, course1);
        teamExercise.setMode(ExerciseMode.TEAM);
        StudentParticipation practiceParticipation = new StudentParticipation();
        practiceParticipation.setExercise(teamExercise);
        practiceParticipation.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        practiceParticipation.setPracticeMode(true);

        boolean isAllowed = assessmentService.isAllowedToCreateOrOverrideResult(null, teamExercise, practiceParticipation, userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"),
                false);

        assertThat(isAllowed).isTrue();
    }

    private TextExercise teamExercise() {
        TextExercise teamExercise = TextExerciseFactory.generateTextExercise(pastTimestamp, pastTimestamp, pastTimestamp, course1);
        teamExercise.setMode(ExerciseMode.TEAM);
        return teamExercise;
    }

    private StudentParticipation practiceParticipationOfStudent1(TextExercise teamExercise) {
        StudentParticipation practiceParticipation = new StudentParticipation();
        practiceParticipation.setExercise(teamExercise);
        practiceParticipation.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        practiceParticipation.setPracticeMode(true);
        return practiceParticipation;
    }

    /**
     * The graded participation of a team keeps the rule that only the tutor who owns the team is the assessor, also when the result is first created.
     */
    @ParameterizedTest(name = "{0} is allowed: {1}")
    @CsvSource({ "tutor1,true", "tutor2,false" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testIsAllowedToCreateOrOverrideResult_teamParticipation_onlyTheTeamTutorIsAllowed(String tutor, boolean expected) {
        TextExercise teamExercise = teamExercise();
        Team team = new Team();
        team.setOwner(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        StudentParticipation teamParticipation = new StudentParticipation();
        teamParticipation.setExercise(teamExercise);
        teamParticipation.setParticipant(team);

        boolean isAllowed = assessmentService.isAllowedToCreateOrOverrideResult(null, teamExercise, teamParticipation, userUtilService.getUserByLogin(TEST_PREFIX + tutor), false);

        assertThat(isAllowed).isEqualTo(expected);
    }

    /**
     * A team without a tutor has nobody who may assess its participation.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testIsAllowedToCreateOrOverrideResult_teamParticipationWithoutTeamTutor_isNotAllowed() {
        TextExercise teamExercise = teamExercise();
        StudentParticipation teamParticipation = new StudentParticipation();
        teamParticipation.setExercise(teamExercise);
        teamParticipation.setParticipant(new Team());

        boolean isAllowed = assessmentService.isAllowedToCreateOrOverrideResult(null, teamExercise, teamParticipation, userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"),
                false);

        assertThat(isAllowed).isFalse();
    }

    /**
     * A practice participation of a team exercise has no team tutor, so it follows the rule of an individual exercise: a tutor may continue an assessment they started, or one
     * nobody started, but not the draft of another tutor.
     */
    @ParameterizedTest(name = "assessor {0}, requested by tutor1: {1}")
    @CsvSource({ "tutor1,true", "tutor2,false", "none,true" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testIsAllowedToCreateOrOverrideResult_practiceParticipationOfTeamExercise_followsTheIndividualRule(String assessor, boolean expected) {
        TextExercise teamExercise = teamExercise();
        StudentParticipation practiceParticipation = practiceParticipationOfStudent1(teamExercise);
        Result draft = new Result();
        if (!"none".equals(assessor)) {
            draft.setAssessor(userUtilService.getUserByLogin(TEST_PREFIX + assessor));
        }

        boolean isAllowed = assessmentService.isAllowedToCreateOrOverrideResult(draft, teamExercise, practiceParticipation, userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"),
                false);

        assertThat(isAllowed).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testIsAllowedToCreateOrOverrideResult_practiceParticipationOfTeamExercise_instructorMayAlwaysOverride() {
        TextExercise teamExercise = teamExercise();
        StudentParticipation practiceParticipation = practiceParticipationOfStudent1(teamExercise);
        Result draft = new Result();
        draft.setAssessor(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));

        boolean isAllowed = assessmentService.isAllowedToCreateOrOverrideResult(draft, teamExercise, practiceParticipation,
                userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), true);

        assertThat(isAllowed).isTrue();
    }
}
