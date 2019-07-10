package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class ExampleSubmissionIntegrationTest {

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    ExampleSubmissionRepository exampleSubmissionRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

    private ModelingExercise modelingExercise;

    private ExampleSubmission exampleSubmission;

    private String emptyModel;

    private String validModel;

    @Before
    public void initTestCase() throws Exception {
        database.resetDatabase();
        database.addUsers(1, 1, 1);
        database.addCourseWithOneModelingExercise();
        modelingExercise = (ModelingExercise) exerciseRepo.findAll().get(0);
        emptyModel = database.loadFileFromResources("test-data/model-submission/empty-model.json");
        validModel = database.loadFileFromResources("test-data/model-submission/model.54727.json");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void createAndUpdateExampleModelingSubmission() throws Exception {
        exampleSubmission = generateExampleSubmission(emptyModel, modelingExercise, false);
        ExampleSubmission returnedExampleSubmission = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission,
                ExampleSubmission.class, HttpStatus.OK);

        database.checkModelingSubmissionCorrectlyStored(returnedExampleSubmission.getSubmission().getId(), emptyModel);
        Optional<ExampleSubmission> storedExampleSubmission = exampleSubmissionRepo.findBySubmissionId(returnedExampleSubmission.getSubmission().getId());
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.get().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();

        exampleSubmission = generateExampleSubmission(validModel, modelingExercise, false);
        returnedExampleSubmission = request.postWithResponseBody("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission, ExampleSubmission.class,
                HttpStatus.OK);

        database.checkModelingSubmissionCorrectlyStored(returnedExampleSubmission.getSubmission().getId(), validModel);
        storedExampleSubmission = exampleSubmissionRepo.findBySubmissionId(returnedExampleSubmission.getSubmission().getId());
        assertThat(storedExampleSubmission).as("example submission correctly stored").isPresent();
        assertThat(storedExampleSubmission.get().getSubmission().isExampleSubmission()).as("submission flagged as example submission").isTrue();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createExampleModelingSubmission_asTutor_forbidden() throws Exception {
        exampleSubmission = generateExampleSubmission(emptyModel, modelingExercise, true);
        request.post("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "student1")
    public void createExampleModelingSubmission_asStudent_forbidden() throws Exception {
        exampleSubmission = generateExampleSubmission(emptyModel, modelingExercise, true);
        request.post("/api/exercises/" + modelingExercise.getId() + "/example-submissions", exampleSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getExampleModelingSubmission() throws Exception {
        ExampleSubmission storedExampleSubmission = database.addExampleSubmission(generateExampleSubmission(validModel, modelingExercise, true), "student1");

        exampleSubmission = request.get("/api/example-submissions/" + storedExampleSubmission.getId(), HttpStatus.OK, ExampleSubmission.class);

        database.checkModelsAreEqual(((ModelingSubmission) exampleSubmission.getSubmission()).getModel(), validModel);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getExampleModelingSubmission_asStudent_forbidden() throws Exception {
        ExampleSubmission storedExampleSubmission = database.addExampleSubmission(generateExampleSubmission(validModel, modelingExercise, true), "student1");

        request.get("/api/example-submissions/" + storedExampleSubmission.getId(), HttpStatus.FORBIDDEN, ExampleSubmission.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void createExampleModelingAssessment() throws Exception {
        ExampleSubmission storedExampleSubmission = database.addExampleSubmission(generateExampleSubmission(validModel, modelingExercise, true), "student1");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        request.putWithResponseBody("/api/modeling-submissions/" + storedExampleSubmission.getId() + "/exampleAssessment", feedbacks, Result.class, HttpStatus.OK);

        Result storedResult = resultRepo.findDistinctWithFeedbackBySubmissionId(storedExampleSubmission.getSubmission().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
    }

    private ExampleSubmission generateExampleSubmission(String path, Exercise exercise, boolean flagAsExampleSubmission) {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(path, false);
        submission.setExampleSubmission(flagAsExampleSubmission);
        return ModelFactory.generateExampleSubmission(submission, exercise, false);
    }

    private void checkFeedbackCorrectlyStored(List<Feedback> sentFeedback, List<Feedback> storedFeedback, FeedbackType feedbackType) {
        assertThat(sentFeedback.size()).as("contains the same amount of feedback").isEqualTo(storedFeedback.size());
        Result storedFeedbackResult = new Result();
        Result sentFeedbackResult = new Result();
        storedFeedbackResult.setFeedbacks(storedFeedback);
        sentFeedbackResult.setFeedbacks(sentFeedback);
        storedFeedbackResult.evaluateFeedback(20);
        sentFeedbackResult.evaluateFeedback(20);
        assertThat(storedFeedbackResult.getScore()).as("stored feedback evaluates to the same score as sent feedback").isEqualTo(sentFeedbackResult.getScore());
        storedFeedback.forEach(feedback -> {
            assertThat(feedback.getType()).as("type has been set to MANUAL").isEqualTo(feedbackType);
        });
    }
}
