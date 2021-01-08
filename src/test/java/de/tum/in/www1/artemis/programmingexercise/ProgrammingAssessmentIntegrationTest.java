package de.tum.in.www1.artemis.programmingexercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.ProgrammingAssessmentService;
import de.tum.in.www1.artemis.util.ModelFactory;

public class ProgrammingAssessmentIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    ComplaintRepository complaintRepo;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    ProgrammingAssessmentService programmingAssessmentService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission programmingSubmission;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private Result manualResult;

    @BeforeEach
    void initTestCase() {
        database.addUsers(3, 2, 2);
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

        manualResult = ModelFactory.generateResult(true, 90).participation(programmingExerciseStudentParticipation);
        List<Feedback> feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here")).collect(Collectors.toList());
        manualResult.setFeedbacks(feedbacks);
        manualResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        manualResult.rated(true);

        double points = programmingAssessmentService.calculateTotalScore(manualResult);
        manualResult.resultString("3 of 3 passed, 1 issue, " + manualResult.createResultString(points, programmingExercise.getMaxScore()));

        String dummyHash = "9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d";
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

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
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
        var submissionFromDb = programmingSubmissionService.findByIdWithEagerResultAndFeedback(programmingSubmission.getId());
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

        ComplaintResponse complaintResponse = new ComplaintResponse().complaint(complaint.accepted(false)).responseText("rejected");
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

        ProgrammingSubmission submission = programmingSubmissionService
                .findByIdWithEagerResultAndFeedback(programmingExerciseStudentParticipation.getSubmissions().iterator().next().getId());
        String commitHash = submission.getCommitHash();

        assertThat("123".equalsIgnoreCase(commitHash));
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
        assertThat(response.isRated().equals(Boolean.TRUE));
        assertThat(response.getCompletionDate().equals(ZonedDateTime.now()));
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_IncludedCompletelyWithBonusPointsExercise() throws Exception {
        // setting up exercise
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        programmingExercise.setMaxScore(10.0);
        programmingExercise.setBonusPoints(10.0);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        manualResult.getParticipation().setExercise(programmingExercise);

        // setting up student submission
        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 150L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 200L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 200L);

    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_IncludedCompletelyWithoutBonusPointsExercise() throws Exception {
        // setting up exercise
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        programmingExercise.setMaxScore(10.0);
        programmingExercise.setBonusPoints(0.0);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        manualResult.getParticipation().setExercise(programmingExercise);

        // setting up student submission
        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 100L);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_IncludedAsBonusExercise() throws Exception {
        // setting up exercise
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        programmingExercise.setMaxScore(10.0);
        programmingExercise.setBonusPoints(0.0);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        manualResult.getParticipation().setExercise(programmingExercise);

        // setting up student submission
        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 100L);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createManualProgrammingExerciseResult_NotIncludedExercise() throws Exception {
        // setting up exercise
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        programmingExercise.setMaxScore(10.0);
        programmingExercise.setBonusPoints(0.0);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        manualResult.getParticipation().setExercise(programmingExercise);

        // setting up student submission
        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(feedbacks, 5.0, 100L);
        // TODO: check for isRated() == false?
    }

    private void addAssessmentFeedbackAndCheckScore(List<Feedback> feedbacks, Double pointsAwarded, Long expectedScore) throws Exception {
        feedbacks.add(new Feedback().credits(pointsAwarded).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        manualResult.setFeedbacks(feedbacks);
        double points = programmingAssessmentService.calculateTotalScore(manualResult);
        long score = (long) ((points / programmingExercise.getMaxScore()) * 100.0);
        manualResult.resultString(manualResult.createResultString(points, programmingExercise.getMaxScore()));
        manualResult.score(score);
        manualResult.rated(true);
        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult,
                Result.class, HttpStatus.OK);
        assertThat(response.getScore()).isEqualTo(expectedScore);
        double maxPoints = programmingExercise.getMaxScore();
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
        double points = programmingAssessmentService.calculateTotalScore(manualResult);
        manualResult.resultString("3 of 3 passed, 1 issue, " + manualResult.createResultString(points, programmingExercise.getMaxScore()));
        // As maxScore is 100 points, 1 point is 1%
        manualResult.score((long) points);
        manualResult.rated(true);

        Result response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult,
                Result.class, HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(105);
        assertThat(response.getResultString()).isEqualTo("3 of 3 passed, 1 issue, 105 of 100 points");

        // Check that result is capped to maximum of maxScore + bonus points -> 110
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 3"));
        points = programmingAssessmentService.calculateTotalScore(manualResult);
        manualResult.score((long) points);
        manualResult.resultString("3 of 3 passed, 1 issue, " + manualResult.createResultString(points, programmingExercise.getMaxScore()));

        response = request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results?submit=true", manualResult, Result.class,
                HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(110);
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
        double points = programmingAssessmentService.calculateTotalScore(manualResult);
        // As maxScore is 100 points, 1 point is 1%
        manualResult.score((long) points);
        manualResult.resultString("3 of 3 passed, 1 issue, " + manualResult.createResultString(points, programmingExercise.getMaxScore()));

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
                "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.   \n"
                        + "Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat.   \n"
                        + "Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat. Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore...");
        request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);

        // Result score is missing
        result.setResultString("Good work here");
        request.putWithResponseBody("/api/participations/" + programmingExerciseStudentParticipation.getId() + "/manual-results", result, Result.class, HttpStatus.BAD_REQUEST);
        result.setScore(100L);

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
        double points = programmingAssessmentService.calculateTotalScore(manualResult);
        manualResult.setScore((long) points);
        manualResult.resultString("3 of 3 passed, 1 issue, " + manualResult.createResultString(points, programmingExercise.getMaxScore()));
        manualResult = resultRepository.save(manualResult);

        Result response = request.putWithResponseBody("/api/participations/" + participation.getId() + "/manual-results", manualResult, Result.class, HttpStatus.OK);
        assertThat(response.getScore()).isEqualTo(2);
        assertThat(response.getResultString()).isEqualTo("3 of 3 passed, 1 issue, 2 of 100 points");
        assertThat(response.getParticipation()).isEqualTo(manualResult.getParticipation());
        assertThat(response.getFeedbacks().size()).isEqualTo(manualResult.getFeedbacks().size());

        // Submission in response is lazy loaded therefore, we fetch submission and check if relation is correct
        ProgrammingSubmission submissionFetch = programmingSubmissionService.findByIdWithEagerResultAndFeedback(programmingSubmission.getId());
        assertThat(response.getId().equals(submissionFetch.getLatestResult().getId()));
        assertThat(submissionFetch.getId().equals(programmingSubmission.getId()));
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
        manualResult.setScore(77L);
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
        result.setScore(75L);
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
}
