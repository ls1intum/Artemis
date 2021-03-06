package de.tum.in.www1.artemis;

import static java.time.ZonedDateTime.now;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.TextExerciseUtilService;
import de.tum.in.www1.artemis.web.rest.dto.TextAssessmentDTO;
import de.tum.in.www1.artemis.web.rest.dto.TextAssessmentUpdateDTO;

public class TextAssessmentIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    FeedbackRepository feedbackRepository;

    @Autowired
    ComplaintRepository complaintRepo;

    @Autowired
    ComplaintResponseRepository complaintResponseRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    private TextClusterRepository textClusterRepository;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private FeedbackConflictRepository feedbackConflictRepository;

    private TextExercise textExercise;

    private Course course;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 3, 1);
        course = database.addCourseWithOneReleasedTextExercise();
        textExercise = database.findTextExerciseWithTitle(course.getExercises(), "Text");
        textExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exerciseRepo.save(textExercise);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void retrieveParticipationForSubmission_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, false);
        textSubmission = database.saveTextSubmission(textExercise, textSubmission, "student1");

        StudentParticipation participationWithoutAssessment = request.get("/api/text-assessments/submission/" + textSubmission.getId(), HttpStatus.OK, StudentParticipation.class);

        assertThat(participationWithoutAssessment).as("participation with submission was found").isNotNull();
        assertThat(participationWithoutAssessment.getSubmissions().iterator().next().getId()).as("participation with correct text submission was found")
                .isEqualTo(textSubmission.getId());
        assertThat(participationWithoutAssessment.getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void retrieveParticipationForLockedSubmission() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, false);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor2");
        Result result = textSubmission.getLatestResult();
        result.setCompletionDate(null); // assessment is still in progress for this test
        resultRepo.save(result);
        StudentParticipation participation = request.get("/api/text-assessments/submission/" + textSubmission.getId(), HttpStatus.BAD_REQUEST, StudentParticipation.class);
        assertThat(participation).as("participation is locked and should not be returned").isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void retrieveParticipationForNonExistingSubmission() throws Exception {
        StudentParticipation participation = request.get("/api/text-assessments/submission/345395769256365", HttpStatus.BAD_REQUEST, StudentParticipation.class);
        assertThat(participation).as("participation should not be found").isNull();
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void updateTextAssessmentAfterComplaint_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        Result textAssessment = textSubmission.getLatestResult();
        Complaint complaint = new Complaint().result(textAssessment).complaintText("This is not fair");

        complaintRepo.save(complaint);
        complaint.getResult().setParticipation(null); // Break infinite reference chain

        ComplaintResponse complaintResponse = database.createInitialEmptyResponse("tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(new ArrayList<>()).complaintResponse(complaintResponse);

        Result updatedResult = request.putWithResponseBody("/api/text-assessments/text-submissions/" + textSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
        assertThat(((StudentParticipation) updatedResult.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void updateTextAssessmentAfterComplaint_withTextBlocks() throws Exception {
        // Setup
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("This is Part 1, and this is Part 2. There is also Part 3.", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        database.addAndSaveTextBlocksToTextSubmission(Set.of(new TextBlock().startIndex(0).endIndex(15).automatic(), new TextBlock().startIndex(16).endIndex(35).automatic(),
                new TextBlock().startIndex(36).endIndex(57).automatic()), textSubmission);

        Result textAssessment = textSubmission.getLatestResult();
        complaintRepo.save(new Complaint().result(textAssessment).complaintText("This is not fair"));

        // Get Text Submission and Complaint
        StudentParticipation participation = request.get("/api/text-assessments/submission/" + textSubmission.getId(), HttpStatus.OK, StudentParticipation.class);
        final Result result = participation.getResults().iterator().next();
        final Complaint complaint = request.get("/api/complaints/result/" + result.getId(), HttpStatus.OK, Complaint.class);

        // Accept Complaint and update Assessment
        ComplaintResponse complaintResponse = database.createInitialEmptyResponse("tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        TextAssessmentUpdateDTO assessmentUpdate = new TextAssessmentUpdateDTO();
        assessmentUpdate.feedbacks(new ArrayList<>()).complaintResponse(complaintResponse);
        assessmentUpdate.setTextBlocks(new HashSet<>());

        Result updatedResult = request.putWithResponseBody("/api/text-assessments/text-submissions/" + textSubmission.getId() + "/assessment-after-complaint", assessmentUpdate,
                Result.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void saveTextAssessment_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");
        exerciseDueDatePassed();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        TextSubmission submissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setFeedbacks(new ArrayList<>());

        Result result = request.putWithResponseBody("/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + submissionWithoutAssessment.getLatestResult().getId(),
                textAssessmentDTO, Result.class, HttpStatus.OK);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void submitTextAssessment_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");
        exerciseDueDatePassed();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        TextSubmission submissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setFeedbacks(new ArrayList<>());
        Result result = request.putWithResponseBody(
                "/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + submissionWithoutAssessment.getLatestResult().getId() + "/submit", textAssessmentDTO,
                Result.class, HttpStatus.OK);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getResult_studentHidden() throws Exception {
        int submissionCount = 5;
        int submissionSize = 4;
        int[] clusterSizes = new int[] { 4, 5, 10, 1 };
        var textBlocks = textExerciseUtilService.generateTextBlocks(submissionCount * submissionSize);
        TextExercise textExercise = textExerciseUtilService.createSampleTextExerciseWithSubmissions(course, new ArrayList<>(textBlocks), submissionCount, submissionSize);
        textBlocks.forEach(TextBlock::computeId);
        List<TextCluster> clusters = textExerciseUtilService.addTextBlocksToCluster(textBlocks, clusterSizes, textExercise);
        textClusterRepository.saveAll(clusters);
        textBlockRepository.saveAll(textBlocks);

        StudentParticipation studentParticipation = (StudentParticipation) textSubmissionRepository.findAll().get(0).getParticipation();

        // connect it with a student (!= tutor assessing it)
        User user = database.getUserByLogin("student1");
        studentParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipation.setParticipant(user);
        studentParticipationRepository.save(studentParticipation);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        TextSubmission submissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);
        final Result result = submissionWithoutAssessment.getLatestResult();
        assertThat(result).as("saved result found").isNotNull();
        assertThat(((StudentParticipation) submissionWithoutAssessment.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
        assertThat(result.getParticipation()).isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getParticipationForNonTextExercise() throws Exception {
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
                ZonedDateTime.now().plusDays(2), "png,pdf", textExercise.getCourseViaExerciseGroupOrCourseMember());
        exerciseRepo.save(fileUploadExercise);

        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        database.saveFileUploadSubmissionWithResultAndAssessorFeedback(fileUploadExercise, fileUploadSubmission, "student1", "tutor1", new ArrayList<>());

        final Participation participation = request.get("/api/exercises/" + fileUploadExercise.getId() + "/text-submission-without-assessment", HttpStatus.BAD_REQUEST,
                Participation.class);

        assertThat(participation).as("no result should be returned when exercise is not a text exercise").isNull();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getDataForTextEditor_assessorHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");

        Participation participation = request.get("/api/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, Participation.class);

        assertThat(participation).as("participation found").isNotNull();
        assertThat(participation.getResults().iterator().next()).as("result found").isNotNull();
        assertThat(participation.getResults().iterator().next().getAssessor()).as("assessor of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getDataForTextEditor_hasTextBlocks() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        var textBlocks = textExerciseUtilService.generateTextBlocks(1);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        database.addAndSaveTextBlocksToTextSubmission(textBlocks, textSubmission);

        Participation participation = request.get("/api/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, Participation.class);

        final TextSubmission submission = (TextSubmission) participation.getResults().iterator().next().getSubmission();
        assertThat(submission.getBlocks()).isNotNull();
        assertThat(submission.getBlocks()).isNotEmpty();
    }

    @Test
    @WithMockUser(value = "student2", roles = "USER")
    public void getDataForTextEditor_asOtherStudent() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        request.get("/api/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.FORBIDDEN, Participation.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getDataForTextEditor_BeforeExamPublishDate_Forbidden() throws Exception {
        // create exam
        Exam exam = database.addExamWithExerciseGroup(course, true);
        exam.setStartDate(now().minusHours(2));
        exam.setEndDate(now().minusHours(1));
        exam.setVisibleDate(now().minusHours(3));
        exam.setPublishResultsDate(now().plusHours(3));

        // creating exercise
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().get(0);

        TextExercise textExercise = ModelFactory.generateTextExerciseForExam(exerciseGroup);
        exerciseGroup.addExercise(textExercise);
        exerciseGroupRepository.save(exerciseGroup);
        textExercise = exerciseRepo.save(textExercise);

        examRepository.save(exam);

        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        request.get("/api/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.FORBIDDEN, Participation.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getDataForTextEditor_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");

        StudentParticipation participation = request.get("/api/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, StudentParticipation.class);

        assertThat(participation).as("participation found").isNotNull();
        assertThat(participation.getResults().iterator().next()).as("result found").isNotNull();
        assertThat(participation.getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getDataForTextEditor_submissionWithoutResult() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmission(textExercise, textSubmission, "student1");
        request.get("/api/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, StudentParticipation.class);
    }

    private Result getExampleResultForTutor(HttpStatus expectedStatus, boolean isExample) throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission.setExampleSubmission(isExample);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "instructor1");
        final var exampleSubmission = ModelFactory.generateExampleSubmission(textSubmission, textExercise, true);
        database.addExampleSubmission(exampleSubmission);

        Result result = request.get("/api/text-assessments/exercise/" + textExercise.getId() + "/submission/" + textSubmission.getId() + "/example-result", expectedStatus,
                Result.class);

        if (expectedStatus == HttpStatus.OK) {
            assertThat(result).as("result found").isNotNull();
            assertThat(result.getSubmission().getId()).as("result for correct submission").isEqualTo(textSubmission.getId());
        }

        return result;
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getExampleResultForTutorAsStudent() throws Exception {
        getExampleResultForTutor(HttpStatus.FORBIDDEN, true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getExampleResultForTutorAsTutor() throws Exception {
        // TODO: somehow this test fails in IntelliJ but passes when executed on the command line?!?
        getExampleResultForTutor(HttpStatus.OK, true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getExampleResultForNonExampleSubmissionAsTutor() throws Exception {
        final var result = getExampleResultForTutor(HttpStatus.OK, false);
        assertThat(result.getFeedbacks()).isEmpty();
        assertThat(result.getScore()).isNull();
        assertThat(result.getResultString()).isNull();
    }

    private void cancelAssessment(HttpStatus expectedStatus) throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        database.addSampleFeedbackToResults(textSubmission.getLatestResult());
        request.put("/api/text-assessments/exercise/" + textExercise.getId() + "/submission/" + textSubmission.getId() + "/cancel-assessment", null, expectedStatus);
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

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void cancelAssessment_wrongSubmissionId() throws Exception {
        request.put("/api/text-assessments/exercise/" + textExercise.getId() + "/submission/100/cancel-assessment", null, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void testOverrideAssessment_saveOtherTutorForbidden() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_saveInstructorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_saveSameTutorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void testOverrideAssessment_submitOtherTutorForbidden() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_submitInstructorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void testOverrideAssessment_saveOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_saveInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_saveSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_saveSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", false);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void testOverrideAssessment_submitOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_submitInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", false);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testSubmitAssessment_IncludedCompletelyWithBonusPointsExercise() throws Exception {
        // setting up exercise
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(10.0);
        exerciseRepo.save(textExercise);

        // setting up student submission
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");
        // ending exercise
        exerciseDueDatePassed();

        // getting submission from db
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        TextSubmission submissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 5.0, 150L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 5.0, 200L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 5.0, 200L);

        Course course = request.get("/api/courses/" + textExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-tutor-dashboard", HttpStatus.OK, Course.class);
        Exercise exercise = (Exercise) course.getExercises().toArray()[0];
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds().length).isEqualTo(1L);
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()[0].getInTime()).isEqualTo(1L);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testSubmitAssessment_IncludedCompletelyWithoutBonusPointsExercise() throws Exception {
        // setting up exercise
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        exerciseRepo.save(textExercise);

        // setting up student submission
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");
        // ending exercise
        exerciseDueDatePassed();

        // getting submission from db
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        TextSubmission submissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 5.0, 100L);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testSubmitAssessment_IncludedAsBonusExercise() throws Exception {
        // setting up exercise
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        exerciseRepo.save(textExercise);

        // setting up student submission
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");
        // ending exercise
        exerciseDueDatePassed();

        // getting submission from db
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        TextSubmission submissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 5.0, 100L);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testSubmitAssessment_NotIncludedExercise() throws Exception {
        // setting up exercise
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        exerciseRepo.save(textExercise);

        // setting up student submission
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");
        // ending exercise
        exerciseDueDatePassed();

        // getting submission from db
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        TextSubmission submissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, textAssessmentDTO, feedbacks, 5.0, 100L);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testSubmitAssessment_withResultOver100Percent() throws Exception {
        textExercise = (TextExercise) database.addMaxScoreAndBonusPointsToExercise(textExercise);
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");
        exerciseDueDatePassed();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        TextSubmission submissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 2"));
        textAssessmentDTO.setFeedbacks(feedbacks);

        // Check that result is over 100% -> 105
        Result response = request.putWithResponseBody(
                "/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + submissionWithoutAssessment.getLatestResult().getId() + "/submit", textAssessmentDTO,
                Result.class, HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(105);

        feedbacks.add(new Feedback().credits(20.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 3"));
        textAssessmentDTO.setFeedbacks(feedbacks);

        // Check that result is capped to maximum of maxScore + bonus points -> 110
        response = request.putWithResponseBody(
                "/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + submissionWithoutAssessment.getLatestResult().getId() + "/submit", textAssessmentDTO,
                Result.class, HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(110, Offset.offset(0.00001));
    }

    private void exerciseDueDatePassed() {
        database.updateExerciseDueDate(textExercise.getId(), ZonedDateTime.now().minusHours(2));
    }

    private void assessmentDueDatePassed() {
        database.updateAssessmentDueDate(textExercise.getId(), ZonedDateTime.now().minusSeconds(10));
    }

    private void overrideAssessment(String student, String originalAssessor, HttpStatus httpStatus, String submit, boolean originalAssessmentSubmitted) throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Test123", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, student, originalAssessor);
        textSubmission.getLatestResult().setCompletionDate(originalAssessmentSubmitted ? ZonedDateTime.now() : null);
        resultRepo.save(textSubmission.getLatestResult());
        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", submit);
        List<Feedback> feedbacks = ModelFactory.generateFeedback();
        var path = "/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + textSubmission.getLatestResult().getId();
        if (submit.equals("true")) {
            path = path + "/submit";
        }
        var body = new TextAssessmentDTO(feedbacks);
        request.putWithResponseBodyAndParams(path, body, Result.class, httpStatus, params);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testTextBlocksAreConsistentWhenOpeningSameAssessmentTwiceWithAtheneEnabled() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("This is Part 1, and this is Part 2. There is also Part 3.", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");
        exerciseDueDatePassed();

        var blocks = Set.of(new TextBlock().startIndex(0).endIndex(15).automatic(), new TextBlock().startIndex(16).endIndex(35).automatic(),
                new TextBlock().startIndex(36).endIndex(57).automatic());
        database.addAndSaveTextBlocksToTextSubmission(blocks, textSubmission);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        TextSubmission submission1stRequest = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK, TextSubmission.class,
                params);

        var blocksFrom1stRequest = submission1stRequest.getBlocks();
        assertThat(blocksFrom1stRequest.toArray()).containsExactlyInAnyOrder(blocks.toArray());

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setFeedbacks(
                Collections.singletonList(new Feedback().detailText("Test").credits(1d).reference(blocksFrom1stRequest.iterator().next().getId()).type(FeedbackType.MANUAL)));
        textAssessmentDTO.setTextBlocks(blocksFrom1stRequest);
        request.putWithResponseBody("/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + submission1stRequest.getLatestResult().getId() + "/submit",
                textAssessmentDTO, Result.class, HttpStatus.OK);

        Participation participation2ndRequest = request.get("/api/text-assessments/submission/" + textSubmission.getId(), HttpStatus.OK, Participation.class, params);
        TextSubmission submission2ndRequest = (TextSubmission) (participation2ndRequest).getSubmissions().iterator().next();
        var blocksFrom2ndRequest = submission2ndRequest.getBlocks();
        assertThat(blocksFrom2ndRequest.toArray()).containsExactlyInAnyOrder(blocks.toArray());
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void checkTextSubmissionWithoutAssessmentAndRetrieveParticipationForSubmissionReturnSameBlocksAndFeedback() throws Exception {
        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedbackForAutomaticFeedback();

        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("lock", "true");
        TextSubmission textSubmissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, parameters);

        request.put("/api/text-assessments/exercise/" + textExercise.getId() + "/submission/" + textSubmissions.get(0).getId() + "/cancel-assessment", null, HttpStatus.OK);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        Participation participation = request.get("/api/text-assessments/submission/" + textSubmissions.get(0).getId(), HttpStatus.OK, Participation.class, params);
        final TextSubmission submissionFromParticipation = (TextSubmission) participation.getSubmissions().toArray()[0];
        final Result resultFromParticipation = (Result) participation.getResults().toArray()[0];

        assertThat(textSubmissionWithoutAssessment.getId()).isEqualTo(submissionFromParticipation.getId());
        assertThat(Arrays.equals(textSubmissionWithoutAssessment.getBlocks().toArray(), submissionFromParticipation.getBlocks().toArray())).isTrue();
        final Feedback feedbackFromSubmissionWithoutAssessment = textSubmissionWithoutAssessment.getLatestResult().getFeedbacks().get(0);
        final Feedback feedbackFromParticipation = resultFromParticipation.getFeedbacks().get(0);
        assertThat(feedbackFromSubmissionWithoutAssessment.getCredits()).isEqualTo(feedbackFromParticipation.getCredits());
        assertThat(feedbackFromSubmissionWithoutAssessment.getDetailText()).isEqualTo(feedbackFromParticipation.getDetailText());
        assertThat(feedbackFromSubmissionWithoutAssessment.getType()).isEqualTo(feedbackFromParticipation.getType());
    }

    @NotNull
    private List<TextSubmission> prepareTextSubmissionsWithFeedbackForAutomaticFeedback() {
        TextSubmission textSubmission1 = ModelFactory.generateTextSubmission("This is Part 1, and this is Part 2. There is also Part 3.", Language.ENGLISH, true);
        TextSubmission textSubmission2 = ModelFactory.generateTextSubmission("This is another Submission.", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission1, "student1");
        database.saveTextSubmission(textExercise, textSubmission2, "student2");
        exerciseDueDatePassed();

        final TextCluster cluster = new TextCluster().exercise(textExercise);
        textClusterRepository.save(cluster);

        final TextBlock textBlockSubmission1 = new TextBlock().startIndex(0).endIndex(15).automatic().cluster(cluster);
        final TextBlock textBlockSubmission2 = new TextBlock().startIndex(0).endIndex(27).automatic().cluster(cluster);

        cluster.blocks(asList(textBlockSubmission1, textBlockSubmission2)).distanceMatrix(new double[][] { { 0.1, 0.1 }, { 0.1, 0.1 } });

        textSubmission1 = database.addAndSaveTextBlocksToTextSubmission(
                Set.of(textBlockSubmission1, new TextBlock().startIndex(16).endIndex(35).automatic(), new TextBlock().startIndex(36).endIndex(57).automatic()), textSubmission1);

        textSubmission2 = database.addAndSaveTextBlocksToTextSubmission(Set.of(textBlockSubmission2), textSubmission2);

        textClusterRepository.save(cluster);

        final Feedback feedback = new Feedback().detailText("Foo Bar.").credits(2d).reference(textBlockSubmission2.getId());
        textSubmission2 = database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission2, "student2", "tutor1", Collections.singletonList(feedback));

        // refetch the database objects to avoid lazy exceptions
        textSubmission1 = textSubmissionRepository.findWithEagerResultsAndFeedbackAndTextBlocksById(textSubmission1.getId()).get();
        textSubmission2 = textSubmissionRepository.findWithEagerResultsAndFeedbackAndTextBlocksById(textSubmission2.getId()).get();
        return asList(textSubmission1, textSubmission2);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void checkTextBlockSavePreservesClusteringInformation() throws Exception {
        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedbackForAutomaticFeedback();
        final Map<String, TextBlock> blocksSubmission1 = textSubmissions.get(0).getBlocks().stream().collect(Collectors.toMap(TextBlock::getId, block -> block));

        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("lock", "true");
        TextSubmission textSubmissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, parameters);

        textSubmissionWithoutAssessment.getBlocks()
                .forEach(block -> assertThat(block).isEqualToIgnoringGivenFields(blocksSubmission1.get(block.getId()), "positionInCluster", "submission", "cluster"));

        textBlockRepository.findAllWithEagerClusterBySubmissionId(textSubmissionWithoutAssessment.getId())
                .forEach(block -> assertThat(block).isEqualToComparingFieldByField(blocksSubmission1.get(block.getId())));

        final var newTextBlocksToSimulateAngularSerialization = textSubmissionWithoutAssessment.getBlocks().stream().map(oldBlock -> {
            var newBlock = new TextBlock();
            newBlock.setText(oldBlock.getText());
            newBlock.setStartIndex(oldBlock.getStartIndex());
            newBlock.setEndIndex(oldBlock.getEndIndex());
            newBlock.setId(oldBlock.getId());
            return newBlock;
        }).collect(Collectors.toSet());

        final TextAssessmentDTO dto = new TextAssessmentDTO();
        dto.setTextBlocks(newTextBlocksToSimulateAngularSerialization);
        dto.setFeedbacks(new ArrayList<>());

        request.putWithResponseBody("/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + textSubmissionWithoutAssessment.getLatestResult().getId(), dto,
                Result.class, HttpStatus.OK);

        textBlockRepository.findAllWithEagerClusterBySubmissionId(textSubmissionWithoutAssessment.getId())
                .forEach(block -> assertThat(block).isEqualToComparingFieldByField(blocksSubmission1.get(block.getId())));
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void checkTrackingTokenHeader() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");
        exerciseDueDatePassed();

        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("lock", "true");

        request.getWithHeaders("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK, TextSubmission.class, parameters, new HttpHeaders(),
                new String[] { "X-Athene-Tracking-Authorization" });

    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void retrieveConflictingTextSubmissions() throws Exception {
        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedbackAndConflicts();
        List<TextSubmission> conflictingTextSubmissions = request.getList("/api/text-assessments/submission/" + textSubmissions.get(0).getId() + "/feedback/"
                + textSubmissions.get(0).getLatestResult().getFeedbacks().get(0).getId() + "/feedback-conflicts", HttpStatus.OK, TextSubmission.class);

        assertThat(conflictingTextSubmissions).hasSize(1);
        TextSubmission conflictingTextSubmission = conflictingTextSubmissions.get(0);
        assertThat(conflictingTextSubmission).isEqualTo(textSubmissions.get(1));
        assertThat(conflictingTextSubmission.getParticipation()).isNotNull();
        assertThat(conflictingTextSubmission.getLatestResult()).isEqualTo(textSubmissions.get(1).getLatestResult());
        assertThat(conflictingTextSubmission.getBlocks()).isNotNull();
        assertThat(conflictingTextSubmission.getLatestResult().getFeedbacks().get(0)).isEqualTo(textSubmissions.get(1).getLatestResult().getFeedbacks().get(0));
        assertThat(conflictingTextSubmission.getLatestResult().getFeedbacks().get(0).getResult()).isNull();
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void retrieveConflictingTextSubmissions_otherTutorForbidden() throws Exception {
        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedbackAndConflicts();
        request.getList("/api/text-assessments/submission/" + textSubmissions.get(0).getId() + "/feedback/" + textSubmissions.get(0).getLatestResult().getFeedbacks().get(0).getId()
                + "/feedback-conflicts", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void retrieveConflictingTextSubmissions_forNonExistingSubmission() throws Exception {
        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedbackAndConflicts();
        List<TextSubmission> conflictingTextSubmissions = request.getList(
                "/api/text-assessments/submission/123/feedback/" + textSubmissions.get(0).getLatestResult().getFeedbacks().get(0).getId() + "/feedback-conflicts",
                HttpStatus.BAD_REQUEST, TextSubmission.class);
        assertThat(conflictingTextSubmissions).as("passed submission should not be found").isNull();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void solveFeedbackConflict_tutor() throws Exception {
        FeedbackConflict feedbackConflict = solveFeedbackConflict(HttpStatus.OK);
        assertThat(feedbackConflict).isNotNull();
        assertThat(feedbackConflict.getSolvedAt()).isNotNull();
        assertThat(feedbackConflict.getConflict()).isEqualTo(false);
        assertThat(feedbackConflict.getDiscard()).isEqualTo(true);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void solveFeedbackConflict_otherTutor() throws Exception {
        solveFeedbackConflict(HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void solveFeedbackConflict_instructor() throws Exception {
        solveFeedbackConflict(HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "tutor3", roles = "TA")
    public void solveFeedbackConflict_forbiddenTutor() throws Exception {
        solveFeedbackConflict(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void solveFeedbackConflict_forNonExistingConflict() throws Exception {
        prepareTextSubmissionsWithFeedbackAndConflicts();
        FeedbackConflict feedbackConflict = request.get("/api/text-assessments/exercise/" + textExercise.getId() + "/feedbackConflict/2/solve-feedback-conflict",
                HttpStatus.BAD_REQUEST, FeedbackConflict.class);
        assertThat(feedbackConflict).as("feedback conflict should not be found").isNull();
    }

    private FeedbackConflict solveFeedbackConflict(HttpStatus expectedStatus) throws Exception {
        prepareTextSubmissionsWithFeedbackAndConflicts();
        return request.get(
                "/api/text-assessments/exercise/" + textExercise.getId() + "/feedbackConflict/" + feedbackConflictRepository.findAll().get(0).getId() + "/solve-feedback-conflict",
                expectedStatus, FeedbackConflict.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void multipleCorrectionRoundsForExam() throws Exception {
        // Setup exam with 2 correction rounds and a programming exercise
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();
        Exam exam = database.addExam(textExercise.getCourseViaExerciseGroupOrCourseMember());
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam.addExerciseGroup(exerciseGroup1);
        exam.setVisibleDate(ZonedDateTime.now().minusHours(3));
        exam.setStartDate(ZonedDateTime.now().minusHours(2));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        exam = examRepository.save(exam);

        Exam examWithExerciseGroups = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).get();
        exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().get(0);
        TextExercise exercise = ModelFactory.generateTextExerciseForExam(exerciseGroup1);
        exercise = exerciseRepo.save(exercise);
        exerciseGroup1.addExercise(exercise);

        // add student submission
        var studentParticipation = new StudentParticipation();
        studentParticipation.setExercise(exercise);
        studentParticipation.setParticipant(database.getUserByLogin("student1"));
        studentParticipation = studentParticipationRepository.save(studentParticipation);
        var submission = ModelFactory.generateTextSubmission("Text", Language.ENGLISH, true);
        submission.setParticipation(studentParticipation);
        submission = textSubmissionRepository.save(submission);

        // verify setup
        assertThat(exam.getNumberOfCorrectionRoundsInExam()).isEqualTo(2);
        assertThat(exam.getEndDate()).isBefore(ZonedDateTime.now());
        var optionalFetchedExercise = exerciseRepo.findWithEagerStudentParticipationsStudentAndSubmissionsById(exercise.getId());
        assertThat(optionalFetchedExercise.isPresent()).isTrue();
        final var exerciseWithParticipation = optionalFetchedExercise.get();
        studentParticipation = exerciseWithParticipation.getStudentParticipations().stream().iterator().next();

        // request to manually assess latest submission (correction round: 0)
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        params.add("correction-round", "0");
        TextSubmission submissionWithoutFirstAssessment = request.get("/api/exercises/" + exerciseWithParticipation.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);
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
        var assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList.size()).isEqualTo(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(0)).isEqualTo(submissionWithoutFirstAssessment.getLatestResult());

        // assess submission and submit
        List<Feedback> feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here")).collect(Collectors.toList());
        TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setFeedbacks(feedbacks);
        Result firstSubmittedManualResult = request.putWithResponseBody(
                "/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + submissionWithoutFirstAssessment.getLatestResult().getId() + "/submit", textAssessmentDTO,
                Result.class, HttpStatus.OK);

        // make sure that new result correctly appears after the assessment for first correction round
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList.size()).isEqualTo(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(0)).isNotNull();
        assertThat(firstSubmittedManualResult.getAssessor().getLogin()).isEqualTo("tutor1");

        // verify that the result contains the relationship
        assertThat(firstSubmittedManualResult).isNotNull();
        assertThat(firstSubmittedManualResult.getParticipation()).isEqualTo(studentParticipation);

        // verify that the relationship between student participation,
        var databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation.isPresent()).isTrue();
        var fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions().size()).isEqualTo(1);
        assertThat(fetchedParticipation.findLatestSubmission().isPresent()).isTrue();
        assertThat(fetchedParticipation.findLatestSubmission().get()).isEqualTo(submissionWithoutFirstAssessment);
        assertThat(fetchedParticipation.findLatestResult()).isEqualTo(firstSubmittedManualResult);

        var databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository
                .findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission.size()).isEqualTo(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.get(0);
        assertThat(fetchedParticipation.getSubmissions().size()).isEqualTo(1);
        assertThat(fetchedParticipation.findLatestSubmission().isPresent()).isTrue();
        // it should contain the lock for the manual result
        assertThat(fetchedParticipation.findLatestSubmission().get().getResults().size()).isEqualTo(1);
        assertThat(fetchedParticipation.findLatestSubmission().get().getLatestResult()).isEqualTo(firstSubmittedManualResult);

        // SECOND ROUND OF CORRECTION

        database.changeUser("tutor2");
        LinkedMultiValueMap<String, String> paramsSecondCorrection = new LinkedMultiValueMap<>();
        paramsSecondCorrection.add("lock", "true");
        paramsSecondCorrection.add("correction-round", "1");

        final var submissionWithoutSecondAssessment = request.get("/api/exercises/" + exerciseWithParticipation.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, paramsSecondCorrection);

        // verify that the submission is not new
        assertThat(submissionWithoutSecondAssessment).isEqualTo(submission);
        // verify that the lock has been set
        assertThat(submissionWithoutSecondAssessment.getLatestResult()).isNotNull();
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessor().getLogin()).isEqualTo("tutor2");
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.MANUAL);

        // verify that the relationship between student participation,
        databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation.isPresent()).isTrue();
        fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions().size()).isEqualTo(1);
        assertThat(fetchedParticipation.findLatestSubmission().isPresent()).isTrue();
        assertThat(fetchedParticipation.findLatestSubmission().get()).isEqualTo(submissionWithoutSecondAssessment);
        assertThat(fetchedParticipation.getResults().stream().filter(x -> x.getCompletionDate() == null).findFirst().get())
                .isEqualTo(submissionWithoutSecondAssessment.getLatestResult());

        databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission.size()).isEqualTo(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.get(0);
        assertThat(fetchedParticipation.getSubmissions().size()).isEqualTo(1);
        assertThat(fetchedParticipation.findLatestSubmission().isPresent()).isTrue();
        assertThat(fetchedParticipation.findLatestSubmission().get().getResults().size()).isEqualTo(2);
        assertThat(fetchedParticipation.findLatestSubmission().get().getLatestResult()).isEqualTo(submissionWithoutSecondAssessment.getLatestResult());

        // assess submission and submit
        Result secondSubmittedManualResult = request.putWithResponseBody(
                "/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + submissionWithoutSecondAssessment.getLatestResult().getId() + "/submit", textAssessmentDTO,
                Result.class, HttpStatus.OK);
        assertThat(secondSubmittedManualResult).isNotNull();

        // make sure that new result correctly appears after the assessment for second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR2 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR2.add("assessedByTutor", "true");
        paramsGetAssessedCR2.add("correction-round", "1");
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class,
                paramsGetAssessedCR2);

        assertThat(assessedSubmissionList.size()).isEqualTo(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutSecondAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(1)).isEqualTo(secondSubmittedManualResult);

        // make sure that they do not appear for the first correction round as the tutor only assessed the second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1.add("assessedByTutor", "true");
        paramsGetAssessedCR1.add("correction-round", "0");
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class,
                paramsGetAssessedCR1);

        assertThat(assessedSubmissionList.size()).isEqualTo(0);
    }

    @NotNull
    private List<TextSubmission> prepareTextSubmissionsWithFeedbackAndConflicts() {
        TextSubmission textSubmission1 = ModelFactory.generateTextSubmission("This is first submission's first sentence.", Language.ENGLISH, true);
        TextSubmission textSubmission2 = ModelFactory.generateTextSubmission("This is second submission's first sentence.", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission1, "student1");
        database.saveTextSubmission(textExercise, textSubmission2, "student2");

        final TextBlock textBlock1 = new TextBlock().startIndex(0).endIndex(42).automatic();
        final TextBlock textBlock2 = new TextBlock().startIndex(0).endIndex(43).automatic();
        database.addAndSaveTextBlocksToTextSubmission(Set.of(textBlock1), textSubmission1);
        database.addAndSaveTextBlocksToTextSubmission(Set.of(textBlock2), textSubmission2);

        final Feedback feedback1 = new Feedback().detailText("Good answer").credits(1D).reference(textBlock1.getId());
        final Feedback feedback2 = new Feedback().detailText("Bad answer").credits(2D).reference(textBlock2.getId());
        textSubmission1 = database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission1, "student1", "tutor1", List.of(feedback1));
        textSubmission2 = database.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission2, "student2", "tutor2", List.of(feedback2));

        // important: use the updated feedback that was already saved to the database and not the feedback1 and feedback2 objects
        FeedbackConflict feedbackConflict = ModelFactory.generateFeedbackConflictBetweenFeedbacks(textSubmission1.getLatestResult().getFeedbacks().get(0),
                textSubmission2.getLatestResult().getFeedbacks().get(0));
        feedbackConflictRepository.save(feedbackConflict);

        return asList(textSubmission1, textSubmission2);
    }

    public void addAssessmentFeedbackAndCheckScore(TextSubmission submissionWithoutAssessment, TextAssessmentDTO textAssessmentDTO, List<Feedback> feedbacks, double pointsAwarded,
            long expectedScore) throws Exception {
        feedbacks.add(new Feedback().credits(pointsAwarded).type(FeedbackType.MANUAL_UNREFERENCED).detailText("gj"));
        textAssessmentDTO.setFeedbacks(feedbacks);
        Result response = request.putWithResponseBody(
                "/api/text-assessments/exercise/" + textExercise.getId() + "/result/" + submissionWithoutAssessment.getLatestResult().getId() + "/submit", textAssessmentDTO,
                Result.class, HttpStatus.OK);
        assertThat(response.getScore()).isEqualTo(expectedScore);
    }
}
