package de.tum.in.www1.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.GradingCriterionRepository;
import de.tum.in.www1.artemis.repository.GradingInstructionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.ExampleSubmissionService;
import de.tum.in.www1.artemis.service.SubmissionService;
import de.tum.in.www1.artemis.service.TutorParticipationService;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ModelFactory;

class TutorParticipationIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private ExampleSubmissionService exampleSubmissionService;

    @Autowired
    private TutorParticipationService tutorParticipationService;

    @Autowired
    private GradingInstructionRepository gradingInstructionRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private List<String> textBlockIds;

    private String path;

    @BeforeEach
    void initTestCase() throws Exception {
        database.addUsers(0, 1, 0, 0);
        Course course = database.addCourseWithModelingAndTextExercise();
        for (Exercise exercise : course.getExercises()) {
            if (exercise instanceof ModelingExercise) {
                modelingExercise = (ModelingExercise) exercise;
            }
            if (exercise instanceof TextExercise) {
                textExercise = (TextExercise) exercise;
            }
        }

        for (Exercise exercise : new Exercise[] { textExercise, modelingExercise }) {
            exercise.setTitle("exercise name");
            exerciseRepo.save(exercise);
            path = "/api/exercises/" + exercise.getId() + "/assess-example-submission";
        }

        textBlockIds = new ArrayList<>();
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "tutor1", roles = "TA")
    void testTutorParticipateInModelingExerciseWithExampleSubmission(boolean usedForTutorial) throws Exception {
        ExampleSubmission exampleSubmission = prepareModelingExampleSubmission(usedForTutorial);
        var tutorParticipation = request.postWithResponseBody(path, exampleSubmission, TutorParticipation.class, HttpStatus.OK);
        assertThat(tutorParticipation.getTrainedExampleSubmissions()).as("Tutor participation has example submission").hasSize(1);
        assertThat(tutorParticipation.getTutor().getLogin()).as("Tutor participation belongs to correct tutor").isEqualTo("tutor1");
        assertThat(tutorParticipation.getAssessedExercise()).as("Tutor participation belongs to correct exercise").isEqualTo(modelingExercise);
        assertThat(tutorParticipation.getStatus()).as("Tutor participation has correct status").isEqualTo(TutorParticipationStatus.TRAINED);
    }

    /**
     * Tests the tutor training with example submission in Text exercises.
     * In case tutor has provided a feedback which was not provided by the instructor, response is BAD_REQUEST.
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testTutorParticipateInTextExerciseWithExampleSubmissionAddingUnnecessaryFeedbackBadRequest() throws Exception {
        ExampleSubmission exampleSubmission = prepareTextExampleSubmission(true);

        // Tutor reviewed the instructions.
        var tutor = database.getUserByLogin("tutor1");
        var tutorParticipation = new TutorParticipation().tutor(tutor).status(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        tutorParticipationService.createNewParticipation(textExercise, tutor);
        exampleSubmission.addTutorParticipations(tutorParticipation);
        exampleSubmissionService.save(exampleSubmission);

        exampleSubmission.getSubmission().getLatestResult().addFeedback(ModelFactory.createManualTextFeedback(1D, textBlockIds.get(1)));
        var path = "/api/exercises/" + textExercise.getId() + "/assess-example-submission";
        request.postWithResponseBody(path, exampleSubmission, TutorParticipation.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tests when tutor provides unnecessry unreferenced feedback in text example assessment, bad request exception is thrown
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testTutorParticipateInTextExerciseWithExampleSubmissionAddingUnnecessaryUnreferencedFeedbackBadRequest() throws Exception {
        ExampleSubmission exampleSubmission = prepareTextExampleSubmission(true);

        // Tutor reviewed the instructions.
        var tutor = database.getUserByLogin("tutor1");
        var tutorParticipation = new TutorParticipation().tutor(tutor).status(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        tutorParticipationService.createNewParticipation(textExercise, tutor);
        exampleSubmission.addTutorParticipations(tutorParticipation);
        exampleSubmissionService.save(exampleSubmission);

        exampleSubmission.getSubmission().getLatestResult().addFeedback(ModelFactory.createPositiveFeedback(FeedbackType.MANUAL_UNREFERENCED));
        var path = "/api/exercises/" + textExercise.getId() + "/assess-example-submission";
        request.postWithResponseBody(path, exampleSubmission, TutorParticipation.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tests the tutor training with example submission in Modelling exercises.
     * In case tutor has provided a feedback which was not provided by the instructor, response is BAD_REQUEST.
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testTutorParticipateInModelingExerciseWithExampleSubmissionAddingUnnecessaryFeedbackBadRequest() throws Exception {
        ExampleSubmission exampleSubmission = prepareModelingExampleSubmission(true);

        // Tutor reviewed the instructions.
        var tutor = database.getUserByLogin("tutor1");
        var tutorParticipation = new TutorParticipation().tutor(tutor).status(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        tutorParticipationService.createNewParticipation(textExercise, tutor);
        exampleSubmission.addTutorParticipations(tutorParticipation);
        exampleSubmissionService.save(exampleSubmission);

        exampleSubmission.getSubmission().getLatestResult().addFeedback(ModelFactory.createManualTextFeedback(1D, "6aba5764-d102-4740-9675-b2bd0a4f2680"));
        var path = "/api/exercises/" + textExercise.getId() + "/assess-example-submission";
        request.postWithResponseBody(path, exampleSubmission, TutorParticipation.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tests when tutor provides unnecessry unreferenced feedback in modeling example assessment, bad request exception is thrown
     */
    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testTutorParticipateInModelingExerciseWithExampleSubmissionAddingUnnecessaryUnreferencedFeedbackBadRequest() throws Exception {
        ExampleSubmission exampleSubmission = prepareModelingExampleSubmission(true);

        // Tutor reviewed the instructions.
        var tutor = database.getUserByLogin("tutor1");
        var tutorParticipation = new TutorParticipation().tutor(tutor).status(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        tutorParticipationService.createNewParticipation(textExercise, tutor);
        exampleSubmission.addTutorParticipations(tutorParticipation);
        exampleSubmissionService.save(exampleSubmission);

        exampleSubmission.getSubmission().getLatestResult().addFeedback(ModelFactory.createPositiveFeedback(FeedbackType.MANUAL_UNREFERENCED));
        var path = "/api/exercises/" + textExercise.getId() + "/assess-example-submission";
        request.postWithResponseBody(path, exampleSubmission, TutorParticipation.class, HttpStatus.BAD_REQUEST);
    }

    @NotNull
    private ExampleSubmission prepareTextExampleSubmission(boolean usedForTutorial) throws Exception {
        var exampleSubmissionText = "This is first sentence:This is second sentence.";
        ExampleSubmission exampleSubmission = database.generateExampleSubmission(exampleSubmissionText, textExercise, false, usedForTutorial);
        TextSubmission textSubmission = (TextSubmission) exampleSubmission.getSubmission();

        for (var sentence : exampleSubmissionText.split(":")) {
            var block = new TextBlock();
            var startIndex = exampleSubmissionText.indexOf(sentence);

            var textBlock = new TextBlock().text(sentence).startIndex(startIndex).endIndex(startIndex + sentence.length()).submission(textSubmission).manual();
            textBlock.computeId();
            textSubmission.addBlock(block);

            // Store the id in textBlockIds for later access.
            textBlockIds.add(textBlock.getId());
        }

        exampleSubmissionService.save(exampleSubmission);

        if (usedForTutorial) {
            var result = submissionService.saveNewEmptyResult(exampleSubmission.getSubmission());
            result.setExampleResult(true);

            var feedback = ModelFactory.createManualTextFeedback(1D, textBlockIds.get(0));
            var gradingCriterion = ModelFactory.generateGradingCriterion("criterion");
            gradingCriterionRepository.save(gradingCriterion);

            var instructions = ModelFactory.generateGradingInstructions(gradingCriterion, 1, 1);
            gradingInstructionRepository.saveAll(instructions);
            instructions.forEach(feedback::setGradingInstruction);
            result.addFeedback(feedback);
            resultRepository.save(result);
        }

        request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/tutor-participations", null, TutorParticipation.class, HttpStatus.CREATED);
        return exampleSubmission;
    }

    @NotNull
    private ExampleSubmission prepareModelingExampleSubmission(boolean usedForTutorial) throws Exception {
        String validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        ExampleSubmission exampleSubmission = database.generateExampleSubmission(validModel, modelingExercise, false, usedForTutorial);
        exampleSubmissionService.save(exampleSubmission);
        if (usedForTutorial) {
            var result = submissionService.saveNewEmptyResult(exampleSubmission.getSubmission());
            result.setExampleResult(true);
            resultRepository.save(result);
        }
        request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/tutor-participations", null, TutorParticipation.class, HttpStatus.CREATED);
        return exampleSubmission;
    }
}
