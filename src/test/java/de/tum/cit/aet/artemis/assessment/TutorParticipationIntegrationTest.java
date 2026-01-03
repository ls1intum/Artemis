package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.ExampleParticipation;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.dto.TutorParticipationDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingInstructionRepository;
import de.tum.cit.aet.artemis.assessment.service.ExampleParticipationService;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.assessment.service.TutorParticipationService;
import de.tum.cit.aet.artemis.assessment.test_repository.ExampleParticipationTestRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.TutorParticipationTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
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
    private ExampleParticipationService exampleParticipationService;

    @Autowired
    private ExampleParticipationTestRepository exampleParticipationRepository;

    @Autowired
    private TutorParticipationService tutorParticipationService;

    @Autowired
    private GradingInstructionRepository gradingInstructionRepository;

    @Autowired
    private TutorParticipationTestRepository tutorParticipationRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ResultService resultService;

    @Autowired
    private SubmissionTestRepository submissionRepository;

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
            path = "/api/assessment/exercises/" + exercise.getId() + "/assess-example-participation";
        }

        textBlockIds = new ArrayList<>();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTutorParticipateInModelingExerciseWithExampleSubmission(boolean usedForTutorial) throws Exception {
        var tutorId = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1").getId();

        ExampleParticipation exampleSubmission = prepareModelingExampleSubmission(usedForTutorial);
        var tutorParticipationDTO = request.postWithResponseBody(path, exampleSubmission, TutorParticipationDTO.class, HttpStatus.OK);
        assertThat(tutorParticipationDTO.trainedExampleParticipations()).as("Tutor participation has example submission").hasSize(1);
        assertThat(tutorParticipationDTO.tutorId()).as("Tutor participation belongs to correct tutor").isEqualTo(tutorId);
        assertThat(tutorParticipationDTO.exerciseId()).as("Tutor participation belongs to correct exercise").isEqualTo(modelingExercise.getId());
        assertThat(tutorParticipationDTO.status()).as("Tutor participation has correct status").isEqualTo(TutorParticipationStatus.TRAINED);
    }

    /**
     * Tests the tutor training with example submission in Text exercises.
     * In case tutor has provided a feedback which was not provided by the instructor, response is BAD_REQUEST.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTutorParticipateInTextExerciseWithExampleSubmissionAddingUnnecessaryFeedbackBadRequest() throws Exception {
        ExampleParticipation exampleSubmission = prepareTextExampleSubmission(true);

        // Tutor reviewed the instructions.
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        var existing = tutorParticipationRepository.findWithEagerExampleParticipationAndResultsByAssessedExerciseAndTutor(textExercise, tutor);
        // Refetch with tutorParticipations loaded to avoid LazyInitializationException
        exampleSubmission = exampleParticipationRepository.findByIdWithResultsAndTutorParticipations(exampleSubmission.getId()).orElseThrow();
        exampleSubmission.addTutorParticipation(existing);
        exampleSubmission = exampleParticipationService.save(exampleSubmission);

        Submission submissionWithResults = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(exampleSubmission.getSubmission().getId());
        submissionWithResults.getLatestResult().addFeedback(ParticipationFactory.createManualTextFeedback(1D, textBlockIds.get(1)));

        var path = "/api/assessment/exercises/" + textExercise.getId() + "/assess-example-participation";
        request.post(path, exampleSubmission, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tests when tutor provides unnecessary unreferenced feedback in text example assessment, bad request exception is thrown
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTutorParticipateInTextExerciseWithExampleSubmissionAddingUnnecessaryUnreferencedFeedbackBadRequest() throws Exception {
        ExampleParticipation exampleSubmission = prepareTextExampleSubmission(true);

        // Tutor reviewed the instructions.
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        var existing = tutorParticipationRepository.findWithEagerExampleParticipationAndResultsByAssessedExerciseAndTutor(textExercise, tutor);
        // Refetch with tutorParticipations loaded to avoid LazyInitializationException
        exampleSubmission = exampleParticipationRepository.findByIdWithResultsAndTutorParticipations(exampleSubmission.getId()).orElseThrow();
        exampleSubmission.addTutorParticipation(existing);
        exampleSubmission = exampleParticipationService.save(exampleSubmission);

        Submission submissionWithResults = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(exampleSubmission.getSubmission().getId());
        submissionWithResults.getLatestResult().addFeedback(ParticipationFactory.createPositiveFeedback(FeedbackType.MANUAL_UNREFERENCED));

        var path = "/api/assessment/exercises/" + textExercise.getId() + "/assess-example-participation";
        request.post(path, exampleSubmission, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tests the tutor training with example submission in Modelling exercises.
     * In case tutor has provided a feedback which was not provided by the instructor, response is BAD_REQUEST.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTutorParticipateInModelingExerciseWithExampleSubmissionAddingUnnecessaryFeedbackBadRequest() throws Exception {
        ExampleParticipation exampleSubmission = prepareModelingExampleSubmission(true);

        // Tutor reviewed the instructions.
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        var tutorParticipation = new TutorParticipation().tutor(tutor).status(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        tutorParticipationService.createNewParticipation(textExercise, tutor);
        exampleSubmission.addTutorParticipation(tutorParticipation);
        exampleParticipationService.save(exampleSubmission);

        exampleSubmission.getSubmission().getLatestResult().addFeedback(ParticipationFactory.createManualTextFeedback(1D, "6aba5764-d102-4740-9675-b2bd0a4f2680"));
        var path = "/api/assessment/exercises/" + textExercise.getId() + "/assess-example-participation";
        request.post(path, exampleSubmission, HttpStatus.BAD_REQUEST);
    }

    /**
     * Tests when tutor provides unnecessry unreferenced feedback in modeling example assessment, bad request exception is thrown
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testTutorParticipateInModelingExerciseWithExampleSubmissionAddingUnnecessaryUnreferencedFeedbackBadRequest() throws Exception {
        ExampleParticipation exampleSubmission = prepareModelingExampleSubmission(true);

        // Tutor reviewed the instructions.
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        var tutorParticipation = new TutorParticipation().tutor(tutor).status(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        tutorParticipationService.createNewParticipation(textExercise, tutor);
        exampleSubmission.addTutorParticipation(tutorParticipation);
        exampleParticipationService.save(exampleSubmission);

        exampleSubmission.getSubmission().getLatestResult().addFeedback(ParticipationFactory.createPositiveFeedback(FeedbackType.MANUAL_UNREFERENCED));
        var path = "/api/assessment/exercises/" + textExercise.getId() + "/assess-example-participation";
        request.post(path, exampleSubmission, HttpStatus.BAD_REQUEST);
    }

    @NonNull
    private ExampleParticipation prepareTextExampleSubmission(boolean usedForTutorial) throws Exception {
        var exampleSubmissionText = "This is first sentence:This is second sentence.";
        ExampleParticipation exampleSubmission = participationUtilService.generateExampleParticipation(exampleSubmissionText, textExercise, false, usedForTutorial);

        // Save first to get submission ID (needed for textBlock.computeId())
        exampleSubmission = exampleParticipationService.save(exampleSubmission);
        TextSubmission textSubmission = (TextSubmission) exampleSubmission.getSubmission();

        for (var sentence : exampleSubmissionText.split(":")) {
            var startIndex = exampleSubmissionText.indexOf(sentence);

            var textBlock = new TextBlock().text(sentence).startIndex(startIndex).endIndex(startIndex + sentence.length()).submission(textSubmission).manual();
            textBlock.computeId();
            textSubmission.addBlock(textBlock);

            // Store the id in textBlockIds for later access.
            textBlockIds.add(textBlock.getId());
        }

        exampleSubmission = exampleParticipationService.save(exampleSubmission);

        if (usedForTutorial) {
            // Fetch fresh submission with results loaded to avoid LazyInitializationException
            var freshSubmission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(exampleSubmission.getSubmission().getId());
            var result = submissionService.saveNewEmptyResult(freshSubmission, textExercise.getId());
            result.setExampleResult(true);
            result.setExerciseId(textExercise.getId());

            var feedback = ParticipationFactory.createManualTextFeedback(1D, textBlockIds.getFirst());
            var gradingCriterion = ExerciseFactory.generateGradingCriterion("criterion");
            gradingCriterion = gradingCriterionRepository.save(gradingCriterion);

            var instructions = ExerciseFactory.generateGradingInstructions(gradingCriterion, 1, 1);
            instructions = new HashSet<>(gradingInstructionRepository.saveAll(instructions));
            instructions.forEach(feedback::setGradingInstruction);
            resultService.addFeedbackToResult(result, List.of(feedback), true);
        }

        request.post("/api/assessment/exercises/" + textExercise.getId() + "/tutor-participations", null, HttpStatus.CREATED);
        return exampleSubmission;
    }

    @NonNull
    private ExampleParticipation prepareModelingExampleSubmission(boolean usedForTutorial) throws Exception {
        String validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        ExampleParticipation exampleSubmission = participationUtilService.generateExampleParticipation(validModel, modelingExercise, false, usedForTutorial);
        exampleParticipationService.save(exampleSubmission);
        if (usedForTutorial) {
            var result = submissionService.saveNewEmptyResult(exampleSubmission.getSubmission(), modelingExercise.getId());
            result.setExampleResult(true);
            resultRepository.save(result);
        }
        request.post("/api/assessment/exercises/" + modelingExercise.getId() + "/tutor-participations", null, HttpStatus.CREATED);
        return exampleSubmission;
    }
}
