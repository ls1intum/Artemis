package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ModelFactory;

public class ModelingAssessmentIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    public static final String API_MODELING_SUBMISSIONS = "/api/modeling-submissions/";

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    ModelingSubmissionService modelSubmissionService;

    @Autowired
    ModelingSubmissionRepository modelingSubmissionRepo;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    ParticipationService participationService;

    @Autowired
    ExampleSubmissionService exampleSubmissionService;

    @Autowired
    CompassService compassService;

    @Autowired
    AssessmentService assessmentService;

    @Autowired
    ExamRepository examRepository;

    @Autowired
    StudentParticipationRepository studentParticipationRepository;

    private ModelingExercise classExercise;

    private ModelingExercise activityExercise;

    private ModelingExercise objectExercise;

    private ModelingExercise useCaseExercise;

    private ModelingSubmission modelingSubmission;

    private Result modelingAssessment;

    private String validModel;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(6, 2, 1);
        Course course = database.addCourseWithDifferentModelingExercises();
        classExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        activityExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ActivityDiagram");
        objectExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ObjectDiagram");
        useCaseExercise = database.findModelingExerciseWithTitle(course.getExercises(), "UseCaseDiagram");
        validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetAssessmentBySubmissionId() throws Exception {
        saveModelingSubmissionAndAssessment(true);
        List<Feedback> feedback = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.v2.json");
        database.updateAssessmentDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        Result result = request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.OK, Result.class);

        checkAssessmentFinished(result, null);
        checkFeedbackCorrectlyStored(feedback, result.getFeedbacks(), FeedbackType.MANUAL);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetAssessmentBySubmissionId_notFound() throws Exception {
        saveModelingSubmission();
        request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.NOT_FOUND, Result.class);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetAssessmentBySubmissionId_assessmentNotFinished_forbidden() throws Exception {
        saveModelingSubmissionAndAssessment(false);
        database.updateAssessmentDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testGetAssessmentBySubmissionId_assessmentDueDateNotOver_forbidden() throws Exception {
        saveModelingSubmissionAndAssessment(true);

        request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = "student2")
    public void testGetAssessmentBySubmissionId_studentNotOwnerOfSubmission_forbidden() throws Exception {
        saveModelingSubmissionAndAssessment(true);
        database.updateAssessmentDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test()
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetExampleAssessment() throws Exception {
        ExampleSubmission storedExampleSubmission = database.addExampleSubmission(database.generateExampleSubmission(validModel, classExercise, true, true));
        List<Feedback> feedbackList = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        Result storedResult = request.putWithResponseBody("/api/modeling-submissions/" + storedExampleSubmission.getId() + "/example-assessment", feedbackList, Result.class,
                HttpStatus.OK);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
        assertThat(exampleSubmissionService.findById(storedExampleSubmission.getId())).isPresent();
        // NOTE: for some reason this test fails in IntelliJ but works fine on the command line
        request.get("/api/exercise/" + classExercise.getId() + "/modeling-submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-assessment", HttpStatus.OK,
                Result.class);
    }

    @Test
    @WithMockUser(username = "student1")
    public void testManualAssessmentSubmitAsStudent() throws Exception {
        ModelingSubmission submission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.put(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + 1 + "/assessment?submit=true", feedbacks, HttpStatus.FORBIDDEN);

        Optional<Result> storedResult = resultRepo.findDistinctBySubmissionId(submission.getId());
        assertThat(storedResult).as("result is not saved").isNotPresent();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testManualAssessmentSave() throws Exception {
        User assessor = database.getUserByLogin("tutor1");
        ModelingSubmission submission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.put(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + 1 + "/assessment", feedbacks, HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentNotFinished(storedResult, assessor);
        assertThat(storedResult.getParticipation()).isNotNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testManualAssessmentSave_noCourse() throws Exception {
        classExercise.setCourse(null);
        exerciseRepo.save(classExercise);
        ModelingSubmission submission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.put(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + 1 + "/assessment", feedbacks, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testManualAssessmentSubmit_classDiagram() throws Exception {
        User assessor = database.getUserByLogin("tutor1");
        ModelingSubmission submission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.put(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + 1 + "/assessment?submit=true", feedbacks, HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
        assertThat(storedResult.getParticipation()).isNotNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testManualAssessmentSubmit_activityDiagram() throws Exception {
        User assessor = database.getUserByLogin("tutor1");
        ModelingSubmission submission = database.addModelingSubmissionFromResources(activityExercise, "test-data/model-submission/example-activity-diagram.json", "student1");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/example-activity-assessment.json");
        request.put(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + 1 + "/assessment?submit=true", feedbacks, HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
        assertThat(storedResult.getParticipation()).isNotNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testManualAssessmentSubmit_objectDiagram() throws Exception {
        User assessor = database.getUserByLogin("tutor1");
        ModelingSubmission submission = database.addModelingSubmissionFromResources(objectExercise, "test-data/model-submission/object-model.json", "student1");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/object-assessment.json");
        request.put(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + 1 + "/assessment?submit=true", feedbacks, HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testManualAssessmentSubmit_useCaseDiagram() throws Exception {
        User assessor = database.getUserByLogin("tutor1");
        ModelingSubmission submission = database.addModelingSubmissionFromResources(useCaseExercise, "test-data/model-submission/use-case-model.json", "student1");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/use-case-assessment.json");
        request.put(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + 1 + "/assessment?submit=true", feedbacks, HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
        assertThat(storedResult.getParticipation()).isNotNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testManualAssessmentSaveAndSubmit() throws Exception {
        User assessor = database.getUserByLogin("tutor1");
        ModelingSubmission submission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.put(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + 1 + "/assessment", feedbacks, HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentNotFinished(storedResult, assessor);

        feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.v2.json");
        request.put(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + 1 + "/assessment?submit=true", feedbacks, HttpStatus.OK);

        storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
        assertThat(storedResult.getParticipation()).isNotNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testManualAssessmentSubmit_withResultOver100Percent() throws Exception {
        useCaseExercise = (ModelingExercise) database.addMaxScoreAndBonusPointsToExercise(useCaseExercise);
        ModelingSubmission submission = database.addModelingSubmissionFromResources(useCaseExercise, "test-data/model-submission/use-case-model.json", "student1");

        List<Feedback> feedbacks = new ArrayList<>();
        // Check that result is over 100% -> 105
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 2"));
        request.put(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + 1 + "/assessment?submit=true", feedbacks, HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();

        assertThat(storedResult.getScore()).isEqualTo(105);

        // Check that result is capped to maximum of maxScore + bonus points -> 110
        feedbacks.add(new Feedback().credits(20.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 3"));
        request.put(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + 1 + "/assessment?submit=true", feedbacks, HttpStatus.OK);

        storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();

        assertThat(storedResult.getScore()).isEqualTo(110);
    }

    // region Automatic Assessment Tests
    @Test
    @WithMockUser(username = "student2")
    public void testAutomaticAssessmentUponModelSubmission_identicalModel() throws Exception {
        saveModelingSubmissionAndAssessment(true);
        database.createAndSaveParticipationForExercise(classExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.cpy.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Result automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(storedSubmission.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(automaticResult);
        checkFeedbackCorrectlyStored(modelingAssessment.getFeedbacks(), automaticResult.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "student2")
    public void testAutomaticAssessmentUponModelSubmission_activityDiagram_identicalModel() throws Exception {
        saveModelingSubmissionAndAssessment_activityDiagram(true);
        database.createAndSaveParticipationForExercise(activityExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/example-activity-diagram.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + activityExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Result automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(storedSubmission.getId(), activityExercise.getId());
        assertThat(automaticResult).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(automaticResult);
        checkFeedbackCorrectlyStored(modelingAssessment.getFeedbacks(), automaticResult.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "student2")
    public void testAutomaticAssessmentUponModelSubmission_partialModel() throws Exception {
        saveModelingSubmissionAndAssessment(true);
        database.createAndSaveParticipationForExercise(classExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.partial.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Result automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(storedSubmission.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(automaticResult);
        List<Feedback> feedbackUsedForAutomaticAssessment = modelingAssessment.getFeedbacks().stream()
                .filter(feedback -> automaticResult.getFeedbacks().stream().anyMatch(storedFeedback -> storedFeedback.getReference().equals(feedback.getReference())))
                .collect(Collectors.toList());
        checkFeedbackCorrectlyStored(feedbackUsedForAutomaticAssessment, automaticResult.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "student2")
    public void testAutomaticAssessmentUponModelSubmission_partialModelExists() throws Exception {
        modelingSubmission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.partial.json", "student1");
        modelingAssessment = database.addModelingAssessmentForSubmission(classExercise, modelingSubmission, "test-data/model-assessment/assessment.54727.partial.json", "tutor1",
                true);
        database.createAndSaveParticipationForExercise(classExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Result automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(storedSubmission.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(automaticResult);
        checkFeedbackCorrectlyStored(modelingAssessment.getFeedbacks(), automaticResult.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "ADMIN")
    public void testStatistics() throws Exception {
        saveModelingSubmissionAndAssessment(true);
        database.createAndSaveParticipationForExercise(classExercise, "instructor1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.partial.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);
        compassService.getResultWithFeedbackSuggestionsForSubmission(storedSubmission.getId(), classExercise.getId());

        request.get("/api/modeling-exercises/" + classExercise.getId() + "/print-statistic", HttpStatus.OK, String.class);   // void == empty string
        String statistics = request.get("/api/modeling-exercises/" + classExercise.getId() + "/statistics", HttpStatus.OK, String.class);
        // TODO: assert that the statistics is correct
    }

    @Test
    @WithMockUser(username = "student2")
    public void testAutomaticAssessmentUponModelSubmission_noSimilarity() throws Exception {
        modelingSubmission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54745.json", "student1");
        database.addModelingAssessmentForSubmission(classExercise, modelingSubmission, "test-data/model-assessment/assessment.54745.json", "tutor1", true);
        database.createAndSaveParticipationForExercise(classExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Result automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(storedSubmission.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(automaticResult);
        assertThat(automaticResult.getFeedbacks()).as("no feedback has been assigned").isEmpty();
    }

    @Test
    @WithMockUser(username = "student2")
    public void testAutomaticAssessmentUponModelSubmission_similarElementsWithinModel() throws Exception {
        modelingSubmission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.inheritance.json"), true);
        modelingSubmission = database.addModelingSubmission(classExercise, modelingSubmission, "student1");
        modelingAssessment = database.addModelingAssessmentForSubmission(classExercise, modelingSubmission, "test-data/model-assessment/assessment.inheritance.json", "tutor1",
                true);
        database.createAndSaveParticipationForExercise(classExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.inheritance.cpy.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Result automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(storedSubmission.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(automaticResult);
        checkFeedbackCorrectlyStored(modelingAssessment.getFeedbacks(), automaticResult.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "student2")
    public void testAutomaticAssessmentUponModelSubmission_noResultInDatabase() throws Exception {
        saveModelingSubmissionAndAssessment(true);
        database.createAndSaveParticipationForExercise(classExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.cpy.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Optional<Result> automaticResult = resultRepo.findDistinctWithFeedbackBySubmissionId(storedSubmission.getId());
        assertThat(automaticResult).as("automatic result not stored in database").isNotPresent();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testConfidenceThreshold() throws Exception {
        Feedback feedbackOnePoint = new Feedback().credits(1.0).reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        Feedback feedbackTwentyPoints = new Feedback().credits(20.0).reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student2");
        ModelingSubmission submission3 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student3");
        ModelingSubmission submission4 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student4");
        ModelingSubmission submission5 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student5");
        ModelingSubmission submissionToCheck = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student6");

        request.put(API_MODELING_SUBMISSIONS + submission1.getId() + "/result/" + 1 + "/assessment?submit=true", Collections.singletonList(feedbackTwentyPoints.text("wrong text")),
                HttpStatus.OK);

        Result automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(submissionToCheck.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks().size()).as("element is assessed automatically").isEqualTo(1);
        assertThat(automaticResult.getFeedbacks().get(0).getCredits()).as("credits of element are correct").isEqualTo(20);
        assertThat(automaticResult.getFeedbacks().get(0).getText()).as("feedback text of element is correct").isEqualTo("wrong text");

        request.put(API_MODELING_SUBMISSIONS + submission2.getId() + "/result/" + 1 + "/assessment?submit=true",
                Collections.singletonList(feedbackOnePoint.text("long feedback text")), HttpStatus.OK);

        automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(submissionToCheck.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks().size()).as("element is not assessed automatically").isEqualTo(0);

        request.put(API_MODELING_SUBMISSIONS + submission3.getId() + "/result/" + 1 + "/assessment?submit=true", Collections.singletonList(feedbackOnePoint.text("short text")),
                HttpStatus.OK);

        automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(submissionToCheck.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks().size()).as("element is not assessed automatically").isEqualTo(0);

        request.put(API_MODELING_SUBMISSIONS + submission4.getId() + "/result/" + 1 + "/assessment?submit=true",
                Collections.singletonList(feedbackOnePoint.text("very long feedback text")), HttpStatus.OK);

        automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(submissionToCheck.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks().size()).as("element is not assessed automatically").isEqualTo(0);

        request.put(API_MODELING_SUBMISSIONS + submission5.getId() + "/result/" + 1 + "/assessment?submit=true", Collections.singletonList(feedbackOnePoint.text("medium text")),
                HttpStatus.OK);

        automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(submissionToCheck.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks().size()).as("element is assessed automatically").isEqualTo(1);
        assertThat(automaticResult.getFeedbacks().get(0).getCredits()).as("credits of element are correct").isEqualTo(1);
        assertThat(automaticResult.getFeedbacks().get(0).getText()).as("feedback text of element is correct").isEqualTo("very long feedback text");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testLongestFeedbackTextSelection() throws Exception {
        Feedback feedbackOnePoint = new Feedback().credits(1.0).reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student2");
        ModelingSubmission submission3 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student3");
        ModelingSubmission submissionToCheck = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student4");

        request.put(API_MODELING_SUBMISSIONS + submission1.getId() + "/result/" + 1 + "/assessment?submit=true", Collections.singletonList(feedbackOnePoint.text("feedback text")),
                HttpStatus.OK);

        Result automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(submissionToCheck.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks().size()).as("element is assessed automatically").isEqualTo(1);
        assertThat(automaticResult.getFeedbacks().get(0).getText()).as("feedback text of element is correct").isEqualTo("feedback text");

        request.put(API_MODELING_SUBMISSIONS + submission2.getId() + "/result/" + 1 + "/assessment?submit=true", Collections.singletonList(feedbackOnePoint.text("short")),
                HttpStatus.OK);

        automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(submissionToCheck.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks().size()).as("element is assessed automatically").isEqualTo(1);
        assertThat(automaticResult.getFeedbacks().get(0).getText()).as("feedback text of element is correct").isEqualTo("feedback text");

        request.put(API_MODELING_SUBMISSIONS + submission3.getId() + "/result/" + 1 + "/assessment?submit=true",
                Collections.singletonList(feedbackOnePoint.text("very long feedback text")), HttpStatus.OK);

        automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(submissionToCheck.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks().size()).as("element is assessed automatically").isEqualTo(1);
        assertThat(automaticResult.getFeedbacks().get(0).getText()).as("feedback text of element is correct").isEqualTo("very long feedback text");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAutomaticAssessmentUponAssessmentSubmission() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.cpy.json", "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        request.put(API_MODELING_SUBMISSIONS + submission1.getId() + "/result/" + 1 + "/assessment?submit=true", feedbacks, HttpStatus.OK);

        Result storedResultOfSubmission2 = compassService.getResultWithFeedbackSuggestionsForSubmission(submission2.getId(), classExercise.getId());
        assertThat(storedResultOfSubmission2).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(storedResultOfSubmission2);
        checkFeedbackCorrectlyStored(feedbacks, storedResultOfSubmission2.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAutomaticAssessmentUponAssessmentSubmission_noResultInDatabase() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.cpy.json", "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        request.put(API_MODELING_SUBMISSIONS + submission1.getId() + "/result/" + 1 + "/assessment?submit=true", feedbacks, HttpStatus.OK);

        Optional<Result> automaticResult = resultRepo.findDistinctWithFeedbackBySubmissionId(submission2.getId());
        assertThat(automaticResult).as("automatic result not stored in database").isNotPresent();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testNoAutomaticAssessmentUponAssessmentSave() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.cpy.json", "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        request.put(API_MODELING_SUBMISSIONS + submission1.getId() + "/result/" + 1 + "/assessment", feedbacks, HttpStatus.OK);

        Result storedResultOfSubmission2 = compassService.getResultWithFeedbackSuggestionsForSubmission(submission2.getId(), classExercise.getId());
        assertThat(storedResultOfSubmission2).as("no automatic result has been created").isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAutomaticAssessment_elementsWithDifferentContextInSameSimilaritySet() throws Exception {
        List<Feedback> assessment1 = database.loadAssessmentFomResources("test-data/model-assessment/assessment.different-context.json");
        List<Feedback> assessment2 = database.loadAssessmentFomResources("test-data/model-assessment/assessment.different-context.automatic.json");
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.different-context.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.different-context.json", "student2");
        ModelingSubmission submissionToCheck = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.different-context.json", "student3");

        request.put(API_MODELING_SUBMISSIONS + submission1.getId() + "/result/" + 1 + "/assessment?submit=true", assessment1, HttpStatus.OK);

        Result automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(submissionToCheck.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks().size()).as("all elements got assessed automatically").isEqualTo(4);

        request.put(API_MODELING_SUBMISSIONS + submission2.getId() + "/result/" + 1 + "/assessment?submit=true", assessment2, HttpStatus.OK);

        automaticResult = compassService.getResultWithFeedbackSuggestionsForSubmission(submissionToCheck.getId(), classExercise.getId());
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks().size()).as("not all elements got assessed automatically").isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testOverrideAutomaticAssessment() throws Exception {
        modelingSubmission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.partial.json", "student1");
        modelingAssessment = database.addModelingAssessmentForSubmission(classExercise, modelingSubmission, "test-data/model-assessment/assessment.54727.partial.json", "tutor1",
                true);
        database.createAndSaveParticipationForExercise(classExercise, "tutor1");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);
        Result resultWithFeedback = compassService.getResultWithFeedbackSuggestionsForSubmission(storedSubmission.getId(), classExercise.getId());
        List<Feedback> existingFeedback = resultWithFeedback.getFeedbacks();
        Feedback feedback = existingFeedback.get(0);
        existingFeedback.set(0, feedback.credits(feedback.getCredits() + 0.5));
        feedback = existingFeedback.get(2);
        existingFeedback.set(2, feedback.text(feedback.getText() + " foo"));
        List<Feedback> newFeedback = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.partial2.json");
        List<Feedback> overrideFeedback = new ArrayList<>(existingFeedback);
        overrideFeedback.addAll(newFeedback);

        Result storedResult = request.putWithResponseBody(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result/" + modelingAssessment.getId() + "/assessment",
                overrideFeedback, Result.class, HttpStatus.OK);

        List<Feedback> manualFeedback = new ArrayList<>();
        List<Feedback> automaticFeedback = new ArrayList<>();
        List<Feedback> adaptedFeedback = new ArrayList<>();
        List<Feedback> manualUnreferencedFeedback = new ArrayList<>();

        storedResult.getFeedbacks().forEach(storedFeedback -> {
            switch (storedFeedback.getType()) {
                case MANUAL -> manualFeedback.add(storedFeedback);
                case AUTOMATIC -> automaticFeedback.add(storedFeedback);
                case MANUAL_UNREFERENCED -> manualUnreferencedFeedback.add(storedFeedback);
                case AUTOMATIC_ADAPTED -> adaptedFeedback.add(storedFeedback);
            }
        });
        assertThat(storedResult.getAssessmentType()).as("type of result is SEMI_AUTOMATIC").isEqualTo(AssessmentType.SEMI_AUTOMATIC);
        assertThat(manualFeedback.size()).as("number of manual feedback elements is correct").isEqualTo(newFeedback.size());
        assertThat(automaticFeedback.size()).as("number of automatic feedback elements is correct").isEqualTo(existingFeedback.size() - 2);
        assertThat(adaptedFeedback.size()).as("number of adapted feedback elements is correct").isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testOverrideAutomaticAssessment_existingManualAssessmentDoesNotChange() throws Exception {
        Feedback originalFeedback = new Feedback().credits(1.0).text("some feedback text").reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        Feedback changedFeedback = new Feedback().credits(2.0).text("another text").reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        modelingSubmission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student1");
        ModelingSubmission modelingSubmission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student2");
        request.put(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result/" + 1 + "/assessment?submit=true", Collections.singletonList(originalFeedback), HttpStatus.OK);

        request.put(API_MODELING_SUBMISSIONS + modelingSubmission2.getId() + "/result/" + 1 + "/assessment?submit=true", Collections.singletonList(changedFeedback), HttpStatus.OK);

        modelingAssessment = resultRepo.findDistinctWithFeedbackBySubmissionId(modelingSubmission2.getId()).get();
        assertThat(modelingAssessment.getFeedbacks().size()).as("assessment is correctly stored").isEqualTo(1);
        assertThat(modelingAssessment.getFeedbacks().get(0)).as("feedback credits and text are correct").isEqualToComparingOnlyGivenFields(changedFeedback, "credits", "text");
        modelingAssessment = resultRepo.findDistinctWithFeedbackBySubmissionId(modelingSubmission.getId()).get();
        assertThat(modelingAssessment.getFeedbacks().size()).as("existing manual assessment has correct amount of feedback").isEqualTo(1);
        assertThat(modelingAssessment.getFeedbacks().get(0)).as("existing manual assessment did not change").isEqualToComparingOnlyGivenFields(originalFeedback, "credits", "text");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testOverrideSubmittedManualAssessment_noConflict() throws Exception {
        Feedback originalFeedback = new Feedback().credits(1.0).text("some feedback text").reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        modelingSubmission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student1");
        ModelingSubmission modelingSubmission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student2");
        request.put(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result/" + 1 + "/assessment?submit=true", Collections.singletonList(originalFeedback), HttpStatus.OK);

        Result originalResult = resultRepo.findDistinctWithFeedbackBySubmissionId(modelingSubmission.getId()).get();
        Feedback changedFeedback = originalResult.getFeedbacks().get(0).credits(2.0).text("another text");
        request.put(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result/" + originalResult.getId() + "/assessment?submit=true",
                Collections.singletonList(changedFeedback), HttpStatus.OK);

        modelingAssessment = resultRepo.findDistinctWithFeedbackBySubmissionId(modelingSubmission.getId()).get();
        assertThat(modelingAssessment.getFeedbacks().size()).as("overridden assessment has correct amount of feedback").isEqualTo(1);
        assertThat(modelingAssessment.getFeedbacks().get(0)).as("feedback is properly overridden").isEqualToComparingOnlyGivenFields(changedFeedback, "credits", "text");
        modelingAssessment = compassService.getResultWithFeedbackSuggestionsForSubmission(modelingSubmission2.getId(), classExercise.getId());
        assertThat(modelingAssessment.getFeedbacks().size()).as("automatic assessment still exists").isEqualTo(1);
        assertThat(modelingAssessment.getFeedbacks().get(0)).as("automatic assessment is overridden properly").isEqualToComparingOnlyGivenFields(changedFeedback, "credits",
                "text");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testOverrideSubmittedManualAssessment_conflict() throws Exception {
        Feedback originalFeedback = new Feedback().credits(1.0).text("some feedback text").reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        Feedback originalFeedbackWithoutReference = new Feedback().credits(1.5).text("some feedback text again").reference(null).type(FeedbackType.MANUAL_UNREFERENCED);
        modelingSubmission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student1");
        ModelingSubmission modelingSubmission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student2");
        ModelingSubmission modelingSubmission3 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student3");
        request.put(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result/" + 1 + "/assessment?submit=true",
                Arrays.asList(originalFeedback, originalFeedbackWithoutReference), HttpStatus.OK);
        request.put(API_MODELING_SUBMISSIONS + modelingSubmission2.getId() + "/result/" + 2 + "/assessment?submit=true",
                Arrays.asList(originalFeedback, originalFeedbackWithoutReference), HttpStatus.OK);

        Result originalResult = resultRepo.findDistinctWithFeedbackBySubmissionId(modelingSubmission.getId()).get();
        Feedback changedFeedback = originalResult.getFeedbacks().get(0).credits(2.0).text("another text");
        Feedback feedbackWithoutReference = new Feedback().credits(1.0).text("another feedback text again").reference(null).type(FeedbackType.MANUAL_UNREFERENCED);
        request.put(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result/" + originalResult.getId() + "/assessment?submit=true",
                Arrays.asList(changedFeedback, feedbackWithoutReference), HttpStatus.OK);

        modelingAssessment = resultRepo.findDistinctWithFeedbackBySubmissionId(modelingSubmission.getId()).get();
        assertThat(modelingAssessment.getFeedbacks().size()).as("overridden assessment has correct amount of feedback").isEqualTo(2);
        assertThat(modelingAssessment.getFeedbacks().get(0)).as("feedback is properly overridden").isEqualToComparingOnlyGivenFields(changedFeedback, "credits", "text");
        modelingAssessment = resultRepo.findDistinctWithFeedbackBySubmissionId(modelingSubmission2.getId()).get();
        assertThat(modelingAssessment.getFeedbacks().size()).as("existing submitted assessment still exists").isEqualTo(2);
        assertThat(modelingAssessment.getFeedbacks().get(0)).as("existing feedback is still the same").isEqualToComparingOnlyGivenFields(originalFeedback, "credits", "text");
        modelingAssessment = compassService.getResultWithFeedbackSuggestionsForSubmission(modelingSubmission3.getId(), classExercise.getId());
        assertThat(modelingAssessment.getFeedbacks().size()).as("automatic assessment is not possible").isEqualTo(0);
    }
    // endregion

    private void checkAssessmentNotFinished(Result storedResult, User assessor) {
        assertThat(storedResult.isRated() == null || !storedResult.isRated()).as("rated has not been set").isTrue();
        assertThat(storedResult.getScore()).as("score has not been calculated").isNull();
        assertThat(storedResult.getAssessor()).as("Assessor has been set").isEqualTo(assessor);
        assertThat(storedResult.getResultString()).as("result string has not been set").isNull();
        assertThat(storedResult.getCompletionDate()).as("completion date has not been set").isNull();
    }

    private void checkAssessmentFinished(Result storedResult, User assessor) {
        assertThat(storedResult.isRated()).as("rated has been set").isTrue();
        assertThat(storedResult.getScore()).as("score has been calculated").isNotNull();
        assertThat(storedResult.getAssessor()).as("Assessor has been set").isEqualTo(assessor);
        assertThat(storedResult.getResultString()).as("result string has been set").isNotNull().isNotEqualTo("");
        assertThat(storedResult.getCompletionDate()).as("completion date has been set").isNotNull();
    }

    private void checkFeedbackCorrectlyStored(List<Feedback> sentFeedback, List<Feedback> storedFeedback, FeedbackType feedbackType) {
        assertThat(sentFeedback.size()).as("contains the same amount of feedback").isEqualTo(storedFeedback.size());
        Result storedFeedbackResult = new Result();
        Result sentFeedbackResult = new Result();
        storedFeedbackResult.setFeedbacks(storedFeedback);
        sentFeedbackResult.setFeedbacks(sentFeedback);

        Double calculatedScore = assessmentService.calculateTotalScore(storedFeedback);
        double totalScore = assessmentService.calculateTotalScore(calculatedScore, 20.0);
        storedFeedbackResult.setScore(totalScore, 20.0);
        storedFeedbackResult.setResultString(totalScore, 20.0);

        Double calculatedScore2 = assessmentService.calculateTotalScore(sentFeedback);
        double totalScore2 = assessmentService.calculateTotalScore(calculatedScore2, 20.0);
        sentFeedbackResult.setScore(totalScore2, 20.0);
        sentFeedbackResult.setResultString(totalScore2, 20.0);

        assertThat(storedFeedbackResult.getScore()).as("stored feedback evaluates to the same score as sent feedback").isEqualTo(sentFeedbackResult.getScore());
        storedFeedback.forEach(feedback -> {
            assertThat(feedback.getType()).as("type has been set correctly").isEqualTo(feedbackType);
        });
    }

    private void checkAutomaticAssessment(Result storedResult) {
        assertThat(storedResult.isRated() == null || !storedResult.isRated()).as("rated has not been set").isTrue();
        assertThat(storedResult.getScore()).as("score has not been calculated").isNull();
        assertThat(storedResult.getAssessor()).as("assessor has not been set").isNull();
        assertThat(storedResult.getResultString()).as("result string has not been set").isNull();
        assertThat(storedResult.getCompletionDate()).as("completion date has not been set").isNull();
        assertThat(storedResult.getAssessmentType()).as("result type is SEMI AUTOMATIC").isEqualTo(AssessmentType.SEMI_AUTOMATIC);
    }

    private void saveModelingSubmission() throws Exception {
        modelingSubmission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        modelingSubmission = database.addModelingSubmission(classExercise, modelingSubmission, "student1");
    }

    private void saveModelingSubmissionAndAssessment(boolean submitAssessment) throws Exception {
        modelingSubmission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        modelingSubmission = database.addModelingSubmission(classExercise, modelingSubmission, "student1");
        modelingAssessment = database.addModelingAssessmentForSubmission(classExercise, modelingSubmission, "test-data/model-assessment/assessment.54727.v2.json", "tutor1",
                submitAssessment);
    }

    private void saveModelingSubmissionAndAssessment_activityDiagram(boolean submitAssessment) throws Exception {
        modelingSubmission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/example-activity-diagram.json"), true);
        modelingSubmission = database.addModelingSubmission(activityExercise, modelingSubmission, "student1");
        modelingAssessment = database.addModelingAssessmentForSubmission(activityExercise, modelingSubmission, "test-data/model-assessment/example-activity-assessment.json",
                "tutor1", submitAssessment);
    }

    private void cancelAssessment(HttpStatus expectedStatus) throws Exception {
        modelingSubmission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/example-activity-diagram.json"), true);
        modelingSubmission = database.addModelingSubmission(activityExercise, modelingSubmission, "student1");
        modelingAssessment = database.addModelingAssessmentForSubmission(activityExercise, modelingSubmission, "test-data/model-assessment/example-activity-assessment.json",
                "tutor1", false);
        request.put(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/cancel-assessment", null, expectedStatus);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void cancelOwnAssessmentAsStudent() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void cancelOwnAssessmentAsTutor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void cancelAssessmentOfOtherTutorAsTutor() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void cancelAssessmentOfOtherTutorAsInstructor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void testOverrideAssessment_saveOtherTutorForbidden() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_saveInstructorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_saveSameTutorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void testOverrideAssessment_submitOtherTutorForbidden() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_submitInstructorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void testOverrideAssessment_saveOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_saveInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_saveSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_saveSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", false);
    }

    @Test
    @WithMockUser(value = "tutor2", roles = "TA")
    public void testOverrideAssessment_submitOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testOverrideAssessment_submitInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testOverrideAssessment_submitSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", false);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void multipleCorrectionRoundsForExam() throws Exception {
        // Setup exam with 2 correction rounds and a programming exercise
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();
        Exam exam = database.addExam(classExercise.getCourseViaExerciseGroupOrCourseMember());
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam.addExerciseGroup(exerciseGroup1);
        exam.setVisibleDate(ZonedDateTime.now().minusHours(3));
        exam.setStartDate(ZonedDateTime.now().minusHours(2));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        exam = examRepository.save(exam);

        Exam examWithExerciseGroups = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).get();
        exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().get(0);
        ModelingExercise exercise = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        exercise = exerciseRepo.save(exercise);
        exerciseGroup1.addExercise(exercise);

        // add student submission
        final var submission = database.addModelingSubmissionFromResources(exercise, "test-data/model-submission/model.54727.partial.json", "student1");

        // verify setup
        assertThat(exam.getNumberOfCorrectionRoundsInExam()).isEqualTo(2);
        assertThat(exam.getEndDate()).isBefore(ZonedDateTime.now());
        var optionalFetchedExercise = exerciseRepo.findWithEagerStudentParticipationsStudentAndSubmissionsById(exercise.getId());
        assertThat(optionalFetchedExercise.isPresent()).isTrue();
        final var exerciseWithParticipation = optionalFetchedExercise.get();
        final var studentParticipation = exerciseWithParticipation.getStudentParticipations().stream().iterator().next();

        // request to manually assess latest submission (correction round: 0)
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        params.add("correction-round", "0");
        ModelingSubmission submissionWithoutFirstAssessment = request.get("/api/exercises/" + exerciseWithParticipation.getId() + "/modeling-submission-without-assessment",
                HttpStatus.OK, ModelingSubmission.class, params);
        // verify that no new submission was created
        assertThat(submissionWithoutFirstAssessment).isEqualTo(submission);
        // verify that the lock has been set
        assertThat(submissionWithoutFirstAssessment.getLatestResult()).isNotNull();
        assertThat(submissionWithoutFirstAssessment.getLatestResult().getAssessor().getLogin()).isEqualTo("tutor1");
        assertThat(submissionWithoutFirstAssessment.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.MANUAL);

        // make sure that new result correctly appears inside the continue box
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1Tutor1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1Tutor1.add("assessedByTutor", "true");
        paramsGetAssessedCR1Tutor1.add("correction-round", "0");
        var assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList.size()).isEqualTo(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(0)).isEqualTo(submissionWithoutFirstAssessment.getLatestResult());

        // assess submission and submit
        List<Feedback> feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here")).collect(Collectors.toList());
        params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        final var firstSubmittedManualResult = request.putWithResponseBodyAndParams(
                API_MODELING_SUBMISSIONS + submissionWithoutFirstAssessment.getId() + "/result/" + submissionWithoutFirstAssessment.getFirstResult().getId() + "/assessment",
                feedbacks, Result.class, HttpStatus.OK, params);

        // make sure that new result correctly appears after the assessment for first correction round
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList.size()).isEqualTo(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(0)).isNotNull();
        assertThat(firstSubmittedManualResult.getAssessor().getLogin()).isEqualTo("tutor1");

        // verify that the result contains the relationship
        assertThat(firstSubmittedManualResult).isNotNull();
        assertThat(firstSubmittedManualResult.getParticipation()).isEqualTo(studentParticipation);

        // verify that the relationship between student participation,
        var databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation.isPresent()).isTrue();
        var fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions().size()).isEqualTo(1);
        assertThat(fetchedParticipation.findLatestSubmission().isPresent()).isTrue();
        assertThat(fetchedParticipation.findLatestSubmission().get()).isEqualTo(submissionWithoutFirstAssessment);
        assertThat(fetchedParticipation.findLatestResult()).isEqualTo(firstSubmittedManualResult);

        var databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository
                .findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission.size()).isEqualTo(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.get(0);
        assertThat(fetchedParticipation.getSubmissions().size()).isEqualTo(1);
        assertThat(fetchedParticipation.findLatestSubmission().isPresent()).isTrue();
        // it should contain the lock for the manual result
        assertThat(fetchedParticipation.findLatestSubmission().get().getResults().size()).isEqualTo(1);
        assertThat(fetchedParticipation.findLatestSubmission().get().getLatestResult()).isEqualTo(firstSubmittedManualResult);

        // SECOND ROUND OF CORRECTION

        database.changeUser("tutor2");
        LinkedMultiValueMap<String, String> paramsSecondCorrection = new LinkedMultiValueMap<>();
        paramsSecondCorrection.add("lock", "true");
        paramsSecondCorrection.add("correction-round", "1");

        final var submissionWithoutSecondAssessment = request.get("/api/exercises/" + exerciseWithParticipation.getId() + "/modeling-submission-without-assessment", HttpStatus.OK,
                ModelingSubmission.class, paramsSecondCorrection);

        // verify that the submission is not new
        assertThat(submissionWithoutSecondAssessment).isEqualTo(submission);
        // verify that the lock has been set
        assertThat(submissionWithoutSecondAssessment.getLatestResult()).isNotNull();
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessor().getLogin()).isEqualTo("tutor2");
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.MANUAL);

        // verify that the relationship between student participation,
        databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation.isPresent()).isTrue();
        fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions().size()).isEqualTo(1);
        assertThat(fetchedParticipation.findLatestSubmission().isPresent()).isTrue();
        assertThat(fetchedParticipation.findLatestSubmission().get()).isEqualTo(submissionWithoutSecondAssessment);
        assertThat(fetchedParticipation.getResults().stream().filter(x -> x.getCompletionDate() == null).findFirst().get())
                .isEqualTo(submissionWithoutSecondAssessment.getLatestResult());

        databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission.size()).isEqualTo(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.get(0);
        assertThat(fetchedParticipation.getSubmissions().size()).isEqualTo(1);
        assertThat(fetchedParticipation.findLatestSubmission().isPresent()).isTrue();
        assertThat(fetchedParticipation.findLatestSubmission().get().getResults().size()).isEqualTo(2);
        assertThat(fetchedParticipation.findLatestSubmission().get().getLatestResult()).isEqualTo(submissionWithoutSecondAssessment.getLatestResult());

        // assess submission and submit
        feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here")).collect(Collectors.toList());
        params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        final var secondSubmittedManualResult = request.putWithResponseBodyAndParams(
                API_MODELING_SUBMISSIONS + submissionWithoutFirstAssessment.getId() + "/result/" + submissionWithoutSecondAssessment.getResults().get(1).getId() + "/assessment",
                feedbacks, Result.class, HttpStatus.OK, params);
        assertThat(secondSubmittedManualResult).isNotNull();

        // make sure that new result correctly appears after the assessment for second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR2 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR2.add("assessedByTutor", "true");
        paramsGetAssessedCR2.add("correction-round", "1");
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class,
                paramsGetAssessedCR2);

        assertThat(assessedSubmissionList.size()).isEqualTo(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutSecondAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(1)).isEqualTo(secondSubmittedManualResult);

        // make sure that they do not appear for the first correction round as the tutor only assessed the second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1.add("assessedByTutor", "true");
        paramsGetAssessedCR1.add("correction-round", "0");
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class,
                paramsGetAssessedCR1);

        assertThat(assessedSubmissionList.size()).isEqualTo(0);
    }

    private void assessmentDueDatePassed() {
        database.updateAssessmentDueDate(classExercise.getId(), ZonedDateTime.now().minusSeconds(10));
    }

    private void overrideAssessment(String student, String originalAssessor, HttpStatus httpStatus, String submit, boolean originalAssessmentSubmitted) throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        submission = database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, student, originalAssessor);
        submission.getLatestResult().setCompletionDate(originalAssessmentSubmitted ? ZonedDateTime.now() : null);
        resultRepo.save(submission.getLatestResult());
        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", submit);
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.putWithResponseBodyAndParams(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + submission.getLatestResult().getId() + "/assessment", feedbacks,
                Result.class, httpStatus, params);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "ADMIN")
    public void overrideAssessment_inFirstCorrectionRoundByInstructor() throws Exception {
        String student = "student1";
        String originalAssessor = "tutor1";
        HttpStatus httpStatus = HttpStatus.OK;
        String submit = "true";

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        submission = database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, student, originalAssessor);

        Result newResult = database.addResultToSubmission(submission, AssessmentType.MANUAL, database.getUserByLogin("tutor2"), null, null, true, null).getLatestResult();

        resultRepo.save(submission.getLatestResult());
        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", submit);
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.putWithResponseBodyAndParams(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + newResult.getId() + "/assessment", feedbacks, Result.class, httpStatus,
                params);
    }

}
