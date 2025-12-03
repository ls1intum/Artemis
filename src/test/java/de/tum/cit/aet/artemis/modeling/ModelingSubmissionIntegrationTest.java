package de.tum.cit.aet.artemis.modeling;

import static de.tum.cit.aet.artemis.core.util.TestResourceUtils.HalfSecond;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionVersion;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionVersionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.test_repository.ModelingSubmissionTestRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ModelingSubmissionIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "modelingsubmissionintegration";

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private ModelingSubmissionTestRepository modelingSubmissionRepo;

    @Autowired
    private SubmissionVersionRepository submissionVersionRepository;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private ModelingExercise classExercise;

    private ModelingExercise activityExercise;

    private ModelingExercise objectExercise;

    private ModelingExercise useCaseExercise;

    private ModelingExercise finishedExercise;

    private ModelingSubmission submittedSubmission;

    private ModelingSubmission unsubmittedSubmission;

    private StudentParticipation afterDueDateParticipation;

    private String emptyModel;

    private String validModel;

    private TextExercise textExercise;

    private Course course;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 3, 1, 0, 1);
        course = modelingExerciseUtilService.addCourseWithDifferentModelingExercises();
        classExercise = ExerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        activityExercise = ExerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ActivityDiagram");
        objectExercise = ExerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ObjectDiagram");
        useCaseExercise = ExerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "UseCaseDiagram");
        finishedExercise = ExerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "finished");
        afterDueDateParticipation = participationUtilService.createAndSaveParticipationForExercise(finishedExercise, TEST_PREFIX + "student3");
        participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student3");

        emptyModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/empty-class-diagram.json");
        validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        submittedSubmission = generateSubmittedSubmission();
        unsubmittedSubmission = generateUnsubmittedSubmission();

        Course course2 = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        textExercise = (TextExercise) new ArrayList<>(course2.getExercises()).getFirst();

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student4");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor2");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void createModelingSubmission_badRequest() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingSubmissionRepo.save(submission);
        request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void createModelingSubmission_studentNotInCourse() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void saveAndSubmitModelingSubmission_tooLarge() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyModel, false);
        // should be ok
        char[] charsModel = new char[(int) (Constants.MAX_SUBMISSION_MODEL_LENGTH)];
        Arrays.fill(charsModel, 'a');
        submission.setModel(new String(charsModel));
        request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.OK);

        // should be too large
        char[] charsModelTooLarge = new char[(int) (Constants.MAX_SUBMISSION_MODEL_LENGTH + 1)];
        Arrays.fill(charsModelTooLarge, 'a');
        submission.setModel(new String(charsModelTooLarge));
        request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void saveAndSubmitModelingSubmission_classDiagram() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(classExercise.getId(), submission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyModel);
        checkDetailsHidden(returnedSubmission, true);

        returnedSubmission.setModel(validModel);
        returnedSubmission.setSubmitted(true);
        returnedSubmission = performUpdateOnModelSubmission(classExercise.getId(), returnedSubmission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void saveAndSubmitModelingSubmission_activityDiagram() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(activityExercise, TEST_PREFIX + "student1");
        String emptyActivityModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/empty-activity-diagram.json");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyActivityModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(activityExercise.getId(), submission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyActivityModel);
        checkDetailsHidden(returnedSubmission, true);

        String validActivityModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/example-activity-diagram.json");
        returnedSubmission.setModel(validActivityModel);
        returnedSubmission.setSubmitted(true);
        returnedSubmission = performUpdateOnModelSubmission(activityExercise.getId(), returnedSubmission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validActivityModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void saveAndSubmitModelingSubmission_objectDiagram() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(objectExercise, TEST_PREFIX + "student1");
        String emptyObjectModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/empty-object-diagram.json");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyObjectModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(objectExercise.getId(), submission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyObjectModel);
        checkDetailsHidden(returnedSubmission, true);

        String validObjectModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/object-model.json");
        returnedSubmission.setModel(validObjectModel);
        returnedSubmission.setSubmitted(true);
        returnedSubmission = performUpdateOnModelSubmission(objectExercise.getId(), returnedSubmission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validObjectModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void saveAndSubmitModelingSubmission_useCaseDiagram() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(useCaseExercise, TEST_PREFIX + "student1");
        String emptyUseCaseModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/empty-use-case-diagram.json");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyUseCaseModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(useCaseExercise.getId(), submission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyUseCaseModel);
        checkDetailsHidden(returnedSubmission, true);

        String validUseCaseModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/use-case-model.json");
        returnedSubmission.setModel(validUseCaseModel);
        returnedSubmission.setSubmitted(true);
        returnedSubmission = performUpdateOnModelSubmission(useCaseExercise.getId(), returnedSubmission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validUseCaseModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void saveAndSubmitModelingSubmission_isTeamMode() throws Exception {
        useCaseExercise.setMode(ExerciseMode.TEAM);
        exerciseRepository.save(useCaseExercise);
        Team team = new Team();
        team.setName("Team");
        team.setShortName(TEST_PREFIX + "team");
        team.setExercise(useCaseExercise);
        team.addStudents(userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow());
        team.addStudents(userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow());
        teamRepository.save(useCaseExercise, team);

        participationUtilService.addTeamParticipationForExercise(useCaseExercise, team.getId());
        String emptyUseCaseModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/empty-use-case-diagram.json");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyUseCaseModel, false);
        submission.setExplanationText("This is a use case diagram.");
        ModelingSubmission returnedSubmission = performInitialModelSubmission(useCaseExercise.getId(), submission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyUseCaseModel);

        userUtilService.changeUser(TEST_PREFIX + "student1");
        Optional<SubmissionVersion> version = submissionVersionRepository.findLatestVersion(returnedSubmission.getId());
        assertThat(version).as("submission version was created").isNotEmpty();
        assertThat(version.orElseThrow().getAuthor().getLogin()).as("submission version has correct author").isEqualTo(TEST_PREFIX + "student1");
        assertThat(version.get().getContent()).as("submission version has correct content")
                .isEqualTo("Model: " + returnedSubmission.getModel() + "; Explanation: " + returnedSubmission.getExplanationText());
        assertThat(version.get().getCreatedDate()).isNotNull();
        assertThat(version.get().getLastModifiedDate()).isNotNull();

        userUtilService.changeUser(TEST_PREFIX + "student2");
        String validUseCaseModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/use-case-model.json");
        returnedSubmission.setModel(validUseCaseModel);
        returnedSubmission.setSubmitted(true);
        returnedSubmission = performUpdateOnModelSubmission(useCaseExercise.getId(), returnedSubmission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validUseCaseModel);
        checkDetailsHidden(returnedSubmission, true);

        userUtilService.changeUser(TEST_PREFIX + "student2");
        version = submissionVersionRepository.findLatestVersion(returnedSubmission.getId());
        assertThat(version).as("submission version was created").isNotEmpty();
        assertThat(version.orElseThrow().getAuthor().getLogin()).as("submission version has correct author").isEqualTo(TEST_PREFIX + "student2");
        assertThat(version.get().getContent()).as("submission version has correct content")
                .isEqualTo("Model: " + returnedSubmission.getModel() + "; Explanation: " + returnedSubmission.getExplanationText());

        returnedSubmission = performUpdateOnModelSubmission(useCaseExercise.getId(), returnedSubmission);
        userUtilService.changeUser(TEST_PREFIX + "student2");
        Optional<SubmissionVersion> newVersion = submissionVersionRepository.findLatestVersion(returnedSubmission.getId());
        assertThat(newVersion.orElseThrow().getId()).as("submission version was not created").isEqualTo(version.get().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void updateModelSubmission() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student2");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyModel, true);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(classExercise.getId(), submission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyModel);

        returnedSubmission.setModel(validModel);
        returnedSubmission.setSubmitted(false);
        request.putWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", returnedSubmission, ModelingSubmission.class, HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validModel);

        returnedSubmission.setSubmitted(true);
        returnedSubmission = request.putWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", returnedSubmission, ModelingSubmission.class,
                HttpStatus.OK);
        StudentParticipation studentParticipation = (StudentParticipation) returnedSubmission.getParticipation();
        assertThat(studentParticipation.getSubmissions()).as("do not send old submissions to the client").isEmpty();
        assertThat(studentParticipation.getExercise().getGradingInstructions()).as("sensitive information (grading instructions) is hidden").isNull();
        assertThat(returnedSubmission.getLatestResult()).as("sensitive information (exercise result) is hidden").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void injectResultOnSubmissionUpdate() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, false);
        Result result = new Result();
        result.setScore(100D);
        result.setRated(true);
        result.setAssessor(user);
        result.setExerciseId(classExercise.getId());
        submission.addResult(result);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class);

        userUtilService.changeUser(TEST_PREFIX + "student1");
        storedSubmission = modelingSubmissionRepo.findByIdWithEagerResult(storedSubmission.getId()).orElseThrow();
        assertThat(storedSubmission.getLatestResult()).as("submission still unrated").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = modelingExerciseUtilService.addModelingSubmission(classExercise, submittedSubmission, TEST_PREFIX + "student1");
        ModelingSubmission submission2 = modelingExerciseUtilService.addModelingSubmission(classExercise, unsubmittedSubmission, TEST_PREFIX + "student2");

        List<ModelingSubmission> submissions = request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.OK,
                ModelingSubmission.class);

        assertThat(submissions).as("contains both submissions").containsExactlyInAnyOrder(submission1, submission2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void getAllSubmissionsOfExercise_instructorNotInCourse() throws Exception {
        request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllSubmissionsOfExercise_assessedByTutor() throws Exception {
        List<ModelingSubmission> submissions = request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions?assessedByTutor=true", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(submissions).as("does not have a modeling submission assessed by the tutor").isEmpty();

        modelingExerciseUtilService.addModelingSubmissionWithFinishedResultAndAssessor(classExercise, submittedSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        submissions = request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions?assessedByTutor=true", HttpStatus.OK, ModelingSubmission.class);
        assertThat(submissions).as("has a modeling submission assessed by the tutor").hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void getAllSubmissionsOfExercise_assessedByTutor_instructorNotInCourse() throws Exception {
        request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions?assessedByTutor=true", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getAllSubmissionsOfExerciseAsStudent() throws Exception {
        modelingExerciseUtilService.addModelingSubmission(classExercise, submittedSubmission, TEST_PREFIX + "student1");
        modelingExerciseUtilService.addModelingSubmission(classExercise, unsubmittedSubmission, TEST_PREFIX + "student2");

        request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.FORBIDDEN, ModelingSubmission.class);
        request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions?submittedOnly=true", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllSubmittedSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = modelingExerciseUtilService.addModelingSubmission(classExercise, submittedSubmission, TEST_PREFIX + "student1");
        modelingExerciseUtilService.addModelingSubmission(classExercise, unsubmittedSubmission, TEST_PREFIX + "student2");
        ModelingSubmission submission3 = modelingExerciseUtilService.addModelingSubmission(classExercise, generateSubmittedSubmission(), TEST_PREFIX + "student3");

        List<ModelingSubmission> submissions = request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions?submittedOnly=true", HttpStatus.OK,
                ModelingSubmission.class);

        assertThat(submissions).as("contains only submitted submission").containsExactlyInAnyOrder(submission1, submission3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmission() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        ModelingSubmission storedSubmission = request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmission.class);

        assertThat(storedSubmission.getLatestResult()).as("result has been set").isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutResults() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        ModelingSubmission storedSubmission = request.get("/api/modeling/modeling-submissions/" + submission.getId() + "?withoutResults=true", HttpStatus.OK,
                ModelingSubmission.class);

        assertThat(storedSubmission.getLatestResult()).as("result has not been set").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void getModelSubmission_tutorNotInCourse() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(value = TEST_PREFIX + "student2", roles = "USER")
    void getModelSubmissionWithResult_notInvolved_notAllowed() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmission_lockLimitReached_success() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        createNineLockedSubmissionsForDifferentExercisesAndUsers(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(useCaseExercise, submission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        ModelingSubmission storedSubmission = request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmission.class);

        assertThat(storedSubmission.getLatestResult()).as("result has been set").isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmission_lockLimitReached_badRequest() throws Exception {
        createTenLockedSubmissionsForDifferentExercisesAndUsers(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(useCaseExercise, submission, TEST_PREFIX + "student2");

        request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.BAD_REQUEST, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "TA")
    void getModelingSubmissionWithResultId() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = (ModelingSubmission) participationUtilService.addSubmissionWithTwoFinishedResultsWithAssessor(classExercise, submission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        Result storedResult = submission.getResultForCorrectionRound(1);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("resultId", String.valueOf(storedResult.getId()));
        ModelingSubmission storedSubmission = request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmission.class, params);

        assertThat(storedSubmission.getResults()).isNotNull();
        assertThat(storedSubmission.getResults()).contains(storedResult);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelingSubmissionWithResultIdAsTutor_badRequest() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = (ModelingSubmission) modelingExerciseUtilService.addModelingSubmissionWithFinishedResultAndAssessor(classExercise, submission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        Result storedResult = submission.getResultForCorrectionRound(0);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("resultId", String.valueOf(storedResult.getId()));
        request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmission.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmission storedSubmission = request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK,
                ModelingSubmission.class);

        assertThat(storedSubmission).as("submission was found").usingRecursiveComparison().ignoringFields("results", "submissionDate", "participation").isEqualTo(submission);
        assertThat(storedSubmission.getSubmissionDate()).as("submission date is correct").isCloseTo(submission.getSubmissionDate(), HalfSecond());
        assertThat(storedSubmission.getLatestResult()).as("result is not set").isNull();
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_wrongExerciseType() throws Exception {
        request.get("/api/modeling/exercises/" + textExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.BAD_REQUEST, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_lockSubmission() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmission storedSubmission = request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment?lock=true", HttpStatus.OK,
                ModelingSubmission.class);

        // set dates to UTC and round to milliseconds for comparison
        submission.setSubmissionDate(ZonedDateTime.ofInstant(submission.getSubmissionDate().truncatedTo(ChronoUnit.SECONDS).toInstant(), ZoneId.of("UTC")));
        storedSubmission.setSubmissionDate(ZonedDateTime.ofInstant(storedSubmission.getSubmissionDate().truncatedTo(ChronoUnit.SECONDS).toInstant(), ZoneId.of("UTC")));
        final String[] ignoringFields = { "results", "submissionDate", "participation", "similarElementCounts" };
        assertThat(storedSubmission).as("submission was found").usingRecursiveComparison().ignoringFields(ignoringFields).isEqualTo(submission);
        assertThat(storedSubmission.getLatestResult()).as("result is set").isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_noSubmittedSubmission_null() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, false);
        modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        var response = request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK, ModelingSubmission.class);
        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_noSubmissionWithoutAssessment_null() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(classExercise, submission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        var response = request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK, ModelingSubmission.class);
        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_dueDateNotOver() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void getModelSubmissionWithoutAssessment_notTutorInCourse() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getModelSubmissionWithoutAssessment_asStudent_forbidden() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_testLockLimit() throws Exception {
        createNineLockedSubmissionsForDifferentExercisesAndUsers(TEST_PREFIX + "tutor1");
        ModelingSubmission newSubmission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(useCaseExercise, newSubmission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(useCaseExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmission storedSubmission = request.get("/api/modeling/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment?lock=true", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(storedSubmission).as("submission was found").isNotNull();
        request.get("/api/modeling/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.BAD_REQUEST, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllModelingSubmissions() throws Exception {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> modelingSubmissionRepo.findByIdElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> modelingSubmissionRepo.findByIdWithEagerResultAndFeedbackAndAssessorAndAssessmentNoteAndParticipationResultsElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> modelingSubmissionRepo.findByIdWithEagerResultAndFeedbackElseThrow(Long.MAX_VALUE));

        createNineLockedSubmissionsForDifferentExercisesAndUsers(TEST_PREFIX + "tutor1");
        ModelingSubmission newSubmission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(useCaseExercise, newSubmission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(useCaseExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmission storedSubmission = request.get("/api/modeling/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment?lock=true", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(storedSubmission).as("submission was found").isNotNull();
        request.get("/api/modeling/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.BAD_REQUEST, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getModelSubmissionForModelingEditor() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        classExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        classExercise.setAssessmentDueDate(ZonedDateTime.now().minusHours(1));
        modelingExerciseUtilService.updateExercise(classExercise);
        submission = (ModelingSubmission) modelingExerciseUtilService.addModelingSubmissionWithFinishedResultAndAssessor(classExercise, submission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");

        ModelingSubmission receivedSubmission = request.get("/api/modeling/participations/" + submission.getParticipation().getId() + "/latest-modeling-submission", HttpStatus.OK,
                ModelingSubmission.class);

        // set dates to UTC and round to milliseconds for comparison
        submission.setSubmissionDate(ZonedDateTime.ofInstant(submission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        receivedSubmission.setSubmissionDate(ZonedDateTime.ofInstant(receivedSubmission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(receivedSubmission).as("submission was found").isEqualTo(submission);
        assertThat(receivedSubmission.getLatestResult()).as("result is set").isNotNull();
        assertThat(receivedSubmission.getLatestResult().getAssessor()).as("assessor is hidden").isNull();

        // students can only see their own models
        submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student2");
        request.get("/api/modeling/participations/" + submission.getParticipation().getId() + "/latest-modeling-submission", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionForModelingEditor_badRequest() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        StudentParticipation participation = new StudentParticipation();
        participation.setParticipant(user);
        participation.setExercise(null);
        StudentParticipation studentParticipation = studentParticipationRepository.save(participation);
        request.get("/api/modeling/participations/" + studentParticipation.getId() + "/latest-modeling-submission", HttpStatus.BAD_REQUEST, ModelingSubmission.class);

        participation.setExercise(textExercise);
        studentParticipation = studentParticipationRepository.save(participation);
        request.get("/api/modeling/participations/" + studentParticipation.getId() + "/latest-modeling-submission", HttpStatus.BAD_REQUEST, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getModelingResult_BeforeExamPublishDate_Forbidden() throws Exception {
        // create exam
        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        exam.setStartDate(ZonedDateTime.now().minusHours(2));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        exam.setVisibleDate(ZonedDateTime.now().minusHours(3));
        exam.setPublishResultsDate(ZonedDateTime.now().plusHours(3));

        // creating exercise
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().getFirst();

        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ActivityDiagram, exerciseGroup);
        exerciseGroup.addExercise(modelingExercise);
        exerciseGroupRepository.save(exerciseGroup);
        modelingExercise = exerciseRepository.save(modelingExercise);

        examRepository.save(exam);

        ModelingSubmission modelingSubmission = ParticipationFactory.generateModelingSubmission("Some text", true);
        modelingSubmission = modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(modelingExercise, modelingSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        request.get("/api/modeling/participations/" + modelingSubmission.getParticipation().getId() + "/latest-modeling-submission", HttpStatus.FORBIDDEN,
                ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getModelingResult_testExam() throws Exception {
        // create test exam
        Exam exam = examUtilService.addTestExamWithExerciseGroup(course, true);
        exam.setStartDate(ZonedDateTime.now().minusHours(2));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        exam.setVisibleDate(ZonedDateTime.now().minusHours(3));

        ExerciseGroup exerciseGroup = exam.getExerciseGroups().getFirst();
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ActivityDiagram, exerciseGroup);
        exerciseGroup.addExercise(modelingExercise);
        exerciseGroupRepository.save(exerciseGroup);
        modelingExercise = exerciseRepository.save(modelingExercise);

        exam = examRepository.save(exam);

        var studentExam = examUtilService.addStudentExamForTestExam(exam, TEST_PREFIX + "student1");
        studentExam.setStartedAndStartDate(ZonedDateTime.now().minusMinutes(5));
        studentExam.setSubmitted(true);
        studentExam.setSubmissionDate(ZonedDateTime.now().minusMinutes(2));
        studentExamRepository.save(studentExam);

        ModelingSubmission modelingSubmission = ParticipationFactory.generateModelingSubmission("Some text", true);
        modelingSubmission = modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(modelingExercise, modelingSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        // students can always view their submissions for test exams
        var submission = request.get("/api/modeling/participations/" + modelingSubmission.getParticipation().getId() + "/latest-modeling-submission", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(submission).isNotNull();
        assertThat(submission.getId()).isEqualTo(modelingSubmission.getId());
        assertThat(submission.getParticipation().getExercise().getExerciseGroup().getExam()).as("The exam object should not be send to students").isNull();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionForModelingEditor_emptySubmission() throws Exception {
        StudentParticipation studentParticipation = participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        assertThat(studentParticipation.getSubmissions()).isEmpty();
        ModelingSubmission returnedSubmission = request.get("/api/modeling/participations/" + studentParticipation.getId() + "/latest-modeling-submission", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(returnedSubmission).as("new submission is created").isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionForModelingEditor_unfinishedAssessment() throws Exception {
        StudentParticipation studentParticipation = participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        modelingExerciseUtilService.addModelingSubmissionWithEmptyResult(classExercise, "", TEST_PREFIX + "student1");

        ModelingSubmission returnedSubmission = request.get("/api/modeling/participations/" + studentParticipation.getId() + "/latest-modeling-submission", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(returnedSubmission.getLatestResult()).as("the result is not sent to the client if the assessment is not finished").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_afterDueDate_forbidden() throws Exception {
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now().minusDays(2));
        studentParticipationRepository.saveAndFlush(afterDueDateParticipation);
        request.post("/api/modeling/exercises/" + finishedExercise.getId() + "/modeling-submissions", submittedSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_beforeDueDate_allowed() throws Exception {
        ModelingSubmission submission = request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", submittedSubmission,
                ModelingSubmission.class, HttpStatus.OK);

        assertThat(submission.getSubmissionDate()).isCloseTo(ZonedDateTime.now(), within(500, ChronoUnit.MILLIS));
        assertThat(submission.getParticipation().getInitializationState()).isEqualTo(InitializationState.FINISHED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_beforeDueDateSecondSubmission_allowed() throws Exception {
        submittedSubmission.setModel(validModel);
        submittedSubmission = request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", submittedSubmission,
                ModelingSubmission.class, HttpStatus.OK);

        final var submissionInDb = modelingSubmissionRepo.findById(submittedSubmission.getId());
        assertThat(submissionInDb).isPresent();
        assertThat(submissionInDb.get().getModel()).isEqualTo(validModel);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_afterDueDateWithParticipationStartAfterDueDate_allowed() throws Exception {
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepository.saveAndFlush(afterDueDateParticipation);

        request.postWithoutLocation("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", submittedSubmission, HttpStatus.OK, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void saveExercise_beforeDueDate() throws Exception {
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", unsubmittedSubmission,
                ModelingSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void saveExercise_afterDueDateWithParticipationStartAfterDueDate() throws Exception {
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepository.saveAndFlush(afterDueDateParticipation);

        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", unsubmittedSubmission,
                ModelingSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionsWithResultsForParticipation_beforeSubmissionDueDate_returnsOnlyAthenaResults() throws Exception {
        // Set submission due date in the future
        classExercise.setDueDate(ZonedDateTime.now().plusHours(2));
        // Set assessment due date after the submission due date
        classExercise.setAssessmentDueDate(ZonedDateTime.now().plusHours(4));
        modelingExerciseUtilService.updateExercise(classExercise);

        // Create participation and submission for student1
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();

        // Create an Athena automatic result and a manual result
        // The manual result should not be returned before the assessment due date
        createResult(AssessmentType.AUTOMATIC_ATHENA, submission, participation, null);
        createResult(AssessmentType.MANUAL, submission, participation, null);

        List<Submission> submissions = request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.OK, Submission.class);

        // Verify that only the ATHENA result is returned
        assertThat(submissions).hasSize(1);
        Submission returnedSubmission = submissions.getFirst();
        assertThat(returnedSubmission.getResults()).hasSize(1);
        assertThat(returnedSubmission.getResults().getFirst().getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC_ATHENA);
        assertThat(returnedSubmission.getResults().getFirst().getAssessor()).isNull(); // Sensitive info filtered
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionsWithResultsForParticipation_afterSubmissionDueDate_returnsOnlyAthenaResults() throws Exception {
        // Given
        // Set submission due date in the past
        classExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        // Set assessment due date in the future
        classExercise.setAssessmentDueDate(ZonedDateTime.now().plusHours(2));
        modelingExerciseUtilService.updateExercise(classExercise);

        // Create participation and submission for student1
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission.setSubmissionDate(ZonedDateTime.now().minusMinutes(30)); // Submitted after the due date
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();

        // Create an Athena automatic result and a manual result
        createResult(AssessmentType.AUTOMATIC_ATHENA, submission, participation, null);
        createResult(AssessmentType.MANUAL, submission, participation, null);

        List<Submission> submissions = request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.OK, Submission.class);

        // Verify that only the ATHENA result is returned before the assessment due date
        assertThat(submissions).hasSize(1);
        Submission returnedSubmission = submissions.getFirst();
        assertThat(returnedSubmission.getResults()).hasSize(1);
        assertThat(returnedSubmission.getResults().getFirst().getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC_ATHENA);
        // Sensitive information should be filtered
        assertThat(returnedSubmission.getResults().getFirst().getAssessor()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionsWithResultsForParticipation_afterAssessmentDueDate_returnsAllResults() throws Exception {
        // Set submission due date in the past
        classExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        // Set assessment due date in the past
        classExercise.setAssessmentDueDate(ZonedDateTime.now().minusHours(1));
        modelingExerciseUtilService.updateExercise(classExercise);

        // Create participation and submission for student1
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission.setSubmissionDate(ZonedDateTime.now().minusHours(1).minusMinutes(30)); // Submitted after due date but before assessment due date
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();

        // Create an Athena automatic result and a manual result
        createResult(AssessmentType.AUTOMATIC_ATHENA, submission, participation, null);
        createResult(AssessmentType.MANUAL, submission, participation, null);

        List<Submission> submissions = request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.OK, Submission.class);

        // Verify that both results are returned after the assessment due date
        assertThat(submissions).hasSize(1);
        Submission returnedSubmission = submissions.getFirst();
        assertThat(returnedSubmission.getResults()).hasSize(2);
        // Sensitive information should be filtered
        returnedSubmission.getResults().forEach(result -> assertThat(result.getAssessor()).isNull());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionsWithResultsForParticipation_noResults_returnsEmptyList() throws Exception {
        // Create participation for student1
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");

        // Create a modeling submission without results
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission.setParticipation(participation);
        submission = modelingSubmissionRepo.save(submission);
        participation.addSubmission(submission);
        studentParticipationRepository.save(participation);

        List<Submission> submissions = request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.OK, Submission.class);

        assertThat(submissions).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionsWithResultsForParticipation_otherStudentParticipation_forbidden() throws Exception {
        // Given
        // Create participation for student2
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student2");

        // When & Then
        request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.FORBIDDEN, Submission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getSubmissionsWithResultsForParticipation_asTutor_returnsAllResults() throws Exception {
        // No need to adjust assessment due date; tutors have access before the due date
        // Create participation and submission for student1
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission.setParticipation(participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1"));
        submission = modelingSubmissionRepo.save(submission);
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();
        participation.addSubmission(submission);
        studentParticipationRepository.save(participation);

        // Create a manual result
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        createResult(AssessmentType.MANUAL, submission, participation, tutor);

        List<Submission> submissions = request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.OK, Submission.class);

        assertThat(submissions).hasSize(1);
        Submission returnedSubmission = submissions.getFirst();
        assertThat(returnedSubmission.getResults()).hasSize(1);
        // Verify that the tutor can see the manual result
        Result returnedResult = returnedSubmission.getResults().getFirst();
        assertThat(returnedResult.getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(returnedResult.getAssessor()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionsWithResultsForParticipation_notModelingExercise_badRequest() throws Exception {
        // Given
        // Initialize and save a text exercise with required dates
        ZonedDateTime now = ZonedDateTime.now();
        textExercise = textExerciseUtilService.createIndividualTextExercise(course, now.minusDays(1), now.plusDays(1), now.plusDays(2));
        exerciseRepository.save(textExercise);

        // Create participation for student1 with the text exercise
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");

        // When & Then
        // Attempt to get submissions for a non-modeling exercise
        request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.BAD_REQUEST, Submission.class);
    }

    private void checkDetailsHidden(ModelingSubmission submission, boolean isStudent) {
        assertThat(submission.getParticipation().getSubmissions()).isNullOrEmpty();
        if (isStudent) {
            var modelingExercise = ((ModelingExercise) submission.getParticipation().getExercise());
            assertThat(modelingExercise.getExampleSolutionModel()).isNullOrEmpty();
            assertThat(modelingExercise.getExampleSolutionExplanation()).isNullOrEmpty();
            assertThat(submission.getLatestResult()).isNull();
        }
    }

    private ModelingSubmission performInitialModelSubmission(Long exerciseId, ModelingSubmission submission) throws Exception {
        return request.postWithResponseBody("/api/modeling/exercises/" + exerciseId + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.OK);
    }

    private ModelingSubmission performUpdateOnModelSubmission(Long exerciseId, ModelingSubmission submission) throws Exception {
        return request.putWithResponseBody("/api/modeling/exercises/" + exerciseId + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.OK);
    }

    private ModelingSubmission generateSubmittedSubmission() {
        return ParticipationFactory.generateModelingSubmission(emptyModel, true);
    }

    private ModelingSubmission generateUnsubmittedSubmission() {
        return ParticipationFactory.generateModelingSubmission(emptyModel, false);
    }

    private void createNineLockedSubmissionsForDifferentExercisesAndUsers(String assessor) {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(classExercise, submission, TEST_PREFIX + "student1", assessor);
        submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(classExercise, submission, TEST_PREFIX + "student2", assessor);
        submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(classExercise, submission, TEST_PREFIX + "student3", assessor);
        submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(activityExercise, submission, TEST_PREFIX + "student1", assessor);
        submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(activityExercise, submission, TEST_PREFIX + "student2", assessor);
        submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(activityExercise, submission, TEST_PREFIX + "student3", assessor);
        submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(objectExercise, submission, TEST_PREFIX + "student1", assessor);
        submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(objectExercise, submission, TEST_PREFIX + "student2", assessor);
        submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(objectExercise, submission, TEST_PREFIX + "student3", assessor);
    }

    private void createTenLockedSubmissionsForDifferentExercisesAndUsers(String assessor) {
        createNineLockedSubmissionsForDifferentExercisesAndUsers(assessor);
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(useCaseExercise, submission, TEST_PREFIX + "student1", assessor);
    }

    private Result createResult(AssessmentType assessmentType, ModelingSubmission submission, StudentParticipation participation, User assessor) {
        Result result = new Result();
        result.setAssessmentType(assessmentType);
        result.setCompletionDate(ZonedDateTime.now());
        submission.setParticipation(participation);
        result.setSubmission(submission);
        if (assessor != null) {
            result.setAssessor(assessor);
        }
        result.setExerciseId(participation.getExercise().getId());
        resultRepository.save(result);
        submission.addResult(result);
        modelingSubmissionRepo.save(submission);
        return result;
    }
}
