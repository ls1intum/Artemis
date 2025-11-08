package de.tum.cit.aet.artemis.exercise.participation;

import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_TEST;
import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_TEXT_TEST;
import static de.tum.cit.aet.artemis.core.util.TestResourceUtils.HalfSecond;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.service.GradingScaleService;
import de.tum.cit.aet.artemis.assessment.util.GradingScaleUtilService;
import de.tum.cit.aet.artemis.athena.AbstractAthenaTest;
import de.tum.cit.aet.artemis.atlas.profile.util.LearnerProfileUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepositoryUriUtil;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseTestService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizPointStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedText;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchJoinDTO;
import de.tum.cit.aet.artemis.quiz.service.QuizBatchService;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ParticipationIntegrationTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "participationintegration";

    @Autowired
    private StudentParticipationTestRepository participationRepo;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private ParticipationDeletionService participationDeletionService;

    @Autowired
    private QuizBatchService quizBatchService;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    private GradingScaleService gradingScaleService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private GradingScaleUtilService gradingScaleUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamTestRepository examTestRepository;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private LearnerProfileUtilService learnerProfileUtilService;

    @Captor
    private ArgumentCaptor<Result> resultCaptor;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    private Course course;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTestData() throws Exception {
        super.initTestCase();
        userUtilService.addUsers(TEST_PREFIX, 4, 1, 1, 1);
        learnerProfileUtilService.createLearnerProfilesForUsers(TEST_PREFIX);

        // Add users that are not in the course/exercise
        userUtilService.createAndSaveUser(TEST_PREFIX + "student3");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor2");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor2");

        course = courseUtilService.addCourseWithModelingAndTextExercise();
        for (Exercise exercise : course.getExercises()) {
            if (exercise instanceof ModelingExercise) {
                modelingExercise = (ModelingExercise) exercise;
            }
            if (exercise instanceof TextExercise) {
                textExercise = (TextExercise) exercise;
            }
        }
        modelingExercise.setTitle("UML Class Diagram");
        exerciseRepository.save(modelingExercise);

        programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), course);
        programmingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig()));
        programmingExercise = exerciseRepository.save(programmingExercise);
        course.addExercises(programmingExercise);
        course = courseRepository.save(course);

        doReturn("Success").when(continuousIntegrationService).copyBuildPlan(any(), any(), any(), any(), any(), anyBoolean());

        doNothing().when(continuousIntegrationService).configureBuildPlan(any());

        programmingExerciseTestService.setup(this, versionControlService);
    }

    @AfterEach
    void tearDown() throws Exception {
        Mockito.reset(programmingMessagingService);

        featureToggleService.enableFeature(Feature.ProgrammingExercises);
        programmingExerciseTestService.tearDown();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInModelingExercise() throws Exception {
        URI location = request.post("/api/exercise/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.CREATED);

        StudentParticipation participation = request.get(location.getPath(), HttpStatus.OK, StudentParticipation.class);
        assertThat(participation.getExercise()).as("participated in correct exercise").isEqualTo(modelingExercise);
        assertThat(participation.getStudent()).as("Student got set").isNotNull();
        assertThat(participation.getParticipantIdentifier()).as("Correct student got set").isEqualTo(TEST_PREFIX + "student1");
        Participation storedParticipation = participationRepo
                .findWithEagerSubmissionsByExerciseIdAndStudentLoginAndTestRun(modelingExercise.getId(), TEST_PREFIX + "student1", false).orElseThrow();
        assertThat(storedParticipation.getSubmissions()).as("submission was initialized").hasSize(1);
        assertThat(storedParticipation.getSubmissions().iterator().next().getClass()).as("submission is of type modeling submission").isEqualTo(ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void participateInTextExercise() throws Exception {
        URI location = request.post("/api/exercise/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);

        StudentParticipation participation = request.get(location.getPath(), HttpStatus.OK, StudentParticipation.class);
        assertThat(participation.getExercise()).as("participated in correct exercise").isEqualTo(textExercise);
        assertThat(participation.getStudent()).as("Student got set").isNotNull();
        assertThat(participation.getParticipantIdentifier()).as("Correct student got set").isEqualTo(TEST_PREFIX + "student2");
        Participation storedParticipation = participationRepo.findWithEagerSubmissionsByExerciseIdAndStudentLoginAndTestRun(textExercise.getId(), TEST_PREFIX + "student2", false)
                .orElseThrow();
        assertThat(storedParticipation.getSubmissions()).as("submission was initialized").hasSize(1);
        assertThat(storedParticipation.getSubmissions().iterator().next().getClass()).as("submission is of type text submission").isEqualTo(TextSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateTwiceInModelingExercise_sameParticipation() throws Exception {
        var participation1 = request.post("/api/exercise/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.CREATED);
        var participation2 = request.post("/api/exercise/exercises/" + modelingExercise.getId() + "/participations", null, HttpStatus.CREATED);
        assertThat(participation1).isEqualTo(participation2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateTwiceInTextExercise_sameParticipation() throws Exception {
        var participation1 = request.post("/api/exercise/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);
        var participation2 = request.post("/api/exercise/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);
        assertThat(participation1).isEqualTo(participation2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void participateInTextExercise_releaseDateNotReached() throws Exception {
        textExercise.setReleaseDate(ZonedDateTime.now().plusHours(2));
        exerciseRepository.save(textExercise);
        request.post("/api/exercise/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void participateInTextExercise_noReleaseDateStartDateNotReached() throws Exception {
        textExercise.setReleaseDate(null);
        textExercise.setStartDate(ZonedDateTime.now().plusHours(2));
        exerciseRepository.save(textExercise);
        request.post("/api/exercise/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void participateInTextExercise_releaseDateReachedStartDateNotReached() throws Exception {
        textExercise.setReleaseDate(ZonedDateTime.now().minusMinutes(1));
        textExercise.setStartDate(ZonedDateTime.now().plusHours(2));
        exerciseRepository.save(textExercise);
        request.post("/api/exercise/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void participateInTextExercise_releaseDateReachedStartDateNotReachedAsTutor() throws Exception {
        textExercise.setReleaseDate(ZonedDateTime.now().minusMinutes(1));
        textExercise.setStartDate(ZonedDateTime.now().plusHours(2));
        exerciseRepository.save(textExercise);
        request.post("/api/exercise/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInTextExercise_releaseDateReachedStartDateReached() throws Exception {
        textExercise.setReleaseDate(ZonedDateTime.now().minusMinutes(2));
        textExercise.setStartDate(ZonedDateTime.now().minusMinutes(1));
        exerciseRepository.save(textExercise);
        request.post("/api/exercise/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3")
    void participateInTextExercise_notStudentInCourse() throws Exception {
        request.post("/api/exercise/exercises/" + textExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInTextExerciseAsStudentBeforeDueDatePassed() throws Exception {
        textExercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exerciseRepository.save(textExercise);

        StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/participations", null, StudentParticipation.class,
                HttpStatus.CREATED);

        assertThat(participation).isNotNull();
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Set<User> participationUsers = participation.getStudents();
        assertThat(participationUsers).contains(user);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInTextExerciseAsStudentDueDatePassed() throws Exception {
        textExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        exerciseRepository.save(textExercise);

        request.postWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/participations", null, StudentParticipation.class, HttpStatus.FORBIDDEN);
    }

    /**
     * Students can start participations during the working time of the exam.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInTextExerciseAsStudentBeforeNormalDueDatePassed() throws Exception {
        TextExercise examTextExercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        examTextExercise.getExam().setStartDate(ZonedDateTime.now().minusHours(2));
        examTextExercise.getExam().setEndDate(ZonedDateTime.now().plusHours(1));
        examTestRepository.save(examTextExercise.getExam());

        StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + examTextExercise.getId() + "/participations", null,
                StudentParticipation.class, HttpStatus.CREATED);
        assertThat(participation).isNotNull();
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Set<User> participationUsers = participation.getStudents();
        assertThat(participationUsers).contains(user);
    }

    /**
     * Students cannot start participations after the working time of the exam.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInTextExerciseAsStudentAfterNormalDueDatePassed() throws Exception {
        TextExercise examTextExercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        examTextExercise.getExam().setStartDate(ZonedDateTime.now().minusHours(2));
        examTextExercise.getExam().setEndDate(ZonedDateTime.now().minusHours(1));
        examTestRepository.save(examTextExercise.getExam());

        request.postWithResponseBody("/api/exercise/exercises/" + examTextExercise.getId() + "/participations", null, StudentParticipation.class, HttpStatus.FORBIDDEN);
    }

    /**
     * Students cannot start participations before the working time of the exam began.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInTextExerciseAsStudentAfterBeforeStartDatePassed() throws Exception {
        TextExercise examTextExercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        examTextExercise.getExam().setStartDate(ZonedDateTime.now().plusHours(1));
        examTextExercise.getExam().setEndDate(ZonedDateTime.now().plusHours(2));
        examTestRepository.save(examTextExercise.getExam());

        request.postWithResponseBody("/api/exercise/exercises/" + examTextExercise.getId() + "/participations", null, StudentParticipation.class, HttpStatus.FORBIDDEN);
    }

    /**
     * Students might have individual working time on exams. If the normal working time is over,
     * but the individual working time is still ongoing, the student should be able to start a participation.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInTextExerciseAsStudentAfterNormalDueDatePassedWithOngoingIndividualWorkingTime() throws Exception {
        String studentLogin = TEST_PREFIX + "student1";
        TextExercise examTextExercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        examTextExercise.getExam().setStartDate(ZonedDateTime.now().minusHours(2));
        examTextExercise.getExam().setEndDate(ZonedDateTime.now().minusHours(1));
        examTestRepository.save(examTextExercise.getExam());

        StudentExam studentExam = examUtilService.addStudentExamWithUser(examTextExercise.getExam(), studentLogin);
        int threeHours = 3 * 60 * 60;
        studentExam.setWorkingTime(threeHours);
        studentExamRepository.save(studentExam);

        StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + examTextExercise.getId() + "/participations", null,
                StudentParticipation.class, HttpStatus.CREATED);

        assertThat(participation).isNotNull();
        User user = userUtilService.getUserByLogin(studentLogin);
        Set<User> participationUsers = participation.getStudents();
        assertThat(participationUsers).contains(user);
    }

    /**
     * If the individual working time has expired, the student should NOT be able to start a participation.
     *
     * @see #participateInTextExerciseAsStudentAfterNormalDueDatePassedWithOngoingIndividualWorkingTime
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInTextExerciseAsStudentAfterNormalDueDatePassedWithExpiredIndividualWorkingTime() throws Exception {
        String studentLogin = TEST_PREFIX + "student1";
        TextExercise examTextExercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        examTextExercise.getExam().setStartDate(ZonedDateTime.now().minusHours(3));
        examTextExercise.getExam().setEndDate(ZonedDateTime.now().minusHours(1));
        examTestRepository.save(examTextExercise.getExam());

        StudentExam studentExam = examUtilService.addStudentExamWithUser(examTextExercise.getExam(), studentLogin);
        int twoHours = 2 * 60 * 60;
        studentExam.setWorkingTime(twoHours);
        studentExamRepository.save(studentExam);

        request.postWithResponseBody("/api/exercise/exercises/" + examTextExercise.getId() + "/participations", null, StudentParticipation.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInProgrammingExercise_featureDisabled() throws Exception {
        featureToggleService.disableFeature(Feature.ProgrammingExercises);
        request.post("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);

        // Reset
        featureToggleService.enableFeature(Feature.ProgrammingExercises);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void practiceProgrammingExercise_featureDisabled() throws Exception {
        featureToggleService.disableFeature(Feature.ProgrammingExercises);
        request.post("/api/exercise/exercises/" + programmingExercise.getId() + "/participations/practice", null, HttpStatus.FORBIDDEN);

        // Reset
        featureToggleService.enableFeature(Feature.ProgrammingExercises);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInProgrammingExercise_dueDatePassed() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        exerciseRepository.save(programmingExercise);
        request.post("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void participateInProgrammingExerciseAsStudentDueDatePassed() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(2));

        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        prepareMocksForProgrammingExercise();
        mockConnectorRequestsForStartParticipation(programmingExercise, TEST_PREFIX + "student1", Set.of(user), true);

        request.postWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null, StudentParticipation.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void participateInProgrammingExerciseAsEditorDueDatePassed() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(2));

        User user = userUtilService.getUserByLogin(TEST_PREFIX + "editor1");
        prepareMocksForProgrammingExercise();
        mockConnectorRequestsForStartParticipation(programmingExercise, TEST_PREFIX + "editor1", Set.of(user), true);

        request.postWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null, StudentParticipation.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void practiceProgrammingExercise_beforeDatePassed() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exerciseRepository.save(programmingExercise);
        request.post("/api/exercise/exercises/" + programmingExercise.getId() + "/participations/practice", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInProgrammingTeamExercise_withoutAssignedTeam() throws Exception {
        programmingExercise.setMode(ExerciseMode.TEAM);
        exerciseRepository.save(programmingExercise);
        request.post("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void practiceProgrammingExercise_successful() throws Exception {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
        exerciseRepository.save(programmingExercise);

        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        prepareMocksForProgrammingExercise();

        mockConnectorRequestsForStartPractice(programmingExercise, TEST_PREFIX + "student1");

        StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/participations/practice", null,
                StudentParticipation.class, HttpStatus.CREATED);
        assertThat(participation).isNotNull();
        assertThat(participation.isPracticeMode()).isTrue();
        assertThat(participation.getStudent()).contains(user);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void participateInProgrammingExercise_successful() throws Exception {

        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        prepareMocksForProgrammingExercise();
        mockConnectorRequestsForStartParticipation(programmingExercise, TEST_PREFIX + "student1", Set.of(user), true);

        StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null,
                StudentParticipation.class, HttpStatus.CREATED);
        assertThat(participation).isNotNull();
        assertThat(participation.isPracticeMode()).isFalse();
        assertThat(participation.getStudent()).contains(user);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void practiceProgrammingTeamExercise_Forbidden() throws Exception {
        programmingExercise.setMode(ExerciseMode.TEAM);
        exerciseRepository.save(programmingExercise);
        request.post("/api/exercise/exercises/" + programmingExercise.getId() + "/participations/practice", null, HttpStatus.BAD_REQUEST);
    }

    private void prepareMocksForProgrammingExercise() throws Exception {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        jenkinsRequestMockProvider.enableMockingOfRequests();
        programmingExerciseTestService.setupRepositoryMocks(programmingExercise);
        var repo = new LocalRepository(defaultBranch);
        repo.configureRepos(localVCBasePath, "studentRepo", "studentOriginRepo");
        repo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteParticipation() throws Exception {
        Submission submissionWithResult = participationUtilService.addSubmission(modelingExercise, new ModelingSubmission(), TEST_PREFIX + "student1");
        participationUtilService.addSubmission((StudentParticipation) submissionWithResult.getParticipation(), new ModelingSubmission());
        Long participationId = submissionWithResult.getParticipation().getId();
        participationUtilService.addResultToSubmission(submissionWithResult, null);

        // Participation should now exist.
        assertThat(participationRepo.existsById(participationId)).isTrue();
        // There should be a submission and result assigned to the participation.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).hasSize(2);
        assertThat(resultRepository.findBySubmissionParticipationIdOrderByCompletionDateDesc(participationId)).hasSize(1);

        request.delete("/api/exercise/participations/" + participationId, HttpStatus.OK);
        Optional<StudentParticipation> participation = participationRepo.findById(participationId);
        // Participation should now be gone.
        assertThat(participation).isEmpty();
        // Make sure that also the submission and result were deleted.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).isEmpty();
        assertThat(resultRepository.findBySubmissionParticipationIdOrderByCompletionDateDesc(participationId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteSubmissionWithoutResult() throws Exception {
        Submission submissionWithoutResult = participationUtilService.addSubmission(modelingExercise, new ModelingSubmission(), TEST_PREFIX + "student1");
        participationUtilService.addSubmission((StudentParticipation) submissionWithoutResult.getParticipation(), submissionWithoutResult);
        Long participationId = submissionWithoutResult.getParticipation().getId();

        // Participation should now exist.
        assertThat(participationRepo.existsById(participationId)).isTrue();

        // There should be a submission and no result assigned to the participation.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).hasSize(1);
        assertThat(resultRepository.findBySubmissionParticipationIdOrderByCompletionDateDesc(participationId)).isEmpty();

        request.delete("/api/exercise/participations/" + participationId, HttpStatus.OK);
        Optional<StudentParticipation> participation = participationRepo.findById(participationId);
        // Participation should now be gone.
        assertThat(participation).isEmpty();
        // Make sure that the submission is deleted.
        assertThat(submissionRepository.findAllByParticipationId(participationId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteResultWithoutSubmission() throws Exception {
        StudentParticipation studentParticipation = participationUtilService.createAndSaveParticipationForExercise(modelingExercise, TEST_PREFIX + "student1");
        participationUtilService.addSubmission(studentParticipation, new ModelingSubmission());
        participationUtilService.addResultToSubmission(null, null, studentParticipation.findLatestSubmission().orElseThrow());
        Long participationId = studentParticipation.getId();

        // Participation should now exist.
        assertThat(participationRepo.existsById(participationId)).isTrue();

        assertThat(submissionRepository.findAllByParticipationId(participationId)).hasSize(1);
        assertThat(resultRepository.findBySubmissionParticipationIdOrderByCompletionDateDesc(participationId)).hasSize(1);

        request.delete("/api/exercise/participations/" + participationId, HttpStatus.OK);
        Optional<StudentParticipation> participation = participationRepo.findById(participationId);
        // Participation should now be gone.
        assertThat(participation).isEmpty();
        // Make sure that the result is deleted.
        assertThat(resultRepository.findBySubmissionParticipationIdOrderByCompletionDateDesc(participationId)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteParticipation_notFound() throws Exception {
        request.delete("/api/exercise/participations/" + -1, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void requestFeedbackExerciseNotPossibleIfOnlyAutomaticFeedbacks() throws Exception {
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        exerciseRepository.save(programmingExercise);

        var participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participationRepo.save(participation);

        request.putWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/request-feedback", null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void requestFeedbackAlreadySent() throws Exception {
        var participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participation.setIndividualDueDate(ZonedDateTime.now().minusMinutes(20));
        participationRepo.save(participation);

        request.putWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/request-feedback", null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void requestProgrammingFeedbackIfARequestAlreadySent_withAthenaSuccess() throws Exception {

        var course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        course.setRestrictedAthenaModulesAccess(true);
        this.courseRepository.save(course);

        this.programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        this.exerciseRepository.save(programmingExercise);

        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("programming");

        var participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        var localRepo = new LocalRepository(defaultBranch);
        localRepo.configureRepos(localVCBasePath, "testLocalRepo", "testOriginRepo");

        var localVcsRepositoryUri = new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.remoteBareGitRepoFile, localVCBasePath));
        participation.setRepositoryUri(localVcsRepositoryUri);
        participationRepo.save(participation);

        gitService.getDefaultLocalPathOfRepo(participation.getVcsRepositoryUri());

        Result result1 = participationUtilService.createSubmissionAndResult(participation, 100, false);
        Result result2 = participationUtilService.addResultToSubmission(participation, result1.getSubmission());
        result2.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
        result2.setSuccessful(null);
        result2.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(result2);

        request.putWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/request-feedback", null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.OK);

        verify(programmingMessagingService, timeout(2000).times(2)).notifyUserAboutNewResult(resultCaptor.capture(), any());

        Result invokedResult = resultCaptor.getAllValues().getFirst();
        assertThat(invokedResult).isNotNull();
        assertThat(invokedResult.getId()).isNotNull();
        assertThat(invokedResult.isSuccessful()).isTrue();
        assertThat(invokedResult.isAthenaBased()).isTrue();
        assertThat(invokedResult.getFeedbacks()).hasSize(1);

        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void requestProgrammingFeedbackSuccess_withAthenaSuccess() throws Exception {

        var course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        course.setRestrictedAthenaModulesAccess(true);
        this.courseRepository.save(course);

        this.programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        this.exerciseRepository.save(programmingExercise);

        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("programming");

        var participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        var localRepo = new LocalRepository(defaultBranch);
        localRepo.configureRepos(localVCBasePath, "testLocalRepo", "testOriginRepo");

        var localVcsRepositoryUri = new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.remoteBareGitRepoFile, localVCBasePath));
        participation.setRepositoryUri(localVcsRepositoryUri);
        participationRepo.save(participation);

        gitService.getDefaultLocalPathOfRepo(participation.getVcsRepositoryUri());

        Result result1 = participationUtilService.createSubmissionAndResult(participation, 100, false);
        Result result2 = participationUtilService.addResultToSubmission(participation, result1.getSubmission());
        result2.setAssessmentType(AssessmentType.AUTOMATIC);
        result2.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(result2);

        request.putWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/request-feedback", null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.OK);

        verify(programmingMessagingService, timeout(2000).times(2)).notifyUserAboutNewResult(resultCaptor.capture(), any());

        Result invokedResult = resultCaptor.getAllValues().getFirst();
        assertThat(invokedResult).isNotNull();
        assertThat(invokedResult.getId()).isNotNull();
        assertThat(invokedResult.isSuccessful()).isTrue();
        assertThat(invokedResult.isAthenaBased()).isTrue();
        assertThat(invokedResult.getFeedbacks()).hasSize(1);

        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void requestTextFeedbackSuccess_withAthenaSuccess() throws Exception {

        var textCourse = textExercise.getCourseViaExerciseGroupOrCourseMember();
        textCourse.setRestrictedAthenaModulesAccess(true);
        this.courseRepository.save(textCourse);

        this.textExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_TEST);
        this.exerciseRepository.save(textExercise);

        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("text");

        var textParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.INACTIVE, textExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        participationRepo.save(textParticipation);

        Result resultText1 = participationUtilService.createSubmissionAndResult(textParticipation, 100, true);
        TextSubmission submission = (TextSubmission) resultText1.getSubmission();
        submission.setText("some random text");
        submission.setSubmitted(true);
        Result resultText2 = participationUtilService.addResultToSubmission(textParticipation, submission);
        resultText2.setSuccessful(true);
        resultText2.setAssessmentType(AssessmentType.MANUAL);
        resultText2.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(resultText2);

        request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/request-feedback", null, StudentParticipation.class, HttpStatus.OK);

        verify(resultWebsocketService, timeout(2000).times(2)).broadcastNewResult(any(), resultCaptor.capture());

        Result invokedTextResult = resultCaptor.getAllValues().get(1);
        assertThat(invokedTextResult).isNotNull();
        assertThat(invokedTextResult.getId()).isNotNull();
        assertThat(invokedTextResult.isSuccessful()).isTrue();
        assertThat(invokedTextResult.isAthenaBased()).isTrue();
        assertThat(invokedTextResult.getFeedbacks()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void requestModelingFeedbackSuccess_withAthenaSuccess() throws Exception {

        var modelingCourse = modelingExercise.getCourseViaExerciseGroupOrCourseMember();
        modelingCourse.setRestrictedAthenaModulesAccess(true);
        this.courseRepository.save(modelingCourse);

        this.modelingExercise.setFeedbackSuggestionModule("module_modeling_test");
        this.exerciseRepository.save(modelingExercise);

        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect("modeling");

        var modelingParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.INACTIVE, modelingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        participationRepo.save(modelingParticipation);

        Result resultModeling1 = participationUtilService.createSubmissionAndResult(modelingParticipation, 100, true);
        ModelingSubmission submission = (ModelingSubmission) resultModeling1.getSubmission();
        submission.setModel("some random model");
        submission.setSubmitted(true);
        Result resultModeling2 = participationUtilService.addResultToSubmission(modelingParticipation, resultModeling1.getSubmission());
        resultModeling2.setAssessmentType(AssessmentType.MANUAL);
        resultModeling2.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(resultModeling2);

        request.putWithResponseBody("/api/exercise/exercises/" + modelingExercise.getId() + "/request-feedback", null, StudentParticipation.class, HttpStatus.OK);

        verify(resultWebsocketService, timeout(2000).times(2)).broadcastNewResult(any(), resultCaptor.capture());

        Result invokedModelingResult = resultCaptor.getAllValues().get(1);
        assertThat(invokedModelingResult).isNotNull();
        assertThat(invokedModelingResult.getId()).isNotNull();
        assertThat(invokedModelingResult.isAthenaBased()).isTrue();
        assertThat(invokedModelingResult.getFeedbacks()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void requestProgrammingFeedbackSuccess_withAthenaFailure() throws Exception {

        var course = programmingExercise.getCourseViaExerciseGroupOrCourseMember();
        course.setRestrictedAthenaModulesAccess(true);
        this.courseRepository.save(course);

        this.programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        this.exerciseRepository.save(programmingExercise);
        this.athenaRequestMockProvider.mockGetFeedbackSuggestionsWithFailure("programming");

        var participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        var localRepo = new LocalRepository(defaultBranch);
        localRepo.configureRepos(localVCBasePath, "testLocalRepo", "testOriginRepo");

        var localVcsRepositoryUri = new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.remoteBareGitRepoFile, localVCBasePath));
        participation.setRepositoryUri(localVcsRepositoryUri);
        participationRepo.save(participation);

        gitService.getDefaultLocalPathOfRepo(participation.getVcsRepositoryUri());

        Result result1 = participationUtilService.createSubmissionAndResult(participation, 100, false);
        Result result2 = participationUtilService.addResultToSubmission(participation, result1.getSubmission());
        result2.setAssessmentType(AssessmentType.AUTOMATIC);
        result2.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(result2);

        request.putWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/request-feedback", null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.OK);

        verify(programmingMessagingService, timeout(2000).times(2)).notifyUserAboutNewResult(resultCaptor.capture(), any());

        Result invokedResult = resultCaptor.getAllValues().getFirst();
        assertThat(invokedResult).isNotNull();
        assertThat(invokedResult.getId()).isNotNull();
        assertThat(invokedResult.isSuccessful()).isFalse();
        assertThat(invokedResult.isAthenaBased()).isTrue();
        assertThat(invokedResult.getFeedbacks()).hasSize(0);

        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void requestTextFeedbackSuccess_withAthenaFailure() throws Exception {

        var textCourse = textExercise.getCourseViaExerciseGroupOrCourseMember();
        textCourse.setRestrictedAthenaModulesAccess(true);
        this.courseRepository.save(textCourse);

        this.textExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_TEST);

        this.exerciseRepository.save(textExercise);

        athenaRequestMockProvider.mockGetFeedbackSuggestionsWithFailure("text");

        var textParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.INACTIVE, textExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        participationRepo.save(textParticipation);

        Result resultText1 = participationUtilService.createSubmissionAndResult(textParticipation, 100, false);
        TextSubmission submission = (TextSubmission) resultText1.getSubmission();
        submission.setText("some random text");
        submission.setSubmitted(true);
        Result resultText2 = participationUtilService.addResultToSubmission(textParticipation, resultText1.getSubmission());
        resultText2.setAssessmentType(AssessmentType.MANUAL);
        resultText2.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(resultText2);

        request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/request-feedback", null, StudentParticipation.class, HttpStatus.OK);

        verify(resultWebsocketService, timeout(2000).times(2)).broadcastNewResult(any(), resultCaptor.capture());

        Result invokedTextResult = resultCaptor.getAllValues().getFirst();
        assertThat(invokedTextResult).isNotNull();
        assertThat(invokedTextResult.isAthenaBased()).isTrue();
        assertThat(invokedTextResult.getFeedbacks()).hasSize(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void requestModelingFeedbackSuccess_withAthenaFailure() throws Exception {

        var modelingCourse = modelingExercise.getCourseViaExerciseGroupOrCourseMember();
        modelingCourse.setRestrictedAthenaModulesAccess(true);
        this.courseRepository.save(modelingCourse);

        this.modelingExercise.setFeedbackSuggestionModule("module_modeling_test");
        this.exerciseRepository.save(modelingExercise);

        athenaRequestMockProvider.mockGetFeedbackSuggestionsWithFailure("modeling");

        var modelingParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.INACTIVE, modelingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        participationRepo.save(modelingParticipation);

        Result resultModeling1 = participationUtilService.createSubmissionAndResult(modelingParticipation, 100, false);
        ModelingSubmission submission = (ModelingSubmission) resultModeling1.getSubmission();
        submission.setModel("some random model");
        submission.setSubmitted(true);
        Result resultModeling2 = participationUtilService.addResultToSubmission(modelingParticipation, resultModeling1.getSubmission());
        resultModeling2.setAssessmentType(AssessmentType.MANUAL);
        resultModeling2.setCompletionDate(ZonedDateTime.now());
        resultRepository.save(resultModeling2);

        request.putWithResponseBody("/api/exercise/exercises/" + modelingExercise.getId() + "/request-feedback", null, StudentParticipation.class, HttpStatus.OK);

        verify(resultWebsocketService, timeout(2000).times(2)).broadcastNewResult(any(), resultCaptor.capture());

        Result invokedModelingResult = resultCaptor.getAllValues().getFirst();
        assertThat(invokedModelingResult).isNotNull();
        assertThat(invokedModelingResult.isAthenaBased()).isTrue();
        assertThat(invokedModelingResult.getFeedbacks()).hasSize(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resumeProgrammingExerciseParticipation() throws Exception {
        var participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        var localRepo = new LocalRepository(defaultBranch);
        localRepo.configureRepos(localVCBasePath, "testLocalRepo", "testOriginRepo");
        var localVcsRepositoryUri = new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.remoteBareGitRepoFile, localVCBasePath));
        participation.setRepositoryUri(localVcsRepositoryUri);
        participationRepo.save(participation);
        gitService.getDefaultLocalPathOfRepo(participation.getVcsRepositoryUri());
        var updatedParticipation = request.putWithResponseBody(
                "/api/exercise/exercises/" + programmingExercise.getId() + "/resume-programming-participation/" + participation.getId(), null,
                ProgrammingExerciseStudentParticipation.class, HttpStatus.OK);
        assertThat(updatedParticipation.getInitializationState()).isEqualTo(InitializationState.INITIALIZED);
        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resumeProgrammingExerciseParticipation_wrongExerciseId() throws Exception {
        var participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INITIALIZED, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participationRepo.save(participation);
        request.putWithResponseBody("/api/exercise/exercises/10000/resume-programming-participation/" + participation.getId(), null, ProgrammingExerciseStudentParticipation.class,
                HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void resumeProgrammingExerciseParticipation_forbidden() throws Exception {
        var exercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1), course);
        exercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(exercise.getBuildConfig()));
        exercise = exerciseRepository.save(exercise);
        var participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, exercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participationRepo.save(participation);
        request.putWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/resume-programming-participation/" + participation.getId(), null,
                ProgrammingExerciseStudentParticipation.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllParticipationsForExercise() throws Exception {
        participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student2");
        StudentParticipation testParticipation = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student3");
        testParticipation.setPracticeMode(true);
        participationRepo.save(testParticipation);
        var participations = request.getList("/api/exercise/exercises/" + textExercise.getId() + "/participations", HttpStatus.OK, StudentParticipation.class);
        assertThat(participations).as("Exactly 3 participations are returned").hasSize(3).as("Only participation that has student are returned")
                .allMatch(participation -> participation.getStudent().isPresent()).as("No submissions should exist for participations")
                .allMatch(participation -> participation.getSubmissionCount() == null || participation.getSubmissionCount() == 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllParticipationsForExercise_withLatestSubmissionResult() throws Exception {
        List<User> students = IntStream.range(1, 5).mapToObj(i -> userUtilService.getUserByLogin(TEST_PREFIX + "student" + i)).toList();
        participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");

        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student2");
        Result result1 = participationUtilService.createSubmissionAndResult(participation, 42, true);
        Result result2 = participationUtilService.addResultToSubmission(participation, result1.getSubmission());
        result2.setAssessmentType(AssessmentType.MANUAL);
        resultRepository.save(result2);
        Result result3 = participationUtilService.addResultToSubmission(participation, result1.getSubmission());

        Submission onlySubmission = textExerciseUtilService.createSubmissionForTextExercise(textExercise, students.get(2), "asdf");

        StudentParticipation testParticipation = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student4");
        testParticipation.setPracticeMode(true);
        participationRepo.save(testParticipation);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("withLatestResults", "true");
        var participations = request.getList("/api/exercise/exercises/" + textExercise.getId() + "/participations", HttpStatus.OK, StudentParticipation.class, params);
        assertThat(participations).as("Exactly 4 participations are returned").hasSize(4).as("Only participation that has student are returned")
                .allMatch(p -> p.getStudent().isPresent());
        StudentParticipation receivedOnlyParticipation = participations.stream().filter(p -> p.getParticipant().equals(students.getFirst())).findFirst().orElseThrow();
        StudentParticipation receivedParticipationWithResult = participations.stream().filter(p -> p.getParticipant().equals(students.get(1))).findFirst().orElseThrow();
        StudentParticipation receivedParticipationWithOnlySubmission = participations.stream().filter(p -> p.getParticipant().equals(students.get(2))).findFirst().orElseThrow();
        StudentParticipation receivedTestParticipation = participations.stream().filter(p -> p.getParticipant().equals(students.get(3))).findFirst().orElseThrow();
        assertThat(receivedOnlyParticipation.getSubmissions()).isEmpty();
        assertThat(receivedOnlyParticipation.getSubmissionCount()).isZero();

        assertThat(participationUtilService.getResultsForParticipation(receivedParticipationWithResult)).containsExactlyInAnyOrder(result3);
        assertThat(receivedParticipationWithResult.getSubmissions()).containsExactly(result1.getSubmission());
        assertThat(receivedParticipationWithResult.getSubmissionCount()).isEqualTo(1);

        assertThat(receivedParticipationWithOnlySubmission.getSubmissions().iterator().next().getResults()).isEmpty();
        assertThat(receivedParticipationWithOnlySubmission.getSubmissions()).containsExactlyInAnyOrder(onlySubmission);
        assertThat(receivedParticipationWithOnlySubmission.getSubmissionCount()).isEqualTo(1);

        assertThat(receivedTestParticipation.getSubmissions()).isEmpty();
        assertThat(receivedTestParticipation.getSubmissionCount()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllParticipationsForExercise_withLatestResults_forQuizExercise() throws Exception {
        var quizExercise = QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), QuizMode.INDIVIDUAL, course);
        course.addExercises(quizExercise);
        courseRepository.save(course);
        exerciseRepository.save(quizExercise);

        final var login = TEST_PREFIX + "student1";
        var participation = participationUtilService.createAndSaveParticipationForExercise(quizExercise, login);
        var result1 = participationUtilService.createSubmissionAndResult(participation, 42, true);
        var notGradedResult = participationUtilService.addResultToSubmission(participation, result1.getSubmission());
        notGradedResult.setRated(false);
        resultRepository.save(notGradedResult);

        final var params = new LinkedMultiValueMap<String, String>();
        params.add("withLatestResults", "true");
        var participations = request.getList("/api/exercise/exercises/" + quizExercise.getId() + "/participations", HttpStatus.OK, StudentParticipation.class, params);

        var receivedParticipation = participations.stream().filter(p -> p.getParticipantIdentifier().equals(login)).findFirst().orElseThrow();

        assertThat(participationUtilService.getResultsForParticipation(receivedParticipation)).containsOnly(notGradedResult);
        assertThat(receivedParticipation.getSubmissions()).containsOnly(result1.getSubmission());
        assertThat(receivedParticipation.getSubmissionCount()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllParticipationsForExercise_withLatestResult_multipleAssessments() throws Exception {
        var participation1 = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        var participation2 = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student2");
        var participation3 = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student3");
        var submission1 = participationUtilService.addSubmission(participation1, new TextSubmission());
        var submission2 = participationUtilService.addSubmission(participation2, new TextSubmission());
        var submission3 = participationUtilService.addSubmission(participation3, new TextSubmission());
        participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, null, submission1);
        participationUtilService.addResultToSubmission(AssessmentType.MANUAL, null, submission1);
        participationUtilService.addResultToSubmission(AssessmentType.MANUAL, null, submission2);
        participationUtilService.addResultToSubmission(AssessmentType.MANUAL, null, submission2);
        participationUtilService.addResultToSubmission(AssessmentType.MANUAL, null, submission3);
        participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, null, submission3);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("withLatestResults", "true");
        var participations = request.getList("/api/exercise/exercises/" + textExercise.getId() + "/participations", HttpStatus.OK, StudentParticipation.class, params);
        assertThat(participations).as("Exactly 3 participations are returned").hasSize(3).as("Only participation that has student are returned")
                .allMatch(p -> p.getStudent().isPresent()).as("Each participation should have 1 submission").allMatch(p -> p.getSubmissionCount() == 1);
        var recievedParticipation1 = participations.stream().filter(participation -> participation.getParticipant().equals(participation1.getParticipant())).findAny();
        var recievedParticipation2 = participations.stream().filter(participation -> participation.getParticipant().equals(participation2.getParticipant())).findAny();
        var recievedParticipation3 = participations.stream().filter(participation -> participation.getParticipant().equals(participation3.getParticipant())).findAny();
        assertThat(recievedParticipation1).hasValueSatisfying(participation -> assertThat(participationUtilService.getResultsForParticipation(participation)).hasSize(1));
        assertThat(recievedParticipation2).hasValueSatisfying(participation -> assertThat(participationUtilService.getResultsForParticipation(participation)).hasSize(1));
        assertThat(recievedParticipation3).hasValueSatisfying(participation -> assertThat(participationUtilService.getResultsForParticipation(participation)).hasSize(1));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void getAllParticipationsForExercise_NotTutorInCourse() throws Exception {
        request.getList("/api/exercise/exercises/" + textExercise.getId() + "/participations", HttpStatus.FORBIDDEN, StudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateParticipation() throws Exception {
        var participation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participation.setPresentationScore(1.);
        participation = participationRepo.save(participation);
        participation.setPresentationScore(null);
        var actualParticipation = request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/participations", participation, StudentParticipation.class,
                HttpStatus.OK);
        assertThat(actualParticipation).as("The participation was updated").isNotNull();
        assertThat(actualParticipation.getPresentationScore()).as("Presentation score was set to null").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateParticipation_notStored() throws Exception {
        var participation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/participations", participation, StudentParticipation.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateParticipation_presentationScoreMoreThan0() throws Exception {
        var participation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participation = participationRepo.save(participation);
        participation.setPresentationScore(2.);
        var actualParticipation = request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/participations", participation, StudentParticipation.class,
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

        GradingScale gradingScale = gradingScaleUtilService.generateGradingScale(2, new double[] { 0, 50, 100 }, true, 1, Optional.empty(), course, 2, 20.);
        gradingScaleService.saveGradingScale(gradingScale);

        StudentParticipation participation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participation = participationRepo.save(participation);

        participation.setPresentationScore(input);

        if (isBadRequest) {
            StudentParticipation actualParticipation = request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/participations", participation,
                    StudentParticipation.class, HttpStatus.BAD_REQUEST);
            assertThat(actualParticipation).as("The participation was not updated").isNull();
        }
        else {
            StudentParticipation actualParticipation = request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/participations", participation,
                    StudentParticipation.class, HttpStatus.OK);
            assertThat(actualParticipation).as("The participation was updated").isNotNull();
            assertThat(actualParticipation.getPresentationScore()).as("Presentation score was set to " + input).isEqualTo(input);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateParticipation_exceedingPresentationNumber() throws Exception {
        Course course = textExercise.getCourseViaExerciseGroupOrCourseMember();
        course.setPresentationScore(0);

        GradingScale gradingScale = gradingScaleUtilService.generateGradingScale(2, new double[] { 0, 50, 100 }, true, 1, Optional.empty(), course, 1, 20.);
        gradingScaleService.saveGradingScale(gradingScale);

        StudentParticipation participation1 = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participation1 = participationRepo.save(participation1);
        participationUtilService.createSubmissionAndResult(participation1, 50, true);

        // SHOULD ADD FIRST PRESENTATION GRADE
        participation1.setPresentationScore(100.0);

        var actualParticipation1 = request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/participations", participation1, StudentParticipation.class,
                HttpStatus.OK);
        assertThat(actualParticipation1).as("The participation was updated").isNotNull();
        assertThat(actualParticipation1.getPresentationScore()).as("Presentation score was set to 100").isEqualTo(100.0);

        // SHOULD UPDATE FIRST PRESENTATION GRADE
        participation1.setPresentationScore(80.0);

        actualParticipation1 = request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/participations", participation1, StudentParticipation.class,
                HttpStatus.OK);
        assertThat(actualParticipation1).as("The participation was updated").isNotNull();
        assertThat(actualParticipation1.getPresentationScore()).as("Presentation score was set to 80").isEqualTo(80.0);

        // SHOULD NOT ADD SECOND PRESENTATION GRADE
        StudentParticipation participation2 = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, modelingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participation2 = participationRepo.save(participation2);

        participation2.setPresentationScore(100.0);

        request.putWithResponseBody("/api/exercise/exercises/" + modelingExercise.getId() + "/participations", participation2, StudentParticipation.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void updateParticipation_notTutorInCourse() throws Exception {
        var participation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, textExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participation = participationRepo.save(participation);
        request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/participations", participation, StudentParticipation.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDateExamExercise() throws Exception {
        final FileUploadExercise exercise = fileUploadExerciseUtilService.addCourseExamExerciseGroupWithOneFileUploadExercise(false);
        StudentParticipation participation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participation = participationRepo.save(participation);
        participation.setIndividualDueDate(ZonedDateTime.now().plusDays(3));

        final var participationsToUpdate = new StudentParticipationList(participation);
        request.putAndExpectError(String.format("/api/exercise/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate,
                HttpStatus.BAD_REQUEST, "examexercise");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDateQuizExercise() throws Exception {
        final Course course = quizExerciseUtilService.addCourseWithOneQuizExercise();
        final QuizExercise exercise = (QuizExercise) course.getExercises().stream().findFirst().orElseThrow();
        StudentParticipation participation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participation = participationRepo.save(participation);
        participation.setIndividualDueDate(ZonedDateTime.now().plusDays(3));

        final var participationsToUpdate = new StudentParticipationList(participation);
        request.putAndExpectError(String.format("/api/exercise/exercises/%d/participations/update-individual-due-date", exercise.getId()), participationsToUpdate,
                HttpStatus.BAD_REQUEST, "quizexercise");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDateOk() throws Exception {
        final var course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        var exercise = (FileUploadExercise) course.getExercises().stream().findAny().orElseThrow();
        exercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exercise = exerciseRepository.save(exercise);

        var submission = fileUploadExerciseUtilService.addFileUploadSubmission(exercise, ParticipationFactory.generateFileUploadSubmission(true), TEST_PREFIX + "student1");
        submission.getParticipation().setIndividualDueDate(ZonedDateTime.now().plusDays(1));

        final var participationsToUpdate = new StudentParticipationList((StudentParticipation) submission.getParticipation());
        final var response = request.putWithResponseBodyList(String.format("/api/exercise/exercises/%d/participations/update-individual-due-date", exercise.getId()),
                participationsToUpdate, StudentParticipation.class, HttpStatus.OK);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getIndividualDueDate()).isCloseTo(submission.getParticipation().getIndividualDueDate(), HalfSecond());

        verify(programmingExerciseScheduleService, never()).updateScheduling(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDateProgrammingExercise() throws Exception {
        final var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().orElseThrow();
        exercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exercise = exerciseRepository.save(exercise);

        final var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(20));

        // due date before exercise due date ⇒ should be ignored
        final var participation2 = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student2");
        participation2.setIndividualDueDate(ZonedDateTime.now().plusHours(1));

        final var participationsToUpdate = new StudentParticipationList(participation, participation2);
        final var response = request.putWithResponseBodyList(String.format("/api/exercise/exercises/%d/participations/update-individual-due-date", exercise.getId()),
                participationsToUpdate, StudentParticipation.class, HttpStatus.OK);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getIndividualDueDate()).isCloseTo(participation.getIndividualDueDate(), HalfSecond());

        verify(programmingExerciseScheduleService).updateScheduling(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDateUnchanged() throws Exception {
        final var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().orElseThrow();
        exercise.setDueDate(ZonedDateTime.now().plusHours(2));
        exercise = exerciseRepository.save(exercise);

        final var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        final var participationsToUpdate = new StudentParticipationList(participation);
        final var response = request.putWithResponseBodyList(String.format("/api/exercise/exercises/%d/participations/update-individual-due-date", exercise.getId()),
                participationsToUpdate, StudentParticipation.class, HttpStatus.OK);

        assertThat(response).isEmpty();
        verify(programmingExerciseScheduleService, never()).updateScheduling(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateIndividualDueDateNoExerciseDueDate() throws Exception {
        final var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().orElseThrow();
        exercise.setDueDate(null);
        exercise = exerciseRepository.save(exercise);

        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(4));

        final var participationsToUpdate = new StudentParticipationList(participation);
        final var response = request.putWithResponseBodyList(String.format("/api/exercise/exercises/%d/participations/update-individual-due-date", exercise.getId()),
                participationsToUpdate, StudentParticipation.class, HttpStatus.OK);

        assertThat(response).isEmpty(); // individual due date should remain null
        verify(programmingExerciseScheduleService, never()).updateScheduling(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseIndividualDueDateInFuture() throws Exception {
        final var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().orElseThrow();
        exercise.setDueDate(ZonedDateTime.now().minusHours(4));
        exercise = exerciseRepository.save(exercise);

        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(6));
        participation = participationRepo.save(participation);

        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(2));

        final var participationsToUpdate = new StudentParticipationList(participation);
        final var response = request.putWithResponseBodyList(String.format("/api/exercise/exercises/%d/participations/update-individual-due-date", exercise.getId()),
                participationsToUpdate, StudentParticipation.class, HttpStatus.OK);

        assertThat(response).hasSize(1);
        verify(programmingExerciseScheduleService).updateScheduling(exercise);
        // make sure the student repo is unlocked as the due date is in the future
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExerciseIndividualDueDateInPast() throws Exception {
        final var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var exercise = (ProgrammingExercise) course.getExercises().stream().findAny().orElseThrow();
        exercise.setDueDate(ZonedDateTime.now().minusHours(4));
        exercise = exerciseRepository.save(exercise);

        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        participation.setIndividualDueDate(ZonedDateTime.now().plusHours(4));
        participation = participationRepo.save(participation);

        participation.setIndividualDueDate(ZonedDateTime.now().minusHours(2));

        final var participationsToUpdate = new StudentParticipationList(participation);
        final var response = request.putWithResponseBodyList(String.format("/api/exercise/exercises/%d/participations/update-individual-due-date", exercise.getId()),
                participationsToUpdate, StudentParticipation.class, HttpStatus.OK);

        assertThat(response).hasSize(1);
        verify(programmingExerciseScheduleService).updateScheduling(exercise);
    }

    /**
     * When using {@code List<StudentParticipation>} directly as body in the unit tests, the deserialization fails as
     * there no longer is a {@code type} attribute due to type erasure. Therefore, Jackson does not know which subtype
     * of {@link Participation} is stored in the list.
     * <p>
     * Using this wrapper-class avoids this issue.
     */
    private static class StudentParticipationList extends ArrayList<StudentParticipation> {

        public StudentParticipationList(StudentParticipation... participations) {
            this.addAll(Arrays.asList(participations));
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipationWithLatestResult() throws Exception {
        var participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        var submission = participationUtilService.addSubmission(participation, ParticipationFactory.generateTextSubmission("text", Language.ENGLISH, true));
        participationUtilService.addResultToSubmission(null, null, participation.findLatestSubmission().orElseThrow());
        var result = ParticipationFactory.generateResult(true, 70D);
        result.submission(submission).setCompletionDate(ZonedDateTime.now().minusHours(2));
        result.setExerciseId(textExercise.getId());
        resultRepository.save(result);
        var actualParticipation = request.get("/api/exercise/participations/" + participation.getId() + "/with-latest-result", HttpStatus.OK, StudentParticipation.class);

        assertThat(actualParticipation).isNotNull();
        assertThat(actualParticipation.getSubmissions()).as("Only latest submission is returned").containsExactly(submission);
        assertThat(participationUtilService.getResultsForParticipation(actualParticipation)).as("Only latest result is returned").containsExactly(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSubmissionOfParticipation() throws Exception {
        var participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        var submission1 = participationUtilService.addSubmission(participation, ParticipationFactory.generateTextSubmission("text", Language.ENGLISH, true));
        var submission2 = participationUtilService.addSubmission(participation, ParticipationFactory.generateTextSubmission("text2", Language.ENGLISH, true));
        var submissions = request.getList("/api/exercise/participations/" + participation.getId() + "/submissions", HttpStatus.OK, Submission.class);
        assertThat(submissions).contains(submission1, submission2);
    }

    @ParameterizedTest
    @CsvSource({ "false,false", "false,true", "true,false", "true,true" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void cleanupBuildPlan(boolean practiceMode, boolean afterDueDate) throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        participation.setPracticeMode(practiceMode);
        participationRepo.save(participation);
        if (afterDueDate) {
            programmingExercise.setDueDate(ZonedDateTime.now().minusHours(1));
            exerciseRepository.save(programmingExercise);
        }
        jenkinsRequestMockProvider.enableMockingOfRequests();
        mockDeleteBuildPlan(programmingExercise.getProjectKey(), participation.getBuildPlanId(), false);
        var actualParticipation = request.putWithResponseBody("/api/exercise/participations/" + participation.getId() + "/cleanup-build-plan", null, Participation.class,
                HttpStatus.OK);
        assertThat(actualParticipation).isEqualTo(participation);
        assertThat(actualParticipation.getInitializationState()).isEqualTo(!practiceMode && afterDueDate ? InitializationState.FINISHED : InitializationState.INACTIVE);
        assertThat(((ProgrammingExerciseStudentParticipation) actualParticipation).getBuildPlanId()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipation() throws Exception {
        var participation = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        var actualParticipation = request.get("/api/exercise/exercises/" + textExercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipationForTeamExercise() throws Exception {
        var now = ZonedDateTime.now();
        var exercise = TextExerciseFactory.generateTextExercise(now.minusDays(2), now.plusDays(2), now.plusDays(4), course);
        exercise.setMode(ExerciseMode.TEAM);
        exercise = exerciseRepository.save(exercise);

        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var team = createTeamForExercise(student, exercise);

        var teams = new HashSet<Team>();
        teams.add(team);
        exercise.setTeams(teams);
        exercise = exerciseRepository.save(exercise);

        var participation = participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercise/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipationByExerciseAndStudentIdWithEagerSubmissionsForTeam() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercise/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        var participations = participationService.findByExerciseAndStudentIdWithEagerSubmissions(exercise, student.getId());
        assertThat(participations).hasSize(1);
        assertThat(participations.getFirst().getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipationByExerciseAndStudentIdForTeam() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercise/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        var participations = participationService.findByExerciseAndStudentIdWithSubmissionsAndResults(exercise, student.getId());
        assertThat(participations).hasSize(1);
        assertThat(participations.getFirst().getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipationByExerciseAndStudentLoginAnyStateWithEagerResultsForTeam() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercise/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        var dbParticipation = participationService.findOneByExerciseAndStudentLoginAnyStateWithEagerResultsElseThrow(exercise, student.getLogin());
        assertThat(dbParticipation).isNotNull();
        assertThat(dbParticipation.getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipationByExerciseAndStudentLoginAnyStateForTeam() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercise/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        var participations = participationService.findOneByExerciseAndStudentLoginAnyState(exercise, student.getLogin());
        assertThat(participations).isPresent();
        assertThat(participations.get().getId()).isEqualTo(participation.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteAllByTeamId() throws Exception {
        var exercise = createTextExerciseForTeam();
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var team = createTeamForExercise(student, exercise);
        exercise = addTeamToExercise(team, exercise);

        var participation = participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
        var actualParticipation = request.get("/api/exercise/exercises/" + exercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);

        participationDeletionService.deleteAllByTeamId(team.getId());

        var participations = participationRepo.findByTeamId(team.getId());
        assertThat(participations).isEmpty();
    }

    private Exercise createTextExerciseForTeam() {
        var now = ZonedDateTime.now();
        var exercise = TextExerciseFactory.generateTextExercise(now.minusDays(2), now.plusDays(2), now.plusDays(4), course);
        exercise.setMode(ExerciseMode.TEAM);
        return exerciseRepository.save(exercise);
    }

    private Team createTeamForExercise(User student, Exercise exercise) {
        var team = new Team();
        team.setShortName(TEST_PREFIX + "createTeamForExercise");
        team.addStudents(student);
        team.setExercise(exercise);
        return teamRepository.save(team);
    }

    private Exercise addTeamToExercise(Team team, Exercise exercise) {
        var teams = new HashSet<Team>();
        teams.add(team);
        exercise.setTeams(teams);
        return exerciseRepository.save(exercise);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(QuizMode.class)
    void getParticipation_quizExerciseNotStarted(QuizMode quizMode) throws Exception {
        var quizEx = QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().plusHours(2), ZonedDateTime.now().plusDays(1), quizMode, course);
        quizEx = exerciseRepository.save(quizEx);
        request.get("/api/exercise/exercises/" + quizEx.getId() + "/participation", HttpStatus.FORBIDDEN, StudentParticipation.class);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(QuizMode.class)
    void getParticipation_quizExerciseStartedAndNoParticipation(QuizMode quizMode) throws Exception {
        var quizEx = QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusMinutes(2), ZonedDateTime.now().minusMinutes(1), quizMode, course);
        quizEx = exerciseRepository.save(quizEx);
        request.getNullable("/api/exercise/exercises/" + quizEx.getId() + "/participation", HttpStatus.NO_CONTENT, StudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipation_quizBatchNotPresent() throws Exception {
        var quizEx = QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now().plusMinutes(5), QuizMode.INDIVIDUAL, course).duration(360);
        quizEx = exerciseRepository.save(quizEx);
        var participation = request.get("/api/exercise/exercises/" + quizEx.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        quizEx.setRemainingNumberOfAttempts(1);
        assertThat(participation.getExercise()).as("Participation contains exercise").isEqualTo(quizEx);
        assertThat(((QuizExercise) participation.getExercise()).getRemainingNumberOfAttempts()).as("remainingNumberOfAttempts are returned correctly")
                .isEqualTo(quizEx.getRemainingNumberOfAttempts());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(QuizMode.class)
    void getParticipation_quizExerciseFinished(QuizMode quizMode) throws Exception {
        var quizEx = QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusMinutes(20), ZonedDateTime.now().minusMinutes(20), quizMode, course);
        quizEx = exerciseRepository.save(quizEx);
        var participation = participationUtilService.createAndSaveParticipationForExercise(quizEx, TEST_PREFIX + "student1");
        var submission = participationUtilService.addSubmission(participation, new QuizSubmission().scoreInPoints(11D).submitted(true));
        participationUtilService.addResultToSubmission(participation, submission);
        var actualParticipation = request.get("/api/exercise/exercises/" + quizEx.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        assertThat(actualParticipation).isEqualTo(participation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getParticipation_noParticipation() throws Exception {
        request.get("/api/exercise/exercises/" + textExercise.getId() + "/participation", HttpStatus.FAILED_DEPENDENCY, StudentParticipation.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void getParticipation_notStudentInCourse() throws Exception {
        request.get("/api/exercise/exercises/" + textExercise.getId() + "/participation", HttpStatus.FORBIDDEN, StudentParticipation.class);
    }

    @ParameterizedTest
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(QuizMode.class)
    void testCheckQuizParticipation(QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusMinutes(10), ZonedDateTime.now().minusMinutes(8), quizMode, course);
        quizExercise.addQuestions(QuizExerciseFactory.createShortAnswerQuestion());
        quizExercise.setDuration(600);
        quizExercise.setQuizPointStatistic(new QuizPointStatistic());
        quizExercise = exerciseRepository.save(quizExercise);

        ShortAnswerQuestion saQuestion = (ShortAnswerQuestion) quizExercise.getQuizQuestions().getFirst();
        List<ShortAnswerSpot> spots = saQuestion.getSpots();
        ShortAnswerSubmittedAnswer submittedAnswer = new ShortAnswerSubmittedAnswer();
        submittedAnswer.setQuizQuestion(saQuestion);

        ShortAnswerSubmittedText text = new ShortAnswerSubmittedText();
        text.setSpot(spots.getFirst());
        text.setText("test");
        submittedAnswer.addSubmittedTexts(text);

        QuizSubmission quizSubmission = new QuizSubmission();
        quizSubmission.addSubmittedAnswers(submittedAnswer);
        quizSubmission.submitted(true);
        participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student1");
        participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);

        var actualParticipation = request.get("/api/exercise/exercises/" + quizExercise.getId() + "/participation", HttpStatus.OK, StudentParticipation.class);
        var actualResults = participationUtilService.getResultsForParticipation(actualParticipation);

        assertThat(actualResults).hasSize(1);

        var actualSubmission = (QuizSubmission) actualParticipation.getSubmissions().stream().findFirst().get();
        assertThat(actualSubmission.isSubmitted()).isTrue();

        var actualSubmittedAnswers = actualSubmission.getSubmittedAnswers();
        assertThat(actualSubmittedAnswers).hasSize(1);

        var actualSubmittedAnswer = (ShortAnswerSubmittedAnswer) actualSubmittedAnswers.stream().findFirst().get();
        assertThat(actualSubmittedAnswer.getQuizQuestion()).isEqualTo(saQuestion);
        assertThat(actualSubmittedAnswer.getSubmittedTexts().stream().findFirst().isPresent()).isTrue();

        var actualSubmittedAnswerText = actualSubmittedAnswer.getSubmittedTexts().stream().findFirst().get();
        assertThat(actualSubmittedAnswerText.getText()).isEqualTo("test");
        assertThat(actualSubmittedAnswerText.isIsCorrect()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void whenRequestFeedbackForExam_thenFail() throws Exception {

        Exam examWithExerciseGroups = ExamFactory.generateExamWithExerciseGroup(course, false);
        examRepository.save(examWithExerciseGroups);
        var exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().getFirst();
        programmingExercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        programmingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig()));
        programmingExercise = exerciseRepository.save(programmingExercise);
        course.addExercises(programmingExercise);
        course = courseRepository.save(course);

        var participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        var localRepo = new LocalRepository(defaultBranch);
        localRepo.configureRepos(localVCBasePath, "testLocalRepo", "testOriginRepo");

        var localVcsRepositoryUri = new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.remoteBareGitRepoFile, localVCBasePath));
        participation.setRepositoryUri(localVcsRepositoryUri);
        participationRepo.save(participation);

        var submission = participationUtilService.addSubmission(participation, new ProgrammingSubmission());

        gitService.getDefaultLocalPathOfRepo(participation.getVcsRepositoryUri());

        var result = ParticipationFactory.generateResult(true, 100).submission(submission);
        result.setCompletionDate(ZonedDateTime.now());
        result.setExerciseId(programmingExercise.getId());
        resultRepository.save(result);

        request.putAndExpectError("/api/exercise/exercises/" + programmingExercise.getId() + "/request-feedback", null, HttpStatus.BAD_REQUEST, "preconditions not met");

        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void whenFeedbackRequestedAndDeadlinePassed_thenFail() throws Exception {

        programmingExercise.setDueDate(ZonedDateTime.now().minusDays(100));
        programmingExercise = exerciseRepository.save(programmingExercise);

        var participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        var localRepo = new LocalRepository(defaultBranch);
        localRepo.configureRepos(localVCBasePath, "testLocalRepo", "testOriginRepo");

        var localVcsRepositoryUri = new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.remoteBareGitRepoFile, localVCBasePath));
        participation.setRepositoryUri(localVcsRepositoryUri);
        participationRepo.save(participation);
        var submission = participationUtilService.addSubmission(participation, new ProgrammingSubmission());

        gitService.getDefaultLocalPathOfRepo(participation.getVcsRepositoryUri());

        var result = ParticipationFactory.generateResult(true, 100).submission(submission);
        result.setCompletionDate(ZonedDateTime.now());
        result.setExerciseId(programmingExercise.getId());
        resultRepository.save(result);

        request.putAndExpectError("/api/exercise/exercises/" + programmingExercise.getId() + "/request-feedback", null, HttpStatus.BAD_REQUEST, "feedbackRequestAfterDueDate");

        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void whenFeedbackRequestedAndRateLimitExceeded_thenFail() throws Exception {

        programmingExercise.setDueDate(ZonedDateTime.now().plusDays(100));
        programmingExercise = exerciseRepository.save(programmingExercise);

        var participation = ParticipationFactory.generateProgrammingExerciseStudentParticipation(InitializationState.INACTIVE, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        var localRepo = new LocalRepository(defaultBranch);
        localRepo.configureRepos(localVCBasePath, "testLocalRepo", "testOriginRepo");

        var localVcsRepositoryUri = new LocalVCRepositoryUri(LocalRepositoryUriUtil.convertToLocalVcUriString(localRepo.remoteBareGitRepoFile, localVCBasePath));
        participation.setRepositoryUri(localVcsRepositoryUri);
        participationRepo.save(participation);
        var submission = participationUtilService.addSubmission(participation, new ProgrammingSubmission());

        gitService.getDefaultLocalPathOfRepo(participation.getVcsRepositoryUri());

        var result = ParticipationFactory.generateResult(true, 100).submission(submission);
        result.setCompletionDate(ZonedDateTime.now());
        result.setExerciseId(programmingExercise.getId());
        resultRepository.save(result);

        // generate 20 athena results
        for (int i = 0; i < 20; i++) {
            var athenaResult = ParticipationFactory.generateResult(false, 100).submission(submission);
            athenaResult.setCompletionDate(ZonedDateTime.now());
            athenaResult.setAssessmentType(AssessmentType.AUTOMATIC_ATHENA);
            athenaResult.setExerciseId(programmingExercise.getId());
            submission.addResult(athenaResult);
            resultRepository.save(athenaResult);
        }
        submissionRepository.save(submission);

        request.putAndExpectError("/api/exercise/exercises/" + programmingExercise.getId() + "/request-feedback", null, HttpStatus.BAD_REQUEST, "maxAthenaResultsReached");

        localRepo.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void whenTextFeedbackRequestedAndNoSubmission_thenFail() throws Exception {
        var textParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.INACTIVE, textExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participationRepo.save(textParticipation);

        request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/request-feedback", null, StudentParticipation.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void whenTextFeedbackRequestedAndNoSubmittedSubmission_thenFail() throws Exception {
        var textParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.INACTIVE, textExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participationRepo.save(textParticipation);

        TextSubmission submission = new TextSubmission();
        submission.setParticipation(textParticipation);
        submissionRepository.save(submission);

        request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/request-feedback", null, StudentParticipation.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void whenTextFeedbackRequestedAndSubmissionEmpty_thenFail() throws Exception {
        var textParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.INACTIVE, textExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participationRepo.save(textParticipation);

        TextSubmission submission = new TextSubmission();
        submission.setParticipation(textParticipation);

        submission.setText("");
        submission.setSubmitted(true);
        submissionRepository.save(submission);

        request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/request-feedback", null, StudentParticipation.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void whenModelingFeedbackRequestedAndSubmissionEmpty_thenFail() throws Exception {
        var modelingParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.INACTIVE, modelingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        participationRepo.save(modelingParticipation);

        ModelingSubmission submission = new ModelingSubmission();
        submission.setParticipation(modelingParticipation);

        submission.setModel("");
        submission.setSubmitted(true);
        submissionRepository.save(submission);

        request.putWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/request-feedback", null, StudentParticipation.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testStartParticipationForExamProgrammingExerciseAsTutorNotAllowed() throws Exception {
        Exam exam = ExamFactory.generateExamWithExerciseGroup(course, false);
        exam = examRepository.save(exam);
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().getFirst();
        ProgrammingExercise examExercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup);
        examExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(examExercise.getBuildConfig()));
        examExercise = exerciseRepository.save(examExercise);

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/exercise/exercises/" + examExercise.getId() + "/participations", null, HttpStatus.FORBIDDEN, null);
        assertThat(response.getContentAsString()).contains("Assignment repositories are not allowed for exam exercises. Please use the Test Run feature instead");
    }

    @Nested
    @Isolated
    class ParticipationIntegrationIsolatedTest {

        @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        @EnumSource(QuizMode.class)
        void getParticipation_quizExerciseStartedAndSubmissionAllowed(QuizMode quizMode) throws Exception {
            var quizEx = QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now().plusMinutes(5), quizMode, course).duration(360);
            quizEx = exerciseRepository.save(quizEx);

            request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizEx.getId() + "/start-participation", null, StudentParticipation.class, HttpStatus.OK);

            if (quizMode != QuizMode.SYNCHRONIZED) {
                var batch = quizBatchService.save(QuizExerciseFactory.generateQuizBatch(quizEx, ZonedDateTime.now().minusSeconds(10)));
                request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizEx.getId() + "/join", new QuizBatchJoinDTO(batch.getPassword()), QuizBatch.class, HttpStatus.OK);
            }
            var participation = request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizEx.getId() + "/start-participation", null, StudentParticipation.class,
                    HttpStatus.OK);
            assertThat(participation.getExercise()).as("Participation contains exercise").isEqualTo(quizEx);
            assertThat(participationUtilService.getResultsForParticipation(participation)).as("New result was added to the participation").hasSize(1);
            assertThat(participation.getInitializationState()).as("Participation was initialized").isEqualTo(InitializationState.INITIALIZED);
        }
    }
}
