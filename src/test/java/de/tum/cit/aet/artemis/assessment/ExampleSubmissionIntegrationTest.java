package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

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

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleParticipation;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.dto.ExampleParticipationInputDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ExampleParticipationTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.TextAssessmentDTO;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ExampleParticipationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final Logger log = LoggerFactory.getLogger(ExampleParticipationIntegrationTest.class);

    private static final String TEST_PREFIX = "examplesubmissionintegration";

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private ExampleParticipationTestRepository exampleParticipationRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private ExampleParticipation exampleParticipation;

    private Course course;

    private String emptyModel;

    private String validModel;

    @BeforeEach
    void initTestCase() throws Exception {
        log.debug("Test setup start");
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        course = courseUtilService.addCourseWithModelingAndTextExercise();
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
        exampleParticipation = participationUtilService.generateExampleParticipation(emptyModel, modelingExercise, false, usedForTutorial);
        ExampleParticipation returnedExampleParticipation = request.postWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-participations",
                ExampleParticipationInputDTO.of(exampleParticipation), ExampleParticipation.class, HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedExampleParticipation.getSubmission().getId(), emptyModel);
        Optional<ExampleParticipation> storedExampleParticipation = exampleParticipationRepository.findBySubmissionsId(returnedExampleParticipation.getSubmission().getId());
        assertThat(storedExampleParticipation).as("example submission correctly stored").isPresent();
        assertThat(storedExampleParticipation.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        exampleParticipation = participationUtilService.generateExampleParticipation(validModel, modelingExercise, false);
        returnedExampleParticipation = request.postWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-participations",
                ExampleParticipationInputDTO.of(exampleParticipation), ExampleParticipation.class, HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedExampleParticipation.getSubmission().getId(), validModel);
        storedExampleParticipation = exampleParticipationRepository.findBySubmissionsId(returnedExampleParticipation.getSubmission().getId());
        assertThat(storedExampleParticipation).as("example submission correctly stored").isPresent();
        assertThat(storedExampleParticipation.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExampleModelingSubmission(boolean usedForTutorial) throws Exception {
        exampleParticipation = participationUtilService.generateExampleParticipation(emptyModel, modelingExercise, false, usedForTutorial);
        ExampleParticipation returnedExampleParticipation = request.postWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-participations",
                ExampleParticipationInputDTO.of(exampleParticipation), ExampleParticipation.class, HttpStatus.OK);
        ExampleParticipation updateExistingExampleParticipation = request.putWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-participations",
                ExampleParticipationInputDTO.of(returnedExampleParticipation), ExampleParticipation.class, HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(updateExistingExampleParticipation.getSubmission().getId(), emptyModel);
        Optional<ExampleParticipation> storedExampleParticipation = exampleParticipationRepository.findBySubmissionsId(updateExistingExampleParticipation.getSubmission().getId());
        assertThat(storedExampleParticipation).as("example submission correctly stored").isPresent();
        assertThat(storedExampleParticipation.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        ExampleParticipation updatedExampleParticipation = participationUtilService.generateExampleParticipation(validModel, modelingExercise, false);
        ExampleParticipation returnedUpdatedExampleParticipation = request.putWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-participations",
                ExampleParticipationInputDTO.of(updatedExampleParticipation), ExampleParticipation.class, HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedUpdatedExampleParticipation.getSubmission().getId(), validModel);
        storedExampleParticipation = exampleParticipationRepository.findBySubmissionsId(returnedUpdatedExampleParticipation.getSubmission().getId());
        assertThat(storedExampleParticipation).as("example submission correctly stored").isPresent();
        assertThat(storedExampleParticipation.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAndDeleteExampleModelingSubmission(boolean usedForTutorial) throws Exception {
        exampleParticipation = participationUtilService.generateExampleParticipation(validModel, modelingExercise, false, usedForTutorial);
        ExampleParticipation returnedExampleParticipation = request.postWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-participations",
                ExampleParticipationInputDTO.of(exampleParticipation), ExampleParticipation.class, HttpStatus.OK);
        Long submissionId = returnedExampleParticipation.getSubmission().getId();

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(submissionId, validModel);
        Optional<ExampleParticipation> storedExampleParticipation = exampleParticipationRepository.findBySubmissionsId(submissionId);
        assertThat(storedExampleParticipation).as("example submission correctly stored").isPresent();
        assertThat(storedExampleParticipation.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        request.delete("/api/assessment/example-participations/" + storedExampleParticipation.get().getId(), HttpStatus.OK);
        assertThat(exampleParticipationRepository.findAllByExerciseId(modelingExercise.getId())).isEmpty();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAndDeleteExampleModelingSubmissionWithResult(boolean usedForTutorial) throws Exception {
        exampleParticipation = participationUtilService.generateExampleParticipation(validModel, modelingExercise, false, usedForTutorial);
        exampleParticipation.addTutorParticipation(new TutorParticipation());
        ExampleParticipation returnedExampleParticipation = request.postWithResponseBody("/api/assessment/exercises/" + modelingExercise.getId() + "/example-participations",
                ExampleParticipationInputDTO.of(exampleParticipation), ExampleParticipation.class, HttpStatus.OK);
        Long submissionId = returnedExampleParticipation.getSubmission().getId();

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(submissionId, validModel);
        Optional<ExampleParticipation> storedExampleParticipation = exampleParticipationRepository.findBySubmissionsId(submissionId);
        assertThat(storedExampleParticipation).as("example submission correctly stored").isPresent();
        assertThat(storedExampleParticipation.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        request.delete("/api/assessment/example-participations/" + storedExampleParticipation.get().getId(), HttpStatus.OK);
        assertThat(exampleParticipationRepository.findAllByExerciseId(modelingExercise.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createExampleModelingSubmission_asTutor_forbidden() throws Exception {
        exampleParticipation = participationUtilService.generateExampleParticipation(emptyModel, modelingExercise, true);
        request.post("/api/assessment/exercises/" + modelingExercise.getId() + "/example-participations", ExampleParticipationInputDTO.of(exampleParticipation),
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void createExampleModelingSubmission_asStudent_forbidden() throws Exception {
        exampleParticipation = participationUtilService.generateExampleParticipation(emptyModel, modelingExercise, true);
        request.post("/api/assessment/exercises/" + modelingExercise.getId() + "/example-participations", ExampleParticipationInputDTO.of(exampleParticipation),
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExampleModelingSubmission() throws Exception {
        ExampleParticipation storedExampleParticipation = participationUtilService
                .saveExampleParticipation(participationUtilService.generateExampleParticipation(validModel, modelingExercise, true));
        exampleParticipation = request.get("/api/assessment/example-participations/" + storedExampleParticipation.getId(), HttpStatus.OK, ExampleParticipation.class);
        modelingExerciseUtilService.checkModelsAreEqual(((ModelingSubmission) exampleParticipation.getSubmission()).getModel(), validModel);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getExampleModelingSubmission_asStudent_forbidden() throws Exception {
        ExampleParticipation storedExampleParticipation = participationUtilService
                .saveExampleParticipation(participationUtilService.generateExampleParticipation(validModel, modelingExercise, true));
        request.get("/api/assessment/example-participations/" + storedExampleParticipation.getId(), HttpStatus.FORBIDDEN, ExampleParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createExampleModelingAssessment() throws Exception {
        ExampleParticipation storedExampleParticipation = participationUtilService
                .saveExampleParticipation(participationUtilService.generateExampleParticipation(validModel, modelingExercise, true));
        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        request.putWithResponseBody("/api/modeling/modeling-submissions/" + storedExampleParticipation.getId() + "/example-assessment", feedbacks, Result.class, HttpStatus.OK);

        Result storedResult = resultRepository.findDistinctWithFeedbackBySubmissionId(storedExampleParticipation.getSubmission().getId()).orElseThrow();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, new ArrayList<>(storedResult.getFeedbacks()), FeedbackType.MANUAL);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExampleModelingAssessment_whenTutorAndUsedForTutorial_shouldSendCleanedResult() throws Exception {
        ExampleParticipation exampleParticipation = participationUtilService
                .saveExampleParticipation(participationUtilService.generateExampleParticipation(validModel, modelingExercise, true, true));
        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.putWithResponseBody("/api/modeling/modeling-submissions/" + exampleParticipation.getId() + "/example-assessment", feedbacks, Result.class, HttpStatus.OK);

        Result cleanResult = request.get(
                "/api/modeling/exercise/" + modelingExercise.getId() + "/modeling-submissions/" + exampleParticipation.getSubmission().getId() + "/example-assessment",
                HttpStatus.OK, Result.class);
        for (Feedback feedback : cleanResult.getFeedbacks()) {
            assertThat(feedback.getCredits()).isNull();
            assertThat(feedback.getDetailText()).isNull();
            assertThat(feedback.getReference()).isNotNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void prepareExampleTextSubmissionForAssessmentShouldCreateBlocks() throws Exception {
        ExampleParticipation storedExampleParticipation = participationUtilService
                .saveExampleParticipation(participationUtilService.generateExampleParticipation("Text. Submission.", textExercise, true));

        ExampleParticipation unpreparedExampleParticipation = request.get("/api/assessment/example-participations/" + storedExampleParticipation.getId(), HttpStatus.OK,
                ExampleParticipation.class);
        TextSubmission unpreparedTextSubmission = (TextSubmission) unpreparedExampleParticipation.getSubmission();
        assertThat(unpreparedTextSubmission.getBlocks()).hasSize(0);

        request.postWithoutResponseBody(
                "/api/assessment/exercises/" + textExercise.getId() + "/example-participations/" + storedExampleParticipation.getId() + "/prepare-assessment", HttpStatus.OK,
                new LinkedMultiValueMap<>());
        ExampleParticipation preparedExampleParticipation = request.get("/api/assessment/example-participations/" + storedExampleParticipation.getId(), HttpStatus.OK,
                ExampleParticipation.class);
        TextSubmission preparedTextSubmission = (TextSubmission) preparedExampleParticipation.getSubmission();
        assertThat(preparedTextSubmission.getBlocks()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExampleTextAssessment() throws Exception {
        ExampleParticipation storedExampleParticipation = participationUtilService
                .saveExampleParticipation(participationUtilService.generateExampleParticipation("Text. Submission.", textExercise, true));
        participationUtilService.addResultToSubmission(storedExampleParticipation.getSubmission(), AssessmentType.MANUAL, textExercise.getId());
        final Result exampleResult = request.get(
                "/api/text/exercises/" + textExercise.getId() + "/submissions/" + storedExampleParticipation.getSubmission().getId() + "/example-result", HttpStatus.OK,
                Result.class);
        final Set<TextBlock> blocks = ((TextSubmission) exampleResult.getSubmission()).getBlocks();
        assertThat(blocks).hasSize(2);
        List<Feedback> feedbacks = new ArrayList<>();
        final Iterator<TextBlock> textBlockIterator = blocks.iterator();
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL).detailText("nice submission 1").reference(textBlockIterator.next().getId()));
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL).detailText("nice submission 2").reference(textBlockIterator.next().getId()));
        var dto = new TextAssessmentDTO();
        dto.setFeedbacks(feedbacks);
        request.putWithResponseBody("/api/text/exercises/" + textExercise.getId() + "/example-participations/" + storedExampleParticipation.getId() + "/example-text-assessment",
                dto, Result.class, HttpStatus.OK);
        Result storedResult = resultRepository.findDistinctWithFeedbackBySubmissionId(storedExampleParticipation.getSubmission().getId()).orElseThrow();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, new ArrayList<>(storedResult.getFeedbacks()), FeedbackType.MANUAL);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExampleTextAssessmentNotExistentId() throws Exception {
        ExampleParticipation storedExampleParticipation = participationUtilService
                .saveExampleParticipation(participationUtilService.generateExampleParticipation("Text. Submission.", textExercise, true));
        participationUtilService.addResultToSubmission(storedExampleParticipation.getSubmission(), AssessmentType.MANUAL, textExercise.getId());
        final Result exampleResult = request.get(
                "/api/text/exercises/" + textExercise.getId() + "/submissions/" + storedExampleParticipation.getSubmission().getId() + "/example-result", HttpStatus.OK,
                Result.class);
        final Set<TextBlock> blocks = ((TextSubmission) exampleResult.getSubmission()).getBlocks();
        assertThat(blocks).hasSize(2);
        List<Feedback> feedbacks = ParticipationFactory.generateManualFeedback();
        var dto = new TextAssessmentDTO();
        dto.setFeedbacks(feedbacks);
        long randomId = 1233;
        request.putWithResponseBody("/api/text/exercises/" + textExercise.getId() + "/example-participations/" + randomId + "/example-text-assessment", dto, Result.class,
                HttpStatus.NOT_FOUND);
        assertThat(exampleParticipationRepository.findBySubmissionsId(randomId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExampleTextAssessment_wrongExerciseId() throws Exception {
        ExampleParticipation storedExampleParticipation = participationUtilService
                .saveExampleParticipation(participationUtilService.generateExampleParticipation("Text. Submission.", textExercise, true));
        participationUtilService.addResultToSubmission(storedExampleParticipation.getSubmission(), AssessmentType.MANUAL, textExercise.getId());
        final Result exampleResult = request.get(
                "/api/text/exercises/" + textExercise.getId() + "/submissions/" + storedExampleParticipation.getSubmission().getId() + "/example-result", HttpStatus.OK,
                Result.class);
        final Set<TextBlock> blocks = ((TextSubmission) exampleResult.getSubmission()).getBlocks();
        assertThat(blocks).hasSize(2);
        List<Feedback> feedbacks = ParticipationFactory.generateManualFeedback();
        var dto = new TextAssessmentDTO();
        dto.setFeedbacks(feedbacks);
        long randomId = 1233;
        request.putWithResponseBody("/api/text/exercises/" + randomId + "/example-participations/" + storedExampleParticipation.getId() + "/example-text-assessment", dto,
                Result.class, HttpStatus.BAD_REQUEST);
        assertThat(exampleParticipationRepository.findBySubmissionsId(randomId)).isEmpty();
    }

    private ExampleParticipation importExampleParticipation(Long exerciseId, Long submissionId, HttpStatus expectedStatus) throws Exception {
        return request.postWithResponseBody("/api/assessment/exercises/" + exerciseId + "/example-participations/import/" + submissionId, null, ExampleParticipation.class,
                expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleParticipationWithTextSubmission() throws Exception {
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

        ExampleParticipation exampleParticipation = importExampleParticipation(textExercise.getId(), submission.getId(), HttpStatus.OK);
        List<TextBlock> copiedTextBlocks = new ArrayList<>(((TextSubmission) exampleParticipation.getSubmission()).getBlocks());
        assertThat(exampleParticipation.getId()).isNotNull();
        assertThat(((TextSubmission) exampleParticipation.getSubmission()).getText()).isEqualTo(submission.getText());
        assertThat(exampleParticipation.getSubmission().getLatestResult().getFeedbacks()).isNotEmpty();
        assertThat(exampleParticipation.getSubmission().getLatestResult().getFeedbacks().iterator().next().getCredits()).isEqualTo(feedback.getCredits());
        assertThat(copiedTextBlocks).isNotEmpty();
        assertThat(copiedTextBlocks.getFirst().getText()).isEqualTo(textBlock.getText());
        assertThat(exampleParticipation.getSubmission().getLatestResult().getFeedbacks().iterator().next().getReference()).isEqualTo(copiedTextBlocks.getFirst().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleParticipationWithModelingSubmission() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(modelingExercise, submission, TEST_PREFIX + "student1");
        participationUtilService.addResultToSubmission(submission, AssessmentType.MANUAL, modelingExercise.getId());

        ExampleParticipation exampleParticipation = importExampleParticipation(modelingExercise.getId(), submission.getId(), HttpStatus.OK);
        assertThat(exampleParticipation.getId()).isNotNull();
        assertThat(((ModelingSubmission) exampleParticipation.getSubmission()).getModel()).isEqualTo(submission.getModel());
        assertThat(exampleParticipation.getSubmission().getLatestResult().getScore()).isEqualTo(submission.getLatestResult().getScore());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleParticipationForModelingExerciseCopiesGradingInstruction() throws Exception {
        testGradingCriteriaAreImported(modelingExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleParticipationForTextExerciseCopiesGradingInstruction() throws Exception {
        testGradingCriteriaAreImported(textExercise);
    }

    private void testGradingCriteriaAreImported(Exercise exercise) throws Exception {
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(exercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        var studentParticipation = participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(exercise, TEST_PREFIX + "instructor1");
        Submission originalSubmission = studentParticipation.findLatestSubmission().orElseThrow();
        Optional<Result> orginalResult = resultRepository.findDistinctWithFeedbackBySubmissionId(originalSubmission.getId());

        ExampleParticipation exampleParticipation = importExampleParticipation(exercise.getId(), originalSubmission.getId(), HttpStatus.OK);
        assertThat(exampleParticipation.getSubmission().getResults().iterator().next().getFeedbacks().iterator().next().getGradingInstruction().getId())
                .isEqualTo(orginalResult.orElseThrow().getFeedbacks().iterator().next().getGradingInstruction().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleParticipationWithStudentSubmission_wrongExerciseId() throws Exception {
        Submission submission = new TextSubmission();
        submission.setId(12345L);
        Long randomId = 1233L;
        importExampleParticipation(randomId, submission.getId(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleParticipationWithStudentSubmission_isNotAtLeastInstructorInExercise_forbidden() throws Exception {
        Submission submission = new TextSubmission();
        submission.setId(12345L);
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        importExampleParticipation(textExercise.getId(), submission.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleParticipationWithTextSubmission_exerciseIdNotMatched() throws Exception {
        TextSubmission submission = ParticipationFactory.generateTextSubmission("submissionText", Language.ENGLISH, true);
        submission = textExerciseUtilService.saveTextSubmission(textExercise, submission, TEST_PREFIX + "student1");

        Exercise textExerciseToBeConflicted = new TextExercise();
        textExerciseToBeConflicted.setCourse(course);
        Exercise exercise = exerciseRepository.save(textExerciseToBeConflicted);

        importExampleParticipation(exercise.getId(), submission.getId(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleParticipationWithModelingSubmission_exerciseIdNotMatched() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(modelingExercise, submission, TEST_PREFIX + "student1");

        Exercise modelingExerciseToBeConflicted = new ModelingExercise();
        modelingExerciseToBeConflicted.setCourse(course);
        Exercise exercise = exerciseRepository.save(modelingExerciseToBeConflicted);

        importExampleParticipation(exercise.getId(), submission.getId(), HttpStatus.BAD_REQUEST);

    }

}
