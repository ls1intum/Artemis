package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.ExampleSubmissionService;
import de.tum.in.www1.artemis.service.SubmissionService;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ModelFactory;

public class TutorParticipationIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private ExampleSubmissionService exampleSubmissionService;

    private ModelingExercise modelingExercise;

    private String path;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(0, 1, 0, 0);
        Course course = database.addCourseWithModelingAndTextExercise();
        for (Exercise exercise : course.getExercises()) {
            if (exercise instanceof ModelingExercise) {
                modelingExercise = (ModelingExercise) exercise;
            }
        }
        modelingExercise.setTitle("UML Class Diagram");
        exerciseRepo.save(modelingExercise);
        path = "/api/exercises/" + modelingExercise.getId() + "/assess-example-submission";
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testTutorParticipateInModelingExerciseWithExampleSubmission(boolean usedForTutorial) throws Exception {
        ExampleSubmission exampleSubmission = prepareExampleSubmission(usedForTutorial);
        var tutorParticipation = request.postWithResponseBody(path, exampleSubmission, TutorParticipation.class, HttpStatus.OK);
        assertThat(tutorParticipation.getTrainedExampleSubmissions()).as("Tutor participation has example submission").hasSize(1);
        assertThat(tutorParticipation.getTutor().getLogin()).as("Tutor participation belongs to correct tutor").isEqualTo("tutor1");
        assertThat(tutorParticipation.getAssessedExercise()).as("Tutor participation belongs to correct exercise").isEqualTo(modelingExercise);
        assertThat(tutorParticipation.getStatus()).as("Tutor participation has correct status").isEqualTo(TutorParticipationStatus.TRAINED);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testTutorParticipateInModelingExerciseWithExampleSubmissionTooHigh() throws Exception {
        ExampleSubmission exampleSubmission = prepareExampleSubmission(true);
        exampleSubmission.getSubmission().getLatestResult().addFeedback(ModelFactory.createNegativeFeedback(FeedbackType.MANUAL));
        var path = "/api/exercises/" + modelingExercise.getId() + "/assess-example-submission";
        request.postWithResponseBody(path, exampleSubmission, TutorParticipation.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testTutorParticipateInModelingExerciseWithExampleSubmissionTooLow() throws Exception {
        ExampleSubmission exampleSubmission = prepareExampleSubmission(true);
        exampleSubmission.getSubmission().getLatestResult().addFeedback(ModelFactory.createPositiveFeedback(FeedbackType.MANUAL));
        var path = "/api/exercises/" + modelingExercise.getId() + "/assess-example-submission";
        request.postWithResponseBody(path, exampleSubmission, TutorParticipation.class, HttpStatus.BAD_REQUEST);
    }

    @NotNull
    private ExampleSubmission prepareExampleSubmission(boolean usedForTutorial) throws Exception {
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
