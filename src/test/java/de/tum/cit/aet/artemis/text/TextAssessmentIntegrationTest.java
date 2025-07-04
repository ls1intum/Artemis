package de.tum.cit.aet.artemis.text;

import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_TEXT_TEST;
import static java.time.ZonedDateTime.now;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUpdateDTO;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ExampleSubmissionTestRepository;
import de.tum.cit.aet.artemis.assessment.util.ComplaintUtilService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseFactory;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.TextAssessmentDTO;
import de.tum.cit.aet.artemis.text.dto.TextAssessmentUpdateDTO;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.service.TextAssessmentService;
import de.tum.cit.aet.artemis.text.test_repository.TextSubmissionTestRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class TextAssessmentIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "textassessment";

    @Autowired
    private ComplaintRepository complaintRepo;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private TextSubmissionTestRepository textSubmissionRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private ExampleSubmissionTestRepository exampleSubmissionRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private ExamTestRepository examTestRepository;

    @Autowired
    private TextAssessmentService textAssessmentService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ComplaintUtilService complaintUtilService;

    private TextExercise textExercise;

    private Course course;

    @Autowired
    protected AthenaRequestMockProvider athenaRequestMockProvider;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 3, 0, 1);
        course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        textExercise = ExerciseUtilService.findTextExerciseWithTitle(course.getExercises(), "Text");
        textExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exerciseRepository.save(textExercise);
        // every test indirectly uses the submission selection in Athena, so we mock it here
        athenaRequestMockProvider.enableMockingOfRequests();
        athenaRequestMockProvider.mockSelectSubmissionsAndExpect("text", 0); // always select the first submission
    }

    @AfterEach
    void tearDown() throws Exception {
        athenaRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testPrepareSubmissionForAssessment() {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, false);
        textSubmission = textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        textAssessmentService.prepareSubmissionForAssessment(textSubmission, null);
        var result = resultRepository.findDistinctBySubmissionId(textSubmission.getId());
        assertThat(result).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void retrieveParticipationForSubmission_studentHidden() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, false);
        textSubmission = textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");

        StudentParticipation participationWithoutAssessment = request.get("/api/text/text-submissions/" + textSubmission.getId() + "/for-assessment", HttpStatus.OK,
                StudentParticipation.class);

        assertThat(participationWithoutAssessment).as("participation with submission was found").isNotNull();
        assertThat(participationWithoutAssessment.getSubmissions().iterator().next().getId()).as("participation with correct text submission was found")
                .isEqualTo(textSubmission.getId());
        assertThat(participationWithoutAssessment.getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void retrieveParticipationForLockedSubmission() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, false);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor2");
        Result result = textSubmission.getLatestResult();
        result.setCompletionDate(null); // assessment is still in progress for this test
        resultRepository.save(result);
        StudentParticipation participation = request.get("/api/text/text-submissions/" + textSubmission.getId() + "/for-assessment", HttpStatus.LOCKED, StudentParticipation.class);
        assertThat(participation).as("participation is locked and should not be returned").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void retrieveParticipationForNonExistingSubmission() throws Exception {
        StudentParticipation participation = request.get("/api/text/text-submissions/345395769256365/for-assessment", HttpStatus.NOT_FOUND, StudentParticipation.class);
        assertThat(participation).as("participation should not be found").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeleteTextExampleAssessment() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission.setExampleSubmission(true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "instructor1");
        final var exampleSubmission = ParticipationFactory.generateExampleSubmission(textSubmission, textExercise, true);
        participationUtilService.addExampleSubmission(exampleSubmission);
        request.delete("/api/text/exercises/" + exampleSubmission.getExercise().getId() + "/example-submissions/" + exampleSubmission.getId() + "/example-text-assessment/feedback",
                HttpStatus.NO_CONTENT);
        assertThat(exampleSubmissionRepository.findByIdWithEagerResultAndFeedbackElseThrow(exampleSubmission.getId()).getSubmission().getLatestResult()).isNull();
        assertThat(textBlockRepository.findAllBySubmissionId(exampleSubmission.getSubmission().getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeleteTextExampleAssessment_wrongExerciseId() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission.setExampleSubmission(true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "instructor1");
        final var exampleSubmission = ParticipationFactory.generateExampleSubmission(textSubmission, textExercise, true);
        participationUtilService.addExampleSubmission(exampleSubmission);
        long randomId = 4532;
        request.delete("/api/text/exercises/" + randomId + "/example-submissions/" + exampleSubmission.getId() + "/example-text-assessment/feedback", HttpStatus.BAD_REQUEST);
        assertThat(exampleSubmissionRepository.findByIdWithEagerResultAndFeedbackElseThrow(exampleSubmission.getId()).getSubmission().getLatestResult().getFeedbacks()).isEmpty();
        assertThat(textBlockRepository.findAllBySubmissionId(exampleSubmission.getSubmission().getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "TA")
    void getTextSubmissionWithResultId() throws Exception {
        TextSubmission submission = ParticipationFactory.generateTextSubmission("asdf", null, true);
        submission = (TextSubmission) participationUtilService.addSubmissionWithTwoFinishedResultsWithAssessor(textExercise, submission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        Result storedResult = submission.getResultForCorrectionRound(1);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("resultId", String.valueOf(storedResult.getId()));
        StudentParticipation participation = request.get("/api/text/text-submissions/" + submission.getId() + "/for-assessment", HttpStatus.OK, StudentParticipation.class, params);

        assertThat(participation.getSubmissions().stream().findFirst().isPresent()).isTrue();
        assertThat(participation.getSubmissions().stream().findFirst().get().getResults()).isNotNull().contains(storedResult);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTextSubmissionWithResultIdAsTutor_badRequest() throws Exception {
        TextSubmission submission = ParticipationFactory.generateTextSubmission("asdf", null, true);
        submission = (TextSubmission) participationUtilService.addSubmissionWithTwoFinishedResultsWithAssessor(textExercise, submission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        Result storedResult = submission.getResultForCorrectionRound(0);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("resultId", String.valueOf(storedResult.getId()));
        request.get("/api/text/text-submissions/" + submission.getId() + "/for-assessment", HttpStatus.FORBIDDEN, TextSubmission.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void updateTextAssessmentAfterComplaint_wrongParticipationId() throws Exception {
        TextSubmission textSubmission = textExerciseUtilService.createTextSubmissionWithResultAndAssessor(textExercise, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        AssessmentUpdateDTO assessmentUpdate = complaintUtilService.createComplaintAndResponse(textSubmission.getLatestResult(), TEST_PREFIX + "tutor2");

        long randomId = 12354;
        Result updatedResult = request.putWithResponseBody("/api/text/participations/" + randomId + "/submissions/" + textSubmission.getId() + "/text-assessment-after-complaint",
                assessmentUpdate, Result.class, HttpStatus.BAD_REQUEST);

        assertThat(updatedResult).as("updated result found").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void updateTextAssessmentAfterComplaint_studentHidden() throws Exception {
        TextSubmission textSubmission = textExerciseUtilService.createTextSubmissionWithResultAndAssessor(textExercise, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        AssessmentUpdateDTO assessmentUpdate = complaintUtilService.createComplaintAndResponse(textSubmission.getLatestResult(), TEST_PREFIX + "tutor2");

        Result updatedResult = request.putWithResponseBody(
                "/api/text/participations/" + textSubmission.getParticipation().getId() + "/submissions/" + textSubmission.getId() + "/text-assessment-after-complaint",
                assessmentUpdate, Result.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
        assertThat(((StudentParticipation) updatedResult.getSubmission().getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void updateTextAssessmentAfterComplaint_withTextBlocks() throws Exception {
        // Setup
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is Part 1, and this is Part 2. There is also Part 3.", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(Set.of(new TextBlock().startIndex(0).endIndex(15).automatic(),
                new TextBlock().startIndex(16).endIndex(35).automatic(), new TextBlock().startIndex(36).endIndex(57).automatic()), textSubmission);

        Result textAssessment = textSubmission.getLatestResult();
        complaintRepo.save(new Complaint().result(textAssessment).complaintText("This is not fair"));

        // Get Text Submission and Complaint
        request.get("/api/text/text-submissions/" + textSubmission.getId() + "/for-assessment", HttpStatus.OK, StudentParticipation.class);
        final Complaint complaint = request.get("/api/assessment/complaints?submissionId=" + textSubmission.getId(), HttpStatus.OK, Complaint.class);

        // Accept Complaint and update Assessment
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        final var assessmentUpdate = new TextAssessmentUpdateDTO(new ArrayList<>(), complaintResponse, null, new HashSet<>());
        Result updatedResult = request.putWithResponseBody(
                "/api/text/participations/" + textSubmission.getParticipation().getId() + "/submissions/" + textSubmission.getId() + "/text-assessment-after-complaint",
                assessmentUpdate, Result.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
    }

    private TextSubmission prepareSubmission() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        exerciseDueDatePassed();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        return request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK, TextSubmission.class, params);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    @ValueSource(booleans = { true, false })
    void saveOrSubmitTextAssessment_studentHidden(boolean submit) throws Exception {
        TextSubmission submissionWithoutAssessment = prepareSubmission();
        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setFeedbacks(new ArrayList<>());

        Result result = saveOrSubmitTextAssessment(submissionWithoutAssessment.getParticipation().getId(),
                Objects.requireNonNull(submissionWithoutAssessment.getLatestResult()).getId(), textAssessmentDTO, submit, HttpStatus.OK);
        assertThat(result).as("saved result found").isNotNull();
        assertThat(((StudentParticipation) result.getSubmission().getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    @ValueSource(booleans = { true, false })
    void saveOrSubmitTextAssessment_wrongParticipationId(boolean submit) throws Exception {
        TextSubmission submissionWithoutAssessment = prepareSubmission();
        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setFeedbacks(new ArrayList<>());

        Result result = saveOrSubmitTextAssessment(1343L, Objects.requireNonNull(submissionWithoutAssessment.getLatestResult()).getId(), textAssessmentDTO, submit,
                HttpStatus.BAD_REQUEST);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getResult_studentHidden() throws Exception {
        int submissionCount = 5;
        int submissionSize = 4;
        var textBlocks = TextExerciseFactory.generateTextBlocks(submissionCount * submissionSize);
        TextExercise textExercise = textExerciseUtilService.createSampleTextExerciseWithSubmissions(course, new ArrayList<>(textBlocks), submissionCount, submissionSize);
        textBlocks.forEach(TextBlock::computeId);
        textBlockRepository.saveAll(textBlocks);
        var participations = textExercise.getStudentParticipations().iterator();

        for (int i = 0; i < submissionCount; i++) {
            StudentParticipation studentParticipation = participations.next();
            // connect it with a student (!= tutor assessing it)
            User user = userUtilService.getUserByLogin(TEST_PREFIX + "student" + (i % 2 + 1));
            studentParticipation.setInitializationDate(now());
            studentParticipation.setParticipant(user);
            studentParticipationRepository.save(studentParticipation);
        }

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        TextSubmission submissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);
        final Result result = submissionWithoutAssessment.getLatestResult();
        assertThat(result).as("saved result found").isNotNull();
        assertThat(((StudentParticipation) submissionWithoutAssessment.getParticipation()).getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getParticipationForNonTextExercise() throws Exception {
        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(now().minusDays(1), now().plusDays(1), now().plusDays(2), "png,pdf",
                textExercise.getCourseViaExerciseGroupOrCourseMember());
        exerciseRepository.save(fileUploadExercise);

        FileUploadSubmission fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);
        fileUploadExerciseUtilService.saveFileUploadSubmissionWithResultAndAssessorFeedback(fileUploadExercise, fileUploadSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1", new ArrayList<>());

        final Participation participation = request.get("/api/text/exercises/" + fileUploadExercise.getId() + "/text-submission-without-assessment", HttpStatus.BAD_REQUEST,
                Participation.class);

        assertThat(participation).as("no result should be returned when exercise is not a text exercise").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_assessorHidden() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), null);
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        Participation participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, Participation.class);

        assertThat(participation).as("participation found").isNotNull();
        assertThat(participation.getSubmissions().iterator().next().getResults().getFirst()).as("result found").isNotNull();
        assertThat(participation.getSubmissions().iterator().next().getResults().getFirst().getAssessor()).as("assessor of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditorForNonTextExercise_badRequest() throws Exception {
        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(now().minusDays(1), now().plusDays(1), now().plusDays(2), "png,pdf",
                textExercise.getCourseViaExerciseGroupOrCourseMember());
        exerciseRepository.save(fileUploadExercise);

        FileUploadSubmission fileUploadSubmission = ParticipationFactory.generateFileUploadSubmission(true);
        fileUploadExerciseUtilService.saveFileUploadSubmissionWithResultAndAssessorFeedback(fileUploadExercise, fileUploadSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1", new ArrayList<>());

        request.get("/api/text/text-editor/" + fileUploadSubmission.getParticipation().getId(), HttpStatus.BAD_REQUEST, Participation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_hasTextBlocks() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), null);
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        var textBlocks = TextExerciseFactory.generateTextBlocks(1);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(textBlocks, textSubmission);

        Participation participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, Participation.class);

        final TextSubmission submission = (TextSubmission) participation.getSubmissions().iterator().next();
        assertThat(submission.getBlocks()).isNotNull();
        assertThat(submission.getBlocks()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void getDataForTextEditor_asOtherStudent() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.FORBIDDEN, Participation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_BeforeExamPublishDate_Forbidden() throws Exception {
        // create exam
        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        exam.setStartDate(now().minusHours(2));
        exam.setEndDate(now().minusHours(1));
        exam.setVisibleDate(now().minusHours(3));
        exam.setPublishResultsDate(now().plusHours(3));

        // creating exercise
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().getFirst();

        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        exerciseGroup.addExercise(textExercise);
        exerciseGroupRepository.save(exerciseGroup);
        textExercise = exerciseRepository.save(textExercise);

        examTestRepository.save(exam);

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.FORBIDDEN, Participation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_testExam() throws Exception {
        var student = TEST_PREFIX + "student1";
        // create exam
        Exam exam = examUtilService.addTestExamWithExerciseGroup(course, true);
        exam.setStartDate(now().minusHours(2));
        exam.setEndDate(now().minusHours(1));
        exam.setVisibleDate(now().minusHours(3));

        ExerciseGroup exerciseGroup = exam.getExerciseGroups().getFirst();
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        exerciseGroup.addExercise(textExercise);
        exerciseGroupRepository.save(exerciseGroup);
        textExercise = exerciseRepository.save(textExercise);

        examTestRepository.save(exam);

        examUtilService.addStudentExamWithUser(exam, student);

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, student);
        var participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, Participation.class);
        assertThat(participation).isNotNull();
        assertThat(participation.getSubmissions()).containsExactly(textSubmission);
        exam = participation.getExercise().getExerciseGroup().getExam();
        assertThat(exam).isNotNull(); // The client needs the exam object to check if results are published yet
        assertThat(exam.isTestExam()).isTrue();
        assertThat(exam.getExerciseGroups()).isNullOrEmpty();
        assertThat(exam.getCourse()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_secondCorrection_oneResult() throws Exception {
        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        exam.setVisibleDate(now().minusHours(3));
        exam.setStartDate(now().minusHours(2));
        exam.setEndDate(now().minusHours(1));
        exam.setPublishResultsDate(now().minusMinutes(30));
        exam.setNumberOfCorrectionRoundsInExam(2);

        ExerciseGroup exerciseGroup = exam.getExerciseGroups().getFirst();
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup);
        exerciseGroup.addExercise(textExercise);
        exerciseGroupRepository.save(exerciseGroup);
        textExercise = exerciseRepository.save(textExercise);

        examTestRepository.save(exam);

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        // second correction round
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor2");

        var participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, Participation.class);

        assertThat(participation).isNotNull();
        assertThat(participation.getSubmissions()).containsExactly(textSubmission);
        var submission = participation.getSubmissions().iterator().next();
        assertThat(submission.getResults()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getDataForTextEditor_studentHidden() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), null);
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        StudentParticipation participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, StudentParticipation.class);

        assertThat(participation).as("participation found").isNotNull();
        assertThat(participation.getSubmissions().iterator().next().getResults().getFirst()).as("result found").isNotNull();
        assertThat(participation.getStudent()).as("student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getDataForTextEditor_submissionWithoutResult() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, StudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_beforeAssessmentDueDate_noResult() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), now().plusDays(1));

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        StudentParticipation participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, StudentParticipation.class);

        assertThat(participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream()).toList()).isEmpty();
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getSubmissions().iterator().next().getResults()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_beforeAssessmentDueDate_athenaResults() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), now().plusDays(1));

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithAthenaResult(textExercise, textSubmission, TEST_PREFIX + "student1");

        StudentParticipation participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, StudentParticipation.class);

        assertThat(participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream()).toList()).hasSize(1);
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getSubmissions().iterator().next().getResults()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_beforeAssessmentDueDate_athenaAndManualResults() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), now().plusDays(1));

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithAthenaResult(textExercise, textSubmission, TEST_PREFIX + "student1");
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        StudentParticipation participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, StudentParticipation.class);

        assertThat(participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream()).toList()).hasSize(1);
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getSubmissions().iterator().next().getResults()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_afterAssessmentDueDate_athenaAndManualResults() throws Exception {
        assessmentDueDatePassed();

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithAthenaResult(textExercise, textSubmission, TEST_PREFIX + "student1");
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        StudentParticipation participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, StudentParticipation.class);

        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getSubmissions().iterator().next().getResults()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getDataForTextEditor_asTutor_beforeAssessmentDueDate_athenaAndManualResults() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), now().plusDays(1));

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithAthenaResult(textExercise, textSubmission, TEST_PREFIX + "student1");
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        StudentParticipation participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, StudentParticipation.class);

        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getSubmissions().iterator().next().getResults()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getDataForTextEditor_asTutor_afterAssessmentDueDate_athenaAndManualResults() throws Exception {
        assessmentDueDatePassed();

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithAthenaResult(textExercise, textSubmission, TEST_PREFIX + "student1");
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        StudentParticipation participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, StudentParticipation.class);
        assertThat(participation.getSubmissions()).hasSize(1);
        assertThat(participation.getSubmissions().iterator().next().getResults()).hasSize(1);
    }

    private void getExampleResultForTutor(HttpStatus expectedStatus, boolean isExample) throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission.setExampleSubmission(isExample);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "instructor1");
        final var exampleSubmission = ParticipationFactory.generateExampleSubmission(textSubmission, textExercise, true);
        participationUtilService.addExampleSubmission(exampleSubmission);

        Result result = request.getNullable("/api/text/exercises/" + textExercise.getId() + "/submissions/" + textSubmission.getId() + "/example-result", expectedStatus,
                Result.class);

        if (expectedStatus == HttpStatus.OK && result != null) {
            assertThat(result.getId() == null).isTrue();
            for (Feedback feedback : result.getFeedbacks()) {
                assertThat(feedback.getCredits()).isNull();
                assertThat(feedback.getDetailText()).isNull();
                assertThat(feedback.getReference()).isNotNull();
            }
        }

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExampleResultForTutor_wrongExerciseId() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission.setExampleSubmission(true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "instructor1");
        final var exampleSubmission = ParticipationFactory.generateExampleSubmission(textSubmission, textExercise, true);
        participationUtilService.addExampleSubmission(exampleSubmission);
        long randomId = 23454;
        Result result = request.get("/api/text/exercises/" + randomId + "/submissions/" + textSubmission.getId() + "/example-result", HttpStatus.BAD_REQUEST, Result.class);

        assertThat(result).as("result found").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getExampleResultForTutorAsStudent() throws Exception {
        getExampleResultForTutor(HttpStatus.FORBIDDEN, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExampleResultForTutorAsTutor() throws Exception {
        // TODO: somehow this test fails in IntelliJ but passes when executed on the command line?!?
        getExampleResultForTutor(HttpStatus.OK, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExampleResultForNonExampleSubmissionAsTutor() throws Exception {
        getExampleResultForTutor(HttpStatus.OK, false);
    }

    private void cancelAssessment(HttpStatus expectedStatus) throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        participationUtilService.addSampleFeedbackToResults(textSubmission.getLatestResult());
        request.postWithoutLocation("/api/text/participations/" + textSubmission.getParticipation().getId() + "/submissions/" + textSubmission.getId() + "/cancel-assessment", null,
                expectedStatus, null);
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
    void cancelAssessment_wrongSubmissionId() throws Exception {
        request.post("/api/text/participations/1/submissions/" + 1000000000 + "/cancel-assessment", null, HttpStatus.NOT_FOUND);
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmitAssessment_IncludedCompletelyWithBonusPointsExercise() throws Exception {
        // setting up exercise
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(10.0);
        exerciseRepository.save(textExercise);

        // setting up student submission
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        // ending exercise
        exerciseDueDatePassed();

        // getting submission from db
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        TextSubmission submissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
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

        Course course = request.get("/api/core/courses/" + textExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-assessment-dashboard", HttpStatus.OK,
                Course.class);
        Exercise exercise = (Exercise) course.getExercises().toArray()[0];
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()).hasSize(1);
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(1L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmitAssessment_IncludedCompletelyWithoutBonusPointsExercise() throws Exception {
        // setting up exercise
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        exerciseRepository.save(textExercise);

        // setting up student submission
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        // ending exercise
        exerciseDueDatePassed();

        // getting submission from db
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        TextSubmission submissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
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
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmitAssessment_IncludedAsBonusExercise() throws Exception {
        // setting up exercise
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        exerciseRepository.save(textExercise);

        // setting up student submission
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        // ending exercise
        exerciseDueDatePassed();

        // getting submission from db
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        TextSubmission submissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
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
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmitAssessment_NotIncludedExercise() throws Exception {
        // setting up exercise
        textExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        textExercise.setMaxPoints(10.0);
        textExercise.setBonusPoints(0.0);
        exerciseRepository.save(textExercise);

        // setting up student submission
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        // ending exercise
        exerciseDueDatePassed();

        // getting submission from db
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        TextSubmission submissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
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
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSubmitAssessment_withResultOver100Percent() throws Exception {
        textExercise = (TextExercise) exerciseUtilService.addMaxScoreAndBonusPointsToExercise(textExercise);
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        exerciseDueDatePassed();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        TextSubmission submissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1")
                .reference(TextExerciseFactory.generateTextBlock(0, 5, "test1").getId()));
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 2")
                .reference(TextExerciseFactory.generateTextBlock(0, 5, "test2").getId()));
        textAssessmentDTO.setFeedbacks(feedbacks);

        // Check that result is over 100% -> 105
        Result response = request.postWithResponseBody("/api/text/participations/" + submissionWithoutAssessment.getParticipation().getId() + "/results/"
                + submissionWithoutAssessment.getLatestResult().getId() + "/submit-text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(105);

        feedbacks.add(new Feedback().credits(20.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 3")
                .reference(TextExerciseFactory.generateTextBlock(0, 5, "test3").getId()));
        textAssessmentDTO.setFeedbacks(feedbacks);

        // Check that result is capped to maximum of maxScore + bonus points -> 110
        response = request.postWithResponseBody("/api/text/participations/" + submissionWithoutAssessment.getParticipation().getId() + "/results/"
                + submissionWithoutAssessment.getLatestResult().getId() + "/submit-text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);

        assertThat(response.getScore()).isEqualTo(110, Offset.offset(0.0001));
    }

    private void exerciseDueDatePassed() {
        exerciseUtilService.updateExerciseDueDate(textExercise.getId(), now().minusHours(2));
    }

    private void assessmentDueDatePassed() {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), now().minusSeconds(10));
    }

    private void overrideAssessment(String student, String originalAssessor, HttpStatus httpStatus, String submit, boolean originalAssessmentSubmitted) throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Test123", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, student, originalAssessor);
        textSubmission.getLatestResult().setCompletionDate(originalAssessmentSubmitted ? now() : null);
        resultRepository.save(textSubmission.getLatestResult());
        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", submit);
        List<Feedback> feedbacks = ParticipationFactory.generateFeedback();
        var path = "/api/text/participations/" + textSubmission.getParticipation().getId() + "/results/" + textSubmission.getLatestResult().getId() + "/text-assessment";
        var body = new TextAssessmentDTO(feedbacks);
        if (submit.equals("true")) {
            path = "/api/text/participations/" + textSubmission.getParticipation().getId() + "/results/" + textSubmission.getLatestResult().getId() + "/submit-text-assessment";
            request.postWithResponseBody(path, body, Result.class, httpStatus);
        }
        else {
            request.putWithResponseBodyAndParams(path, body, Result.class, httpStatus, params);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTextBlocksAreConsistentWhenOpeningSameAssessmentTwiceWithAthenaEnabled() throws Exception {
        textExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_TEST);
        textExerciseRepository.save(textExercise);
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is Part 1, and this is Part 2. There is also Part 3.", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        exerciseDueDatePassed();

        var blocks = Set.of(new TextBlock().startIndex(0).endIndex(15).automatic(), new TextBlock().startIndex(16).endIndex(35).automatic(),
                new TextBlock().startIndex(36).endIndex(57).automatic());
        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(blocks, textSubmission);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        TextSubmission submission1stRequest = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);

        var blocksFrom1stRequest = submission1stRequest.getBlocks();
        assertThat(blocksFrom1stRequest.toArray()).containsExactlyInAnyOrder(blocks.toArray());

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setFeedbacks(
                Collections.singletonList(new Feedback().detailText("Test").credits(1d).reference(blocksFrom1stRequest.iterator().next().getId()).type(FeedbackType.MANUAL)));
        textAssessmentDTO.setTextBlocks(blocksFrom1stRequest);
        request.postWithResponseBody("/api/text/participations/" + submission1stRequest.getParticipation().getId() + "/results/" + submission1stRequest.getLatestResult().getId()
                + "/submit-text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);

        Participation participation2ndRequest = request.get("/api/text/text-submissions/" + textSubmission.getId() + "/for-assessment", HttpStatus.OK, Participation.class, params);
        TextSubmission submission2ndRequest = (TextSubmission) (participation2ndRequest).getSubmissions().iterator().next();
        var blocksFrom2ndRequest = submission2ndRequest.getBlocks();
        assertThat(blocksFrom2ndRequest.toArray()).containsExactlyInAnyOrder(blocks.toArray());
    }

    @NotNull
    private List<TextSubmission> prepareTextSubmissionsWithFeedbackForAutomaticFeedback() {
        TextSubmission textSubmission1 = ParticipationFactory.generateTextSubmission("This is Part 1, and this is Part 2. There is also Part 3.", Language.ENGLISH, true);
        TextSubmission textSubmission2 = ParticipationFactory.generateTextSubmission("This is another Submission.", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission1, TEST_PREFIX + "student1");
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission2, TEST_PREFIX + "student2");
        exerciseDueDatePassed();

        final Set<TextBlock> textBlocksSubmission1 = Set.of(new TextBlock().startIndex(0).endIndex(15).automatic(), new TextBlock().startIndex(16).endIndex(35).automatic(),
                new TextBlock().startIndex(36).endIndex(57).automatic());
        final TextBlock textBlockSubmission2 = new TextBlock().startIndex(0).endIndex(27).automatic();
        for (TextBlock textBlock : textBlocksSubmission1) {
            textBlock.computeId();
        }
        textBlockSubmission2.computeId();

        textSubmission1 = textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(textBlocksSubmission1, textSubmission1);
        textSubmission2 = textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(Set.of(textBlockSubmission2), textSubmission2);

        final Feedback feedback = new Feedback().detailText("Foo Bar.").credits(2d).reference(textBlockSubmission2.getId());
        textSubmission2 = textExerciseUtilService.addTextSubmissionWithResultAndAssessorAndFeedbacks(textExercise, textSubmission2, TEST_PREFIX + "student2",
                TEST_PREFIX + "tutor1", Collections.singletonList(feedback));

        // refetch the database objects to avoid lazy exceptions
        textSubmission1 = textSubmissionRepository.findWithEagerResultsAndFeedbackAndTextBlocksById(textSubmission1.getId()).orElseThrow();
        textSubmission2 = textSubmissionRepository.findWithEagerResultsAndFeedbackAndTextBlocksById(textSubmission2.getId()).orElseThrow();
        return asList(textSubmission1, textSubmission2);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = AssessmentType.class, names = { "SEMI_AUTOMATIC", "MANUAL" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void multipleCorrectionRoundsForExam(AssessmentType assessmentType) throws Exception {
        // Setup exam with 2 correction rounds and a text exercise
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();
        Exam exam = examUtilService.addExam(textExercise.getCourseViaExerciseGroupOrCourseMember());
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam.addExerciseGroup(exerciseGroup1);
        exam.setVisibleDate(now().minusHours(3));
        exam.setStartDate(now().minusHours(2));
        exam.setEndDate(now().minusHours(1));
        exam = examTestRepository.save(exam);

        Exam examWithExerciseGroups = examTestRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).orElseThrow();
        exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().getFirst();
        TextExercise exercise = TextExerciseFactory.generateTextExerciseForExam(exerciseGroup1);
        exercise.setAssessmentType(assessmentType);
        exercise = exerciseRepository.save(exercise);
        exerciseGroup1.addExercise(exercise);

        // add student submission
        var studentParticipation = new StudentParticipation();
        studentParticipation.setExercise(exercise);
        studentParticipation.setParticipant(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        studentParticipation = studentParticipationRepository.save(studentParticipation);
        var submission = ParticipationFactory.generateTextSubmission("Text", Language.ENGLISH, true);
        submission.setParticipation(studentParticipation);
        submission = textSubmissionRepository.save(submission);

        // verify setup
        assertThat(exam.getNumberOfCorrectionRoundsInExam()).isEqualTo(2);
        assertThat(exam.getEndDate()).isBefore(now());
        var optionalFetchedExercise = exerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(exercise.getId());
        assertThat(optionalFetchedExercise).isPresent();
        final var exerciseWithParticipation = optionalFetchedExercise.get();
        studentParticipation = exerciseWithParticipation.getStudentParticipations().stream().iterator().next();

        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        // check properties set
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("withExerciseGroups", "true");
        Exam examReturned = request.get("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        examReturned.getExerciseGroups().getFirst().getExercises().forEach(exerciseExamReturned -> {
            assertThat(exerciseExamReturned.getNumberOfParticipations()).isNotNull();
            assertThat(exerciseExamReturned.getNumberOfParticipations()).isEqualTo(1);
        });
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        // request to manually assess latest submission (correction round: 0)
        params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        params.add("correction-round", "0");
        // Athena requests are ok
        TextSubmission submissionWithoutFirstAssessment = request.get("/api/text/exercises/" + exerciseWithParticipation.getId() + "/text-submission-without-assessment",
                HttpStatus.OK, TextSubmission.class, params);
        // verify that no new submission was created
        assertThat(submissionWithoutFirstAssessment).isEqualTo(submission);
        // verify that the lock has been set
        assertThat(submissionWithoutFirstAssessment.getLatestResult()).isNotNull();
        assertThat(submissionWithoutFirstAssessment.getLatestResult().getAssessor().getLogin()).isEqualTo(TEST_PREFIX + "tutor1");
        assertThat(submissionWithoutFirstAssessment.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.MANUAL);

        // make sure that new result correctly appears inside the continue box
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1Tutor1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1Tutor1.add("assessedByTutor", "true");
        paramsGetAssessedCR1Tutor1.add("correction-round", "0");
        var assessedSubmissionList = request.getList("/api/text/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.getFirst().getResultForCorrectionRound(0)).isEqualTo(submissionWithoutFirstAssessment.getLatestResult());

        // assess submission and submit
        List<Feedback> feedbacks = ParticipationFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here"))
                .collect(Collectors.toCollection(ArrayList::new));
        TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setFeedbacks(feedbacks);
        Result firstSubmittedManualResult = request.postWithResponseBody("/api/text/participations/" + submissionWithoutFirstAssessment.getParticipation().getId() + "/results/"
                + submissionWithoutFirstAssessment.getLatestResult().getId() + "/submit-text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);

        // make sure that new result correctly appears after the assessment for first correction round
        assessedSubmissionList = request.getList("/api/text/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.getFirst().getResultForCorrectionRound(0)).isNotNull();
        assertThat(firstSubmittedManualResult.getAssessor().getLogin()).isEqualTo(TEST_PREFIX + "tutor1");

        // verify that the result contains the relationship
        assertThat(firstSubmittedManualResult).isNotNull();
        assertThat(firstSubmittedManualResult.getSubmission().getParticipation()).isEqualTo(studentParticipation);

        // verify that the relationship between student participation,
        var databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        var fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).contains(submissionWithoutFirstAssessment);
        assertThat(fetchedParticipation.findLatestResult()).isEqualTo(firstSubmittedManualResult);

        var databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository
                .findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.getFirst();
        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).isPresent();
        // it should contain the lock for the manual result
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getResults()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getLatestResult()).isEqualTo(firstSubmittedManualResult);

        // SECOND ROUND OF CORRECTION

        userUtilService.changeUser(TEST_PREFIX + "tutor2");
        LinkedMultiValueMap<String, String> paramsSecondCorrection = new LinkedMultiValueMap<>();
        paramsSecondCorrection.add("lock", "true");
        paramsSecondCorrection.add("correction-round", "1");

        final var submissionWithoutSecondAssessment = request.get("/api/text/exercises/" + exerciseWithParticipation.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, paramsSecondCorrection);

        // verify that the submission is not new
        assertThat(submissionWithoutSecondAssessment).isEqualTo(submission);
        // verify that the lock has been set
        assertThat(submissionWithoutSecondAssessment.getLatestResult()).isNotNull();
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessor().getLogin()).isEqualTo(TEST_PREFIX + "tutor2");
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getFeedbacks()).isNotEmpty();

        // verify that the relationship between student participation,
        databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).contains(submissionWithoutSecondAssessment);
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getResults().stream().filter(x -> x.getCompletionDate() == null).findFirst())
                .contains(submissionWithoutSecondAssessment.getLatestResult());

        databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.getFirst();
        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).isPresent();
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getResults()).hasSize(2);
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getLatestResult()).isEqualTo(submissionWithoutSecondAssessment.getLatestResult());

        Feedback secondCorrectionFeedback = new Feedback();
        secondCorrectionFeedback.setDetailText("asfd");
        secondCorrectionFeedback.setCredits(10.0);
        secondCorrectionFeedback.setPositive(true);
        submissionWithoutSecondAssessment.getLatestResult().getFeedbacks().add(secondCorrectionFeedback);
        textAssessmentDTO.setFeedbacks(submissionWithoutSecondAssessment.getLatestResult().getFeedbacks());

        // assess submission and submit
        Result secondSubmittedManualResult = request.postWithResponseBody("/api/text/participations/" + submissionWithoutFirstAssessment.getParticipation().getId() + "/results/"
                + submissionWithoutSecondAssessment.getLatestResult().getId() + "/submit-text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);
        assertThat(secondSubmittedManualResult).isNotNull();

        // check if feedback copy was set correctly
        assertThat(secondSubmittedManualResult.getFeedbacks()).isNotEmpty();

        // make sure that new result correctly appears after the assessment for second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR2 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR2.add("assessedByTutor", "true");
        paramsGetAssessedCR2.add("correction-round", "1");
        assessedSubmissionList = request.getList("/api/text/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class,
                paramsGetAssessedCR2);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().getId()).isEqualTo(submissionWithoutSecondAssessment.getId());
        assertThat(assessedSubmissionList.getFirst().getResultForCorrectionRound(1)).isEqualTo(secondSubmittedManualResult);

        // make sure that they do not appear for the first correction round as the tutor only assessed the second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1.add("assessedByTutor", "true");
        paramsGetAssessedCR1.add("correction-round", "0");
        assessedSubmissionList = request.getList("/api/text/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class,
                paramsGetAssessedCR1);

        assertThat(assessedSubmissionList).isEmpty();

        // Student should not have received a result over WebSocket as manual correction is ongoing
        verify(websocketMessagingService, never()).sendMessageToUser(notNull(), eq(Constants.NEW_RESULT_TOPIC), isA(ResultDTO.class));
    }

    private void addAssessmentFeedbackAndCheckScore(TextSubmission submissionWithoutAssessment, TextAssessmentDTO textAssessmentDTO, List<Feedback> feedbacks, double pointsAwarded,
            long expectedScore) throws Exception {
        feedbacks.add(new Feedback().credits(pointsAwarded).type(FeedbackType.MANUAL_UNREFERENCED).detailText("gj")
                .reference(TextExerciseFactory.generateTextBlock(0, 5, "test" + feedbacks.size()).getId()));
        textAssessmentDTO.setFeedbacks(feedbacks);
        Result response = request.postWithResponseBody("/api/text/participations/" + submissionWithoutAssessment.getParticipation().getId() + "/results/"
                + submissionWithoutAssessment.getLatestResult().getId() + "/submit-text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);
        assertThat(response.getScore()).isEqualTo(expectedScore);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testdeleteResult() throws Exception {
        Course course = exerciseUtilService.addCourseWithOneExerciseAndSubmissions(TEST_PREFIX, "text", 1);
        Exercise exercise = exerciseRepository.findAllExercisesByCourseId(course.getId()).iterator().next();
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor2"));

        var submissions = participationUtilService.getAllSubmissionsOfExercise(exercise);
        Submission submission = submissions.getFirst();
        assertThat(submission.getResults()).hasSize(2);
        Result firstResult = submission.getResults().getFirst();
        Result lastResult = submission.getLatestResult();
        request.delete("/api/text/participations/" + submission.getParticipation().getId() + "/text-submissions/" + submission.getId() + "/results/" + firstResult.getId(),
                HttpStatus.OK);
        submission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submission.getId());
        assertThat(submission.getResults()).hasSize(1);
        assertThat(submission.getResults().getFirst()).isEqualTo(lastResult);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFeedbackIdIsSetCorrectly() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("This is Part 1, and this is Part 2. There is also Part 3.", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        exerciseDueDatePassed();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        TextSubmission submissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class, params);

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO();
        textAssessmentDTO.setTextBlocks(Set.of(TextExerciseFactory.generateTextBlock(0, 15, "This is Part 1,"),
                TextExerciseFactory.generateTextBlock(16, 35, " and this is Part 2."), TextExerciseFactory.generateTextBlock(36, 57, " There is also Part 3.")));

        List<Feedback> feedbacks = new ArrayList<>();
        textAssessmentDTO.getTextBlocks()
                .forEach(textBlock -> feedbacks.add(new Feedback().type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1").reference(textBlock.getId())));
        textAssessmentDTO.setFeedbacks(feedbacks);

        request.postWithResponseBody("/api/text/participations/" + submissionWithoutAssessment.getParticipation().getId() + "/results/"
                + submissionWithoutAssessment.getLatestResult().getId() + "/submit-text-assessment", textAssessmentDTO, Result.class, HttpStatus.OK);

        Set<TextBlock> textBlocks = textBlockRepository.findAllBySubmissionId(submissionWithoutAssessment.getId());
        assertThat(textBlocks).isNotEmpty().allMatch(textBlock -> textBlock.getId() != null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testConsecutiveSaveFailsAfterAddingOrRemovingReferencedFeedback() throws Exception {
        List<TextSubmission> textSubmissions = prepareTextSubmissionsWithFeedbackForAutomaticFeedback();
        final Map<String, TextBlock> blocksSubmission = textSubmissions.stream().flatMap(submission -> submission.getBlocks().stream())
                .collect(Collectors.toMap(TextBlock::getId, block -> block));

        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("lock", "true");
        TextSubmission textSubmissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
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
        feedbacks.add(
                new Feedback().credits(20.00).type(FeedbackType.MANUAL).detailText("nice submission 3").reference(TextExerciseFactory.generateTextBlock(0, 15, "test3").getId()));
        dto.setFeedbacks(feedbacks);

        // These two lines ensure the call count verification near the end of this test can spot the call
        // made during the PUT /api/text/participations/:id/text-assessment request and not other places.
        int irrelevantCallCount = 1;
        verify(textBlockService, times(irrelevantCallCount)).findAllBySubmissionId(textSubmissionWithoutAssessment.getId());

        request.putWithResponseBody("/api/text/participations/" + textSubmissionWithoutAssessment.getParticipation().getId() + "/results/"
                + textSubmissionWithoutAssessment.getLatestResult().getId() + "/text-assessment", dto, Result.class, HttpStatus.OK);

        feedbacks.removeFirst();

        request.putWithResponseBody("/api/text/participations/" + textSubmissionWithoutAssessment.getParticipation().getId() + "/results/"
                + textSubmissionWithoutAssessment.getLatestResult().getId() + "/text-assessment", dto, Result.class, HttpStatus.OK);

        var result = request.putWithResponseBody("/api/text/participations/" + textSubmissionWithoutAssessment.getParticipation().getId() + "/results/"
                + textSubmissionWithoutAssessment.getLatestResult().getId() + "/text-assessment", dto, Result.class, HttpStatus.OK);

        final var textSubmission = textSubmissionRepository.getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(result.getId());

        // This is to ensure the fix for https://github.com/ls1intum/Artemis/issues/4962 is not removed. Please ensure that problem is not occurring
        // if you remove or change this verification. This test uses a spy because the error was caused by and EntityNotFound exception related to MySQL
        // however the intergration tests use H2 in-memory database so same code did not produce the same error.
        verify(textBlockService, times(irrelevantCallCount + 3)).findAllBySubmissionId(textSubmission.getId());

        Set<TextBlock> textBlocks = textBlockRepository.findAllBySubmissionId(textSubmissionWithoutAssessment.getId());
        final String[] ignoringFields = { "submission.results", "submission.submissionDate", "submission.participation", "submission.blocks", "submission.versions" };
        assertThat(textBlocks).allSatisfy(block -> assertThat(block).usingRecursiveComparison().ignoringFields(ignoringFields).isEqualTo(blocksSubmission.get(block.getId())));
    }

    /**
     * Saves or submits a text assessment.
     *
     * @param participationId   The participation id of a submission.
     * @param latestResultId    The id of the latest result.
     * @param textAssessmentDTO The DTO of the text assessment.
     * @param submit            True, if the text assessment should be submitted. False, if it should only be saved.
     * @param expectedStatus    Expected response status of the request.
     * @return The response of the request in form of a result.
     * @throws Exception If the request fails.
     */
    private Result saveOrSubmitTextAssessment(Long participationId, Long latestResultId, TextAssessmentDTO textAssessmentDTO, boolean submit, HttpStatus expectedStatus)
            throws Exception {
        if (submit) {
            return request.postWithResponseBody("/api/text/participations/" + participationId + "/results/" + latestResultId + "/submit-text-assessment", textAssessmentDTO,
                    Result.class, expectedStatus);

        }
        else {
            return request.putWithResponseBody("/api/text/participations/" + participationId + "/results/" + latestResultId + "/text-assessment", textAssessmentDTO, Result.class,
                    expectedStatus);
        }
    }
}
