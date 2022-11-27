package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.*;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingSubmissionElement;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AssessmentService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ModelFactory;

class ModelingAssessmentIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    public static final String API_MODELING_SUBMISSIONS = "/api/modeling-submissions/";

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ModelingSubmissionRepository modelingSubmissionRepo;

    @Autowired
    private ResultRepository resultRepo;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private ExampleSubmissionRepository exampleSubmissionRepository;

    @Autowired
    private CompassService compassService;

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ComplaintResponseRepository complaintResponseRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    private ModelingExercise classExercise;

    private ModelingExercise activityExercise;

    private ModelingExercise objectExercise;

    private ModelingExercise useCaseExercise;

    private ModelingExercise communicationExercise;

    private ModelingExercise componentExercise;

    private ModelingExercise deploymentExercise;

    private ModelingExercise petriNetExercise;

    private ModelingExercise syntaxTreeExercise;

    private ModelingExercise flowchartExercise;

    private ModelingSubmission modelingSubmission;

    private Result modelingAssessment;

    private String validModel;

    private Course course;

    private final double offsetByTenThousandth = 0.0001;

    @BeforeEach
    void initTestCase() throws Exception {
        database.addUsers(6, 2, 0, 1);
        this.course = database.addCourseWithDifferentModelingExercises();
        classExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        activityExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ActivityDiagram");
        objectExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ObjectDiagram");
        useCaseExercise = database.findModelingExerciseWithTitle(course.getExercises(), "UseCaseDiagram");
        communicationExercise = database.findModelingExerciseWithTitle(course.getExercises(), "CommunicationDiagram");
        componentExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ComponentDiagram");
        deploymentExercise = database.findModelingExerciseWithTitle(course.getExercises(), "DeploymentDiagram");
        petriNetExercise = database.findModelingExerciseWithTitle(course.getExercises(), "PetriNet");
        syntaxTreeExercise = database.findModelingExerciseWithTitle(course.getExercises(), "SyntaxTree");
        flowchartExercise = database.findModelingExerciseWithTitle(course.getExercises(), "Flowchart");

        validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetAssessmentBySubmissionId() throws Exception {
        saveModelingSubmissionAndAssessment(true);
        List<Feedback> feedback = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.v2.json");
        database.updateAssessmentDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        Result result = request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.OK, Result.class);

        checkAssessmentFinished(result, null);
        database.checkFeedbackCorrectlyStored(feedback, result.getFeedbacks(), FeedbackType.MANUAL);
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetAssessmentBySubmissionId_notFound() throws Exception {
        saveModelingSubmission();
        request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.NOT_FOUND, Result.class);
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetAssessmentBySubmissionId_assessmentNotFinished_forbidden() throws Exception {
        saveModelingSubmissionAndAssessment(false);
        database.updateAssessmentDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = "student1")
    void testGetAssessmentBySubmissionId_assessmentDueDateNotOver_forbidden() throws Exception {
        saveModelingSubmissionAndAssessment(true);

        request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = "student2")
    void testGetAssessmentBySubmissionId_studentNotOwnerOfSubmission_forbidden() throws Exception {
        saveModelingSubmissionAndAssessment(true);
        database.updateAssessmentDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test()
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetExampleAssessmentAsTutor() throws Exception {
        ExampleSubmission storedExampleSubmission = database.addExampleSubmission(database.generateExampleSubmission(validModel, classExercise, true, true));
        List<Feedback> feedbackList = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        Result storedResult = request.putWithResponseBody("/api/modeling-submissions/" + storedExampleSubmission.getId() + "/example-assessment", feedbackList, Result.class,
                HttpStatus.OK);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
        assertThat(exampleSubmissionRepository.findById(storedExampleSubmission.getId())).isPresent();
        var result = request.get("/api/exercise/" + classExercise.getId() + "/modeling-submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-assessment",
                HttpStatus.OK, Result.class);
        for (Feedback feedback : result.getFeedbacks()) {
            assertThat(feedback.getCredits()).isNull();
            assertThat(feedback.getDetailText()).isNull();
            assertThat(feedback.getReference()).isNotNull();
        }
    }

    @Test()
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetExampleAssessmentAsTutorNoTutorial() throws Exception {
        ExampleSubmission storedExampleSubmission = database.addExampleSubmission(database.generateExampleSubmission(validModel, classExercise, true, false));
        List<Feedback> feedbackList = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        Result storedResult = request.putWithResponseBody("/api/modeling-submissions/" + storedExampleSubmission.getId() + "/example-assessment", feedbackList, Result.class,
                HttpStatus.OK);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
        assertThat(exampleSubmissionRepository.findById(storedExampleSubmission.getId())).isPresent();
        request.get("/api/exercise/" + classExercise.getId() + "/modeling-submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-assessment", HttpStatus.OK,
                Result.class);
    }

    @Test()
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetExampleAssessmentAsInstructor() throws Exception {
        ExampleSubmission storedExampleSubmission = database.addExampleSubmission(database.generateExampleSubmission(validModel, classExercise, true, true));
        List<Feedback> feedbackList = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        Result storedResult = request.putWithResponseBody("/api/modeling-submissions/" + storedExampleSubmission.getId() + "/example-assessment", feedbackList, Result.class,
                HttpStatus.OK);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
        assertThat(exampleSubmissionRepository.findById(storedExampleSubmission.getId())).isPresent();
        request.get("/api/exercise/" + classExercise.getId() + "/modeling-submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-assessment", HttpStatus.OK,
                Result.class);
    }

    @Test
    @WithMockUser(username = "student1")
    void testManualAssessmentSubmitAsStudent() throws Exception {
        ModelingSubmission submission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.FORBIDDEN);
        Optional<Result> storedResult = resultRepo.findDistinctBySubmissionId(submission.getId());
        assertThat(storedResult).as("result is not saved").isNotPresent();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testManualAssessmentSave() throws Exception {
        User assessor = database.getUserByLogin("tutor1");
        ModelingSubmission submission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        createAssessment(submission, feedbacks, "/assessment", HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        database.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentNotFinished(storedResult, assessor);
        assertThat(storedResult.getParticipation()).isNotNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testManualAssessmentSave_noCourse() throws Exception {
        classExercise.setCourse(null);
        exerciseRepo.save(classExercise);
        ModelingSubmission submission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        createAssessment(submission, feedbacks, "/assessment", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testManualAssessmentSubmit_classDiagram() throws Exception {
        User assessor = database.getUserByLogin("tutor1");
        ModelingSubmission submission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        database.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
        assertThat(storedResult.getParticipation()).isNotNull();

        Course course = request.get("/api/courses/" + this.course.getId() + "/for-assessment-dashboard", HttpStatus.OK, Course.class);
        Exercise exercise = database.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()).hasSize(1);
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(1L);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testManualAssessmentSubmit_activityDiagram() throws Exception {
        User assessor = database.getUserByLogin("tutor1");
        ModelingSubmission submission = database.addModelingSubmissionFromResources(activityExercise, "test-data/model-submission/example-activity-diagram.json", "student1");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/example-activity-assessment.json");
        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        database.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
        assertThat(storedResult.getParticipation()).isNotNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testManualAssessmentSubmit_objectDiagram() throws Exception {
        User assessor = database.getUserByLogin("tutor1");
        ModelingSubmission submission = database.addModelingSubmissionFromResources(objectExercise, "test-data/model-submission/object-model.json", "student1");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/object-assessment.json");
        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        database.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testManualAssessmentSubmit_useCaseDiagram() throws Exception {
        User assessor = database.getUserByLogin("tutor1");
        ModelingSubmission submission = database.addModelingSubmissionFromResources(useCaseExercise, "test-data/model-submission/use-case-model.json", "student1");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/use-case-assessment.json");
        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        database.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
        assertThat(storedResult.getParticipation()).isNotNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testManualAssessmentSaveAndSubmit() throws Exception {
        User assessor = database.getUserByLogin("tutor1");
        ModelingSubmission submission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");

        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        createAssessment(submission, feedbacks, "/assessment", HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        database.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentNotFinished(storedResult, assessor);

        feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.v2.json");
        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        database.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
        assertThat(storedResult.getParticipation()).isNotNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testManualAssessmentSubmit_IncludedCompletelyWithBonusPointsExercise() throws Exception {
        // setting up exercise
        useCaseExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        useCaseExercise.setMaxPoints(10.0);
        useCaseExercise.setBonusPoints(10.0);
        exerciseRepo.save(useCaseExercise);

        // setting up student submission
        ModelingSubmission submission = database.addModelingSubmissionFromResources(useCaseExercise, "test-data/model-submission/use-case-model.json", "student1");
        List<Feedback> feedbacks = new ArrayList<>();

        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 0.0, 0D);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, -1.0, 0D);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 1.0, 0D);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 50D);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 100D);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 150D);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 200D);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 200D);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testManualAssessmentSubmit_IncludedCompletelyWithoutBonusPointsExercise() throws Exception {
        // setting up exercise
        useCaseExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        useCaseExercise.setMaxPoints(10.0);
        useCaseExercise.setBonusPoints(0.0);
        exerciseRepo.save(useCaseExercise);

        // setting up student submission
        ModelingSubmission submission = database.addModelingSubmissionFromResources(useCaseExercise, "test-data/model-submission/use-case-model.json", "student1");
        List<Feedback> feedbacks = new ArrayList<>();

        setupStudentSubmissions(submission, feedbacks);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testManualAssessmentSubmit_IncludedAsBonusExercise() throws Exception {
        // setting up exercise
        useCaseExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        useCaseExercise.setMaxPoints(10.0);
        useCaseExercise.setBonusPoints(0.0);
        exerciseRepo.save(useCaseExercise);

        // setting up student submission
        ModelingSubmission submission = database.addModelingSubmissionFromResources(useCaseExercise, "test-data/model-submission/use-case-model.json", "student1");
        List<Feedback> feedbacks = new ArrayList<>();

        setupStudentSubmissions(submission, feedbacks);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testManualAssessmentSubmit_NotIncludedExercise() throws Exception {
        // setting up exercise
        useCaseExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        useCaseExercise.setMaxPoints(10.0);
        useCaseExercise.setBonusPoints(0.0);
        exerciseRepo.save(useCaseExercise);

        // setting up student submission
        ModelingSubmission submission = database.addModelingSubmissionFromResources(useCaseExercise, "test-data/model-submission/use-case-model.json", "student1");
        List<Feedback> feedbacks = new ArrayList<>();

        setupStudentSubmissions(submission, feedbacks);
    }

    private void setupStudentSubmissions(ModelingSubmission submission, List<Feedback> feedbacks) throws Exception {
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 0.0, 0D);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, -1.0, 0D);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 1.0, 0D);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 50D);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 100D);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 100D);
    }

    private void addAssessmentFeedbackAndCheckScore(ModelingSubmission submission, List<Feedback> feedbacks, double pointsAwarded, Double expectedScore) throws Exception {
        feedbacks.add(new Feedback().credits(pointsAwarded).type(FeedbackType.MANUAL_UNREFERENCED).detailText("gj"));
        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);
        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();
        assertThat(storedResult.getScore()).isEqualTo(expectedScore, Offset.offset(offsetByTenThousandth));
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testManualAssessmentSubmit_withResultOver100Percent() throws Exception {
        useCaseExercise = (ModelingExercise) database.addMaxScoreAndBonusPointsToExercise(useCaseExercise);
        ModelingSubmission submission = database.addModelingSubmissionFromResources(useCaseExercise, "test-data/model-submission/use-case-model.json", "student1");

        List<Feedback> feedbacks = new ArrayList<>();
        // Check that result is over 100% -> 105
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 2"));

        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        Result storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();

        assertThat(storedResult.getScore()).isEqualTo(105);

        // Check that result is capped to maximum of maxScore + bonus points -> 110
        feedbacks.add(new Feedback().credits(20.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 3"));

        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).get();
        storedResult = resultRepo.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).get();

        assertThat(storedResult.getScore()).isEqualTo(110, Offset.offset(offsetByTenThousandth));
    }

    // region Automatic Assessment Tests
    @Test
    @WithMockUser(username = "student2")
    void testAutomaticAssessmentUponModelSubmission_identicalModel() throws Exception {
        saveModelingSubmissionAndAssessment(true);
        database.createAndSaveParticipationForExercise(classExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.cpy.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Result automaticResult = compassService.getSuggestionResult(storedSubmission, classExercise);
        assertThat(automaticResult).as("automatic result is not created").isNull();
    }

    /**
     * Tests that a submission without model (can happen for exam submissions) does not throw an exception and does
     * not create an automatic result.
     * @throws Exception any exception in the test
     */
    @Test
    @WithMockUser(username = "student2")
    void testAutomaticAssessmentUponModelSubmission_emptyModel() throws Exception {
        database.createAndSaveParticipationForExercise(classExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(null, true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Result automaticResult = compassService.getSuggestionResult(storedSubmission, classExercise);
        assertThat(automaticResult).as("automatic result is not created").isNull();
    }

    @Test
    @WithMockUser(username = "student2")
    void testAutomaticAssessmentUponModelSubmission_activityDiagram_identicalModel() throws Exception {
        saveModelingSubmissionAndAssessment_activityDiagram(true);
        database.createAndSaveParticipationForExercise(activityExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/example-activity-diagram.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + activityExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Result automaticResult = compassService.getSuggestionResult(storedSubmission, activityExercise);
        assertThat(automaticResult).as("automatic result is not created").isNull();
    }

    @Test
    @WithMockUser(username = "student2")
    void testAutomaticAssessmentUponModelSubmission_objectDiagram_identicalModel() throws Exception {
        saveModelingSubmissionAndAssessment_activityDiagram(true);
        database.createAndSaveParticipationForExercise(activityExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/example-activity-diagram.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + activityExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Result automaticResult = compassService.getSuggestionResult(storedSubmission, activityExercise);
        assertThat(automaticResult).as("automatic result is not created").isNull();
    }

    @Test
    @WithMockUser(username = "student2")
    void testAutomaticAssessmentUponModelSubmission_partialModel() throws Exception {
        saveModelingSubmissionAndAssessment(true);
        database.createAndSaveParticipationForExercise(classExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.partial.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Result automaticResult = compassService.getSuggestionResult(storedSubmission, classExercise);
        assertThat(automaticResult).as("automatic result is not created").isNull();
    }

    @Test
    @WithMockUser(username = "student2")
    void testAutomaticAssessmentUponModelSubmission_partialModelExists() throws Exception {
        modelingSubmission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.partial.json", "student1");
        modelingAssessment = database.addModelingAssessmentForSubmission(classExercise, modelingSubmission, "test-data/model-assessment/assessment.54727.partial.json", "tutor1",
                true);
        database.createAndSaveParticipationForExercise(classExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Result automaticResult = compassService.getSuggestionResult(storedSubmission, classExercise);
        assertThat(automaticResult).as("automatic result is not created").isNull();
    }

    @Test
    @WithMockUser(username = "student2")
    void testAutomaticAssessmentUponModelSubmission_noSimilarity() throws Exception {
        modelingSubmission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54745.json", "student1");
        database.addModelingAssessmentForSubmission(classExercise, modelingSubmission, "test-data/model-assessment/assessment.54745.json", "tutor1", true);
        database.createAndSaveParticipationForExercise(classExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Result automaticResult = compassService.getSuggestionResult(storedSubmission, classExercise);
        assertThat(automaticResult).as("automatic result is not created").isNull();
    }

    @Test
    @WithMockUser(username = "student2")
    void testAutomaticAssessmentUponModelSubmission_similarElementsWithinModel() throws Exception {
        modelingSubmission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.inheritance.json"), true);
        modelingSubmission = database.addModelingSubmission(classExercise, modelingSubmission, "student1");
        modelingAssessment = database.addModelingAssessmentForSubmission(classExercise, modelingSubmission, "test-data/model-assessment/assessment.inheritance.json", "tutor1",
                true);
        database.createAndSaveParticipationForExercise(classExercise, "student2");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.inheritance.cpy.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        Result automaticResult = compassService.getSuggestionResult(storedSubmission, classExercise);
        assertThat(automaticResult).as("automatic result is not created").isNull();
    }

    @Test
    @WithMockUser(username = "student2")
    void testAutomaticAssessmentUponModelSubmission_noResultInDatabase() throws Exception {
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
    void testConfidenceThreshold() throws Exception {
        Feedback feedbackOnePoint = new Feedback().credits(1.0).reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        Feedback feedbackTwentyPoints = new Feedback().credits(20.0).reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student2");
        ModelingSubmission submission3 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student3");
        ModelingSubmission submission4 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student4");
        ModelingSubmission submission5 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student5");
        ModelingSubmission submissionToCheck = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student6");

        compassService.build(classExercise);

        createAssessment(submission1, Collections.singletonList(feedbackTwentyPoints.text("wrong text")), "/assessment?submit=true", HttpStatus.OK);

        Result automaticResult = compassService.getSuggestionResult(submissionToCheck, classExercise);
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks()).as("element is assessed automatically").hasSize(1);
        assertThat(automaticResult.getFeedbacks().get(0).getCredits()).as("credits of element are correct").isEqualTo(20);
        assertThat(automaticResult.getFeedbacks().get(0).getText()).as("feedback text of element is correct").isEqualTo("wrong text");

        createAssessment(submission2, Collections.singletonList(feedbackOnePoint.text("long feedback text")), "/assessment?submit=true", HttpStatus.OK);

        automaticResult = compassService.getSuggestionResult(submissionToCheck, classExercise);
        assertThat(automaticResult).as("automatic result was not created").isNull();

        createAssessment(submission3, Collections.singletonList(feedbackOnePoint.text("short text")), "/assessment?submit=true", HttpStatus.OK);

        automaticResult = compassService.getSuggestionResult(submissionToCheck, classExercise);
        assertThat(automaticResult).as("automatic result was not created").isNull();

        createAssessment(submission4, Collections.singletonList(feedbackOnePoint.text("very long feedback text")), "/assessment?submit=true", HttpStatus.OK);

        automaticResult = compassService.getSuggestionResult(submissionToCheck, classExercise);
        assertThat(automaticResult).as("automatic result was not created").isNull();

        createAssessment(submission5, Collections.singletonList(feedbackOnePoint.text("medium text")), "/assessment?submit=true", HttpStatus.OK);

        automaticResult = compassService.getSuggestionResult(submissionToCheck, classExercise);
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks()).as("element is assessed automatically").hasSize(1);
        assertThat(automaticResult.getFeedbacks().get(0).getCredits()).as("credits of element are correct").isEqualTo(1);
        assertThat(automaticResult.getFeedbacks().get(0).getText()).as("feedback text of element is correct").isEqualTo("very long feedback text");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testLongestFeedbackTextSelection() throws Exception {
        Feedback feedbackOnePoint = new Feedback().credits(1.0).reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student2");
        ModelingSubmission submission3 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student3");
        ModelingSubmission submissionToCheck = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student4");

        compassService.build(classExercise);

        createAssessment(submission1, Collections.singletonList(feedbackOnePoint.text("feedback text")), "/assessment?submit=true", HttpStatus.OK);

        Result automaticResult = compassService.getSuggestionResult(submissionToCheck, classExercise);
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks()).as("element is assessed automatically").hasSize(1);
        assertThat(automaticResult.getFeedbacks().get(0).getText()).as("feedback text of element is correct").isEqualTo("feedback text");

        createAssessment(submission2, Collections.singletonList(feedbackOnePoint.text("short")), "/assessment?submit=true", HttpStatus.OK);

        automaticResult = compassService.getSuggestionResult(submissionToCheck, classExercise);
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks()).as("element is assessed automatically").hasSize(1);
        assertThat(automaticResult.getFeedbacks().get(0).getText()).as("feedback text of element is correct").isEqualTo("feedback text");

        createAssessment(submission3, Collections.singletonList(feedbackOnePoint.text("very long feedback text")), "/assessment?submit=true", HttpStatus.OK);

        automaticResult = compassService.getSuggestionResult(submissionToCheck, classExercise);
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks()).as("element is assessed automatically").hasSize(1);
        assertThat(automaticResult.getFeedbacks().get(0).getText()).as("feedback text of element is correct").isEqualTo("very long feedback text");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAutomaticAssessmentUponAssessmentSubmission() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.cpy.json", "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        compassService.build(classExercise);

        createAssessment(submission1, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        Result storedResultOfSubmission2 = compassService.getSuggestionResult(submission2, classExercise);
        assertThat(storedResultOfSubmission2).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(storedResultOfSubmission2);
        database.checkFeedbackCorrectlyStored(feedbacks, storedResultOfSubmission2.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAutomaticAssessmentActivityDiagramUponAssessmentSubmission() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(activityExercise, "test-data/model-submission/example-activity-diagram.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(activityExercise, "test-data/model-submission/example-activity-diagram-cpy.json", "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/example-activity-assessment.json");

        compassService.build(activityExercise);

        createAssessment(submission1, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        Result storedResultOfSubmission2 = compassService.getSuggestionResult(submission2, activityExercise);
        assertThat(storedResultOfSubmission2).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(storedResultOfSubmission2);
        database.checkFeedbackCorrectlyStored(feedbacks, storedResultOfSubmission2.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAutomaticAssessmentObjectDiagramUponAssessmentSubmission() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(objectExercise, "test-data/model-submission/example-object-diagram.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(objectExercise, "test-data/model-submission/example-object-diagram.json", "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/example-object-assessment.json");

        compassService.build(objectExercise);

        createAssessment(submission1, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        Result storedResultOfSubmission2 = compassService.getSuggestionResult(submission2, objectExercise);
        assertThat(storedResultOfSubmission2).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(storedResultOfSubmission2);
        database.checkFeedbackCorrectlyStored(feedbacks, storedResultOfSubmission2.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAutomaticAssessmentUseCaseDiagramUponAssessmentSubmission() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(useCaseExercise, "test-data/model-submission/use-case-model.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(useCaseExercise, "test-data/model-submission/use-case-model.json", "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/use-case-assessment.json");

        compassService.build(useCaseExercise);

        createAssessment(submission1, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        Result storedResultOfSubmission2 = compassService.getSuggestionResult(submission2, useCaseExercise);
        assertThat(storedResultOfSubmission2).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(storedResultOfSubmission2);
        database.checkFeedbackCorrectlyStored(feedbacks, storedResultOfSubmission2.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAutomaticAssessmentCommunicationDiagramUponAssessmentSubmission() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(communicationExercise, "test-data/model-submission/example-communication-diagram.json",
                "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(communicationExercise, "test-data/model-submission/example-communication-diagram-cpy.json",
                "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/example-communication-assessment.json");

        compassService.build(communicationExercise);

        createAssessment(submission1, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        Result storedResultOfSubmission2 = compassService.getSuggestionResult(submission2, communicationExercise);
        assertThat(storedResultOfSubmission2).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(storedResultOfSubmission2);
        database.checkFeedbackCorrectlyStored(feedbacks, storedResultOfSubmission2.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAutomaticAssessmentComponentDiagramUponAssessmentSubmission() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(componentExercise, "test-data/model-submission/example-component-diagram.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(componentExercise, "test-data/model-submission/example-component-diagram-cpy.json",
                "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/example-component-assessment.json");

        compassService.build(componentExercise);

        createAssessment(submission1, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        Result storedResultOfSubmission2 = compassService.getSuggestionResult(submission2, componentExercise);
        assertThat(storedResultOfSubmission2).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(storedResultOfSubmission2);
        database.checkFeedbackCorrectlyStored(feedbacks, storedResultOfSubmission2.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAutomaticAssessmentDeploymentDiagramUponAssessmentSubmission() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(deploymentExercise, "test-data/model-submission/example-deployment-diagram.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(deploymentExercise, "test-data/model-submission/example-deployment-diagram-cpy.json",
                "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/example-deployment-assessment.json");

        compassService.build(deploymentExercise);

        createAssessment(submission1, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        Result storedResultOfSubmission2 = compassService.getSuggestionResult(submission2, deploymentExercise);
        assertThat(storedResultOfSubmission2).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(storedResultOfSubmission2);
        database.checkFeedbackCorrectlyStored(feedbacks, storedResultOfSubmission2.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAutomaticAssessmentPetriNetDiagramUponAssessmentSubmission() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(petriNetExercise, "test-data/model-submission/example-petri-net-diagram.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(petriNetExercise, "test-data/model-submission/example-petri-net-diagram-cpy.json", "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/example-petri-net-assessment.json");

        compassService.build(petriNetExercise);

        createAssessment(submission1, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        Result storedResultOfSubmission2 = compassService.getSuggestionResult(submission2, petriNetExercise);
        assertThat(storedResultOfSubmission2).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(storedResultOfSubmission2);
        database.checkFeedbackCorrectlyStored(feedbacks, storedResultOfSubmission2.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAutomaticAssessmentSyntaxTreeDiagramUponAssessmentSubmission() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(syntaxTreeExercise, "test-data/model-submission/example-syntax-tree-diagram.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(syntaxTreeExercise, "test-data/model-submission/example-syntax-tree-diagram-cpy.json",
                "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/example-syntax-tree-assessment.json");

        compassService.build(syntaxTreeExercise);

        createAssessment(submission1, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        Result storedResultOfSubmission2 = compassService.getSuggestionResult(submission2, syntaxTreeExercise);
        assertThat(storedResultOfSubmission2).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(storedResultOfSubmission2);
        database.checkFeedbackCorrectlyStored(feedbacks, storedResultOfSubmission2.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAutomaticAssessmentFlowchartDiagramUponAssessmentSubmission() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(flowchartExercise, "test-data/model-submission/example-flowchart-diagram.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(flowchartExercise, "test-data/model-submission/example-flowchart-diagram-cpy.json",
                "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/example-flowchart-assessment.json");

        compassService.build(flowchartExercise);

        createAssessment(submission1, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        Result storedResultOfSubmission2 = compassService.getSuggestionResult(submission2, flowchartExercise);
        assertThat(storedResultOfSubmission2).as("automatic result is created").isNotNull();
        checkAutomaticAssessment(storedResultOfSubmission2);
        database.checkFeedbackCorrectlyStored(feedbacks, storedResultOfSubmission2.getFeedbacks(), FeedbackType.AUTOMATIC);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAutomaticAssessmentUponAssessmentSubmission_noResultInDatabase() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.cpy.json", "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        compassService.build(classExercise);

        createAssessment(submission1, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        Optional<Result> automaticResult = resultRepo.findDistinctWithFeedbackBySubmissionId(submission2.getId());
        assertThat(automaticResult).as("automatic result not stored in database").isNotPresent();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testNoAutomaticAssessmentWhenNotBuild() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.cpy.json", "student2");
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        createAssessment(submission1, feedbacks, "/assessment", HttpStatus.OK);

        Result storedResultOfSubmission2 = compassService.getSuggestionResult(submission2, classExercise);
        assertThat(storedResultOfSubmission2).as("no automatic result has been created").isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAutomaticAssessment_elementsWithDifferentContextInSameSimilaritySet() throws Exception {
        List<Feedback> assessment1 = database.loadAssessmentFomResources("test-data/model-assessment/assessment.different-context.json");
        List<Feedback> assessment2 = database.loadAssessmentFomResources("test-data/model-assessment/assessment.different-context.automatic.json");
        ModelingSubmission submission1 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.different-context.json", "student1");
        ModelingSubmission submission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.different-context.json", "student2");
        ModelingSubmission submissionToCheck = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.different-context.json", "student3");

        compassService.build(classExercise);

        createAssessment(submission1, assessment1, "/assessment?submit=true", HttpStatus.OK);

        Result automaticResult = compassService.getSuggestionResult(submissionToCheck, classExercise);
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks()).as("all elements got assessed automatically").hasSize(4);

        createAssessment(submission2, assessment2, "/assessment?submit=true", HttpStatus.OK);

        automaticResult = compassService.getSuggestionResult(submissionToCheck, classExercise);
        assertThat(automaticResult).as("automatic result was created").isNotNull();
        assertThat(automaticResult.getFeedbacks()).as("not all elements got assessed automatically").hasSize(2);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideAutomaticAssessment() throws Exception {
        classExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        exerciseRepo.save(classExercise);
        modelingSubmission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.partial.json", "student1");
        modelingAssessment = database.addModelingAssessmentForSubmission(classExercise, modelingSubmission, "test-data/model-assessment/assessment.54727.partial.json", "tutor1",
                true);
        database.createAndSaveParticipationForExercise(classExercise, "tutor1");

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        compassService.build(classExercise);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        params.add("correction-round", "0");
        ModelingSubmission submissionWithAutomaticAssessment = request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK,
                ModelingSubmission.class, params);

        assertThat(submissionWithAutomaticAssessment.getId()).isEqualTo(storedSubmission.getId());

        Result resultWithFeedback = submissionWithAutomaticAssessment.getLatestResult();
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
        assertThat(manualFeedback).as("number of manual feedback elements is correct").hasSameSizeAs(newFeedback);
        assertThat(automaticFeedback).as("number of automatic feedback elements is correct").hasSize(existingFeedback.size() - 2);
        assertThat(adaptedFeedback).as("number of adapted feedback elements is correct").hasSize(2);
        assertThat(manualUnreferencedFeedback).as("number of manual unreferenced feedback elements is correct").isEmpty();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideAutomaticAssessment_existingManualAssessmentDoesNotChange() throws Exception {
        Feedback originalFeedback = new Feedback().credits(1.0).text("some feedback text").reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        Feedback changedFeedback = new Feedback().credits(2.0).text("another text").reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        modelingSubmission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student1");
        ModelingSubmission modelingSubmission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student2");
        createAssessment(modelingSubmission, Collections.singletonList(originalFeedback), "/assessment?submit=true", HttpStatus.OK);

        createAssessment(modelingSubmission2, Collections.singletonList(changedFeedback), "/assessment?submit=true", HttpStatus.OK);

        modelingAssessment = resultRepo.findDistinctWithFeedbackBySubmissionId(modelingSubmission2.getId()).get();
        assertThat(modelingAssessment.getFeedbacks()).as("assessment is correctly stored").hasSize(1);
        assertThat(modelingAssessment.getFeedbacks().get(0)).as("feedback credits and text are correct").isEqualToComparingOnlyGivenFields(changedFeedback, "credits", "text");
        modelingAssessment = resultRepo.findDistinctWithFeedbackBySubmissionId(modelingSubmission.getId()).get();
        assertThat(modelingAssessment.getFeedbacks()).as("existing manual assessment has correct amount of feedback").hasSize(1);
        assertThat(modelingAssessment.getFeedbacks().get(0)).as("existing manual assessment did not change").isEqualToComparingOnlyGivenFields(originalFeedback, "credits", "text");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideSubmittedManualAssessment_noConflict() throws Exception {
        Feedback originalFeedback = new Feedback().credits(1.0).text("some feedback text").reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        modelingSubmission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student1");
        ModelingSubmission modelingSubmission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student2");

        compassService.build(classExercise);

        createAssessment(modelingSubmission, Collections.singletonList(originalFeedback), "/assessment?submit=true", HttpStatus.OK);

        Result originalResult = resultRepo.findDistinctWithFeedbackBySubmissionId(modelingSubmission.getId()).get();
        Feedback changedFeedback = originalResult.getFeedbacks().get(0).credits(2.0).text("another text");
        request.put(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result/" + originalResult.getId() + "/assessment?submit=true",
                Collections.singletonList(changedFeedback), HttpStatus.OK);

        modelingAssessment = resultRepo.findDistinctWithFeedbackBySubmissionId(modelingSubmission.getId()).get();
        assertThat(modelingAssessment.getFeedbacks()).as("overridden assessment has correct amount of feedback").hasSize(1);
        assertThat(modelingAssessment.getFeedbacks().get(0)).as("feedback is properly overridden").isEqualToComparingOnlyGivenFields(changedFeedback, "credits", "text");
        modelingAssessment = compassService.getSuggestionResult(modelingSubmission2, classExercise);
        assertThat(modelingAssessment.getFeedbacks()).as("automatic assessment still exists").hasSize(1);
        assertThat(modelingAssessment.getFeedbacks().get(0)).as("automatic assessment is overridden properly").isEqualToComparingOnlyGivenFields(changedFeedback, "credits",
                "text");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideSubmittedManualAssessment_conflict() throws Exception {
        Feedback originalFeedback = new Feedback().credits(1.0).text("some feedback text").reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        Feedback originalFeedbackWithoutReference = new Feedback().credits(1.5).text("some feedback text again").reference(null).type(FeedbackType.MANUAL_UNREFERENCED);
        modelingSubmission = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student1");
        ModelingSubmission modelingSubmission2 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student2");
        ModelingSubmission modelingSubmission3 = database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json", "student3");

        compassService.build(classExercise);

        createAssessment(modelingSubmission, Arrays.asList(originalFeedback, originalFeedbackWithoutReference), "/assessment?submit=true", HttpStatus.OK);
        createAssessment(modelingSubmission2, Arrays.asList(originalFeedback, originalFeedbackWithoutReference), "/assessment?submit=true", HttpStatus.OK);

        Result originalResult = resultRepo.findDistinctWithFeedbackBySubmissionId(modelingSubmission.getId()).get();
        Feedback changedFeedback = originalResult.getFeedbacks().get(0).credits(2.0).text("another text");
        Feedback feedbackWithoutReference = new Feedback().credits(1.0).text("another feedback text again").reference(null).type(FeedbackType.MANUAL_UNREFERENCED);
        request.put(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result/" + originalResult.getId() + "/assessment?submit=true",
                Arrays.asList(changedFeedback, feedbackWithoutReference), HttpStatus.OK);

        modelingAssessment = resultRepo.findDistinctWithFeedbackBySubmissionId(modelingSubmission.getId()).get();
        assertThat(modelingAssessment.getFeedbacks()).as("overridden assessment has correct amount of feedback").hasSize(2);
        assertThat(modelingAssessment.getFeedbacks().get(0)).as("feedback is properly overridden").isEqualToComparingOnlyGivenFields(changedFeedback, "credits", "text");
        modelingAssessment = resultRepo.findDistinctWithFeedbackBySubmissionId(modelingSubmission2.getId()).get();
        assertThat(modelingAssessment.getFeedbacks()).as("existing submitted assessment still exists").hasSize(2);
        assertThat(modelingAssessment.getFeedbacks().get(0)).as("existing feedback is still the same").isEqualToComparingOnlyGivenFields(originalFeedback, "credits", "text");
        modelingAssessment = compassService.getSuggestionResult(modelingSubmission3, classExercise);
        assertThat(modelingAssessment).as("automatic assessment is not possible").isNull();

    }
    // endregion

    private void checkAssessmentNotFinished(Result storedResult, User assessor) {
        assertThat(storedResult.isRated() == null || !storedResult.isRated()).as("rated has not been set").isTrue();
        assertThat(storedResult.getScore()).as("score has not been calculated").isNull();
        assertThat(storedResult.getAssessor()).as("Assessor has been set").isEqualTo(assessor);
        assertThat(storedResult.getCompletionDate()).as("completion date has not been set").isNull();
    }

    private void checkAssessmentFinished(Result storedResult, User assessor) {
        assertThat(storedResult.isRated()).as("rated has been set").isTrue();
        assertThat(storedResult.getScore()).as("score has been calculated").isNotNull();
        assertThat(storedResult.getAssessor()).as("Assessor has been set").isEqualTo(assessor);
        assertThat(storedResult.getCompletionDate()).as("completion date has been set").isNotNull();
    }

    private void checkAutomaticAssessment(Result storedResult) {
        assertThat(storedResult.isRated() == null || !storedResult.isRated()).as("rated has not been set").isTrue();
        assertThat(storedResult.getScore()).as("score has not been calculated").isNull();
        assertThat(storedResult.getAssessor()).as("assessor has not been set").isNull();
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
        modelingAssessment = database.addModelingAssessmentForSubmission(activityExercise, modelingSubmission, "tutor1", false);
        request.put(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/cancel-assessment", null, expectedStatus);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void cancelOwnAssessmentAsStudent() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void cancelOwnAssessmentAsTutor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void cancelAssessmentOfOtherTutorAsTutor() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void cancelAssessmentOfOtherTutorAsInstructor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testOverrideAssessment_saveOtherTutorForbidden() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_saveInstructorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideAssessment_saveSameTutorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testOverrideAssessment_submitOtherTutorForbidden() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_submitInstructorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorPossible() throws Exception {
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testOverrideAssessment_saveOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_saveInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideAssessment_saveSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideAssessment_saveSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "false", false);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testOverrideAssessment_submitOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_submitInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment("student1", "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment("student1", "tutor1", HttpStatus.OK, "true", false);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void multipleCorrectionRoundsForExam() throws Exception {
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
        assertThat(optionalFetchedExercise).isPresent();
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

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(0)).isEqualTo(submissionWithoutFirstAssessment.getLatestResult());

        // assess submission and submit
        List<Feedback> feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here")).toList();
        params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        final var firstSubmittedManualResult = request.putWithResponseBodyAndParams(
                API_MODELING_SUBMISSIONS + submissionWithoutFirstAssessment.getId() + "/result/" + submissionWithoutFirstAssessment.getFirstResult().getId() + "/assessment",
                feedbacks, Result.class, HttpStatus.OK, params);

        // make sure that new result correctly appears after the assessment for first correction round
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(0)).isNotNull();
        assertThat(firstSubmittedManualResult.getAssessor().getLogin()).isEqualTo("tutor1");

        // verify that the result contains the relationship
        assertThat(firstSubmittedManualResult).isNotNull();
        assertThat(firstSubmittedManualResult.getParticipation()).isEqualTo(studentParticipation);

        // verify that the relationship between student participation,
        var databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerLegalSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        var fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).contains(submissionWithoutFirstAssessment);
        assertThat(fetchedParticipation.findLatestLegalResult()).isEqualTo(firstSubmittedManualResult);

        var databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository
                .findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.get(0);
        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).isPresent();
        // it should contain the lock for the manual result
        assertThat(fetchedParticipation.findLatestSubmission().get().getResults()).hasSize(1);
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
        databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerLegalSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).contains(submissionWithoutSecondAssessment);
        assertThat(fetchedParticipation.getResults().stream().filter(x -> x.getCompletionDate() == null).findFirst()).contains(submissionWithoutSecondAssessment.getLatestResult());

        databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.get(0);
        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).isPresent();
        assertThat(fetchedParticipation.findLatestSubmission().get().getResults()).hasSize(2);
        assertThat(fetchedParticipation.findLatestSubmission().get().getLatestResult()).isEqualTo(submissionWithoutSecondAssessment.getLatestResult());

        // assess submission and submit
        feedbacks = ModelFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here")).toList();
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

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.get(0).getId()).isEqualTo(submissionWithoutSecondAssessment.getId());
        assertThat(assessedSubmissionList.get(0).getResultForCorrectionRound(1)).isEqualTo(secondSubmittedManualResult);

        // make sure that they do not appear for the first correction round as the tutor only assessed the second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1.add("assessedByTutor", "true");
        paramsGetAssessedCR1.add("correction-round", "0");
        assessedSubmissionList = request.getList("/api/exercises/" + exerciseWithParticipation.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class,
                paramsGetAssessedCR1);

        assertThat(assessedSubmissionList).isEmpty();

        // Student should not have received a result over WebSocket as manual correction is ongoing
        verify(messagingTemplate, never()).convertAndSendToUser(notNull(), eq(Constants.NEW_RESULT_TOPIC), isA(Result.class));
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
    void overrideAssessment_inFirstCorrectionRoundByInstructor() throws Exception {
        String student = "student1";
        String originalAssessor = "tutor1";
        HttpStatus httpStatus = HttpStatus.OK;
        String submit = "true";

        ModelingSubmission submission = ModelFactory.generateModelingSubmission(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        submission = database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, student, originalAssessor);

        Result newResult = database.addResultToSubmission(submission, AssessmentType.MANUAL, database.getUserByLogin("tutor2"), null, true, null).getLatestResult();

        resultRepo.save(submission.getLatestResult());
        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", submit);
        List<Feedback> feedbacks = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        request.putWithResponseBodyAndParams(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + newResult.getId() + "/assessment", feedbacks, Result.class, httpStatus,
                params);
    }

    private void createAssessment(ModelingSubmission submission, List<Feedback> feedbacks, String urlEnding, HttpStatus expectedStatus) throws Exception {
        // id 0 so no result exists and a new one will be created
        request.put(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + 0 + urlEnding, feedbacks, expectedStatus);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void overrideAssessmentAfterComplaint() throws Exception {
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(ZonedDateTime.now().minusDays(5), ZonedDateTime.now().plusDays(5),
                ZonedDateTime.now().plusDays(8), DiagramType.ClassDiagram, course);
        modelingExercise.setMaxPoints(10.0);
        modelingExercise.setBonusPoints(0.0);
        modelingExercise = exerciseRepo.saveAndFlush(modelingExercise);

        // creating participation of student1 by starting the exercise
        User student1 = userRepo.findOneByLogin("student1").orElse(null);
        StudentParticipation studentParticipation = participationService.startExercise(modelingExercise, student1, false);

        // creating submission of student1
        ModelingSubmission submission = new ModelingSubmission();
        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        submission.setSubmitted(Boolean.TRUE);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission = submissionRepository.saveAndFlush(submission);

        // creating assessment by tutor1
        User tutor1 = userRepo.findOneByLogin("tutor1").orElse(null);
        Result firstResult = ModelFactory.generateResult(true, 50);
        firstResult.setAssessor(tutor1);
        firstResult.setHasComplaint(true);
        firstResult.setParticipation(studentParticipation);
        firstResult = resultRepo.saveAndFlush(firstResult);

        submission.addResult(firstResult);
        firstResult.setSubmission(submission);
        submission = submissionRepository.saveAndFlush(submission);

        // creating complaint by student 1
        Complaint complaint = new Complaint();
        complaint.setComplaintType(ComplaintType.COMPLAINT);
        complaint.setComplaintText("Unfair");
        complaint.setResult(firstResult);
        complaint.setAccepted(null);
        complaint.setSubmittedTime(null);
        complaint.setParticipant(student1);
        complaint = complaintRepository.saveAndFlush(complaint);

        // creating complaintResponse
        ComplaintResponse complaintResponse = new ComplaintResponse();
        complaintResponse.setComplaint(complaint);
        complaintResponse.getComplaint().setAccepted(true);
        complaintResponseRepository.saveAndFlush(complaintResponse);

        // could throw exception
        List<Feedback> feedback = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");  // 1,5/10 points
        AssessmentUpdate assessmentUpdate = new AssessmentUpdate().feedbacks(feedback).complaintResponse(complaintResponse);
        Result resultAfterComplaint = assessmentService.updateAssessmentAfterComplaint(submission.getLatestResult(), modelingExercise, assessmentUpdate);

        List<Feedback> overrideFeedback = database.loadAssessmentFomResources("test-data/model-assessment/assessment.54745.json"); // 4/10 points
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        Result overwrittenResult = request.putWithResponseBodyAndParams(
                API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + submission.getLatestResult().getId() + "/assessment", overrideFeedback, Result.class, HttpStatus.OK,
                params);

        assertThat(firstResult.getScore()).isEqualTo(50L); // first result was instantiated with a score of 50%
        assertThat(resultAfterComplaint.getScore()).isEqualTo(15L); // score after complaint evaluation got changed to 15%
        assertThat(overwrittenResult.getScore()).isEqualTo(40L); // the instructor overwrote the score to 40%
        assertThat(overwrittenResult.hasComplaint()).isFalse();

        // Also check that it's correctly saved in the database
        ModelingSubmission savedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).orElse(null);
        assertThat(savedSubmission).isNotNull();
        assertThat(savedSubmission.getLatestResult().getScore()).isEqualTo(40L);
        assertThat(savedSubmission.getFirstResult().hasComplaint()).isTrue();
        assertThat(savedSubmission.getLatestResult().hasComplaint()).isFalse();

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarismIdenticalLongModels() throws Exception {
        database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student1");
        database.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json", "student2");
        var path = "/api/modeling-exercises/" + classExercise.getId() + "/check-plagiarism";
        var result = request.get(path, HttpStatus.OK, ModelingPlagiarismResult.class, database.getDefaultPlagiarismOptions());
        assertThat(result.getComparisons()).hasSize(1);
        assertThat(result.getExercise().getId()).isEqualTo(classExercise.getId());

        PlagiarismComparison<ModelingSubmissionElement> comparison = result.getComparisons().iterator().next();

        assertThat(comparison.getSimilarity()).isEqualTo(100.0, Offset.offset(0.1));
        assertThat(comparison.getStatus()).isEqualTo(PlagiarismStatus.NONE);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testdeleteResult() throws Exception {
        Course course = database.addCourseWithOneExerciseAndSubmissions("modeling", 1, Optional.of(FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json")));
        Exercise exercise = exerciseRepository.findAllExercisesByCourseId(course.getId()).iterator().next();
        database.addAssessmentToExercise(exercise, database.getUserByLogin("tutor1"));
        database.addAssessmentToExercise(exercise, database.getUserByLogin("tutor2"));

        var submissions = database.getAllSubmissionsOfExercise(exercise);
        Submission submission = submissions.get(0);
        assertThat(submission.getResults()).hasSize(2);
        Result firstResult = submission.getResults().get(0);
        Result lastResult = submission.getLatestResult();
        request.delete("/api/participations/" + submission.getParticipation().getId() + "/modeling-submissions/" + submission.getId() + "/results/" + firstResult.getId(),
                HttpStatus.OK);
        submission = submissionRepository.findOneWithEagerResultAndFeedback(submission.getId());
        assertThat(submission.getResults()).hasSize(1);
        assertThat(submission.getResults().get(0)).isEqualTo(lastResult);
    }
}
