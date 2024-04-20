package de.tum.in.www1.artemis.exercise.modelingexercise;

import static org.assertj.core.api.Assertions.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingSubmissionElement;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.TestResourceUtils;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class ModelingSubmissionIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "modelingsubmissionintegration";

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ModelingSubmissionRepository modelingSubmissionRepo;

    @Autowired
    private SubmissionVersionRepository submissionVersionRepository;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private CompassService compassService;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

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

    private String validSameModel;

    private TextExercise textExercise;

    private Course course;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 3, 1, 0, 1);
        course = modelingExerciseUtilService.addCourseWithDifferentModelingExercises();
        classExercise = exerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        activityExercise = exerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ActivityDiagram");
        objectExercise = exerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "ObjectDiagram");
        useCaseExercise = exerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "UseCaseDiagram");
        finishedExercise = exerciseUtilService.findModelingExerciseWithTitle(course.getExercises(), "finished");
        afterDueDateParticipation = participationUtilService.createAndSaveParticipationForExercise(finishedExercise, TEST_PREFIX + "student3");
        participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student3");

        emptyModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/empty-class-diagram.json");
        validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        validSameModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727-copy.json");
        submittedSubmission = generateSubmittedSubmission();
        unsubmittedSubmission = generateUnsubmittedSubmission();

        Course course2 = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        textExercise = (TextExercise) new ArrayList<>(course2.getExercises()).get(0);

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
        request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void createModelingSubmission_studentNotInCourse() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.FORBIDDEN);
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
        request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.OK);

        // should be too large
        char[] charsModelTooLarge = new char[(int) (Constants.MAX_SUBMISSION_MODEL_LENGTH + 1)];
        Arrays.fill(charsModelTooLarge, 'a');
        submission.setModel(new String(charsModelTooLarge));
        request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.BAD_REQUEST);
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
        exerciseRepo.save(useCaseExercise);
        Team team = new Team();
        team.setName("Team");
        team.setShortName(TEST_PREFIX + "team");
        team.setExercise(useCaseExercise);
        team.addStudents(userRepo.findOneByLogin(TEST_PREFIX + "student1").orElseThrow());
        team.addStudents(userRepo.findOneByLogin(TEST_PREFIX + "student2").orElseThrow());
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
        request.putWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", returnedSubmission, ModelingSubmission.class, HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validModel);

        returnedSubmission.setSubmitted(true);
        returnedSubmission = request.putWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", returnedSubmission, ModelingSubmission.class,
                HttpStatus.OK);
        StudentParticipation studentParticipation = (StudentParticipation) returnedSubmission.getParticipation();
        assertThat(studentParticipation.getResults()).as("do not send old results to the client").isEmpty();
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
        submission.addResult(result);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
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

        List<ModelingSubmission> submissions = request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class);

        assertThat(submissions).as("contains both submissions").containsExactlyInAnyOrder(submission1, submission2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void getAllSubmissionsOfExercise_instructorNotInCourse() throws Exception {
        request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllSubmissionsOfExercise_assessedByTutor() throws Exception {
        List<ModelingSubmission> submissions = request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions?assessedByTutor=true", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(submissions).as("does not have a modeling submission assessed by the tutor").isEmpty();

        modelingExerciseUtilService.addModelingSubmissionWithFinishedResultAndAssessor(classExercise, submittedSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        submissions = request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions?assessedByTutor=true", HttpStatus.OK, ModelingSubmission.class);
        assertThat(submissions).as("has a modeling submission assessed by the tutor").hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void getAllSubmissionsOfExercise_assessedByTutor_instructorNotInCourse() throws Exception {
        request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions?assessedByTutor=true", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getAllSubmissionsOfExerciseAsStudent() throws Exception {
        modelingExerciseUtilService.addModelingSubmission(classExercise, submittedSubmission, TEST_PREFIX + "student1");
        modelingExerciseUtilService.addModelingSubmission(classExercise, unsubmittedSubmission, TEST_PREFIX + "student2");

        request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.FORBIDDEN, ModelingSubmission.class);
        request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions?submittedOnly=true", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllSubmittedSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = modelingExerciseUtilService.addModelingSubmission(classExercise, submittedSubmission, TEST_PREFIX + "student1");
        modelingExerciseUtilService.addModelingSubmission(classExercise, unsubmittedSubmission, TEST_PREFIX + "student2");
        ModelingSubmission submission3 = modelingExerciseUtilService.addModelingSubmission(classExercise, generateSubmittedSubmission(), TEST_PREFIX + "student3");

        List<ModelingSubmission> submissions = request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions?submittedOnly=true", HttpStatus.OK,
                ModelingSubmission.class);

        assertThat(submissions).as("contains only submitted submission").containsExactlyInAnyOrder(submission1, submission3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmission() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        ModelingSubmission storedSubmission = request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmission.class);

        assertThat(storedSubmission.getLatestResult()).as("result has been set").isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutResults() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        ModelingSubmission storedSubmission = request.get("/api/modeling-submissions/" + submission.getId() + "?withoutResults=true", HttpStatus.OK, ModelingSubmission.class);

        assertThat(storedSubmission.getLatestResult()).as("result has not been set").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void getModelSubmission_tutorNotInCourse() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getModelSubmissionWithResult_involved_allowed() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        PlagiarismComparison<ModelingSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        PlagiarismSubmission<ModelingSubmissionElement> submissionA = new PlagiarismSubmission<>();
        submissionA.setStudentLogin(TEST_PREFIX + "student1");
        submissionA.setSubmissionId(submission.getId());
        plagiarismComparison.setSubmissionA(submissionA);
        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(classExercise);
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
        Post post = new Post();
        post.setAuthor(userRepo.getUserByLoginElseThrow(TEST_PREFIX + "instructor1"));
        post.setTitle("Title Plagiarism Case Post");
        post.setContent("Content Plagiarism Case Post");
        post.setVisibleForStudents(true);
        post.setPlagiarismCase(plagiarismCase);
        postRepository.save(post);
        submissionA.setPlagiarismCase(plagiarismCase);
        plagiarismComparisonRepository.save(plagiarismComparison);

        var submissionResult = request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmission.class);

        assertThat(submissionResult.getParticipation()).as("Should anonymize participation").isNull();
        assertThat(submissionResult.getResults()).as("Should anonymize results").isEmpty();
        assertThat(submissionResult.getSubmissionDate()).as("Should anonymize submission date").isNull();
    }

    @Test
    @WithMockUser(value = TEST_PREFIX + "student1", roles = "USER")
    void getModelSubmissionWithResult_notInvolved_notAllowed() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(value = TEST_PREFIX + "student1", roles = "USER")
    void getModelSubmissionWithResult_notOwner_beforeDueDate_notAllowed() throws Exception {
        var submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student2");

        var plagiarismComparison = new PlagiarismComparison<ModelingSubmissionElement>();
        var submissionA = new PlagiarismSubmission<ModelingSubmissionElement>();
        submissionA.setStudentLogin(TEST_PREFIX + "student2");
        submissionA.setSubmissionId(submission.getId());
        plagiarismComparison.setSubmissionA(submissionA);

        plagiarismComparisonRepository.save(plagiarismComparison);

        request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmission_lockLimitReached_success() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        createNineLockedSubmissionsForDifferentExercisesAndUsers(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(useCaseExercise, submission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        ModelingSubmission storedSubmission = request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmission.class);

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

        request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.BAD_REQUEST, ModelingSubmission.class);
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
        ModelingSubmission storedSubmission = request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmission.class, params);

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
        request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmission.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmission storedSubmission = request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK,
                ModelingSubmission.class);

        assertThat(storedSubmission).as("submission was found").isEqualToIgnoringGivenFields(submission, "results", "submissionDate");
        assertThat(storedSubmission.getSubmissionDate()).as("submission date is correct").isEqualToIgnoringNanos(submission.getSubmissionDate());
        assertThat(storedSubmission.getLatestResult()).as("result is not set").isNull();
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithSimilarElements() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        ModelingSubmission submission2 = ParticipationFactory.generateModelingSubmission(validSameModel, true);
        modelingExerciseUtilService.addModelingSubmission(classExercise, submission2, TEST_PREFIX + "student2");

        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        compassService.build(classExercise);

        ModelingSubmission storedSubmission = request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment?lock=true", HttpStatus.OK,
                ModelingSubmission.class);

        assertThat(storedSubmission).as("submission was found").isNotNull();
        assertThat(storedSubmission.getSimilarElements()).as("similarity count is set").isNotNull();
        assertThat(storedSubmission.getSimilarElements()).as("similarity count is set").hasSize(10);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_wrongExerciseType() throws Exception {
        request.get("/api/exercises/" + textExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.BAD_REQUEST, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_lockSubmission() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmission storedSubmission = request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment?lock=true", HttpStatus.OK,
                ModelingSubmission.class);

        // set dates to UTC and round to milliseconds for comparison
        submission.setSubmissionDate(ZonedDateTime.ofInstant(submission.getSubmissionDate().truncatedTo(ChronoUnit.SECONDS).toInstant(), ZoneId.of("UTC")));
        storedSubmission.setSubmissionDate(ZonedDateTime.ofInstant(storedSubmission.getSubmissionDate().truncatedTo(ChronoUnit.SECONDS).toInstant(), ZoneId.of("UTC")));
        assertThat(storedSubmission).as("submission was found").isEqualToIgnoringGivenFields(submission, "results", "similarElementCounts");
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

        var response = request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK, ModelingSubmission.class);
        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_noSubmissionWithoutAssessment_null() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(classExercise, submission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        var response = request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK, ModelingSubmission.class);
        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_dueDateNotOver() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void getModelSubmissionWithoutAssessment_notTutorInCourse() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getModelSubmissionWithoutAssessment_asStudent_forbidden() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_testLockLimit() throws Exception {
        createNineLockedSubmissionsForDifferentExercisesAndUsers(TEST_PREFIX + "tutor1");
        ModelingSubmission newSubmission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(useCaseExercise, newSubmission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(useCaseExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmission storedSubmission = request.get("/api/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment?lock=true", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(storedSubmission).as("submission was found").isNotNull();
        request.get("/api/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.BAD_REQUEST, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllModelingSubmissions() throws Exception {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> modelingSubmissionRepo.findByIdElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> modelingSubmissionRepo.findByIdWithEagerResultAndFeedbackAndAssessorAndParticipationResultsElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> modelingSubmissionRepo.findByIdWithEagerResultAndFeedbackElseThrow(Long.MAX_VALUE));

        createNineLockedSubmissionsForDifferentExercisesAndUsers(TEST_PREFIX + "tutor1");
        ModelingSubmission newSubmission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(useCaseExercise, newSubmission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(useCaseExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmission storedSubmission = request.get("/api/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment?lock=true", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(storedSubmission).as("submission was found").isNotNull();
        request.get("/api/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.BAD_REQUEST, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getModelSubmissionForModelingEditor() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = (ModelingSubmission) modelingExerciseUtilService.addModelingSubmissionWithFinishedResultAndAssessor(classExercise, submission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");

        ModelingSubmission receivedSubmission = request.get("/api/participations/" + submission.getParticipation().getId() + "/latest-modeling-submission", HttpStatus.OK,
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
        request.get("/api/participations/" + submission.getParticipation().getId() + "/latest-modeling-submission", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionForModelingEditor_badRequest() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        StudentParticipation participation = new StudentParticipation();
        participation.setParticipant(user);
        participation.setExercise(null);
        StudentParticipation studentParticipation = studentParticipationRepository.save(participation);
        request.get("/api/participations/" + studentParticipation.getId() + "/latest-modeling-submission", HttpStatus.BAD_REQUEST, ModelingSubmission.class);

        participation.setExercise(textExercise);
        studentParticipation = studentParticipationRepository.save(participation);
        request.get("/api/participations/" + studentParticipation.getId() + "/latest-modeling-submission", HttpStatus.BAD_REQUEST, ModelingSubmission.class);
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
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().get(0);

        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ActivityDiagram, exerciseGroup);
        exerciseGroup.addExercise(modelingExercise);
        exerciseGroupRepository.save(exerciseGroup);
        modelingExercise = exerciseRepo.save(modelingExercise);

        examRepository.save(exam);

        ModelingSubmission modelingSubmission = ParticipationFactory.generateModelingSubmission("Some text", true);
        modelingSubmission = modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(modelingExercise, modelingSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        request.get("/api/participations/" + modelingSubmission.getParticipation().getId() + "/latest-modeling-submission", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getModelingResult_testExam() throws Exception {
        // create test exam
        Exam exam = examUtilService.addTestExamWithExerciseGroup(course, true);
        exam.setStartDate(ZonedDateTime.now().minusHours(2));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        exam.setVisibleDate(ZonedDateTime.now().minusHours(3));

        ExerciseGroup exerciseGroup = exam.getExerciseGroups().get(0);
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ActivityDiagram, exerciseGroup);
        exerciseGroup.addExercise(modelingExercise);
        exerciseGroupRepository.save(exerciseGroup);
        modelingExercise = exerciseRepo.save(modelingExercise);

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
        var submission = request.get("/api/participations/" + modelingSubmission.getParticipation().getId() + "/latest-modeling-submission", HttpStatus.OK,
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
        ModelingSubmission returnedSubmission = request.get("/api/participations/" + studentParticipation.getId() + "/latest-modeling-submission", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(returnedSubmission).as("new submission is created").isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionForModelingEditor_unfinishedAssessment() throws Exception {
        StudentParticipation studentParticipation = participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        modelingExerciseUtilService.addModelingSubmissionWithEmptyResult(classExercise, "", TEST_PREFIX + "student1");

        ModelingSubmission returnedSubmission = request.get("/api/participations/" + studentParticipation.getId() + "/latest-modeling-submission", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(returnedSubmission.getLatestResult()).as("the result is not sent to the client if the assessment is not finished").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_afterDueDate_forbidden() throws Exception {
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now().minusDays(2));
        studentParticipationRepository.saveAndFlush(afterDueDateParticipation);
        request.post("/api/exercises/" + finishedExercise.getId() + "/modeling-submissions", submittedSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_beforeDueDate_allowed() throws Exception {
        ModelingSubmission submission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submittedSubmission,
                ModelingSubmission.class, HttpStatus.OK);

        assertThat(submission.getSubmissionDate()).isCloseTo(ZonedDateTime.now(), within(500, ChronoUnit.MILLIS));
        assertThat(submission.getParticipation().getInitializationState()).isEqualTo(InitializationState.FINISHED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_beforeDueDateSecondSubmission_allowed() throws Exception {
        submittedSubmission.setModel(validModel);
        submittedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submittedSubmission, ModelingSubmission.class,
                HttpStatus.OK);

        final var submissionInDb = modelingSubmissionRepo.findById(submittedSubmission.getId());
        assertThat(submissionInDb).isPresent();
        assertThat(submissionInDb.get().getModel()).isEqualTo(validModel);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_afterDueDateWithParticipationStartAfterDueDate_allowed() throws Exception {
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepository.saveAndFlush(afterDueDateParticipation);

        request.postWithoutLocation("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submittedSubmission, HttpStatus.OK, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void saveExercise_beforeDueDate() throws Exception {
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", unsubmittedSubmission,
                ModelingSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void saveExercise_afterDueDateWithParticipationStartAfterDueDate() throws Exception {
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepository.saveAndFlush(afterDueDateParticipation);

        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", unsubmittedSubmission,
                ModelingSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isFalse();
    }

    private void checkDetailsHidden(ModelingSubmission submission, boolean isStudent) {
        assertThat(submission.getParticipation().getSubmissions()).isNullOrEmpty();
        assertThat(submission.getParticipation().getResults()).isNullOrEmpty();
        if (isStudent) {
            var modelingExercise = ((ModelingExercise) submission.getParticipation().getExercise());
            assertThat(modelingExercise.getExampleSolutionModel()).isNullOrEmpty();
            assertThat(modelingExercise.getExampleSolutionExplanation()).isNullOrEmpty();
            assertThat(submission.getLatestResult()).isNull();
        }
    }

    private ModelingSubmission performInitialModelSubmission(Long exerciseId, ModelingSubmission submission) throws Exception {
        return request.postWithResponseBody("/api/exercises/" + exerciseId + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.OK);
    }

    private ModelingSubmission performUpdateOnModelSubmission(Long exerciseId, ModelingSubmission submission) throws Exception {
        return request.putWithResponseBody("/api/exercises/" + exerciseId + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.OK);
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
}
