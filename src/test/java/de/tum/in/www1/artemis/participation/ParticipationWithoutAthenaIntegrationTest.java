package de.tum.in.www1.artemis.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.exam.ExamFactory;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseTestService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggleService;
import de.tum.in.www1.artemis.service.scheduled.cache.quiz.QuizScheduleService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.LocalRepository;

class ParticipationWithoutAthenaIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "participationintegration";

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private StudentParticipationRepository participationRepo;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    protected QuizScheduleService quizScheduleService;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamRepository examRepository;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    private ProgrammingExercise programmingExercise;

    private Course course;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 4, 1, 1, 1);

        // Add users that are not in the course/exercise
        userUtilService.createAndSaveUser(TEST_PREFIX + "student3");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor2");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor2");

        course = courseUtilService.addCourseWithModelingAndTextExercise();

        programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), course);
        programmingExercise = exerciseRepo.save(programmingExercise);
        course.addExercises(programmingExercise);
        course = courseRepo.save(course);

        doReturn(defaultBranch).when(versionControlService).getDefaultBranchOfRepository(any());
        doReturn("Success").when(continuousIntegrationService).copyBuildPlan(any(), any(), any(), any(), any(), anyBoolean());
        doNothing().when(continuousIntegrationService).configureBuildPlan(any(), any());

        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
    }

    @AfterEach
    void tearDown() throws Exception {
        featureToggleService.enableFeature(Feature.ProgrammingExercises);
        programmingExerciseTestService.tearDown();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void requestFeedbackSuccess_withoutAthena() throws Exception {

        var participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        var localRepo = new LocalRepository(defaultBranch);
        localRepo.configureRepos("testLocalRepo", "testOriginRepo");

        participation.setRepositoryUri(ParticipationFactory.getMockFileRepositoryUri(localRepo).getURI().toString());
        participationRepo.save(participation);

        gitService.getDefaultLocalPathOfRepo(participation.getVcsRepositoryUri());

        var result = ParticipationFactory.generateResult(true, 100).participation(participation);
        result.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(result);

        doNothing().when(programmingExerciseParticipationService).lockStudentRepositoryAndParticipation(programmingExercise, participation);

        var response = request.putWithResponseBody("/api/exercises/" + programmingExercise.getId() + "/request-feedback", null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.OK);

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getIndividualDueDate()).isNotNull().isBefore(ZonedDateTime.now());

        verify(programmingExerciseParticipationService).lockStudentRepositoryAndParticipation(programmingExercise, participation);
        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void whenRequestFeedbackForExam_thenFail_withoutAthena() throws Exception {

        Exam examWithExerciseGroups = ExamFactory.generateExamWithExerciseGroup(course, false);
        examRepository.save(examWithExerciseGroups);
        var exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().get(0);
        programmingExercise = exerciseRepo.save(ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup1));
        course.addExercises(programmingExercise);
        course = courseRepo.save(course);

        var participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        var localRepo = new LocalRepository(defaultBranch);
        localRepo.configureRepos("testLocalRepo", "testOriginRepo");

        participation.setRepositoryUri(ParticipationFactory.getMockFileRepositoryUri(localRepo).getURI().toString());
        participationRepo.save(participation);

        gitService.getDefaultLocalPathOfRepo(participation.getVcsRepositoryUri());

        var result = ParticipationFactory.generateResult(true, 100).participation(participation);
        result.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(result);

        request.putAndExpectError("/api/exercises/" + programmingExercise.getId() + "/request-feedback", null, HttpStatus.BAD_REQUEST, "preconditions not met");

        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void whenFeedbackRequestedAndDeadlinePassed_thenFail_withoutAthena() throws Exception {

        programmingExercise.setDueDate(ZonedDateTime.now().minusDays(100));
        programmingExercise = exerciseRepo.save(programmingExercise);

        var participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        var localRepo = new LocalRepository(defaultBranch);
        localRepo.configureRepos("testLocalRepo", "testOriginRepo");

        participation.setRepositoryUri(ParticipationFactory.getMockFileRepositoryUri(localRepo).getURI().toString());
        participationRepo.save(participation);

        gitService.getDefaultLocalPathOfRepo(participation.getVcsRepositoryUri());

        var result = ParticipationFactory.generateResult(true, 100).participation(participation);
        result.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(result);

        request.putAndExpectError("/api/exercises/" + programmingExercise.getId() + "/request-feedback", null, HttpStatus.BAD_REQUEST, "preconditions not met");

        localRepo.resetLocalRepo();
    }
}
