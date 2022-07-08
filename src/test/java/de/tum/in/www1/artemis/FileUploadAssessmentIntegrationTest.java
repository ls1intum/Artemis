package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FileUploadAssessmentIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    public static final String API_FILE_UPLOAD_SUBMISSIONS = "/api/file-upload-submissions/";

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    private FileUploadExercise afterReleaseFileUploadExercise;

    private Course course;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 2, 0, 1);
        course = database.addCourseWithThreeFileUploadExercise();
        afterReleaseFileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public List<Feedback> exerciseWithSGI() throws Exception {
        database.addGradingInstructionsToExercise(afterReleaseFileUploadExercise);
        FileUploadExercise receivedFileUploadExercise = request.putWithResponseBody("/api/file-upload-exercises/" + afterReleaseFileUploadExercise.getId(),
                afterReleaseFileUploadExercise, FileUploadExercise.class, HttpStatus.OK);
        return ModelFactory.applySGIonFeedback(receivedFileUploadExercise);
    }

    @Order(1)
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSubmitFileUploadAssessment_asInstructor() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(afterReleaseFileUploadExercise, fileUploadSubmission, "student1");

        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", "true");
        List<Feedback> feedbacks = exerciseWithSGI();

        Result result = request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/feedback", feedbacks, Result.class, HttpStatus.OK,
                params);

        assertThat(result).as("submitted result found").isNotNull();
        assertThat(result.isRated()).isTrue();
        assertThat(result.getScore()).isEqualTo(60); // total score 3P (60%) because gradingInstructionWithLimit was applied twice but only counts once
        assertThat(result.getFeedbacks()).hasSize(4);
        assertThat(result.getFeedbacks().get(0).getCredits()).isEqualTo(feedbacks.get(0).getCredits());
        assertThat(result.getFeedbacks().get(1).getCredits()).isEqualTo(feedbacks.get(1).getCredits());

        Course course = request.get("/api/courses/" + afterReleaseFileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-assessment-dashboard", HttpStatus.OK,
                Course.class);
        Exercise exercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()).hasSize(1);
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(1L);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testManualAssessmentSubmit_IncludedCompletelyWithBonusPointsExercise() throws Exception {
        // setting up exercise
        afterReleaseFileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        afterReleaseFileUploadExercise.setMaxPoints(10.0);
        afterReleaseFileUploadExercise.setBonusPoints(10.0);
        exerciseRepository.save(afterReleaseFileUploadExercise);

        // setting up student submission
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(true);
        submission = database.addFileUploadSubmission(afterReleaseFileUploadExercise, submission, "student1");
        List<Feedback> feedbacks = new ArrayList<>();

        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 150L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 200L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 200L);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testManualAssessmentSubmit_IncludedCompletelyWithoutBonusPointsExercise() throws Exception {
        // setting up exercise
        afterReleaseFileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        afterReleaseFileUploadExercise.setMaxPoints(10.0);
        afterReleaseFileUploadExercise.setBonusPoints(0.0);
        exerciseRepository.save(afterReleaseFileUploadExercise);

        // setting up student submission
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(true);
        submission = database.addFileUploadSubmission(afterReleaseFileUploadExercise, submission, "student1");
        List<Feedback> feedbacks = new ArrayList<>();

        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 100L);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testManualAssessmentSubmit_IncludedAsBonusExercise() throws Exception {
        // setting up exercise
        afterReleaseFileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        afterReleaseFileUploadExercise.setMaxPoints(10.0);
        afterReleaseFileUploadExercise.setBonusPoints(0.0);
        exerciseRepository.save(afterReleaseFileUploadExercise);

        // setting up student submission
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(true);
        submission = database.addFileUploadSubmission(afterReleaseFileUploadExercise, submission, "student1");
        List<Feedback> feedbacks = new ArrayList<>();

        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 100L);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testManualAssessmentSubmit_NotIncludedExercise() throws Exception {
        // setting up exercise
        afterReleaseFileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        afterReleaseFileUploadExercise.setMaxPoints(10.0);
        afterReleaseFileUploadExercise.setBonusPoints(0.0);
        exerciseRepository.save(afterReleaseFileUploadExercise);

        // setting up student submission
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(true);
        submission = database.addFileUploadSubmission(afterReleaseFileUploadExercise, submission, "student1");
        List<Feedback> feedbacks = new ArrayList<>();

        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 100L);
    }

    public void addAssessmentFeedbackAndCheckScore(FileUploadSubmission fileUploadSubmission, List<Feedback> feedbacks, double pointsAwarded, long expectedScore) throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", "true");
        feedbacks.add(new Feedback().credits(pointsAwarded).type(FeedbackType.MANUAL_UNREFERENCED).detailText("gj"));
        Result response = request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/feedback", feedbacks, Result.class, HttpStatus.OK,
                params);
        assertThat(response.getScore()).isEqualTo(expectedScore);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testSubmitFileUploadAssessment_withResultOver100Percent() throws Exception {
        afterReleaseFileUploadExercise = (FileUploadExercise) database.addMaxScoreAndBonusPointsToExercise(afterReleaseFileUploadExercise);
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(afterReleaseFileUploadExercise, fileUploadSubmission, "student1");

        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", "true");
        List<Feedback> feedbacks = new ArrayList<>();
        // Check that result is over 100% -> 105
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 2"));
        Result response = request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/feedback", feedbacks, Result.class, HttpStatus.OK,
                params);

        assertThat(response.getScore()).isEqualTo(105);

        // Check that result is capped to maximum of maxScore + bonus points -> 110
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 3"));
        response = request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/feedback", feedbacks, Result.class, HttpStatus.OK, params);
        Double offsetByTenThousandth = 0.0001;
        assertThat(response.getScore()).isEqualTo(110, Offset.offset(offsetByTenThousandth));
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testUpdateFileUploadAssessmentAfterComplaint_studentHidden() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.saveFileUploadSubmissionWithResultAndAssessor(afterReleaseFileUploadExercise, fileUploadSubmission, "student1", "tutor1");
        Result fileUploadAssessment = fileUploadSubmission.getLatestResult();
        Complaint complaint = new Complaint().result(fileUploadAssessment).complaintText("This is not fair");

        complaint = complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        ComplaintResponse complaintResponse = database.createInitialEmptyResponse("tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        List<Feedback> feedbacks = ModelFactory.generateFeedback();
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(feedbacks).complaintResponse(complaintResponse);

        Result updatedResult = request.putWithResponseBody(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
        assertThat(((StudentParticipation) updatedResult.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
        assertThat(updatedResult.getFeedbacks()).hasSize(3);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testSaveFileUploadAssessment_studentHidden() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(afterReleaseFileUploadExercise, fileUploadSubmission, "student1");

        Result result = request.putWithResponseBody(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/feedback", new ArrayList<String>(), Result.class, HttpStatus.OK);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(result.isRated()).isNull();
        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testSubmitFileUploadAssessment_studentHidden() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(afterReleaseFileUploadExercise, fileUploadSubmission, "student1");

        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", "true");
        List<Feedback> feedbacks = ModelFactory.generateFeedback();

        Result result = request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/feedback", feedbacks, Result.class, HttpStatus.OK,
                params);

        assertThat(result).as("submitted result found").isNotNull();
        assertThat(result.isRated()).isTrue();
        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
        assertThat(result.getFeedbacks()).hasSize(3);
        assertThat(result.getFeedbacks().get(0).getCredits()).isEqualTo(feedbacks.get(0).getCredits());
        assertThat(result.getFeedbacks().get(1).getCredits()).isEqualTo(feedbacks.get(1).getCredits());
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testOverrideAssessment_saveOtherTutorForbidden() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_saveInstructorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testOverrideAssessment_saveSameTutorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testOverrideAssessment_submitOtherTutorForbidden() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_submitInstructorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testOverrideAssessment_saveOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_saveInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testOverrideAssessment_saveSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testOverrideAssessment_saveSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", false);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testOverrideAssessment_submitOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_submitInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", false);
    }

    private void assessmentDueDatePassed() {
        database.updateAssessmentDueDate(afterReleaseFileUploadExercise.getId(), ZonedDateTime.now().minusSeconds(10));
    }

    private void overrideAssessment(String student, String originalAssessor, HttpStatus httpStatus, String submit, boolean originalAssessmentSubmitted) throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.saveFileUploadSubmissionWithResultAndAssessor(afterReleaseFileUploadExercise, fileUploadSubmission, student, originalAssessor);
        fileUploadSubmission.getLatestResult().setCompletionDate(originalAssessmentSubmitted ? ZonedDateTime.now() : null);
        resultRepo.save(fileUploadSubmission.getLatestResult());
        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", submit);
        List<Feedback> feedbacks = ModelFactory.generateFeedback();
        request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/feedback", feedbacks, Result.class, httpStatus, params);
    }

    private void cancelAssessment(HttpStatus expectedStatus) throws Exception {
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(true);
        submission = database.saveFileUploadSubmissionWithResultAndAssessor(afterReleaseFileUploadExercise, submission, "student1", "tutor1");
        database.addSampleFeedbackToResults(submission.getLatestResult());
        request.put(API_FILE_UPLOAD_SUBMISSIONS + submission.getId() + "/cancel-assessment", null, expectedStatus);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCancelOwnAssessmentAsStudent() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testCancelOwnAssessmentAsTutor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testCancelAssessmentOfOtherTutorAsTutor() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCancelAssessmentOfOtherTutorAsInstructor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getOwnAssessmentAsStudent() throws Exception {
        FileUploadExercise assessedFileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "assessed");
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.saveFileUploadSubmissionWithResultAndAssessor(assessedFileUploadExercise, fileUploadSubmission, "student1", "tutor1");
        Result result = request.get("/api/file-upload-submissions/" + fileUploadSubmission.getId() + "/result", HttpStatus.OK, Result.class);
        assertThat(result.getScore()).isEqualTo(100D);
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    public void getAssessmentOfOtherStudentAsStudent() throws Exception {
        FileUploadExercise assessedFileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "assessed");
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.saveFileUploadSubmissionWithResultAndAssessor(assessedFileUploadExercise, fileUploadSubmission, "student1", "tutor1");
        request.get("/api/file-upload-submissions/" + fileUploadSubmission.getId() + "/result", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void multipleCorrectionRoundsForExam() throws Exception {
        // Setup exam with 2 correction rounds and a programming exercise
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();
        Exam exam = database.addExam(course);
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam.addExerciseGroup(exerciseGroup1);
        exam.setVisibleDate(ZonedDateTime.now().minusHours(3));
        exam.setStartDate(ZonedDateTime.now().minusHours(2));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        exam = examRepository.save(exam);

        Exam examWithExerciseGroups = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).get();
        exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().get(0);
        FileUploadExercise exercise = ModelFactory.generateFileUploadExerciseForExam("test.pdf", exerciseGroup1);
        exercise = fileUploadExerciseRepository.save(exercise);
        exerciseGroup1.addExercise(exercise);

        // add student submission
        var submission = ModelFactory.generateFileUploadSubmission(true);
        submission = database.addFileUploadSubmission(exercise, submission, "student1");

        // verify setup
        assertThat(exam.getNumberOfCorrectionRoundsInExam()).isEqualTo(2);
        assertThat(exam.getEndDate()).isBefore(ZonedDateTime.now());
        var optionalFetchedExercise = exerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(exercise.getId());
        assertThat(optionalFetchedExercise).isPresent();
        final var exerciseWithParticipation = optionalFetchedExercise.get();
        final var studentParticipation = exerciseWithParticipation.getStudentParticipations().stream().iterator().next();

        // request to manually assess latest submission (correction round: 0)
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        params.add("correction-round", "0");
        FileUploadSubmission submissionWithoutFirstAssessment = request.get("/api/exercises/" + exerciseWithParticipation.getId() + "/file-upload-submission-without-assessment",
                HttpStatus.OK, FileUploadSubmission.class, params);
        // verify that no new submission was created
        assertThat(submissionWithoutFirstAssessment).isEqualTo(submission);
        // verify that the lock has been set
        assertThat(submissionWithoutFirstAssessment.getLatestResult()).isNotNull();
        assertThat(submissionWithoutFirstAssessment.getLatestResult().getAssessor().getLogin()).isEqualTo("tutor1");
        assertThat(submissionWithoutFirstAssessment.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.MANUAL);

        // make sure that new result correctly appears inside the continue box
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1Tutor1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1Tutor1.add("assessedByTutor", "true");
        paramsGetAssessedCR1Tutor1.add("correction-round", "0");
        var assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/file-upload-submissions", HttpStatus.OK, FileUploadSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(0)).isEqualTo(submissionWithoutFirstAssessment.getLatestResult());

        // assess submission and submit
        List<Feedback> feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here"))
                .collect(Collectors.toCollection(ArrayList::new));
        params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        final Result firstSubmittedManualResult = request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + submissionWithoutFirstAssessment.getId() + "/feedback",
                feedbacks, Result.class, HttpStatus.OK, params);

        // make sure that new result correctly appears after the assessment for first correction round
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/file-upload-submissions", HttpStatus.OK, FileUploadSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(0)).isNotNull();
        assertThat(firstSubmittedManualResult.getAssessor().getLogin()).isEqualTo("tutor1");

        // verify that the result contains the relationship
        assertThat(firstSubmittedManualResult).isNotNull();
        assertThat(firstSubmittedManualResult.getParticipation()).isEqualTo(studentParticipation);

        // verify that the relationship between student participation,
        var databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerLegalSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        var fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).contains(submissionWithoutFirstAssessment);
        assertThat(fetchedParticipation.findLatestLegalResult()).isEqualTo(firstSubmittedManualResult);

        var databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository
                .findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.get(0);
        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).isPresent();
        // it should contain the lock for the manual result
        assertThat(fetchedParticipation.findLatestSubmission().get().getResults()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission().get().getLatestResult()).isEqualTo(firstSubmittedManualResult);

        // SECOND ROUND OF CORRECTION

        database.changeUser("tutor2");
        LinkedMultiValueMap<String, String> paramsSecondCorrection = new LinkedMultiValueMap<>();
        paramsSecondCorrection.add("lock", "true");
        paramsSecondCorrection.add("correction-round", "1");

        final var submissionWithoutSecondAssessment = request.get("/api/exercises/" + exerciseWithParticipation.getId() + "/file-upload-submission-without-assessment",
                HttpStatus.OK, FileUploadSubmission.class, paramsSecondCorrection);

        // verify that the submission is not new
        assertThat(submissionWithoutSecondAssessment).isEqualTo(submission);
        // verify that the lock has been set
        assertThat(submissionWithoutSecondAssessment.getLatestResult()).isNotNull();
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessor().getLogin()).isEqualTo("tutor2");
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.MANUAL);

        // verify that the relationship between student participation,
        databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerLegalSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).contains(submissionWithoutSecondAssessment);
        assertThat(fetchedParticipation.getResults().stream().filter(x -> x.getCompletionDate() == null).findFirst()).contains(submissionWithoutSecondAssessment.getLatestResult());

        databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.get(0);
        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).isPresent();
        assertThat(fetchedParticipation.findLatestSubmission().get().getResults()).hasSize(2);
        assertThat(fetchedParticipation.findLatestSubmission().get().getLatestResult()).isEqualTo(submissionWithoutSecondAssessment.getLatestResult());

        // assess submission and submit
        feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here")).collect(Collectors.toCollection(ArrayList::new));
        params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        final var secondSubmittedManualResult = request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + submissionWithoutFirstAssessment.getId() + "/feedback",
                feedbacks, Result.class, HttpStatus.OK, params);

        assertThat(secondSubmittedManualResult).isNotNull();

        // make sure that new result correctly appears after the assessment for second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR2 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR2.add("assessedByTutor", "true");
        paramsGetAssessedCR2.add("correction-round", "1");
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/file-upload-submissions", HttpStatus.OK, FileUploadSubmission.class,
                paramsGetAssessedCR2);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutSecondAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(1)).isEqualTo(secondSubmittedManualResult);

        // make sure that they do not appear for the first correction round as the tutor only assessed the second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1.add("assessedByTutor", "true");
        paramsGetAssessedCR1.add("correction-round", "0");
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/file-upload-submissions", HttpStatus.OK, FileUploadSubmission.class,
                paramsGetAssessedCR1);

        assertThat(assessedSubmissionList).isEmpty();

        // Student should not have received a result over WebSocket as manual correction is ongoing
        verify(messagingTemplate, never()).convertAndSendToUser(notNull(), eq(Constants.NEW_RESULT_TOPIC), isA(Result.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testdeleteResult() throws Exception {
        Course course = database.addCourseWithOneExerciseAndSubmissions("file-upload", 1);
        Exercise exercise = exerciseRepository.findAllExercisesByCourseId(course.getId()).iterator().next();
        database.addAssessmentToExercise(exercise, database.getUserByLogin("tutor2"));
        database.addAssessmentToExercise(exercise, database.getUserByLogin("tutor1"));
        database.addAssessmentToExercise(exercise, database.getUserByLogin("tutor2"));

        var submissions = database.getAllSubmissionsOfExercise(exercise);
        Submission submission = submissions.get(0);
        assertThat(submission.getResults()).hasSize(3);
        Result firstResult = submission.getResults().get(0);
        Result lastResult = submission.getLatestResult();
        request.delete("/api/participations/" + submission.getParticipation().getId() + "/file-upload-submissions/" + submission.getId() + "/results/" + firstResult.getId(),
                HttpStatus.OK);
        submission = submissionRepository.findOneWithEagerResultAndFeedback(submission.getId());
        assertThat(submission.getResults()).hasSize(2);
        assertThat(submission.getResults().get(1)).isEqualTo(lastResult);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testdeleteResult_invalidResultSubmissionCombination() throws Exception {
        Course course = database.addCourseWithOneExerciseAndSubmissions("file-upload", 2);
        Exercise exercise = exerciseRepository.findAllExercisesByCourseId(course.getId()).iterator().next();
        database.addAssessmentToExercise(exercise, database.getUserByLogin("tutor2"));
        database.addAssessmentToExercise(exercise, database.getUserByLogin("tutor1"));
        database.addAssessmentToExercise(exercise, database.getUserByLogin("tutor2"));

        var submissions = database.getAllSubmissionsOfExercise(exercise);
        Submission submission1 = submissions.get(0);
        Submission submission2 = submissions.get(1);

        assertThat(submission1.getResults()).hasSize(3);
        Result resultOfOtherSubmission = submission2.getLatestResult();
        Result lastResult = submission1.getLatestResult();
        request.delete(
                "/api/participations/" + submission1.getParticipation().getId() + "/file-upload-submissions/" + submission1.getId() + "/results/" + resultOfOtherSubmission.getId(),
                HttpStatus.BAD_REQUEST);
        submission1 = submissionRepository.findOneWithEagerResultAndFeedback(submission1.getId());
        assertThat(submission1.getResults()).hasSize(3);
        assertThat(submission1.getResults().get(2)).isEqualTo(lastResult);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testdeleteResult_failAsResultHasAComplaint() throws Exception {
        Course course = database.addCourseWithOneExerciseAndSubmissions("file-upload", 1);
        Exercise exercise = exerciseRepository.findAllExercisesByCourseId(course.getId()).iterator().next();
        database.addAssessmentToExercise(exercise, database.getUserByLogin("tutor2"));
        database.addAssessmentToExercise(exercise, database.getUserByLogin("tutor1"));

        var submissions = database.getAllSubmissionsOfExercise(exercise);

        Submission submission = submissions.get(0);
        database.addComplaintToSubmission(submission, "student1", ComplaintType.COMPLAINT);
        assertThat(submission.getResults()).hasSize(2);
        Result lastResult = submission.getLatestResult();
        request.delete("/api/participations/" + submission.getParticipation().getId() + "/file-upload-submissions/" + submission.getId() + "/results/" + lastResult.getId(),
                HttpStatus.BAD_REQUEST);
        submission = submissionRepository.findOneWithEagerResultAndFeedback(submission.getId());
        assertThat(submission.getResults()).hasSize(2);
        assertThat(submission.getResults().get(1)).isEqualTo(lastResult);
    }
}
