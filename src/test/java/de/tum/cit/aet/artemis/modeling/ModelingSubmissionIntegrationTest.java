package de.tum.cit.aet.artemis.modeling;

import static de.tum.cit.aet.artemis.core.util.TestResourceUtils.HalfSecond;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.ResultDTO;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.dto.UserNameDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
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
import de.tum.cit.aet.artemis.modeling.dto.ModelingSubmissionRequestDTO;
import de.tum.cit.aet.artemis.modeling.dto.ModelingSubmissionResponseDTO;
import de.tum.cit.aet.artemis.modeling.test_repository.ModelingSubmissionTestRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ModelingSubmissionIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "modelingsubmissionintegration";

    /** Users not enrolled in the test course; exercise the wrong-course branches. */
    private static final String OTHER_PREFIX = "modelingsubmissionother";

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
        userUtilService.addUsers(OTHER_PREFIX, 1, 1, 0, 1); // outsider student, tutor, instructor — never enrolled
        course = modelingExerciseUtilService.addEnrolledCourseWithDifferentModelingExercises(TEST_PREFIX);

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

        Course course2 = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        textExercise = (TextExercise) new ArrayList<>(course2.getExercises()).getFirst();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void createModelingSubmission_badRequest() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingSubmissionRepo.save(submission);
        // the request DTO carries the id, which is rejected on create
        request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", toRequest(submission), ModelingSubmissionResponseDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "student1", roles = "USER")
    void createModelingSubmission_studentNotInCourse() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", toRequest(submission), ModelingSubmissionResponseDTO.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void createModelingSubmission_ignoresAParticipationSentByTheClient() throws Exception {
        StudentParticipation ownParticipation = participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        StudentParticipation someoneElsesParticipation = participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student2");

        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        // This endpoint deserializes the request body into the entity, so a client can name any participation here. The
        // server has to resolve the participation from the authenticated user, or from what the exam submission gate
        // handed it, and never off the submission, or a student could write their submission into somebody else's
        // participation.
        submission.setParticipation(someoneElsesParticipation);

        ModelingSubmission returned = request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", submission,
                ModelingSubmission.class, HttpStatus.OK);

        ModelingSubmission stored = modelingSubmissionRepo.findById(returned.getId()).orElseThrow();
        assertThat(stored.getParticipation().getId()).isEqualTo(ownParticipation.getId());
        assertThat(stored.getParticipation().getId()).isNotEqualTo(someoneElsesParticipation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void saveAndSubmitModelingSubmission_tooLarge() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyModel, false);
        // should be ok
        char[] charsModel = new char[Constants.MAX_SUBMISSION_MODEL_LENGTH];
        Arrays.fill(charsModel, 'a');
        submission.setModel(new String(charsModel));
        request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", toRequest(submission), ModelingSubmissionResponseDTO.class,
                HttpStatus.OK);

        // should be too large
        char[] charsModelTooLarge = new char[Constants.MAX_SUBMISSION_MODEL_LENGTH + 1];
        Arrays.fill(charsModelTooLarge, 'a');
        submission.setModel(new String(charsModelTooLarge));
        request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", toRequest(submission), ModelingSubmissionResponseDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void saveAndSubmitModelingSubmission_classDiagram() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyModel, false);
        ModelingSubmissionResponseDTO returnedSubmission = performInitialModelSubmission(classExercise.getId(), submission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.id(), emptyModel);
        checkDetailsHidden(returnedSubmission, true);

        returnedSubmission = performUpdateOnModelSubmission(classExercise.getId(), new ModelingSubmissionRequestDTO(returnedSubmission.id(), validModel, null, true));
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.id(), validModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void saveAndSubmitModelingSubmission_activityDiagram() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(activityExercise, TEST_PREFIX + "student1");
        String emptyActivityModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/empty-activity-diagram.json");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyActivityModel, false);
        ModelingSubmissionResponseDTO returnedSubmission = performInitialModelSubmission(activityExercise.getId(), submission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.id(), emptyActivityModel);
        checkDetailsHidden(returnedSubmission, true);

        String validActivityModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/example-activity-diagram.json");
        returnedSubmission = performUpdateOnModelSubmission(activityExercise.getId(), new ModelingSubmissionRequestDTO(returnedSubmission.id(), validActivityModel, null, true));
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.id(), validActivityModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void saveAndSubmitModelingSubmission_objectDiagram() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(objectExercise, TEST_PREFIX + "student1");
        String emptyObjectModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/empty-object-diagram.json");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyObjectModel, false);
        ModelingSubmissionResponseDTO returnedSubmission = performInitialModelSubmission(objectExercise.getId(), submission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.id(), emptyObjectModel);
        checkDetailsHidden(returnedSubmission, true);

        String validObjectModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/object-model.json");
        returnedSubmission = performUpdateOnModelSubmission(objectExercise.getId(), new ModelingSubmissionRequestDTO(returnedSubmission.id(), validObjectModel, null, true));
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.id(), validObjectModel);
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void saveAndSubmitModelingSubmission_useCaseDiagram() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(useCaseExercise, TEST_PREFIX + "student1");
        String emptyUseCaseModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/empty-use-case-diagram.json");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyUseCaseModel, false);
        ModelingSubmissionResponseDTO returnedSubmission = performInitialModelSubmission(useCaseExercise.getId(), submission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.id(), emptyUseCaseModel);
        checkDetailsHidden(returnedSubmission, true);

        String validUseCaseModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/use-case-model.json");
        returnedSubmission = performUpdateOnModelSubmission(useCaseExercise.getId(), new ModelingSubmissionRequestDTO(returnedSubmission.id(), validUseCaseModel, null, true));
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.id(), validUseCaseModel);
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
        ModelingSubmissionResponseDTO returnedSubmission = performInitialModelSubmission(useCaseExercise.getId(), submission);
        assertTeamParticipationOwners(returnedSubmission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.id(), emptyUseCaseModel);

        userUtilService.changeUser(TEST_PREFIX + "student1");
        Optional<SubmissionVersion> version = submissionVersionRepository.findLatestVersion(returnedSubmission.id());
        assertThat(version).as("submission version was created").isNotEmpty();
        assertThat(version.orElseThrow().getAuthor().getLogin()).as("submission version has correct author").isEqualTo(TEST_PREFIX + "student1");
        assertThat(version.get().getContent()).as("submission version has correct content")
                .isEqualTo("Model: " + returnedSubmission.model() + "; Explanation: " + returnedSubmission.explanationText());
        assertThat(version.get().getCreatedDate()).isNotNull();
        assertThat(version.get().getLastModifiedDate()).isNotNull();

        userUtilService.changeUser(TEST_PREFIX + "student2");
        String validUseCaseModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/use-case-model.json");
        returnedSubmission = performUpdateOnModelSubmission(useCaseExercise.getId(),
                new ModelingSubmissionRequestDTO(returnedSubmission.id(), validUseCaseModel, returnedSubmission.explanationText(), true));
        assertTeamParticipationOwners(returnedSubmission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.id(), validUseCaseModel);
        checkDetailsHidden(returnedSubmission, true);

        userUtilService.changeUser(TEST_PREFIX + "student2");
        version = submissionVersionRepository.findLatestVersion(returnedSubmission.id());
        assertThat(version).as("submission version was created").isNotEmpty();
        assertThat(version.orElseThrow().getAuthor().getLogin()).as("submission version has correct author").isEqualTo(TEST_PREFIX + "student2");
        assertThat(version.get().getContent()).as("submission version has correct content")
                .isEqualTo("Model: " + returnedSubmission.model() + "; Explanation: " + returnedSubmission.explanationText());

        returnedSubmission = performUpdateOnModelSubmission(useCaseExercise.getId(),
                new ModelingSubmissionRequestDTO(returnedSubmission.id(), returnedSubmission.model(), returnedSubmission.explanationText(), returnedSubmission.submitted()));
        assertTeamParticipationOwners(returnedSubmission);
        userUtilService.changeUser(TEST_PREFIX + "student2");
        Optional<SubmissionVersion> newVersion = submissionVersionRepository.findLatestVersion(returnedSubmission.id());
        assertThat(newVersion.orElseThrow().getId()).as("submission version was not created").isEqualTo(version.get().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void updateModelSubmission() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student2");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyModel, true);
        ModelingSubmissionResponseDTO returnedSubmission = performInitialModelSubmission(classExercise.getId(), submission);
        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.id(), emptyModel);

        request.putWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions",
                new ModelingSubmissionRequestDTO(returnedSubmission.id(), validModel, null, false), ModelingSubmissionResponseDTO.class, HttpStatus.OK);

        modelingExerciseUtilService.checkModelingSubmissionCorrectlyStored(returnedSubmission.id(), validModel);

        returnedSubmission = request.putWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions",
                new ModelingSubmissionRequestDTO(returnedSubmission.id(), validModel, null, true), ModelingSubmissionResponseDTO.class, HttpStatus.OK);
        // sensitive information (grading instructions) is hidden and no result is sent to the owning student
        assertThat(returnedSubmission.participation().exercise().gradingInstructions()).as("sensitive information (grading instructions) is hidden").isNull();
        assertThat(returnedSubmission.results()).as("sensitive information (exercise result) is hidden").isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void submitModelingSubmission_responseCarriesParticipationOwnerAndModel() throws Exception {
        // wire-contract pin: the save/submit response carries the participation owner (individual student login) and the model
        participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        ModelingSubmissionResponseDTO returnedSubmission = performInitialModelSubmission(classExercise.getId(), submission);

        assertThat(returnedSubmission.participation()).as("participation is present").isNotNull();
        assertThat(returnedSubmission.participation().student()).as("participation owner is present").isNotNull();
        assertThat(returnedSubmission.participation().student().login()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(returnedSubmission.model()).as("model is echoed back").isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void updateModelingSubmission_clientShapedRequest_persistsModel() throws Exception {
        // Build the request exactly the way the client sends it (id + full model + submitted), then reload from a fresh
        // session and assert the persisted model is non-empty. Guards against dropping model/explanationText on the request DTO.
        participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(emptyModel, false);
        ModelingSubmissionResponseDTO created = performInitialModelSubmission(classExercise.getId(), submission);

        ModelingSubmissionRequestDTO clientRequest = new ModelingSubmissionRequestDTO(created.id(), validModel, "some explanation", true);
        ModelingSubmissionResponseDTO updated = performUpdateOnModelSubmission(classExercise.getId(), clientRequest);

        ModelingSubmission persisted = modelingSubmissionRepo.findById(updated.id()).orElseThrow();
        assertThat(persisted.getModel()).as("persisted model is non-empty after a client-shaped write").isNotEmpty();
        modelingExerciseUtilService.checkModelsAreEqual(persisted.getModel(), validModel);
        assertThat(persisted.getExplanationText()).isEqualTo("some explanation");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void injectResultOnSubmissionUpdate() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, false);
        // The request DTO does not carry results at all, so a student can never inject one.
        ModelingSubmissionResponseDTO storedSubmission = request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions",
                toRequest(submission), ModelingSubmissionResponseDTO.class);

        userUtilService.changeUser(TEST_PREFIX + "student1");
        ModelingSubmission reloaded = modelingSubmissionRepo.findByIdWithEagerResult(storedSubmission.id()).orElseThrow();
        assertThat(reloaded.getLatestResult()).as("submission still unrated").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void updateModelingSubmission_existingResult_forksNewSubmission() throws Exception {
        // A9 pin: when the persisted submission already has a result (e.g. an Athena auto-feedback), an autosave/update
        // must NOT overwrite the result-bearing submission but fork a new one. The service reconstructs this decision from
        // persisted state (resultRepository.existsBySubmissionId), independent of the request body, which no longer carries results.
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission.setParticipation(participation);
        submission = modelingSubmissionRepo.save(submission);
        participation.addSubmission(submission);
        studentParticipationRepository.save(participation);
        Result athenaResult = createResult(AssessmentType.AUTOMATIC_ATHENA, submission, participation, null);
        Long originalSubmissionId = submission.getId();

        // The client-shaped request carries only id + model + submitted (no results)
        ModelingSubmissionRequestDTO autosave = new ModelingSubmissionRequestDTO(originalSubmissionId, emptyModel, null, false);
        ModelingSubmissionResponseDTO returned = performUpdateOnModelSubmission(classExercise.getId(), autosave);

        assertThat(returned.id()).as("a new submission was forked instead of overwriting the result-bearing one").isNotEqualTo(originalSubmissionId);
        // the original submission and its result must still exist
        ModelingSubmission original = modelingSubmissionRepo.findByIdWithEagerResult(originalSubmissionId).orElseThrow();
        assertThat(original.getResults()).extracting(Result::getId).contains(athenaResult.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = modelingExerciseUtilService.addModelingSubmission(classExercise, submittedSubmission, TEST_PREFIX + "student1");
        ModelingSubmission submission2 = modelingExerciseUtilService.addModelingSubmission(classExercise, unsubmittedSubmission, TEST_PREFIX + "student2");

        List<ModelingSubmissionResponseDTO> submissions = request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        assertThat(submissions).extracting(ModelingSubmissionResponseDTO::id).containsExactlyInAnyOrder(submission1.getId(), submission2.getId());
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllSubmissionsOfExercise_instructorNotInCourse() throws Exception {
        request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.FORBIDDEN, ModelingSubmissionResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllSubmissionsOfExercise_assessedByTutor() throws Exception {
        List<ModelingSubmissionResponseDTO> submissions = request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions?assessedByTutor=true",
                HttpStatus.OK, ModelingSubmissionResponseDTO.class);
        assertThat(submissions).as("does not have a modeling submission assessed by the tutor").isEmpty();

        modelingExerciseUtilService.addModelingSubmissionWithFinishedResultAndAssessor(classExercise, submittedSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        submissions = request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions?assessedByTutor=true", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);
        assertThat(submissions).as("has a modeling submission assessed by the tutor").hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "tutor1", roles = "TA")
    void getAllSubmissionsOfExercise_assessedByTutor_instructorNotInCourse() throws Exception {
        request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions?assessedByTutor=true", HttpStatus.FORBIDDEN,
                ModelingSubmissionResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getAllSubmissionsOfExerciseAsStudent() throws Exception {
        modelingExerciseUtilService.addModelingSubmission(classExercise, submittedSubmission, TEST_PREFIX + "student1");
        modelingExerciseUtilService.addModelingSubmission(classExercise, unsubmittedSubmission, TEST_PREFIX + "student2");

        request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.FORBIDDEN, ModelingSubmissionResponseDTO.class);
        request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions?submittedOnly=true", HttpStatus.FORBIDDEN, ModelingSubmissionResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllSubmittedSubmissionsOfExercise() throws Exception {
        ModelingSubmission submission1 = modelingExerciseUtilService.addModelingSubmission(classExercise, submittedSubmission, TEST_PREFIX + "student1");
        modelingExerciseUtilService.addModelingSubmission(classExercise, unsubmittedSubmission, TEST_PREFIX + "student2");
        ModelingSubmission submission3 = modelingExerciseUtilService.addModelingSubmission(classExercise, generateSubmittedSubmission(), TEST_PREFIX + "student3");

        List<ModelingSubmissionResponseDTO> submissions = request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions?submittedOnly=true",
                HttpStatus.OK, ModelingSubmissionResponseDTO.class);

        assertThat(submissions).extracting(ModelingSubmissionResponseDTO::id).containsExactlyInAnyOrder(submission1.getId(), submission3.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllSubmissionsOfExercise_queryCount() throws Exception {
        modelingExerciseUtilService.addModelingSubmission(classExercise, submittedSubmission, TEST_PREFIX + "student1");
        modelingExerciseUtilService.addModelingSubmission(classExercise, unsubmittedSubmission, TEST_PREFIX + "student2");
        assertThatDb(() -> request.getList("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", HttpStatus.OK, ModelingSubmissionResponseDTO.class))
                .hasBeenCalledAtMostTimes(30);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmission() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        ModelingSubmissionResponseDTO storedSubmission = request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        assertThat(storedSubmission.results()).as("result has been set").isNotEmpty();
        assertThat(storedSubmission.results().getLast().assessor()).as("assessor is tutor1").isNotNull();
        assertThat(storedSubmission.results().getLast().assessor().login()).isEqualTo(user.getLogin());
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmission_queryCount() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        final Long submissionId = submission.getId();
        assertThatDb(() -> request.get("/api/modeling/modeling-submissions/" + submissionId, HttpStatus.OK, ModelingSubmissionResponseDTO.class)).hasBeenCalledAtMostTimes(40);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutResults() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        ModelingSubmissionResponseDTO storedSubmission = request.get("/api/modeling/modeling-submissions/" + submission.getId() + "?withoutResults=true", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        assertThat(storedSubmission.results()).as("result has not been set").isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "tutor1", roles = "TA")
    void getModelSubmission_tutorNotInCourse() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmissionResponseDTO.class);
    }

    @Test
    @WithMockUser(value = TEST_PREFIX + "student2", roles = "USER")
    void getModelSubmissionWithResult_notInvolved_notAllowed() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmissionResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getModelSubmission_ownerBeforeAssessment_anonymized() throws Exception {
        // The owning student may fetch their own submission before they are allowed to assess it; the submission is
        // anonymized (participation, results and submissionDate nulled). The DTO factory must map that without an NPE.
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        ModelingSubmissionResponseDTO storedSubmission = request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        assertThat(storedSubmission).isNotNull();
        assertThat(storedSubmission.participation()).as("participation is anonymized").isNull();
        assertThat(storedSubmission.results()).as("results are anonymized").isNullOrEmpty();
        assertThat(storedSubmission.submissionDate()).as("submission date is anonymized").isNull();
        assertThat(storedSubmission.model()).as("the model is still returned to the owner").isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmission_lockLimitReached_success() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        createNineLockedSubmissionsForDifferentExercisesAndUsers(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(useCaseExercise, submission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        ModelingSubmissionResponseDTO storedSubmission = request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        assertThat(storedSubmission.results()).as("result has been set").isNotEmpty();
        assertThat(storedSubmission.results().getLast().assessor()).as("assessor is tutor1").isNotNull();
        assertThat(storedSubmission.results().getLast().assessor().login()).isEqualTo(user.getLogin());
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmission_lockLimitReached_badRequest() throws Exception {
        createTenLockedSubmissionsForDifferentExercisesAndUsers(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(useCaseExercise, submission, TEST_PREFIX + "student2");

        request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.BAD_REQUEST, ModelingSubmissionResponseDTO.class);
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
        ModelingSubmissionResponseDTO storedSubmission = request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmissionResponseDTO.class,
                params);

        assertThat(storedSubmission.results()).isNotNull();
        assertThat(storedSubmission.results()).extracting(ResultDTO::id).contains(storedResult.getId());

        // result-id stability round-trip (C4 @OrderColumn on Submission.results): a second fetch returns the same result id
        ModelingSubmissionResponseDTO refetched = request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.OK, ModelingSubmissionResponseDTO.class,
                params);
        assertThat(refetched.results()).extracting(ResultDTO::id).isEqualTo(storedSubmission.results().stream().map(ResultDTO::id).toList());
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
        request.get("/api/modeling/modeling-submissions/" + submission.getId(), HttpStatus.FORBIDDEN, ModelingSubmissionResponseDTO.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmissionResponseDTO storedSubmission = request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        assertThat(storedSubmission).as("submission was found").isNotNull();
        assertThat(storedSubmission.id()).isEqualTo(submission.getId());
        modelingExerciseUtilService.checkModelsAreEqual(storedSubmission.model(), submission.getModel());
        assertThat(storedSubmission.submissionDate()).as("submission date is correct").isCloseTo(submission.getSubmissionDate(), HalfSecond());
        assertThat(storedSubmission.results()).as("result is not set").isNullOrEmpty();
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_wrongExerciseType() throws Exception {
        request.get("/api/modeling/exercises/" + textExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.BAD_REQUEST, ModelingSubmissionResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_lockSubmission() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmissionResponseDTO storedSubmission = request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment?lock=true",
                HttpStatus.OK, ModelingSubmissionResponseDTO.class);

        assertThat(storedSubmission.id()).as("submission was found").isEqualTo(submission.getId());
        modelingExerciseUtilService.checkModelsAreEqual(storedSubmission.model(), submission.getModel());
        assertThat(storedSubmission.results()).as("result is set").isNotEmpty();
        assertThat(storedSubmission.results().getLast().assessor()).as("assessor is tutor1").isNotNull();
        assertThat(storedSubmission.results().getLast().assessor().login()).isEqualTo(user.getLogin());
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_queryCount() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        assertThatDb(() -> request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class)).hasBeenCalledAtMostTimes(40);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_noSubmittedSubmission_null() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, false);
        modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        var response = request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);
        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_noSubmissionWithoutAssessment_null() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmissionWithResultAndAssessor(classExercise, submission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        var response = request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);
        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_dueDateNotOver() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");

        request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.FORBIDDEN, ModelingSubmissionResponseDTO.class);
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_notTutorInCourse() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.FORBIDDEN, ModelingSubmissionResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getModelSubmissionWithoutAssessment_asStudent_forbidden() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.FORBIDDEN, ModelingSubmissionResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getModelSubmissionWithoutAssessment_testLockLimit() throws Exception {
        createNineLockedSubmissionsForDifferentExercisesAndUsers(TEST_PREFIX + "tutor1");
        ModelingSubmission newSubmission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingExerciseUtilService.addModelingSubmission(useCaseExercise, newSubmission, TEST_PREFIX + "student1");
        exerciseUtilService.updateExerciseDueDate(useCaseExercise.getId(), ZonedDateTime.now().minusHours(1));

        ModelingSubmissionResponseDTO storedSubmission = request.get("/api/modeling/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment?lock=true",
                HttpStatus.OK, ModelingSubmissionResponseDTO.class);
        assertThat(storedSubmission).as("submission was found").isNotNull();
        request.get("/api/modeling/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.BAD_REQUEST, ModelingSubmissionResponseDTO.class);
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

        ModelingSubmissionResponseDTO storedSubmission = request.get("/api/modeling/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment?lock=true",
                HttpStatus.OK, ModelingSubmissionResponseDTO.class);
        assertThat(storedSubmission).as("submission was found").isNotNull();
        request.get("/api/modeling/exercises/" + useCaseExercise.getId() + "/modeling-submission-without-assessment", HttpStatus.BAD_REQUEST, ModelingSubmissionResponseDTO.class);
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

        ModelingSubmissionResponseDTO receivedSubmission = request.get("/api/modeling/participations/" + submission.getParticipation().getId() + "/latest-modeling-submission",
                HttpStatus.OK, ModelingSubmissionResponseDTO.class);

        assertThat(receivedSubmission.id()).as("submission was found").isEqualTo(submission.getId());
        modelingExerciseUtilService.checkModelsAreEqual(receivedSubmission.model(), submission.getModel());
        // wire-contract pin: individual participation carries participation.student.login
        assertThat(receivedSubmission.participation().student()).as("participation owner is present").isNotNull();
        assertThat(receivedSubmission.participation().student().login()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(receivedSubmission.results()).as("result is set").isNotEmpty();
        assertThat(receivedSubmission.results().getLast().assessor()).as("assessor is hidden").isNull();

        // students can only see their own models
        submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student2");
        request.get("/api/modeling/participations/" + submission.getParticipation().getId() + "/latest-modeling-submission", HttpStatus.FORBIDDEN,
                ModelingSubmissionResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getModelSubmissionForModelingEditor_queryCount() throws Exception {
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        final long participationId = submission.getParticipation().getId();
        assertThatDb(() -> request.get("/api/modeling/participations/" + participationId + "/latest-modeling-submission", HttpStatus.OK, ModelingSubmissionResponseDTO.class))
                .hasBeenCalledAtMostTimes(40);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getModelSubmissionForModelingEditor_team_carriesTeamStudentLogins() throws Exception {
        // wire-contract pin: for a team participation the editor payload carries participation.team.students[*].login,
        // which the client uses to verify ownership for the owning student.
        useCaseExercise.setMode(ExerciseMode.TEAM);
        exerciseRepository.save(useCaseExercise);
        Team team = new Team();
        team.setName("Team");
        team.setShortName(TEST_PREFIX + "team");
        team.setExercise(useCaseExercise);
        team.addStudents(userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow());
        team.addStudents(userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow());
        teamRepository.save(useCaseExercise, team);

        StudentParticipation participation = participationUtilService.addTeamParticipationForExercise(useCaseExercise, team.getId());
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        participationUtilService.addSubmission(participation, submission);

        ModelingSubmissionResponseDTO receivedSubmission = request.get("/api/modeling/participations/" + participation.getId() + "/latest-modeling-submission", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        assertThat(receivedSubmission.participation().team()).as("team is present").isNotNull();
        assertThat(receivedSubmission.participation().team().students()).as("team members are present").isNotNull();
        assertThat(receivedSubmission.participation().team().students()).extracting(UserNameDTO::login).contains(TEST_PREFIX + "student1", TEST_PREFIX + "student2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionForModelingEditor_badRequest() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        StudentParticipation participation = new StudentParticipation();
        participation.setParticipant(user);
        participation.setExercise(null);
        StudentParticipation studentParticipation = studentParticipationRepository.save(participation);
        request.get("/api/modeling/participations/" + studentParticipation.getId() + "/latest-modeling-submission", HttpStatus.BAD_REQUEST, ModelingSubmissionResponseDTO.class);

        participation.setExercise(textExercise);
        studentParticipation = studentParticipationRepository.save(participation);
        request.get("/api/modeling/participations/" + studentParticipation.getId() + "/latest-modeling-submission", HttpStatus.BAD_REQUEST, ModelingSubmissionResponseDTO.class);
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
                ModelingSubmissionResponseDTO.class);
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
                ModelingSubmissionResponseDTO.class);
        assertThat(submission).isNotNull();
        assertThat(submission.id()).isEqualTo(modelingSubmission.getId());
        // The masked editor payload maps exerciseGroup WITHOUT exam for exam exercises
        assertThat(submission.participation().exercise().exerciseGroup()).as("exercise group is present").isNotNull();
        assertThat(submission.participation().exercise().exerciseGroup().exam()).as("The exam object should not be sent to students").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getModelingResult_examExerciseWithPublishedResult_masksExamAndReturnsResult() throws Exception {
        // Regression pin for the ExamResults student-editor 500 (masked-exam course resolution): a real (non-test) exam
        // whose results are already published, so the student is allowed to view the assessed result.
        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        exam.setStartDate(ZonedDateTime.now().minusHours(3));
        exam.setEndDate(ZonedDateTime.now().minusHours(2));
        exam.setVisibleDate(ZonedDateTime.now().minusHours(4));
        exam.setPublishResultsDate(ZonedDateTime.now().minusHours(1));

        ExerciseGroup exerciseGroup = exam.getExerciseGroups().getFirst();
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExerciseForExam(DiagramType.ActivityDiagram, exerciseGroup);
        exerciseGroup.addExercise(modelingExercise);
        exerciseGroupRepository.save(exerciseGroup);
        modelingExercise = exerciseRepository.save(modelingExercise);
        examRepository.save(exam);

        // A submitted submission with a rated, completed, assessor-attached result: the endpoint keeps the result and maps
        // it, so the response walks result -> submission -> participation -> (exam) exercise. That path used to NPE because
        // the endpoint masks exerciseGroup.exam for exam exercises while ParticipationExerciseDTO resolved the course via
        // the (now missing) exam.
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "student1");
        ModelingSubmission modelingSubmission = ParticipationFactory.generateModelingSubmission(validModel, true);
        modelingSubmission.setParticipation(participation);
        modelingSubmission = modelingSubmissionRepo.save(modelingSubmission);
        participation.addSubmission(modelingSubmission);
        studentParticipationRepository.save(participation);

        User assessor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        Result result = createResult(AssessmentType.MANUAL, modelingSubmission, participation, assessor);
        result.setRated(true);
        resultRepository.save(result);

        ModelingSubmissionResponseDTO submission = request.get("/api/modeling/participations/" + participation.getId() + "/latest-modeling-submission", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        assertThat(submission).isNotNull();
        assertThat(submission.id()).isEqualTo(modelingSubmission.getId());
        // The masked editor payload carries the exercise group but strips the exam for exam exercises.
        assertThat(submission.participation().exercise().exerciseGroup()).as("exercise group is present").isNotNull();
        assertThat(submission.participation().exercise().exerciseGroup().exam()).as("exam is stripped for students").isNull();
        // The published exam result is visible and maps without NPE (the masked-exam course-resolution fix).
        assertThat(submission.results()).as("the assessed result is returned after publication").isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getModelSubmissionForModelingEditor_uninitializedCategories_mapsSafely() throws Exception {
        // The exercise has categories in the DB, but the editor fetch path leaves the LAZY @ElementCollection uninitialized.
        // The DTO factory must copy (guarded) rather than embed the live persistent set, so the response maps to 200 and the
        // categories are simply omitted instead of leaking a live Hibernate collection into the response record.
        classExercise.setCategories(new HashSet<>(Set.of("uml", "diagram")));
        exerciseRepository.save(classExercise);

        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission.setParticipation(participation);
        submission = modelingSubmissionRepo.save(submission);
        participation.addSubmission(submission);
        studentParticipationRepository.save(participation);

        ModelingSubmissionResponseDTO returnedSubmission = request.get("/api/modeling/participations/" + participation.getId() + "/latest-modeling-submission", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        assertThat(returnedSubmission).isNotNull();
        assertThat(returnedSubmission.participation().exercise()).as("exercise maps without a live categories collection").isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionForModelingEditor_emptySubmission() throws Exception {
        StudentParticipation studentParticipation = participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        assertThat(studentParticipation.getSubmissions()).isEmpty();
        ModelingSubmissionResponseDTO returnedSubmission = request.get("/api/modeling/participations/" + studentParticipation.getId() + "/latest-modeling-submission",
                HttpStatus.OK, ModelingSubmissionResponseDTO.class);
        assertThat(returnedSubmission).as("new submission is created").isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionForModelingEditor_unfinishedAssessment() throws Exception {
        StudentParticipation studentParticipation = participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student1");
        modelingExerciseUtilService.addModelingSubmissionWithEmptyResult(classExercise, "", TEST_PREFIX + "student1");

        ModelingSubmissionResponseDTO returnedSubmission = request.get("/api/modeling/participations/" + studentParticipation.getId() + "/latest-modeling-submission",
                HttpStatus.OK, ModelingSubmissionResponseDTO.class);
        assertThat(returnedSubmission.results()).as("the result is not sent to the client if the assessment is not finished").isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_afterDueDate_forbidden() throws Exception {
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now().minusDays(2));
        studentParticipationRepository.saveAndFlush(afterDueDateParticipation);
        request.post("/api/modeling/exercises/" + finishedExercise.getId() + "/modeling-submissions", toRequest(submittedSubmission), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_beforeDueDate_allowed() throws Exception {
        ModelingSubmissionResponseDTO submission = request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions",
                toRequest(submittedSubmission), ModelingSubmissionResponseDTO.class, HttpStatus.OK);

        assertThat(submission.submissionDate()).isCloseTo(ZonedDateTime.now(), within(500, ChronoUnit.MILLIS));
        assertThat(submission.participation().initializationState()).isEqualTo(InitializationState.FINISHED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_beforeDueDateSecondSubmission_allowed() throws Exception {
        submittedSubmission.setModel(validModel);
        ModelingSubmissionResponseDTO submitted = request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions",
                toRequest(submittedSubmission), ModelingSubmissionResponseDTO.class, HttpStatus.OK);

        final var submissionInDb = modelingSubmissionRepo.findById(submitted.id());
        assertThat(submissionInDb).isPresent();
        assertThat(submissionInDb.get().getModel()).isEqualTo(validModel);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_afterDueDateWithParticipationStartAfterDueDate_allowed() throws Exception {
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepository.saveAndFlush(afterDueDateParticipation);

        request.postWithoutLocation("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions", toRequest(submittedSubmission), HttpStatus.OK, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void saveExercise_beforeDueDate() throws Exception {
        ModelingSubmissionResponseDTO storedSubmission = request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions",
                toRequest(unsubmittedSubmission), ModelingSubmissionResponseDTO.class, HttpStatus.OK);
        assertThat(storedSubmission.submitted()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void saveExercise_afterDueDateWithParticipationStartAfterDueDate() throws Exception {
        exerciseUtilService.updateExerciseDueDate(classExercise.getId(), ZonedDateTime.now().minusHours(1));
        afterDueDateParticipation.setInitializationDate(ZonedDateTime.now());
        studentParticipationRepository.saveAndFlush(afterDueDateParticipation);

        ModelingSubmissionResponseDTO storedSubmission = request.postWithResponseBody("/api/modeling/exercises/" + classExercise.getId() + "/modeling-submissions",
                toRequest(unsubmittedSubmission), ModelingSubmissionResponseDTO.class, HttpStatus.OK);
        assertThat(storedSubmission.submitted()).isFalse();
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

        List<ModelingSubmissionResponseDTO> submissions = request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        // Verify that only the ATHENA result is returned
        assertThat(submissions).hasSize(1);
        ModelingSubmissionResponseDTO returnedSubmission = submissions.getFirst();
        assertThat(returnedSubmission.results()).hasSize(1);
        assertThat(returnedSubmission.results().getFirst().assessmentType()).isEqualTo(AssessmentType.AUTOMATIC_ATHENA);
        assertThat(returnedSubmission.results().getFirst().assessor()).isNull(); // Sensitive info filtered
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getSubmissionsWithResultsForParticipation_withUnfinishedAssessment_returnsResults() throws Exception {
        classExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        classExercise.setAssessmentDueDate(ZonedDateTime.now().minusHours(1));
        modelingExerciseUtilService.updateExercise(classExercise);

        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(validModel, true);
        submission = modelingExerciseUtilService.addModelingSubmission(classExercise, submission, TEST_PREFIX + "student1");
        StudentParticipation participation = (StudentParticipation) submission.getParticipation();

        createResult(AssessmentType.AUTOMATIC_ATHENA, submission, participation, null);
        Result unfinished = createResult(AssessmentType.MANUAL, submission, participation, null);
        unfinished.setCompletionDate(null);
        resultRepository.save(unfinished);

        List<ModelingSubmissionResponseDTO> submissions = request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        assertThat(submissions).hasSize(1);
        var results = submissions.getFirst().results();
        assertThat(results).hasSize(2);
        assertThat(results.getFirst().completionDate()).isNotNull();
        assertThat(results.getLast().completionDate()).isNull();
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

        List<ModelingSubmissionResponseDTO> submissions = request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        // Verify that only the ATHENA result is returned before the assessment due date
        assertThat(submissions).hasSize(1);
        ModelingSubmissionResponseDTO returnedSubmission = submissions.getFirst();
        assertThat(returnedSubmission.results()).hasSize(1);
        assertThat(returnedSubmission.results().getFirst().assessmentType()).isEqualTo(AssessmentType.AUTOMATIC_ATHENA);
        // Sensitive information should be filtered
        assertThat(returnedSubmission.results().getFirst().assessor()).isNull();
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

        List<ModelingSubmissionResponseDTO> submissions = request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        // Verify that both results are returned after the assessment due date
        assertThat(submissions).hasSize(1);
        ModelingSubmissionResponseDTO returnedSubmission = submissions.getFirst();
        assertThat(returnedSubmission.results()).hasSize(2);
        // Sensitive information should be filtered
        returnedSubmission.results().forEach(result -> assertThat(result.assessor()).isNull());
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

        List<ModelingSubmissionResponseDTO> submissions = request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        assertThat(submissions).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getSubmissionsWithResultsForParticipation_otherStudentParticipation_forbidden() throws Exception {
        // Given
        // Create participation for student2
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(classExercise, TEST_PREFIX + "student2");

        // When & Then
        request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.FORBIDDEN, ModelingSubmissionResponseDTO.class);
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

        List<ModelingSubmissionResponseDTO> submissions = request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.OK,
                ModelingSubmissionResponseDTO.class);

        assertThat(submissions).hasSize(1);
        ModelingSubmissionResponseDTO returnedSubmission = submissions.getFirst();
        assertThat(returnedSubmission.results()).hasSize(1);
        // Verify that the tutor can see the manual result
        ResultDTO returnedResult = returnedSubmission.results().getFirst();
        assertThat(returnedResult.assessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(returnedResult.assessor()).isNull();
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
        request.getList("/api/modeling/participations/" + participation.getId() + "/submissions-with-results", HttpStatus.BAD_REQUEST, ModelingSubmissionResponseDTO.class);
    }

    private void checkDetailsHidden(ModelingSubmissionResponseDTO submission, boolean isStudent) {
        // The participation.submissions component is intentionally omitted from the DTO (the client rebuilds it), so old
        // submissions can never leak here.
        if (isStudent) {
            var modelingExercise = submission.participation().exercise();
            assertThat(modelingExercise.exampleSolutionModel()).isNullOrEmpty();
            assertThat(modelingExercise.exampleSolutionExplanation()).isNullOrEmpty();
            assertThat(submission.results()).isNullOrEmpty();
        }
    }

    private static ModelingSubmissionRequestDTO toRequest(ModelingSubmission submission) {
        return new ModelingSubmissionRequestDTO(submission.getId(), submission.getModel(), submission.getExplanationText(), submission.isSubmitted());
    }

    private void assertTeamParticipationOwners(ModelingSubmissionResponseDTO response) {
        assertThat(response.participation()).as("participation is present").isNotNull();
        assertThat(response.participation().team()).as("team is present").isNotNull();
        assertThat(response.participation().team().students()).as("team members are present").isNotNull();
        assertThat(response.participation().team().students()).extracting(UserNameDTO::login).containsExactlyInAnyOrder(TEST_PREFIX + "student1", TEST_PREFIX + "student2");
    }

    private ModelingSubmissionResponseDTO performInitialModelSubmission(Long exerciseId, ModelingSubmission submission) throws Exception {
        return request.postWithResponseBody("/api/modeling/exercises/" + exerciseId + "/modeling-submissions", toRequest(submission), ModelingSubmissionResponseDTO.class,
                HttpStatus.OK);
    }

    private ModelingSubmissionResponseDTO performUpdateOnModelSubmission(Long exerciseId, ModelingSubmissionRequestDTO submission) throws Exception {
        return request.putWithResponseBody("/api/modeling/exercises/" + exerciseId + "/modeling-submissions", submission, ModelingSubmissionResponseDTO.class, HttpStatus.OK);
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
