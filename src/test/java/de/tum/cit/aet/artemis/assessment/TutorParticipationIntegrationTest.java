package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingInstructionRepository;
import de.tum.cit.aet.artemis.assessment.service.ExampleSubmissionService;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.assessment.service.TutorParticipationService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseFactory;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus;

class TutorParticipationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "tutorparticipationintegration";

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private ExampleSubmissionService exampleSubmissionService;

    @Autowired
    private TutorParticipationService tutorParticipationService;

    @Autowired
    private GradingInstructionRepository gradingInstructionRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ResultService resultService;

    @Autowired
    private SubmissionRepository submissionRepository;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private List<String> textBlockIds;

    private String path;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 0, 1, 0, 0);
        Course course = courseUtilService.addCourseWithModelingAndTextExercise();
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
            exerciseRepository.save(exercise);
            path = "/api/exercises/" + exercise.getId() + "/assess-example-submission";
        }

        textBlockIds = new ArrayList<>();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTutorParticipateInModelingExerciseWithExampleSubmission(boolean usedForTutorial) throws Exception {
        ExampleSubmission exampleSubmission = prepareModelingExampleSubmission(usedForTutorial);
        var tutorParticipation = request.postWithResponseBody(path, exampleSubmission, TutorParticipation.class, HttpStatus.OK);
        assertThat(tutorParticipation.getTrainedExampleSubmissions()).as("Tutor participation has example submission").hasSize(1);
        assertThat(tutorParticipation.getTutor().getLogin()).as("Tutor participation belongs to correct tutor").isEqualTo(TEST_PREFIX + "tutor1");
        assertThat(tutorParticipation.getAssessedExercise()).as("Tutor participation belongs to correct exercise").isEqualTo(modelingExercise);
        assertThat(tutorParticipation.getStatus()).as("Tutor participation has correct status").isEqualTo(TutorParticipationStatus.TRAINED);
    }

    /**
     * Tests the tutor training with example submission in Text exercises.
     * In case tutor has provided a feedback which was not provided by the instructor, response is BAD_REQUEST.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTutorParticipateInTextExerciseWithExampleSubmissionAddingUnnecessaryFeedbackBadRequest() throws Exception {
        ExampleSubmission exampleSubmission = prepareTextExampleSubmission(true);

        // Tutor reviewed the instructions.
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        var tutorParticipation = tutorParticipationService.createNewParticipation(textExercise, tutor);
        exampleSubmission.addTutorParticipations(tutorParticipation);
        exampleSubmission = exampleSubmissionService.save(exampleSubmission);

        Submission submissionWithResults = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(exampleSubmission.getSubmission().getId());
        submissionWithResults.getLatestResult().addFeedback(ParticipationFactory.createManualTextFeedback(1D, textBlockIds.get(1)));

        var path = "/api/exercises/" + textExercise.getId() + "/assess-example-submission";
        request.postWithResponseBody(path, exampleSubmission, TutorParticipation.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tests when tutor provides unnecessary unreferenced feedback in text example assessment, bad request exception is thrown
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTutorParticipateInTextExerciseWithExampleSubmissionAddingUnnecessaryUnreferencedFeedbackBadRequest() throws Exception {
        ExampleSubmission exampleSubmission = prepareTextExampleSubmission(true);

        // Tutor reviewed the instructions.
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        var tutorParticipation = tutorParticipationService.createNewParticipation(textExercise, tutor);
        exampleSubmission.addTutorParticipations(tutorParticipation);
        exampleSubmission = exampleSubmissionService.save(exampleSubmission);

        Submission submissionWithResults = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(exampleSubmission.getSubmission().getId());
        submissionWithResults.getLatestResult().addFeedback(ParticipationFactory.createPositiveFeedback(FeedbackType.MANUAL_UNREFERENCED));

        var path = "/api/exercises/" + textExercise.getId() + "/assess-example-submission";
        request.postWithResponseBody(path, exampleSubmission, TutorParticipation.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tests the tutor training with example submission in Modelling exercises.
     * In case tutor has provided a feedback which was not provided by the instructor, response is BAD_REQUEST.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTutorParticipateInModelingExerciseWithExampleSubmissionAddingUnnecessaryFeedbackBadRequest() throws Exception {
        ExampleSubmission exampleSubmission = prepareModelingExampleSubmission(true);

        // Tutor reviewed the instructions.
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        var tutorParticipation = new TutorParticipation().tutor(tutor).status(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        tutorParticipationService.createNewParticipation(textExercise, tutor);
        exampleSubmission.addTutorParticipations(tutorParticipation);
        exampleSubmissionService.save(exampleSubmission);

        exampleSubmission.getSubmission().getLatestResult().addFeedback(ParticipationFactory.createManualTextFeedback(1D, "6aba5764-d102-4740-9675-b2bd0a4f2680"));
        var path = "/api/exercises/" + textExercise.getId() + "/assess-example-submission";
        request.postWithResponseBody(path, exampleSubmission, TutorParticipation.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tests when tutor provides unnecessry unreferenced feedback in modeling example assessment, bad request exception is thrown
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTutorParticipateInModelingExerciseWithExampleSubmissionAddingUnnecessaryUnreferencedFeedbackBadRequest() throws Exception {
        ExampleSubmission exampleSubmission = prepareModelingExampleSubmission(true);

        // Tutor reviewed the instructions.
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        var tutorParticipation = new TutorParticipation().tutor(tutor).status(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        tutorParticipationService.createNewParticipation(textExercise, tutor);
        exampleSubmission.addTutorParticipations(tutorParticipation);
        exampleSubmissionService.save(exampleSubmission);

        exampleSubmission.getSubmission().getLatestResult().addFeedback(ParticipationFactory.createPositiveFeedback(FeedbackType.MANUAL_UNREFERENCED));
        var path = "/api/exercises/" + textExercise.getId() + "/assess-example-submission";
        request.postWithResponseBody(path, exampleSubmission, TutorParticipation.class, HttpStatus.BAD_REQUEST);
    }

    @NotNull
    private ExampleSubmission prepareTextExampleSubmission(boolean usedForTutorial) throws Exception {
        var exampleSubmissionText = "This is first sentence:This is second sentence.";
        ExampleSubmission exampleSubmission = participationUtilService.generateExampleSubmission(exampleSubmissionText, textExercise, false, usedForTutorial);
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

        exampleSubmission = exampleSubmissionService.save(exampleSubmission);

        if (usedForTutorial) {
            var result = submissionService.saveNewEmptyResult(exampleSubmission.getSubmission());
            result.setExampleResult(true);

            var feedback = ParticipationFactory.createManualTextFeedback(1D, textBlockIds.getFirst());
            var gradingCriterion = ExerciseFactory.generateGradingCriterion("criterion");
            gradingCriterion = gradingCriterionRepository.save(gradingCriterion);

            var instructions = ExerciseFactory.generateGradingInstructions(gradingCriterion, 1, 1);
            instructions = new HashSet<>(gradingInstructionRepository.saveAll(instructions));
            instructions.forEach(feedback::setGradingInstruction);
            resultService.addFeedbackToResult(result, List.of(feedback), true);
        }

        request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/tutor-participations", null, TutorParticipation.class, HttpStatus.CREATED);
        return exampleSubmission;
    }

    @NotNull
    private ExampleSubmission prepareModelingExampleSubmission(boolean usedForTutorial) throws Exception {
        String validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        ExampleSubmission exampleSubmission = participationUtilService.generateExampleSubmission(validModel, modelingExercise, false, usedForTutorial);
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
