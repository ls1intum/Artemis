package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

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
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.service.compass.CompassService;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class ModelingSubmissionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
    private CompassService compassService;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PostRepository postRepository;

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
        database.addUsers(3, 1, 0, 1);
        course = database.addCourseWithDifferentModelingExercises();
        classExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        activityExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ActivityDiagram");
        objectExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ObjectDiagram");
        useCaseExercise = database.findModelingExerciseWithTitle(course.getExercises(), "UseCaseDiagram");
        finishedExercise = database.findModelingExerciseWithTitle(course.getExercises(), "finished");
        afterDueDateParticipation = database.createAndSaveParticipationForExercise(finishedExercise, "student3");
        database.createAndSaveParticipationForExercise(classExercise, "student3");

        emptyModel = FileUtils.loadFileFromResources("test-data/model-submission/empty-class-diagram.json");
        validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        validSameModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727-copy.json");
        submittedSubmission = generateSubmittedSubmission();
        unsubmittedSubmission = generateUnsubmittedSubmission();

        Course course2 = database.addCourseWithOneReleasedTextExercise();
        textExercise = (TextExercise) new ArrayList<>(course2.getExercises()).get(0);

        // Add users that are not in the course
        userRepo.save(ModelFactory.generateActivatedUser("student4"));
        userRepo.save(ModelFactory.generateActivatedUser("tutor2"));
        userRepo.save(ModelFactory.generateActivatedUser("instructor2"));
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1")
    void createModelingSubmission_badRequest() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        modelingSubmissionRepo.save(submission);
        request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student4", roles = "USER")
    void createModelingSubmission_studentNotInCourse() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission, ModelingSubmission.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1")
    void saveAndSubmitModelingSubmission_tooLarge() throws Exception {
        database.createAndSaveParticipationForExercise(classExercise, "student1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyModel, false);
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
    @WithMockUser(username = "student1")
    void saveAndSubmitModelingSubmission_classDiagram() throws Exception {
        database.createAndSaveParticipationForExercise(classExercise, "student1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(classExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyModel);
        checkDetailsHidden(returnedSubmission, true);

        returnedSubmission.setModel(validModel);
        returnedSubmission.setSubmitted(true);
        returnedSubmission = performUpdateOnModelSubmission(classExercise.getId(), returnedSubmission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(username = "student1")
    void saveAndSubmitModelingSubmission_activityDiagram() throws Exception {
        database.createAndSaveParticipationForExercise(activityExercise, "student1");
        String emptyActivityModel = FileUtils.loadFileFromResources("test-data/model-submission/empty-activity-diagram.json");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyActivityModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(activityExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyActivityModel);
        checkDetailsHidden(returnedSubmission, true);

        String validActivityModel = FileUtils.loadFileFromResources("test-data/model-submission/example-activity-diagram.json");
        returnedSubmission.setModel(validActivityModel);
        returnedSubmission.setSubmitted(true);
        returnedSubmission = performUpdateOnModelSubmission(activityExercise.getId(), returnedSubmission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validActivityModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(username = "student1")
    void saveAndSubmitModelingSubmission_objectDiagram() throws Exception {
        database.createAndSaveParticipationForExercise(objectExercise, "student1");
        String emptyObjectModel = FileUtils.loadFileFromResources("test-data/model-submission/empty-object-diagram.json");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyObjectModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(objectExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyObjectModel);
        checkDetailsHidden(returnedSubmission, true);

        String validObjectModel = FileUtils.loadFileFromResources("test-data/model-submission/object-model.json");
        returnedSubmission.setModel(validObjectModel);
        returnedSubmission.setSubmitted(true);
        returnedSubmission = performUpdateOnModelSubmission(objectExercise.getId(), returnedSubmission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validObjectModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(username = "student1")
    void saveAndSubmitModelingSubmission_useCaseDiagram() throws Exception {
        database.createAndSaveParticipationForExercise(useCaseExercise, "student1");
        String emptyUseCaseModel = FileUtils.loadFileFromResources("test-data/model-submission/empty-use-case-diagram.json");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyUseCaseModel, false);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(useCaseExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyUseCaseModel);
        checkDetailsHidden(returnedSubmission, true);

        String validUseCaseModel = FileUtils.loadFileFromResources("test-data/model-submission/use-case-model.json");
        returnedSubmission.setModel(validUseCaseModel);
        returnedSubmission.setSubmitted(true);
        returnedSubmission = performUpdateOnModelSubmission(useCaseExercise.getId(), returnedSubmission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validUseCaseModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(username = "student1")
    void saveAndSubmitModelingSubmission_isTeamMode() throws Exception {
        useCaseExercise.setMode(ExerciseMode.TEAM);
        exerciseRepo.save(useCaseExercise);
        Team team = new Team();
        team.setName("Team");
        team.setShortName("team");
        team.setExercise(useCaseExercise);
        team.addStudents(userRepo.findOneByLogin("student1").orElseThrow());
        team.addStudents(userRepo.findOneByLogin("student2").orElseThrow());
        teamRepository.save(useCaseExercise, team);

        database.addTeamParticipationForExercise(useCaseExercise, team.getId());
        String emptyUseCaseModel = FileUtils.loadFileFromResources("test-data/model-submission/empty-use-case-diagram.json");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyUseCaseModel, false);
        submission.setExplanationText("This is a use case diagram.");
        ModelingSubmission returnedSubmission = performInitialModelSubmission(useCaseExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyUseCaseModel);

        database.changeUser("student1");
        Optional<SubmissionVersion> version = submissionVersionRepository.findLatestVersion(returnedSubmission.getId());
        assertThat(version).as("submission version was created").isNotEmpty();
        assertThat(version.get().getAuthor().getLogin()).as("submission version has correct author").isEqualTo("student1");
        assertThat(version.get().getContent()).as("submission version has correct content")
                .isEqualTo("Model: " + returnedSubmission.getModel() + "; Explanation: " + returnedSubmission.getExplanationText());
        assertThat(version.get().getCreatedDate()).isNotNull();
        assertThat(version.get().getLastModifiedDate()).isNotNull();

        database.changeUser("student2");
        String validUseCaseModel = FileUtils.loadFileFromResources("test-data/model-submission/use-case-model.json");
        returnedSubmission.setModel(validUseCaseModel);
        returnedSubmission.setSubmitted(true);
        returnedSubmission = performUpdateOnModelSubmission(useCaseExercise.getId(), returnedSubmission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validUseCaseModel);
        checkDetailsHidden(returnedSubmission, true);

        database.changeUser("student2");
        version = submissionVersionRepository.findLatestVersion(returnedSubmission.getId());
        assertThat(version).as("submission version was created").isNotEmpty();
        assertThat(version.get().getAuthor().getLogin()).as("submission version has correct author").isEqualTo("student2");
        assertThat(version.get().getContent()).as("submission version has correct content")
                .isEqualTo("Model: " + returnedSubmission.getModel() + "; Explanation: " + returnedSubmission.getExplanationText());

        returnedSubmission = performUpdateOnModelSubmission(useCaseExercise.getId(), returnedSubmission);
        database.changeUser("student2");
        Optional<SubmissionVersion> newVersion = submissionVersionRepository.findLatestVersion(returnedSubmission.getId());
        assertThat(newVersion.orElseThrow().getId()).as("submission version was not created").isEqualTo(version.get().getId());
    }

    @Test
    @WithMockUser(username = "student2")
    void updateModelSubmission() throws Exception {
        database.createAndSaveParticipationForExercise(classExercise, "student2");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(emptyModel, true);
        ModelingSubmission returnedSubmission = performInitialModelSubmission(classExercise.getId(), submission);
        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), emptyModel);

        returnedSubmission.setModel(validModel);
        returnedSubmission.setSubmitted(false);
        request.putWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", returnedSubmission, ModelingSubmission.class, HttpStatus.OK);

        database.checkModelingSubmissionCorrectlyStored(returnedSubmission.getId(), validModel);

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
    @WithMockUser(username = "student1")
    void injectResultOnSubmissionUpdate() throws Exception {
        User user = database.getUserByLogin("student1");
        database.createAndSaveParticipationForExercise(classExercise, "student1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, false);
        Result result = new Result();
        result.setScore(100D);
        result.setRated(true);
        result.setAssessor(user);
        submission.addResult(result);
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class);

        database.changeUser("student1");
        storedSubmission = modelingSubmissionRepo.findByIdWithEagerResult(storedSubmission.getId()).get();
        assertThat(storedSubmission.getLatestResult()).as("submission still unrated").isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getAllSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmission(classExercise, submittedSubmission, "student1");
        ModelingSubmission submission2 = database.addModelingSubmission(classExercise, unsubmittedSubmission, "student2");

        List<ModelingSubmission> submissions = request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmission.class);

        assertThat(submissions).as("contains both submissions").containsExactlyInAnyOrder(submission1, submission2);
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    void getAllSubmissionsOfExercise_instructorNotInCourse() throws Exception {
        request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getAllSubmissionsOfExercise_assessedByTutor() throws Exception {
        List<ModelingSubmission> submissions = request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions?assessedByTutor=true", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(submissions).as("does not have a modeling submission assessed by the tutor").isEmpty();

        database.addModelingSubmissionWithFinishedResultAndAssessor(classExercise, submittedSubmission, "student1", "tutor1");
        submissions = request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions?assessedByTutor=true", HttpStatus.OK, ModelingSubmission.class);
        assertThat(submissions).as("has a modeling submission assessed by the tutor").hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void getAllSubmissionsOfExercise_assessedByTutor_instructorNotInCourse() throws Exception {
        request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions?assessedByTutor=true", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "student1")
    void getAllSubmissionsOfExerciseAsStudent() throws Exception {
        database.addModelingSubmission(classExercise, submittedSubmission, "student1");
        database.addModelingSubmission(classExercise, unsubmittedSubmission, "student2");

        request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.FORBIDDEN, ModelingSubmission.class);
        request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions?submittedOnly=true", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getAllSubmittedSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = database.addModelingSubmission(classExercise, submittedSubmission, "student1");
        database.addModelingSubmission(classExercise, unsubmittedSubmission, "student2");
        ModelingSubmission submission3 = database.addModelingSubmission(classExercise, generateSubmittedSubmission(), "student3");

        List<ModelingSubmission> submissions = request.getList("/api/exercises/" + classExercise.getId() + "/modeling-submissions?submittedOnly=true", HttpStatus.OK,
                ModelingSubmission.class);

        assertThat(submissions).as("contains only submitted submission").containsExactlyInAnyOrder(submission1, submission3);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getModelSubmission() throws Exception {
        User user = database.getUserByLogin("tutor1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student1");

        ModelingSubmission storedSubmission = request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmission.class);

        assertThat(storedSubmission.getLatestResult()).as("result has been set").isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getModelSubmissionWithoutResults() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student1");

        ModelingSubmission storedSubmission = request.get("/api/modeling-submissions/" + submission.getId() + "?withoutResults=true", HttpStatus.OK, ModelingSubmission.class);

        assertThat(storedSubmission.getLatestResult()).as("result has not been set").isNull();
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void getModelSubmission_tutorNotInCourse() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student1");

        request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void getModelSubmissionWithResult_involved_allowed() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student1");
        PlagiarismComparison<ModelingSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        PlagiarismSubmission<ModelingSubmissionElement> submissionA = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        submissionA.setSubmissionId(submission.getId());
        plagiarismComparison.setSubmissionA(submissionA);
        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
        Post post = new Post();
        post.setAuthor(userRepo.getUserByLoginElseThrow("instructor1"));
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
    @WithMockUser(value = "student1", roles = "USER")
    void getModelSubmissionWithResult_notInvolved_notAllowed() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student1");
        request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getModelSubmission_lockLimitReached_success() throws Exception {
        User user = database.getUserByLogin("tutor1");
        createNineLockedSubmissionsForDifferentExercisesAndUsers("tutor1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmissionWithResultAndAssessor(useCaseExercise, submission, "student1", "tutor1");

        ModelingSubmission storedSubmission = request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmission.class);

        assertThat(storedSubmission.getLatestResult()).as("result has been set").isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getModelSubmission_lockLimitReached_badRequest() throws Exception {
        createTenLockedSubmissionsForDifferentExercisesAndUsers("tutor1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmission(useCaseExercise, submission, "student2");

        request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.BAD_REQUEST, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "TA")
    void getModelingSubmissionWithResultId() throws Exception {
        User user = database.getUserByLogin("tutor1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = (ModelingSubmission) database.addSubmissionWithTwoFinishedResultsWithAssessor(classExercise, submission, "student1", "tutor1");
        Result storedResult = submission.getResultForCorrectionRound(1);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("resultId", String.valueOf(storedResult.getId()));
        ModelingSubmission storedSubmission = request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmission.class, params);

        assertThat(storedSubmission.getResults()).isNotNull();
        assertThat(storedSubmission.getResults()).contains(storedResult);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getModelingSubmissionWithResultIdAsTutor_badRequest() throws Exception {
        User user = database.getUserByLogin("tutor1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = (ModelingSubmission) database.addModelingSubmissionWithFinishedResultAndAssessor(classExercise, submission, "student1", "tutor1");
        Result storedResult = submission.getResultForCorrectionRound(0);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("resultId", String.valueOf(storedResult.getId()));
        request.get("/api/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmission.class, params);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student1");

        database.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmission storedSubmission = request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK,
                ModelingSubmission.class);

        // set dates to UTC and round to milliseconds for comparison
        submission.setSubmissionDate(ZonedDateTime.ofInstant(submission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        storedSubmission.setSubmissionDate(ZonedDateTime.ofInstant(storedSubmission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(storedSubmission).as("submission was found").isEqualToIgnoringGivenFields(submission, "results");
        assertThat(storedSubmission.getLatestResult()).as("result is not set").isNull();
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getModelSubmissionWithSimilarElements() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmission(classExercise, submission, "student1");
        ModelingSubmission submission2 = ModelFactory.generateModelingSubmission(validSameModel, true);
        database.addModelingSubmission(classExercise, submission2, "student2");

        database.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        compassService.build(classExercise);

        ModelingSubmission storedSubmission = request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment?lock=true", HttpStatus.OK,
                ModelingSubmission.class);

        assertThat(storedSubmission).as("submission was found").isNotNull();
        assertThat(storedSubmission.getSimilarElements()).as("similarity count is set").isNotNull();
        assertThat(storedSubmission.getSimilarElements()).as("similarity count is set").hasSize(10);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_wrongExerciseType() throws Exception {
        request.get("/api/exercises/" + textExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.BAD_REQUEST, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_lockSubmission() throws Exception {
        User user = database.getUserByLogin("tutor1");
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student1");
        database.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

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
    @WithMockUser(username = "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_noSubmittedSubmission_notFound() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, false);
        database.addModelingSubmission(classExercise, submission, "student1");
        database.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        var response = request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK, ModelingSubmission.class);
        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_noSubmissionWithoutAssessment_notFound() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, "student1", "tutor1");
        database.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        var response = request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK, ModelingSubmission.class);
        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_dueDateNotOver() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmission(classExercise, submission, "student1");

        request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void getModelSubmissionWithoutAssessment_notTutorInCourse() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmission(classExercise, submission, "student1");
        database.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "student1")
    void getModelSubmissionWithoutAssessment_asStudent_forbidden() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmission(classExercise, submission, "student1");
        database.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_testLockLimit() throws Exception {
        createNineLockedSubmissionsForDifferentExercisesAndUsers("tutor1");
        ModelingSubmission newSubmission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmission(useCaseExercise, newSubmission, "student1");
        database.updateExerciseDueDate(useCaseExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmission storedSubmission = request.get("/api/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment?lock=true", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(storedSubmission).as("submission was found").isNotNull();
        request.get("/api/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.BAD_REQUEST, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getAllModelingSubmissions() throws Exception {
        assertThrows(EntityNotFoundException.class, () -> modelingSubmissionRepo.findByIdElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> modelingSubmissionRepo.findByIdWithEagerResultAndFeedbackAndAssessorAndParticipationResultsElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> modelingSubmissionRepo.findByIdWithEagerResultAndFeedbackElseThrow(Long.MAX_VALUE));

        createNineLockedSubmissionsForDifferentExercisesAndUsers("tutor1");
        ModelingSubmission newSubmission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmission(useCaseExercise, newSubmission, "student1");
        database.updateExerciseDueDate(useCaseExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmission storedSubmission = request.get("/api/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment?lock=true", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(storedSubmission).as("submission was found").isNotNull();
        request.get("/api/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.BAD_REQUEST, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "student1")
    void getModelSubmissionForModelingEditor() throws Exception {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = (ModelingSubmission) database.addModelingSubmissionWithFinishedResultAndAssessor(classExercise, submission, "student1", "tutor1");

        ModelingSubmission receivedSubmission = request.get("/api/participations/" + submission.getParticipation().getId() + "/latest-modeling-submission", HttpStatus.OK,
                ModelingSubmission.class);

        // set dates to UTC and round to milliseconds for comparison
        submission.setSubmissionDate(ZonedDateTime.ofInstant(submission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        receivedSubmission.setSubmissionDate(ZonedDateTime.ofInstant(receivedSubmission.getSubmissionDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(receivedSubmission).as("submission was found").isEqualTo(submission);
        assertThat(receivedSubmission.getLatestResult()).as("result is set").isNotNull();
        assertThat(receivedSubmission.getLatestResult().getAssessor()).as("assessor is hidden").isNull();

        // students can only see their own models
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        submission = database.addModelingSubmission(classExercise, submission, "student2");
        request.get("/api/participations/" + submission.getParticipation().getId() + "/latest-modeling-submission", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "student1")
    void getSubmissionForModelingEditor_badRequest() throws Exception {
        User user = database.getUserByLogin("student1");
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
    @WithMockUser(username = "student1", roles = "USER")
    void getModelingResult_BeforeExamPublishDate_Forbidden() throws Exception {
        // create exam
        Exam exam = database.addExamWithExerciseGroup(course, true);
        exam.setStartDate(ZonedDateTime.now().minusHours(2));
        exam.setEndDate(ZonedDateTime.now().minusHours(1));
        exam.setVisibleDate(ZonedDateTime.now().minusHours(3));
        exam.setPublishResultsDate(ZonedDateTime.now().plusHours(3));

        // creating exercise
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().get(0);

        ModelingExercise modelingExercise = ModelFactory.generateModelingExerciseForExam(DiagramType.ActivityDiagram, exerciseGroup);
        exerciseGroup.addExercise(modelingExercise);
        exerciseGroupRepository.save(exerciseGroup);
        modelingExercise = exerciseRepo.save(modelingExercise);

        examRepository.save(exam);

        ModelingSubmission modelingSubmission = ModelFactory.generateModelingSubmission("Some text", true);
        modelingSubmission = database.addModelingSubmissionWithResultAndAssessor(modelingExercise, modelingSubmission, "student1", "tutor1");
        request.get("/api/participations/" + modelingSubmission.getParticipation().getId() + "/latest-modeling-submission", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "student1")
    void getSubmissionForModelingEditor_emptySubmission() throws Exception {
        StudentParticipation studentParticipation = database.createAndSaveParticipationForExercise(classExercise, "student1");
        assertThat(studentParticipation.getSubmissions()).isEmpty();
        ModelingSubmission returnedSubmission = request.get("/api/participations/" + studentParticipation.getId() + "/latest-modeling-submission", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(returnedSubmission).as("new submission is created").isNotNull();
    }

    @Test
    @WithMockUser(username = "student1")
    void getSubmissionForModelingEditor_unfinishedAssessment() throws Exception {
        StudentParticipation studentParticipation = database.createAndSaveParticipationForExercise(classExercise, "student1");
        database.addModelingSubmissionWithEmptyResult(classExercise, "", "student1");

        ModelingSubmission returnedSubmission = request.get("/api/participations/" + studentParticipation.getId() + "/latest-modeling-submission", HttpStatus.OK,
                ModelingSubmission.class);
        assertThat(returnedSubmission.getLatestResult()).as("the result is not sent to the client if the assessment is not finished").isNull();
    }

    @Test
    @WithMockUser(username = "student3", roles = "USER")
    void submitExercise_afterDueDate_forbidden() throws Exception {
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now().minusDays(2));
        studentParticipationRepository.saveAndFlush(afterDueDateParticipation);
        request.post("/api/exercises/" + finishedExercise.getId() + "/modeling-submissions", submittedSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student3", roles = "USER")
    void submitExercise_beforeDueDate_allowed() throws Exception {
        ModelingSubmission submission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submittedSubmission,
                ModelingSubmission.class, HttpStatus.OK);

        assertThat(submission.getSubmissionDate()).isEqualToIgnoringNanos(ZonedDateTime.now());
        assertThat(submission.getParticipation().getInitializationState()).isEqualTo(InitializationState.FINISHED);
    }

    @Test
    @WithMockUser(username = "student3", roles = "USER")
    void submitExercise_beforeDueDateSecondSubmission_allowed() throws Exception {
        submittedSubmission.setModel(validModel);
        submittedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submittedSubmission, ModelingSubmission.class,
                HttpStatus.OK);

        final var submissionInDb = modelingSubmissionRepo.findById(submittedSubmission.getId());
        assertThat(submissionInDb).isPresent();
        assertThat(submissionInDb.get().getModel()).isEqualTo(validModel);
    }

    @Test
    @WithMockUser(username = "student3", roles = "USER")
    void submitExercise_afterDueDateWithParticipationStartAfterDueDate_allowed() throws Exception {
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepository.saveAndFlush(afterDueDateParticipation);

        request.postWithoutLocation("/api/exercises/" + classExercise.getId() + "/modeling-submissions", submittedSubmission, HttpStatus.OK, null);
    }

    @Test
    @WithMockUser(username = "student3", roles = "USER")
    void saveExercise_beforeDueDate() throws Exception {
        ModelingSubmission storedSubmission = request.postWithResponseBody("/api/exercises/" + classExercise.getId() + "/modeling-submissions", unsubmittedSubmission,
                ModelingSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isTrue();
    }

    @Test
    @WithMockUser(username = "student3", roles = "USER")
    void saveExercise_afterDueDateWithParticipationStartAfterDueDate() throws Exception {
        database.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));
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
        return ModelFactory.generateModelingSubmission(emptyModel, true);
    }

    private ModelingSubmission generateUnsubmittedSubmission() {
        return ModelFactory.generateModelingSubmission(emptyModel, false);
    }

    private void createNineLockedSubmissionsForDifferentExercisesAndUsers(String assessor) {
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, "student1", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, "student2", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(classExercise, submission, "student3", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(activityExercise, submission, "student1", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(activityExercise, submission, "student2", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(activityExercise, submission, "student3", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(objectExercise, submission, "student1", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(objectExercise, submission, "student2", assessor);
        submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(objectExercise, submission, "student3", assessor);
    }

    private void createTenLockedSubmissionsForDifferentExercisesAndUsers(String assessor) {
        createNineLockedSubmissionsForDifferentExercisesAndUsers(assessor);
        ModelingSubmission submission = ModelFactory.generateModelingSubmission(validModel, true);
        database.addModelingSubmissionWithResultAndAssessor(useCaseExercise, submission, "student1", assessor);
    }
}
