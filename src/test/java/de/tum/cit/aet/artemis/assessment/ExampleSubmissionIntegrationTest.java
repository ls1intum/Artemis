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

import de.tum.cit.aet.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.modeling.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.exercise.text.TextExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.participation.ParticipationFactory;
import de.tum.cit.aet.artemis.participation.ParticipationUtilService;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.util.TestResourceUtils;
import de.tum.cit.aet.artemis.text.dto.TextAssessmentDTO;

class ExampleSubmissionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final Logger log = LoggerFactory.getLogger(ExampleSubmissionIntegrationTest.class);

    private static final String TEST_PREFIX = "examplesubmissionintegration";

    @Autowired
    private GradingCriterionRepository gradingCriterionRepo;

    @Autowired
    private ExampleSubmissionRepository exampleSubmissionRepo;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

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
        course = courseUtilService.addCourseWithModelingAndTextExercise();
        modelingExercise = exerciseUtilService.getFirstExerciseWithType(course, ModelingExercise.class);
        textExercise = exerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        emptyModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/empty-class-diagram.json");
        validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        log.debug("Test setup done");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAndUpdateExampleModelingSubmissionTutorial(boolean usedForTutorial) throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission(emptyModel, modelingExercise, false, usedForTutorial);
        ExampleSubmission returnedExampleSubmission = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission,
                ExampleSubmission.class, HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedExampleSubmission.getSubmission().getId(), emptyModel);
        Optional<ExampleSubmission> storedExampleSubmission = exampleSubmissionRepo.findBySubmissionId(returnedExampleSubmission.getSubmission().getId());
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        exampleSubmission = participationUtilService.generateExampleSubmission(validModel, modelingExercise, false);
        returnedExampleSubmission = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission, ExampleSubmission.class,
                HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedExampleSubmission.getSubmission().getId(), validModel);
        storedExampleSubmission = exampleSubmissionRepo.findBySubmissionId(returnedExampleSubmission.getSubmission().getId());
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateExampleModelingSubmission(boolean usedForTutorial) throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission(emptyModel, modelingExercise, false, usedForTutorial);
        ExampleSubmission returnedExampleSubmission = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission,
                ExampleSubmission.class, HttpStatus.OK);
        ExampleSubmission updateExistingExampleSubmission = request.putWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions",
                returnedExampleSubmission, ExampleSubmission.class, HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(updateExistingExampleSubmission.getSubmission().getId(), emptyModel);
        Optional<ExampleSubmission> storedExampleSubmission = exampleSubmissionRepo.findBySubmissionId(updateExistingExampleSubmission.getSubmission().getId());
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        ExampleSubmission updatedExampleSubmission = participationUtilService.generateExampleSubmission(validModel, modelingExercise, false);
        ExampleSubmission returnedUpdatedExampleSubmission = request.putWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions",
                updatedExampleSubmission, ExampleSubmission.class, HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedUpdatedExampleSubmission.getSubmission().getId(), validModel);
        storedExampleSubmission = exampleSubmissionRepo.findBySubmissionId(returnedUpdatedExampleSubmission.getSubmission().getId());
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAndDeleteExampleModelingSubmission(boolean usedForTutorial) throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission(validModel, modelingExercise, false, usedForTutorial);
        ExampleSubmission returnedExampleSubmission = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission,
                ExampleSubmission.class, HttpStatus.OK);
        Long submissionId = returnedExampleSubmission.getSubmission().getId();

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(submissionId, validModel);
        Optional<ExampleSubmission> storedExampleSubmission = exampleSubmissionRepo.findBySubmissionId(submissionId);
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        request.delete("/api/example-submissions/" + storedExampleSubmission.get().getId(), HttpStatus.OK);
        assertThat(exampleSubmissionRepo.findAllByExerciseId(modelingExercise.getId())).isEmpty();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAndDeleteExampleModelingSubmissionWithResult(boolean usedForTutorial) throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission(validModel, modelingExercise, false, usedForTutorial);
        exampleSubmission.addTutorParticipations(new TutorParticipation());
        ExampleSubmission returnedExampleSubmission = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission,
                ExampleSubmission.class, HttpStatus.OK);
        Long submissionId = returnedExampleSubmission.getSubmission().getId();

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(submissionId, validModel);
        Optional<ExampleSubmission> storedExampleSubmission = exampleSubmissionRepo.findBySubmissionId(submissionId);
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.orElseThrow().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        request.delete("/api/example-submissions/" + storedExampleSubmission.get().getId(), HttpStatus.OK);
        assertThat(exampleSubmissionRepo.findAllByExerciseId(modelingExercise.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createExampleModelingSubmission_asTutor_forbidden() throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission(emptyModel, modelingExercise, true);
        request.post("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void createExampleModelingSubmission_asStudent_forbidden() throws Exception {
        exampleSubmission = participationUtilService.generateExampleSubmission(emptyModel, modelingExercise, true);
        request.post("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExampleModelingSubmission() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission(validModel, modelingExercise, true));
        exampleSubmission = request.get("/api/example-submissions/" + storedExampleSubmission.getId(), HttpStatus.OK, ExampleSubmission.class);
        modelingExerciseUtilService.checkModelsAreEqual(((ModelingSubmission) exampleSubmission.getSubmission()).getModel(), validModel);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getExampleModelingSubmission_asStudent_forbidden() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission(validModel, modelingExercise, true));
        request.get("/api/example-submissions/" + storedExampleSubmission.getId(), HttpStatus.FORBIDDEN, ExampleSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createExampleModelingAssessment() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission(validModel, modelingExercise, true));
        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        request.putWithResponseBody("/api/modeling-submissions/" + storedExampleSubmission.getId() + "/example-assessment", feedbacks, Result.class, HttpStatus.OK);

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
        request.putWithResponseBody("/api/modeling-submissions/" + exampleSubmission.getId() + "/example-assessment", feedbacks, Result.class, HttpStatus.OK);

        Result cleanResult = request.get("/api/exercise/" + modelingExercise.getId() + "/modeling-submissions/" + exampleSubmission.getSubmission().getId() + "/example-assessment",
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
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission("Text. Submission.", textExercise, true));

        ExampleSubmission unpreparedExampleSubmission = request.get("/api/example-submissions/" + storedExampleSubmission.getId(), HttpStatus.OK, ExampleSubmission.class);
        TextSubmission unpreparedTextSubmission = (TextSubmission) unpreparedExampleSubmission.getSubmission();
        assertThat(unpreparedTextSubmission.getBlocks()).hasSize(0);

        request.postWithoutResponseBody("/api/exercises/" + textExercise.getId() + "/example-submissions/" + storedExampleSubmission.getId() + "/prepare-assessment", HttpStatus.OK,
                new LinkedMultiValueMap<>());
        ExampleSubmission preparedExampleSubmission = request.get("/api/example-submissions/" + storedExampleSubmission.getId(), HttpStatus.OK, ExampleSubmission.class);
        TextSubmission preparedTextSubmission = (TextSubmission) preparedExampleSubmission.getSubmission();
        assertThat(preparedTextSubmission.getBlocks()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExampleTextAssessment() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission("Text. Submission.", textExercise, true));
        participationUtilService.addResultToSubmission(storedExampleSubmission.getSubmission(), AssessmentType.MANUAL);
        final Result exampleResult = request.get("/api/exercises/" + textExercise.getId() + "/submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-result",
                HttpStatus.OK, Result.class);
        final Set<TextBlock> blocks = ((TextSubmission) exampleResult.getSubmission()).getBlocks();
        assertThat(blocks).hasSize(2);
        List<Feedback> feedbacks = new ArrayList<>();
        final Iterator<TextBlock> textBlockIterator = blocks.iterator();
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL).detailText("nice submission 1").reference(textBlockIterator.next().getId()));
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL).detailText("nice submission 2").reference(textBlockIterator.next().getId()));
        var dto = new TextAssessmentDTO();
        dto.setFeedbacks(feedbacks);
        request.putWithResponseBody("/api/exercises/" + textExercise.getId() + "/example-submissions/" + storedExampleSubmission.getId() + "/example-text-assessment", dto,
                Result.class, HttpStatus.OK);
        Result storedResult = resultRepository.findDistinctWithFeedbackBySubmissionId(storedExampleSubmission.getSubmission().getId()).orElseThrow();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExampleTextAssessmentNotExistentId() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission("Text. Submission.", textExercise, true));
        participationUtilService.addResultToSubmission(storedExampleSubmission.getSubmission(), AssessmentType.MANUAL);
        final Result exampleResult = request.get("/api/exercises/" + textExercise.getId() + "/submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-result",
                HttpStatus.OK, Result.class);
        final Set<TextBlock> blocks = ((TextSubmission) exampleResult.getSubmission()).getBlocks();
        assertThat(blocks).hasSize(2);
        List<Feedback> feedbacks = ParticipationFactory.generateManualFeedback();
        var dto = new TextAssessmentDTO();
        dto.setFeedbacks(feedbacks);
        long randomId = 1233;
        request.putWithResponseBody("/api/exercises/" + textExercise.getId() + "/example-submissions/" + randomId + "/example-text-assessment", dto, Result.class,
                HttpStatus.NOT_FOUND);
        assertThat(exampleSubmissionRepo.findBySubmissionId(randomId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createExampleTextAssessment_wrongExerciseId() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission("Text. Submission.", textExercise, true));
        participationUtilService.addResultToSubmission(storedExampleSubmission.getSubmission(), AssessmentType.MANUAL);
        final Result exampleResult = request.get("/api/exercises/" + textExercise.getId() + "/submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-result",
                HttpStatus.OK, Result.class);
        final Set<TextBlock> blocks = ((TextSubmission) exampleResult.getSubmission()).getBlocks();
        assertThat(blocks).hasSize(2);
        List<Feedback> feedbacks = ParticipationFactory.generateManualFeedback();
        var dto = new TextAssessmentDTO();
        dto.setFeedbacks(feedbacks);
        long randomId = 1233;
        request.putWithResponseBody("/api/exercises/" + randomId + "/example-submissions/" + storedExampleSubmission.getId() + "/example-text-assessment", dto, Result.class,
                HttpStatus.BAD_REQUEST);
        assertThat(exampleSubmissionRepo.findBySubmissionId(randomId)).isEmpty();
    }

    private ExampleSubmission importExampleSubmission(Long exerciseId, Long submissionId, HttpStatus expectedStatus) throws Exception {
        return request.postWithResponseBody("/api/exercises/" + exerciseId + "/example-submissions/import/" + submissionId, null, ExampleSubmission.class, expectedStatus);
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

        participationUtilService.addResultToSubmission(submission, AssessmentType.MANUAL);

        // add one feedback for the created text block
        List<TextBlock> textBlocks = new ArrayList<>(submission.getBlocks());
        Feedback feedback = new Feedback();

        assertThat(textBlocks).isNotEmpty();

        feedback.setCredits(1.0);
        feedback.setReference(textBlocks.getFirst().getId());
        participationUtilService.addFeedbackToResult(feedback, submission.getLatestResult());

        ExampleSubmission exampleSubmission = importExampleSubmission(textExercise.getId(), submission.getId(), HttpStatus.OK);
        List<TextBlock> copiedTextBlocks = new ArrayList<>(((TextSubmission) exampleSubmission.getSubmission()).getBlocks());
        assertThat(exampleSubmission.getId()).isNotNull();
        assertThat(((TextSubmission) exampleSubmission.getSubmission()).getText()).isEqualTo(submission.getText());
        assertThat(exampleSubmission.getSubmission().getLatestResult().getFeedbacks()).isNotEmpty();
        assertThat(exampleSubmission.getSubmission().getLatestResult().getFeedbacks().getFirst().getCredits()).isEqualTo(feedback.getCredits());
        assertThat(copiedTextBlocks).isNotEmpty();
        assertThat(copiedTextBlocks.getFirst().getText()).isEqualTo(textBlock.getText());
        assertThat(exampleSubmission.getSubmission().getLatestResult().getFeedbacks().getFirst().getReference()).isEqualTo(copiedTextBlocks.getFirst().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExampleSubmissionWithModelingSubmission() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(modelingExercise, submission, TEST_PREFIX + "student1");
        participationUtilService.addResultToSubmission(submission, AssessmentType.MANUAL);

        ExampleSubmission exampleSubmission = importExampleSubmission(modelingExercise.getId(), submission.getId(), HttpStatus.OK);
        assertThat(exampleSubmission.getId()).isNotNull();
        assertThat(((ModelingSubmission) exampleSubmission.getSubmission()).getModel()).isEqualTo(submission.getModel());
        assertThat(exampleSubmission.getSubmission().getLatestResult().getScore()).isEqualTo(submission.getLatestResult().getScore());
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
        gradingCriterionRepo.saveAll(gradingCriteria);
        var studentParticipation = participationUtilService.addAssessmentWithFeedbackWithGradingInstructionsForExercise(exercise, TEST_PREFIX + "instructor1");
        Submission originalSubmission = studentParticipation.findLatestSubmission().orElseThrow();
        Optional<Result> orginalResult = resultRepository.findDistinctWithFeedbackBySubmissionId(originalSubmission.getId());

        ExampleSubmission exampleSubmission = importExampleSubmission(exercise.getId(), originalSubmission.getId(), HttpStatus.OK);
        assertThat(exampleSubmission.getSubmission().getResults().getFirst().getFeedbacks().getFirst().getGradingInstruction().getId())
                .isEqualTo(orginalResult.orElseThrow().getFeedbacks().getFirst().getGradingInstruction().getId());
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
        course.setInstructorGroupName("test");
        courseRepository.save(course);
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
