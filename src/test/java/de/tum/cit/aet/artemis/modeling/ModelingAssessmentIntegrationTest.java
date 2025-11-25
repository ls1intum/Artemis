package de.tum.cit.aet.artemis.modeling;

import static de.tum.cit.aet.artemis.core.util.TestResourceUtils.loadFileFromResources;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUpdateDTO;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentService;
import de.tum.cit.aet.artemis.assessment.test_repository.ComplaintResponseTestRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ExampleSubmissionTestRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.dto.ModelingAssessmentDTO;
import de.tum.cit.aet.artemis.modeling.test_repository.ModelingSubmissionTestRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ModelingAssessmentIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "modelingassessment";

    public static final String API_MODELING_SUBMISSIONS = "/api/modeling/modeling-submissions/";

    @Autowired
    private ModelingSubmissionTestRepository modelingSubmissionRepo;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private ExampleSubmissionTestRepository exampleSubmissionRepository;

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private ComplaintResponseTestRepository complaintResponseRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private ModelingExercise classExercise;

    private ModelingExercise activityExercise;

    private ModelingExercise objectExercise;

    private ModelingExercise useCaseExercise;

    private ModelingSubmission modelingSubmission;

    private Result modelingAssessment;

    private String validModel;

    private Course course;

    private final double offsetByTenThousandth = 0.0001;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 6, 2, 0, 1);
        this.course = modelingExerciseUtilService.addCourseWithDifferentModelingExercises();
        classExercise = ExerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        activityExercise = ExerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ActivityDiagram");
        objectExercise = ExerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ObjectDiagram");
        useCaseExercise = ExerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "UseCaseDiagram");

        validModel = loadFileFromResources("test-data/model-submission/model.54727.json");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testGetAssessmentBySubmissionId() throws Exception {
        saveModelingSubmissionAndAssessment(true);
        List<Feedback> feedback = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.v2.json");
        exerciseUtilService.updateAssessmentDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        Result result = request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.OK, Result.class);

        checkAssessmentFinished(result, null);
        participationUtilService.checkFeedbackCorrectlyStored(feedback, result.getFeedbacks(), FeedbackType.MANUAL);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testGetAssessmentBySubmissionId_notFound() throws Exception {
        saveModelingSubmission();
        request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.NOT_FOUND, Result.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testGetAssessmentBySubmissionId_assessmentNotFinished_forbidden() throws Exception {
        saveModelingSubmissionAndAssessment(false);
        exerciseUtilService.updateAssessmentDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testGetAssessmentBySubmissionId_assessmentDueDateNotOver_forbidden() throws Exception {
        saveModelingSubmissionAndAssessment(true);

        request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void testGetAssessmentBySubmissionId_studentNotOwnerOfSubmission_forbidden() throws Exception {
        saveModelingSubmissionAndAssessment(true);
        exerciseUtilService.updateAssessmentDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/result", HttpStatus.FORBIDDEN, Result.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExampleAssessmentAsTutor() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission(validModel, classExercise, true, true));
        List<Feedback> feedbackList = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        Result storedResult = request.putWithResponseBody("/api/modeling/modeling-submissions/" + storedExampleSubmission.getId() + "/example-assessment", feedbackList,
                Result.class, HttpStatus.OK);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
        assertThat(exampleSubmissionRepository.findById(storedExampleSubmission.getId())).isPresent();
        var result = request.get(
                "/api/modeling/exercise/" + classExercise.getId() + "/modeling-submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-assessment",
                HttpStatus.OK, Result.class);
        for (Feedback feedback : result.getFeedbacks()) {
            assertThat(feedback.getCredits()).isNull();
            assertThat(feedback.getDetailText()).isNull();
            assertThat(feedback.getReference()).isNotNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExampleAssessmentAsTutorNoTutorial() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission(validModel, classExercise, true, false));
        List<Feedback> feedbackList = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        Result storedResult = request.putWithResponseBody("/api/modeling/modeling-submissions/" + storedExampleSubmission.getId() + "/example-assessment", feedbackList,
                Result.class, HttpStatus.OK);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
        assertThat(exampleSubmissionRepository.findById(storedExampleSubmission.getId())).isPresent();
        request.get("/api/modeling/exercise/" + classExercise.getId() + "/modeling-submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-assessment",
                HttpStatus.OK, Result.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExampleAssessmentAsInstructor() throws Exception {
        ExampleSubmission storedExampleSubmission = participationUtilService
                .addExampleSubmission(participationUtilService.generateExampleSubmission(validModel, classExercise, true, true));
        List<Feedback> feedbackList = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        Result storedResult = request.putWithResponseBody("/api/modeling/modeling-submissions/" + storedExampleSubmission.getId() + "/example-assessment", feedbackList,
                Result.class, HttpStatus.OK);
        assertThat(storedResult.isExampleResult()).as("stored result is flagged as example result").isTrue();
        assertThat(exampleSubmissionRepository.findById(storedExampleSubmission.getId())).isPresent();
        request.get("/api/modeling/exercise/" + classExercise.getId() + "/modeling-submissions/" + storedExampleSubmission.getSubmission().getId() + "/example-assessment",
                HttpStatus.OK, Result.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testManualAssessmentSubmitAsStudent() throws Exception {
        ModelingSubmission submission = modelingExerciseUtilService.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json",
                TEST_PREFIX + "student1");
        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.FORBIDDEN);
        Optional<Result> storedResult = resultRepository.findDistinctBySubmissionId(submission.getId());
        assertThat(storedResult).as("result is not saved").isNotPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testManualAssessmentSave() throws Exception {
        User assessor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = modelingExerciseUtilService.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json",
                TEST_PREFIX + "student1");

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        createAssessment(submission, feedbacks, "/assessment", HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).orElseThrow();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).orElseThrow();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentNotFinished(storedResult, assessor);
        assertThat(storedResult.getSubmission().getParticipation()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testManualAssessmentSave_noCourse() throws Exception {
        classExercise.setCourse(null);
        exerciseRepository.save(classExercise);
        ModelingSubmission submission = modelingExerciseUtilService.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json",
                TEST_PREFIX + "student1");

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        createAssessment(submission, feedbacks, "/assessment", HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testManualAssessmentSubmit_classDiagram() throws Exception {
        User assessor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = modelingExerciseUtilService.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json",
                TEST_PREFIX + "student1");
        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");

        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).orElseThrow();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).orElseThrow();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
        assertThat(storedResult.getSubmission().getParticipation()).isNotNull();

        Course course = request.get("/api/core/courses/" + this.course.getId() + "/for-assessment-dashboard", HttpStatus.OK, Course.class);
        Exercise exercise = ExerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()).hasSize(1);
        assertThat(exercise.getNumberOfAssessmentsOfCorrectionRounds()[0].inTime()).isEqualTo(1L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testManualAssessmentSubmit_activityDiagram() throws Exception {
        User assessor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = modelingExerciseUtilService.addModelingSubmissionFromResources(activityExercise, "test-data/model-submission/example-activity-diagram.json",
                TEST_PREFIX + "student1");

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/example-activity-assessment.json");
        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).orElseThrow();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).orElseThrow();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
        assertThat(storedResult.getSubmission().getParticipation()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testManualAssessmentSubmit_objectDiagram() throws Exception {
        User assessor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = modelingExerciseUtilService.addModelingSubmissionFromResources(objectExercise, "test-data/model-submission/object-model.json",
                TEST_PREFIX + "student1");

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/object-assessment.json");
        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).orElseThrow();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).orElseThrow();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testManualAssessmentSubmit_useCaseDiagram() throws Exception {
        User assessor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = modelingExerciseUtilService.addModelingSubmissionFromResources(useCaseExercise, "test-data/model-submission/use-case-model.json",
                TEST_PREFIX + "student1");

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/use-case-assessment.json");
        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).orElseThrow();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).orElseThrow();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
        assertThat(storedResult.getSubmission().getParticipation()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testManualAssessmentSaveAndSubmit() throws Exception {
        User assessor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = modelingExerciseUtilService.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.54727.json",
                TEST_PREFIX + "student1");

        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        createAssessment(submission, feedbacks, "/assessment", HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).orElseThrow();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).orElseThrow();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentNotFinished(storedResult, assessor);

        feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.v2.json");
        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).orElseThrow();
        storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).orElseThrow();
        participationUtilService.checkFeedbackCorrectlyStored(feedbacks, storedResult.getFeedbacks(), FeedbackType.MANUAL);
        checkAssessmentFinished(storedResult, assessor);
        assertThat(storedResult.getSubmission().getParticipation()).isNotNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    @CsvSource({ "INCLUDED_COMPLETELY,true", "INCLUDED_COMPLETELY,false", "INCLUDED_AS_BONUS,true", "INCLUDED_AS_BONUS,false", "NOT_INCLUDED,true", "INCLUDED_AS_BONUS,false" })
    void testManualAssessmentSubmit(IncludedInOverallScore includedInOverallScore, boolean bonus) throws Exception {
        // setting up exercise
        useCaseExercise.setIncludedInOverallScore(includedInOverallScore);
        useCaseExercise.setMaxPoints(10.0);
        useCaseExercise.setBonusPoints(bonus ? 10.0 : 0.0);
        exerciseRepository.save(useCaseExercise);

        // setting up student submission
        ModelingSubmission submission = modelingExerciseUtilService.addModelingSubmissionFromResources(useCaseExercise, "test-data/model-submission/use-case-model.json",
                TEST_PREFIX + "student1");
        List<Feedback> feedbacks = new ArrayList<>();

        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 0.0, 0.0);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, -1.0, 0.0);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 1.0, 0.0);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 50.0);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, -2.5, 25.0);
        addAssessmentFeedbackAndCheckScore(submission, feedbacks, 15.0, bonus ? 175.0 : 100.0);

        if (bonus) {
            addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 200.0);
            addAssessmentFeedbackAndCheckScore(submission, feedbacks, 5.0, 200.0);
        }
    }

    private void addAssessmentFeedbackAndCheckScore(ModelingSubmission submission, List<Feedback> feedbacks, Double pointsAwarded, Double expectedScore) throws Exception {
        feedbacks.add(new Feedback().credits(pointsAwarded).type(FeedbackType.MANUAL_UNREFERENCED).detailText("gj"));
        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);
        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).orElseThrow();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).orElseThrow();
        assertThat(storedResult.getScore()).isEqualTo(expectedScore, Offset.offset(offsetByTenThousandth));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testManualAssessmentSubmit_withResultOver100Percent() throws Exception {
        useCaseExercise = (ModelingExercise) exerciseUtilService.addMaxScoreAndBonusPointsToExercise(useCaseExercise);
        ModelingSubmission submission = modelingExerciseUtilService.addModelingSubmissionFromResources(useCaseExercise, "test-data/model-submission/use-case-model.json",
                TEST_PREFIX + "student1");

        List<Feedback> feedbacks = new ArrayList<>();
        // Check that result is over 100% -> 105
        feedbacks.add(new Feedback().credits(80.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 1"));
        feedbacks.add(new Feedback().credits(25.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 2"));

        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        ModelingSubmission storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).orElseThrow();
        Result storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).orElseThrow();

        assertThat(storedResult.getScore()).isEqualTo(105);

        // Check that result is capped to maximum of maxScore + bonus points -> 110
        feedbacks.add(new Feedback().credits(20.00).type(FeedbackType.MANUAL_UNREFERENCED).detailText("nice submission 3"));

        createAssessment(submission, feedbacks, "/assessment?submit=true", HttpStatus.OK);

        storedSubmission = modelingSubmissionRepo.findWithEagerResultById(submission.getId()).orElseThrow();
        storedResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(storedSubmission.getLatestResult().getId()).orElseThrow();

        assertThat(storedResult.getScore()).isEqualTo(110, Offset.offset(offsetByTenThousandth));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAutomaticAssessment_existingManualAssessmentDoesNotChange() throws Exception {
        Feedback originalFeedback = new Feedback().credits(1.0).text("some feedback text").reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        Feedback changedFeedback = new Feedback().credits(2.0).text("another text").reference("Class:6aba5764-d102-4740-9675-b2bd0a4f2123");
        modelingSubmission = modelingExerciseUtilService.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json",
                TEST_PREFIX + "student1");
        ModelingSubmission modelingSubmission2 = modelingExerciseUtilService.addModelingSubmissionFromResources(classExercise, "test-data/model-submission/model.one-element.json",
                TEST_PREFIX + "student2");
        createAssessment(modelingSubmission, Collections.singletonList(originalFeedback), "/assessment?submit=true", HttpStatus.OK);

        createAssessment(modelingSubmission2, Collections.singletonList(changedFeedback), "/assessment?submit=true", HttpStatus.OK);

        modelingAssessment = resultRepository.findDistinctWithFeedbackBySubmissionId(modelingSubmission2.getId()).orElseThrow();
        assertThat(modelingAssessment.getFeedbacks()).as("assessment is correctly stored").hasSize(1);
        assertThat(modelingAssessment.getFeedbacks().getFirst().getCredits()).as("feedback credits are correct").isEqualTo(changedFeedback.getCredits());
        assertThat(modelingAssessment.getFeedbacks().getFirst().getText()).as("feedback text are correct").isEqualTo(changedFeedback.getText());
        modelingAssessment = resultRepository.findDistinctWithFeedbackBySubmissionId(modelingSubmission.getId()).orElseThrow();
        assertThat(modelingAssessment.getFeedbacks()).as("existing manual assessment has correct amount of feedback").hasSize(1);
        assertThat(modelingAssessment.getFeedbacks().getFirst().getCredits()).as("existing manual assessment did not change credits").isEqualTo(originalFeedback.getCredits());
        assertThat(modelingAssessment.getFeedbacks().getFirst().getText()).as("existing manual assessment did not change text").isEqualTo(originalFeedback.getText());
    }

    private void checkAssessmentNotFinished(Result storedResult, User assessor) {
        assertThat(storedResult.isRated()).as("rated has not been set").isFalse();
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
        assertThat(storedResult.isRated()).as("rated has not been set").isFalse();
        assertThat(storedResult.getScore()).as("score has not been calculated").isNull();
        assertThat(storedResult.getAssessor()).as("assessor has not been set").isNull();
        assertThat(storedResult.getCompletionDate()).as("completion date has not been set").isNull();
        assertThat(storedResult.getAssessmentType()).as("result type is SEMI AUTOMATIC").isEqualTo(AssessmentType.SEMI_AUTOMATIC);
    }

    private void saveModelingSubmission() throws Exception {
        modelingSubmission = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        modelingSubmission = modelingExerciseUtilService.addModelingSubmission(classExercise, modelingSubmission, TEST_PREFIX + "student1");
    }

    private void saveModelingSubmissionAndAssessment(boolean submitAssessment) throws Exception {
        modelingSubmission = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        modelingSubmission = modelingExerciseUtilService.addModelingSubmission(classExercise, modelingSubmission, TEST_PREFIX + "student1");
        modelingAssessment = modelingExerciseUtilService.addModelingAssessmentForSubmission(classExercise, modelingSubmission,
                "test-data/model-assessment/assessment.54727.v2.json", TEST_PREFIX + "tutor1", submitAssessment);
    }

    private void saveModelingSubmissionAndAssessment_activityDiagram(boolean submitAssessment) throws Exception {
        modelingSubmission = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/example-activity-diagram.json"), true);
        modelingSubmission = modelingExerciseUtilService.addModelingSubmission(activityExercise, modelingSubmission, TEST_PREFIX + "student1");
        modelingAssessment = modelingExerciseUtilService.addModelingAssessmentForSubmission(activityExercise, modelingSubmission,
                "test-data/model-assessment/example-activity-assessment.json", TEST_PREFIX + "tutor1", submitAssessment);
    }

    private void cancelAssessment(HttpStatus expectedStatus) throws Exception {
        modelingSubmission = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/example-activity-diagram.json"), true);
        modelingSubmission = modelingExerciseUtilService.addModelingSubmission(activityExercise, modelingSubmission, TEST_PREFIX + "student1");
        modelingAssessment = modelingExerciseUtilService.addModelingAssessmentForSubmission(activityExercise, modelingSubmission, TEST_PREFIX + "tutor1", false);
        request.put(API_MODELING_SUBMISSIONS + modelingSubmission.getId() + "/cancel-assessment", null, expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void cancelOwnAssessmentAsStudent() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void cancelOwnAssessmentAsTutor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void cancelAssessmentOfOtherTutorAsTutor() throws Exception {
        cancelAssessment(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void cancelAssessmentOfOtherTutorAsInstructor() throws Exception {
        cancelAssessment(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testOverrideAssessment_saveOtherTutorForbidden() throws Exception {
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_saveInstructorPossible() throws Exception {
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_saveSameTutorPossible() throws Exception {
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testOverrideAssessment_submitOtherTutorForbidden() throws Exception {
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_submitInstructorPossible() throws Exception {
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorPossible() throws Exception {
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testOverrideAssessment_saveOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_saveInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "false", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_saveSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.FORBIDDEN, "false", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_saveSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "false", false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testOverrideAssessment_submitOtherTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testOverrideAssessment_submitInstructorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "true", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorAfterAssessmentDueDateForbidden() throws Exception {
        assessmentDueDatePassed();
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.FORBIDDEN, "true", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testOverrideAssessment_submitSameTutorAfterAssessmentDueDatePossible() throws Exception {
        assessmentDueDatePassed();
        // should be possible because the original result was not yet submitted
        overrideAssessment(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1", HttpStatus.OK, "true", false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void multipleCorrectionRoundsForExam() throws Exception {
        // Setup exam with 2 correction rounds and a programming exercise
        ExerciseGroup exerciseGroup1 = new ExerciseGroup();
        Exam exam = examUtilService.addExam(classExercise.getCourseViaExerciseGroupOrCourseMember());
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam.addExerciseGroup(exerciseGroup1);
        exam.setVisibleDate(ZonedDateTime.now().minusHours(3));
        exam.setStartDate(ZonedDateTime.now().minusHours(2));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        exam = examRepository.save(exam);

        Exam examWithExerciseGroups = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).orElseThrow();
        exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().getFirst();
        ModelingExercise exercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        exercise = exerciseRepository.save(exercise);
        exerciseGroup1.addExercise(exercise);

        // add student submission
        final var submission = modelingExerciseUtilService.addModelingSubmissionFromResources(exercise, "test-data/model-submission/model.54727.partial.json",
                TEST_PREFIX + "student1");

        Participation studentParticipation = submission.getParticipation();

        // request to manually assess latest submission (correction round: 0)
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");
        params.add("correction-round", "0");
        ModelingSubmission submissionWithoutFirstAssessment = request.get("/api/modeling/exercises/" + exercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK,
                ModelingSubmission.class, params);
        // verify that no new submission was created
        assertThat(submissionWithoutFirstAssessment).isEqualTo(submission);
        // verify that the lock has been set
        assertThat(submissionWithoutFirstAssessment.getLatestResult()).isNotNull();
        assertThat(submissionWithoutFirstAssessment.getLatestResult().getAssessor().getLogin()).isEqualTo(TEST_PREFIX + "tutor1");
        assertThat(submissionWithoutFirstAssessment.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.MANUAL);

        // make sure that new result correctly appears inside the continue box
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1Tutor1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1Tutor1.add("assessedByTutor", "true");
        paramsGetAssessedCR1Tutor1.add("correction-round", "0");
        var assessedSubmissionList = request.getList("/api/modeling/exercises/" + exercise.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.getFirst().getResultForCorrectionRound(0)).isEqualTo(submissionWithoutFirstAssessment.getLatestResult());

        // assess submission and submit
        List<Feedback> feedbacks = ParticipationFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here")).toList();
        params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        ModelingAssessmentDTO body = new ModelingAssessmentDTO(feedbacks, "text");
        final var firstSubmittedManualResult = request.putWithResponseBodyAndParams(
                API_MODELING_SUBMISSIONS + submissionWithoutFirstAssessment.getId() + "/result/" + submissionWithoutFirstAssessment.getFirstResult().getId() + "/assessment", body,
                Result.class, HttpStatus.OK, params);

        // make sure that new result correctly appears after the assessment for first correction round
        assessedSubmissionList = request.getList("/api/modeling/exercises/" + exercise.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class,
                paramsGetAssessedCR1Tutor1);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().getId()).isEqualTo(submissionWithoutFirstAssessment.getId());
        assertThat(assessedSubmissionList.getFirst().getResultForCorrectionRound(0)).isNotNull();
        assertThat(firstSubmittedManualResult.getAssessor().getLogin()).isEqualTo(TEST_PREFIX + "tutor1");

        // verify that the result contains the relationship
        assertThat(firstSubmittedManualResult).isNotNull();
        assertThat(firstSubmittedManualResult.getSubmission().getParticipation()).isEqualTo(studentParticipation);

        // verify that the relationship between student participation,
        var databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        var fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).contains(submissionWithoutFirstAssessment);
        assertThat(fetchedParticipation.findLatestResult()).isEqualTo(firstSubmittedManualResult);

        var databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository
                .findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.getFirst();
        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).isPresent();
        // it should contain the lock for the manual result
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getResults()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getLatestResult()).isEqualTo(firstSubmittedManualResult);

        // SECOND ROUND OF CORRECTION

        userUtilService.changeUser(TEST_PREFIX + "tutor2");
        LinkedMultiValueMap<String, String> paramsSecondCorrection = new LinkedMultiValueMap<>();
        paramsSecondCorrection.add("lock", "true");
        paramsSecondCorrection.add("correction-round", "1");

        final var submissionWithoutSecondAssessment = request.get("/api/modeling/exercises/" + exercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK,
                ModelingSubmission.class, paramsSecondCorrection);

        // verify that the submission is not new
        assertThat(submissionWithoutSecondAssessment).isEqualTo(submission);
        // verify that the lock has been set
        assertThat(submissionWithoutSecondAssessment.getLatestResult()).isNotNull();
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessor().getLogin()).isEqualTo(TEST_PREFIX + "tutor2");
        assertThat(submissionWithoutSecondAssessment.getLatestResult().getAssessmentType()).isEqualTo(AssessmentType.MANUAL);

        // verify that the relationship between student participation,
        databaseRelationshipStateOfResultsOverParticipation = studentParticipationRepository.findWithEagerSubmissionsAndResultsAssessorsById(studentParticipation.getId());
        assertThat(databaseRelationshipStateOfResultsOverParticipation).isPresent();
        fetchedParticipation = databaseRelationshipStateOfResultsOverParticipation.get();

        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).contains(submissionWithoutSecondAssessment);
        assertThat(participationUtilService.getResultsForParticipation(fetchedParticipation).stream().filter(result -> result.getCompletionDate() == null).findFirst())
                .contains(submissionWithoutSecondAssessment.getLatestResult());

        databaseRelationshipStateOfResultsOverSubmission = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exercise.getId());
        assertThat(databaseRelationshipStateOfResultsOverSubmission).hasSize(1);
        fetchedParticipation = databaseRelationshipStateOfResultsOverSubmission.getFirst();
        assertThat(fetchedParticipation.getSubmissions()).hasSize(1);
        assertThat(fetchedParticipation.findLatestSubmission()).isPresent();
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getResults()).hasSize(2);
        assertThat(fetchedParticipation.findLatestSubmission().orElseThrow().getLatestResult()).isEqualTo(submissionWithoutSecondAssessment.getLatestResult());

        // assess submission and submit
        feedbacks = ParticipationFactory.generateFeedback().stream().peek(feedback -> feedback.setDetailText("Good work here")).toList();
        params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        body = new ModelingAssessmentDTO(feedbacks, "text");
        final var secondSubmittedManualResult = request.putWithResponseBodyAndParams(
                API_MODELING_SUBMISSIONS + submissionWithoutFirstAssessment.getId() + "/result/" + submissionWithoutSecondAssessment.getResults().get(1).getId() + "/assessment",
                body, Result.class, HttpStatus.OK, params);
        assertThat(secondSubmittedManualResult).isNotNull();

        // make sure that new result correctly appears after the assessment for second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR2 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR2.add("assessedByTutor", "true");
        paramsGetAssessedCR2.add("correction-round", "1");
        assessedSubmissionList = request.getList("/api/modeling/exercises/" + exercise.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class,
                paramsGetAssessedCR2);

        assertThat(assessedSubmissionList).hasSize(1);
        assertThat(assessedSubmissionList.getFirst().getId()).isEqualTo(submissionWithoutSecondAssessment.getId());
        assertThat(assessedSubmissionList.getFirst().getResultForCorrectionRound(1)).isEqualTo(secondSubmittedManualResult);

        // make sure that they do not appear for the first correction round as the tutor only assessed the second correction round
        LinkedMultiValueMap<String, String> paramsGetAssessedCR1 = new LinkedMultiValueMap<>();
        paramsGetAssessedCR1.add("assessedByTutor", "true");
        paramsGetAssessedCR1.add("correction-round", "0");
        assessedSubmissionList = request.getList("/api/modeling/exercises/" + exercise.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class,
                paramsGetAssessedCR1);

        assertThat(assessedSubmissionList).isEmpty();

        // Student should not have received a result over WebSocket as manual correction is ongoing
        verify(websocketMessagingService, never()).sendMessageToUser(notNull(), eq(Constants.NEW_RESULT_TOPIC), isA(ResultDTO.class));
    }

    private void assessmentDueDatePassed() {
        exerciseUtilService.updateAssessmentDueDate(classExercise.getId(), ZonedDateTime.now().minusSeconds(10));
    }

    private void overrideAssessment(String student, String originalAssessor, HttpStatus httpStatus, String submit, boolean originalAssessmentSubmitted) throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        submission = modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(classExercise, submission, student, originalAssessor);
        submission.getLatestResult().setCompletionDate(originalAssessmentSubmitted ? ZonedDateTime.now() : null);
        resultRepository.save(submission.getLatestResult());
        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", submit);
        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        ModelingAssessmentDTO body = new ModelingAssessmentDTO(feedbacks, "text");
        request.putWithResponseBodyAndParams(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + submission.getLatestResult().getId() + "/assessment", body, Result.class,
                httpStatus, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "ADMIN")
    void overrideAssessment_inFirstCorrectionRoundByInstructor() throws Exception {
        String student = TEST_PREFIX + "student1";
        String originalAssessor = TEST_PREFIX + "tutor1";
        HttpStatus httpStatus = HttpStatus.OK;
        String submit = "true";

        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(loadFileFromResources("test-data/model-submission/model.54727.json"), true);
        submission = modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(classExercise, submission, student, originalAssessor);

        Result newResult = participationUtilService
                .addResultToSubmission(submission, AssessmentType.MANUAL, userUtilService.getUserByLogin(TEST_PREFIX + "tutor2"), null, true, null).getLatestResult();

        resultRepository.save(submission.getLatestResult());
        var params = new LinkedMultiValueMap<String, String>();
        params.add("submit", submit);
        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");
        ModelingAssessmentDTO body = new ModelingAssessmentDTO(feedbacks, "text");
        request.putWithResponseBodyAndParams(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + newResult.getId() + "/assessment", body, Result.class, httpStatus,
                params);
    }

    private void createAssessment(ModelingSubmission submission, List<Feedback> feedbacks, String urlEnding, HttpStatus expectedStatus) throws Exception {
        // id 0 so no result exists and a new one will be created
        request.put(API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + 0 + urlEnding, new ModelingAssessmentDTO(feedbacks, "text"), expectedStatus);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void overrideAssessmentAfterComplaint() throws Exception {
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(ZonedDateTime.now().minusDays(5), ZonedDateTime.now().plusDays(5),
                ZonedDateTime.now().plusDays(8), DiagramType.ClassDiagram, course);
        modelingExercise.setMaxPoints(10.0);
        modelingExercise.setBonusPoints(0.0);
        modelingExercise = exerciseRepository.saveAndFlush(modelingExercise);

        // creating participation of student1 by starting the exercise
        User student1 = userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElse(null);
        StudentParticipation studentParticipation = participationService.startExercise(modelingExercise, student1, false);

        // creating submission of student1
        ModelingSubmission submission = new ModelingSubmission();
        submission.setType(SubmissionType.MANUAL);
        submission.setParticipation(studentParticipation);
        submission.setSubmitted(Boolean.TRUE);
        submission.setSubmissionDate(ZonedDateTime.now());
        submission = submissionRepository.saveAndFlush(submission);

        // creating assessment by tutor1
        User tutor1 = userTestRepository.findOneByLogin("tutor1").orElse(null);
        Result firstResult = ParticipationFactory.generateResult(true, 50);
        firstResult.setAssessor(tutor1);
        firstResult.setHasComplaint(true);
        firstResult.setSubmission(submission);
        firstResult.setExerciseId(modelingExercise.getId());
        firstResult = resultRepository.saveAndFlush(firstResult);

        submission.addResult(firstResult);
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
        List<Feedback> feedbacks = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54727.json");  // 1,5/10 points
        final var assessmentUpdate = new AssessmentUpdateDTO(feedbacks, complaintResponse, null);
        Result resultAfterComplaint = assessmentService.updateAssessmentAfterComplaint(submission.getLatestResult(), modelingExercise, assessmentUpdate);

        List<Feedback> overrideFeedback = participationUtilService.loadAssessmentFomResources("test-data/model-assessment/assessment.54745.json"); // 4/10 points
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("submit", "true");
        ModelingAssessmentDTO body = new ModelingAssessmentDTO(overrideFeedback, "text");
        Result overwrittenResult = request.putWithResponseBodyAndParams(
                API_MODELING_SUBMISSIONS + submission.getId() + "/result/" + submission.getLatestResult().getId() + "/assessment", body, Result.class, HttpStatus.OK, params);

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
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteResult() throws Exception {
        Course course = exerciseUtilService.addCourseWithOneExerciseAndSubmissions(TEST_PREFIX, "modeling", 1,
                Optional.of(loadFileFromResources("test-data/model-submission/model.54727.json")));
        Exercise exercise = exerciseRepository.findAllExercisesByCourseId(course.getId()).iterator().next();
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        exerciseUtilService.addAssessmentToExercise(exercise, userUtilService.getUserByLogin(TEST_PREFIX + "tutor2"));

        var submissions = participationUtilService.getAllSubmissionsOfExercise(exercise);
        Submission submission = submissions.getFirst();
        assertThat(submission.getResults()).hasSize(2);
        Result firstResult = submission.getResults().getFirst();
        Result lastResult = submission.getLatestResult();
        request.delete("/api/modeling/participations/" + submission.getParticipation().getId() + "/modeling-submissions/" + submission.getId() + "/results/" + firstResult.getId(),
                HttpStatus.OK);
        submission = submissionRepository.findOneWithEagerResultAndFeedbackAndAssessmentNote(submission.getId());
        assertThat(submission.getResults()).hasSize(1);
        assertThat(submission.getResults().getFirst()).isEqualTo(lastResult);
    }
}
