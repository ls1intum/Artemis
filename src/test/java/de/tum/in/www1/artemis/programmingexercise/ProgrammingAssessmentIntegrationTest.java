package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.assertj.core.data.Offset;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.programming.ProgrammingAssessmentService;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ModelFactory;

public class ProgrammingAssessmentIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ProgrammingAssessmentService programmingAssessmentService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserRepository userRepository;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission programmingSubmission;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private Result manualResult;

    private final String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";

    private final Double offsetByTenThousandth = 0.0001;

    @BeforeEach
    void initTestCase() {
        database.addUsers(3, 2, 0, 2);
        database.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = programmingExerciseRepository.findAllWithEagerParticipations().get(0);
        programmingExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().minusDays(1));
        database.addMaxScoreAndBonusPointsToExercise(programmingExercise);
        programmingSubmission = ModelFactory.generateProgrammingSubmission(true);
        programmingSubmission = database.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, programmingSubmission, "student1", "tutor1",
                AssessmentType.SEMI_AUTOMATIC, true);

        programmingExerciseStudentParticipation = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");
        // A new manual result and submission are created during the locking of submission for manual assessment
        // The new result has a result string, assessment type, automatic feedbacks and assessor
        var automaticFeedback = new Feedback().credits(0.0).detailText("asdfasdf").type(FeedbackType.AUTOMATIC).text("asdf");
        var automaticFeedbacks = new ArrayList<Feedback>();
        automaticFeedbacks.add(automaticFeedback);
        var newManualResult = database.addResultToParticipation(AssessmentType.SEMI_AUTOMATIC, null, programmingExerciseStudentParticipation, "2 of 3 passed", "tutor1",
                automaticFeedbacks);
        programmingExerciseStudentParticipation.addResult(newManualResult);
        // Set submission of newResult
        database.addProgrammingSubmissionToResultAndParticipation(newManualResult, programmingExerciseStudentParticipation, "123");

        manualResult = ModelFactory.generateResult(true, 90D).participation(programmingExerciseStudentParticipation);
        List<Feedback> feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here")).collect(Collectors.toList());
        manualResult.setFeedbacks(feedbacks);
        manualResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        manualResult.rated(true);

        double points = resultRepository.calculateTotalPointsForProgrammingExercise(manualResult);
        manualResult.resultString("3 of 3 passed, 1 issue, " + manualResult.createResultString(points, programmingExercise.getMaxPoints()));

        doReturn(ObjectId.fromString(dummyHash)).when(gitService).getLastCommitHash(ArgumentMatchers.any());
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void updateAssessmentAfterComplaint_studentHidden() throws Exception {
        ProgrammingSubmission programmingSubmission = ModelFactory.generateProgrammingSubmission(true);
        programmingSubmission = database.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, programmingSubmission, "student1", "tutor1",
                AssessmentType.SEMI_AUTOMATIC, true);
        Result programmingAssessment = programmingSubmission.getLatestResult();
        Complaint complaint = new Complaint().result(programmingAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain
        ComplaintResponse complaintResponse = database.createInitialEmptyResponse("tutor2", complaint);
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
        assertThat(updatedResult.getResultString()).isEqualTo("1 of 13 passed, 1 issue, 80 of 100 points");
        assertThat(((StudentParticipation) updatedResult.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();

        // Check that result and submission are properly connected
        var submissionFromDb = programmingSubmissionRepository.findByIdWithResultsFeedbacksAssessor(programmingSubmission.getId());
        var resultFromDb = resultRepository.findWithEagerSubmissionAndFeedbackById(programmingAssessment.getId()).get();
        assertThat(submissionFromDb.getLatestResult()).isEqualTo(updatedResult);
        assertThat(resultFromDb.getSubmission()).isEqualTo(updatedResult.getSubmission());
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void updateAssessmentAfterComplaint_automaticAssessment_forbidden() throws Exception {
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
    @WithMockUser(value = "tutor2", roles = "TA")
    public void updateAssessmentAfterComplaint_dueDateNotPassed_forbidden() throws Exception {
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
    @WithMockUser(value = "tutor1", roles = "TA")
    public void updateAssessmentAfterComplaint_sameAsAssessor_forbidden() throws Exception {
        Result programmingAssessment = programmingSubmission.getLatestResult();
        Complaint complaint = new Complaint().result(programmingAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        ComplaintResponse complaintResponse = database.createInitialEmptyResponse("tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(new ArrayList<>()).complaintResponse(complaintResponse);

        request.putWithResponseBody("/api/programming-submissions/" + programmingSubmission.getId() + "/assessment-after-complaint", assessmentUpdate, Result.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void testOverrideAssessment_submitOtherTutorForbidden() throws Exception {
        overrideAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_submitInstructorPossible() throws Exception {
        overrideAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorPossible() throws Exception {
        overrideAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void testOverrideAssessment_submitOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_submitInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorNoAssessmentDueDatePossible() throws Exception {
        database.updateAssessmentDueDate(programmingExercise.getId(), null);
        overrideAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void programmingExerciseManualResult_noManualReviewsAllowed_forbidden() throws Exception {
        request.put("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, HttpStatus.FORBIDDEN);
        request.put("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void programmingExerciseManualResult_submissionNotModified() throws Exception {
        ProgrammingSubmission newSubmission = new ProgrammingSubmission().commitHash("asdf");
        manualResult.setSubmission(newSubmission);
        request.put("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, HttpStatus.OK);
        var submission = programmingSubmissionRepository.findByIdWithResultsFeedbacksAssessor(programmingExerciseStudentParticipation.getSubmissions().iterator().next().getId());
        String commitHash = submission.getCommitHash();

        assertThat("123").isEqualToIgnoringCase(commitHash);
        assertThat(submission.getLatestResult()).isNotNull();
        assertThat(submission.getLatestResult().getResultString()).isEqualTo(manualResult.getResultString());
        assertThat(submission.getLatestResult().getScore()).isEqualTo(manualResult.getScore());

    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_save() throws Exception {
        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, Result.class,
                HttpStatus.OK);

        assertThat(response.getResultString()).isEqualTo("3 of 3 passed, 1 issue, 2 of 100 points");
        assertThat(response.getParticipation()).isEqualTo(manualResult.getParticipation());
        assertThat(response.getFeedbacks().size()).isEqualTo(manualResult.getFeedbacks().size());
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_submit() throws Exception {
        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult,
                Result.class, HttpStatus.OK);

        assertThat(response.getResultString()).isEqualTo("3 of 3 passed, 1 issue, 2 of 100 points");
        assertThat(response.getSubmission()).isNotNull();
        assertThat(response.getParticipation()).isEqualTo(manualResult.getParticipation());
        assertThat(response.getFeedbacks().size()).isEqualTo(manualResult.getFeedbacks().size());
        assertThat(response.isRated()).isEqualTo(Boolean.TRUE);
        var now = ZonedDateTime.now();
        assertThat(response.getCompletionDate()).isBetween(now.minusSeconds(1), now.plusSeconds(1));

        Course course = request.get("/api/courses/" + programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-assessment-dashboard", HttpStatus.OK,
                Course.class);
        Exercise exercise = (Exercise) course.getExercises().toArray()[0];
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds().length).isEqualTo(1L);
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(1L);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_IncludedCompletelyWithBonusPointsExercise() throws Exception {
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
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_IncludedCompletelyWithoutBonusPointsExercise() throws Exception {
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
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_IncludedAsBonusExercise() throws Exception {
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
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_NotIncludedExercise() throws Exception {
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
        double points = resultRepository.calculateTotalPointsForProgrammingExercise(manualResult);
        var score = (points / programmingExercise.getMaxPoints()) * 100.0;
        manualResult.resultString(manualResult.createResultString(points, programmingExercise.getMaxPoints()));
        manualResult.score(score);
        manualResult.rated(true);
        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult,
                Result.class, HttpStatus.OK);
        assertThat(response.getScore()).isEqualTo(expectedScore, Offset.offset(offsetByTenThousandth));
        double maxPoints = programmingExercise.getMaxPoints();
        assertThat(response.getResultString()).isEqualTo((int) points + " of " + (int) maxPoints + " points");
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_withResultOver100Percent() throws Exception {
        List<Feedback> feedbacks = new ArrayList<>();
        // Check that result is over 100% -> 105
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 2"));
        manualResult.setFeedbacks(feedbacks);
        double points = resultRepository.calculateTotalPointsForProgrammingExercise(manualResult);
        manualResult.resultString("3 of 3 passed, 1 issue, " + manualResult.createResultString(points, programmingExercise.getMaxPoints()));
        // As maxScore is 100 points, 1 point is 1%
        manualResult.score(points);
        manualResult.rated(true);

        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult,
                Result.class, HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(105);
        assertThat(response.getResultString()).isEqualTo("3 of 3 passed, 1 issue, 105 of 100 points");

        // Check that result is capped to maximum of maxScore + bonus points -> 110
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 3"));
        points = resultRepository.calculateTotalPointsForProgrammingExercise(manualResult);
        manualResult.score(points);
        manualResult.resultString("3 of 3 passed, 1 issue, " + manualResult.createResultString(points, programmingExercise.getMaxPoints()));

        response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult, Result.class,
                HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(110, Offset.offset(offsetByTenThousandth));
        assertThat(response.getResultString()).isEqualTo("3 of 3 passed, 1 issue, 110 of 100 points");
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_resultHasAutomaticFeedback() throws Exception {
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().credits(1.00).type(FeedbackType.AUTOMATIC));
        feedbacks.add(new Feedback().credits(1.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        feedbacks.add(new Feedback().credits(1.00).detailText("nice submission 2"));
        feedbacks.add(new Feedback().credits(1.00).type(FeedbackType.MANUAL).detailText("nice submission 1").text("manual feedback"));

        manualResult.setFeedbacks(feedbacks);
        double points = resultRepository.calculateTotalPointsForProgrammingExercise(manualResult);
        // As maxScore is 100 points, 1 point is 1%
        manualResult.score(points);
        manualResult.resultString("3 of 3 passed, 1 issue, " + manualResult.createResultString(points, programmingExercise.getMaxPoints()));

        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult,
                Result.class, HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(4);
        assertThat(response.getResultString()).isEqualTo("3 of 3 passed, 1 issue, 4 of 100 points");
        assertThat(response.getFeedbacks().stream().anyMatch(feedback -> feedback.getType().equals(FeedbackType.AUTOMATIC))).isTrue();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_manualResultsNotAllowed() throws Exception {
        var participation = setParticipationForProgrammingExercise(AssessmentType.AUTOMATIC);
        manualResult.setParticipation(participation);

        request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, Result.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_resultPropertyMissing() throws Exception {
        Result result = new Result();
        Feedback feedback = new Feedback();

        // Result rated is missing
        request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);
        result.rated(true);

        // Result string is missing
        request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);

        // Result string is too long
        result.setResultString(
                """
                        Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.  \s
                        Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat.  \s
                        Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat. Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore...""");
        request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);

        // Result score is missing
        result.setResultString("Good work here");
        request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);
        result.setScore(100D);

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
    @WithMockUser(value = "tutor1", roles = "TA")
    public void updateManualProgrammingExerciseResult() throws Exception {
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").submitted(true).submissionDate(ZonedDateTime.now());
        programmingSubmission = database.addProgrammingSubmission(programmingExercise, programmingSubmission, "student1");
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
        double points = resultRepository.calculateTotalPointsForProgrammingExercise(manualResult);
        manualResult.setScore(points);
        manualResult.resultString("3 of 3 passed, 1 issue, " + manualResult.createResultString(points, programmingExercise.getMaxPoints()));
        manualResult = resultRepository.save(manualResult);

        Result response = request.putWithResponseBody("/api/participations/" + participation.getId() + "/manual-results", manualResult, Result.class, HttpStatus.OK);
        assertThat(response.getScore()).isEqualTo(2);
        assertThat(response.getResultString()).isEqualTo("3 of 3 passed, 1 issue, 2 of 100 points");
        assertThat(response.getParticipation()).isEqualTo(manualResult.getParticipation());
        assertThat(response.getFeedbacks().size()).isEqualTo(manualResult.getFeedbacks().size());

        // Submission in response is lazy loaded therefore, we fetch submission and check if relation is correct
        ProgrammingSubmission submissionFetch = programmingSubmissionRepository.findByIdWithResultsFeedbacksAssessor(programmingSubmission.getId());
        assertThat(response.getId()).isEqualTo(submissionFetch.getLatestResult().getId());
        assertThat(submissionFetch.getId()).isEqualTo(programmingSubmission.getId());
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void updateManualProgrammingExerciseResult_newResult() throws Exception {
        ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash("abc").submitted(true).submissionDate(ZonedDateTime.now());
        database.addProgrammingSubmission(programmingExercise, programmingSubmission, "student1");

        manualResult.setParticipation(programmingExerciseStudentParticipation);
        manualResult.setSubmission(programmingSubmission);

        // Remove feedbacks, change text and score.
        manualResult.setFeedbacks(manualResult.getFeedbacks().subList(0, 1));
        manualResult.setScore(77D);
        manualResult.resultString("2 of 3 passed, 77 of 100 points");
        manualResult.rated(true);

        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", manualResult, Result.class,
                HttpStatus.OK);
        assertThat(response.getResultString()).isEqualTo("2 of 3 passed, 77 of 100 points");
        assertThat(response.getParticipation()).isEqualTo(manualResult.getParticipation());
        assertThat(response.getFeedbacks().size()).isEqualTo(manualResult.getFeedbacks().size());
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void cancelOwnAssessmentAsStudent() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void cancelOwnAssessmentAsTutor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void cancelAssessmentOfOtherTutorAsTutor() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void cancelAssessmentOfOtherTutorAsInstructor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    private void assessmentDueDatePassed() {
        database.updateAssessmentDueDate(programmingExercise.getId(), ZonedDateTime.now().minusSeconds(10));
    }

    private void overrideAssessment(HttpStatus httpStatus) throws Exception {
        var participation = programmingSubmission.getParticipation();
        var result = programmingSubmission.getLatestResult();
        assertThat(result).isNotNull();
        result.setScore(75D);
        List<Feedback> feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here")).collect(Collectors.toList());
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
        return database.addStudentParticipationForProgrammingExercise(programmingExercise, "student1");
    }

    private void cancelAssessment(HttpStatus expectedStatus) throws Exception {
        ProgrammingSubmission submission = database.createProgrammingSubmission(null, false);
        submission = database.addProgrammingSubmissionWithResultAndAssessor(programmingExercise, submission, "student1", "tutor1", AssessmentType.AUTOMATIC, true);
        request.put("/api/programming-submissions/" + submission.getId() + "/cancel-assessment", null, expectedStatus);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void multipleCorrectionRoundsForExam() throws Exception {
        // Setup exam with 2 correction rounds and a programming exercise
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();
        Exam exam = database.addExam(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam.addExerciseGroup(exerciseGroup1);
        exam.setVisibleDate(ZonedDateTime.now().minusHours(3));
        exam.setStartDate(ZonedDateTime.now().minusHours(2));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        exam = examRepository.save(exam);

        Exam examWithExerciseGroups = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).get();
        exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().get(0);
        ProgrammingExercise exercise = ModelFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        exercise = programmingExerciseRepository.save(exercise);
        exerciseGroup1.addExercise(exercise);

        // add three user submissions with automatic results to student participation
        final var studentParticipation = database.addStudentParticipationForProgrammingExercise(exercise, "student1");
        final var firstSubmission = database.createProgrammingSubmission(studentParticipation, true, "1");
        database.addResultToSubmission(firstSubmission, AssessmentType.AUTOMATIC, null);
        final var secondSubmission = database.createProgrammingSubmission(studentParticipation, false, "2");
        database.addResultToSubmission(secondSubmission, AssessmentType.AUTOMATIC, null);
        // The commit hash must be the same as the one used for initializing the tests because this test calls gitService.getLastCommitHash
        final var thirdSubmission = database.createProgrammingSubmission(studentParticipation, false, dummyHash);
        database.addResultToSubmission(thirdSubmission, AssessmentType.AUTOMATIC, null);

        // verify setup
        assertThat(exam.getNumberOfCorrectionRoundsInExam()).isEqualTo(2);
        assertThat(exam.getEndDate()).isBefore(ZonedDateTime.now());
        var optionalFetchedExercise = programmingExerciseRepository.findWithAllParticipationsById(exercise.getId());
        assertThat(optionalFetchedExercise.isPresent()).isTrue();
        final var exerciseWithParticipation = optionalFetchedExercise.get();
        var submissionsOfParticipation = submissionRepository
                .findAllWithResultsAndAssessorByParticipationId(exerciseWithParticipation.getStudentParticipations().stream().iterator().next().getId());
        assertThat(submissionsOfParticipation.size()).isEqualTo(3);
        for (final var submission : submissionsOfParticipation) {
            assertThat(submission.getResults()).isNotNull();
            assertThat(submission.getLatestResult()).isNotNull();
            assertThat(submission.getResults().size()).isEqualTo(1);
            assertThat(submission.getResults().get(0).getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
        }

        // request to manually assess latest submission (correction round: 0)
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        params.add("correction-round", "0");
        ProgrammingSubmission submissionWithoutFirstAssessment = request.get("/api/exercises/" + exerciseWithParticipation.getId() + "/programming-submission-without-assessment",
                HttpStatus.OK, ProgrammingSubmission.class, params);
        // verify that a new submission was created
        assertThat(submissionWithoutFirstAssessment).isNotEqualTo(firstSubmission);
        assertThat(submissionWithoutFirstAssessment).isNotEqualTo(secondSubmission);
        // We want to get the third Submission, as it is the latest one, and contains a automatic result;
        assertThat(submissionWithoutFirstAssessment).isEqualTo(thirdSubmission);
        // verify that the lock has been set
        assertThat(submissionWithoutFirstAssessment.getLatestResult()).isNotNull();
        assertThat(submissionWithoutFirstAssessment.getLatestResult().getAssessor().getLogin()).isEqualTo("tutor1");
        assertThat(submissionWithoutFirstAssessment.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);

        // make sure that new result correctly appears inside the continue box
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1Tutor1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1Tutor1.add("assessedByTutor", "true");
        paramsGetAssessedCR1Tutor1.add("correction-round", "0");
        var assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList.size()).isEqualTo(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(0)).isEqualTo(submissionWithoutFirstAssessment.getLatestResult());

        // assess submission and submit
        var manualResultLockedFirstRound = submissionWithoutFirstAssessment.getLatestResult();
        List<Feedback> feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here")).collect(Collectors.toList());
        manualResultLockedFirstRound.setFeedbacks(feedbacks);
        manualResultLockedFirstRound.setRated(true);
        manualResultLockedFirstRound.setHasFeedback(true);
        manualResultLockedFirstRound.setScore(80D);

        params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        params.add("correction-round", "0");

        Result firstSubmittedManualResult = request.putWithResponseBodyAndParams("/api/participations/" + studentParticipation.getId() + "/manual-results",
                manualResultLockedFirstRound, Result.class, HttpStatus.OK, params);

        // make sure that new result correctly appears after the assessment for first correction round
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList.size()).isEqualTo(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(0)).isEqualTo(firstSubmittedManualResult);

        // change the user here, so that for the next query the result will show up again.
        // set to true, if a tutor is only able to assess a submission if he has not assessed it any prior correction rounds
        firstSubmittedManualResult.setAssessor(database.getUserByLogin("instructor1"));
        resultRepository.save(firstSubmittedManualResult);
        assertThat(firstSubmittedManualResult.getAssessor().getLogin()).isEqualTo("instructor1");

        // verify that the result contains the relationship
        assertThat(firstSubmittedManualResult).isNotNull();
        assertThat(firstSubmittedManualResult.getSubmission()).isEqualTo(submissionWithoutFirstAssessment);
        assertThat(firstSubmittedManualResult.getParticipation()).isEqualTo(studentParticipation);

        // verify that the relationship between student participation,
        var databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerLegalSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation.isPresent()).isTrue();
        var fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions().size()).isEqualTo(3);
        assertThat(fetchedParticipation.findLatestSubmission().isPresent()).isTrue();
        assertThat(fetchedParticipation.findLatestSubmission().get()).isEqualTo(submissionWithoutFirstAssessment);
        assertThat(fetchedParticipation.findLatestLegalResult()).isEqualTo(firstSubmittedManualResult);

        var databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository
                .findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission.size()).isEqualTo(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.get(0);
        assertThat(fetchedParticipation.getSubmissions().size()).isEqualTo(3);
        assertThat(fetchedParticipation.findLatestSubmission().isPresent()).isTrue();
        // it should contain the latest automatic result, and the lock for the manual result
        assertThat(fetchedParticipation.findLatestSubmission().get().getResults().size()).isEqualTo(2);
        assertThat(fetchedParticipation.findLatestSubmission().get().getLatestResult()).isEqualTo(firstSubmittedManualResult);

        // SECOND ROUND OF CORRECTION
        LinkedMultiValueMap<String, String> paramsSecondCorrection = new LinkedMultiValueMap<>();
        paramsSecondCorrection.add("lock", "true");
        paramsSecondCorrection.add("correction-round", "1");

        final var submissionWithoutSecondAssessment = request.get("/api/exercises/" + exerciseWithParticipation.getId() + "/programming-submission-without-assessment",
                HttpStatus.OK, ProgrammingSubmission.class, paramsSecondCorrection);

        // verify that the submission is not new
        assertThat(submissionWithoutSecondAssessment).isNotEqualTo(firstSubmission);
        assertThat(submissionWithoutSecondAssessment).isNotEqualTo(secondSubmission);
        // it should contain the latest automatic result, and the first manual result, and the lock for the second manual result
        assertThat(submissionWithoutSecondAssessment).isEqualTo(thirdSubmission);
        assertThat(submissionWithoutSecondAssessment).isEqualTo(submissionWithoutFirstAssessment);
        // verify that the lock has been set
        assertThat(submissionWithoutSecondAssessment.getLatestResult()).isNotNull();
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessor().getLogin()).isEqualTo("tutor1");
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);

        // verify that the relationship between student participation,
        databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerLegalSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation.isPresent()).isTrue();
        fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions().size()).isEqualTo(3);
        assertThat(fetchedParticipation.findLatestSubmission().isPresent()).isTrue();
        assertThat(fetchedParticipation.findLatestSubmission().get()).isEqualTo(submissionWithoutSecondAssessment);
        assertThat(fetchedParticipation.getResults().stream().filter(x -> x.getCompletionDate() == null).findFirst().get())
                .isEqualTo(submissionWithoutSecondAssessment.getLatestResult());

        databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission.size()).isEqualTo(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.get(0);
        assertThat(fetchedParticipation.getSubmissions().size()).isEqualTo(3);
        assertThat(fetchedParticipation.findLatestSubmission().isPresent()).isTrue();
        assertThat(fetchedParticipation.findLatestSubmission().get().getResults().size()).isEqualTo(3);
        assertThat(fetchedParticipation.findLatestSubmission().get().getLatestResult()).isEqualTo(submissionWithoutSecondAssessment.getLatestResult());

        // assess submission and submit
        final var manualResultLockedSecondRound = submissionWithoutSecondAssessment.getLatestResult();
        assertThat(manualResultLockedFirstRound).isNotEqualTo(manualResultLockedSecondRound);
        manualResultLockedSecondRound.setFeedbacks(feedbacks);
        manualResultLockedSecondRound.setHasFeedback(true);
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
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class,
                paramsGetAssessedCR2);

        assertThat(assessedSubmissionList.size()).isEqualTo(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutSecondAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(1)).isEqualTo(manualResultLockedSecondRound);

        // make sure that they do not appear for the first correction round as the tutor only assessed the second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1.add("assessedByTutor", "true");
        paramsGetAssessedCR1.add("correction-round", "0");
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/programming-submissions", HttpStatus.OK, ProgrammingSubmission.class,
                paramsGetAssessedCR1);

        assertThat(assessedSubmissionList.size()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void overrideProgrammingAssessmentAfterComplaint() throws Exception {
        User student1 = userRepository.findOneByLogin("student1").orElse(null);

        // Starting participation
        StudentParticipation participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise, student1);
        studentParticipationRepository.save(participation);

        // Creating submission
        ProgrammingSubmission programmingSubmission = ModelFactory.generateProgrammingSubmission(true);
        programmingSubmission.setType(SubmissionType.MANUAL);
        programmingSubmission.setParticipation(participation);
        programmingSubmission.setSubmitted(Boolean.TRUE);
        programmingSubmission.setSubmissionDate(ZonedDateTime.now());
        programmingSubmission = submissionRepository.save(programmingSubmission);

        // assess this submission
        User tutor1 = userRepository.findOneByLogin("tutor1").orElse(null);
        Result initialResult = ModelFactory.generateResult(true, 50);
        initialResult.setAssessor(tutor1);
        initialResult.setHasComplaint(true);
        initialResult.setHasFeedback(false);
        initialResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        initialResult.setParticipation(participation);
        initialResult.setResultString(50.0, 100.0);
        initialResult = resultRepository.save(initialResult);

        programmingSubmission.addResult(initialResult);
        initialResult.setSubmission(programmingSubmission);
        programmingSubmission = submissionRepository.save(programmingSubmission);

        // complaining
        Complaint complaint = new Complaint().result(initialResult).complaintText("This is not fair");
        complaint = complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        // Creating complaint response
        ComplaintResponse complaintResponse = database.createInitialEmptyResponse("tutor2", complaint);
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
        resultAfterComplaint.setHasFeedback(true);
        resultAfterComplaint.setScore(10D);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        Result overwrittenResult = request.putWithResponseBodyAndParams("/api/participations/" + programmingSubmission.getParticipation().getId() + "/manual-results",
                resultAfterComplaint, Result.class, HttpStatus.OK, params);

        assertThat(initialResult.getScore()).isEqualTo(50D); // first result was instantiated with a score of 50%
        assertThat(resultAfterComplaintScore).isEqualTo(100D); // score after complaint evaluation got changed to 100%
        assertThat(overwrittenResult.getScore()).isEqualTo(10D); // the instructor overwrote the score to 10%
        assertThat(overwrittenResult.hasComplaint()).isEqualTo(true); // Very important: It must not be overwritten whether the result actually had a complaint

        // Also check that its correctly saved in the database
        ProgrammingSubmission savedSubmission = programmingSubmissionRepository.findWithEagerResultsById(programmingSubmission.getId()).orElse(null);
        assertThat(savedSubmission).isNotNull();
        assertThat(savedSubmission.getLatestResult().getScore()).isEqualTo(10D);
        assertThat(savedSubmission.getLatestResult().hasComplaint()).isEqualTo(true);

    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testdeleteResult() throws Exception {
        Course course = database.addCourseWithOneExerciseAndSubmissions("modeling", 1, Optional.of(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json")));
        Exercise exercise = exerciseRepository.findAllExercisesByCourseId(course.getId()).stream().toList().get(0);

        database.addAutomaticAssessmentToExercise(exercise);
        database.addAutomaticAssessmentToExercise(exercise);
        database.addAutomaticAssessmentToExercise(exercise);
        database.addAssessmentToExercise(exercise, database.getUserByLogin("tutor1"));
        database.addAssessmentToExercise(exercise, database.getUserByLogin("tutor2"));

        var submissions = database.getAllSubmissionsOfExercise(exercise);
        Submission submission = submissions.get(0);
        assertThat(submission.getResults().size()).isEqualTo(5);
        Result firstResult = submission.getResults().get(0);
        Result midResult = submission.getResults().get(2);
        Result firstSemiAutomaticResult = submission.getResults().get(3);

        Result lastResult = submission.getLatestResult();
        // we will only delte the middle automatic result at index 2
        request.delete("/api/programming-submissions/" + submission.getId() + "/delete/" + midResult.getId(), HttpStatus.OK);
        submission = submissionRepository.findOneWithEagerResultAndFeedback(submission.getId());
        assertThat(submission.getResults().size()).isEqualTo(4);
        assertThat(submission.getResults().get(0)).isEqualTo(firstResult);
        assertThat(submission.getResults().get(2)).isEqualTo(firstSemiAutomaticResult);
        assertThat(submission.getResults().get(3)).isEqualTo(submission.getLatestResult()).isEqualTo(lastResult);
    }
}
