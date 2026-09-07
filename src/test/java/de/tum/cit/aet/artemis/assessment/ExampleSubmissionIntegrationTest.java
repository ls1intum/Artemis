package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.dto.ExampleSubmissionDetailDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackDTO;
import de.tum.cit.aet.artemis.assessment.dto.ResultDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ExampleSubmissionTestRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.TextAssessmentDTO;
import de.tum.cit.aet.artemis.text.dto.TextBlockDTO;
import de.tum.cit.aet.artemis.text.dto.TextExampleResultDTO;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ExampleSubmissionIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final Logger log = LoggerFactory.getLogger(ExampleSubmissionIntegrationTest.class);

    private static final String TEST_PREFIX = "examplesubmissionintegration";

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private ExampleSubmissionTestRepository exampleSubmissionRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private ExampleSubmission exampleSubmission;

    private Course course;

    private String emptyModel;

    private String validModel;

    @BeforeEach
    void initTestCase() throws Exception {
        log.debug("Test setup start");
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = courseUtilService.addEnrolledCourseWithModelingAndTextExercise(TEST_PREFIX);
        modelingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ModelingExercise.class);
        textExercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        emptyModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/empty-class-diagram.json");
        validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        log.debug("Test setup done");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAndUpdateExampleModelingSubmissionTutorial(boolean usedForTutorial) throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission(emptyModel, modelingExercise, false, usedForTutorial);
        ExampleSubmissionDetailDTO returnedExampleSubmission = request.postWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-submissions",
                exampleSubmission, ExampleSubmissionDetailDTO.class, HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedExampleSubmission.submission().id(), emptyModel);
        Optional<ExampleSubmission> storedExampleSubmission = exampleSubmissionRepository.findBySubmissionId(returnedExampleSubmission.submission().id());
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        exampleSubmission = participationUtilService.generateExampleSubmission(validModel, modelingExercise, false);
        returnedExampleSubmission = request.postWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission,
                ExampleSubmissionDetailDTO.class, HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedExampleSubmission.submission().id(), validModel);
        storedExampleSubmission = exampleSubmissionRepository.findBySubmissionId(returnedExampleSubmission.submission().id());
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExampleModelingSubmission(boolean usedForTutorial) throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission(emptyModel, modelingExercise, false, usedForTutorial);
        ExampleSubmissionDetailDTO returnedExampleSubmission = request.postWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-submissions",
                exampleSubmission, ExampleSubmissionDetailDTO.class, HttpStatus.OK);
        ExampleSubmissionDetailDTO updateExistingExampleSubmission = request.putWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-submissions",
                returnedExampleSubmission, ExampleSubmissionDetailDTO.class, HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(updateExistingExampleSubmission.submission().id(), emptyModel);
        Optional<ExampleSubmission> storedExampleSubmission = exampleSubmissionRepository.findBySubmissionId(updateExistingExampleSubmission.submission().id());
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        ExampleSubmission updatedExampleSubmission = participationUtilService.generateExampleSubmission(validModel, modelingExercise, false);
        ExampleSubmissionDetailDTO returnedUpdatedExampleSubmission = request.putWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-submissions",
                updatedExampleSubmission, ExampleSubmissionDetailDTO.class, HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedUpdatedExampleSubmission.submission().id(), validModel);
        storedExampleSubmission = exampleSubmissionRepository.findBySubmissionId(returnedUpdatedExampleSubmission.submission().id());
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();
    }

    /**
     * The example-submission edit page loads the exercise from the modeling detail endpoint and echoes that response
     * verbatim as {@code exampleSubmission.exercise} in its save PUT. The detail response embeds the exercise's example
     * submissions, so the echoed JSON must stay deserializable into the entity graph - in particular the nested
     * polymorphic {@link Submission} needs its {@code submissionExerciseType} discriminator on the wire.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExampleModelingSubmission_acceptsEchoedExerciseDetailResponse() throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission(validModel, modelingExercise, true);
        ExampleSubmissionDetailDTO created = request.postWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission,
                ExampleSubmissionDetailDTO.class, HttpStatus.OK);

        String exerciseDetailJson = request.get("/api/modeling/modeling-exercises/" + modelingExercise.getId(), HttpStatus.OK, String.class);
        String exampleSubmissionJson = request.get("/api/assessment/example-submissions/" + created.id(), HttpStatus.OK, String.class);

        ObjectMapper mapper = request.getObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("id", created.id());
        body.put("usedForTutorial", false);
        body.set("exercise", mapper.readTree(exerciseDetailJson));
        body.set("submission", mapper.readTree(exampleSubmissionJson).get("submission"));

        ExampleSubmissionDetailDTO updated = request.putWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-submissions", body,
                ExampleSubmissionDetailDTO.class, HttpStatus.OK);

        assertThat(updated.id()).isEqualTo(created.id());
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(updated.submission().id(), validModel);
    }

    /**
     * Once an example assessment exists, the edit page attaches the result it loaded from the (migrated, DTO-shaped)
     * example-assessment endpoint to the submission before the save PUT. The echoed result must keep every column the
     * server-side cascade merge writes back - {@code Result.exerciseId} is a primitive non-null FK column, so a wire
     * shape without it merges {@code exercise_id = 0} and the save dies on the foreign-key constraint.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExampleModelingSubmission_acceptsEchoedDtoShapedExampleAssessment() throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission(validModel, modelingExercise, true);
        ExampleSubmissionDetailDTO created = request.postWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission,
                ExampleSubmissionDetailDTO.class, HttpStatus.OK);
        // Example submissions have no participation, so build the example result directly (the fixture helpers
        // derive exerciseId from the participation and would NPE).
        Result exampleResult = new Result().submission(submissionOf(created)).assessmentType(AssessmentType.MANUAL).completionDate(ZonedDateTime.now()).score(50D).rated(true);
        exampleResult.setAssessor(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
        exampleResult.setExerciseId(modelingExercise.getId());
        exampleResult.setExampleResult(true);
        Feedback feedback = new Feedback();
        feedback.setCredits(2.0);
        feedback.setText("element:1");
        feedback.setDetailText("Good relation");
        feedback.setType(FeedbackType.MANUAL);
        feedback.setResult(exampleResult);
        exampleResult.addFeedback(feedback);
        exampleResult = resultRepository.save(exampleResult);

        // Every ingredient exactly as the page loads it: the (migrated) exercise detail, the example-submission
        // GET the page reads its submission from, and the (migrated) example-assessment wire shape.
        String exerciseDetailJson = request.get("/api/modeling/modeling-exercises/" + modelingExercise.getId(), HttpStatus.OK, String.class);
        String exampleSubmissionJson = request.get("/api/assessment/example-submissions/" + created.id(), HttpStatus.OK, String.class);
        String exampleAssessmentJson = request.get(
                "/api/modeling/exercises/" + modelingExercise.getId() + "/modeling-submissions/" + created.submission().id() + "/example-assessment", HttpStatus.OK, String.class);

        ObjectMapper mapper = request.getObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("id", created.id());
        body.put("usedForTutorial", false);
        body.set("exercise", mapper.readTree(exerciseDetailJson));
        // The submission node the page holds: the example-submission GET's nested submission, mutated the way the
        // component mutates it before saving (model edit + explanation + example flag), with the loaded example
        // assessment attached via setLatestSubmissionResult and its own submission reference deleted.
        ObjectNode submissionNode = (ObjectNode) mapper.readTree(exampleSubmissionJson).get("submission");
        submissionNode.put("model", validModel);
        submissionNode.put("explanationText", "updated explanation");
        submissionNode.put("exampleSubmission", true);
        ObjectNode resultNode = (ObjectNode) mapper.readTree(exampleAssessmentJson);
        resultNode.remove("submission");
        submissionNode.set("results", mapper.createArrayNode().add(resultNode));
        body.set("submission", submissionNode);

        Long feedbackId = feedback.getId();
        ExampleSubmissionDetailDTO updated = request.putWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-submissions", body,
                ExampleSubmissionDetailDTO.class, HttpStatus.OK);
        assertThat(updated.submission().explanationText()).isEqualTo("updated explanation");

        // The echoed result is not part of the request contract: the stored example assessment must survive the save
        // untouched (same result, same feedback rows, FK intact) and the content edit must be persisted.
        Result reloaded = resultRepository.findDistinctWithFeedbackBySubmissionId(created.submission().id()).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(exampleResult.getId());
        assertThat(reloaded.getExerciseId()).isEqualTo(modelingExercise.getId());
        assertThat(reloaded.getFeedbacks()).extracting(Feedback::getId).containsExactly(feedbackId);
        assertThat(reloaded.getFeedbacks().iterator().next().getDetailText()).isEqualTo("Good relation");
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(created.submission().id(), validModel);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAndDeleteExampleModelingSubmission(boolean usedForTutorial) throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission(validModel, modelingExercise, false, usedForTutorial);
        ExampleSubmissionDetailDTO returnedExampleSubmission = request.postWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-submissions",
                exampleSubmission, ExampleSubmissionDetailDTO.class, HttpStatus.OK);
        Long submissionId = returnedExampleSubmission.submission().id();

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(submissionId, validModel);
        Optional<ExampleSubmission> storedExampleSubmission = exampleSubmissionRepository.findBySubmissionId(submissionId);
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        request.delete("/api/assessment/example-submissions/" + storedExampleSubmission.get().getId(), HttpStatus.OK);
        assertThat(exampleSubmissionRepository.findAllByExerciseId(modelingExercise.getId())).isEmpty();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAndDeleteExampleModelingSubmissionWithResult(boolean usedForTutorial) throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission(validModel, modelingExercise, false, usedForTutorial);
        exampleSubmission.addTutorParticipations(new TutorParticipation());
        ExampleSubmissionDetailDTO returnedExampleSubmission = request.postWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-submissions",
                exampleSubmission, ExampleSubmissionDetailDTO.class, HttpStatus.OK);
        Long submissionId = returnedExampleSubmission.submission().id();

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(submissionId, validModel);
        Optional<ExampleSubmission> storedExampleSubmission = exampleSubmissionRepository.findBySubmissionId(submissionId);
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        request.delete("/api/assessment/example-submissions/" + storedExampleSubmission.get().getId(), HttpStatus.OK);
        assertThat(exampleSubmissionRepository.findAllByExerciseId(modelingExercise.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createExampleModelingSubmission_asTutor_forbidden() throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission(emptyModel, modelingExercise, true);
        request.post("/api/assessment/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void createExampleModelingSubmission_asStudent_forbidden() throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission(emptyModel, modelingExercise, true);
        request.post("/api/assessment/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExampleModelingSubmission() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission(validModel, modelingExercise, true));
        ExampleSubmissionDetailDTO response = assertThatDb(
                () -> request.get("/api/assessment/example-submissions/" + storedExampleSubmission.getId(), HttpStatus.OK, ExampleSubmissionDetailDTO.class))
                .hasBeenCalledAtMostTimes(6);
        assertThat(response.id()).isEqualTo(storedExampleSubmission.getId());
        assertThat(response.submission().id()).isEqualTo(storedExampleSubmission.getSubmission().getId());
        assertThat(response.submission().submissionExerciseType()).isEqualTo("modeling");
        modelingExerciseUtilService.checkModelsAreEqual(response.submission().model(), validModel);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getExampleModelingSubmission_asStudent_forbidden() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission(validModel, modelingExercise, true));
        request.get("/api/assessment/example-submissions/" + storedExampleSubmission.getId(), HttpStatus.FORBIDDEN, ExampleSubmissionDetailDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createExampleModelingAssessment() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission(validModel, modelingExercise, true));
        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        request.putWithResponseBody("/api/modeling/modeling-submissions/" + storedExampleSubmission.getId() + "/example-assessment", feedbacks, Result.class, HttpStatus.OK);

        Result storedResult = resultRepository.findDistinctWithFeedbackBySubmissionId(storedExampleSubmission.getSubmission().getId()).orElseThrow();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExampleModelingAssessment_whenTutorAndUsedForTutorial_shouldSendCleanedResult() throws Exception {
        ExampleSubmission exampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission(validModel, modelingExercise, true, true));
        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.putWithResponseBody("/api/modeling/modeling-submissions/" + exampleSubmission.getId() + "/example-assessment", feedbacks, Result.class, HttpStatus.OK);

        Result cleanResult = request.get(
                "/api/modeling/exercises/" + modelingExercise.getId() + "/modeling-submissions/" + exampleSubmission.getSubmission().getId() + "/example-assessment", HttpStatus.OK,
                Result.class);
        for (Feedback feedback : cleanResult.getFeedbacks()) {
            assertThat(feedback.getCredits()).isNull();
            assertThat(feedback.getDetailText()).isNull();
            assertThat(feedback.getReference()).isNotNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void prepareExampleTextSubmissionForAssessmentShouldCreateBlocks() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission("Text. Submission.", textExercise, true));

        ExampleSubmissionDetailDTO unpreparedExampleSubmission = request.get("/api/assessment/example-submissions/" + storedExampleSubmission.getId(), HttpStatus.OK,
                ExampleSubmissionDetailDTO.class);
        assertThat(unpreparedExampleSubmission.submission().submissionExerciseType()).isEqualTo("text");
        assertThat(unpreparedExampleSubmission.submission().text()).isEqualTo("Text. Submission.");
        assertThat(unpreparedExampleSubmission.submission().blocks()).isNullOrEmpty();

        request.postWithoutResponseBody("/api/assessment/exercises/" + textExercise.getId() + "/example-submissions/" + storedExampleSubmission.getId() + "/prepare-assessment",
                HttpStatus.OK, new LinkedMultiValueMap<>());
        ExampleSubmissionDetailDTO preparedExampleSubmission = request.get("/api/assessment/example-submissions/" + storedExampleSubmission.getId(), HttpStatus.OK,
                ExampleSubmissionDetailDTO.class);
        assertThat(preparedExampleSubmission.submission().blocks()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExampleTextAssessment() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission("Text. Submission.", textExercise, true));
        participationUtilService.addResultToSubmission(storedExampleSubmission.getSubmission(), AssessmentType.MANUAL, textExercise.getId());
        final TextExampleResultDTO exampleResult = request.get(
                "/api/text/exercises/" + textExercise.getId() + "/submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-result", HttpStatus.OK,
                TextExampleResultDTO.class);
        final List<TextBlockDTO> blocks = exampleResult.submission().blocks();
        assertThat(blocks).hasSize(2);
        List<Feedback> feedbacks = new ArrayList<>();
        final Iterator<TextBlockDTO> textBlockIterator = blocks.iterator();
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL).detailText("nice submission 1").reference(textBlockIterator.next().id()));
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL).detailText("nice submission 2").reference(textBlockIterator.next().id()));
        var dto = new TextAssessmentDTO(feedbacks.stream().map(FeedbackDTO::of).toList(), null, null);
        ResultDTO response = request.putWithResponseBody(
                "/api/text/exercises/" + textExercise.getId() + "/example-submissions/" + storedExampleSubmission.getId() + "/example-text-assessment", dto, ResultDTO.class,
                HttpStatus.OK);
        assertThat(response.exampleResult()).as("response uses the text ResultDTO contract").isTrue();
        assertThat(response.feedbacks()).hasSize(2);
        Result storedResult = resultRepository.findDistinctWithFeedbackBySubmissionId(storedExampleSubmission.getSubmission().getId()).orElseThrow();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
    }

    /**
     * Once an example assessment exists, the edit page attaches the result it loaded from the (migrated, DTO-shaped)
     * example-result endpoint to the submission before the save PUT. That wire shape does not carry
     * {@code Result.exerciseId}, a primitive non-null FK column the cascade merge writes back, so the server must derive
     * it from the path instead of the payload - otherwise the merge writes {@code exercise_id = 0} and the save dies on
     * the foreign-key constraint.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExampleTextSubmission_acceptsEchoedDtoShapedExampleResult() throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission("Text. Submission.", textExercise, true);
        ExampleSubmissionDetailDTO created = request.postWithResponseBody("/api/assessment/exercises/" + textExercise.getId() + "/example-submissions", exampleSubmission,
                ExampleSubmissionDetailDTO.class, HttpStatus.OK);
        Submission submissionWithResult = participationUtilService.addResultToSubmission(submissionOf(created), AssessmentType.MANUAL, textExercise.getId());
        Result exampleResult = submissionWithResult.getLatestResult();

        String exerciseDetailJson = request.get("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.OK, String.class);
        String exampleSubmissionJson = request.get("/api/assessment/example-submissions/" + created.id(), HttpStatus.OK, String.class);
        // The result exactly as the page loads it: the migrated example-result endpoint's wire shape.
        String exampleResultJson = request.get("/api/text/exercises/" + textExercise.getId() + "/submissions/" + created.submission().id() + "/example-result", HttpStatus.OK,
                String.class);

        ObjectMapper mapper = request.getObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("id", created.id());
        body.put("usedForTutorial", false);
        body.set("exercise", mapper.readTree(exerciseDetailJson));
        // The submission the page holds, with the result attached the way setLatestSubmissionResult does: the
        // echoed example-result payload, its own submission reference deleted by the client.
        ObjectNode submissionNode = (ObjectNode) mapper.readTree(exampleSubmissionJson).get("submission");
        ObjectNode resultNode = (ObjectNode) mapper.readTree(exampleResultJson);
        resultNode.remove("submission");
        submissionNode.set("results", mapper.createArrayNode().add(resultNode));
        body.set("submission", submissionNode);

        request.putWithResponseBody("/api/assessment/exercises/" + textExercise.getId() + "/example-submissions", body, ExampleSubmissionDetailDTO.class, HttpStatus.OK);

        Result reloaded = resultRepository.findById(exampleResult.getId()).orElseThrow();
        assertThat(reloaded.getExerciseId()).isEqualTo(textExercise.getId());
    }

    /**
     * Same echo, other load path: within one session the page keeps the response of the example-assessment save
     * (a {@link ResultDTO}) and attaches that to the next example-submission save PUT. {@link ResultDTO} happens to carry
     * {@code exerciseId}, so this passes regardless of the server-side stamping; it pins the second echo shape so a
     * future change to either side keeps the save working.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExampleTextSubmission_acceptsEchoedExampleAssessmentResponse() throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission("Text. Submission.", textExercise, true);
        ExampleSubmissionDetailDTO created = request.postWithResponseBody("/api/assessment/exercises/" + textExercise.getId() + "/example-submissions", exampleSubmission,
                ExampleSubmissionDetailDTO.class, HttpStatus.OK);
        participationUtilService.addResultToSubmission(submissionOf(created), AssessmentType.MANUAL, textExercise.getId());
        final TextExampleResultDTO exampleResult = request.get("/api/text/exercises/" + textExercise.getId() + "/submissions/" + created.submission().id() + "/example-result",
                HttpStatus.OK, TextExampleResultDTO.class);
        List<Feedback> feedbacks = List
                .of(new Feedback().credits(80.00).type(FeedbackType.MANUAL).detailText("nice submission 1").reference(exampleResult.submission().blocks().iterator().next().id()));
        var assessmentDto = new TextAssessmentDTO(feedbacks.stream().map(FeedbackDTO::of).toList(), null, null);
        ResultDTO assessmentResponse = request.putWithResponseBody(
                "/api/text/exercises/" + textExercise.getId() + "/example-submissions/" + created.id() + "/example-text-assessment", assessmentDto, ResultDTO.class, HttpStatus.OK);

        String exerciseDetailJson = request.get("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.OK, String.class);
        String exampleSubmissionJson = request.get("/api/assessment/example-submissions/" + created.id(), HttpStatus.OK, String.class);
        ObjectMapper mapper = request.getObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("id", created.id());
        body.put("usedForTutorial", false);
        body.set("exercise", mapper.readTree(exerciseDetailJson));
        ObjectNode submissionNode = (ObjectNode) mapper.readTree(exampleSubmissionJson).get("submission");
        ObjectNode resultNode = mapper.valueToTree(assessmentResponse);
        resultNode.remove("submission");
        submissionNode.set("results", mapper.createArrayNode().add(resultNode));
        body.set("submission", submissionNode);

        request.putWithResponseBody("/api/assessment/exercises/" + textExercise.getId() + "/example-submissions", body, ExampleSubmissionDetailDTO.class, HttpStatus.OK);

        Result reloaded = resultRepository.findById(assessmentResponse.id()).orElseThrow();
        assertThat(reloaded.getExerciseId()).isEqualTo(textExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExampleTextAssessmentNotExistentId() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission("Text. Submission.", textExercise, true));
        participationUtilService.addResultToSubmission(storedExampleSubmission.getSubmission(), AssessmentType.MANUAL, textExercise.getId());
        final TextExampleResultDTO exampleResult = request.get(
                "/api/text/exercises/" + textExercise.getId() + "/submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-result", HttpStatus.OK,
                TextExampleResultDTO.class);
        final List<TextBlockDTO> blocks = exampleResult.submission().blocks();
        assertThat(blocks).hasSize(2);
        List<Feedback> feedbacks = ParticipationFactory.generateManualFeedback();
        var dto = new TextAssessmentDTO(feedbacks.stream().map(FeedbackDTO::of).toList(), null, null);
        long randomId = 1233;
        request.putWithResponseBody("/api/text/exercises/" + textExercise.getId() + "/example-submissions/" + randomId + "/example-text-assessment", dto, ResultDTO.class,
                HttpStatus.NOT_FOUND);
        assertThat(exampleSubmissionRepository.findBySubmissionId(randomId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExampleTextAssessment_wrongExerciseId() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission("Text. Submission.", textExercise, true));
        participationUtilService.addResultToSubmission(storedExampleSubmission.getSubmission(), AssessmentType.MANUAL, textExercise.getId());
        final TextExampleResultDTO exampleResult = request.get(
                "/api/text/exercises/" + textExercise.getId() + "/submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-result", HttpStatus.OK,
                TextExampleResultDTO.class);
        final List<TextBlockDTO> blocks = exampleResult.submission().blocks();
        assertThat(blocks).hasSize(2);
        List<Feedback> feedbacks = ParticipationFactory.generateManualFeedback();
        var dto = new TextAssessmentDTO(feedbacks.stream().map(FeedbackDTO::of).toList(), null, null);
        long randomId = 1233;
        request.putWithResponseBody("/api/text/exercises/" + randomId + "/example-submissions/" + storedExampleSubmission.getId() + "/example-text-assessment", dto,
                ResultDTO.class, HttpStatus.BAD_REQUEST);
        assertThat(exampleSubmissionRepository.findBySubmissionId(randomId)).isEmpty();
    }

    /**
     * The text editor page creates an example submission the way the client builds it: the typed text as a bare
     * submission, the loaded exercise attached, no submission id. Nothing on the wire is an entity anymore.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExampleTextSubmission_clientShape() throws Exception {
        String exerciseDetailJson = request.get("/api/text/text-exercises/" + textExercise.getId(), HttpStatus.OK, String.class);
        ObjectMapper mapper = request.getObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("usedForTutorial", true);
        body.set("exercise", mapper.readTree(exerciseDetailJson));
        body.set("submission", mapper.createObjectNode().put("text", "Example text"));

        ExampleSubmissionDetailDTO created = request.postWithResponseBody("/api/assessment/exercises/" + textExercise.getId() + "/example-submissions", body,
                ExampleSubmissionDetailDTO.class, HttpStatus.OK);

        assertThat(created.id()).isNotNull();
        assertThat(created.usedForTutorial()).isTrue();
        assertThat(created.submission().id()).isNotNull();
        assertThat(created.submission().submissionExerciseType()).isEqualTo("text");
        assertThat(created.submission().text()).isEqualTo("Example text");
        ExampleSubmission stored = exampleSubmissionRepository.findBySubmissionId(created.submission().id()).orElseThrow();
        assertThat(stored.getId()).isEqualTo(created.id());
        assertThat(stored.getExercise().getId()).isEqualTo(textExercise.getId());
        assertThat(stored.isUsedForTutorial()).isTrue();
        assertThat(stored.getSubmission().isExampleSubmission()).isTrue();
        assertThat(((TextSubmission) stored.getSubmission()).getText()).isEqualTo("Example text");
        assertThat(exampleSubmissionRepository.findAllByExerciseId(textExercise.getId())).extracting(ExampleSubmission::getId).containsExactly(created.id());
    }

    /**
     * Second visit: the page echoes the example submission it loaded (no exercise on it), with the text edited and the
     * training mode switched. The path exercise is the reference; the edit must be persisted.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExampleTextSubmission_loadedShapeWithoutExercise() throws Exception {
        ExampleSubmission stored = participationUtilService.addExampleSubmission(participationUtilService.generateExampleSubmission("Text. Submission.", textExercise, true, true));
        String exampleSubmissionJson = request.get("/api/assessment/example-submissions/" + stored.getId(), HttpStatus.OK, String.class);
        ObjectMapper mapper = request.getObjectMapper();
        ObjectNode body = (ObjectNode) mapper.readTree(exampleSubmissionJson);
        assertThat(body.has("exercise")).isFalse();
        body.put("usedForTutorial", false);
        ((ObjectNode) body.get("submission")).put("text", "Edited text.");

        ExampleSubmissionDetailDTO updated = request.putWithResponseBody("/api/assessment/exercises/" + textExercise.getId() + "/example-submissions", body,
                ExampleSubmissionDetailDTO.class, HttpStatus.OK);

        assertThat(updated.id()).isEqualTo(stored.getId());
        assertThat(updated.usedForTutorial()).isFalse();
        assertThat(updated.submission().id()).isEqualTo(stored.getSubmission().getId());
        assertThat(updated.submission().text()).isEqualTo("Edited text.");
        ExampleSubmission reloaded = exampleSubmissionRepository.findById(stored.getId()).orElseThrow();
        assertThat(reloaded.isUsedForTutorial()).isFalse();
        assertThat(((TextSubmission) reloaded.getSubmission()).getText()).isEqualTo("Edited text.");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExampleSubmission_bodyExerciseDiffersFromPath_badRequest() throws Exception {
        ExampleSubmission stored = participationUtilService.addExampleSubmission(participationUtilService.generateExampleSubmission("Text. Submission.", textExercise, true));
        ObjectMapper mapper = request.getObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("id", stored.getId());
        body.set("exercise", mapper.createObjectNode().put("id", modelingExercise.getId()));
        body.set("submission", mapper.createObjectNode().put("text", "Edited text."));

        request.put("/api/assessment/exercises/" + textExercise.getId() + "/example-submissions", body, HttpStatus.BAD_REQUEST);

        assertThat(((TextSubmission) exampleSubmissionRepository.findById(stored.getId()).orElseThrow().getSubmission()).getText()).isEqualTo("Text. Submission.");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExampleSubmission_ofAnotherExercise_badRequest() throws Exception {
        ExampleSubmission stored = participationUtilService.addExampleSubmission(participationUtilService.generateExampleSubmission("Text. Submission.", textExercise, true));
        ObjectMapper mapper = request.getObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("id", stored.getId());
        body.set("submission", mapper.createObjectNode().put("model", validModel));

        request.put("/api/assessment/exercises/" + modelingExercise.getId() + "/example-submissions", body, HttpStatus.BAD_REQUEST);

        assertThat(((TextSubmission) exampleSubmissionRepository.findById(stored.getId()).orElseThrow().getSubmission()).getText()).isEqualTo("Text. Submission.");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExampleSubmission_withoutSubmission_badRequest() throws Exception {
        ObjectMapper mapper = request.getObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("usedForTutorial", false);

        request.post("/api/assessment/exercises/" + textExercise.getId() + "/example-submissions", body, HttpStatus.BAD_REQUEST);

        assertThat(exampleSubmissionRepository.findAllByExerciseId(textExercise.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExampleSubmission_unsupportedExerciseType_badRequest() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadExerciseUtilService.addFileUploadExercise(course, null, null, null, null);
        ObjectMapper mapper = request.getObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.set("submission", mapper.createObjectNode().put("text", "Example text"));

        request.post("/api/assessment/exercises/" + fileUploadExercise.getId() + "/example-submissions", body, HttpStatus.BAD_REQUEST);

        assertThat(exampleSubmissionRepository.findAllByExerciseId(fileUploadExercise.getId())).isEmpty();
    }

    private Submission submissionOf(ExampleSubmissionDetailDTO exampleSubmission) {
        // fetch the results too: the fixture helpers add a result to the collection
        return exampleSubmissionRepository.findByIdWithEagerResultAndFeedbackElseThrow(exampleSubmission.id()).getSubmission();
    }

    private ExampleSubmissionDetailDTO importExampleSubmission(Long exerciseId, Long submissionId, HttpStatus expectedStatus) throws Exception {
        return request.postWithResponseBody("/api/assessment/exercises/" + exerciseId + "/example-submissions/import?sourceSubmissionId=" + submissionId, null,
                ExampleSubmissionDetailDTO.class, expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleSubmissionWithTextSubmission() throws Exception {
        TextSubmission submission = ParticipationFactory.generateTextSubmission("submissionText", Language.ENGLISH, true);
        submission = textExerciseUtilService.saveTextSubmission(textExercise, submission, TEST_PREFIX + "student1");

        TextBlock textBlock = new TextBlock();
        textBlock.setStartIndex(0);
        textBlock.setEndIndex(14);
        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(Set.of(textBlock), submission);

        participationUtilService.addResultToSubmission(submission, AssessmentType.MANUAL, textExercise.getId());

        // add one feedback for the created text block
        List<TextBlock> textBlocks = new ArrayList<>(submission.getBlocks());
        Feedback feedback = new Feedback();

        assertThat(textBlocks).isNotEmpty();

        feedback.setCredits(1.0);
        feedback.setReference(textBlocks.getFirst().getId());
        participationUtilService.addFeedbackToResult(feedback, submission.getLatestResult());

        ExampleSubmissionDetailDTO exampleSubmission = importExampleSubmission(textExercise.getId(), submission.getId(), HttpStatus.OK);
        assertThat(exampleSubmission.id()).isNotNull();
        assertThat(exampleSubmission.submission().submissionExerciseType()).isEqualTo("text");
        assertThat(exampleSubmission.submission().text()).isEqualTo(submission.getText());
        assertThat(exampleSubmission.submission().blocks()).hasSize(1);
        assertThat(exampleSubmission.submission().blocks().getFirst().text()).isEqualTo(textBlock.getText());
        // the copied assessment is not on the wire; check it through a fresh lookup
        Result importedResult = resultRepository.findDistinctWithFeedbackBySubmissionId(exampleSubmission.submission().id()).orElseThrow();
        assertThat(importedResult.getFeedbacks()).hasSize(1);
        Feedback importedFeedback = importedResult.getFeedbacks().iterator().next();
        assertThat(importedFeedback.getCredits()).isEqualTo(feedback.getCredits());
        assertThat(importedFeedback.getReference()).isEqualTo(exampleSubmission.submission().blocks().getFirst().id());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleSubmissionWithModelingSubmission() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(modelingExercise, submission, TEST_PREFIX + "student1");
        participationUtilService.addResultToSubmission(submission, AssessmentType.MANUAL, modelingExercise.getId());

        ExampleSubmissionDetailDTO exampleSubmission = importExampleSubmission(modelingExercise.getId(), submission.getId(), HttpStatus.OK);
        assertThat(exampleSubmission.id()).isNotNull();
        assertThat(exampleSubmission.submission().submissionExerciseType()).isEqualTo("modeling");
        assertThat(exampleSubmission.submission().model()).isEqualTo(submission.getModel());
        Result importedResult = resultRepository.findDistinctWithFeedbackBySubmissionId(exampleSubmission.submission().id()).orElseThrow();
        assertThat(importedResult.getScore()).isEqualTo(submission.getLatestResult().getScore());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleSubmissionForModelingExerciseCopiesGradingInstruction() throws Exception {
        testGradingCriteriaAreImported(modelingExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleSubmissionForTextExerciseCopiesGradingInstruction() throws Exception {
        testGradingCriteriaAreImported(textExercise);
    }

    private void testGradingCriteriaAreImported(Exercise exercise) throws Exception {
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(exercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        var studentParticipation = participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(exercise, TEST_PREFIX + "instructor1");
        Submission originalSubmission = studentParticipation.findLatestSubmission().orElseThrow();
        Optional<Result> orginalResult = resultRepository.findDistinctWithFeedbackBySubmissionId(originalSubmission.getId());

        ExampleSubmissionDetailDTO exampleSubmission = importExampleSubmission(exercise.getId(), originalSubmission.getId(), HttpStatus.OK);
        Result importedResult = resultRepository.findDistinctWithFeedbackBySubmissionId(exampleSubmission.submission().id()).orElseThrow();
        assertThat(importedResult.getFeedbacks().iterator().next().getGradingInstruction().getId())
                .isEqualTo(orginalResult.orElseThrow().getFeedbacks().iterator().next().getGradingInstruction().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleSubmissionWithStudentSubmission_wrongExerciseId() throws Exception {
        Submission submission = new TextSubmission();
        submission.setId(12345L);
        Long randomId = 1233L;
        importExampleSubmission(randomId, submission.getId(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleSubmissionWithStudentSubmission_isNotAtLeastInstructorInExercise_forbidden() throws Exception {
        Submission submission = new TextSubmission();
        submission.setId(12345L);
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        userUtilService.unenrollUserFromCourseByRole(instructor, course, CourseRole.INSTRUCTOR);
        importExampleSubmission(textExercise.getId(), submission.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleSubmissionWithTextSubmission_exerciseIdNotMatched() throws Exception {
        TextSubmission submission = ParticipationFactory.generateTextSubmission("submissionText", Language.ENGLISH, true);
        submission = textExerciseUtilService.saveTextSubmission(textExercise, submission, TEST_PREFIX + "student1");

        Exercise textExerciseToBeConflicted = new TextExercise();
        textExerciseToBeConflicted.setCourse(course);
        Exercise exercise = exerciseRepository.save(textExerciseToBeConflicted);

        importExampleSubmission(exercise.getId(), submission.getId(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleSubmissionWithModelingSubmission_exerciseIdNotMatched() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(modelingExercise, submission, TEST_PREFIX + "student1");

        Exercise modelingExerciseToBeConflicted = new ModelingExercise();
        modelingExerciseToBeConflicted.setCourse(course);
        Exercise exercise = exerciseRepository.save(modelingExerciseToBeConflicted);

        importExampleSubmission(exercise.getId(), submission.getId(), HttpStatus.BAD_REQUEST);

    }

}
