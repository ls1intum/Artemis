package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.TextAssessmentDTO;

public class ExampleSubmissionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private ExampleSubmissionRepository exampleSubmissionRepo;

    @Autowired
    private CourseRepository courseRepository;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private ExampleSubmission exampleSubmission;

    private Course course;

    private String emptyModel;

    private String validModel;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(1, 1, 0, 1);
        course = database.addCourseWithModelingAndTextExercise();
        modelingExercise = (ModelingExercise) course.getExercises().stream().filter(exercise -> exercise instanceof ModelingExercise).findFirst().get();
        textExercise = (TextExercise) course.getExercises().stream().filter(exercise -> exercise instanceof TextExercise).findFirst().get();
        emptyModel = FileUtils.loadFileFromResources("test-data/model-submission/empty-class-diagram.json");
        validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createAndUpdateExampleModelingSubmissionTutorial(boolean usedForTutorial) throws Exception {
        exampleSubmission = database.generateExampleSubmission(emptyModel, modelingExercise, false, usedForTutorial);
        ExampleSubmission returnedExampleSubmission = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission,
                ExampleSubmission.class, HttpStatus.OK);

        database.checkModelingSubmissionCorrectlyStored(returnedExampleSubmission.getSubmission().getId(), emptyModel);
        Optional<ExampleSubmission> storedExampleSubmission = exampleSubmissionRepo.findBySubmissionId(returnedExampleSubmission.getSubmission().getId());
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.get().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        exampleSubmission = database.generateExampleSubmission(validModel, modelingExercise, false);
        returnedExampleSubmission = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission, ExampleSubmission.class,
                HttpStatus.OK);

        database.checkModelingSubmissionCorrectlyStored(returnedExampleSubmission.getSubmission().getId(), validModel);
        storedExampleSubmission = exampleSubmissionRepo.findBySubmissionId(returnedExampleSubmission.getSubmission().getId());
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.get().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateExampleModelingSubmission(boolean usedForTutorial) throws Exception {
        exampleSubmission = database.generateExampleSubmission(emptyModel, modelingExercise, false, usedForTutorial);
        ExampleSubmission returnedExampleSubmission = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission,
                ExampleSubmission.class, HttpStatus.OK);
        ExampleSubmission updateExistingExampleSubmission = request.putWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions",
                returnedExampleSubmission, ExampleSubmission.class, HttpStatus.OK);

        database.checkModelingSubmissionCorrectlyStored(updateExistingExampleSubmission.getSubmission().getId(), emptyModel);
        Optional<ExampleSubmission> storedExampleSubmission = exampleSubmissionRepo.findBySubmissionId(updateExistingExampleSubmission.getSubmission().getId());
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.get().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        ExampleSubmission updatedExampleSubmission = database.generateExampleSubmission(validModel, modelingExercise, false);
        ExampleSubmission returnedUpdatedExampleSubmission = request.putWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions",
                updatedExampleSubmission, ExampleSubmission.class, HttpStatus.OK);

        database.checkModelingSubmissionCorrectlyStored(returnedUpdatedExampleSubmission.getSubmission().getId(), validModel);
        storedExampleSubmission = exampleSubmissionRepo.findBySubmissionId(returnedUpdatedExampleSubmission.getSubmission().getId());
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.get().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createAndDeleteExampleModelingSubmission(boolean usedForTutorial) throws Exception {
        exampleSubmission = database.generateExampleSubmission(validModel, modelingExercise, false, usedForTutorial);
        ExampleSubmission returnedExampleSubmission = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission,
                ExampleSubmission.class, HttpStatus.OK);
        Long submissionId = returnedExampleSubmission.getSubmission().getId();

        database.checkModelingSubmissionCorrectlyStored(submissionId, validModel);
        Optional<ExampleSubmission> storedExampleSubmission = exampleSubmissionRepo.findBySubmissionId(submissionId);
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.get().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        request.delete("/api/example-submissions/" + storedExampleSubmission.get().getId(), HttpStatus.OK);
        assertThat(exampleSubmissionRepo.findAllByExerciseId(submissionId)).hasSize(0);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createAndDeleteExampleModelingSubmissionWithResult(boolean usedForTutorial) throws Exception {
        exampleSubmission = database.generateExampleSubmission(validModel, modelingExercise, false, usedForTutorial);
        exampleSubmission.addTutorParticipations(new TutorParticipation());
        ExampleSubmission returnedExampleSubmission = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission,
                ExampleSubmission.class, HttpStatus.OK);
        Long submissionId = returnedExampleSubmission.getSubmission().getId();

        database.checkModelingSubmissionCorrectlyStored(submissionId, validModel);
        Optional<ExampleSubmission> storedExampleSubmission = exampleSubmissionRepo.findBySubmissionId(submissionId);
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.get().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        request.delete("/api/example-submissions/" + storedExampleSubmission.get().getId(), HttpStatus.OK);
        assertThat(exampleSubmissionRepo.findAllByExerciseId(submissionId)).hasSize(0);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createExampleModelingSubmission_asTutor_forbidden() throws Exception {
        exampleSubmission = database.generateExampleSubmission(emptyModel, modelingExercise, true);
        request.post("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "student1")
    public void createExampleModelingSubmission_asStudent_forbidden() throws Exception {
        exampleSubmission = database.generateExampleSubmission(emptyModel, modelingExercise, true);
        request.post("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getExampleModelingSubmission() throws Exception {
        ExampleSubmission storedExampleSubmission = database.addExampleSubmission(database.generateExampleSubmission(validModel, modelingExercise, true));
        exampleSubmission = request.get("/api/example-submissions/" + storedExampleSubmission.getId(), HttpStatus.OK, ExampleSubmission.class);
        database.checkModelsAreEqual(((ModelingSubmission) exampleSubmission.getSubmission()).getModel(), validModel);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getExampleModelingSubmission_asStudent_forbidden() throws Exception {
        ExampleSubmission storedExampleSubmission = database.addExampleSubmission(database.generateExampleSubmission(validModel, modelingExercise, true));
        request.get("/api/example-submissions/" + storedExampleSubmission.getId(), HttpStatus.FORBIDDEN, ExampleSubmission.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createExampleModelingAssessment() throws Exception {
        ExampleSubmission storedExampleSubmission = database.addExampleSubmission(database.generateExampleSubmission(validModel, modelingExercise, true));
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        request.putWithResponseBody("/api/modeling-submissions/" + storedExampleSubmission.getId() + "/example-assessment", feedbacks, Result.class, HttpStatus.OK);

        Result storedResult = resultRepo.findDistinctWithFeedbackBySubmissionId(storedExampleSubmission.getSubmission().getId()).get();
        database.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createExampleTextAssessment() throws Exception {
        ExampleSubmission storedExampleSubmission = database.addExampleSubmission(database.generateExampleSubmission("Text. Submission.", textExercise, true));
        database.addResultToSubmission(storedExampleSubmission.getSubmission(), AssessmentType.MANUAL);
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
        Result storedResult = resultRepo.findDistinctWithFeedbackBySubmissionId(storedExampleSubmission.getSubmission().getId()).get();
        database.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createExampleTextAssessmentNotExistentId() throws Exception {
        ExampleSubmission storedExampleSubmission = database.addExampleSubmission(database.generateExampleSubmission("Text. Submission.", textExercise, true));
        database.addResultToSubmission(storedExampleSubmission.getSubmission(), AssessmentType.MANUAL);
        final Result exampleResult = request.get("/api/exercises/" + textExercise.getId() + "/submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-result",
                HttpStatus.OK, Result.class);
        final Set<TextBlock> blocks = ((TextSubmission) exampleResult.getSubmission()).getBlocks();
        assertThat(blocks).hasSize(2);
        List<Feedback> feedbacks = ModelFactory.generateManualFeedback();
        var dto = new TextAssessmentDTO();
        dto.setFeedbacks(feedbacks);
        long randomId = 1233;
        request.putWithResponseBody("/api/exercises/" + textExercise.getId() + "/example-submissions/" + randomId + "/example-text-assessment", dto, Result.class,
                HttpStatus.NOT_FOUND);
        assertThat(exampleSubmissionRepo.findBySubmissionId(randomId)).isEmpty();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createExampleTextAssessment_wrongExerciseId() throws Exception {
        ExampleSubmission storedExampleSubmission = database.addExampleSubmission(database.generateExampleSubmission("Text. Submission.", textExercise, true));
        database.addResultToSubmission(storedExampleSubmission.getSubmission(), AssessmentType.MANUAL);
        final Result exampleResult = request.get("/api/exercises/" + textExercise.getId() + "/submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-result",
                HttpStatus.OK, Result.class);
        final Set<TextBlock> blocks = ((TextSubmission) exampleResult.getSubmission()).getBlocks();
        assertThat(blocks).hasSize(2);
        List<Feedback> feedbacks = ModelFactory.generateManualFeedback();
        var dto = new TextAssessmentDTO();
        dto.setFeedbacks(feedbacks);
        long randomId = 1233;
        request.putWithResponseBody("/api/exercises/" + randomId + "/example-submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-text-assessment", dto,
                Result.class, HttpStatus.BAD_REQUEST);
        assertThat(exampleSubmissionRepo.findBySubmissionId(randomId)).isEmpty();
    }

    private ExampleSubmission importExampleSubmission(HttpStatus expectedStatus, Submission submission, Long exerciseId) throws Exception {
        return request.postWithResponseBody("/api/exercises/" + exerciseId + "/example-submissions/import", submission, ExampleSubmission.class, expectedStatus);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void importExampleSubmissionWithTextSubmission() throws Exception {
        Submission submission = ModelFactory.generateTextSubmission("submissionText", Language.ENGLISH, true);
        submission = database.saveTextSubmission(textExercise, (TextSubmission) submission, "student1");

        TextBlock textBlock = new TextBlock();
        textBlock.setCluster(null);
        textBlock.setAddedDistance(0);
        textBlock.setStartIndex(0);
        textBlock.setEndIndex(14);
        database.addAndSaveTextBlocksToTextSubmission(Set.of(textBlock), (TextSubmission) submission);

        database.addResultToSubmission(submission, AssessmentType.MANUAL);

        // add one feedback for the created text block
        List<TextBlock> textBlocks = new ArrayList<TextBlock>(((TextSubmission) submission).getBlocks());
        Feedback feedback = new Feedback();
        if (!textBlocks.isEmpty()) {
            feedback.setCredits(1.0);
            feedback.setReference(textBlocks.get(0).getId());
        }

        database.addFeedbackToResult(feedback, submission.getLatestResult());

        ExampleSubmission exampleSubmission = importExampleSubmission(HttpStatus.OK, submission, textExercise.getId());
        List<TextBlock> copiedTextBlocks = new ArrayList<TextBlock>(((TextSubmission) exampleSubmission.getSubmission()).getBlocks());
        assertThat(exampleSubmission.getId()).isNotNull();
        assertThat(((TextSubmission) exampleSubmission.getSubmission()).getText()).isEqualTo(((TextSubmission) submission).getText());
        assertThat(exampleSubmission.getSubmission().getLatestResult().getFeedbacks().size()).isEqualTo(1);
        assertThat(copiedTextBlocks.size()).isEqualTo(1);
        assertThat(exampleSubmission.getSubmission().getLatestResult().getFeedbacks().get(0).getReference()).isEqualTo(copiedTextBlocks.get(0).getId());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void importExampleSubmissionWithModelingSubmission() throws Exception {
        Submission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(modelingExercise, (ModelingSubmission) submission, "student1");
        database.addResultToSubmission(submission, AssessmentType.MANUAL);

        ExampleSubmission exampleSubmission = importExampleSubmission(HttpStatus.OK, submission, modelingExercise.getId());
        assertThat(exampleSubmission.getId()).isNotNull();
        assertThat(exampleSubmission.getSubmission().getLatestResult().getScore()).isEqualTo(submission.getLatestResult().getScore());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void importExampleSubmissionWithStudentSubmission_badRequest() throws Exception {
        Submission submission = new TextSubmission();
        importExampleSubmission(HttpStatus.BAD_REQUEST, submission, null);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void importExampleSubmissionWithStudentSubmission_wrongExerciseId() throws Exception {
        Submission submission = new TextSubmission();
        submission.setId(12345L);
        Long randomId = 1233L;
        importExampleSubmission(HttpStatus.NOT_FOUND, submission, randomId);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void importExampleSubmissionWithStudentSubmission_isNotAtLeastInstructorInExercise_forbidden() throws Exception {
        Submission submission = new TextSubmission();
        submission.setId(12345L);
        course.setInstructorGroupName("test");
        courseRepository.save(course);
        importExampleSubmission(HttpStatus.FORBIDDEN, submission, textExercise.getId());
    }

}
