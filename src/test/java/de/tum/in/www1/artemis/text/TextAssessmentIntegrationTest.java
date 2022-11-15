package de.tum.in.www1.artemis.text;

import static java.time.ZonedDateTime.now;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AutomaticTextFeedbackService;
import de.tum.in.www1.artemis.service.TextAssessmentService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.TextExerciseUtilService;
import de.tum.in.www1.artemis.web.rest.dto.TextAssessmentDTO;
import de.tum.in.www1.artemis.web.rest.dto.TextAssessmentUpdateDTO;

class TextAssessmentIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    private TextClusterRepository textClusterRepository;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ExampleSubmissionRepository exampleSubmissionRepository;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private FeedbackConflictRepository feedbackConflictRepository;

    @Autowired
    private AutomaticTextFeedbackService automaticTextFeedbackService;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private TextAssessmentService textAssessmentService;

    private TextExercise textExercise;

    private Course course;

    @BeforeEach
    void initTestCase() {
        database.addUsers(2, 3, 0, 1);
        course = database.addCourseWithOneReleasedTextExercise();
        textExercise = database.findTextExerciseWithTitle(course.getExercises(), "Text");
        textExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exerciseRepo.save(textExercise);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testPrepareSubmissionForAssessment() {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, false);
        textSubmission = database.saveTextSubmission(textExercise, textSubmission, "student1");
        textAssessmentService.prepareSubmissionForAssessment(textSubmission, null);
        var result = resultRepo.findDistinctBySubmissionId(textSubmission.getId());
        assertThat(result).isPresent();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testPrepareSubmissionForAssessmentAutomaticLabel() {
        // create two text blocks
        int submissionCount = 2;
        int submissionSize = 1;
        int numberOfBlocksTotally = submissionCount * submissionSize;
        var textBlocks = textExerciseUtilService.generateTextBlocksWithIdenticalTexts(numberOfBlocksTotally);

        // Create Exercise to save blocks & submissions
        TextExercise textExercise = textExerciseUtilService.createSampleTextExerciseWithSubmissions(course, new ArrayList<>(textBlocks), submissionCount, submissionSize);
        textExercise.setMaxPoints(1000D);
        exerciseRepository.save(textExercise);

        // Create a cluster and set minimal distance to < Threshold = 1
        int[] clusterSizes = { 2 };
        List<TextCluster> clusters = textExerciseUtilService.addTextBlocksToCluster(textBlocks, clusterSizes, textExercise);
        double[][] minimalDistanceMatrix = new double[numberOfBlocksTotally][numberOfBlocksTotally];
        // Fill each row with an arbitrary fixed value < 1 to stimulate a simple case of 10 automatic feedback suggestions
        for (double[] row : minimalDistanceMatrix) {
            Arrays.fill(row, 0.1);
        }
        clusters.get(0).blocks(textBlocks.stream().toList()).distanceMatrix(minimalDistanceMatrix);
        textClusterRepository.saveAll(clusters);

        // save textBLocks
        textBlockRepository.saveAll(textBlocks);

        var listOfSubmissions = textSubmissionRepository.getTextSubmissionsWithTextBlocksByExerciseId(textExercise.getId());
        TextSubmission firstSubmission = listOfSubmissions.get(0);
        TextSubmission secondSubmission = listOfSubmissions.get(1);

        // Create and set a sample result for submissions
        Result firstResult = createSampleResultForSubmission(firstSubmission);
        Result secondResult = createSampleResultForSubmission(secondSubmission);

        // Create a manual feedback and attach first block to it as reference
        Feedback feedback = new Feedback().credits(10.0).type(FeedbackType.MANUAL).detailText("gj");
        feedback.setReference(textBlocks.iterator().next().getId());

        // Set feedback to result
        firstResult.addFeedback(feedback);
        resultRepo.save(firstResult);
        feedback.setResult(firstResult);
        feedbackRepository.save(feedback);

        automaticTextFeedbackService.suggestFeedback(secondResult);

        assertThat(secondResult).as("saved result found").isNotNull();
        assertThat(secondResult.getFeedbacks().get(0).getReference()).isEqualTo(textBlocks.stream().toList().get(1).getId());
        assertThat(secondResult.getFeedbacks().get(0).getSuggestedFeedbackOriginSubmissionReference()).isEqualTo(firstSubmission.getId());
        assertThat(secondResult.getFeedbacks().get(0).getSuggestedFeedbackParticipationReference()).isEqualTo(firstSubmission.getParticipation().getId());
        assertThat(secondResult.getFeedbacks().get(0).getSuggestedFeedbackReference()).isEqualTo(feedback.getReference());
    }

    private Result createSampleResultForSubmission(Submission submission) {
        Result result = new Result();
        result.setAssessor(database.getUserByLogin("tutor1"));
        result.setCompletionDate(now());
        result.setSubmission(submission);
        submission.setResults(Collections.singletonList(result));
        return result;
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void retrieveParticipationForSubmission_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, false);
        textSubmission = database.saveTextSubmission(textExercise, textSubmission, "student1");

        StudentParticipation participationWithoutAssessment = request.get(
            "/api/participations/" + textSubmission.getParticipation().getId() + "/submissions/" + textSubmission.getId() + "/for-text-assessment", HttpStatus.OK,
            StudentParticipation.class);

        assertThat(participationWithoutAssessment).as("participation with submission was found").isNotNull();
        assertThat(participationWithoutAssessment.getSubmissions().iterator().next().getId()).as("participation with correct text submission was found")
            .isEqualTo(textSubmission.getId());
        assertThat(participationWithoutAssessment.getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void retrieveParticipationForLockedSubmission() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, false);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor2");
        Result result = textSubmission.getLatestResult();
        result.setCompletionDate(null); // assessment is still in progress for this test
        resultRepo.save(result);
        StudentParticipation participation = request.get(
            "/api/participations/" + textSubmission.getParticipation().getId() + "/submissions/" + textSubmission.getId() + "/for-text-assessment", HttpStatus.LOCKED,
            StudentParticipation.class);
        assertThat(participation).as("participation is locked and should not be returned").isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void retrieveParticipationForNonExistingSubmission() throws Exception {
        StudentParticipation participation = request.get("/api/participations/1/submissions/345395769256365/for-text-assessment", HttpStatus.NOT_FOUND, StudentParticipation.class);
        assertThat(participation).as("participation should not be found").isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testDeleteTextExampleAssessment() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission.setExampleSubmission(true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "instructor1");
        final var exampleSubmission = ModelFactory.generateExampleSubmission(textSubmission, textExercise, true);
        database.addExampleSubmission(exampleSubmission);
        request.delete("/api/exercises/" + exampleSubmission.getExercise().getId() + "/example-submissions/" + exampleSubmission.getId() + "/example-text-assessment/feedback",
            HttpStatus.NO_CONTENT);
        assertThat(exampleSubmissionRepository.findByIdWithEagerResultAndFeedbackElseThrow(exampleSubmission.getId()).getSubmission().getLatestResult()).isNull();
        assertThat(textBlockRepository.findAllWithEagerClusterBySubmissionId(exampleSubmission.getSubmission().getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testDeleteTextExampleAssessment_wrongExerciseId() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission.setExampleSubmission(true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "instructor1");
        final var exampleSubmission = ModelFactory.generateExampleSubmission(textSubmission, textExercise, true);
        database.addExampleSubmission(exampleSubmission);
        long randomId = 4532;
        request.delete("/api/exercises/" + randomId + "/example-submissions/" + exampleSubmission.getId() + "/example-text-assessment/feedback", HttpStatus.BAD_REQUEST);
        assertThat(exampleSubmissionRepository.findByIdWithEagerResultAndFeedbackElseThrow(exampleSubmission.getId()).getSubmission().getLatestResult().getFeedbacks()).isEmpty();
        assertThat(textBlockRepository.findAllWithEagerClusterBySubmissionId(exampleSubmission.getSubmission().getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "TA")
    void getTextSubmissionWithResultId() throws Exception {
        TextSubmission submission = ModelFactory.generateTextSubmission("asdf", null, true);
        submission = (TextSubmission) database.addSubmissionWithTwoFinishedResultsWithAssessor(textExercise, submission, "student1", "tutor1");
        Result storedResult = submission.getResultForCorrectionRound(1);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("resultId", String.valueOf(storedResult.getId()));
        StudentParticipation participation = request.get(
            "/api/participations/" + submission.getParticipation().getId() + "/submissions/" + submission.getId() + "/for-text-assessment", HttpStatus.OK,
            StudentParticipation.class, params);

        assertThat(participation.getResults()).isNotNull().contains(storedResult);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getTextSubmissionWithResultIdAsTutor_badRequest() throws Exception {
        TextSubmission submission = ModelFactory.generateTextSubmission("asdf", null, true);
        submission = (TextSubmission) database.addSubmissionWithTwoFinishedResultsWithAssessor(textExercise, submission, "student1", "tutor1");
        Result storedResult = submission.getResultForCorrectionRound(0);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("resultId", String.valueOf(storedResult.getId()));
        request.get("/api/participations/" + submission.getParticipation().getId() + "/submissions/" + submission.getId() + "/for-text-assessment", HttpStatus.FORBIDDEN,
            TextSubmission.class, params);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getTextSubmissionWithResultIdAsTutor_wrongParticipationId() throws Exception {
        TextSubmission submission = ModelFactory.generateTextSubmission("asdf", null, true);
        submission = (TextSubmission) database.addSubmissionWithTwoFinishedResultsWithAssessor(textExercise, submission, "student1", "tutor1");
        Result storedResult = submission.getResultForCorrectionRound(0);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("resultId", String.valueOf(storedResult.getId()));
        long randomId = 12534;

        TextSubmission returnedSubmission = request.get("/api/participations/" + randomId + "/submissions/" + submission.getId() + "/for-text-assessment", HttpStatus.BAD_REQUEST,
            TextSubmission.class, params);
        assertThat(returnedSubmission).isNull();
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void updateTextAssessmentAfterComplaint_wrongParticipationId() throws Exception {
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

        long randomId = 12354;
        Result updatedResult = request.putWithResponseBody("/api/participations/" + randomId + "/submissions/" + textSubmission.getId() + "/text-assessment-after-complaint",
            assessmentUpdate, Result.class, HttpStatus.BAD_REQUEST);

        assertThat(updatedResult).as("updated result found").isNull();
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void updateTextAssessmentAfterComplaint_studentHidden() throws Exception {
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

        Result updatedResult = request.putWithResponseBody(
            "/api/participations/" + textSubmission.getParticipation().getId() + "/submissions/" + textSubmission.getId() + "/text-assessment-after-complaint",
            assessmentUpdate, Result.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
        assertThat(((StudentParticipation) updatedResult.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void updateTextAssessmentAfterComplaint_withTextBlocks() throws Exception {
        // Setup
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("This is Part 1, and this is Part 2. There is also Part 3.", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        database.addAndSaveTextBlocksToTextSubmission(Set.of(new TextBlock().startIndex(0).endIndex(15).automatic(), new TextBlock().startIndex(16).endIndex(35).automatic(),
            new TextBlock().startIndex(36).endIndex(57).automatic()), textSubmission);

        Result textAssessment = textSubmission.getLatestResult();
        complaintRepo.save(new Complaint().result(textAssessment).complaintText("This is not fair"));

        // Get Text Submission and Complaint
        StudentParticipation participation = request.get(
            "/api/participations/" + textSubmission.getParticipation().getId() + "/submissions/" + textSubmission.getId() + "/for-text-assessment", HttpStatus.OK,
            StudentParticipation.class);
        final Complaint complaint = request.get("/api/complaints/submissions/" + textSubmission.getId(), HttpStatus.OK, Complaint.class);

        // Accept Complaint and update Assessment
        ComplaintResponse complaintResponse = database.createInitialEmptyResponse("tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        TextAssessmentUpdateDTO assessmentUpdate = new TextAssessmentUpdateDTO();
        assessmentUpdate.feedbacks(new ArrayList<>()).complaintResponse(complaintResponse);
        assessmentUpdate.setTextBlocks(new HashSet<>());

        Result updatedResult = request.putWithResponseBody(
            "/api/participations/" + textSubmission.getParticipation().getId() + "/submissions/" + textSubmission.getId() + "/text-assessment-after-complaint",
            assessmentUpdate, Result.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
    }

    private TextSubmission prepareSubmission() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");
        exerciseDueDatePassed();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        return request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK, TextSubmission.class, params);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void saveTextAssessment_studentHidden() throws Exception {
        TextSubmission submissionWithoutAssessment = prepareSubmission();

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setFeedbacks(new ArrayList<>());

        Result result = request.putWithResponseBody("/api/participations/" + submissionWithoutAssessment.getParticipation().getId() + "/results/"
            + submissionWithoutAssessment.getLatestResult().getId() + "/text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void saveTextAssessment_wrongParticipationId() throws Exception {

        TextSubmission submissionWithoutAssessment = prepareSubmission();

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setFeedbacks(new ArrayList<>());

        long randomId = 1343;

        Result result = request.putWithResponseBody("/api/participations/" + randomId + "/results/" + submissionWithoutAssessment.getLatestResult().getId() + "/text-assessment",
            textAssessmentDTO, Result.class, HttpStatus.BAD_REQUEST);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void submitTextAssessment_studentHidden() throws Exception {

        TextSubmission submissionWithoutAssessment = prepareSubmission();
        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setFeedbacks(new ArrayList<>());
        Result result = request.postWithResponseBody("/api/participations/" + submissionWithoutAssessment.getParticipation().getId() + "/results/"
            + submissionWithoutAssessment.getLatestResult().getId() + "/submit-text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(((StudentParticipation) result.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void submitTextAssessment_wrongParticipationId() throws Exception {
        TextSubmission submissionWithoutAssessment = prepareSubmission();

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setFeedbacks(new ArrayList<>());
        long randomId = 1548;
        Result result = request.postWithResponseBody(
            "/api/participations/" + randomId + "/results/" + submissionWithoutAssessment.getLatestResult().getId() + "/submit-text-assessment", textAssessmentDTO,
            Result.class, HttpStatus.BAD_REQUEST);

        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void setNumberOfAffectedSubmissionsPerBlock_withIdenticalTextBlocks() throws Exception {
        int submissionCount = 5;
        int submissionSize = 1;
        int numberOfBlocksTotally = submissionCount * submissionSize;
        int[] clusterSizes = new int[] { 5 };
        var textBlocks = textExerciseUtilService.generateTextBlocksWithIdenticalTexts(numberOfBlocksTotally);
        TextExercise textExercise = textExerciseUtilService.createSampleTextExerciseWithSubmissions(course, new ArrayList<>(textBlocks), submissionCount, submissionSize);
        textBlocks.forEach(TextBlock::computeId);
        List<TextCluster> clusters = textExerciseUtilService.addTextBlocksToCluster(textBlocks, clusterSizes, textExercise);
        textClusterRepository.saveAll(clusters);
        textBlockRepository.saveAll(textBlocks);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        TextSubmission submissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
            TextSubmission.class, params);
        var participation = textExercise.getStudentParticipations().stream().findFirst().get();
        Result result = new Result();
        result.setParticipation(participation);
        result.setSubmission(submissionWithoutAssessment);

        textBlockService.setNumberOfAffectedSubmissionsPerBlock(result);

        assertThat(result).as("saved result found").isNotNull();
        assertThat(submissionWithoutAssessment.getBlocks()).hasSize(1);
        assertThat(submissionWithoutAssessment.getBlocks().stream().findFirst()).isPresent().get().hasFieldOrPropertyWithValue("numberOfAffectedSubmissions",
            numberOfBlocksTotally - 1);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getResult_studentHidden() throws Exception {
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
    @WithMockUser(username = "tutor1", roles = "TA")
    void getParticipationForNonTextExercise() throws Exception {
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
    @WithMockUser(username = "student1", roles = "USER")
    void getDataForTextEditor_assessorHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");

        Participation participation = request.get("/api/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, Participation.class);

        assertThat(participation).as("participation found").isNotNull();
        assertThat(participation.getResults().iterator().next()).as("result found").isNotNull();
        assertThat(participation.getResults().iterator().next().getAssessor()).as("assessor of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getDataForTextEditorForNonTextExercise_badRequest() throws Exception {
        FileUploadExercise fileUploadExercise = ModelFactory.generateFileUploadExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1),
            ZonedDateTime.now().plusDays(2), "png,pdf", textExercise.getCourseViaExerciseGroupOrCourseMember());
        exerciseRepo.save(fileUploadExercise);

        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        database.saveFileUploadSubmissionWithResultAndAssessorFeedback(fileUploadExercise, fileUploadSubmission, "student1", "tutor1", new ArrayList<>());

        request.get("/api/text-editor/" + fileUploadSubmission.getParticipation().getId(), HttpStatus.BAD_REQUEST, Participation.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getDataForTextEditor_hasTextBlocks() throws Exception {
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
    @WithMockUser(username = "student2", roles = "USER")
    void getDataForTextEditor_asOtherStudent() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        request.get("/api/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.FORBIDDEN, Participation.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getDataForTextEditor_BeforeExamPublishDate_Forbidden() throws Exception {
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
    @WithMockUser(username = "tutor1", roles = "TA")
    void getDataForTextEditor_studentHidden() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");

        StudentParticipation participation = request.get("/api/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, StudentParticipation.class);

        assertThat(participation).as("participation found").isNotNull();
        assertThat(participation.getResults().iterator().next()).as("result found").isNotNull();
        assertThat(participation.getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getDataForTextEditor_submissionWithoutResult() throws Exception {
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

        Result result = request.getNullable("/api/exercises/" + textExercise.getId() + "/submissions/" + textSubmission.getId() + "/example-result", expectedStatus, Result.class);

        if (expectedStatus == HttpStatus.OK && result != null) {
            assertThat(result.getId() == null).isTrue();
            for (Feedback feedback : result.getFeedbacks()) {
                assertThat(feedback.getCredits()).isNull();
                assertThat(feedback.getDetailText()).isNull();
                assertThat(feedback.getReference()).isNotNull();
            }
        }

        return result;
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getExampleResultForTutor_wrongExerciseId() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission.setExampleSubmission(true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "instructor1");
        final var exampleSubmission = ModelFactory.generateExampleSubmission(textSubmission, textExercise, true);
        database.addExampleSubmission(exampleSubmission);
        long randomId = 23454;
        Result result = request.get("/api/exercises/" + randomId + "/submissions/" + textSubmission.getId() + "/example-result", HttpStatus.BAD_REQUEST, Result.class);

        assertThat(result).as("result found").isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getExampleResultForTutorAsStudent() throws Exception {
        getExampleResultForTutor(HttpStatus.FORBIDDEN, true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getExampleResultForTutorAsTutor() throws Exception {
        // TODO: somehow this test fails in IntelliJ but passes when executed on the command line?!?
        getExampleResultForTutor(HttpStatus.OK, true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getExampleResultForNonExampleSubmissionAsTutor() throws Exception {
        getExampleResultForTutor(HttpStatus.OK, false);
    }

    private void cancelAssessment(HttpStatus expectedStatus) throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = database.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");
        database.addSampleFeedbackToResults(textSubmission.getLatestResult());
        request.postWithoutLocation("/api/participations/" + textSubmission.getParticipation().getId() + "/submissions/" + textSubmission.getId() + "/cancel-assessment", null,
            expectedStatus, null);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void cancelOwnAssessmentAsStudent() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void cancelOwnAssessmentAsTutor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void cancelAssessmentOfOtherTutorAsTutor() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void cancelAssessmentOfOtherTutorAsInstructor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void cancelAssessment_wrongSubmissionId() throws Exception {
        request.post("/api/participations/1/submissions/" + 1000000000 + "/cancel-assessment", null, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testOverrideAssessment_saveOtherTutorForbidden() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_saveInstructorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideAssessment_saveSameTutorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testOverrideAssessment_submitOtherTutorForbidden() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_submitInstructorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testOverrideAssessment_saveOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_saveInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideAssessment_saveSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideAssessment_saveSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", false);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testOverrideAssessment_submitOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_submitInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", false);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testSubmitAssessment_IncludedCompletelyWithBonusPointsExercise() throws Exception {
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

        Course course = request.get("/api/courses/" + textExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-assessment-dashboard", HttpStatus.OK, Course.class);
        Exercise exercise = (Exercise) course.getExercises().toArray()[0];
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()).hasSize(1);
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(1L);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testSubmitAssessment_IncludedCompletelyWithoutBonusPointsExercise() throws Exception {
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
    @WithMockUser(username = "tutor1", roles = "TA")
    void testSubmitAssessment_IncludedAsBonusExercise() throws Exception {
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
    @WithMockUser(username = "tutor1", roles = "TA")
    void testSubmitAssessment_NotIncludedExercise() throws Exception {
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
    @WithMockUser(username = "tutor1", roles = "TA")
    void testSubmitAssessment_withResultOver100Percent() throws Exception {
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
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1")
            .reference(ModelFactory.generateTextBlock(0, 5, "test1").getId()));
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 2")
            .reference(ModelFactory.generateTextBlock(0, 5, "test2").getId()));
        textAssessmentDTO.setFeedbacks(feedbacks);

        // Check that result is over 100% -> 105
        Result response = request.postWithResponseBody("/api/participations/" + submissionWithoutAssessment.getParticipation().getId() + "/results/"
            + submissionWithoutAssessment.getLatestResult().getId() + "/submit-text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(105);

        feedbacks.add(new Feedback().credits(20.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 3")
            .reference(ModelFactory.generateTextBlock(0, 5, "test3").getId()));
        textAssessmentDTO.setFeedbacks(feedbacks);

        // Check that result is capped to maximum of maxScore + bonus points -> 110
        response = request.postWithResponseBody("/api/participations/" + submissionWithoutAssessment.getParticipation().getId() + "/results/"
            + submissionWithoutAssessment.getLatestResult().getId() + "/submit-text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(110, Offset.offset(0.0001));
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
        var path = "/api/participations/" + textSubmission.getParticipation().getId() + "/results/" + textSubmission.getLatestResult().getId() + "/text-assessment";
        var body = new TextAssessmentDTO(feedbacks);
        if (submit.equals("true")) {
            path = "/api/participations/" + textSubmission.getParticipation().getId() + "/results/" + textSubmission.getLatestResult().getId() + "/submit-text-assessment";
            request.postWithResponseBody(path, body, Result.class, httpStatus);
        }
        else {
            request.putWithResponseBodyAndParams(path, body, Result.class, httpStatus, params);
        }
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testTextBlocksAreConsistentWhenOpeningSameAssessmentTwiceWithAtheneEnabled() throws Exception {
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
        request.postWithResponseBody(
            "/api/participations/" + submission1stRequest.getParticipation().getId() + "/results/" + submission1stRequest.getLatestResult().getId() + "/submit-text-assessment",
            textAssessmentDTO, Result.class, HttpStatus.OK);

        Participation participation2ndRequest = request.get(
            "/api/participations/" + textSubmission.getParticipation().getId() + "/submissions/" + textSubmission.getId() + "/for-text-assessment", HttpStatus.OK,
            Participation.class, params);
        TextSubmission submission2ndRequest = (TextSubmission) (participation2ndRequest).getSubmissions().iterator().next();
        var blocksFrom2ndRequest = submission2ndRequest.getBlocks();
        assertThat(blocksFrom2ndRequest.toArray()).containsExactlyInAnyOrder(blocks.toArray());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void checkTextSubmissionWithoutAssessmentAndRetrieveParticipationForSubmissionReturnSameBlocksAndFeedback() throws Exception {
        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedbackForAutomaticFeedback();

        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("lock", "true");
        TextSubmission textSubmissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
            TextSubmission.class, parameters);

        request.put("/api/participations/" + textSubmissions.get(0).getParticipation().getId() + "/submissions/" + textSubmissions.get(0).getId() + "/cancel-assessment", null,
            HttpStatus.OK);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        Participation participation = request.get(
            "/api/participations/" + textSubmissions.get(0).getParticipation().getId() + "/submissions/" + textSubmissions.get(0).getId() + "/for-text-assessment",
            HttpStatus.OK, Participation.class, params);
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
    @WithMockUser(username = "tutor1", roles = "TA")
    void checkTextBlockSavePreservesClusteringInformation() throws Exception {
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

        request.putWithResponseBody("/api/participations/" + textSubmissionWithoutAssessment.getParticipation().getId() + "/results/"
            + textSubmissionWithoutAssessment.getLatestResult().getId() + "/text-assessment", dto, Result.class, HttpStatus.OK);

        textBlockRepository.findAllWithEagerClusterBySubmissionId(textSubmissionWithoutAssessment.getId())
            .forEach(block -> assertThat(block).isEqualToComparingFieldByField(blocksSubmission1.get(block.getId())));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void checkTrackingTokenHeader() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");
        exerciseDueDatePassed();

        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("lock", "true");

        request.getWithHeaders("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK, TextSubmission.class, parameters, new HttpHeaders(),
            new String[] { "X-Athene-Tracking-Authorization" });

    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void retrieveConflictingTextSubmissions() throws Exception {
        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedbackAndConflict();
        List<TextSubmission> conflictingTextSubmissions = request.getList("/api/participations/" + textSubmissions.get(0).getParticipation().getId() + "/submissions/"
                + textSubmissions.get(0).getId() + "/feedbacks/" + textSubmissions.get(0).getLatestResult().getFeedbacks().get(0).getId() + "/feedback-conflicts", HttpStatus.OK,
            TextSubmission.class);

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
    @WithMockUser(username = "tutor1", roles = "TA")
    void retrieveConflictingTextSubmissions_wrongParticipationId() throws Exception {
        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedbackAndConflict();
        long randomId = 54234;
        List<TextSubmission> conflictingTextSubmissions = request.getList("/api/participations/" + randomId + "/submissions/" + textSubmissions.get(0).getId() + "/feedbacks/"
            + textSubmissions.get(0).getLatestResult().getFeedbacks().get(0).getId() + "/feedback-conflicts", HttpStatus.BAD_REQUEST, TextSubmission.class);

        assertThat(conflictingTextSubmissions).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void retrieveConflictingTextSubmissions_automaticAssessmentDisabled() throws Exception {
        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedbackAndConflict();
        textExercise.setAssessmentType(AssessmentType.MANUAL);
        exerciseRepo.save(textExercise);
        List<TextSubmission> conflictingTextSubmissions = request.getList("/api/participations/" + textSubmissions.get(0).getParticipation().getId() + "/submissions/"
                + textSubmissions.get(0).getId() + "/feedbacks/" + textSubmissions.get(0).getLatestResult().getFeedbacks().get(0).getId() + "/feedback-conflicts",
            HttpStatus.BAD_REQUEST, TextSubmission.class);
        assertThat(conflictingTextSubmissions).isNull();
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void retrieveConflictingTextSubmissions_otherTutorForbidden() throws Exception {
        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedbackAndConflict();
        request.getList("/api/participations/" + textSubmissions.get(0).getParticipation().getId() + "/submissions/" + textSubmissions.get(0).getId() + "/feedbacks/"
            + textSubmissions.get(0).getLatestResult().getFeedbacks().get(0).getId() + "/feedback-conflicts", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void retrieveConflictingTextSubmissions_forNonExistingSubmission() throws Exception {
        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedbackAndConflict();
        List<TextSubmission> conflictingTextSubmissions = request.getList("/api/participations/" + textSubmissions.get(0).getParticipation().getId() + "/submissions/123/feedbacks/"
            + textSubmissions.get(0).getLatestResult().getFeedbacks().get(0).getId() + "/feedback-conflicts", HttpStatus.BAD_REQUEST, TextSubmission.class);
        assertThat(conflictingTextSubmissions).as("passed submission should not be found").isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void solveFeedbackConflict_tutor() throws Exception {
        FeedbackConflict feedbackConflict = solveFeedbackConflict(HttpStatus.OK);
        assertThat(feedbackConflict).isNotNull();
        assertThat(feedbackConflict.getSolvedAt()).isNotNull();
        assertThat(feedbackConflict.getConflict()).isFalse();
        assertThat(feedbackConflict.getDiscard()).isTrue();
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void solveFeedbackConflict_otherTutor() throws Exception {
        solveFeedbackConflict(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void solveFeedbackConflict_instructor() throws Exception {
        solveFeedbackConflict(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "tutor3", roles = "TA")
    void solveFeedbackConflict_forbiddenTutor() throws Exception {
        solveFeedbackConflict(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void solveFeedbackConflict_forNonExistingConflict() throws Exception {
        prepareTextSubmissionsWithFeedbackAndConflict();

        FeedbackConflict feedbackConflict = request.postWithResponseBody("/api/exercises/" + textExercise.getId() + "/feedback-conflicts/2/solve", null, FeedbackConflict.class,
            HttpStatus.BAD_REQUEST);
        assertThat(feedbackConflict).as("feedback conflict should not be found").isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void solveFeedbackConflict_wrongExerciseId() throws Exception {
        prepareTextSubmissionsWithFeedbackAndConflict();
        long randomId = 57456;
        FeedbackConflict feedbackConflict = request.postWithResponseBody(
            "/api/exercises/" + randomId + "/feedback-conflicts/" + feedbackConflictRepository.findAll().get(0).getId() + "/solve", null, FeedbackConflict.class,
            HttpStatus.BAD_REQUEST);
        assertThat(feedbackConflict).as("feedback conflict should not be found").isNull();
    }

    private FeedbackConflict solveFeedbackConflict(HttpStatus expectedStatus) throws Exception {
        FeedbackConflict feedbackConflict = prepareTextSubmissionsWithFeedbackAndConflictGetConflict();
        return request.postWithResponseBody("/api/exercises/" + textExercise.getId() + "/feedback-conflicts/" + feedbackConflict.getId() + "/solve",
            null, FeedbackConflict.class, expectedStatus);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = AssessmentType.class, names = { "SEMI_AUTOMATIC", "MANUAL" })
    @WithMockUser(username = "tutor1", roles = "TA")
    void multipleCorrectionRoundsForExam(AssessmentType assessmentType) throws Exception {
        // Setup exam with 2 correction rounds and a text exercise
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
        exercise.setAssessmentType(assessmentType);
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
        assertThat(optionalFetchedExercise).isPresent();
        final var exerciseWithParticipation = optionalFetchedExercise.get();
        studentParticipation = exerciseWithParticipation.getStudentParticipations().stream().iterator().next();

        database.changeUser("instructor1");
        // check properties set
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("withExerciseGroups", "true");
        Exam examReturned = request.get("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        examReturned.getExerciseGroups().get(0).getExercises().forEach(exerciseExamReturned -> {
            assertThat(exerciseExamReturned.getNumberOfParticipations()).isNotNull();
            assertThat(exerciseExamReturned.getNumberOfParticipations()).isEqualTo(1);
        });
        database.changeUser("tutor1");

        // request to manually assess latest submission (correction round: 0)
        params = new LinkedMultiValueMap<>();
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

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(0)).isEqualTo(submissionWithoutFirstAssessment.getLatestResult());

        // assess submission and submit
        List<Feedback> feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> {
            feedback.setDetailText("Good work here");
        }).collect(Collectors.toCollection(ArrayList::new));
        TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setFeedbacks(feedbacks);
        Result firstSubmittedManualResult = request.postWithResponseBody("/api/participations/" + submissionWithoutFirstAssessment.getParticipation().getId() + "/results/"
            + submissionWithoutFirstAssessment.getLatestResult().getId() + "/submit-text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);

        // make sure that new result correctly appears after the assessment for first correction round
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class,
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

        final var submissionWithoutSecondAssessment = request.get("/api/exercises/" + exerciseWithParticipation.getId() + "/text-submission-without-assessment", HttpStatus.OK,
            TextSubmission.class, paramsSecondCorrection);

        // verify that the submission is not new
        assertThat(submissionWithoutSecondAssessment).isEqualTo(submission);
        // verify that the lock has been set
        assertThat(submissionWithoutSecondAssessment.getLatestResult()).isNotNull();
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessor().getLogin()).isEqualTo("tutor2");
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getFeedbacks()).isNotEmpty();

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

        Feedback secondCorrectionFeedback = new Feedback();
        secondCorrectionFeedback.setDetailText("asfd");
        secondCorrectionFeedback.setCredits(10.0);
        secondCorrectionFeedback.setPositive(true);
        submissionWithoutSecondAssessment.getLatestResult().getFeedbacks().add(secondCorrectionFeedback);
        textAssessmentDTO.setFeedbacks(submissionWithoutSecondAssessment.getLatestResult().getFeedbacks());

        // assess submission and submit
        Result secondSubmittedManualResult = request.postWithResponseBody("/api/participations/" + submissionWithoutFirstAssessment.getParticipation().getId() + "/results/"
            + submissionWithoutSecondAssessment.getLatestResult().getId() + "/submit-text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);
        assertThat(secondSubmittedManualResult).isNotNull();

        // check if feedback copy was set correctly
        assertThat(secondSubmittedManualResult.getFeedbacks()).isNotEmpty();

        // make sure that new result correctly appears after the assessment for second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR2 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR2.add("assessedByTutor", "true");
        paramsGetAssessedCR2.add("correction-round", "1");
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class,
            paramsGetAssessedCR2);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutSecondAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(1)).isEqualTo(secondSubmittedManualResult);

        // make sure that they do not appear for the first correction round as the tutor only assessed the second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1.add("assessedByTutor", "true");
        paramsGetAssessedCR1.add("correction-round", "0");
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class,
            paramsGetAssessedCR1);

        assertThat(assessedSubmissionList).isEmpty();

        // Student should not have received a result over WebSocket as manual correction is ongoing
        verify(messagingTemplate, never()).convertAndSendToUser(notNull(), eq(Constants.NEW_RESULT_TOPIC), isA(Result.class));
    }

    @NotNull
    private List<TextSubmission> prepareTextSubmissionsWithFeedbackAndConflict() {
        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedback();
        TextSubmission textSubmission1 = textSubmissions.get(0);
        TextSubmission textSubmission2 = textSubmissions.get(1);
        prepareFeedbackConflict(textSubmission1, textSubmission2);
        return asList(textSubmission1, textSubmission2);
    }

    @NotNull
    private FeedbackConflict prepareTextSubmissionsWithFeedbackAndConflictGetConflict() {
        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedback();
        TextSubmission textSubmission1 = textSubmissions.get(0);
        TextSubmission textSubmission2 = textSubmissions.get(1);
        return prepareFeedbackConflict(textSubmission1, textSubmission2);
    }

    @NotNull
    private List<TextSubmission> prepareTextSubmissionsWithFeedback() {
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

        return asList(textSubmission1, textSubmission2);
    }

    @NotNull
    private FeedbackConflict prepareFeedbackConflict(TextSubmission textSubmission1, TextSubmission textSubmission2) {
        FeedbackConflict feedbackConflict = ModelFactory.generateFeedbackConflictBetweenFeedbacks(textSubmission1.getLatestResult().getFeedbacks().get(0),
            textSubmission2.getLatestResult().getFeedbacks().get(0));
        feedbackConflictRepository.save(feedbackConflict);

        return feedbackConflict;
    }

    private void addAssessmentFeedbackAndCheckScore(TextSubmission submissionWithoutAssessment, TextAssessmentDTO textAssessmentDTO, List<Feedback> feedbacks, double pointsAwarded,
                                                    long expectedScore) throws Exception {
        feedbacks.add(new Feedback().credits(pointsAwarded).type(FeedbackType.MANUAL_UNREFERENCED).detailText("gj")
            .reference(ModelFactory.generateTextBlock(0, 5, "test" + feedbacks.size()).getId()));
        textAssessmentDTO.setFeedbacks(feedbacks);
        Result response = request.postWithResponseBody("/api/participations/" + submissionWithoutAssessment.getParticipation().getId() + "/results/"
            + submissionWithoutAssessment.getLatestResult().getId() + "/submit-text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);
        assertThat(response.getScore()).isEqualTo(expectedScore);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testdeleteResult() throws Exception {
        Course course = database.addCourseWithOneExerciseAndSubmissions("text", 1);
        Exercise exercise = exerciseRepository.findAllExercisesByCourseId(course.getId()).iterator().next();
        database.addAssessmentToExercise(exercise, database.getUserByLogin("tutor1"));
        database.addAssessmentToExercise(exercise, database.getUserByLogin("tutor2"));

        var submissions = database.getAllSubmissionsOfExercise(exercise);
        Submission submission = submissions.get(0);
        assertThat(submission.getResults()).hasSize(2);
        Result firstResult = submission.getResults().get(0);
        Result lastResult = submission.getLatestResult();
        request.delete("/api/participations/" + submission.getParticipation().getId() + "/text-submissions/" + submission.getId() + "/results/" + firstResult.getId(),
            HttpStatus.OK);
        submission = submissionRepository.findOneWithEagerResultAndFeedback(submission.getId());
        assertThat(submission.getResults()).hasSize(1);
        assertThat(submission.getResults().get(0)).isEqualTo(lastResult);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testFeedbackIdIsSetCorrectly() throws Exception {
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("This is Part 1, and this is Part 2. There is also Part 3.", Language.ENGLISH, true);
        database.saveTextSubmission(textExercise, textSubmission, "student1");
        exerciseDueDatePassed();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        TextSubmission submissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
            TextSubmission.class, params);

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setTextBlocks(Set.of(ModelFactory.generateTextBlock(0, 15, "This is Part 1,"), ModelFactory.generateTextBlock(16, 35, " and this is Part 2."),
            ModelFactory.generateTextBlock(36, 57, " There is also Part 3.")));

        List<Feedback> feedbacks = new ArrayList<>();
        textAssessmentDTO.getTextBlocks()
            .forEach(textBlock -> feedbacks.add(new Feedback().type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1").reference(textBlock.getId())));
        textAssessmentDTO.setFeedbacks(feedbacks);

        request.postWithResponseBody("/api/participations/" + submissionWithoutAssessment.getParticipation().getId() + "/results/"
            + submissionWithoutAssessment.getLatestResult().getId() + "/submit-text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);

        textBlockRepository.findAllBySubmissionId(submissionWithoutAssessment.getId()).forEach(textBlock -> {
            assertThat(textBlock.getFeedback().getId()).isNotNull();
        });
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testConsecutiveSaveFailsAfterAddingOrRemovingReferencedFeedback() throws Exception {

        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedbackForAutomaticFeedback();
        final Map<String, TextBlock> blocksSubmission1 = textSubmissions.get(0).getBlocks().stream().collect(Collectors.toMap(TextBlock::getId, block -> block));

        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("lock", "true");
        TextSubmission textSubmissionWithoutAssessment = request.get("/api/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
            TextSubmission.class, parameters);

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

        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().credits(20.00).type(FeedbackType.MANUAL).detailText("nice submission 3").reference(ModelFactory.generateTextBlock(0, 15, "test3").getId()));
        dto.setFeedbacks(feedbacks);

        // These two lines ensure the call count verification near the end of this test can spot the call
        // made during the PUT /api/participations/:id/text-assessment request and not other places.
        int irrelevantCallCount = 1;
        verify(textBlockService, times(irrelevantCallCount)).findAllBySubmissionId(textSubmissionWithoutAssessment.getId());

        request.putWithResponseBody("/api/participations/" + textSubmissionWithoutAssessment.getParticipation().getId() + "/results/"
            + textSubmissionWithoutAssessment.getLatestResult().getId() + "/text-assessment", dto, Result.class, HttpStatus.OK);

        feedbacks.remove(0);

        request.putWithResponseBody("/api/participations/" + textSubmissionWithoutAssessment.getParticipation().getId() + "/results/"
            + textSubmissionWithoutAssessment.getLatestResult().getId() + "/text-assessment", dto, Result.class, HttpStatus.OK);

        var result = request.putWithResponseBody("/api/participations/" + textSubmissionWithoutAssessment.getParticipation().getId() + "/results/"
            + textSubmissionWithoutAssessment.getLatestResult().getId() + "/text-assessment", dto, Result.class, HttpStatus.OK);

        final var textSubmission = textSubmissionRepository.getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(result.getId());

        // This is to ensure the fix for https://github.com/ls1intum/Artemis/issues/4962 is not removed. Please ensure that problem is not occurring
        // if you remove or change this verification. This test uses a spy because the error was caused by and EntityNotFound exception related to MySQL
        // however the intergration tests use H2 in-memory database so same code did not produce the same error.
        verify(textBlockService, times(irrelevantCallCount + 3)).findAllBySubmissionId(textSubmission.getId());

        textBlockRepository.findAllWithEagerClusterBySubmissionId(textSubmissionWithoutAssessment.getId())
            .forEach(block -> assertThat(block).isEqualToComparingFieldByField(blocksSubmission1.get(block.getId())));
    }
}
