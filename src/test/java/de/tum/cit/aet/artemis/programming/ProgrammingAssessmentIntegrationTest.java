package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.assertj.core.data.Offset;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentNote;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUpdateDTO;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

class ProgrammingAssessmentIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "programmingassessment";

    private final String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";

    private final Double offsetByTenThousandth = 0.0001;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission programmingSubmission;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private Result manualResult;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 3, 2, 0, 2);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        exerciseUtilService.addMaxScoreAndBonusPointsToExercise(programmingExercise);
        programmingSubmission = ParticipationFactory.generateProgrammingSubmission(true);
        programmingSubmission = programmingExerciseUtilService.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, programmingSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1", AssessmentType.SEMI_AUTOMATIC, true);

        programmingExerciseStudentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        programmingSubmission.setParticipation(programmingExerciseStudentParticipation);
        // A new manual result and submission are created during the locking of submission for manual assessment
        // The new result has an assessment type, automatic feedbacks and assessor
        var automaticFeedback = new Feedback().credits(0.0).detailText("asdfasdf").type(FeedbackType.AUTOMATIC).text("asdf");
        var automaticFeedbacks = new ArrayList<Feedback>();
        automaticFeedbacks.add(automaticFeedback);
        var newManualResult = participationUtilService.addResultToSubmission(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now(), programmingSubmission, TEST_PREFIX + "tutor1",
                automaticFeedbacks);
        // Set submission of newResult
        programmingExerciseUtilService.addProgrammingSubmissionToResultAndParticipation(newManualResult, programmingExerciseStudentParticipation, "123");

        List<Feedback> feedbacks = ParticipationFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here"))
                .collect(Collectors.toCollection(ArrayList::new));
        manualResult = ParticipationFactory.generateResult(true, 90D);
        manualResult.setFeedbacks(feedbacks);
        manualResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        manualResult.rated(true);
        manualResult.setSubmission(programmingSubmission);

        doReturn(ObjectId.fromString(dummyHash)).when(gitService).getLastCommitHash(ArgumentMatchers.any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void updateAssessmentAfterComplaint_studentHidden() throws Exception {
        ProgrammingSubmission programmingSubmission = ParticipationFactory.generateProgrammingSubmission(true);
        programmingSubmission = programmingExerciseUtilService.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, programmingSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1", AssessmentType.SEMI_AUTOMATIC, true);
        Result programmingAssessment = programmingSubmission.getLatestResult();
        Complaint complaint = new Complaint().result(programmingAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        final var assessmentUpdate = new AssessmentUpdateDTO(feedbacks, complaintResponse, null);
        Result updatedResult = request.putWithResponseBody("/api/programming/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint",
                assessmentUpdate, Result.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
        assertThat(updatedResult.getScore()).isEqualTo(80);
        assertThat(((StudentParticipation) updatedResult.getSubmission().getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();

        // Check that result and submission are properly connected
        var submissionFromDb = programmingSubmissionRepository.findByIdWithResultsFeedbacksAssessorTestCases(programmingSubmission.getId());
        var resultFromDb = resultRepository.findWithSubmissionAndFeedbackAndTeamStudentsById(programmingAssessment.getId()).orElseThrow();
        assertThat(submissionFromDb.getLatestResult()).isEqualTo(updatedResult);
        assertThat(resultFromDb.getSubmission()).isEqualTo(updatedResult.getSubmission());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void updateAssessmentAfterComplaint_automaticAssessment_forbidden() throws Exception {
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        programmingExerciseRepository.save(programmingExercise);
        Result programmingAssessment = programmingSubmission.getLatestResult();
        Complaint complaint = new Complaint().result(programmingAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
        final var assessmentUpdate = new AssessmentUpdateDTO(new ArrayList<>(), complaintResponse, null);

        request.putWithResponseBody("/api/programming/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void updateAssessmentAfterComplaint_dueDateNotPassed_forbidden() throws Exception {
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(1));
        programmingExerciseRepository.save(programmingExercise);
        Result programmingAssessment = programmingSubmission.getLatestResult();
        Complaint complaint = new Complaint().result(programmingAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
        final var assessmentUpdate = new AssessmentUpdateDTO(new ArrayList<>(), complaintResponse, null);

        request.putWithResponseBody("/api/programming/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateAssessmentAfterComplaint_sameAsAssessor_forbidden() throws Exception {
        Result programmingAssessment = programmingSubmission.getLatestResult();
        Complaint complaint = new Complaint().result(programmingAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);

        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        final var assessmentUpdate = new AssessmentUpdateDTO(List.of(new Feedback()), complaintResponse, null);

        request.putWithResponseBody("/api/programming/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testOverrideAssessment_submitOtherTutorForbidden() throws Exception {
        overrideAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_submitInstructorPossible() throws Exception {
        overrideAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorPossible() throws Exception {
        overrideAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testOverrideAssessment_submitOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_submitInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorNoAssessmentDueDatePossible() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(programmingExercise.getId(), null);
        overrideAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void programmingExerciseManualResult_noManualReviewsAllowed_forbidden() throws Exception {
        request.put("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, HttpStatus.FORBIDDEN);
        request.put("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void programmingExerciseManualResult_submissionNotModified() throws Exception {
        ProgrammingSubmission newSubmission = new ProgrammingSubmission().commitHash("asdf");
        manualResult.setSubmission(newSubmission);
        request.put("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, HttpStatus.OK);
        var submission = programmingSubmissionRepository
                .findByIdWithResultsFeedbacksAssessorTestCases(programmingExerciseStudentParticipation.getSubmissions().iterator().next().getId());
        String commitHash = submission.getCommitHash();

        assertThat(commitHash).isEqualToIgnoringCase("123");
        assertThat(submission.getLatestResult()).isNotNull();
        assertThat(submission.getLatestResult().getScore()).isEqualTo(manualResult.getScore());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createManualProgrammingExerciseResult_save() throws Exception {
        Result response = request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult,
                Result.class, HttpStatus.OK);

        assertThat(response.getSubmission().getParticipation()).isEqualTo(manualResult.getSubmission().getParticipation());
        assertThat(response.getFeedbacks()).hasSameSizeAs(manualResult.getFeedbacks());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createManualProgrammingExerciseResult_submit() throws Exception {
        Result response = request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true",
                manualResult, Result.class, HttpStatus.OK);

        assertThat(response.getSubmission()).isNotNull();
        assertThat(response.getSubmission().getParticipation()).isEqualTo(manualResult.getSubmission().getParticipation());
        assertThat(response.getFeedbacks()).hasSameSizeAs(manualResult.getFeedbacks());
        assertThat(response.isRated()).isEqualTo(Boolean.TRUE);
        var now = ZonedDateTime.now();
        assertThat(response.getCompletionDate()).isBetween(now.minusSeconds(1), now.plusSeconds(1));

        Course course = request.get("/api/core/courses/" + programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-assessment-dashboard", HttpStatus.OK,
                Course.class);
        Exercise exercise = (Exercise) course.getExercises().toArray()[0];
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()).hasSize(1);
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(1L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createManualProgrammingExerciseResult_IncludedCompletelyWithBonusPointsExercise() throws Exception {
        // setting up exercise
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        programmingExercise.setMaxPoints(10.0);
        programmingExercise.setBonusPoints(10.0);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        manualResult.getSubmission().getParticipation().setExercise(programmingExercise);

        // setting up student submission
        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(feedbacks, 0.0, 0D);
        addAssessmentFeedbackAndCheckScore(feedbacks, -1.0, 0D);
        addAssessmentFeedbackAndCheckScore(feedbacks, 1.0, 0D);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 50D);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 100D);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 150D);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 200D);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 200D);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createManualProgrammingExerciseResult_IncludedCompletelyWithoutBonusPointsExercise() throws Exception {
        // setting up exercise
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        programmingExercise.setMaxPoints(10.0);
        programmingExercise.setBonusPoints(0.0);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        setupStudentSubmissions();
        manualResult.getSubmission().getParticipation().setExercise(programmingExercise);

    }

    private void setupStudentSubmissions() throws Exception {
        // setting up student submission
        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(feedbacks, 0.0, 0D);
        addAssessmentFeedbackAndCheckScore(feedbacks, -1.0, 0D);
        addAssessmentFeedbackAndCheckScore(feedbacks, 1.0, 0D);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 50D);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 100D);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 150D);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createManualProgrammingExerciseResult_IncludedAsBonusExercise() throws Exception {
        // setting up exercise
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        programmingExercise.setMaxPoints(10.0);
        programmingExercise.setBonusPoints(0.0);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        // setting up student submission
        setupStudentSubmissions();
        manualResult.getSubmission().getParticipation().setExercise(programmingExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createManualProgrammingExerciseResult_NotIncludedExercise() throws Exception {
        // setting up exercise
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        programmingExercise.setMaxPoints(10.0);
        programmingExercise.setBonusPoints(0.0);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        // setting up student submission
        setupStudentSubmissions();
        manualResult.getSubmission().getParticipation().setExercise(programmingExercise);
    }

    private void addAssessmentFeedbackAndCheckScore(List<Feedback> feedbacks, Double pointsAwarded, Double expectedScore) throws Exception {
        feedbacks.add(new Feedback().credits(pointsAwarded).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        manualResult.setFeedbacks(feedbacks);
        double points = manualResult.calculateTotalPointsForProgrammingExercises();
        var score = (points / programmingExercise.getMaxPoints()) * 100.0;
        manualResult.score(score);
        manualResult.rated(true);
        Result response = request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true",
                manualResult, Result.class, HttpStatus.OK);
        assertThat(response.getScore()).isEqualTo(expectedScore, Offset.offset(offsetByTenThousandth));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createManualProgrammingExerciseResult_withResultOver100Percent() throws Exception {
        List<Feedback> feedbacks = new ArrayList<>();
        // Check that result is over 100% -> 105
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 2"));
        manualResult.setFeedbacks(feedbacks);
        double points = manualResult.calculateTotalPointsForProgrammingExercises();
        // As maxScore is 100 points, 1 point is 1%
        manualResult.score(points);
        manualResult.rated(true);

        Result response = request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true",
                manualResult, Result.class, HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(105);

        // Check that result is capped to maximum of maxScore + bonus points -> 110
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 3"));
        points = manualResult.calculateTotalPointsForProgrammingExercises();
        manualResult.score(points);

        response = request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult,
                Result.class, HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(110, Offset.offset(offsetByTenThousandth));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createManualProgrammingExerciseResult_resultHasAutomaticFeedback() throws Exception {
        var testCase = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "testCase");
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().credits(1.00).type(FeedbackType.AUTOMATIC).testCase(testCase));
        feedbacks.add(new Feedback().credits(1.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        feedbacks.add(new Feedback().credits(1.00).detailText("nice submission 2"));
        feedbacks.add(new Feedback().credits(1.00).type(FeedbackType.MANUAL).detailText("nice submission 1").text("manual feedback"));

        manualResult.setFeedbacks(feedbacks);
        double points = manualResult.calculateTotalPointsForProgrammingExercises();
        // As maxScore is 100 points, 1 point is 1%
        manualResult.score(points);

        Result response = request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true",
                manualResult, Result.class, HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(4);
        assertThat(response.getFeedbacks()).anySatisfy(feedback -> {
            assertThat(feedback.getType()).isEqualTo(FeedbackType.AUTOMATIC);
            assertThat(feedback.getTestCase().getId()).isEqualTo(testCase.getId());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createManualProgrammingExerciseResult_manualResultsNotAllowed() throws Exception {
        var participation = setParticipationForProgrammingExercise(AssessmentType.AUTOMATIC);

        request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, Result.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createManualProgrammingExerciseResult_resultPropertyMissing() throws Exception {
        Result result = new Result();
        Feedback feedback = new Feedback();

        // Result rated is missing
        request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class,
                HttpStatus.BAD_REQUEST);
        result.rated(true);

        // Check that not automatically created feedbacks must have a detail text
        // Manual feedback
        result.addFeedback(feedback.type(FeedbackType.MANUAL));
        request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class,
                HttpStatus.BAD_REQUEST);
        // Unreferenced feedback
        result.removeFeedback(feedback);
        result.addFeedback(feedback.type(FeedbackType.MANUAL_UNREFERENCED));
        request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class,
                HttpStatus.BAD_REQUEST);
        // General feedback
        result.removeFeedback(feedback);
        result.addFeedback(feedback.type(null));
        request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class,
                HttpStatus.BAD_REQUEST);

        // A feedback has no points
        result.addFeedback(feedback.credits(null));
        request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateManualProgrammingExerciseResult() throws Exception {
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").submitted(true).submissionDate(ZonedDateTime.now());
        programmingSubmission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, programmingSubmission, TEST_PREFIX + "student1");
        var participation = setParticipationForProgrammingExercise(AssessmentType.SEMI_AUTOMATIC);

        resultRepository.save(manualResult);

        programmingSubmission.setParticipation(participation);
        manualResult.setSubmission(programmingSubmission);
        programmingSubmission.addResult(manualResult);

        programmingSubmissionRepository.save(programmingSubmission);

        // Result has to be manual to be updated
        manualResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);

        // Remove feedbacks, change text and score.
        manualResult.setFeedbacks(manualResult.getFeedbacks().subList(0, 1));
        double points = manualResult.calculateTotalPointsForProgrammingExercises();
        manualResult.setScore(points);
        manualResult = resultRepository.save(manualResult);

        Result response = request.putWithResponseBody("/api/programming/participations/" + participation.getId() + "/manual-results", manualResult, Result.class, HttpStatus.OK);
        assertThat(response.getScore()).isEqualTo(2);
        assertThat(response.getSubmission().getParticipation()).isEqualTo(manualResult.getSubmission().getParticipation());
        assertThat(response.getFeedbacks()).hasSameSizeAs(manualResult.getFeedbacks());

        // Submission in response is lazy loaded therefore, we fetch submission and check if relation is correct
        ProgrammingSubmission submissionFetch = programmingSubmissionRepository.findByIdWithResultsFeedbacksAssessorTestCases(programmingSubmission.getId());
        assertThat(response.getId()).isEqualTo(submissionFetch.getLatestResult().getId());
        assertThat(submissionFetch.getId()).isEqualTo(programmingSubmission.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateManualProgrammingExerciseResult_newResult() throws Exception {
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").submitted(true).submissionDate(ZonedDateTime.now());
        programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, programmingSubmission, TEST_PREFIX + "student1");

        programmingSubmission.setParticipation(programmingExerciseStudentParticipation);
        manualResult.setSubmission(programmingSubmission);

        // Remove feedbacks, change text and score.
        manualResult.setFeedbacks(manualResult.getFeedbacks().subList(0, 1));
        manualResult.setScore(77D);
        manualResult.rated(true);

        Result response = request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult,
                Result.class, HttpStatus.OK);
        assertThat(response.getSubmission().getParticipation()).isEqualTo(manualResult.getSubmission().getParticipation());
        assertThat(response.getFeedbacks()).hasSameSizeAs(manualResult.getFeedbacks());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateManualProgrammingExerciseResult_addFeedbackAfterManualLongFeedback() throws Exception {
        List<Feedback> feedbacks = new ArrayList<>();
        var manualLongFeedback = new Feedback().credits(1.00).type(FeedbackType.MANUAL_UNREFERENCED);
        manualLongFeedback.setDetailText("abc".repeat(5000));
        feedbacks.add(manualLongFeedback);

        manualResult = setUpManualResultForUpdate(feedbacks);

        // Overwrite the previous assessment with additional feedback.
        manualResult.addFeedback(new Feedback().credits(1.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        double points = manualResult.calculateTotalPointsForProgrammingExercises();
        manualResult.setScore(points);
        Result response = request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult,
                Result.class, HttpStatus.OK);

        Feedback savedAutomaticLongFeedback = response.getFeedbacks().stream().filter(Feedback::getHasLongFeedbackText).findFirst().orElse(null);

        assertThat(savedAutomaticLongFeedback).isNotNull();

        assertThat(response.getScore()).isEqualTo(2);
        assertThat(response.getFeedbacks()).anySatisfy(feedback -> {
            assertThat(feedback.getHasLongFeedbackText()).isTrue();
            assertThat(feedback.getType()).isEqualTo(FeedbackType.MANUAL_UNREFERENCED);
        });
        assertThat(savedAutomaticLongFeedback.getDetailText()).isEqualTo(manualLongFeedback.getDetailText());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldKeepExistingLongFeedbackWhenSavingAnAssessment(boolean submit) throws Exception {
        var manualLongFeedback = new Feedback().credits(0.0);
        var longText = "abc".repeat(5000);
        manualLongFeedback.setDetailText(longText);
        var result = new Result().feedbacks(List.of(manualLongFeedback)).score(0.0);
        result = resultRepository.save(result);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("submit", String.valueOf(submit));
        result = request.putWithResponseBodyAndParams("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result,
                Result.class, HttpStatus.OK, params);

        var longFeedbackText = longFeedbackTextRepository.findByFeedbackId(result.getFeedbacks().getFirst().getId());
        assertThat(longFeedbackText).isPresent();
        assertThat(longFeedbackText.get().getText()).isEqualTo(longText);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void shouldUpdateUnreferencedLongFeedbackWhenSavingAnAssessment(boolean submit) throws Exception {
        var manualLongFeedback = new Feedback().credits(0.0).type(FeedbackType.MANUAL_UNREFERENCED);
        var longText = "abc".repeat(5000);
        manualLongFeedback.setDetailText(longText);
        var result = new Result().feedbacks(List.of(manualLongFeedback)).score(0.0);
        result = resultRepository.save(result);

        var newLongText = "def".repeat(5000);
        manualLongFeedback = result.getFeedbacks().getFirst();

        // The actual complete longtext is still stored in the detailText field when the result is sent from the client
        var detailText = Feedback.class.getDeclaredField("detailText");
        detailText.setAccessible(true);
        detailText.set(manualLongFeedback, newLongText);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("submit", String.valueOf(submit));
        result = request.putWithResponseBodyAndParams("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result,
                Result.class, HttpStatus.OK, params);

        var longFeedbackText = longFeedbackTextRepository.findByFeedbackId(result.getFeedbacks().getFirst().getId());
        assertThat(longFeedbackText).isPresent();
        assertThat(longFeedbackText.get().getText()).isEqualTo(newLongText);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateManualProgrammingExerciseResult_addFeedbackAfterAutomaticLongFeedback() throws Exception {
        var testCase = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "testCase");

        List<Feedback> feedbacks = new ArrayList<>();
        var automaticLongFeedback = new Feedback().credits(1.00).type(FeedbackType.AUTOMATIC);
        automaticLongFeedback.testCase(testCase);
        automaticLongFeedback.setDetailText("abc".repeat(5000));
        automaticLongFeedback.setText("abc");
        feedbacks.add(automaticLongFeedback);

        manualResult = setUpManualResultForUpdate(feedbacks);

        // Overwrite the previous assessment with additional feedback.
        manualResult.addFeedback(new Feedback().credits(1.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        double points = manualResult.calculateTotalPointsForProgrammingExercises();
        manualResult.setScore(points);
        Result response = request.putWithResponseBody("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult,
                Result.class, HttpStatus.OK);

        Feedback savedAutomaticLongFeedback = response.getFeedbacks().stream().filter(Feedback::getHasLongFeedbackText).findFirst().orElse(null);

        assertThat(savedAutomaticLongFeedback).isNotNull();

        assertThat(response.getScore()).isEqualTo(2);
        assertThat(response.getFeedbacks()).anySatisfy(feedback -> {
            assertThat(feedback.getType()).isEqualTo(FeedbackType.AUTOMATIC);
            assertThat(feedback.getHasLongFeedbackText()).isTrue();
        });
        assertThat(savedAutomaticLongFeedback.getDetailText()).isEqualTo(automaticLongFeedback.getDetailText());
    }

    private Result setUpManualResultForUpdate(List<Feedback> feedbacks) {
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").submitted(true).submissionDate(ZonedDateTime.now());
        programmingSubmission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, programmingSubmission, TEST_PREFIX + "student1");
        var participation = setParticipationForProgrammingExercise(AssessmentType.SEMI_AUTOMATIC);

        resultRepository.save(manualResult);

        programmingSubmission.setParticipation(participation);
        manualResult.setSubmission(programmingSubmission);
        programmingSubmission.addResult(manualResult);

        programmingSubmissionRepository.save(programmingSubmission);

        // Result has to be manual to be updated
        manualResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);

        // Remove feedbacks, change text and score.
        manualResult.setFeedbacks(feedbacks);
        double points = manualResult.calculateTotalPointsForProgrammingExercises();
        manualResult.setScore(points);
        return resultRepository.save(manualResult);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void cancelOwnAssessmentAsStudent() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void cancelOwnAssessmentAsTutor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void cancelAssessmentOfOtherTutorAsTutor() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void cancelAssessmentOfOtherTutorAsInstructor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSaveAssessmentNote() throws Exception {
        AssessmentNote assessmentNote = new AssessmentNote();
        assessmentNote.setNote("note");
        manualResult.setAssessmentNote(assessmentNote);

        User user = userTestRepository.getUser();
        manualResult.setAssessor(user);

        manualResult = request.putWithResponseBody("/api/programming/participations/" + manualResult.getSubmission().getParticipation().getId() + "/manual-results", manualResult,
                Result.class, HttpStatus.OK);
        manualResult = resultRepository.findByIdWithEagerSubmissionAndFeedbackAndTestCasesAndAssessmentNoteElseThrow(manualResult.getId());
        assessmentNote = manualResult.getAssessmentNote();
        assertThat(assessmentNote.getCreatedDate()).isNotNull();
        assertThat(assessmentNote.getLastModifiedDate()).isNotNull();
    }

    private void assessmentDueDatePassed() {
        exerciseUtilService.updateAssessmentDueDate(programmingExercise.getId(), ZonedDateTime.now().minusSeconds(10));
    }

    private void overrideAssessment(HttpStatus httpStatus) throws Exception {
        var participation = programmingSubmission.getParticipation();
        var result = programmingSubmission.getLatestResult();
        assertThat(result).isNotNull();
        result.setScore(75D);
        List<Feedback> feedbacks = ParticipationFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here"))
                .collect(Collectors.toCollection(ArrayList::new));
        result.setCompletionDate(ZonedDateTime.now());
        result.setFeedbacks(feedbacks);
        programmingSubmission.setParticipation(participation);
        result.setSubmission(programmingSubmission);
        request.putWithResponseBody("/api/programming/participations/" + participation.getId() + "/manual-results", result, Result.class, httpStatus);
    }

    private ProgrammingExerciseStudentParticipation setParticipationForProgrammingExercise(AssessmentType assessmentType) {
        programmingExercise.setDueDate(ZonedDateTime.now().minusDays(1));
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        programmingExercise.setAssessmentType(assessmentType);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        return participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
    }

    private void cancelAssessment(HttpStatus expectedStatus) throws Exception {
        ProgrammingSubmission submission = programmingExerciseUtilService.createProgrammingSubmission(null, false);
        submission = programmingExerciseUtilService.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, submission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1",
                AssessmentType.AUTOMATIC, true);
        request.put("/api/programming/programming-submissions/" + submission.getId() + "/cancel-assessment", null, expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void multipleCorrectionRoundsForExam() throws Exception {
        // Setup exam with 2 correction rounds and a programming exercise
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();
        Exam exam = examUtilService.addExam(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam.addExerciseGroup(exerciseGroup1);
        exam.setVisibleDate(ZonedDateTime.now().minusHours(3));
        exam.setStartDate(ZonedDateTime.now().minusHours(2));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        exam = examRepository.save(exam);

        Exam examWithExerciseGroups = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).orElseThrow();
        exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().getFirst();
        ProgrammingExercise exercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        exercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(exercise.getBuildConfig()));
        exercise = programmingExerciseRepository.save(exercise);
        exerciseGroup1.addExercise(exercise);

        // add three user submissions with automatic results to student participation
        final var studentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        final var firstSubmission = programmingExerciseUtilService.createProgrammingSubmission(studentParticipation, true, "1");
        participationUtilService.addResultToSubmission(firstSubmission, AssessmentType.AUTOMATIC, null);
        final var secondSubmission = programmingExerciseUtilService.createProgrammingSubmission(studentParticipation, false, "2");
        participationUtilService.addResultToSubmission(secondSubmission, AssessmentType.AUTOMATIC, null);
        // The commit hash must be the same as the one used for initializing the tests because this test calls gitService.getLastCommitHash
        final var thirdSubmission = programmingExerciseUtilService.createProgrammingSubmission(studentParticipation, false, dummyHash);
        participationUtilService.addResultToSubmission(thirdSubmission, AssessmentType.AUTOMATIC, null);

        var submissionsOfParticipation = submissionRepository.findAllWithResultsAndAssessorByParticipationId(studentParticipation.getId());
        assertThat(submissionsOfParticipation).hasSize(3);
        for (final var submission : submissionsOfParticipation) {
            assertThat(submission.getResults()).isNotNull();
            assertThat(submission.getLatestResult()).isNotNull();
            assertThat(submission.getResults()).hasSize(1);
            assertThat(submission.getResults().getFirst().getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
        }

        // request to manually assess latest submission (correction round: 0)
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        params.add("correction-round", "0");
        ProgrammingSubmission submissionWithoutFirstAssessment = request.get("/api/programming/exercises/" + exercise.getId() + "/programming-submission-without-assessment",
                HttpStatus.OK, ProgrammingSubmission.class, params);
        // verify that a new submission was created
        // We want to get the third Submission, as it is the latest one, and contains an automatic result;
        assertThat(submissionWithoutFirstAssessment).isNotEqualTo(firstSubmission).isNotEqualTo(secondSubmission).isEqualTo(thirdSubmission);
        // verify that the lock has been set
        assertThat(submissionWithoutFirstAssessment.getLatestResult()).isNotNull();
        assertThat(submissionWithoutFirstAssessment.getLatestResult().getAssessor().getLogin()).isEqualTo(TEST_PREFIX + "tutor1");
        assertThat(submissionWithoutFirstAssessment.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);

        // make sure that new result correctly appears inside the continue box
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1Tutor1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1Tutor1.add("assessedByTutor", "true");
        paramsGetAssessedCR1Tutor1.add("correction-round", "0");
        var assessedSubmissionList = request.getList("/api/programming/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.getFirst().getResultForCorrectionRound(0)).isEqualTo(submissionWithoutFirstAssessment.getLatestResult());

        // assess submission and submit
        var manualResultLockedFirstRound = submissionWithoutFirstAssessment.getLatestResult();
        List<Feedback> feedbacks = ParticipationFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here"))
                .collect(Collectors.toCollection(ArrayList::new));
        manualResultLockedFirstRound.setFeedbacks(feedbacks);
        manualResultLockedFirstRound.setRated(true);
        manualResultLockedFirstRound.setScore(80D);

        params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        params.add("correction-round", "0");

        Result firstSubmittedManualResult = request.putWithResponseBodyAndParams("/api/programming/participations/" + studentParticipation.getId() + "/manual-results",
                manualResultLockedFirstRound, Result.class, HttpStatus.OK, params);

        // make sure that new result correctly appears after the assessment for first correction round
        assessedSubmissionList = request.getList("/api/programming/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.getFirst().getResultForCorrectionRound(0)).isEqualTo(firstSubmittedManualResult);

        // change the user here, so that for the next query the result will show up again.
        // set to true, if a tutor is only able to assess a submission if he has not assessed it any prior correction rounds
        firstSubmittedManualResult.setAssessor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        resultRepository.save(firstSubmittedManualResult);
        assertThat(firstSubmittedManualResult.getAssessor().getLogin()).isEqualTo(TEST_PREFIX + "instructor1");

        // verify that the result contains the relationship
        assertThat(firstSubmittedManualResult).isNotNull();
        assertThat(firstSubmittedManualResult.getSubmission()).isEqualTo(submissionWithoutFirstAssessment);
        assertThat(firstSubmittedManualResult.getSubmission().getParticipation()).isEqualTo(studentParticipation);

        // verify that the relationship between student participation,
        var databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        var fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(3);
        assertThat(fetchedParticipation.findLatestSubmission()).contains(submissionWithoutFirstAssessment);
        assertThat(fetchedParticipation.findLatestResult()).isEqualTo(firstSubmittedManualResult);

        var databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository
                .findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.getFirst();
        assertThat(fetchedParticipation.getSubmissions()).hasSize(3);
        assertThat(fetchedParticipation.findLatestSubmission()).isPresent();
        // it should contain the latest automatic result, and the lock for the manual result
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getResults()).hasSize(2);
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getLatestResult()).isEqualTo(firstSubmittedManualResult);

        // SECOND ROUND OF CORRECTION
        LinkedMultiValueMap<String, String> paramsSecondCorrection = new LinkedMultiValueMap<>();
        paramsSecondCorrection.add("lock", "true");
        paramsSecondCorrection.add("correction-round", "1");

        final var submissionWithoutSecondAssessment = request.get("/api/programming/exercises/" + exercise.getId() + "/programming-submission-without-assessment", HttpStatus.OK,
                ProgrammingSubmission.class, paramsSecondCorrection);

        // verify that the submission is not new
        // it should contain the latest automatic result, and the first manual result, and the lock for the second manual result
        assertThat(submissionWithoutSecondAssessment).isNotEqualTo(firstSubmission).isNotEqualTo(secondSubmission).isEqualTo(thirdSubmission)
                .isEqualTo(submissionWithoutFirstAssessment);
        // verify that the lock has been set
        assertThat(submissionWithoutSecondAssessment.getLatestResult()).isNotNull();
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessor().getLogin()).isEqualTo(TEST_PREFIX + "tutor1");
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);

        // verify that the relationship between student participation,
        databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(3);
        assertThat(fetchedParticipation.findLatestSubmission()).contains(submissionWithoutSecondAssessment);
        assertThat(participationUtilService.getResultsForParticipation(fetchedParticipation).stream().filter(result -> result.getCompletionDate() == null).findFirst())
                .contains(submissionWithoutSecondAssessment.getLatestResult());

        databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.getFirst();
        assertThat(fetchedParticipation.getSubmissions()).hasSize(3);
        assertThat(fetchedParticipation.findLatestSubmission()).isPresent();
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getResults()).hasSize(3);
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getLatestResult()).isEqualTo(submissionWithoutSecondAssessment.getLatestResult());

        // assess submission and submit
        final var manualResultLockedSecondRound = submissionWithoutSecondAssessment.getLatestResult();
        assertThat(manualResultLockedFirstRound).isNotEqualTo(manualResultLockedSecondRound);
        manualResultLockedSecondRound.setFeedbacks(feedbacks);
        manualResultLockedSecondRound.setRated(true);
        manualResultLockedSecondRound.setScore(90D);

        paramsSecondCorrection.add("lock", "true");

        var secondSubmittedManualResult = request.putWithResponseBodyAndParams("/api/programming/participations/" + studentParticipation.getId() + "/manual-results",
                manualResultLockedSecondRound, Result.class, HttpStatus.OK, paramsSecondCorrection);
        assertThat(secondSubmittedManualResult).isNotNull();

        // make sure that new result correctly appears after the assessment for second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR2 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR2.add("assessedByTutor", "true");
        paramsGetAssessedCR2.add("correction-round", "1");
        assessedSubmissionList = request.getList("/api/programming/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class,
                paramsGetAssessedCR2);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().getId()).isEqualTo(submissionWithoutSecondAssessment.getId());
        assertThat(assessedSubmissionList.getFirst().getResultForCorrectionRound(1)).isEqualTo(manualResultLockedSecondRound);

        // make sure that they do not appear for the first correction round as the tutor only assessed the second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1.add("assessedByTutor", "true");
        paramsGetAssessedCR1.add("correction-round", "0");
        assessedSubmissionList = request.getList("/api/programming/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class,
                paramsGetAssessedCR1);

        assertThat(assessedSubmissionList).isEmpty();

        // Student should not have received a result over WebSocket as manual correction is ongoing
        verify(websocketMessagingService, never()).sendMessageToUser(notNull(), eq(Constants.NEW_RESULT_TOPIC), isA(ResultDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void overrideProgrammingAssessmentAfterComplaint() throws Exception {
        User student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElse(null);

        // Starting participation
        StudentParticipation participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise, student1);
        studentParticipationRepository.save(participation);

        // Creating submission
        ProgrammingSubmission programmingSubmission = ParticipationFactory.generateProgrammingSubmission(true);
        programmingSubmission.setType(SubmissionType.MANUAL);
        programmingSubmission.setParticipation(participation);
        programmingSubmission.setSubmitted(Boolean.TRUE);
        programmingSubmission.setSubmissionDate(ZonedDateTime.now());
        programmingSubmission = submissionRepository.save(programmingSubmission);

        // assess this submission
        User tutor1 = userTestRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElse(null);
        Result initialResult = ParticipationFactory.generateResult(true, 50);
        initialResult.setAssessor(tutor1);
        initialResult.setHasComplaint(true);
        initialResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        initialResult = resultRepository.save(initialResult);

        programmingSubmission.addResult(initialResult);
        initialResult.setSubmission(programmingSubmission);
        programmingSubmission = submissionRepository.save(programmingSubmission);

        // complaining
        Complaint complaint = new Complaint().result(initialResult).complaintText("This is not fair");
        complaint = complaintRepo.save(complaint);

        // Creating complaint response
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(true);
        complaintResponse.setResponseText("accepted");
        List<Feedback> complaintFeedback = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(complaintFeedback, 40.0, 40D);
        addAssessmentFeedbackAndCheckScore(complaintFeedback, 30.0, 70D);
        addAssessmentFeedbackAndCheckScore(complaintFeedback, 30.0, 100D);
        final var assessmentUpdate = new AssessmentUpdateDTO(complaintFeedback, complaintResponse, null);

        // update assessment after Complaint, now 100%
        Result resultAfterComplaint = request.putWithResponseBody("/api/programming/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint",
                assessmentUpdate, Result.class, HttpStatus.OK);
        Double resultAfterComplaintScore = resultAfterComplaint.getScore();

        // Now, override the complaint response with another assessment -> now 10%
        List<Feedback> overrideFeedback = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(overrideFeedback, 10.0, 10D);
        assertThat(resultAfterComplaint).isNotNull();
        resultAfterComplaint.setFeedbacks(overrideFeedback);
        resultAfterComplaint.setRated(true);
        resultAfterComplaint.setScore(10D);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        Result overwrittenResult = request.putWithResponseBodyAndParams("/api/programming/participations/" + programmingSubmission.getParticipation().getId() + "/manual-results",
                resultAfterComplaint, Result.class, HttpStatus.OK, params);
        initialResult = resultRepository.findById(initialResult.getId()).orElseThrow();

        assertThat(overwrittenResult).isEqualTo(resultAfterComplaint); // check if the Id is identical
        assertThat(initialResult.getScore()).isEqualTo(50D); // first result has a score of 50%
        assertThat(resultAfterComplaintScore).isEqualTo(100D); // score after complaint evaluation got changed to 100% (which is in a new result now)
        assertThat(overwrittenResult.getScore()).isEqualTo(10D); // the instructor overwrote the score to 10%
        assertThat(overwrittenResult.hasComplaint()).isFalse(); // The result has no complaint, as it is the answer for one
        assertThat(initialResult.hasComplaint()).isTrue(); // Very important: It must not be overwritten whether the result actually had a complaint

        // Also check that it's correctly saved in the database
        ProgrammingSubmission savedSubmission = programmingSubmissionRepository.findWithEagerResultsById(programmingSubmission.getId()).orElse(null);
        assertThat(savedSubmission).isNotNull();
        assertThat(savedSubmission.getLatestResult().getScore()).isEqualTo(10D);
        assertThat(savedSubmission.getFirstManualResult().hasComplaint()).isTrue();
        assertThat(savedSubmission.getLatestResult().hasComplaint()).isFalse();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void unlockFeedbackRequestAfterAssessment() throws Exception {
        programmingExercise.setAllowFeedbackRequests(true);
        programmingExercise.setDueDate(ZonedDateTime.now().plusDays(1));
        programmingExerciseRepository.save(programmingExercise);

        ZonedDateTime individualDueDate = ZonedDateTime.now();
        programmingExerciseStudentParticipation.setIndividualDueDate(individualDueDate);
        studentParticipationRepository.save(programmingExerciseStudentParticipation);

        Result result = programmingExerciseStudentParticipation.getSubmissions().stream().findFirst().orElseThrow().getFirstResult();
        assertThat(result).isNotNull();
        result.setScore(100D);
        resultRepository.save(result);

        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", "true");
        var responseResult = request.putWithResponseBodyAndParams("/api/programming/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result,
                Result.class, HttpStatus.OK, params);

        var responseParticipation = (ProgrammingExerciseStudentParticipation) responseResult.getSubmission().getParticipation();
        assertThat(responseParticipation.getIndividualDueDate()).isCloseTo(individualDueDate, within(1, ChronoUnit.MILLIS));
        // TODO: add some meaningful assertions here related to the feedback request
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteResult() throws Exception {
        Course course = exerciseUtilService.addCourseWithOneExerciseAndSubmissions(TEST_PREFIX, "modeling", 1,
                Optional.of(TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json")));
        Exercise exercise = exerciseRepository.findAllExercisesByCourseId(course.getId()).stream().findFirst().orElseThrow();

        exerciseUtilService.addAutomaticAssessmentToExercise(exercise);
        exerciseUtilService.addAutomaticAssessmentToExercise(exercise);
        exerciseUtilService.addAutomaticAssessmentToExercise(exercise);
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor2"));

        var submissions = participationUtilService.getAllSubmissionsOfExercise(exercise);
        Submission submission = submissions.getFirst();
        assertThat(submission.getResults()).hasSize(5);
        Result firstResult = submission.getResults().getFirst();
        Result midResult = submission.getResults().get(2);
        Result firstSemiAutomaticResult = submission.getResults().get(3);

        Result lastResult = submission.getLatestResult();
        // we will only delete the middle automatic result at index 2
        request.delete(
                "/api/programming/participations/" + submission.getParticipation().getId() + "/programming-submissions/" + submission.getId() + "/results/" + midResult.getId(),
                HttpStatus.OK);
        submission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submission.getId());
        assertThat(submission.getResults()).hasSize(4);
        assertThat(submission.getResults().getFirst()).isEqualTo(firstResult);
        assertThat(submission.getResults().get(2)).isEqualTo(firstSemiAutomaticResult);
        assertThat(submission.getResults().get(3)).isEqualTo(submission.getLatestResult()).isEqualTo(lastResult);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TUTOR")
    void deleteAssessmentOfOtherTutorAsTutor() throws Exception {
        deleteAssessmentAsForbiddenUser();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void deleteAssessmentAsTutor() throws Exception {
        deleteAssessmentAsForbiddenUser();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void deleteOwnAssessmentAsStudent() throws Exception {
        deleteAssessmentAsForbiddenUser();
    }

    private void deleteAssessmentAsForbiddenUser() throws Exception {
        ProgrammingSubmission submission = programmingExerciseUtilService.createProgrammingSubmission(null, false);
        submission = programmingExerciseUtilService.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, submission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1",
                AssessmentType.AUTOMATIC, true);
        assertThat(submission.getResults()).hasSize(1);

        request.delete("/api/programming/participations/" + submission.getParticipation().getId() + "/programming-submissions/" + submission.getId() + "/results/"
                + submission.getFirstResult().getId(), HttpStatus.FORBIDDEN);

        assertThat(submission.getResults()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteAssessmentAsInstructor() throws Exception {
        Course course = exerciseUtilService.addCourseWithOneExerciseAndSubmissions(TEST_PREFIX, "programming", 1, Optional.empty());
        Exercise exercise = exerciseRepository.findAllExercisesByCourseId(course.getId()).stream().findFirst().orElseThrow();

        exerciseUtilService.addAutomaticAssessmentToExercise(exercise);
        exerciseUtilService.addAutomaticAssessmentToExercise(exercise);
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));

        var submissions = participationUtilService.getAllSubmissionsOfExercise(exercise);
        Submission submission = submissions.getFirst();
        Result resultToDelete = submission.getResults().get(0);
        Result secondResult = submission.getResults().get(1);
        Result thirdResult = submission.getResults().get(2);
        assertThat(submission.getResults()).hasSize(3);

        request.delete("/api/programming/participations/" + submission.getParticipation().getId() + "/programming-submissions/" + submission.getId() + "/results/"
                + resultToDelete.getId(), HttpStatus.OK);

        submission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submission.getId());
        assertThat(submission.getResults()).hasSize(2);
        assertThat(submission.getResults().get(0)).isEqualTo(secondResult);
        assertThat(submission.getResults().get(1)).isEqualTo(thirdResult);
    }
}
