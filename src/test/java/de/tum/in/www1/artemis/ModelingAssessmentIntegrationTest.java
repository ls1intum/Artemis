package de.tum.in.www1.artemis;

import java.util.List;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public class ModelingAssessmentIntegrationTest {
    @Autowired
    CourseRepository courseRepo;
    @Autowired
    ExerciseRepository exerciseRepo;
    @Autowired
    UserRepository userRepo;
    @Autowired
    RequestUtilService request;
    @Autowired
    DatabaseUtilService database;
    @Autowired
    ModelingSubmissionService modelSubmissionService;
    @Autowired
    ModelingSubmissionRepository modelingSubmissionRepo;
    @Autowired
    ParticipationService participationService;

    private ModelingExercise exercise;


    @Before
    public void initTestCase() throws Exception {
        database.resetFileStorage();
        database.resetDatabase();
        database.addUsers(2, 1);
        database.addCourseWithModelingExercise();
        exercise = (ModelingExercise) exerciseRepo.findAll().get(0);
    }


    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void manualAssessmentSave() throws Exception {
        ModelingSubmission submission = database.addModelingSubmissionFromResources(exercise, "test-data/model-submission/model.54727.json", "student1");
        Result result = submission.getResult();
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        result.setFeedbacks(feedbacks);
        request.put(
            "/api/submissions/"
                + submission.getId() + "/feedback",
            feedbacks,
            HttpStatus.OK);
        ModelingSubmission storedSubmission = modelingSubmissionRepo.findByIdWithEagerResult(submission.getId()).get();
        Result storedResult = storedSubmission.getResult();
        assertThat(storedSubmission.getResult().getFeedbacks()).as("feedback has been stored").isEqualTo(feedbacks);
        assertThat(storedResult.isRated()).as("rated has not been set").isFalse();
        assertThat(storedResult.getScore()).as("score hasnt been calculated").isNull();
        assertThat(storedResult.getAssessor()).as("Assessor has been set").isNotNull();
        assertThat(storedResult.getResultString()).as("result string has been set").isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void manualAssessmentSubmit() throws Exception {
        ModelingSubmission submission = database.addModelingSubmissionFromResources(exercise, "test-data/model-submission/model.54727.json", "student1");
        Result result = submission.getResult();
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        result.setFeedbacks(feedbacks);
        request.put(
            "/api/submissions/"
                + submission.getId() + "/feedback?submit=true",
            feedbacks,
            HttpStatus.OK);
        ModelingSubmission storedSubmission = modelingSubmissionRepo.findByIdWithEagerResult(submission.getId()).get();
        Result storedResult = storedSubmission.getResult();
        assertThat(storedSubmission.getResult().getFeedbacks()).as("feedback has been stored").isEqualTo(feedbacks);
        assertThat(storedResult.isRated()).as("rated has been set").isTrue();
        assertThat(storedResult.getScore()).as("score has been calculated").isNotNull();
        assertThat(storedResult.getAssessor()).as("Assessor has been set").isNotNull();
        assertThat(storedResult.getResultString()).as("result string has been set").isNotNull().isNotEqualTo("");
    }



    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void automaticAssessment() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(exercise, "test-data/model-submission/model.54727.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(exercise, "test-data/model-submission/model.54727.json", "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.put(
            "/api/modeling-submissions/"
                + submission1.getId() + "/feedback?submit=true",
            feedbacks,
            HttpStatus.OK);
        ModelingSubmission storedSubmission1 = modelingSubmissionRepo.findByIdWithEagerResult(submission1.getId()).get();
        Result storedResult1 = storedSubmission1.getResult();
        await().atMost(10, TimeUnit.SECONDS).alias("2nd submission has been automatically assessed").until(submissionHasBeenAssessed(submission2.getId()));
        ModelingSubmission storedSubmission2 = modelingSubmissionRepo.findByIdWithEagerResult(submission1.getId()).get();
        Result storedResult2 = storedSubmission2.getResult();
        assertThat(storedResult1.getScore()).as("identical model got assessed equally").isEqualTo(storedResult2.getScore());
    }


    private Callable<Boolean> submissionHasBeenAssessed(Long id) {
        return () -> {
            Result result = modelingSubmissionRepo.findByIdWithEagerResult(id).get().getResult();
            return result.getScore() != null;
        };
    }

}
