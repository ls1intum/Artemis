package de.tum.in.www1.artemis.exercise.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.assertj.core.data.Offset;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.assessment.ComplaintUtilService;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.TestResourceUtils;
import de.tum.in.www1.artemis.web.rest.dto.ResultDTO;

class ProgrammingAssessmentIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "programmingassessment";

    private final String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";

    private final Double offsetByTenThousandth = 0.0001;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ComplaintUtilService complaintUtilService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission programmingSubmission;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private Result manualResult;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 3, 2, 0, 2);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        exerciseUtilService.addMaxScoreAndBonusPointsToExercise(programmingExercise);
        programmingSubmission = ParticipationFactory.generateProgrammingSubmission(true);
        programmingSubmission = programmingExerciseUtilService.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, programmingSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1", AssessmentType.SEMI_AUTOMATIC, true);

        programmingExerciseStudentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        // A new manual result and submission are created during the locking of submission for manual assessment
        // The new result has an assessment type, automatic feedbacks and assessor
        var automaticFeedback = new Feedback().credits(0.0).detailText("asdfasdf").type(FeedbackType.AUTOMATIC).text("asdf");
        var automaticFeedbacks = new ArrayList<Feedback>();
        automaticFeedbacks.add(automaticFeedback);
        var newManualResult = participationUtilService.addResultToParticipation(AssessmentType.SEMI_AUTOMATIC, null, programmingExerciseStudentParticipation,
                TEST_PREFIX + "tutor1", automaticFeedbacks);
        programmingExerciseStudentParticipation.addResult(newManualResult);
        // Set submission of newResult
        programmingExerciseUtilService.addProgrammingSubmissionToResultAndParticipation(newManualResult, programmingExerciseStudentParticipation, "123");

        List<Feedback> feedbacks = ParticipationFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here"))
                .collect(Collectors.toCollection(ArrayList::new));
        manualResult = ParticipationFactory.generateResult(true, 90D).participation(programmingExerciseStudentParticipation);
        manualResult.setFeedbacks(feedbacks);
        manualResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        manualResult.rated(true);

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
        complaint.getResult().setParticipation(null); // Break infinite reference chain
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        AssessmentUpdate assessmentUpdate = new AssessmentUpdate();
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        assessmentUpdate.setFeedbacks(feedbacks);

        assessmentUpdate.setComplaintResponse(complaintResponse);

        Result updatedResult = request.putWithResponseBody("/api/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
        assertThat(updatedResult.getScore()).isEqualTo(80);
        assertThat(((StudentParticipation) updatedResult.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();

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
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(new ArrayList<>()).complaintResponse(complaintResponse);

        request.putWithResponseBody("/api/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
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
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(new ArrayList<>()).complaintResponse(complaintResponse);

        request.putWithResponseBody("/api/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateAssessmentAfterComplaint_sameAsAssessor_forbidden() throws Exception {
        Result programmingAssessment = programmingSubmission.getLatestResult();
        Complaint complaint = new Complaint().result(programmingAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(new ArrayList<>()).complaintResponse(complaintResponse);

        request.putWithResponseBody("/api/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
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
        request.put("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, HttpStatus.FORBIDDEN);
        request.put("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void programmingExerciseManualResult_submissionNotModified() throws Exception {
        ProgrammingSubmission newSubmission = new ProgrammingSubmission().commitHash("asdf");
        manualResult.setSubmission(newSubmission);
        request.put("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, HttpStatus.OK);
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
        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, Result.class,
                HttpStatus.OK);

        assertThat(response.getParticipation()).isEqualTo(manualResult.getParticipation());
        assertThat(response.getFeedbacks()).hasSameSizeAs(manualResult.getFeedbacks());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createManualProgrammingExerciseResult_submit() throws Exception {
        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult,
                Result.class, HttpStatus.OK);

        assertThat(response.getSubmission()).isNotNull();
        assertThat(response.getParticipation()).isEqualTo(manualResult.getParticipation());
        assertThat(response.getFeedbacks()).hasSameSizeAs(manualResult.getFeedbacks());
        assertThat(response.isRated()).isEqualTo(Boolean.TRUE);
        var now = ZonedDateTime.now();
        assertThat(response.getCompletionDate()).isBetween(now.minusSeconds(1), now.plusSeconds(1));

        Course course = request.get("/api/courses/" + programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-assessment-dashboard", HttpStatus.OK,
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
        manualResult.getParticipation().setExercise(programmingExercise);

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
        manualResult.getParticipation().setExercise(programmingExercise);
        setupStudentSubmissions();

    }

    private void setupStudentSubmissions() throws Exception {
        // setting up student submission
        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(feedbacks, 0.0, 0D);
        addAssessmentFeedbackAndCheckScore(feedbacks, -1.0, 0D);
        addAssessmentFeedbackAndCheckScore(feedbacks, 1.0, 0D);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 50D);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 100D);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 100D);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createManualProgrammingExerciseResult_IncludedAsBonusExercise() throws Exception {
        // setting up exercise
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        programmingExercise.setMaxPoints(10.0);
        programmingExercise.setBonusPoints(0.0);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        manualResult.getParticipation().setExercise(programmingExercise);

        // setting up student submission
        setupStudentSubmissions();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createManualProgrammingExerciseResult_NotIncludedExercise() throws Exception {
        // setting up exercise
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        programmingExercise.setMaxPoints(10.0);
        programmingExercise.setBonusPoints(0.0);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        manualResult.getParticipation().setExercise(programmingExercise);

        // setting up student submission
        setupStudentSubmissions();
    }

    private void addAssessmentFeedbackAndCheckScore(List<Feedback> feedbacks, Double pointsAwarded, Double expectedScore) throws Exception {
        feedbacks.add(new Feedback().credits(pointsAwarded).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        manualResult.setFeedbacks(feedbacks);
        double points = manualResult.calculateTotalPointsForProgrammingExercises();
        var score = (points / programmingExercise.getMaxPoints()) * 100.0;
        manualResult.score(score);
        manualResult.rated(true);
        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult,
                Result.class, HttpStatus.OK);
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

        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult,
                Result.class, HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(105);

        // Check that result is capped to maximum of maxScore + bonus points -> 110
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 3"));
        points = manualResult.calculateTotalPointsForProgrammingExercises();
        manualResult.score(points);

        response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult, Result.class,
                HttpStatus.OK);

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

        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult,
                Result.class, HttpStatus.OK);

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
        manualResult.setParticipation(participation);

        request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, Result.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createManualProgrammingExerciseResult_resultPropertyMissing() throws Exception {
        Result result = new Result();
        Feedback feedback = new Feedback();

        // Result rated is missing
        request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);
        result.rated(true);

        // Check that not automatically created feedbacks must have a detail text
        // Manual feedback
        result.addFeedback(feedback.type(FeedbackType.MANUAL));
        request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);
        // Unreferenced feedback
        result.removeFeedback(feedback);
        result.addFeedback(feedback.type(FeedbackType.MANUAL_UNREFERENCED));
        request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);
        // General feedback
        result.removeFeedback(feedback);
        result.addFeedback(feedback.type(null));
        request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);

        // A feedback has no points
        result.addFeedback(feedback.credits(null));
        request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateManualProgrammingExerciseResult() throws Exception {
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").submitted(true).submissionDate(ZonedDateTime.now());
        programmingSubmission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, programmingSubmission, TEST_PREFIX + "student1");
        var participation = setParticipationForProgrammingExercise(AssessmentType.SEMI_AUTOMATIC);

        resultRepository.save(manualResult);

        manualResult.setParticipation(participation);
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

        Result response = request.putWithResponseBody("/api/participations/" + participation.getId() + "/manual-results", manualResult, Result.class, HttpStatus.OK);
        assertThat(response.getScore()).isEqualTo(2);
        assertThat(response.getParticipation()).isEqualTo(manualResult.getParticipation());
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

        manualResult.setParticipation(programmingExerciseStudentParticipation);
        manualResult.setSubmission(programmingSubmission);

        // Remove feedbacks, change text and score.
        manualResult.setFeedbacks(manualResult.getFeedbacks().subList(0, 1));
        manualResult.setScore(77D);
        manualResult.rated(true);

        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, Result.class,
                HttpStatus.OK);
        assertThat(response.getParticipation()).isEqualTo(manualResult.getParticipation());
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
        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, Result.class,
                HttpStatus.OK);

        Feedback savedAutomaticLongFeedback = response.getFeedbacks().stream().filter(Feedback::getHasLongFeedbackText).findFirst().orElse(null);

        assertThat(savedAutomaticLongFeedback).isNotNull();

        // Retrieve long feedback text with id.
        String longFeedbackText = request.get(String.format("/api/results/%d/feedbacks/%d/long-feedback", response.getId(), savedAutomaticLongFeedback.getId()), HttpStatus.OK,
                String.class);

        assertThat(response.getScore()).isEqualTo(2);
        assertThat(response.getFeedbacks()).anySatisfy(feedback -> {
            assertThat(feedback.getHasLongFeedbackText()).isTrue();
            assertThat(feedback.getType()).isEqualTo(FeedbackType.MANUAL_UNREFERENCED);
        });
        assertThat(longFeedbackText).isEqualTo(manualLongFeedback.getLongFeedback().map(LongFeedbackText::getText).orElse(""));
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
        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, Result.class,
                HttpStatus.OK);

        Feedback savedAutomaticLongFeedback = response.getFeedbacks().stream().filter(Feedback::getHasLongFeedbackText).findFirst().orElse(null);

        assertThat(savedAutomaticLongFeedback).isNotNull();

        // Retrieve long feedback text with id.
        String longFeedbackText = request.get(String.format("/api/results/%d/feedbacks/%d/long-feedback", response.getId(), savedAutomaticLongFeedback.getId()), HttpStatus.OK,
                String.class);

        assertThat(response.getScore()).isEqualTo(2);
        assertThat(response.getFeedbacks()).anySatisfy(feedback -> {
            assertThat(feedback.getType()).isEqualTo(FeedbackType.AUTOMATIC);
            assertThat(feedback.getHasLongFeedbackText()).isTrue();
        });
        assertThat(longFeedbackText).isEqualTo(automaticLongFeedback.getLongFeedback().map(LongFeedbackText::getText).orElse(""));
    }

    private Result setUpManualResultForUpdate(List<Feedback> feedbacks) {
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").submitted(true).submissionDate(ZonedDateTime.now());
        programmingSubmission = programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, programmingSubmission, TEST_PREFIX + "student1");
        var participation = setParticipationForProgrammingExercise(AssessmentType.SEMI_AUTOMATIC);

        resultRepository.save(manualResult);

        manualResult.setParticipation(participation);
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
        result.setParticipation(participation);
        result.setSubmission(programmingSubmission);
        request.putWithResponseBody("/api/participations/" + participation.getId() + "/manual-results", result, Result.class, httpStatus);
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
        request.put("/api/programming-submissions/" + submission.getId() + "/cancel-assessment", null, expectedStatus);
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
        exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().get(0);
        ProgrammingExercise exercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup1);
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
            assertThat(submission.getResults().get(0).getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
        }

        // request to manually assess latest submission (correction round: 0)
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        params.add("correction-round", "0");
        ProgrammingSubmission submissionWithoutFirstAssessment = request.get("/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment", HttpStatus.OK,
                ProgrammingSubmission.class, params);
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
        var assessedSubmissionList = request.getList("/api/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(0)).isEqualTo(submissionWithoutFirstAssessment.getLatestResult());

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

        Result firstSubmittedManualResult = request.putWithResponseBodyAndParams("/api/participations/" + studentParticipation.getId() + "/manual-results",
                manualResultLockedFirstRound, Result.class, HttpStatus.OK, params);

        // make sure that new result correctly appears after the assessment for first correction round
        assessedSubmissionList = request.getList("/api/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(0)).isEqualTo(firstSubmittedManualResult);

        // change the user here, so that for the next query the result will show up again.
        // set to true, if a tutor is only able to assess a submission if he has not assessed it any prior correction rounds
        firstSubmittedManualResult.setAssessor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        resultRepository.save(firstSubmittedManualResult);
        assertThat(firstSubmittedManualResult.getAssessor().getLogin()).isEqualTo(TEST_PREFIX + "instructor1");

        // verify that the result contains the relationship
        assertThat(firstSubmittedManualResult).isNotNull();
        assertThat(firstSubmittedManualResult.getSubmission()).isEqualTo(submissionWithoutFirstAssessment);
        assertThat(firstSubmittedManualResult.getParticipation()).isEqualTo(studentParticipation);

        // verify that the relationship between student participation,
        var databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerLegalSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        var fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(3);
        assertThat(fetchedParticipation.findLatestSubmission()).contains(submissionWithoutFirstAssessment);
        assertThat(fetchedParticipation.findLatestLegalResult()).isEqualTo(firstSubmittedManualResult);

        var databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository
                .findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.get(0);
        assertThat(fetchedParticipation.getSubmissions()).hasSize(3);
        assertThat(fetchedParticipation.findLatestSubmission()).isPresent();
        // it should contain the latest automatic result, and the lock for the manual result
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getResults()).hasSize(2);
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getLatestResult()).isEqualTo(firstSubmittedManualResult);

        // SECOND ROUND OF CORRECTION
        LinkedMultiValueMap<String, String> paramsSecondCorrection = new LinkedMultiValueMap<>();
        paramsSecondCorrection.add("lock", "true");
        paramsSecondCorrection.add("correction-round", "1");

        final var submissionWithoutSecondAssessment = request.get("/api/exercises/" + exercise.getId() + "/programming-submission-without-assessment", HttpStatus.OK,
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
        databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerLegalSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(3);
        assertThat(fetchedParticipation.findLatestSubmission()).contains(submissionWithoutSecondAssessment);
        assertThat(fetchedParticipation.getResults().stream().filter(x -> x.getCompletionDate() == null).findFirst()).contains(submissionWithoutSecondAssessment.getLatestResult());

        databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.get(0);
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

        var secondSubmittedManualResult = request.putWithResponseBodyAndParams("/api/participations/" + studentParticipation.getId() + "/manual-results",
                manualResultLockedSecondRound, Result.class, HttpStatus.OK, paramsSecondCorrection);
        assertThat(secondSubmittedManualResult).isNotNull();

        // make sure that new result correctly appears after the assessment for second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR2 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR2.add("assessedByTutor", "true");
        paramsGetAssessedCR2.add("correction-round", "1");
        assessedSubmissionList = request.getList("/api/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class,
                paramsGetAssessedCR2);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutSecondAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(1)).isEqualTo(manualResultLockedSecondRound);

        // make sure that they do not appear for the first correction round as the tutor only assessed the second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1.add("assessedByTutor", "true");
        paramsGetAssessedCR1.add("correction-round", "0");
        assessedSubmissionList = request.getList("/api/exercises/" + exercise.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class,
                paramsGetAssessedCR1);

        assertThat(assessedSubmissionList).isEmpty();

        // Student should not have received a result over WebSocket as manual correction is ongoing
        verify(websocketMessagingService, never()).sendMessageToUser(notNull(), eq(Constants.NEW_RESULT_TOPIC), isA(ResultDTO.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void overrideProgrammingAssessmentAfterComplaint() throws Exception {
        User student1 = userRepository.findOneByLogin(TEST_PREFIX + "student1").orElse(null);

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
        User tutor1 = userRepository.findOneByLogin(TEST_PREFIX + "tutor1").orElse(null);
        Result initialResult = ParticipationFactory.generateResult(true, 50);
        initialResult.setAssessor(tutor1);
        initialResult.setHasComplaint(true);
        initialResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        initialResult.setParticipation(participation);
        initialResult = resultRepository.save(initialResult);

        programmingSubmission.addResult(initialResult);
        initialResult.setSubmission(programmingSubmission);
        programmingSubmission = submissionRepository.save(programmingSubmission);

        // complaining
        Complaint complaint = new Complaint().result(initialResult).complaintText("This is not fair");
        complaint = complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        // Creating complaint response
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(true);
        complaintResponse.setResponseText("accepted");
        List<Feedback> complaintFeedback = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(complaintFeedback, 40.0, 40D);
        addAssessmentFeedbackAndCheckScore(complaintFeedback, 30.0, 70D);
        addAssessmentFeedbackAndCheckScore(complaintFeedback, 30.0, 100D);
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(complaintFeedback).complaintResponse(complaintResponse);

        // update assessment after Complaint, now 100%
        Result resultAfterComplaint = request.putWithResponseBody("/api/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);
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
        Result overwrittenResult = request.putWithResponseBodyAndParams("/api/participations/" + programmingSubmission.getParticipation().getId() + "/manual-results",
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
        programmingExercise.setAllowManualFeedbackRequests(true);
        programmingExercise.setDueDate(ZonedDateTime.now().plusDays(1));
        exerciseRepository.save(programmingExercise);

        var participation = programmingExerciseStudentParticipation;
        participation.setIndividualDueDate(ZonedDateTime.now().minusDays(1));
        studentParticipationRepository.save(participation);

        Result result = participation.getResults().stream().findFirst().orElseThrow();
        result.setScore(100D);
        resultRepository.save(result);

        doNothing().when(programmingExerciseParticipationService).unlockStudentRepositoryAndParticipation(participation);

        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", "true");
        var response = request.putWithResponseBodyAndParams("/api/participations/" + participation.getId() + "/manual-results", result, Result.class, HttpStatus.OK, params);

        var responseParticipation = (ProgrammingExerciseStudentParticipation) response.getParticipation();
        assertThat(responseParticipation.getIndividualDueDate()).isNull();
        assertThat(responseParticipation.isLocked()).isFalse();

        verify(programmingExerciseParticipationService).unlockStudentRepositoryAndParticipation(participation);
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
        Submission submission = submissions.get(0);
        assertThat(submission.getResults()).hasSize(5);
        Result firstResult = submission.getResults().get(0);
        Result midResult = submission.getResults().get(2);
        Result firstSemiAutomaticResult = submission.getResults().get(3);

        Result lastResult = submission.getLatestResult();
        // we will only delete the middle automatic result at index 2
        request.delete("/api/participations/" + submission.getParticipation().getId() + "/programming-submissions/" + submission.getId() + "/results/" + midResult.getId(),
                HttpStatus.OK);
        submission = submissionRepository.findOneWithEagerResultAndFeedback(submission.getId());
        assertThat(submission.getResults()).hasSize(4);
        assertThat(submission.getResults().get(0)).isEqualTo(firstResult);
        assertThat(submission.getResults().get(2)).isEqualTo(firstSemiAutomaticResult);
        assertThat(submission.getResults().get(3)).isEqualTo(submission.getLatestResult()).isEqualTo(lastResult);
    }
}
