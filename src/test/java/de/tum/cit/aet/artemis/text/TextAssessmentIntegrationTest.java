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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.data.Offset;
import org.jspecify.annotations.NonNull;
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

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUpdateDTO;
import de.tum.cit.aet.artemis.assessment.dto.ComplaintDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;
import de.tum.cit.aet.artemis.assessment.dto.ResultDTO;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ExampleSubmissionTestRepository;
import de.tum.cit.aet.artemis.assessment.util.ComplaintUtilService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
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
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.ComplaintResponseRequestDTO;
import de.tum.cit.aet.artemis.text.dto.TextAssessmentDTO;
import de.tum.cit.aet.artemis.text.dto.TextAssessmentUpdateDTO;
import de.tum.cit.aet.artemis.text.dto.TextBlockDTO;
import de.tum.cit.aet.artemis.text.dto.TextExampleResultDTO;
import de.tum.cit.aet.artemis.text.dto.TextParticipationDTO;
import de.tum.cit.aet.artemis.text.dto.TextSubmissionAssessmentDTO;
import de.tum.cit.aet.artemis.text.dto.TextSubmissionResponseDTO;
import de.tum.cit.aet.artemis.text.dto.TextSubmissionWithoutAssessmentDTO;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.service.TextAssessmentService;
import de.tum.cit.aet.artemis.text.test_repository.TextSubmissionTestRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class TextAssessmentIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

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

    @Autowired
    private LongFeedbackTextRepository longFeedbackTextRepository;

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
    void saveAssessmentWithExistingLongFeedbackPreservesFullText() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some submitted text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        Result result = textSubmission.getLatestResult();

        // A long feedback (> soft max) is stored as a short preview on the feedback plus a separate LongFeedbackText holding
        // the full text.
        final String fullLongText = "L".repeat(1500);
        Feedback longFeedback = new Feedback();
        longFeedback.setCredits(1.0);
        longFeedback.setType(FeedbackType.MANUAL_UNREFERENCED);
        longFeedback.setDetailText(fullLongText);
        participationUtilService.addFeedbackToResult(longFeedback, result);
        final long feedbackId = longFeedback.getId();
        assertThat(longFeedback.getHasLongFeedbackText()).as("setup: feedback is stored as long feedback").isTrue();
        assertThat(longFeedbackTextRepository.findByFeedbackId(feedbackId).orElseThrow().getText()).as("setup: full long text persisted").isEqualTo(fullLongText);

        // Re-save the assessment the way the client does: the DTO carries the feedback id and hasLongFeedbackText, but only
        // the PREVIEW detail text (the full long text is never sent to the client).
        FeedbackDTO feedbackDTO = FeedbackDTO.of(longFeedback);
        assertThat(feedbackDTO.hasLongFeedbackText()).isTrue();
        assertThat(feedbackDTO.detailText()).as("read DTO carries only the preview, not the full long text").isNotEqualTo(fullLongText);
        TextAssessmentDTO body = new TextAssessmentDTO(List.of(feedbackDTO), null, null);
        request.putWithResponseBodyAndParams("/api/text/participations/" + textSubmission.getParticipation().getId() + "/results/" + result.getId() + "/text-assessment", body,
                ResultDTO.class, HttpStatus.OK, new LinkedMultiValueMap<>());

        // The full long feedback must survive the re-save: the mapper must not downgrade it to the preview.
        assertThat(longFeedbackTextRepository.findByFeedbackId(feedbackId)).as("long feedback is preserved on re-save").isPresent();
        assertThat(longFeedbackTextRepository.findByFeedbackId(feedbackId).orElseThrow().getText()).as("the full long text is not downgraded to the preview")
                .isEqualTo(fullLongText);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void retrieveParticipationForSubmission_studentHidden() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, false);
        textSubmission = textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");

        TextParticipationDTO participationWithoutAssessment = request.get("/api/text/text-submissions/" + textSubmission.getId() + "/for-assessment", HttpStatus.OK,
                TextParticipationDTO.class);

        assertThat(participationWithoutAssessment).as("participation with submission was found").isNotNull();
        assertThat(participationWithoutAssessment.submissions().getLast().id()).as("participation with correct text submission was found").isEqualTo(textSubmission.getId());
        assertThat(participationWithoutAssessment.student()).as("student of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void retrieveParticipationForLockedSubmission() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, false);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor2");
        Result result = textSubmission.getLatestResult();
        result.setCompletionDate(null); // assessment is still in progress for this test
        resultRepository.save(result);
        TextParticipationDTO participation = request.get("/api/text/text-submissions/" + textSubmission.getId() + "/for-assessment", HttpStatus.LOCKED, TextParticipationDTO.class);
        assertThat(participation).as("participation is locked and should not be returned").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void retrieveParticipationForNonExistingSubmission() throws Exception {
        TextParticipationDTO participation = request.get("/api/text/text-submissions/345395769256365/for-assessment", HttpStatus.NOT_FOUND, TextParticipationDTO.class);
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
        TextParticipationDTO participation = request.get("/api/text/text-submissions/" + submission.getId() + "/for-assessment", HttpStatus.OK, TextParticipationDTO.class, params);

        assertThat(participation.submissions().stream().findFirst().isPresent()).isTrue();
        assertThat(participation.submissions().getFirst().results()).isNotNull().extracting(ResultDTO::id).contains(storedResult.getId());
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
        request.get("/api/text/text-submissions/" + submission.getId() + "/for-assessment", HttpStatus.FORBIDDEN, TextParticipationDTO.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void updateTextAssessmentAfterComplaint_wrongParticipationId() throws Exception {
        TextSubmission textSubmission = textExerciseUtilService.createTextSubmissionWithResultAndAssessor(textExercise, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        AssessmentUpdateDTO assessmentUpdate = complaintUtilService.createComplaintAndResponse(textSubmission.getLatestResult(), TEST_PREFIX + "tutor2");
        TextAssessmentUpdateDTO textAssessmentUpdate = new TextAssessmentUpdateDTO(new ArrayList<>(), toComplaintResponseRequestDTO(assessmentUpdate.complaintResponse()), null,
                new HashSet<>());

        long randomId = 12354;
        ResultDTO updatedResult = request.putWithResponseBody(
                "/api/text/participations/" + randomId + "/submissions/" + textSubmission.getId() + "/text-assessment-after-complaint", textAssessmentUpdate, ResultDTO.class,
                HttpStatus.BAD_REQUEST);

        assertThat(updatedResult).as("updated result found").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void updateTextAssessmentAfterComplaint_studentHidden() throws Exception {
        TextSubmission textSubmission = textExerciseUtilService.createTextSubmissionWithResultAndAssessor(textExercise, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        AssessmentUpdateDTO assessmentUpdate = complaintUtilService.createComplaintAndResponse(textSubmission.getLatestResult(), TEST_PREFIX + "tutor2");
        TextAssessmentUpdateDTO textAssessmentUpdate = new TextAssessmentUpdateDTO(new ArrayList<>(), toComplaintResponseRequestDTO(assessmentUpdate.complaintResponse()), null,
                new HashSet<>());

        ResultDTO updatedResult = request.putWithResponseBody(
                "/api/text/participations/" + textSubmission.getParticipation().getId() + "/submissions/" + textSubmission.getId() + "/text-assessment-after-complaint",
                textAssessmentUpdate, ResultDTO.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
        // The student of the participation is hidden for non-instructors: ResultDTO.participation() (ParticipationDTO) structurally carries no student field.
        assertThat(updatedResult.participation()).as("participation of result is present (student is structurally omitted)").isNotNull();
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
        request.get("/api/text/text-submissions/" + textSubmission.getId() + "/for-assessment", HttpStatus.OK, TextParticipationDTO.class);
        final ComplaintDTO complaintDto = request.get("/api/assessment/complaints?submissionId=" + textSubmission.getId(), HttpStatus.OK, ComplaintDTO.class);
        final Complaint complaint = complaintRepo.findById(complaintDto.id()).orElseThrow();
        // Accept Complaint and update Assessment
        ComplaintResponse complaintResponse = complaintUtilService.createInitialEmptyResponse(TEST_PREFIX + "tutor2", complaint);
        complaintResponse.getComplaint().setAccepted(false);
        complaintResponse.setResponseText("rejected");

        final var assessmentUpdate = new TextAssessmentUpdateDTO(new ArrayList<>(), toComplaintResponseRequestDTO(complaintResponse), null, new HashSet<>());
        ResultDTO updatedResult = request.putWithResponseBody(
                "/api/text/participations/" + textSubmission.getParticipation().getId() + "/submissions/" + textSubmission.getId() + "/text-assessment-after-complaint",
                assessmentUpdate, ResultDTO.class, HttpStatus.OK);

        assertThat(updatedResult).as("updated result found").isNotNull();
    }

    private static Long participationId(TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment) {
        return submissionWithoutAssessment.participation().id();
    }

    private static ResultDTO latestResult(TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment) {
        return submissionWithoutAssessment.participation().submissions().getLast().results().getLast();
    }

    private static Long latestResultId(TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment) {
        return latestResult(submissionWithoutAssessment).id();
    }

    private static Long submissionId(TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment) {
        return submissionWithoutAssessment.id();
    }

    private static List<TextBlockDTO> blocksOf(TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment) {
        return submissionWithoutAssessment.participation().submissions().getLast().blocks();
    }

    private static ComplaintResponseRequestDTO toComplaintResponseRequestDTO(ComplaintResponse complaintResponse) {
        Complaint complaint = complaintResponse.getComplaint();
        return new ComplaintResponseRequestDTO(complaintResponse.getId(), complaintResponse.getResponseText(),
                new ComplaintResponseRequestDTO.ComplaintRequestDTO(complaint.getId(), complaint.isAccepted()));
    }

    private TextSubmissionWithoutAssessmentDTO prepareSubmission() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        exerciseDueDatePassed();

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        return request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK, TextSubmissionWithoutAssessmentDTO.class, params);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    @ValueSource(booleans = { true, false })
    void saveOrSubmitTextAssessment_studentHidden(boolean submit) throws Exception {
        TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment = prepareSubmission();
        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO(new ArrayList<>(), null, null);

        ResultDTO result = saveOrSubmitTextAssessment(participationId(submissionWithoutAssessment), Objects.requireNonNull(latestResultId(submissionWithoutAssessment)),
                textAssessmentDTO, submit, HttpStatus.OK);
        assertThat(result).as("saved result found").isNotNull();
        // The student of the participation is hidden for non-instructors: ResultDTO.participation() (ParticipationDTO) structurally carries no student field.
        assertThat(result.participation()).as("participation of result is present (student is structurally omitted)").isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void submitTextAssessment_withOmittedFeedbacks_doesNotFail() throws Exception {
        TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment = prepareSubmission();
        // feedbacks omitted entirely (null), not just an empty list: this used to NPE/500 on the unguarded feedbacks.stream() path.
        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO(null, null, null);

        ResultDTO result = request.postWithResponseBody("/api/text/participations/" + participationId(submissionWithoutAssessment) + "/results/"
                + Objects.requireNonNull(latestResultId(submissionWithoutAssessment)) + "/submit-text-assessment", textAssessmentDTO, ResultDTO.class, HttpStatus.OK);

        assertThat(result).as("submitting an assessment with omitted feedbacks returns 200 (not 500)").isNotNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    @ValueSource(booleans = { true, false })
    void saveOrSubmitTextAssessment_wrongParticipationId(boolean submit) throws Exception {
        TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment = prepareSubmission();
        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO(new ArrayList<>(), null, null);

        ResultDTO result = saveOrSubmitTextAssessment(1343L, Objects.requireNonNull(latestResultId(submissionWithoutAssessment)), textAssessmentDTO, submit,
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

        TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment",
                HttpStatus.OK, TextSubmissionWithoutAssessmentDTO.class, params);
        final ResultDTO result = submissionWithoutAssessment.participation().submissions().getLast().results().getLast();
        assertThat(result).as("saved result found").isNotNull();
        assertThat(submissionWithoutAssessment.participation().student()).as("student of participation is hidden").isNull();
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

        final TextSubmissionWithoutAssessmentDTO participation = request.get("/api/text/exercises/" + fileUploadExercise.getId() + "/text-submission-without-assessment",
                HttpStatus.BAD_REQUEST, TextSubmissionWithoutAssessmentDTO.class);

        assertThat(participation).as("no result should be returned when exercise is not a text exercise").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_assessorHidden() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), null);
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        TextParticipationDTO participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, TextParticipationDTO.class);

        assertThat(participation).as("participation found").isNotNull();
        assertThat(participation.submissions().getLast().results().getFirst()).as("result found").isNotNull();
        assertThat(participation.submissions().getLast().results().getFirst().assessor()).as("assessor of participation is hidden").isNull();
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

        request.get("/api/text/text-editor/" + fileUploadSubmission.getParticipation().getId(), HttpStatus.BAD_REQUEST, TextParticipationDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_hasTextBlocks() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), null);
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        var textBlocks = TextExerciseFactory.generateTextBlocks(1);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(textBlocks, textSubmission);

        TextParticipationDTO participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, TextParticipationDTO.class);

        final var submission = participation.submissions().getLast();
        assertThat(submission.blocks()).isNotNull();
        assertThat(submission.blocks()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnSpecificResultWhenResultIdProvided() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), null);
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        // Reload submission to avoid detached entity issues
        textSubmission = textSubmissionRepository.findWithEagerResultsAndFeedbackAndTextBlocksById(textSubmission.getId()).orElseThrow();
        Long firstResultId = textSubmission.getResults().getFirst().getId();

        // Add a second result (simulating Athena assessment)
        Result secondResult = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC_ATHENA, now(), textSubmission);
        secondResult.setScore(85.0);
        secondResult = resultRepository.save(secondResult);

        // Without resultId parameter - should get only the latest result (default behavior)
        TextParticipationDTO actualParticipationWithoutParam = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK,
                TextParticipationDTO.class);
        final var actualSubmissionWithoutParam = actualParticipationWithoutParam.submissions().getLast();
        assertThat(actualSubmissionWithoutParam.results()).hasSize(1);

        // With resultId parameter - should get only the specific result
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("resultId", firstResultId.toString());
        TextParticipationDTO actualParticipationWithParam = request.get(
                "/api/text/text-editor/" + textSubmission.getParticipation().getId() + "?"
                        + params.toSingleValueMap().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).reduce((a, b) -> a + "&" + b).orElse(""),
                HttpStatus.OK, TextParticipationDTO.class);
        final var actualSubmissionWithParam = actualParticipationWithParam.submissions().getLast();
        assertThat(actualSubmissionWithParam.results()).hasSize(1);
        assertThat(actualSubmissionWithParam.results().getFirst().id()).isEqualTo(firstResultId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturn404WhenResultIdDoesNotExist() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), null);
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        long nonExistentResultId = Long.MAX_VALUE;
        request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId() + "?resultId=" + nonExistentResultId, HttpStatus.NOT_FOUND, TextParticipationDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturn404WhenResultIdBelongsToDifferentSubmission() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), null);

        // Create student1's submission with a result
        TextSubmission student1Submission = ParticipationFactory.generateTextSubmission("Student 1 text", Language.ENGLISH, true);
        student1Submission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, student1Submission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        // Create student2's submission with a result
        TextSubmission student2Submission = ParticipationFactory.generateTextSubmission("Student 2 text", Language.ENGLISH, true);
        student2Submission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, student2Submission, TEST_PREFIX + "student2", TEST_PREFIX + "tutor1");
        student2Submission = textSubmissionRepository.findWithEagerResultsAndFeedbackAndTextBlocksById(student2Submission.getId()).orElseThrow();
        Long student2ResultId = student2Submission.getResults().getFirst().getId();

        // Student1 requests their own participation but with student2's resultId - should be 404
        request.get("/api/text/text-editor/" + student1Submission.getParticipation().getId() + "?resultId=" + student2ResultId, HttpStatus.NOT_FOUND, TextParticipationDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void getDataForTextEditor_asOtherStudent() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.FORBIDDEN, TextParticipationDTO.class);
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
        request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.FORBIDDEN, TextParticipationDTO.class);
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
        var participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, TextParticipationDTO.class);
        assertThat(participation).isNotNull();
        assertThat(participation.submissions()).extracting(TextSubmissionAssessmentDTO::id).containsExactly(textSubmission.getId());
        // The client needs the exam information to check if results are published yet: TextExerciseResponseDTO flattens this to examId()/examPublishResultsDate().
        assertThat(participation.exercise()).isNotNull();
        assertThat(participation.exercise().examId()).as("exam id is exposed so the client can resolve the exam").isNotNull();
        // FIXME-DTO: TextExerciseResponseDTO does not carry the exam's isTestExam() flag; the text editor uses exam.isTestExam() to decide test-exam handling. Missing field:
        // Exam.isTestExam.
        assertThat(exam.isTestExam()).isTrue();
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

        var participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, TextParticipationDTO.class);

        assertThat(participation).isNotNull();
        assertThat(participation.submissions()).extracting(TextSubmissionAssessmentDTO::id).containsExactly(textSubmission.getId());
        var submission = participation.submissions().getLast();
        assertThat(submission.results()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getDataForTextEditor_studentHidden() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), null);
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        TextParticipationDTO participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, TextParticipationDTO.class);

        assertThat(participation).as("participation found").isNotNull();
        assertThat(participation.submissions().getLast().results().getFirst()).as("result found").isNotNull();
        assertThat(participation.student()).as("student of participation is hidden").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getDataForTextEditor_submissionWithoutResult() throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, TextParticipationDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_beforeAssessmentDueDate_noResult() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), now().plusDays(1));

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        TextParticipationDTO participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, TextParticipationDTO.class);

        assertThat(
                participation.submissions().stream().flatMap(submission -> submission.results() == null ? java.util.stream.Stream.empty() : submission.results().stream()).toList())
                .isEmpty();
        assertThat(participation.submissions()).hasSize(1);
        // No results before the assessment due date: TextSubmissionAssessmentDTO.results() is null (empty lists are dropped by @JsonInclude(NON_EMPTY)).
        assertThat(participation.submissions().getLast().results()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_beforeAssessmentDueDate_athenaResults() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), now().plusDays(1));

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithAthenaResult(textExercise, textSubmission, TEST_PREFIX + "student1");

        TextParticipationDTO participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, TextParticipationDTO.class);

        assertThat(participation.submissions().stream().flatMap(submission -> submission.results().stream()).toList()).hasSize(1);
        assertThat(participation.submissions()).hasSize(1);
        assertThat(participation.submissions().getLast().results()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_beforeAssessmentDueDate_athenaAndManualResults() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), now().plusDays(1));

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithAthenaResult(textExercise, textSubmission, TEST_PREFIX + "student1");
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        TextParticipationDTO participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, TextParticipationDTO.class);

        assertThat(participation.submissions().stream().flatMap(submission -> submission.results().stream()).toList()).hasSize(1);
        assertThat(participation.submissions()).hasSize(1);
        assertThat(participation.submissions().getLast().results()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditor_afterAssessmentDueDate_athenaAndManualResults() throws Exception {
        assessmentDueDatePassed();

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithAthenaResult(textExercise, textSubmission, TEST_PREFIX + "student1");
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        TextParticipationDTO participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, TextParticipationDTO.class);

        assertThat(participation.submissions()).hasSize(1);
        assertThat(participation.submissions().getLast().results()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getDataForTextEditor_asTutor_beforeAssessmentDueDate_athenaAndManualResults() throws Exception {
        exerciseUtilService.updateAssessmentDueDate(textExercise.getId(), now().plusDays(1));

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithAthenaResult(textExercise, textSubmission, TEST_PREFIX + "student1");
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        TextParticipationDTO participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, TextParticipationDTO.class);

        assertThat(participation.submissions()).hasSize(1);
        assertThat(participation.submissions().getLast().results()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void getDataForTextEditor_asTutor_afterAssessmentDueDate_athenaAndManualResults() throws Exception {
        assessmentDueDatePassed();

        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithAthenaResult(textExercise, textSubmission, TEST_PREFIX + "student1");
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        TextParticipationDTO participation = request.get("/api/text/text-editor/" + textSubmission.getParticipation().getId(), HttpStatus.OK, TextParticipationDTO.class);
        assertThat(participation.submissions()).hasSize(1);
        assertThat(participation.submissions().getLast().results()).hasSize(1);
    }

    private void getExampleResultForTutor(HttpStatus expectedStatus, boolean isExample) throws Exception {
        TextSubmission textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textSubmission.setExampleSubmission(isExample);
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(textExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "instructor1");
        final var exampleSubmission = ParticipationFactory.generateExampleSubmission(textSubmission, textExercise, true);
        participationUtilService.addExampleSubmission(exampleSubmission);

        TextExampleResultDTO result = request.getNullable("/api/text/exercises/" + textExercise.getId() + "/submissions/" + textSubmission.getId() + "/example-result",
                expectedStatus, TextExampleResultDTO.class);

        if (expectedStatus == HttpStatus.OK && result != null) {
            assertThat(result.id()).isNull();
            // empty/absent feedbacks are omitted on the wire (NON_EMPTY); mirror the client which treats them as an empty list
            List<FeedbackDTO> maskedFeedbacks = result.feedbacks() == null ? List.of() : result.feedbacks();
            for (FeedbackDTO feedback : maskedFeedbacks) {
                assertThat(feedback.credits()).isNull();
                assertThat(feedback.detailText()).isNull();
                assertThat(feedback.reference()).isNotNull();
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
        ResultDTO result = request.get("/api/text/exercises/" + randomId + "/submissions/" + textSubmission.getId() + "/example-result", HttpStatus.BAD_REQUEST, ResultDTO.class);

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
        TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment",
                HttpStatus.OK, TextSubmissionWithoutAssessmentDTO.class, params);

        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 5.0, 150L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 5.0, 200L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 5.0, 200L);

        Course course = request.get("/api/course/courses/" + textExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-assessment-dashboard", HttpStatus.OK,
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
        TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment",
                HttpStatus.OK, TextSubmissionWithoutAssessmentDTO.class, params);

        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 5.0, 100L);
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
        TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment",
                HttpStatus.OK, TextSubmissionWithoutAssessmentDTO.class, params);

        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 5.0, 100L);
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
        TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment",
                HttpStatus.OK, TextSubmissionWithoutAssessmentDTO.class, params);

        List<Feedback> feedbacks = new ArrayList<>();
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 0.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, -1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 1.0, 0L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 5.0, 50L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 5.0, 100L);
        addAssessmentFeedbackAndCheckScore(submissionWithoutAssessment, feedbacks, 5.0, 100L);
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

        TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment",
                HttpStatus.OK, TextSubmissionWithoutAssessmentDTO.class, params);

        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1")
                .reference(TextExerciseFactory.generateTextBlock(0, 5, "test1").getId()));
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 2")
                .reference(TextExerciseFactory.generateTextBlock(0, 5, "test2").getId()));

        // Check that result is over 100% -> 105
        ResultDTO response = request.postWithResponseBody(
                "/api/text/participations/" + participationId(submissionWithoutAssessment) + "/results/" + latestResultId(submissionWithoutAssessment) + "/submit-text-assessment",
                new TextAssessmentDTO(feedbacks.stream().map(FeedbackDTO::of).toList(), null, null), ResultDTO.class, HttpStatus.OK);

        assertThat(response.score()).isEqualTo(105);

        feedbacks.add(new Feedback().credits(20.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 3")
                .reference(TextExerciseFactory.generateTextBlock(0, 5, "test3").getId()));

        // Check that result is capped to maximum of maxScore + bonus points -> 110
        response = request.postWithResponseBody(
                "/api/text/participations/" + participationId(submissionWithoutAssessment) + "/results/" + latestResultId(submissionWithoutAssessment) + "/submit-text-assessment",
                new TextAssessmentDTO(feedbacks.stream().map(FeedbackDTO::of).toList(), null, null), ResultDTO.class, HttpStatus.OK);

        assertThat(response.score()).isEqualTo(110, Offset.offset(0.0001));
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
        var body = new TextAssessmentDTO(feedbacks.stream().map(FeedbackDTO::of).toList(), null, null);
        if (submit.equals("true")) {
            path = "/api/text/participations/" + textSubmission.getParticipation().getId() + "/results/" + textSubmission.getLatestResult().getId() + "/submit-text-assessment";
            request.postWithResponseBody(path, body, ResultDTO.class, httpStatus);
        }
        else {
            request.putWithResponseBodyAndParams(path, body, ResultDTO.class, httpStatus, params);
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

        TextSubmissionWithoutAssessmentDTO submission1stRequest = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmissionWithoutAssessmentDTO.class, params);

        List<TextBlockDTO> blocksFrom1stRequest = blocksOf(submission1stRequest);
        final Set<TextBlockDTO> expectedBlockDTOs = blocks.stream().map(TextBlockDTO::of).collect(Collectors.toSet());
        assertThat(blocksFrom1stRequest).containsExactlyInAnyOrderElementsOf(expectedBlockDTOs);

        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO(
                List.of(FeedbackDTO.of(new Feedback().detailText("Test").credits(1d).reference(blocksFrom1stRequest.iterator().next().id()).type(FeedbackType.MANUAL))),
                new HashSet<>(blocksFrom1stRequest), null);
        request.postWithResponseBody(
                "/api/text/participations/" + participationId(submission1stRequest) + "/results/" + latestResultId(submission1stRequest) + "/submit-text-assessment",
                textAssessmentDTO, ResultDTO.class, HttpStatus.OK);

        TextParticipationDTO participation2ndRequest = request.get("/api/text/text-submissions/" + textSubmission.getId() + "/for-assessment", HttpStatus.OK,
                TextParticipationDTO.class, params);
        var submission2ndRequest = participation2ndRequest.submissions().getLast();
        List<TextBlockDTO> blocksFrom2ndRequest = submission2ndRequest.blocks();
        assertThat(blocksFrom2ndRequest).containsExactlyInAnyOrderElementsOf(expectedBlockDTOs);
    }

    @NonNull
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
                TEST_PREFIX + "tutor1", List.of(feedback));

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
        TextSubmissionWithoutAssessmentDTO submissionWithoutFirstAssessment = request.get(
                "/api/text/exercises/" + exerciseWithParticipation.getId() + "/text-submission-without-assessment", HttpStatus.OK, TextSubmissionWithoutAssessmentDTO.class,
                params);
        // verify that no new submission was created
        assertThat(submissionId(submissionWithoutFirstAssessment)).isEqualTo(submission.getId());
        // verify that the lock has been set
        assertThat(latestResult(submissionWithoutFirstAssessment)).isNotNull();
        assertThat(latestResult(submissionWithoutFirstAssessment).assessor().login()).isEqualTo(TEST_PREFIX + "tutor1");
        assertThat(latestResult(submissionWithoutFirstAssessment).assessmentType()).isEqualTo(AssessmentType.MANUAL);

        // make sure that new result correctly appears inside the continue box
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1Tutor1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1Tutor1.add("assessedByTutor", "true");
        paramsGetAssessedCR1Tutor1.add("correction-round", "0");
        var assessedSubmissionList = request.getList("/api/text/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK,
                TextSubmissionResponseDTO.class, paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().id()).isEqualTo(submissionId(submissionWithoutFirstAssessment));
        // result for correction round 0 corresponds to the locked latest result of the submission
        assertThat(assessedSubmissionList.getFirst().results()).extracting(ResultDTO::id).contains(latestResultId(submissionWithoutFirstAssessment));

        // assess submission and submit
        List<Feedback> feedbacks = ParticipationFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here"))
                .collect(Collectors.toCollection(ArrayList::new));
        TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO(feedbacks.stream().map(FeedbackDTO::of).toList(), null, null);
        ResultDTO firstSubmittedManualResult = request.postWithResponseBody("/api/text/participations/" + participationId(submissionWithoutFirstAssessment) + "/results/"
                + latestResultId(submissionWithoutFirstAssessment) + "/submit-text-assessment", textAssessmentDTO, ResultDTO.class, HttpStatus.OK);

        // make sure that new result correctly appears after the assessment for first correction round
        assessedSubmissionList = request.getList("/api/text/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK, TextSubmissionResponseDTO.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().id()).isEqualTo(submissionId(submissionWithoutFirstAssessment));
        assertThat(assessedSubmissionList.getFirst().results()).isNotEmpty();
        assertThat(firstSubmittedManualResult.assessor().login()).isEqualTo(TEST_PREFIX + "tutor1");

        // verify that the result contains the relationship
        assertThat(firstSubmittedManualResult).isNotNull();
        assertThat(firstSubmittedManualResult.participation().id()).isEqualTo(studentParticipation.getId());

        // verify that the relationship between student participation,
        var databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        var fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission().map(Submission::getId)).contains(submissionId(submissionWithoutFirstAssessment));
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

        final var submissionWithoutSecondAssessment = request.get("/api/text/exercises/" + exerciseWithParticipation.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmissionWithoutAssessmentDTO.class, paramsSecondCorrection);

        // verify that the submission is not new
        assertThat(submissionId(submissionWithoutSecondAssessment)).isEqualTo(submission.getId());
        // verify that the lock has been set
        assertThat(latestResult(submissionWithoutSecondAssessment)).isNotNull();
        assertThat(latestResult(submissionWithoutSecondAssessment).assessor().login()).isEqualTo(TEST_PREFIX + "tutor2");
        assertThat(latestResult(submissionWithoutSecondAssessment).assessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(latestResult(submissionWithoutSecondAssessment).feedbacks()).isNotEmpty();

        // verify that the relationship between student participation,
        databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission().map(Submission::getId)).contains(submissionId(submissionWithoutSecondAssessment));
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getResults().stream().filter(x -> x.getCompletionDate() == null).findFirst().map(Result::getId))
                .contains(latestResultId(submissionWithoutSecondAssessment));

        databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.getFirst();
        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).isPresent();
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getResults()).hasSize(2);
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getLatestResult().getId()).isEqualTo(latestResultId(submissionWithoutSecondAssessment));

        // build the updated feedback list from the locked result's feedbacks plus a new one
        List<FeedbackDTO> secondRoundFeedbacks = new ArrayList<>(latestResult(submissionWithoutSecondAssessment).feedbacks());
        Feedback secondCorrectionFeedback = new Feedback();
        secondCorrectionFeedback.setDetailText("asfd");
        secondCorrectionFeedback.setCredits(10.0);
        secondCorrectionFeedback.setPositive(true);
        secondRoundFeedbacks.add(FeedbackDTO.of(secondCorrectionFeedback));
        textAssessmentDTO = new TextAssessmentDTO(secondRoundFeedbacks, null, null);

        // assess submission and submit
        ResultDTO secondSubmittedManualResult = request.postWithResponseBody("/api/text/participations/" + participationId(submissionWithoutFirstAssessment) + "/results/"
                + latestResultId(submissionWithoutSecondAssessment) + "/submit-text-assessment", textAssessmentDTO, ResultDTO.class, HttpStatus.OK);
        assertThat(secondSubmittedManualResult).isNotNull();

        // check if feedback copy was set correctly
        assertThat(secondSubmittedManualResult.feedbacks()).isNotEmpty();

        // make sure that new result correctly appears after the assessment for second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR2 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR2.add("assessedByTutor", "true");
        paramsGetAssessedCR2.add("correction-round", "1");
        assessedSubmissionList = request.getList("/api/text/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK, TextSubmissionResponseDTO.class,
                paramsGetAssessedCR2);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().id()).isEqualTo(submissionId(submissionWithoutSecondAssessment));
        // result for correction round 1 corresponds to the just-submitted second manual result
        assertThat(assessedSubmissionList.getFirst().results()).extracting(ResultDTO::id).contains(secondSubmittedManualResult.id());

        // make sure that they do not appear for the first correction round as the tutor only assessed the second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1.add("assessedByTutor", "true");
        paramsGetAssessedCR1.add("correction-round", "0");
        assessedSubmissionList = request.getList("/api/text/exercises/" + exerciseWithParticipation.getId() + "/text-submissions", HttpStatus.OK, TextSubmissionResponseDTO.class,
                paramsGetAssessedCR1);

        assertThat(assessedSubmissionList).isEmpty();

        // Student should not have received a result over WebSocket as manual correction is ongoing
        verify(websocketMessagingService, never()).sendMessageToUser(notNull(), eq(Constants.NEW_RESULT_TOPIC), isA(de.tum.cit.aet.artemis.programming.dto.ResultDTO.class));
    }

    private void addAssessmentFeedbackAndCheckScore(TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment, List<Feedback> feedbacks, double pointsAwarded,
            long expectedScore) throws Exception {
        feedbacks.add(new Feedback().credits(pointsAwarded).type(FeedbackType.MANUAL_UNREFERENCED).detailText("gj")
                .reference(TextExerciseFactory.generateTextBlock(0, 5, "test" + feedbacks.size()).getId()));
        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO(feedbacks.stream().map(FeedbackDTO::of).toList(), null, null);
        ResultDTO response = request.postWithResponseBody(
                "/api/text/participations/" + participationId(submissionWithoutAssessment) + "/results/" + latestResultId(submissionWithoutAssessment) + "/submit-text-assessment",
                textAssessmentDTO, ResultDTO.class, HttpStatus.OK);
        assertThat(response.score()).isEqualTo(expectedScore);
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
        TextSubmissionWithoutAssessmentDTO submissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment",
                HttpStatus.OK, TextSubmissionWithoutAssessmentDTO.class, params);

        final Set<TextBlock> textBlocksToAssess = Set.of(TextExerciseFactory.generateTextBlock(0, 15, "This is Part 1,"),
                TextExerciseFactory.generateTextBlock(16, 35, " and this is Part 2."), TextExerciseFactory.generateTextBlock(36, 57, " There is also Part 3."));

        List<Feedback> feedbacks = new ArrayList<>();
        textBlocksToAssess.forEach(textBlock -> feedbacks.add(new Feedback().type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1").reference(textBlock.getId())));
        final TextAssessmentDTO textAssessmentDTO = new TextAssessmentDTO(feedbacks.stream().map(FeedbackDTO::of).toList(),
                textBlocksToAssess.stream().map(TextBlockDTO::of).collect(Collectors.toSet()), null);

        request.postWithResponseBody(
                "/api/text/participations/" + participationId(submissionWithoutAssessment) + "/results/" + latestResultId(submissionWithoutAssessment) + "/submit-text-assessment",
                textAssessmentDTO, ResultDTO.class, HttpStatus.OK);

        Set<TextBlock> textBlocks = textBlockRepository.findAllBySubmissionId(submissionId(submissionWithoutAssessment));
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
        TextSubmissionWithoutAssessmentDTO textSubmissionWithoutAssessment = request.get("/api/text/exercises/" + textExercise.getId() + "/text-submission-without-assessment",
                HttpStatus.OK, TextSubmissionWithoutAssessmentDTO.class, parameters);

        final Set<TextBlockDTO> textBlockDTOs = blocksOf(textSubmissionWithoutAssessment).stream()
                // simulate Angular (re-)serialization: keep id/text/indices, drop server-only fields
                .map(oldBlock -> new TextBlockDTO(oldBlock.id(), oldBlock.text(), oldBlock.startIndex(), oldBlock.endIndex(), null)).collect(Collectors.toSet());

        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(
                new Feedback().credits(20.00).type(FeedbackType.MANUAL).detailText("nice submission 3").reference(TextExerciseFactory.generateTextBlock(0, 15, "test3").getId()));
        TextAssessmentDTO dto = new TextAssessmentDTO(feedbacks.stream().map(FeedbackDTO::of).toList(), textBlockDTOs, null);

        // These two lines ensure the call count verification near the end of this test can spot the call
        // made during the PUT /api/text/participations/:id/text-assessment request and not other places.
        int irrelevantCallCount = 1;
        verify(textBlockService, times(irrelevantCallCount)).findAllBySubmissionId(submissionId(textSubmissionWithoutAssessment));

        request.putWithResponseBody(
                "/api/text/participations/" + participationId(textSubmissionWithoutAssessment) + "/results/" + latestResultId(textSubmissionWithoutAssessment) + "/text-assessment",
                dto, ResultDTO.class, HttpStatus.OK);

        feedbacks.removeFirst();
        dto = new TextAssessmentDTO(feedbacks.stream().map(FeedbackDTO::of).toList(), textBlockDTOs, null);

        request.putWithResponseBody(
                "/api/text/participations/" + participationId(textSubmissionWithoutAssessment) + "/results/" + latestResultId(textSubmissionWithoutAssessment) + "/text-assessment",
                dto, ResultDTO.class, HttpStatus.OK);

        var result = request.putWithResponseBody(
                "/api/text/participations/" + participationId(textSubmissionWithoutAssessment) + "/results/" + latestResultId(textSubmissionWithoutAssessment) + "/text-assessment",
                dto, ResultDTO.class, HttpStatus.OK);

        final var textSubmission = textSubmissionRepository.getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(result.id());

        // This is to ensure the fix for https://github.com/ls1intum/Artemis/issues/4962 is not removed. Please ensure that problem is not occurring
        // if you remove or change this verification. This test uses a spy because the error was caused by and EntityNotFound exception related to MySQL
        // however the intergration tests use H2 in-memory database so same code did not produce the same error.
        verify(textBlockService, times(irrelevantCallCount + 3)).findAllBySubmissionId(textSubmission.getId());

        Set<TextBlock> textBlocks = textBlockRepository.findAllBySubmissionId(submissionId(textSubmissionWithoutAssessment));
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
    private ResultDTO saveOrSubmitTextAssessment(Long participationId, Long latestResultId, TextAssessmentDTO textAssessmentDTO, boolean submit, HttpStatus expectedStatus)
            throws Exception {
        if (submit) {
            return request.postWithResponseBody("/api/text/participations/" + participationId + "/results/" + latestResultId + "/submit-text-assessment", textAssessmentDTO,
                    ResultDTO.class, expectedStatus);

        }
        else {
            return request.putWithResponseBody("/api/text/participations/" + participationId + "/results/" + latestResultId + "/text-assessment", textAssessmentDTO,
                    ResultDTO.class, expectedStatus);
        }
    }
}
