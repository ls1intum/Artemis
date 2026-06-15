package de.tum.cit.aet.artemis.fileupload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.GradingInstructionDTO;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadAssessmentInputDTO;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadAssessmentUpdateDTO;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadComplaintResponseInputDTO;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadFeedbackDTO;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadFeedbackInputDTO;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadResultDTO;
import de.tum.cit.aet.artemis.fileupload.dto.FileUploadSubmissionDTO;
import de.tum.cit.aet.artemis.fileupload.dto.UpdateFileUploadExerciseDTO;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseFactory;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO;

class FileUploadAssessmentIntegrationTest extends AbstractFileUploadIntegrationTest {

    private static final String TEST_PREFIX = "fileuploadassessment";

    public static final String API_FILE_UPLOAD_SUBMISSIONS = "/api/fileupload/file-upload-submissions/";

    private FileUploadExercise afterReleaseFileUploadExercise;

    private Course course;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 0, 1);
        course = fileUploadExerciseUtilService.addCourseWithThreeFileUploadExercise();
        afterReleaseFileUploadExercise = ExerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
    }

    private List<Feedback> exerciseWithSGI() throws Exception {
        exerciseUtilService.addGradingInstructionsToExercise(afterReleaseFileUploadExercise);
        FileUploadExercise receivedFileUploadExercise = request.putWithResponseBody("/api/fileupload/file-upload-exercises/" + afterReleaseFileUploadExercise.getId(),
                UpdateFileUploadExerciseDTO.of(afterReleaseFileUploadExercise), FileUploadExercise.class, HttpStatus.OK);
        return ParticipationFactory.applySGIonFeedback(receivedFileUploadExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitFileUploadAssessment_asInstructor() throws Exception {
        FileUploadSubmission fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = fileUploadExerciseUtilService.addFileUploadSubmission(afterReleaseFileUploadExercise, fileUploadSubmission, TEST_PREFIX + "student1");

        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", "true");
        List<Feedback> feedbacks = exerciseWithSGI();
        FileUploadAssessmentInputDTO body = assessmentInput(feedbacks, "text");

        FileUploadResultDTO result = request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/feedback", body, FileUploadResultDTO.class,
                HttpStatus.OK, params);

        assertThat(result).as("submitted result found").isNotNull();
        assertThat(result.rated()).isTrue();
        assertThat(result.score()).isEqualTo(60); // total score 3P (60%) because gradingInstructionWithLimit was applied twice but only counts once
        assertThat(result.feedbacks()).hasSize(4);
        assertThat(findByReference(result.feedbacks(), feedbacks.getFirst().getReference()).credits()).isEqualTo(feedbacks.getFirst().getCredits());
        assertThat(findByReference(result.feedbacks(), feedbacks.get(1).getReference()).credits()).isEqualTo(feedbacks.get(1).getCredits());
        assertThat(result.assessmentNote().note()).isEqualTo("text");
        assertThat(result.assessor()).isEqualTo(result.assessmentNote().creator());

        Course course = request.get("/api/course/courses/" + afterReleaseFileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-assessment-dashboard",
                HttpStatus.OK, Course.class);
        Exercise exercise = ExerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()).hasSize(1);
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(1L);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    @CsvSource({ "INCLUDED_COMPLETELY,true", "INCLUDED_COMPLETELY,false", "INCLUDED_AS_BONUS,true", "INCLUDED_AS_BONUS,false", "NOT_INCLUDED,true", "INCLUDED_AS_BONUS,false" })
    void testManualAssessmentSubmitWithBonus(IncludedInOverallScore includedInOverallScore, boolean bonus) throws Exception {
        // setting up exercise
        afterReleaseFileUploadExercise.setIncludedInOverallScore(includedInOverallScore);
        afterReleaseFileUploadExercise.setMaxPoints(15.0);
        afterReleaseFileUploadExercise.setBonusPoints(bonus ? 15.0 : 0.0);
        exerciseRepository.save(afterReleaseFileUploadExercise);

        // setting up student submission
        FileUploadSubmission submission = ParticipationFactory.generateFileUploadSubmission(true);
        submission = fileUploadExerciseUtilService.addFileUploadSubmission(afterReleaseFileUploadExercise, submission, TEST_PREFIX + "student1");
        List<Feedback> feedbacks = new ArrayList<>();

        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 3.75, 25.0);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 7.5, 75.0);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 7.5, bonus ? 125.0 : 100.0);

        if (bonus) {
            addAssessmentFeedbackAndCheckScore(submission, feedbacks, 7.5, 175.0);
            addAssessmentFeedbackAndCheckScore(submission, feedbacks, 15.0, 200.0);
        }
    }

    private void addAssessmentFeedbackAndCheckScore(FileUploadSubmission fileUploadSubmission, List<Feedback> feedbacks, Double pointsAwarded, Double expectedScore)
            throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", "true");
        feedbacks.add(new Feedback().credits(pointsAwarded).type(FeedbackType.MANUAL_UNREFERENCED).detailText("gj"));
        FileUploadAssessmentInputDTO body = assessmentInput(feedbacks, "text");
        FileUploadResultDTO response = request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/feedback", body,
                FileUploadResultDTO.class, HttpStatus.OK, params);
        assertThat(response.score()).isEqualTo(expectedScore);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmitFileUploadAssessment_withResultOver100Percent() throws Exception {
        afterReleaseFileUploadExercise = (FileUploadExercise) exerciseUtilService.addMaxScoreAndBonusPointsToExercise(afterReleaseFileUploadExercise);
        FileUploadSubmission fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = fileUploadExerciseUtilService.addFileUploadSubmission(afterReleaseFileUploadExercise, fileUploadSubmission, TEST_PREFIX + "student1");

        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", "true");
        List<Feedback> feedbacks = new ArrayList<>();
        // Check that result is over 100% -> 105
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 2"));
        FileUploadAssessmentInputDTO body = assessmentInput(feedbacks, "text");
        FileUploadResultDTO response = request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/feedback", body,
                FileUploadResultDTO.class, HttpStatus.OK, params);

        assertThat(response.score()).isEqualTo(105);

        // Check that result is capped to maximum of maxScore + bonus points -> 110
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 3"));
        body = assessmentInput(feedbacks, "text");
        response = request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/feedback", body, FileUploadResultDTO.class, HttpStatus.OK,
                params);
        Double offsetByTenThousandth = 0.0001;
        assertThat(response.score()).isEqualTo(110, Offset.offset(offsetByTenThousandth));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testUpdateFileUploadAssessmentAfterComplaint_studentHidden() throws Exception {
        FileUploadSubmission fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = fileUploadExerciseUtilService.saveFileUploadSubmissionWithResultAndAssessor(afterReleaseFileUploadExercise, fileUploadSubmission,
                TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        Result fileUploadAssessment = fileUploadSubmission.getLatestResult();
        Complaint complaint = new Complaint().result(fileUploadAssessment).complaintText("This is not fair");

        complaint = complaintRepository.save(complaint);

        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        List<Feedback> feedbacks = ParticipationFactory.generateFeedback();
        final var assessmentUpdate = new FileUploadAssessmentUpdateDTO(feedbackInputDTOs(feedbacks),
                new FileUploadComplaintResponseInputDTO(complaintResponse.getId(), complaintResponse.getResponseText(), complaintResponse.getComplaint().isAccepted()), null);

        FileUploadResultDTO updatedResult = request.putWithResponseBody(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/assessment-after-complaint",
                assessmentUpdate, FileUploadResultDTO.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
        assertThat(updatedResult.submission().participation().participantName()).as("student name is hidden").isNull();
        assertThat(updatedResult.submission().participation().participantIdentifier()).as("student identifier is hidden").isNull();
        assertThat(updatedResult.feedbacks()).hasSize(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSaveFileUploadAssessment_studentHidden() throws Exception {
        FileUploadSubmission fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = fileUploadExerciseUtilService.addFileUploadSubmission(afterReleaseFileUploadExercise, fileUploadSubmission, TEST_PREFIX + "student1");

        FileUploadAssessmentInputDTO body = assessmentInput(new ArrayList<>(), "text");
        FileUploadResultDTO result = request.putWithResponseBody(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/feedback", body, FileUploadResultDTO.class,
                HttpStatus.OK);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(result.rated()).isFalse();
        assertThat(result.submission().participation().participantName()).as("student name is hidden").isNull();
        assertThat(result.submission().participation().participantIdentifier()).as("student identifier is hidden").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmitFileUploadAssessment_studentHidden() throws Exception {
        FileUploadSubmission fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = fileUploadExerciseUtilService.addFileUploadSubmission(afterReleaseFileUploadExercise, fileUploadSubmission, TEST_PREFIX + "student1");

        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", "true");
        List<Feedback> feedbacks = ParticipationFactory.generateFeedback();
        FileUploadAssessmentInputDTO body = assessmentInput(feedbacks, "text");

        FileUploadResultDTO result = request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/feedback", body, FileUploadResultDTO.class,
                HttpStatus.OK, params);

        assertThat(result).as("submitted result found").isNotNull();
        assertThat(result.rated()).isTrue();
        assertThat(result.submission().participation().participantName()).as("student name is hidden").isNull();
        assertThat(result.submission().participation().participantIdentifier()).as("student identifier is hidden").isNull();
        assertThat(result.feedbacks()).hasSize(3);
        assertThat(findByReference(result.feedbacks(), feedbacks.getFirst().getReference()).credits()).isEqualTo(feedbacks.getFirst().getCredits());
        assertThat(findByReference(result.feedbacks(), feedbacks.get(1).getReference()).credits()).isEqualTo(feedbacks.get(1).getCredits());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testOverrideAssessment_saveOtherTutorForbidden() throws Exception {
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_saveInstructorPossible() throws Exception {
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_saveSameTutorPossible() throws Exception {
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testOverrideAssessment_submitOtherTutorForbidden() throws Exception {
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_submitInstructorPossible() throws Exception {
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorPossible() throws Exception {
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testOverrideAssessment_saveOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_saveInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_saveSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_saveSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "false", false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testOverrideAssessment_submitOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_submitInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "true", false);
    }

    private void assessmentDueDatePassed() {
        exerciseUtilService.updateAssessmentDueDate(afterReleaseFileUploadExercise.getId(), ZonedDateTime.now().minusSeconds(10));
    }

    private void overrideAssessment(String student, String originalAssessor, HttpStatus httpStatus, String submit, boolean originalAssessmentSubmitted) throws Exception {
        FileUploadSubmission fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = fileUploadExerciseUtilService.saveFileUploadSubmissionWithResultAndAssessor(afterReleaseFileUploadExercise, fileUploadSubmission, student,
                originalAssessor);
        var lastResult = fileUploadSubmission.getLatestResult();
        assertThat(lastResult).isNotNull();
        lastResult.setCompletionDate(originalAssessmentSubmitted ? ZonedDateTime.now() : null);
        resultRepository.save(fileUploadSubmission.getLatestResult());
        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", submit);
        List<Feedback> feedbacks = ParticipationFactory.generateFeedback();
        FileUploadAssessmentInputDTO body = assessmentInput(feedbacks, "text");
        request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/feedback", body, FileUploadResultDTO.class, httpStatus, params);
    }

    private void cancelAssessment(HttpStatus expectedStatus) throws Exception {
        FileUploadSubmission submission = ParticipationFactory.generateFileUploadSubmission(true);
        submission = fileUploadExerciseUtilService.saveFileUploadSubmissionWithResultAndAssessor(afterReleaseFileUploadExercise, submission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        assertThat(submission).isNotNull();
        var lastResult = submission.getLatestResult();
        assertThat(lastResult).isNotNull();
        participationUtilService.addSampleFeedbackToResults(lastResult);
        request.put(API_FILE_UPLOAD_SUBMISSIONS + submission.getId() + "/cancel-assessment", null, expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCancelOwnAssessmentAsStudent() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCancelOwnAssessmentAsTutor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testCancelAssessmentOfOtherTutorAsTutor() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCancelAssessmentOfOtherTutorAsInstructor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getOwnAssessmentAsStudent() throws Exception {
        FileUploadExercise assessedFileUploadExercise = ExerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "assessed");
        FileUploadSubmission fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = fileUploadExerciseUtilService.saveFileUploadSubmissionWithResultAndAssessor(assessedFileUploadExercise, fileUploadSubmission,
                TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        FileUploadResultDTO result = request.get(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/result", HttpStatus.OK, FileUploadResultDTO.class);
        assertThat(result.score()).isEqualTo(100D);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void getAssessmentOfOtherStudentAsStudent() throws Exception {
        FileUploadExercise assessedFileUploadExercise = ExerciseUtilService.findFileUploadExerciseWithTitle(course.getExercises(), "assessed");
        FileUploadSubmission fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = fileUploadExerciseUtilService.saveFileUploadSubmissionWithResultAndAssessor(assessedFileUploadExercise, fileUploadSubmission,
                TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        request.get(API_FILE_UPLOAD_SUBMISSIONS + fileUploadSubmission.getId() + "/result", HttpStatus.FORBIDDEN, FileUploadResultDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void multipleCorrectionRoundsForExam() throws Exception {
        // Setup exam with 2 correction rounds and a programming exercise
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();
        Exam exam = examUtilService.addExam(course);
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam.addExerciseGroup(exerciseGroup1);
        exam.setVisibleDate(ZonedDateTime.now().minusHours(3));
        exam.setStartDate(ZonedDateTime.now().minusHours(2));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        exam = examRepository.save(exam);

        Exam examWithExerciseGroups = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).orElseThrow();
        exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().getFirst();
        FileUploadExercise exercise = FileUploadExerciseFactory.generateFileUploadExerciseForExam("test.pdf", exerciseGroup1);
        exercise = fileUploadExerciseRepository.save(exercise);
        exerciseGroup1.addExercise(exercise);

        // add student submission
        var submission = ParticipationFactory.generateFileUploadSubmission(true);
        submission = fileUploadExerciseUtilService.addFileUploadSubmission(exercise, submission, TEST_PREFIX + "student1");

        Participation studentParticipation = submission.getParticipation();

        // request to manually assess latest submission (correction round: 0)
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        params.add("correction-round", "0");
        FileUploadSubmissionDTO submissionWithoutFirstAssessment = request.get("/api/fileupload/exercises/" + exercise.getId() + "/file-upload-submission-without-assessment",
                HttpStatus.OK, FileUploadSubmissionDTO.class, params);
        // verify that no new submission was created
        assertThat(submissionWithoutFirstAssessment.id()).isEqualTo(submission.getId());
        // verify that the lock has been set
        assertThat(submissionWithoutFirstAssessment.results()).hasSize(1);
        assertThat(submissionWithoutFirstAssessment.results().getFirst().assessor().login()).isEqualTo(TEST_PREFIX + "tutor1");
        assertThat(submissionWithoutFirstAssessment.results().getFirst().assessmentType()).isEqualTo(AssessmentType.MANUAL);

        // make sure that new result correctly appears inside the continue box
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1Tutor1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1Tutor1.add("assessedByTutor", "true");
        paramsGetAssessedCR1Tutor1.add("correction-round", "0");
        var assessedSubmissionList = request.getList("/api/fileupload/exercises/" + exercise.getId() + "/file-upload-submissions", HttpStatus.OK, FileUploadSubmissionDTO.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().id()).isEqualTo(submissionWithoutFirstAssessment.id());
        assertThat(assessedSubmissionList.getFirst().results().getFirst().id()).isEqualTo(submissionWithoutFirstAssessment.results().getFirst().id());

        // assess submission and submit
        List<Feedback> feedbacks = ParticipationFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here"))
                .collect(Collectors.toCollection(ArrayList::new));
        FileUploadAssessmentInputDTO body = assessmentInput(feedbacks, "text");
        params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        final FileUploadResultDTO firstSubmittedManualResult = request.putWithResponseBodyAndParams(
                API_FILE_UPLOAD_SUBMISSIONS + submissionWithoutFirstAssessment.id() + "/feedback", body, FileUploadResultDTO.class, HttpStatus.OK, params);

        // make sure that new result correctly appears after the assessment for first correction round
        assessedSubmissionList = request.getList("/api/fileupload/exercises/" + exercise.getId() + "/file-upload-submissions", HttpStatus.OK, FileUploadSubmissionDTO.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().id()).isEqualTo(submissionWithoutFirstAssessment.id());
        assertThat(assessedSubmissionList.getFirst().results().getFirst()).isNotNull();
        assertThat(firstSubmittedManualResult.assessor().login()).isEqualTo(TEST_PREFIX + "tutor1");

        // verify that the result contains the relationship
        assertThat(firstSubmittedManualResult).isNotNull();
        assertThat(firstSubmittedManualResult.submission().participation().id()).isEqualTo(studentParticipation.getId());

        // verify that the relationship between student participation,
        var databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        var fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission())
                .hasValueSatisfying(fetchedSubmission -> assertThat(fetchedSubmission.getId()).isEqualTo(submissionWithoutFirstAssessment.id()));
        assertThat(fetchedParticipation.findLatestResult().getId()).isEqualTo(firstSubmittedManualResult.id());

        var databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository
                .findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.getFirst();
        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).isPresent();
        // it should contain the lock for the manual result
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getResults()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getLatestResult().getId()).isEqualTo(firstSubmittedManualResult.id());

        // SECOND ROUND OF CORRECTION

        userUtilService.changeUser(TEST_PREFIX + "tutor2");
        LinkedMultiValueMap<String, String> paramsSecondCorrection = new LinkedMultiValueMap<>();
        paramsSecondCorrection.add("lock", "true");
        paramsSecondCorrection.add("correction-round", "1");

        final var submissionWithoutSecondAssessment = request.get("/api/fileupload/exercises/" + exercise.getId() + "/file-upload-submission-without-assessment", HttpStatus.OK,
                FileUploadSubmissionDTO.class, paramsSecondCorrection);

        // verify that the submission is not new
        assertThat(submissionWithoutSecondAssessment.id()).isEqualTo(submission.getId());
        // verify that the lock has been set
        assertThat(submissionWithoutSecondAssessment.results()).hasSize(2);
        assertThat(submissionWithoutSecondAssessment.results().getLast().assessor().login()).isEqualTo(TEST_PREFIX + "tutor2");
        assertThat(submissionWithoutSecondAssessment.results().getLast().assessmentType()).isEqualTo(AssessmentType.MANUAL);

        // verify that the relationship between student participation,
        databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission())
                .hasValueSatisfying(fetchedSubmission -> assertThat(fetchedSubmission.getId()).isEqualTo(submissionWithoutSecondAssessment.id()));
        assertThat(participationUtilService.getResultsForParticipation(fetchedParticipation).stream().filter(result -> result.getCompletionDate() == null).findFirst())
                .hasValueSatisfying(result -> assertThat(result.getId()).isEqualTo(submissionWithoutSecondAssessment.results().getLast().id()));

        databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.getFirst();
        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).isPresent();
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getResults()).hasSize(2);
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getLatestResult().getId()).isEqualTo(submissionWithoutSecondAssessment.results().getLast().id());

        // assess submission and submit
        feedbacks = ParticipationFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here")).collect(Collectors.toCollection(ArrayList::new));
        params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        body = assessmentInput(feedbacks, "text");
        final var secondSubmittedManualResult = request.putWithResponseBodyAndParams(API_FILE_UPLOAD_SUBMISSIONS + submissionWithoutFirstAssessment.id() + "/feedback", body,
                FileUploadResultDTO.class, HttpStatus.OK, params);

        assertThat(secondSubmittedManualResult).isNotNull();

        // make sure that new result correctly appears after the assessment for second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR2 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR2.add("assessedByTutor", "true");
        paramsGetAssessedCR2.add("correction-round", "1");
        assessedSubmissionList = request.getList("/api/fileupload/exercises/" + exercise.getId() + "/file-upload-submissions", HttpStatus.OK, FileUploadSubmissionDTO.class,
                paramsGetAssessedCR2);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().id()).isEqualTo(submissionWithoutSecondAssessment.id());
        assertThat(assessedSubmissionList.getFirst().results().get(1).id()).isEqualTo(secondSubmittedManualResult.id());

        // make sure that they do not appear for the first correction round as the tutor only assessed the second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1.add("assessedByTutor", "true");
        paramsGetAssessedCR1.add("correction-round", "0");
        assessedSubmissionList = request.getList("/api/fileupload/exercises/" + exercise.getId() + "/file-upload-submissions", HttpStatus.OK, FileUploadSubmissionDTO.class,
                paramsGetAssessedCR1);

        assertThat(assessedSubmissionList).isEmpty();

        // Student should not have received a result over WebSocket as manual correction is ongoing
        verify(websocketMessagingService, never()).sendMessageToUser(notNull(), eq(Constants.NEW_RESULT_TOPIC), isA(ResultDTO.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testdeleteResult() throws Exception {
        Course course = exerciseUtilService.addCourseWithOneExerciseAndSubmissions(TEST_PREFIX, "file-upload", 1);
        Exercise exercise = exerciseRepository.findAllExercisesByCourseId(course.getId()).iterator().next();
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor2"));
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor2"));

        var submissions = participationUtilService.getAllSubmissionsOfExercise(exercise);
        Submission submission = submissions.getFirst();
        assertThat(submission.getResults()).hasSize(3);
        Result firstResult = submission.getResults().getFirst();
        Result lastResult = submission.getLatestResult();
        request.delete(
                "/api/fileupload/participations/" + submission.getParticipation().getId() + "/file-upload-submissions/" + submission.getId() + "/results/" + firstResult.getId(),
                HttpStatus.OK);
        submission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submission.getId());
        assertThat(submission.getResults()).hasSize(2);
        assertThat(submission.getResults().get(1)).isEqualTo(lastResult);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testdeleteResult_invalidResultSubmissionCombination() throws Exception {
        Course course = exerciseUtilService.addCourseWithOneExerciseAndSubmissions(TEST_PREFIX, "file-upload", 2);
        Exercise exercise = exerciseRepository.findAllExercisesByCourseId(course.getId()).iterator().next();
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor2"));
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor2"));

        var submissions = participationUtilService.getAllSubmissionsOfExercise(exercise);
        Submission submission1 = submissions.getFirst();
        Submission submission2 = submissions.get(1);

        assertThat(submission1.getResults()).hasSize(3);
        Result resultOfOtherSubmission = submission2.getLatestResult();
        Result lastResult = submission1.getLatestResult();
        assertThat(lastResult).isNotNull();
        assertThat(resultOfOtherSubmission).isNotNull();
        request.delete("/api/fileupload/participations/" + submission1.getParticipation().getId() + "/file-upload-submissions/" + submission1.getId() + "/results/"
                + resultOfOtherSubmission.getId(), HttpStatus.BAD_REQUEST);
        submission1 = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submission1.getId());
        assertThat(submission1.getResults()).hasSize(3);
        assertThat(submission1.getResults().get(2)).isEqualTo(lastResult);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testdeleteResult_failAsResultHasAComplaint() throws Exception {
        Course course = exerciseUtilService.addCourseWithOneExerciseAndSubmissions(TEST_PREFIX, "file-upload", 1);
        Exercise exercise = exerciseRepository.findAllExercisesByCourseId(course.getId()).iterator().next();
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor2"));
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));

        var submissions = participationUtilService.getAllSubmissionsOfExercise(exercise);

        Submission submission = submissions.getFirst();
        complaintUtilService.addComplaintToSubmission(submission, TEST_PREFIX + "student1", ComplaintType.COMPLAINT);
        assertThat(submission.getResults()).hasSize(2);
        Result lastResult = submission.getLatestResult();
        assertThat(lastResult).isNotNull();
        request.delete(
                "/api/fileupload/participations/" + submission.getParticipation().getId() + "/file-upload-submissions/" + submission.getId() + "/results/" + lastResult.getId(),
                HttpStatus.BAD_REQUEST);
        submission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submission.getId());
        assertThat(submission.getResults()).hasSize(2);
        assertThat(submission.getResults().get(1)).isEqualTo(lastResult);
    }

    private static FileUploadAssessmentInputDTO assessmentInput(List<Feedback> feedbacks, String assessmentNote) {
        return new FileUploadAssessmentInputDTO(feedbackInputDTOs(feedbacks), assessmentNote);
    }

    private static List<FileUploadFeedbackInputDTO> feedbackInputDTOs(List<Feedback> feedbacks) {
        return feedbacks.stream()
                .map(feedback -> new FileUploadFeedbackInputDTO(feedback.getId(), feedback.getText(), feedback.getDetailText(), feedback.getReference(), feedback.getCredits(),
                        feedback.isPositive(), feedback.getType(), feedback.getVisibility(),
                        feedback.getGradingInstruction() != null ? GradingInstructionDTO.of(feedback.getGradingInstruction()) : null))
                .toList();
    }

    private static FileUploadFeedbackDTO findByReference(List<FileUploadFeedbackDTO> feedbacks, String reference) {
        return feedbacks.stream().filter(f -> reference.equals(f.reference())).findFirst().orElseThrow();
    }
}
