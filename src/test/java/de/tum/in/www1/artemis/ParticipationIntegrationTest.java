package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.programmingexercise.ProgrammingExerciseTestService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.GradingScaleService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggleService;
import de.tum.in.www1.artemis.service.scheduled.cache.quiz.QuizScheduleService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.QuizUtilService;

class ParticipationIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "participationintegration";

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private StudentParticipationRepository participationRepo;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private QuizUtilService quizUtilService;

    @Autowired
    protected QuizScheduleService quizScheduleService;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private GradingScaleService gradingScaleService;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    private Course course;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTestCase() throws Exception {
        database.addUsers(TEST_PREFIX, 2, 2, 0, 2);

        // Add users that are not in the course/exercise
        database.createAndSaveUser(TEST_PREFIX + "student3");
        database.createAndSaveUser(TEST_PREFIX + "tutor3");
        database.createAndSaveUser(TEST_PREFIX + "instructor3");

        course = database.addCourseWithModelingAndTextExercise();
        for (Exercise exercise : course.getExercises()) {
            if (exercise instanceof ModelingExercise) {
                modelingExercise = (ModelingExercise) exercise;
            }
            if (exercise instanceof TextExercise) {
                textExercise = (TextExercise) exercise;
            }
        }
        modelingExercise.setTitle("UML Class Diagram");
        exerciseRepo.save(modelingExercise);

        programmingExercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), course);
        programmingExercise = exerciseRepo.save(programmingExercise);
        course.addExercises(programmingExercise);
        course = courseRepo.save(course);

        doReturn(defaultBranch).when(versionControlService).getDefaultBranchOfRepository(any());
        doReturn("Success").when(continuousIntegrationService).copyBuildPlan(any(), any(), any(), any(), any(), anyBoolean());
        doNothing().when(continuousIntegrationService).configureBuildPlan(any(), any());
        doNothing().when(continuousIntegrationService).performEmptySetupCommit(any());

        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
    }

    @AfterEach
    void tearDown() throws Exception {
        featureToggleService.enableFeature(Feature.ProgrammingExercises);
        programmingExerciseTestService.tearDown();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInModelingExercise() throws Exception {
        URI location = request.post("/api/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.CREATED);

        StudentParticipation participation = request.get(location.getPath(), HttpStatus.OK, StudentParticipation.class);
        assertThat(participation.getExercise()).as("participated in correct exercise").isEqualTo(modelingExercise);
        assertThat(participation.getStudent()).as("Student got set").isNotNull();
        assertThat(participation.getParticipantIdentifier()).as("Correct student got set").isEqualTo(TEST_PREFIX + "student1");
        Participation storedParticipation = participationRepo
                .findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(modelingExercise.getId(), TEST_PREFIX + "student1", false).get();
        assertThat(storedParticipation.getSubmissions()).as("submission was initialized").hasSize(1);
        assertThat(storedParticipation.getSubmissions().iterator().next().getClass()).as("submission is of type modeling submission").isEqualTo(ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void participateInTextExercise() throws Exception {
        URI location = request.post("/api/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);

        StudentParticipation participation = request.get(location.getPath(), HttpStatus.OK, StudentParticipation.class);
        assertThat(participation.getExercise()).as("participated in correct exercise").isEqualTo(textExercise);
        assertThat(participation.getStudent()).as("Student got set").isNotNull();
        assertThat(participation.getParticipantIdentifier()).as("Correct student got set").isEqualTo(TEST_PREFIX + "student2");
        Participation storedParticipation = participationRepo
                .findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(textExercise.getId(), TEST_PREFIX + "student2", false).get();
        assertThat(storedParticipation.getSubmissions()).as("submission was initialized").hasSize(1);
        assertThat(storedParticipation.getSubmissions().iterator().next().getClass()).as("submission is of type text submission").isEqualTo(TextSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateTwiceInModelingExercise_sameParticipation() throws Exception {
        var participation1 = request.post("/api/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.CREATED);
        var participation2 = request.post("/api/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.CREATED);
        assertThat(participation1).isEqualTo(participation2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateTwiceInTextExercise_sameParticipation() throws Exception {
        var participation1 = request.post("/api/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);
        var participation2 = request.post("/api/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);
        assertThat(participation1).isEqualTo(participation2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void participateInTextExercise_releaseDateNotReached() throws Exception {
        textExercise.setReleaseDate(ZonedDateTime.now().plusHours(2));
        exerciseRepo.save(textExercise);
        request.post("/api/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void participateInTextExercise_noReleaseDateStartDateNotReached() throws Exception {
        textExercise.setReleaseDate(null);
        textExercise.setStartDate(ZonedDateTime.now().plusHours(2));
        exerciseRepo.save(textExercise);
        request.post("/api/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void participateInTextExercise_releaseDateReachedStartDateNotReached() throws Exception {
        textExercise.setReleaseDate(ZonedDateTime.now().minusMinutes(1));
        textExercise.setStartDate(ZonedDateTime.now().plusHours(2));
        exerciseRepo.save(textExercise);
        request.post("/api/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void participateInTextExercise_releaseDateReachedStartDateNotReachedAsTutor() throws Exception {
        textExercise.setReleaseDate(ZonedDateTime.now().minusMinutes(1));
        textExercise.setStartDate(ZonedDateTime.now().plusHours(2));
        exerciseRepo.save(textExercise);
        request.post("/api/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInTextExercise_releaseDateReachedStartDateReached() throws Exception {
        textExercise.setReleaseDate(ZonedDateTime.now().minusMinutes(2));
        textExercise.setStartDate(ZonedDateTime.now().minusMinutes(1));
        exerciseRepo.save(textExercise);
        request.post("/api/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3")
    void participateInTextExercise_notStudentInCourse() throws Exception {
        request.post("/api/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInProgrammingExercise_featureDisabled() throws Exception {
        featureToggleService.disableFeature(Feature.ProgrammingExercises);
        request.post("/api/exercises/" + programmingExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);

        // Reset
        featureToggleService.enableFeature(Feature.ProgrammingExercises);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void practiceProgrammingExercise_featureDisabled() throws Exception {
        featureToggleService.disableFeature(Feature.ProgrammingExercises);
        request.post("/api/exercises/" + programmingExercise.getId() + "/participations/practice", null, HttpStatus.FORBIDDEN);

        // Reset
        featureToggleService.enableFeature(Feature.ProgrammingExercises);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInProgrammingExercise_dueDatePassed() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        exerciseRepo.save(programmingExercise);
        request.post("/api/exercises/" + programmingExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void practiceProgrammingExercise_beforeDatePassed() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exerciseRepo.save(programmingExercise);
        request.post("/api/exercises/" + programmingExercise.getId() + "/participations/practice", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInProgrammingTeamExercise_withoutAssignedTeam() throws Exception {
        programmingExercise.setMode(ExerciseMode.TEAM);
        exerciseRepo.save(programmingExercise);
        request.post("/api/exercises/" + programmingExercise.getId() + "/participations", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void practiceProgrammingExercise_successful() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        exerciseRepo.save(programmingExercise);
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        User user = database.getUserByLogin(TEST_PREFIX + "student1");
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bambooRequestMockProvider.enableMockingOfRequests(true);

        programmingExerciseTestService.setupRepositoryMocks(programmingExercise);
        var repo = new LocalRepository(defaultBranch);
        repo.configureRepos("studentRepo", "studentOriginRepo");
        programmingExerciseTestService.setupRepositoryMocksParticipant(programmingExercise, user.getLogin(), repo, true);
        mockConnectorRequestsForStartPractice(programmingExercise, TEST_PREFIX + "student1", Set.of(user), true);

        StudentParticipation participation = request.postWithResponseBody("/api/exercises/" + programmingExercise.getId() + "/participations/practice", null,
                StudentParticipation.class, HttpStatus.CREATED);
        assertThat(participation).isNotNull();
        assertThat(participation.isTestRun()).isTrue();
        assertThat(participation.getStudent()).contains(user);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInProgrammingExercise_successful() throws Exception {
        database.addTemplateParticipationForProgrammingExercise(programmingExercise);
        User user = database.getUserByLogin(TEST_PREFIX + "student1");
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bambooRequestMockProvider.enableMockingOfRequests(true);

        programmingExerciseTestService.setupRepositoryMocks(programmingExercise);
        var repo = new LocalRepository(defaultBranch);
        repo.configureRepos("studentRepo", "studentOriginRepo");
        programmingExerciseTestService.setupRepositoryMocksParticipant(programmingExercise, user.getLogin(), repo);
        mockConnectorRequestsForStartParticipation(programmingExercise, TEST_PREFIX + "student1", Set.of(user), true);

        StudentParticipation participation = request.postWithResponseBody("/api/exercises/" + programmingExercise.getId() + "/participations", null, StudentParticipation.class,
                HttpStatus.CREATED);
        assertThat(participation).isNotNull();
        assertThat(participation.isTestRun()).isFalse();
        assertThat(participation.getStudent()).contains(user);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void practiceProgrammingTeamExercise_Forbidden() throws Exception {
        programmingExercise.setMode(ExerciseMode.TEAM);
        exerciseRepo.save(programmingExercise);
        request.post("/api/exercises/" + programmingExercise.getId() + "/participations/practice", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteParticipation() throws Exception {
        Submission submissionWithResult = database.addSubmission(modelingExercise, new ModelingSubmission(), TEST_PREFIX + "student1");
        database.addSubmission((StudentParticipation) submissionWithResult.getParticipation(), new ModelingSubmission());
        Long participationId = submissionWithResult.getParticipation().getId();
        database.addResultToSubmission(submissionWithResult, null);

        // Participation should now exist.
        assertThat(participationRepo.existsById(participationId)).isTrue();
        // There should be a submission and result assigned to the participation.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).hasSize(2);
        assertThat(resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId)).hasSize(1);

        request.delete("/api/participations/" + participationId, HttpStatus.OK);
        Optional<StudentParticipation> participation = participationRepo.findById(participationId);
        // Participation should now be gone.
        assertThat(participation).isEmpty();
        // Make sure that also the submission and result were deleted.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).isEmpty();
        assertThat(resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteSubmissionWithoutResult() throws Exception {
        Submission submissionWithoutResult = database.addSubmission(modelingExercise, new ModelingSubmission(), TEST_PREFIX + "student1");
        database.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);
        Long participationId = submissionWithoutResult.getParticipation().getId();

        // Participation should now exist.
        assertThat(participationRepo.existsById(participationId)).isTrue();

        // There should be a submission and no result assigned to the participation.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).hasSize(1);
        assertThat(resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId)).isEmpty();

        request.delete("/api/participations/" + participationId, HttpStatus.OK);
        Optional<StudentParticipation> participation = participationRepo.findById(participationId);
        // Participation should now be gone.
        assertThat(participation).isEmpty();
        // Make sure that the submission is deleted.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteResultWithoutSubmission() throws Exception {
        StudentParticipation studentParticipation = database.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "student1");
        database.addResultToParticipation(null, null, studentParticipation);
        Long participationId = studentParticipation.getId();

        // Participation should now exist.
        assertThat(participationRepo.existsById(participationId)).isTrue();

        // There should be a submission and no result assigned to the participation.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).isEmpty();
        assertThat(resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId)).hasSize(1);

        request.delete("/api/participations/" + participationId, HttpStatus.OK);
        Optional<StudentParticipation> participation = participationRepo.findById(participationId);
        // Participation should now be gone.
        assertThat(participation).isEmpty();
        // Make sure that the result is deleted.
        assertThat(resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteParticipation_student() throws Exception {
        // Allow students to delete their own participation if it belongs to a guided tour
        StudentParticipation studentParticipation = database.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "student1");
        request.delete("/api/guided-tour/participations/" + studentParticipation.getId(), HttpStatus.OK);

        // Returns forbidden if users do not delete their own participation
        StudentParticipation studentParticipation2 = database.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "student2");
        request.delete("/api/guided-tour/participations/" + studentParticipation2.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void deleteParticipation_tutor() throws Exception {
        // Allow tutors to delete their own participation if it belongs to a guided tour
        StudentParticipation studentParticipation = database.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "tutor1");
        request.delete("/api/guided-tour/participations/" + studentParticipation.getId(), HttpStatus.OK);

        // Returns forbidden if tutors do not delete their own participation
        StudentParticipation studentParticipation2 = database.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "student1");
        request.delete("/api/guided-tour/participations/" + studentParticipation2.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteParticipation_notFound() throws Exception {
        request.delete("/api/participations/" + -1, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void requestFeedbackScoreNotFull() throws Exception {
        var participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, programmingExercise,
                database.getUserByLogin(TEST_PREFIX + "student1"));

        var localRepo = new LocalRepository(defaultBranch);
        localRepo.configureRepos("testLocalRepo", "testOriginRepo");

        participation.setRepositoryUrl(ModelFactory.getMockFileRepositoryUrl(localRepo).getURI().toString());
        participationRepo.save(participation);

        gitService.getDefaultLocalPathOfRepo(participation.getVcsRepositoryUrl());

        var result = ModelFactory.generateResult(true, 90).participation(participation);
        result.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(result);

        request.putWithResponseBody("/api/exercises/" + programmingExercise.getId() + "/request-feedback", null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void requestFeedbackExerciseNotPossibleIfOnlyAutomaticFeedbacks() throws Exception {
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        exerciseRepo.save(programmingExercise);

        var participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise,
                database.getUserByLogin(TEST_PREFIX + "student1"));
        participationRepo.save(participation);

        request.putWithResponseBody("/api/exercises/" + programmingExercise.getId() + "/request-feedback", null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void requestFeedbackAlreadySent() throws Exception {
        var participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise,
                database.getUserByLogin(TEST_PREFIX + "student1"));
        participation.setIndividualDueDate(ZonedDateTime.now().minusMinutes(20));
        participationRepo.save(participation);

        request.putWithResponseBody("/api/exercises/" + programmingExercise.getId() + "/request-feedback", null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void requestFeedbackSuccess() throws Exception {
        var participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, programmingExercise,
                database.getUserByLogin(TEST_PREFIX + "student1"));

        var localRepo = new LocalRepository(defaultBranch);
        localRepo.configureRepos("testLocalRepo", "testOriginRepo");

        participation.setRepositoryUrl(ModelFactory.getMockFileRepositoryUrl(localRepo).getURI().toString());
        participationRepo.save(participation);

        gitService.getDefaultLocalPathOfRepo(participation.getVcsRepositoryUrl());

        var result = ModelFactory.generateResult(true, 100).participation(participation);
        result.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(result);

        doNothing().when(programmingExerciseParticipationService).lockStudentRepository(programmingExercise, participation);

        var response = request.putWithResponseBody("/api/exercises/" + programmingExercise.getId() + "/request-feedback", null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.OK);

        assertThat(response.getResults()).allMatch(result1 -> result.getAssessmentType() == AssessmentType.SEMI_AUTOMATIC);
        assertThat(response.getIndividualDueDate()).isNotNull().isBefore(ZonedDateTime.now());

        verify(programmingExerciseParticipationService, times(1)).lockStudentRepository(programmingExercise, participation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resumeProgrammingExerciseParticipation() throws Exception {
        var participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, programmingExercise,
                database.getUserByLogin(TEST_PREFIX + "student1"));
        var localRepo = new LocalRepository(defaultBranch);
        localRepo.configureRepos("testLocalRepo", "testOriginRepo");
        participation.setRepositoryUrl(ModelFactory.getMockFileRepositoryUrl(localRepo).getURI().toString());
        participationRepo.save(participation);
        gitService.getDefaultLocalPathOfRepo(participation.getVcsRepositoryUrl());
        var updatedParticipation = request.putWithResponseBody("/api/exercises/" + programmingExercise.getId() + "/resume-programming-participation/" + participation.getId(), null,
                ProgrammingExerciseStudentParticipation.class, HttpStatus.OK);
        assertThat(updatedParticipation.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resumeProgrammingExerciseParticipation_wrongExerciseId() throws Exception {
        var participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise,
                database.getUserByLogin(TEST_PREFIX + "student1"));
        participationRepo.save(participation);
        request.putWithResponseBody("/api/exercises/10000/resume-programming-participation/" + participation.getId(), null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resumeProgrammingExerciseParticipation_forbidden() throws Exception {
        var exercise = ModelFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1), course);
        exercise = exerciseRepo.save(exercise);
        var participation = ModelFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, exercise, database.getUserByLogin(TEST_PREFIX + "student1"));
        participationRepo.save(participation);
        request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/resume-programming-participation/" + participation.getId(), null,
                ProgrammingExerciseStudentParticipation.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllParticipationsForExercise() throws Exception {
        database.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        database.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student2");
        StudentParticipation testParticipation = database.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student3");
        testParticipation.setTestRun(true);
        participationRepo.save(testParticipation);
        var participations = request.getList("/api/exercises/" + textExercise.getId() + "/participations", HttpStatus.OK, StudentParticipation.class);
        assertThat(participations).as("Exactly 3 participations are returned").hasSize(3).as("Only participation that has student are returned")
                .allMatch(participation -> participation.getStudent().isPresent()).as("No submissions should exist for participations")
                .allMatch(participation -> participation.getSubmissionCount() == null || participation.getSubmissionCount() == 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllParticipationsForExercise_withLatestResults() throws Exception {
        database.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        var participation = database.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student2");
        database.addResultToParticipation(null, null, participation);
        var result = ModelFactory.generateResult(true, 70D).participation(participation);
        resultRepository.save(result);
        StudentParticipation testParticipation = database.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student3");
        testParticipation.setTestRun(true);
        participationRepo.save(testParticipation);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("withLatestResults", "true");
        var participations = request.getList("/api/exercises/" + textExercise.getId() + "/participations", HttpStatus.OK, StudentParticipation.class, params);
        assertThat(participations).as("Exactly 3 participations are returned").hasSize(3).as("Only participation that has student are returned")
                .allMatch(p -> p.getStudent().isPresent()).as("No submissions should exist for participations")
                .allMatch(p -> p.getSubmissionCount() == null || p.getSubmissionCount() == 0);
        var participationWithResult = participations.stream().filter(p -> p.getParticipant().equals(database.getUserByLogin(TEST_PREFIX + "student2"))).findFirst().get();
        assertThat(participationWithResult.getResults()).hasSize(1).contains(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllParticipationsForExercise_withLatestResults_multipleAssessments() throws Exception {
        var participation1 = database.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        var participation2 = database.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student2");
        var participation3 = database.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student3");
        database.addResultToParticipation(AssessmentType.AUTOMATIC, null, participation1);
        database.addResultToParticipation(AssessmentType.MANUAL, null, participation1);
        database.addResultToParticipation(AssessmentType.MANUAL, null, participation2);
        database.addResultToParticipation(AssessmentType.MANUAL, null, participation2);
        database.addResultToParticipation(AssessmentType.MANUAL, null, participation3);
        database.addResultToParticipation(AssessmentType.AUTOMATIC, null, participation3);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("withLatestResults", "true");
        var participations = request.getList("/api/exercises/" + textExercise.getId() + "/participations", HttpStatus.OK, StudentParticipation.class, params);
        assertThat(participations).as("Exactly 3 participations are returned").hasSize(3).as("Only participation that has student are returned")
                .allMatch(p -> p.getStudent().isPresent()).as("No submissions should exist for participations")
                .allMatch(p -> p.getSubmissionCount() == null || p.getSubmissionCount() == 0);
        var recievedParticipation1 = participations.stream().filter(participation -> participation.getParticipant().equals(participation1.getParticipant())).findAny();
        var recievedParticipation2 = participations.stream().filter(participation -> participation.getParticipant().equals(participation2.getParticipant())).findAny();
        var recievedParticipation3 = participations.stream().filter(participation -> participation.getParticipant().equals(participation3.getParticipant())).findAny();
        assertThat(recievedParticipation1).hasValueSatisfying(participation -> assertThat(participation.getResults()).hasSize(1));
        assertThat(recievedParticipation2).hasValueSatisfying(participation -> assertThat(participation.getResults()).hasSize(2));
        assertThat(recievedParticipation3).hasValueSatisfying(participation -> assertThat(participation.getResults()).hasSize(2));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor3", roles = "TA")
    void getAllParticipationsForExercise_NotTutorInCourse() throws Exception {
        request.getList("/api/exercises/" + textExercise.getId() + "/participations", HttpStatus.FORBIDDEN, StudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllParticipationsForCourse() throws Exception {
        database.createAndSaveParticipationForExercise(programmingExercise, TEST_PREFIX + "student1");
        database.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student2");
        database.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "student1");
        var quizEx = ModelFactory.generateQuizExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), QuizMode.SYNCHRONIZED, course);
        exerciseRepo.save(quizEx);
        database.createAndSaveParticipationForExercise(quizEx, TEST_PREFIX + "student2");

        var participations = request.getList("/api/courses/" + course.getId() + "/participations", HttpStatus.OK, StudentParticipation.class);
        assertThat(participations).hasSize(4);
        participations.forEach(participation -> {
            var exercise = participation.getExercise();
            assertThat(exercise.getCourseViaExerciseGroupOrCourseMember()).isNull();
            assertThat(exercise.getStudentParticipations()).isEmpty();
            assertThat(exercise.getTutorParticipations()).isEmpty();
            assertThat(exercise.getExampleSubmissions()).isEmpty();
            assertThat(exercise.getAttachments()).isEmpty();
            assertThat(exercise.getCategories()).isEmpty();
            assertThat(exercise.getProblemStatement()).isNull();
            assertThat(exercise.getPosts()).isEmpty();
            assertThat(exercise.getGradingInstructions()).isNull();
            assertThat(exercise.getDifficulty()).isNull();
            assertThat(exercise.getMode()).isEqualTo(ExerciseMode.INDIVIDUAL);
            if (exercise instanceof ProgrammingExercise aProgrammingExercise) {
                assertThat(aProgrammingExercise.getSolutionParticipation()).isNull();
                assertThat(aProgrammingExercise.getTemplateParticipation()).isNull();
                assertThat(aProgrammingExercise.getTestRepositoryUrl()).isNull();
                assertThat(aProgrammingExercise.getShortName()).isNull();
                assertThat(aProgrammingExercise.isPublishBuildPlanUrl()).isNull();
                assertThat(aProgrammingExercise.getProgrammingLanguage()).isNull();
                assertThat(aProgrammingExercise.getPackageName()).isNull();
                assertThat(aProgrammingExercise.isAllowOnlineEditor()).isNull();
            }
            else if (exercise instanceof QuizExercise quizExercise) {
                assertThat(quizExercise.getQuizQuestions()).isEmpty();
            }
            else if (exercise instanceof TextExercise aTextExercise) {
                assertThat(aTextExercise.getExampleSolution()).isNull();
            }
            else if (exercise instanceof ModelingExercise aModelingExercise) {
                assertThat(aModelingExercise.getExampleSolutionModel()).isNull();
                assertThat(aModelingExercise.getExampleSolutionExplanation()).isNull();
            }
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor3", roles = "INSTRUCTOR")
    void getAllParticipationsForCourse_noInstructorInCourse() throws Exception {
        request.getList("/api/courses/" + course.getId() + "/participations", HttpStatus.FORBIDDEN, StudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateParticipation() throws Exception {
        var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, database.getUserByLogin(TEST_PREFIX + "student1"));
        participation.setPresentationScore(1.);
        participation = participationRepo.save(participation);
        participation.setPresentationScore(null);
        var actualParticipation = request.putWithResponseBody("/api/exercises/" + textExercise.getId() + "/participations", participation, StudentParticipation.class,
                HttpStatus.OK);
        assertThat(actualParticipation).as("The participation was updated").isNotNull();
        assertThat(actualParticipation.getPresentationScore()).as("Presentation score was set to null").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateParticipation_notStored() throws Exception {
        var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, database.getUserByLogin(TEST_PREFIX + "student1"));
        request.putWithResponseBody("/api/exercises/" + textExercise.getId() + "/participations", participation, StudentParticipation.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateParticipation_presentationScoreMoreThan0() throws Exception {
        var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, database.getUserByLogin(TEST_PREFIX + "student1"));
        participation = participationRepo.save(participation);
        participation.setPresentationScore(2.);
        var actualParticipation = request.putWithResponseBody("/api/exercises/" + textExercise.getId() + "/participations", participation, StudentParticipation.class,
                HttpStatus.OK);
        assertThat(actualParticipation).as("The participation was updated").isNotNull();
        assertThat(actualParticipation.getPresentationScore()).as("Presentation score was set to 1").isEqualTo(1.);
    }

    @ParameterizedTest
    @CsvSource({ "-42,true", "42,false", "420,true" })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateParticipation_gradedPresentation(double input, boolean isBadRequest) throws Exception {
        Course course = textExercise.getCourseViaExerciseGroupOrCourseMember();
        course.setPresentationScore(0);

        GradingScale gradingScale = database.generateGradingScale(2, new double[] { 0, 50, 100 }, true, 1, Optional.empty(), course, 2, 20.);
        gradingScaleService.saveGradingScale(gradingScale);

        StudentParticipation participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise,
                database.getUserByLogin(TEST_PREFIX + "student1"));
        participation = participationRepo.save(participation);

        participation.setPresentationScore(input);

        if (isBadRequest) {
            StudentParticipation actualParticipation = request.putWithResponseBody("/api/exercises/" + textExercise.getId() + "/participations", participation,
                    StudentParticipation.class, HttpStatus.BAD_REQUEST);
            assertThat(actualParticipation).as("The participation was not updated").isNull();
        }
        else {
            StudentParticipation actualParticipation = request.putWithResponseBody("/api/exercises/" + textExercise.getId() + "/participations", participation,
                    StudentParticipation.class, HttpStatus.OK);
            assertThat(actualParticipation).as("The participation was updated").isNotNull();
            assertThat(actualParticipation.getPresentationScore()).as("Presentation score was set to " + input).isEqualTo(input);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor3", roles = "TA")
    void updateParticipation_notTutorInCourse() throws Exception {
        var participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise, database.getUserByLogin(TEST_PREFIX + "student1"));
        participation = participationRepo.save(participation);
        request.putWithResponseBody("/api/exercises/" + textExercise.getId() + "/participations", participation, StudentParticipation.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDateExamExercise() throws Exception {
        final FileUploadExercise exercise = database.addCourseExamExerciseGroupWithOneFileUploadExercise();
        StudentParticipation participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise,
                database.getUserByLogin(TEST_PREFIX + "student1"));
        participation = participationRepo.save(participation);
        participation.setIndividualDueDate(ZonedDateTime.now().plusDays(3));

        final var participationsToUpdate = new StudentParticipationList(participation);
        request.putAndExpectError(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate, HttpStatus.BAD_REQUEST,
                "examexercise");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDateQuizExercise() throws Exception {
        final Course course = database.addCourseWithOneQuizExercise();
        final QuizExercise exercise = (QuizExercise) course.getExercises().stream().findFirst().get();
        StudentParticipation participation = ModelFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise,
                database.getUserByLogin(TEST_PREFIX + "student1"));
        participation = participationRepo.save(participation);
        participation.setIndividualDueDate(ZonedDateTime.now().plusDays(3));

        final var participationsToUpdate = new StudentParticipationList(participation);
        request.putAndExpectError(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate, HttpStatus.BAD_REQUEST,
                "quizexercise");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDateOk() throws Exception {
        final var course = database.addCourseWithFileUploadExercise();
        var exercise = (FileUploadExercise) course.getExercises().stream().findAny().orElseThrow();
        exercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exercise = exerciseRepo.save(exercise);

        var submission = database.addFileUploadSubmission(exercise, ModelFactory.generateFileUploadSubmission(true), TEST_PREFIX + "student1");
        submission.getParticipation().setIndividualDueDate(ZonedDateTime.now().plusDays(1));

        final var participationsToUpdate = new StudentParticipationList((StudentParticipation) submission.getParticipation());
        final var response = request.putWithResponseBodyList(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate,
                StudentParticipation.class, HttpStatus.OK);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getIndividualDueDate()).isEqualToIgnoringNanos(submission.getParticipation().getIndividualDueDate());

        verify(programmingExerciseScheduleService, never()).updateScheduling(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDateProgrammingExercise() throws Exception {
        final var course = database.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().get();
        exercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exercise = exerciseRepo.save(exercise);

        final var participation = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(20));

        // due date before exercise due date ⇒ should be ignored
        final var participation2 = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student2");
        participation2.setIndividualDueDate(ZonedDateTime.now().plusHours(1));

        doNothing().when(programmingExerciseParticipationService).unlockStudentRepository(exercise, participation);

        final var participationsToUpdate = new StudentParticipationList(participation, participation2);
        final var response = request.putWithResponseBodyList(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate,
                StudentParticipation.class, HttpStatus.OK);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getIndividualDueDate()).isEqualToIgnoringNanos(participation.getIndividualDueDate());

        verify(programmingExerciseScheduleService, times(1)).updateScheduling(exercise);
        verify(programmingExerciseParticipationService, times(1)).unlockStudentRepository(exercise, participation);
        verify(programmingExerciseParticipationService, never()).unlockStudentRepository(exercise, participation2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDateUnchanged() throws Exception {
        final var course = database.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().get();
        exercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exercise = exerciseRepo.save(exercise);

        final var participation = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        final var participationsToUpdate = new StudentParticipationList(participation);
        final var response = request.putWithResponseBodyList(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate,
                StudentParticipation.class, HttpStatus.OK);

        assertThat(response).isEmpty();
        verify(programmingExerciseScheduleService, never()).updateScheduling(exercise);
        verify(programmingExerciseParticipationService, never()).unlockStudentRepository(exercise, participation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDateNoExerciseDueDate() throws Exception {
        final var course = database.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().get();
        exercise.setDueDate(null);
        exercise = exerciseRepo.save(exercise);

        var participation = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(4));

        final var participationsToUpdate = new StudentParticipationList(participation);
        final var response = request.putWithResponseBodyList(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate,
                StudentParticipation.class, HttpStatus.OK);

        assertThat(response).isEmpty(); // individual due date should remain null
        verify(programmingExerciseScheduleService, never()).updateScheduling(exercise);
        verify(programmingExerciseParticipationService, never()).unlockStudentRepository(exercise, participation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseIndividualDueDateInFuture() throws Exception {
        final var course = database.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().get();
        exercise.setDueDate(ZonedDateTime.now().minusHours(4));
        exercise = exerciseRepo.save(exercise);

        var participation = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(6));
        participation = participationRepo.save(participation);

        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(2));

        doNothing().when(programmingExerciseParticipationService).unlockStudentRepository(exercise, participation);

        final var participationsToUpdate = new StudentParticipationList(participation);
        final var response = request.putWithResponseBodyList(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate,
                StudentParticipation.class, HttpStatus.OK);

        assertThat(response).hasSize(1);
        verify(programmingExerciseScheduleService, times(1)).updateScheduling(exercise);
        // make sure the student repo is unlocked as the due date is in the future
        verify(programmingExerciseParticipationService, times(1)).unlockStudentRepository(exercise, participation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseIndividualDueDateInPast() throws Exception {
        final var course = database.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().get();
        exercise.setDueDate(ZonedDateTime.now().minusHours(4));
        exercise = exerciseRepo.save(exercise);

        var participation = database.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(4));
        participation = participationRepo.save(participation);

        participation.setIndividualDueDate(ZonedDateTime.now().minusHours(2));

        doNothing().when(programmingExerciseParticipationService).lockStudentRepository(exercise, participation);

        final var participationsToUpdate = new StudentParticipationList(participation);
        final var response = request.putWithResponseBodyList(String.format("/api/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate,
                StudentParticipation.class, HttpStatus.OK);

        assertThat(response).hasSize(1);
        verify(programmingExerciseScheduleService, times(1)).updateScheduling(exercise);
        // student repo should be locked as due date is in the past
        verify(programmingExerciseParticipationService, times(1)).lockStudentRepository(exercise, participation);
    }

    /**
     * When using {@code List<StudentParticipation>} directly as body in the unit tests, the deserialization fails as
     * there no longer is a {@code type} attribute due to type erasure. Therefore, Jackson does not know which subtype
     * of {@link Participation} is stored in the list.
     *
     * Using this wrapper-class avoids this issue.
     */
    private static class StudentParticipationList extends ArrayList<StudentParticipation> {

        public StudentParticipationList(StudentParticipation... participations) {
            super();
            this.addAll(Arrays.asList(participations));
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipationWithLatestResult() throws Exception {
        var participation = database.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        database.addResultToParticipation(null, null, participation);
        var result = ModelFactory.generateResult(true, 70D);
        result.participation(participation).setCompletionDate(ZonedDateTime.now().minusHours(2));
        resultRepository.save(result);
        var actualParticipation = request.get("/api/participations/" + participation.getId() + "/withLatestResult", HttpStatus.OK, StudentParticipation.class);

        assertThat(actualParticipation).isNotNull();
        assertThat(actualParticipation.getResults()).hasSize(1);
        assertThat(actualParticipation.getResults().iterator().next()).as("Only latest result is returned").isEqualTo(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipationBuildArtifact() throws Exception {
        var participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        doReturn(new ResponseEntity<>(null, HttpStatus.OK)).when(continuousIntegrationService).retrieveLatestArtifact(participation);
        request.getNullable("/api/participations/" + participation.getId() + "/buildArtifact", HttpStatus.OK, Object.class);
        verify(continuousIntegrationService).retrieveLatestArtifact(participation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSubmissionOfParticipation() throws Exception {
        var participation = database.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        var submission1 = database.addSubmission(participation, ModelFactory.generateTextSubmission("text", Language.ENGLISH, true));
        var submission2 = database.addSubmission(participation, ModelFactory.generateTextSubmission("text2", Language.ENGLISH, true));
        var submissions = request.getList("/api/participations/" + participation.getId() + "/submissions", HttpStatus.OK, Submission.class);
        assertThat(submissions).contains(submission1, submission2);
    }

    @ParameterizedTest
    @CsvSource({ "false,false", "false,true", "true,false", "true,true" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void cleanupBuildPlan(boolean practiceMode, boolean afterDueDate) throws Exception {
        var participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participation.setTestRun(practiceMode);
        participationRepo.save(participation);
        if (afterDueDate) {
            programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
            exerciseRepo.save(programmingExercise);
        }
        bambooRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.mockDeleteBambooBuildPlan(participation.getBuildPlanId(), false);
        var actualParticipation = request.putWithResponseBody("/api/participations/" + participation.getId() + "/cleanupBuildPlan", null, Participation.class, HttpStatus.OK);
        assertThat(actualParticipation).isEqualTo(participation);
        assertThat(actualParticipation.getInitializationState()).isEqualTo(!practiceMode && afterDueDate ? InitializationState.FINISHED : InitializationState.INACTIVE);
        assertThat(((ProgrammingExerciseStudentParticipation) actualParticipation).getBuildPlanId()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipation() throws Exception {
        var participation = database.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        var actualParticipation = request.get("/api/exercises/" + textExercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipationForTeamExercise() throws Exception {
        var now = ZonedDateTime.now();
        var exercise = ModelFactory.generateTextExercise(now.minusDays(2), now.plusDays(2), now.plusDays(4), course);
        exercise.setMode(ExerciseMode.TEAM);
        exercise = exerciseRepo.save(exercise);

        var student = database.getUserByLogin(TEST_PREFIX + "student1");

        var team = createTeamForExercise(student, exercise);

        var teams = new HashSet<Team>();
        teams.add(team);
        exercise.setTeams(teams);
        exercise = exerciseRepo.save(exercise);

        var participation = database.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipationByExerciseAndStudentIdWithEagerSubmissionsForTeam() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = database.getUserByLogin(TEST_PREFIX + "student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = database.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        var participations = participationService.findByExerciseAndStudentIdWithEagerSubmissions(exercise, student.getId());
        assertThat(participations).hasSize(1);
        assertThat(participations.get(0).getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipationByExerciseAndStudentIdForTeam() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = database.getUserByLogin(TEST_PREFIX + "student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = database.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        var participations = participationService.findByExerciseAndStudentId(exercise, student.getId());
        assertThat(participations).hasSize(1);
        assertThat(participations.get(0).getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipationByExerciseAndStudentLoginAnyStateWithEagerResultsForTeam() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = database.getUserByLogin(TEST_PREFIX + "student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = database.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        participation = participationService.findOneByExerciseAndStudentLoginAnyStateWithEagerResultsElseThrow(exercise, student.getLogin());
        assertThat(participation).isNotNull();
        assertThat(participation.getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipationByExerciseAndStudentLoginAnyStateForTeam() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = database.getUserByLogin(TEST_PREFIX + "student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = database.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        var participations = participationService.findOneByExerciseAndStudentLoginAnyState(exercise, student.getLogin());
        assertThat(participations).isPresent();
        assertThat(participations.get().getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteAllByTeamId() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = database.getUserByLogin(TEST_PREFIX + "student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = database.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        participationService.deleteAllByTeamId(team.getId(), false, false);

        var participations = participationRepo.findByTeamId(team.getId());
        assertThat(participations).isEmpty();
    }

    private Exercise createTextExerciseForTeam() {
        var now = ZonedDateTime.now();
        var exercise = ModelFactory.generateTextExercise(now.minusDays(2), now.plusDays(2), now.plusDays(4), course);
        exercise.setMode(ExerciseMode.TEAM);
        return exerciseRepo.save(exercise);
    }

    private Team createTeamForExercise(User student, Exercise exercise) {
        var team = new Team();
        team.setShortName("t" + UUID.randomUUID().toString().substring(0, 3));
        team.addStudents(student);
        team.setExercise(exercise);
        return teamRepository.save(team);
    }

    private Exercise addTeamToExercise(Team team, Exercise exercise) {
        var teams = new HashSet<Team>();
        teams.add(team);
        exercise.setTeams(teams);
        return exerciseRepo.save(exercise);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(QuizMode.class)
    void getParticipation_quizExerciseNotStarted(QuizMode quizMode) throws Exception {
        var quizEx = ModelFactory.generateQuizExercise(ZonedDateTime.now().plusHours(2), ZonedDateTime.now().plusDays(1), quizMode, course);
        quizEx = exerciseRepo.save(quizEx);
        request.get("/api/exercises/" + quizEx.getId() + "/participation", HttpStatus.FORBIDDEN, StudentParticipation.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(QuizMode.class)
    void getParticipation_quizExerciseStartedAndNoParticipation(QuizMode quizMode) throws Exception {
        var quizEx = ModelFactory.generateQuizExercise(ZonedDateTime.now().minusMinutes(2), ZonedDateTime.now().minusMinutes(1), quizMode, course);
        quizEx = exerciseRepo.save(quizEx);
        request.getNullable("/api/exercises/" + quizEx.getId() + "/participation", HttpStatus.NO_CONTENT, StudentParticipation.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(QuizMode.class)
    void getParticipation_quizExerciseStartedAndSubmissionAllowed(QuizMode quizMode) throws Exception {
        var quizEx = ModelFactory.generateQuizExercise(ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now().plusMinutes(5), quizMode, course).duration(360);
        quizEx = exerciseRepo.save(quizEx);
        quizUtilService.prepareBatchForSubmitting(quizEx, SecurityUtils.makeAuthorizationObject(TEST_PREFIX + "instructor1"),
                SecurityContextHolder.getContext().getAuthentication());
        var participation = request.get("/api/exercises/" + quizEx.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(participation.getExercise()).as("Participation contains exercise").isEqualTo(quizEx);
        assertThat(participation.getResults()).as("New result was added to the participation").hasSize(1);
        assertThat(participation.getInitializationState()).as("Participation was initialized").isEqualTo(InitializationState.INITIALIZED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipation_quizBatchNotPresent() throws Exception {
        var quizEx = ModelFactory.generateQuizExercise(ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now().plusMinutes(5), QuizMode.INDIVIDUAL, course).duration(360);
        quizEx = exerciseRepo.save(quizEx);
        var participation = request.get("/api/exercises/" + quizEx.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        quizEx.setRemainingNumberOfAttempts(1);
        assertThat(participation.getExercise()).as("Participation contains exercise").isEqualTo(quizEx);
        assertThat(((QuizExercise) participation.getExercise()).getRemainingNumberOfAttempts()).as("remainingNumberOfAttempts are returned correctly")
                .isEqualTo(quizEx.getRemainingNumberOfAttempts());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(QuizMode.class)
    void getParticipation_quizExerciseFinished(QuizMode quizMode) throws Exception {
        var quizEx = ModelFactory.generateQuizExercise(ZonedDateTime.now().minusMinutes(20), ZonedDateTime.now().minusMinutes(20), quizMode, course);
        quizEx = exerciseRepo.save(quizEx);
        var participation = database.createAndSaveParticipationForExercise(quizEx, TEST_PREFIX + "student1");
        var submission = database.addSubmission(participation, new QuizSubmission().scoreInPoints(11D).submitted(true));
        database.addResultToParticipation(participation, submission);
        var actualParticipation = request.get("/api/exercises/" + quizEx.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipation_noParticipation() throws Exception {
        request.get("/api/exercises/" + textExercise.getId() + "/participation", HttpStatus.FAILED_DEPENDENCY, StudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void getParticipation_notStudentInCourse() throws Exception {
        request.get("/api/exercises/" + textExercise.getId() + "/participation", HttpStatus.FORBIDDEN, StudentParticipation.class);
    }

    @ParameterizedTest
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @MethodSource("getGetParticipationsubmittedNotEndedQuizParameters")
    void getParticipation_submittedNotEndedQuiz(QuizMode quizMode, boolean isSubmissionAllowed) throws Exception {
        QuizExercise quizExercise = ModelFactory.generateQuizExercise(ZonedDateTime.now().minusMinutes(10), ZonedDateTime.now().plusMinutes(10), quizMode, course);
        quizExercise.addQuestions(database.createShortAnswerQuestion());
        quizExercise.setDuration(600);
        quizExercise.setQuizPointStatistic(new QuizPointStatistic());
        quizExercise = exerciseRepo.save(quizExercise);

        quizUtilService.prepareBatchForSubmitting(quizExercise, SecurityUtils.makeAuthorizationObject(TEST_PREFIX + "instructor1"),
                SecurityContextHolder.getContext().getAuthentication());

        ShortAnswerQuestion saQuestion = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(0);
        List<ShortAnswerSpot> spots = saQuestion.getSpots();
        ShortAnswerSubmittedAnswer submittedAnswer = new ShortAnswerSubmittedAnswer();
        submittedAnswer.setQuizQuestion(saQuestion);

        ShortAnswerSubmittedText text = new ShortAnswerSubmittedText();
        text.setSpot(spots.get(0));
        text.setText("test");
        submittedAnswer.addSubmittedTexts(text);

        QuizSubmission quizSubmission = new QuizSubmission();
        quizSubmission.addSubmittedAnswers(submittedAnswer);
        request.postWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/live", quizSubmission, QuizSubmission.class, HttpStatus.OK);

        quizScheduleService.processCachedQuizSubmissions();

        if (!isSubmissionAllowed) {
            // Duration is set to 0 so that QuizBatch.isSubmissionAllowed() will be false
            quizExercise.setDuration(0);
            quizExercise = exerciseRepo.save(quizExercise);
        }

        var actualParticipation = request.get("/api/exercises/" + quizExercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation.getInitializationState()).isEqualTo(InitializationState.FINISHED);

        var actualResults = actualParticipation.getResults();
        assertThat(actualResults).hasSize(1);

        var actualSubmission = (QuizSubmission) actualResults.stream().findFirst().get().getSubmission();
        assertThat(actualSubmission.getType()).isEqualTo(SubmissionType.MANUAL);
        assertThat(actualSubmission.isSubmitted()).isTrue();

        var actualSubmittedAnswers = actualSubmission.getSubmittedAnswers();
        assertThat(actualSubmittedAnswers).hasSize(1);

        var actualSubmittedAnswer = (ShortAnswerSubmittedAnswer) actualSubmittedAnswers.stream().findFirst().get();
        assertThat(actualSubmittedAnswer.getQuizQuestion()).isEqualTo(saQuestion);
        assertThat(actualSubmittedAnswer.getSubmittedTexts().stream().findFirst().isPresent()).isTrue();

        var actualSubmittedAnswerText = (ShortAnswerSubmittedText) actualSubmittedAnswer.getSubmittedTexts().stream().findFirst().get();
        assertThat(actualSubmittedAnswerText.getText()).isEqualTo("test");
        assertThat(actualSubmittedAnswerText.isIsCorrect()).isFalse();
    }

    private static Stream<Arguments> getGetParticipationsubmittedNotEndedQuizParameters() {
        return Stream.of(Arguments.of(QuizMode.SYNCHRONIZED, true), Arguments.of(QuizMode.SYNCHRONIZED, false), Arguments.of(QuizMode.BATCHED, true),
                Arguments.of(QuizMode.BATCHED, false), Arguments.of(QuizMode.INDIVIDUAL, true), Arguments.of(QuizMode.INDIVIDUAL, false));
    }
}
