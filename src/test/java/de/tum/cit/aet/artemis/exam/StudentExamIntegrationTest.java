package de.tum.cit.aet.artemis.exam;

import static de.tum.cit.aet.artemis.core.util.SensitiveInformationUtil.assertSensitiveInformationWasFilteredFileUploadExercise;
import static de.tum.cit.aet.artemis.core.util.SensitiveInformationUtil.assertSensitiveInformationWasFilteredModelingExercise;
import static de.tum.cit.aet.artemis.core.util.SensitiveInformationUtil.assertSensitiveInformationWasFilteredProgrammingExercise;
import static de.tum.cit.aet.artemis.core.util.SensitiveInformationUtil.assertSensitiveInformationWasFilteredTextExercise;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.hibernate.Hibernate;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.BonusStrategy;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradeType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.BonusRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.util.BonusFactory;
import de.tum.cit.aet.artemis.assessment.util.GradingScaleUtilService;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.util.RoundingUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.CreateTestRunDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamChecklistDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamUpdateDTO;
import de.tum.cit.aet.artemis.exam.dto.StudentExamDTO;
import de.tum.cit.aet.artemis.exam.dto.StudentExamWithGradeDTO;
import de.tum.cit.aet.artemis.exam.dto.conduction.SubmissionPolicyForConductionDTO;
import de.tum.cit.aet.artemis.exam.dto.examevent.ExamAttendanceCheckEventDTO;
import de.tum.cit.aet.artemis.exam.dto.examevent.ExamLiveEventBaseDTO;
import de.tum.cit.aet.artemis.exam.dto.examevent.ExamWideAnnouncementEventDTO;
import de.tum.cit.aet.artemis.exam.dto.examevent.WorkingTimeUpdateEventDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamSessionRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.service.ExamQuizService;
import de.tum.cit.aet.artemis.exam.service.StudentExamService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exam.util.ExamPrepareExercisesTestUtil;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionVersionRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseTestService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedText;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizSubmissionTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.dto.TextSubmissionRequestDTO;

class StudentExamIntegrationTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final Logger log = LoggerFactory.getLogger(StudentExamIntegrationTest.class);

    private static final String TEST_PREFIX = "studexam";

    private static final String OTHER_STUDENT = TEST_PREFIX + "other" + "student42";

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private ExamSessionRepository examSessionRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private SubmissionVersionRepository submissionVersionRepository;

    @Autowired
    private ExamQuizService examQuizService;

    @Autowired
    private QuizSubmissionTestRepository quizSubmissionTestRepository;

    @Autowired
    private ParticipationDeletionService participationDeletionService;

    @Autowired
    private StudentExamService studentExamService;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private BonusRepository bonusRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private TempFileUtilService tempFileUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private GradingScaleUtilService gradingScaleUtilService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User student1;

    private Course course1;

    private Course course2;

    private Exam exam1;

    private Exam exam2;

    private Exam testExam1;

    private Exam testExam2;

    private Exam testRunExam;

    private StudentExam studentExam1;

    private StudentExam studentExamForTestExam1;

    private StudentExam studentExamForTestExam2;

    private final List<LocalVCTestRepository> studentRepos = new ArrayList<>();

    private final Map<Long, String> programmingInitialCommitHashes = new HashMap<>();

    private final Map<Long, String> programmingUpdatedCommitHashes = new HashMap<>();

    private static final int NUMBER_OF_STUDENTS = 2;

    private static final boolean IS_TEST_RUN = false;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 0, 2);

        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        exam1 = examUtilService.addActiveExamWithRegisteredUser(course1, student2);
        exam1 = examRepository.save(exam1);

        exam2 = examUtilService.addExam(course1);
        exam2.setTitle("Real exam 2");  // Change the name to avoid confusion with 'exam1'
        exam2 = examRepository.save(exam2);
        exam2 = examUtilService.addTextModelingProgrammingExercisesToExam(exam2, true, false);

        studentExam1 = examUtilService.addStudentExam(exam1);
        studentExam1.setWorkingTime(7200);
        studentExam1.setUser(student1);
        studentExamRepository.save(studentExam1);
        examUtilService.addStudentExam(exam2);

        testExam1 = examUtilService.addActiveTestExamWithRegisteredUserWithoutStudentExam(course1, student1);
        studentExamForTestExam1 = examUtilService.addStudentExamForTestExam(testExam1, student1);

        testExam2 = examUtilService.addTestExamWithRegisteredUser(course1, student1);
        testExam2.setVisibleDate(ZonedDateTime.now().minusHours(3));
        testExam2.setStartDate(ZonedDateTime.now().minusHours(2));
        testExam2.setEndDate(ZonedDateTime.now().minusHours(1));
        testExam2 = examRepository.save(testExam2);
        testExam2 = examUtilService.addTextModelingProgrammingExercisesToExam(testExam2, false, true);
        studentExamForTestExam2 = examUtilService.addStudentExamForTestExam(testExam2, student1);
        studentExamForTestExam2.setSubmitted(true);
        studentExamForTestExam2.setSubmissionDate(ZonedDateTime.now().minusMinutes(65));
        studentExamRepository.save(studentExamForTestExam2);

        userUtilService.createAndSaveUser(OTHER_STUDENT);
        studentRepos.clear();
        programmingInitialCommitHashes.clear();
        programmingUpdatedCommitHashes.clear();
        // TODO: all parts using programmingExerciseTestService should also be provided for LocalVC+Jenkins
        programmingExerciseTestService.setup(this, versionControlService);
        jenkinsRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        programmingExerciseTestService.tearDown();
        jenkinsRequestMockProvider.reset();
        RepositoryExportTestUtil.cleanupTrackedRepositories();

        for (var repo : studentRepos) {
            repo.deleteWorkingCopy();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFindOne() {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> studentExamRepository.findByIdElseThrow(Long.MAX_VALUE));
        assertThat(studentExamRepository.findByIdElseThrow(studentExam1.getId())).isEqualTo(studentExam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFindOneWithExercisesByUserIdAndExamId() {
        var studentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(Long.MAX_VALUE, exam1.getId(), IS_TEST_RUN);
        assertThat(studentExam).isEmpty();
        studentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(student1.getId(), exam1.getId(), IS_TEST_RUN);
        assertThat(studentExam).contains(studentExam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFindAllDistinctWorkingTimesByExamId() {
        assertThat(studentExamRepository.findAllDistinctWorkingTimesByExamId(Long.MAX_VALUE)).isEqualTo(Set.of());
        assertThat(studentExamRepository.findAllDistinctWorkingTimesByExamId(exam1.getId())).isEqualTo(Set.of(studentExam1.getWorkingTime()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFindMaxWorkingTimeById() {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> studentExamRepository.findMaxWorkingTimeByExamIdElseThrow(Long.MAX_VALUE));

        assertThat(studentExamRepository.findMaxWorkingTimeByExamIdElseThrow(exam1.getId())).isEqualTo(studentExam1.getWorkingTime());
    }

    private void deleteExamWithInstructor(Exam exam) throws Exception {
        // change back to instructor user
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        // Clean up to prevent exceptions during reset database
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId(), HttpStatus.FORBIDDEN, StudentExam.class);
        request.getList("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams", HttpStatus.FORBIDDEN, StudentExamDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStudentExam_asInstructor() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId(), HttpStatus.OK, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStudentExamsForExam_asInstructor() throws Exception {
        // measured baseline is 8 queries (auth + access checks including the user_course_role membership check + findByExamIdWithSessions);
        // guards against an accidental N+1 regression (e.g. touching a lazy association per element) creeping into the DTO factory
        List<StudentExamDTO> studentExams = assertThatDb(
                () -> request.getList("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams", HttpStatus.OK, StudentExamDTO.class))
                .hasBeenCalledAtMostTimes(8);
        assertThat(studentExams).hasSize(2);
        // the nested exam/user are intentionally omitted for this endpoint (see StudentExamDTO#of)
        assertThat(studentExams).allSatisfy(dto -> {
            assertThat(dto.exam()).isNull();
            assertThat(dto.user()).isNull();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStudentExamForExam_withProgrammingExerciseWithActiveSubmissionPolicy_asInstructor() throws Exception {

        // set up a programming exercise with a submission policy
        SubmissionPolicy submissionPolicy = new LockRepositoryPolicy();
        submissionPolicy.setSubmissionLimit(5);
        submissionPolicy.setActive(true);
        var programmingExercise = ExerciseUtilService.getFirstExerciseWithType(exam2, ProgrammingExercise.class);
        programmingExerciseUtilService.addSubmissionPolicyToExercise(submissionPolicy, programmingExercise);

        StudentExam studentExam = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId(), HttpStatus.OK,
                StudentExam.class);

        // check that the submission policy is included in the response
        for (var exercise : studentExam.getExercises()) {
            if (exercise instanceof ProgrammingExercise) {
                assertThat(((ProgrammingExercise) exercise).getSubmissionPolicy().isActive()).isTrue();
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithMultipleTestRuns() throws Exception {
        prepareStudentExamsForConduction(false, true, 1);

        assertThat(studentExamRepository.findByExamId(exam2.getId())).hasSize(1);

        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        exam2 = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(exam2.getId());
        var usersOfExam = exam2.getRegisteredUsers();
        usersOfExam.add(instructor);

        var programmingExercise = ExerciseUtilService.getFirstExerciseWithType(exam2, ProgrammingExercise.class);

        jenkinsRequestMockProvider.reset();

        // the empty commit is not necessary for this test
        mockConnectorRequestsForStartParticipation(programmingExercise, instructor.getParticipantIdentifier(), Set.of(instructor), true);
        mockConnectorRequestsForStartParticipation(programmingExercise, instructor.getParticipantIdentifier(), Set.of(instructor), true);
        mockConnectorRequestsForStartParticipation(programmingExercise, instructor.getParticipantIdentifier(), Set.of(instructor), true);

        // create multiple test runs for the same user (i.e. instructor1), login again because "createTestRun" invokes a server method with changes the authorization
        createTestRun(exam2);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        createTestRun(exam2);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        createTestRun(exam2);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        assertThat(studentExamRepository.findAllTestRunsByExamId(exam2.getId())).hasSize(3);

        jenkinsRequestMockProvider.reset();
        mockDeleteProgrammingExercise(programmingExercise, usersOfExam);

        request.delete("/api/exam/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId(), HttpStatus.OK);

        assertThat(studentExamRepository.findAllTestRunsByExamId(exam2.getId())).isEmpty();
        assertThat(studentExamRepository.findByExamId(exam2.getId())).isEmpty();
    }

    private List<StudentExam> prepareStudentExamsForConduction(boolean early, boolean setFields, int numberOfStudents) throws Exception {
        ZonedDateTime visibleDate;
        ZonedDateTime startDate;
        ZonedDateTime endDate;
        if (early) {
            startDate = ZonedDateTime.now().plusHours(1);
            endDate = ZonedDateTime.now().plusHours(3);
        }
        else {
            // If the exam is prepared only 5 minutes before the release date, the repositories of the students are unlocked as well.
            startDate = ZonedDateTime.now().plusMinutes(6);
            endDate = ZonedDateTime.now().plusMinutes(8);
        }

        visibleDate = ZonedDateTime.now().minusMinutes(15);
        // --> 2 min = 120s working time

        Set<User> registeredStudents = getRegisteredStudents(numberOfStudents);
        var studentExams = programmingExerciseTestService.prepareStudentExamsForConduction(TEST_PREFIX, visibleDate, startDate, endDate, registeredStudents, studentRepos);
        Exam exam = examRepository.findByIdElseThrow(studentExams.getFirst().getExam().getId());
        Course course = exam.getCourse();

        if (!early) {
            // simulate "wait" for exam to start
            exam.setStartDate(ZonedDateTime.now());
            exam.setEndDate(ZonedDateTime.now().plusMinutes(2));
            examRepository.save(exam);
        }

        if (setFields) {
            exam2 = exam;
            course2 = course;
        }
        return studentExams;
    }

    private Set<User> getRegisteredStudents(int numberOfRegisteredStudents) {
        Set<User> registeredStudents = new HashSet<>();
        for (int i = 1; i <= numberOfRegisteredStudents; i++) {
            registeredStudents.add(userUtilService.getUserByLogin(TEST_PREFIX + "student" + i));
        }
        return registeredStudents;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testStartExercises_testExam() throws Exception {
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/start-exercises", null, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStudentExamForConduction() throws Exception {
        List<StudentExam> studentExams = prepareStudentExamsForConduction(false, true, NUMBER_OF_STUDENTS);

        for (var studentExam : studentExams) {
            var user = studentExam.getUser();
            userUtilService.changeUser(user.getLogin());
            final HttpHeaders headers = getHttpHeadersForExamSession();
            var response = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction", HttpStatus.OK,
                    StudentExam.class, headers);
            assertThat(response).isEqualTo(studentExam);
            assertThat(response.isStarted()).isTrue();
            assertThat(response.getExercises()).hasSize(exam2.getNumberOfExercisesInExam());
            for (Exercise exercise : response.getExercises()) {
                assertThat(exercise.getExerciseGroup()).isNotNull();
                assertThat(exercise.getExerciseGroup().getExercises()).isEmpty();
                assertThat(exercise.getExerciseGroup().getExam()).isNull();
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(((ProgrammingExercise) exercise).getBuildConfig()).isNull();
                }
            }
            assertThat(studentExamRepository.findById(studentExam.getId()).orElseThrow().isStarted()).isTrue();
            assertParticipationAndSubmissions(response, user);
        }

        deleteExamWithInstructor(exam1);
    }

    private static HttpHeaders getHttpHeadersForExamSession() {
        final HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "foo");
        headers.set("X-Artemis-Client-Fingerprint", "bar");
        headers.set("X-Forwarded-For", "10.0.28.1");
        return headers;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForConduction_testExam() throws Exception {
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        var exam = examUtilService.addExam(course1, examVisibleDate, examStartDate, examEndDate);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, true);
        exam.setTestExam(true);
        var examUser5 = new ExamUser();
        examUser5.setExam(exam);
        examUser5.setUser(student1);
        examUser5 = examUserRepository.save(examUser5);
        exam.addExamUser(examUser5);
        exam = examRepository.save(exam);

        var programmingExercise = (ProgrammingExercise) exam.getExerciseGroups().get(6).getExercises().iterator().next();
        programmingExerciseTestService.setupRepositories(programmingExercise);
        mockConnectorRequestsForStartParticipation(programmingExercise, student1.getLogin(), Set.of(student1), true);

        StudentExam studentExamForStart = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/own-student-exam", HttpStatus.OK, StudentExam.class);

        final HttpHeaders headers = getHttpHeadersForExamSession();
        var response = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExamForStart.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class, headers);
        assertThat(response).isEqualTo(studentExamForStart);
        assertThat(studentExamRepository.findById(studentExamForStart.getId()).orElseThrow().isStarted()).isTrue();
        assertParticipationAndSubmissions(response, student1);

        // TODO: test the conduction / submission of the test exams, in particular that the summary includes all submissions

        deleteExamWithInstructor(testExam1);
    }

    private void assertParticipationAndSubmissions(StudentExam response, User user) {
        for (var exercise : response.getExercises()) {
            assertThat(exercise.getStudentParticipations()).as(exercise.getClass().getName() + " should have 1 participation").hasSize(1);
            var participation = exercise.getStudentParticipations().iterator().next();
            if (!(exercise instanceof ProgrammingExercise)) {
                assertThat(participation.getSubmissions()).as(exercise.getClass().getName() + " should have 1 submission").hasSize(1);
                var submission = participation.getSubmissions().iterator().next();
                assertThat(participation.getParticipant()).isEqualTo(user);
                assertThat(submission.isSubmitted()).isFalse();
                assertThat(submission.getResults()).as(exercise.getClass().getName() + " should have no results").isNullOrEmpty();
            }
            assertThat(exercise.getGradingCriteria()).isNullOrEmpty();
            assertThat(exercise.getGradingInstructions()).isNullOrEmpty();
        }
        var textExercise = (TextExercise) response.getExercises().getFirst();
        var quizExercise = (QuizExercise) response.getExercises().get(1);

        // Check that sensitive information has been removed
        assertThat(textExercise.getExampleSolution()).isNull();

        assertThat(quizExercise.getQuizQuestions()).hasSize(3);

        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                assertThat(((MultipleChoiceQuestion) question).getAnswerOptions()).hasSize(2);
                for (AnswerOption answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                    assertThat(answerOption.getExplanation()).isNull();
                    assertThat(answerOption.isIsCorrect()).isNull();
                }
            }
            else if (question instanceof DragAndDropQuestion) {
                assertThat(((DragAndDropQuestion) question).getCorrectMappings()).isEmpty();
            }
            else if (question instanceof ShortAnswerQuestion) {
                assertThat(((ShortAnswerQuestion) question).getCorrectMappings()).isEmpty();
            }
        }

        assertThat(response.getExamSessions()).hasSize(1);
        var examSession = response.getExamSessions().iterator().next();
        final var optionalExamSession = examSessionRepository.findById(examSession.getId());
        assertThat(optionalExamSession).isPresent();

        assertThat(examSession.getSessionToken()).isNotNull();
        assertThat(examSession.getUserAgent()).isNull();
        assertThat(examSession.getBrowserFingerprintHash()).isNull();
        assertThat(examSession.getIpAddress()).isNull();
        assertThat(optionalExamSession.get().getUserAgent()).isEqualTo("foo");
        assertThat(optionalExamSession.get().getBrowserFingerprintHash()).isEqualTo("bar");
        assertThat(optionalExamSession.get().getIpAddressAsIpAddress().toNormalizedString()).isEqualTo("10.0.28.1");
    }

    /**
     * Wire-contract guard for the student-facing data minimisation the conduction endpoint performs on the raw JSON.
     * <p>
     * During conduction (results not published, not a test run) the server strips every solution signal a student must
     * not see while keeping exactly the ids the exam-taking client needs to render the question and build an answer.
     * This pins that masking at the wire, at the exact locations the client reads: quiz answer options must carry an
     * {@code id} but neither {@code isCorrect} nor {@code explanation}; drag-and-drop / short-answer questions must
     * carry their answerable {@code dragItems}/{@code dropLocations}/{@code spots} (with ids) but no
     * {@code correctMappings}; short-answer questions must not leak {@code solutions}; and the text exercise must not
     * leak its {@code exampleSolution}. A conduction DTO projection must reproduce this masked shape exactly — never
     * re-adding the stripped fields — so this guards the migration against silently exposing solutions to students.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testConductionWireMasksSolutionsButKeepsAnswerableIds() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();
        userUtilService.changeUser(studentExam.getUser().getLogin());

        final HttpHeaders headers = getHttpHeadersForExamSession();
        JsonNode conductionWire = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, JsonNode.class, headers);

        boolean sawQuiz = false;
        boolean sawText = false;
        for (JsonNode exercise : conductionWire.get("exercises")) {
            if ("text".equals(exercise.path("type").asString())) {
                sawText = true;
                assertThat(exercise.has("exampleSolution")).as("text exercise must not leak exampleSolution during conduction").isFalse();
            }
            JsonNode questions = exercise.get("quizQuestions");
            if (questions == null) {
                continue;
            }
            sawQuiz = true;
            assertThat(questions).hasSize(3);
            for (JsonNode question : questions) {
                assertThat(question.hasNonNull("id")).as("quiz question carries an id for the client").isTrue();
                assertThat(question.hasNonNull("type")).as("quiz question carries its polymorphic type discriminator").isTrue();
                switch (question.get("type").asString()) {
                    case "multiple-choice" -> {
                        JsonNode options = question.get("answerOptions");
                        assertThat(options).as("MC question keeps its answer options for the client").isNotNull();
                        assertThat(options).hasSize(2);
                        for (JsonNode option : options) {
                            assertThat(option.hasNonNull("id")).as("answer option carries an id the client needs to submit a selection").isTrue();
                            assertThat(option.has("isCorrect")).as("answer option must not leak isCorrect during conduction").isFalse();
                            assertThat(option.has("explanation")).as("answer option must not leak explanation during conduction").isFalse();
                        }
                    }
                    case "drag-and-drop" -> {
                        assertThat(question.get("dragItems")).as("DnD question keeps its drag items for the client").isNotNull();
                        assertThat(question.get("dropLocations")).as("DnD question keeps its drop locations for the client").isNotNull();
                        assertThat(question.path("correctMappings").isEmpty()).as("DnD question must not leak correctMappings during conduction").isTrue();
                    }
                    case "short-answer" -> {
                        assertThat(question.get("spots")).as("SA question keeps its spots for the client").isNotNull();
                        assertThat(question.path("correctMappings").isEmpty()).as("SA question must not leak correctMappings during conduction").isTrue();
                        assertThat(question.path("solutions").isEmpty()).as("SA question must not leak solutions during conduction").isTrue();
                    }
                    default -> {
                    }
                }
            }
        }

        assertThat(sawQuiz).as("conduction wire exposed a quiz exercise to mask-check").isTrue();
        assertThat(sawText).as("conduction wire exposed a text exercise to mask-check").isTrue();

        deleteExamWithInstructor(exam1);
    }

    @ParameterizedTest
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @ValueSource(booleans = { true, false })
    void testGetTestRunForConduction(boolean isTestExam) throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        // --> 2 min = 120s working time

        course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        exam2 = examUtilService.addExam(course2, examVisibleDate, examStartDate, examEndDate);

        exam2.setTestExam(isTestExam);
        exam2 = examRepository.save(exam2);

        var exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam2, true, false);
        final var testRun = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        var programmingExercise = (ProgrammingExercise) exam.getExerciseGroups().get(2).getExercises().iterator().next();
        programmingExerciseTestService.setupRepositories(programmingExercise);
        mockConnectorRequestsForStartParticipation(programmingExercise, instructor.getLogin(), Set.of(instructor), true);

        assertThat(testRun.isTestRun()).isTrue();

        var response = request.get("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-runs/" + testRun.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);
        assertThat(response).isEqualTo(testRun);
        assertThat(response.isStarted()).isTrue();
        assertThat(response.isTestRun()).isTrue();
        assertThat(response.getExercises()).hasSize(exam.getNumberOfExercisesInExam());
        for (Exercise exercise : response.getExercises()) {
            assertThat(exercise.getStudentParticipations()).hasSize(1);
        }
        // Ensure that student exam was marked as started
        assertThat(studentExamRepository.findById(testRun.getId()).orElseThrow().isStarted()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFindAllTestRunsForExam() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var instructor2 = userUtilService.getUserByLogin(TEST_PREFIX + "instructor2");
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        // --> 2 min = 120s working time

        course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        exam2 = examUtilService.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        var exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam2, true, false);
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor2, exam.getExerciseGroups());

        // measured baseline is 8 queries (auth + access checks including the user_course_role membership check + findAllTestRunsByExamId);
        // guards against an accidental N+1 regression (e.g. touching a lazy association per element) creeping into the DTO factory
        List<StudentExamDTO> response = assertThatDb(
                () -> request.getList("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-runs", HttpStatus.OK, StudentExamDTO.class))
                .hasBeenCalledAtMostTimes(8);
        assertThat(response).hasSize(2);
        // the template reads user.name/user.id (see StudentExamDTO#withUser); the nested exam is intentionally omitted
        assertThat(response).allSatisfy(dto -> {
            assertThat(dto.user()).isNotNull();
            assertThat(dto.exam()).isNull();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllTestRunSubmissionsForExercise() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        exam2 = examUtilService.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        var exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam2, false, false);
        var testRun = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        List<Submission> response = request.getList("/api/exercise/exercises/" + testRun.getExercises().getFirst().getId() + "/test-run-submissions", HttpStatus.OK,
                Submission.class);
        assertThat(response).isNotEmpty();
        assertThat((response.getFirst().getParticipation()).isTestRun()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetTestRunSubmissionsForProgrammingExerciseKeepsAutomaticFeedback() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        exam2 = examUtilService.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        var exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam2, true, false);
        var testRun = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        var programmingExercise = (ProgrammingExercise) testRun.getExercises().stream().filter(exercise -> exercise instanceof ProgrammingExercise).findFirst().orElseThrow();

        // give the test-run submission an automatic result with typed test-case feedback (including a
        // deduplicated message) - the assessment draft must copy and expose it
        var testRunParticipation = studentParticipationRepository
                .findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(instructor.getId(), List.of(programmingExercise)).getFirst();
        var submission = testRunParticipation.findLatestSubmission().orElseThrow();
        var automaticResult = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, ZonedDateTime.now(), submission);
        var testCase = programmingExerciseUtilService.addTestCaseToProgrammingExercise(programmingExercise, "testRunTest");
        participationUtilService.addTestCaseFeedbackToResult(automaticResult, testCase, false, "test-run failure message");

        List<Submission> response = request.getList("/api/exercise/exercises/" + programmingExercise.getId() + "/test-run-submissions", HttpStatus.OK, Submission.class);

        assertThat(response).hasSize(1);
        var draft = response.getFirst().getResults().stream().filter(result -> result.getAssessmentType() == AssessmentType.SEMI_AUTOMATIC).findFirst().orElseThrow();
        // the automatic feedback was copied into the draft as typed rows and is exposed as synthesized views
        assertThat(draft.getFeedbacks()).anySatisfy(feedback -> {
            assertThat(feedback.getId()).isNegative();
            assertThat(feedback.getDetailText()).isEqualTo("test-run failure message");
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllTestRunSubmissionsForExercise_notExamExercise() throws Exception {
        course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course2, false);
        request.getList("/api/exercise/exercises/" + exercise.getId() + "/test-run-submissions", HttpStatus.FORBIDDEN, Submission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllTestRunSubmissionsForExercise_notInstructor() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        exam2 = examUtilService.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        var exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam2, false, false);
        var testRun = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        userUtilService.changeUser(TEST_PREFIX + "student2");
        request.getList("/api/exercise/exercises/" + testRun.getExercises().getFirst().getId() + "/test-run-submissions", HttpStatus.FORBIDDEN, Submission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllTestRunSubmissionsForExercise_noTestRunSubmissions() throws Exception {
        course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        exam2 = examUtilService.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        var exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam2, false, false);
        final var latestSubmissions = request.getList(
                "/api/exercise/exercises/" + exam.getExerciseGroups().getFirst().getExercises().iterator().next().getId() + "/test-run-submissions", HttpStatus.OK,
                Submission.class);
        assertThat(latestSubmissions).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetWorkingTimesNoStudentExams() {
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(5);
        var examEndDate = ZonedDateTime.now().plusMinutes(20);

        Course course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Exam exam = examUtilService.addExam(course, examVisibleDate, examStartDate, examEndDate);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, true);

        // register user
        Set<User> registeredStudents = getRegisteredStudents(NUMBER_OF_STUDENTS);
        for (User student : registeredStudents) {
            var examUser = new ExamUser();
            examUser.setUser(student);
            examUser.setExam(exam);
            examUser = examUserRepository.save(examUser);
            exam.addExamUser(examUser);
        }
        exam.setNumberOfExercisesInExam(2);
        exam.setRandomizeExerciseOrder(false);
        exam = examRepository.save(exam);

        /*
         * don't generate individual student exams
         */
        assertThat(studentExamRepository.findMaxWorkingTimeByExamId(exam.getId())).isEmpty();
        assertThat(studentExamRepository.findAllDistinctWorkingTimesByExamId(exam.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetWorkingTimesDifferentStudentExams() throws Exception {
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(5);
        var examEndDate = ZonedDateTime.now().plusMinutes(20);

        Course course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Exam exam = examUtilService.addExam(course, examVisibleDate, examStartDate, examEndDate);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, true);

        // register user
        Set<User> registeredStudents = getRegisteredStudents(NUMBER_OF_STUDENTS);
        for (User student : registeredStudents) {
            var examUser = new ExamUser();
            examUser.setUser(student);
            examUser.setExam(exam);
            examUser = examUserRepository.save(examUser);
            exam.addExamUser(examUser);
        }
        exam.setRandomizeExerciseOrder(false);
        exam = examRepository.save(exam);

        // generate individual student exams (the response masks the nested exam; re-fetch managed entities to modify them)
        request.postListWithResponseBody("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExamDTO.class,
                HttpStatus.OK);
        List<StudentExam> studentExams = new ArrayList<>(studentExamRepository.findByExamId(exam.getId()));

        // Modify working times

        var expectedWorkingTimes = new HashSet<Integer>();
        int maxWorkingTime = (int) Duration.between(examStartDate, examEndDate).getSeconds();

        for (int i = 0; i < studentExams.size(); i++) {
            if (i % 2 == 0) {
                maxWorkingTime += 35;
            }
            expectedWorkingTimes.add(maxWorkingTime);

            var studentExam = studentExams.get(i);
            studentExam.setWorkingTime(maxWorkingTime);
            studentExamRepository.save(studentExam);
        }

        assertThat(studentExamRepository.findMaxWorkingTimeByExamId(exam.getId())).contains(maxWorkingTime);
        assertThat(studentExamRepository.findAllDistinctWorkingTimesByExamId(exam.getId())).containsExactlyInAnyOrderElementsOf(expectedWorkingTimes);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateWorkingTime() throws Exception {
        int newWorkingTime = 180 * 60;
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        exam1 = examRepository.save(exam1);
        StudentExamDTO result = request.patchWithResponseBody(
                "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time", newWorkingTime,
                StudentExamDTO.class, HttpStatus.OK);
        assertThat(result.workingTime()).isEqualTo(newWorkingTime);
        assertThat(studentExamRepository.findById(studentExam1.getId()).orElseThrow().getWorkingTime()).isEqualTo(newWorkingTime);
        // the client's processStudentExam/setAccessRightsForCourse need the nested exam + course
        assertThat(result.exam()).isNotNull();
        assertThat(result.exam().id()).isEqualTo(exam1.getId());
        assertThat(result.exam().course()).isNotNull();
        assertThat(result.exam().course().id()).isEqualTo(course1.getId());
        // user is intentionally omitted from this endpoint's response (see StudentExamDTO#withExam)
        assertThat(result.user()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateWorkingTimeInvalid() throws Exception {
        int newWorkingTime = 0;
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        exam1 = examRepository.save(exam1);
        request.patchWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time",
                newWorkingTime, StudentExamDTO.class, HttpStatus.BAD_REQUEST);
        // working time did not change
        var studentExamDB = studentExamRepository.findById(studentExam1.getId()).orElseThrow();
        assertThat(studentExamDB.getWorkingTime()).isEqualTo(studentExam1.getWorkingTime());

        newWorkingTime = -10;
        request.patchWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time",
                newWorkingTime, StudentExamDTO.class, HttpStatus.BAD_REQUEST);
        // working time did not change
        studentExamDB = studentExamRepository.findById(studentExam1.getId()).orElseThrow();
        assertThat(studentExamDB.getWorkingTime()).isEqualTo(studentExam1.getWorkingTime());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateWorkingTime_failsIfIndividualEndReachesSummaryPublicationDate() throws Exception {
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        // the submission overview becomes visible one hour after the nominal end date
        exam1.setPublishResultsDate(null);
        exam1.setExamSummaryPublicationDate(exam1.getEndDate().plusHours(1));
        exam1 = examRepository.save(exam1);
        int originalWorkingTime = studentExam1.getWorkingTime();
        long secondsUntilPublication = Duration.between(exam1.getStartDate(), exam1.getExamSummaryPublicationDate()).getSeconds();

        // an individual extension that would push this student past the publication date must be rejected: otherwise the summary and conduction gates open for students who
        // already submitted while this student is still writing
        request.patchWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time",
                (int) secondsUntilPublication + 60, StudentExam.class, HttpStatus.BAD_REQUEST);
        assertThat(studentExamRepository.findById(studentExam1.getId()).orElseThrow().getWorkingTime()).isEqualTo(originalWorkingTime);

        // an extension landing exactly on the publication date is rejected as well (the summary must not open at the very moment the student is still allowed to submit)
        request.patchWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time",
                (int) secondsUntilPublication, StudentExam.class, HttpStatus.BAD_REQUEST);
        assertThat(studentExamRepository.findById(studentExam1.getId()).orElseThrow().getWorkingTime()).isEqualTo(originalWorkingTime);

        // an extension that keeps the individual end before the publication date is still allowed
        int allowedWorkingTime = (int) secondsUntilPublication - 60;
        StudentExam result = request.patchWithResponseBody(
                "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time", allowedWorkingTime,
                StudentExam.class, HttpStatus.OK);
        assertThat(result.getWorkingTime()).isEqualTo(allowedWorkingTime);

        // with the individual extension in place, the instructor may no longer pull the publication date in front of that student's individual end date
        Exam examWithExtension = examRepository.findByIdElseThrow(exam1.getId());
        examWithExtension.setExamSummaryPublicationDate(examWithExtension.getStartDate().plusSeconds(allowedWorkingTime));
        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(examWithExtension), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExamDuration_failsWhenRescaledIndividualExtensionCrossesSummaryPublicationDate() throws Exception {
        // NOTE: unlike the working-time PATCH tests above, this one goes through the full exam update, which validates
        // the date ordering. The "active exam" fixture starts an hour in the past but keeps a visible date near now, so
        // the visible date has to be pulled in front of the start date for any update of it to be accepted at all.
        exam1.setVisibleDate(exam1.getStartDate().minusMinutes(30));
        exam1.setPublishResultsDate(null);
        exam1 = examRepository.save(exam1);
        int examDuration = exam1.getDuration();

        // Give the student a modest individual extension (+10% of the exam duration) and set the publication date just
        // after that individual end, so the invariant holds for the current state.
        int extendedWorkingTime = examDuration + examDuration / 10;
        request.patchWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time",
                extendedWorkingTime, StudentExam.class, HttpStatus.OK);
        Exam examWithExtension = examRepository.findByIdElseThrow(exam1.getId());
        ZonedDateTime individualEnd = examWithExtension.getStartDate().plusSeconds(extendedWorkingTime);
        examWithExtension.setExamSummaryPublicationDate(individualEnd.plusMinutes(5));
        Exam savedExam = request.putWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(examWithExtension), Exam.class, HttpStatus.OK);

        // Now stretch the exam end date. The nominal end stays before the publication date, but updateStudentExamsAndRescheduleExercises
        // rescales the individual extension proportionally, which pushes this student past it — so the update has to be rejected.
        savedExam.setEndDate(savedExam.getExamSummaryPublicationDate().minusMinutes(1));
        assertThat(savedExam.getEndDate()).isBefore(savedExam.getExamSummaryPublicationDate());
        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(savedExam), HttpStatus.BAD_REQUEST);

        // the rejected update must not have touched the stored working time
        assertThat(studentExamRepository.findById(studentExam1.getId()).orElseThrow().getWorkingTime()).isEqualTo(extendedWorkingTime);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateWorkingTimeLate() throws Exception {
        int newWorkingTime = 180 * 60;
        int oldWorkingTime = studentExam1.getWorkingTime();
        exam1.setVisibleDate(ZonedDateTime.now().minusMinutes(1));
        exam1 = examRepository.save(exam1);
        StudentExamDTO result = request.patchWithResponseBody(
                "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time", newWorkingTime,
                StudentExamDTO.class, HttpStatus.OK);
        assertThat(result.workingTime()).isEqualTo(newWorkingTime);
        assertThat(studentExamRepository.findById(studentExam1.getId()).orElseThrow().getWorkingTime()).isEqualTo(newWorkingTime);

        var capturedEvent = (WorkingTimeUpdateEventDTO) captureExamLiveEventForId(studentExam1.getId(), false);

        assertThat(capturedEvent.newWorkingTime()).isEqualTo(newWorkingTime);
        assertThat(capturedEvent.oldWorkingTime()).isEqualTo(oldWorkingTime);
        // The event also carries the exam's current schedule so a conducting student can refresh the countdown (#13071).
        var examDb = examRepository.findById(exam1.getId()).orElseThrow();
        assertThat(capturedEvent.newStartDate()).isEqualTo(examDb.getStartDate().toInstant());
        assertThat(capturedEvent.newEndDate()).isEqualTo(examDb.getEndDate().toInstant());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateWorkingTimeTestExamDoesNotCarrySchedule() throws Exception {
        // For a test exam the exam start/end dates are only the availability window, not the student's conduction window
        // (which is derived from their individual startedDate). A working time update must therefore NOT carry them, so
        // the client keeps recomputing the timer from the student's startedDate rather than the wrong exam start (#13071).
        int newWorkingTime = 180 * 60;
        testExam1.setVisibleDate(ZonedDateTime.now().minusMinutes(1));
        testExam1.setStartDate(ZonedDateTime.now().minusMinutes(1));
        testExam1.setEndDate(ZonedDateTime.now().plusHours(1));
        testExam1 = examRepository.save(testExam1);

        StudentExamDTO result = request.patchWithResponseBody(
                "/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExamForTestExam1.getId() + "/working-time", newWorkingTime,
                StudentExamDTO.class, HttpStatus.OK);
        assertThat(result.workingTime()).isEqualTo(newWorkingTime);

        var capturedEvent = (WorkingTimeUpdateEventDTO) captureExamLiveEventForId(studentExamForTestExam1.getId(), false);
        assertThat(capturedEvent.newWorkingTime()).isEqualTo(newWorkingTime);
        // Even though the (test) exam has start/end dates set above, the schedule must be omitted for test exams.
        assertThat(capturedEvent.newStartDate()).isNull();
        assertThat(capturedEvent.newEndDate()).isNull();
    }

    private ExamLiveEventBaseDTO captureExamLiveEventForId(Long studentExamOrExamId, boolean examWide) {
        // Create an ArgumentCaptor for the WebSocket message
        ArgumentCaptor<ExamLiveEventBaseDTO> websocketEventCaptor = ArgumentCaptor.forClass(ExamLiveEventBaseDTO.class);

        // Verify that the sendMessage method was called with the expected WebSocket event
        var expectedTopic = examWide ? "/topic/exam-participation/exam/" + studentExamOrExamId + "/events"
                : "/topic/exam-participation/studentExam/" + studentExamOrExamId + "/events";
        verify(websocketMessagingService, timeout(2000)).sendMessage(eq(expectedTopic), websocketEventCaptor.capture());

        // Get the captured WebSocket event
        return websocketEventCaptor.getValue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExamAnnouncementSent() throws Exception {
        exam1.setVisibleDate(ZonedDateTime.now().minusMinutes(1));
        exam1 = examRepository.save(exam1);

        var testMessage = "Test message";
        var result = request.postWithPlainStringResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/announcements", testMessage,
                ExamWideAnnouncementEventDTO.class, HttpStatus.OK);

        assertThat(result.id()).isGreaterThan(0L);
        assertThat(result.text()).isEqualTo(testMessage);
        assertThat(result.createdDate()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));

        var event = captureExamLiveEventForId(exam1.getId(), true);
        assertThat(event).isEqualTo(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExamsCanNotBeSentBeforeVisibleDate() throws Exception {
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(1));
        exam1 = examRepository.save(exam1);

        var testMessage = "Test message";
        request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/announcements", testMessage, ExamWideAnnouncementEventDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testExamAttendanceCheck() throws Exception {
        exam1.setVisibleDate(ZonedDateTime.now().minusMinutes(1));
        exam1 = examRepository.save(exam1);

        var testMessage = "Test message";
        var result = request.postWithPlainStringResponseBody(
                "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + studentExam1.getUser().getLogin() + "/attendance-check", testMessage,
                ExamAttendanceCheckEventDTO.class, HttpStatus.OK);

        assertThat(result.id()).isGreaterThan(0L);
        assertThat(result.text()).isEqualTo(testMessage);
        assertThat(result.createdDate()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testExamsCanNotBeSentBeforeVisibleDateForAttendance() throws Exception {
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(1));
        exam1 = examRepository.save(exam1);

        var testMessage = "Test message";
        request.postWithPlainStringResponseBody(
                "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + studentExam1.getUser().getLogin() + "/attendance-check", testMessage,
                ExamAttendanceCheckEventDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testExamAttendanceCheckForbidden() throws Exception {
        exam1.setVisibleDate(ZonedDateTime.now().minusMinutes(1));
        exam1 = examRepository.save(exam1);

        var testMessage = "Test message";
        request.postWithPlainStringResponseBody(
                "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + studentExam1.getUser().getLogin() + "/attendance-check", testMessage,
                ExamAttendanceCheckEventDTO.class, HttpStatus.FORBIDDEN);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitStudentExam_alreadySubmitted() throws Exception {
        // Set up an exercise
        exam1 = examUtilService.addExerciseGroupsAndExercisesToExam(exam1, false);
        var exercise = exam1.getExerciseGroups().getFirst().getExercises().iterator().next();
        var participation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise, studentExam1.getUser());
        var submission = ParticipationFactory.generateTextSubmission("Test1", Language.ENGLISH, true);
        studentExam1.addExercise(exercise);
        exercise.addParticipation(participation);
        participation.addSubmission(submission);
        studentParticipationRepository.save(participation);
        submissionRepository.save(submission);
        exerciseRepository.save(exercise);
        studentExamRepository.save(studentExam1);

        // Change our submission
        submission.setText("Test2");
        submission.setSubmitted(false);

        // The client-supplied submitted flag is no longer read (it is dropped from the DTO): the submitted state is
        // derived from the database. A client that claims submitted=true while its DB copy is not yet submitted therefore
        // does NOT short-circuit — the exam is legitimately submitted and the change is saved.
        studentExam1.setSubmitted(true);
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.OK, null);
        assertStudentExam1HasSingleTextSubmissionWithTextAndIsSubmitted("Test2", true);

        // Change submission again
        submission.setText("Test3");
        submission.setSubmitted(false);

        // Subsequent calls are ignored because the DATABASE now marks the exam as submitted (the idempotent double-submit
        // guard), regardless of the client-supplied flag, and still return OK without persisting the new change.
        studentExam1.setSubmitted(false);
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.OK, null);
        assertStudentExam1HasSingleTextSubmissionWithTextAndIsSubmitted("Test2", true);
    }

    private void assertStudentExam1HasSingleTextSubmissionWithTextAndIsSubmitted(String content, Boolean submitted) {
        var fromDB = studentExamRepository.findWithExercisesParticipationsSubmissionsById(studentExam1.getId(), false).orElseThrow();
        assertThat(fromDB.isSubmitted()).isEqualTo(submitted);
        assertThat(fromDB.getExercises().getFirst().getStudentParticipations().size()).isEqualTo(1);
        assertThat(fromDB.getExercises().getFirst().getStudentParticipations().iterator().next().getSubmissions().size()).isEqualTo(1);
        assertThat(((TextSubmission) fromDB.getExercises().getFirst().getStudentParticipations().iterator().next().findLatestSubmission().orElseThrow()).getText())
                .isEqualTo(content);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitStudentExam_notInTime() throws Exception {
        studentExam1.setSubmitted(false);
        studentExamRepository.save(studentExam1);
        // Forbidden because user tried to submit before start
        exam1.setStartDate(ZonedDateTime.now().plusHours(1));
        examRepository.save(exam1);
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.FORBIDDEN);
        // Forbidden because user tried to submit after end
        exam1.setStartDate(ZonedDateTime.now().minusHours(5));
        examRepository.save(exam1);
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitStudentExam_manipulatedUserInBodyIsIgnored() throws Exception {
        studentExam1.setSubmitted(false);
        studentExamRepository.save(studentExam1);
        // The submit body no longer carries a user field: ownership is derived from the persisted student exam (owned by
        // student1, the authenticated user), not from a client claim. A body that still claims a different user (a stale
        // full-entity body from before the DTO rollout) is deserialized ignoring that field, so the request succeeds and
        // the DB ownership stays student1. Cross-user submission is rejected by DB truth in testSubmitExamOtherUser_forbidden.
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        studentExam1.setUser(student2);
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.OK, null);

        studentExam1 = studentExamRepository.findByIdElseThrow(studentExam1.getId());
        assertThat(studentExam1.getUser()).isEqualTo(student1);
        assertThat(studentExam1.isSubmitted()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitStudentExam() throws Exception {
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.OK, null);
        StudentExam submittedStudentExam = studentExamRepository.findById(studentExam1.getId()).orElseThrow();
        // Ensure that student exam has been marked as submitted
        assertThat(submittedStudentExam.isSubmitted()).isTrue();
        // Ensure that student exam has been set
        assertThat(submittedStudentExam.getSubmissionDate()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitStudentExam_testExamTriggersBuilds() throws Exception {
        ExamFactory.generateExerciseGroup(true, testExam1);
        testExam1 = examRepository.save(testExam1);
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToExam(testExam1, 0);

        studentExamForTestExam1.addExercise(programmingExercise);
        studentExamForTestExam1 = studentExamRepository.save(studentExamForTestExam1);
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        ProgrammingSubmission submission = new ProgrammingSubmission();
        programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");

        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/submit", studentExamForTestExam1, HttpStatus.OK, null);

        StudentExam submittedStudentExam = studentExamRepository.findById(studentExamForTestExam1.getId()).orElseThrow();
        assertThat(submittedStudentExam.isSubmitted()).isTrue();

        verify(programmingTriggerService, timeout(60000)).triggerBuildForParticipations(List.of(participation));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitExamOtherUser_forbidden() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();

        // make sure the exam is generally accessible
        exam2.setStartDate(ZonedDateTime.now().plusMinutes(4));
        exam2 = examRepository.save(exam2);

        userUtilService.changeUser(TEST_PREFIX + "student1");
        var studentExamResponse = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);
        studentExamResponse.setExercises(null);
        // use a different user
        userUtilService.changeUser(TEST_PREFIX + "student2");
        request.post("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamResponse, HttpStatus.FORBIDDEN);
        deleteExamWithInstructor(exam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testgetExamTooEarly_forbidden() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(true, true, 1).getFirst();

        userUtilService.changeUser(TEST_PREFIX + "student1");

        request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessUnsubmittedStudentExams() throws Exception {
        prepareStudentExamsForConduction(false, true, NUMBER_OF_STUDENTS);
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(10));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(8));
        exam2 = examRepository.save(exam2);

        // Verify that unsubmitted exercises exist before automatic assessment
        ExamChecklistDTO examChecklistDTO = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/statistics", HttpStatus.OK, ExamChecklistDTO.class);
        assertThat(examChecklistDTO.existsUnsubmittedExercises()).isTrue();

        request.postWithoutLocation("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams",
                Optional.empty(), HttpStatus.OK, null);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        Set<StudentExam> unsubmittedStudentExams = studentExamRepository.findAllUnsubmittedWithExercisesByExamId(exam2.getId());
        Map<User, List<Exercise>> exercisesOfUser = studentExamService.getExercisesOfUserMap(unsubmittedStudentExams);
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (final var studentParticipation : studentParticipations) {
                if (studentParticipation.findLatestSubmission().isPresent()) {
                    var result = studentParticipation.findLatestSubmission().get().getLatestResult();
                    assertThat(result).isNotNull();
                    assertThat(result.getScore()).isZero();
                    assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                    result = resultRepository.findByIdWithEagerFeedbacks(result.getId()).orElseThrow();
                    assertThat(result.getFeedbacks()).extracting(Feedback::getDetailText).containsExactly("You did not submit your exam");
                }
                else {
                    fail("StudentParticipation which is part of an unsubmitted StudentExam contains no submission or result after automatic assessment of unsubmitted student exams call.");
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessUnsubmittedStudentExamsForMultipleCorrectionRounds() throws Exception {
        prepareStudentExamsForConduction(false, true, NUMBER_OF_STUDENTS);
        exam2.setNumberOfCorrectionRoundsInExam(2);
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(10));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(8));
        exam2.setWorkingTime(2 * 60);
        exam2 = examRepository.save(exam2);

        request.postWithoutLocation("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams",
                Optional.empty(), HttpStatus.OK, null);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        Set<StudentExam> unsubmittedStudentExams = studentExamRepository.findAllUnsubmittedWithExercisesByExamId(exam2.getId());
        Map<User, List<Exercise>> exercisesOfUser = studentExamService.getExercisesOfUserMap(unsubmittedStudentExams);
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (final var studentParticipation : studentParticipations) {
                if (studentParticipation.findLatestSubmission().isPresent()) {
                    assertThat(studentParticipation.findLatestSubmission().get().getResults()).isNotNull().hasSize(exam2.getNumberOfCorrectionRoundsInExam());
                    for (var result : Objects.requireNonNull(studentParticipation.findLatestSubmission().get().getResults())) {
                        assertThat(result).isNotNull();
                        assertThat(result.getScore()).isZero();
                        assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                        result = resultRepository.findByIdWithEagerFeedbacks(result.getId()).orElseThrow();
                        assertThat(result.getFeedbacks()).extracting(Feedback::getDetailText).containsExactly("You did not submit your exam");
                    }
                }
                else {
                    fail("StudentParticipation which is part of an unsubmitted StudentExam contains no submission or result after automatic assessment of unsubmitted student exams call.");
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessEmptyExamSubmissions() throws Exception {
        final var studentExams = prepareStudentExamsForConduction(false, true, NUMBER_OF_STUDENTS);

        // submit student exam with empty submissions
        for (final var studentExam : studentExams) {
            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExamRepository.save(studentExam);
        }
        // this test should be after the end date of the exam
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(10));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(7));
        exam2.setWorkingTime(3 * 60);
        examRepository.save(exam2);

        request.postWithoutLocation("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams",
                Optional.empty(), HttpStatus.OK, null);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        Map<User, List<Exercise>> exercisesOfUser = studentExamService.getExercisesOfUserMap(new HashSet<>(studentExams));
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (final var studentParticipation : studentParticipations) {
                if (studentParticipation.findLatestSubmission().isPresent()) {
                    var result = studentParticipation.findLatestSubmission().get().getLatestResult();
                    assertThat(result).isNotNull();
                    assertThat(result.getScore()).isZero();
                    assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                    result = resultRepository.findByIdWithEagerFeedbacks(result.getId()).orElseThrow();
                    assertThat(result.getFeedbacks()).extracting(Feedback::getDetailText).containsExactly("Empty submission");
                }
                else {
                    fail("StudentParticipation which is part of an unsubmitted StudentExam contains no submission or result after automatic assessment of unsubmitted student exams call.");
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessEmptyExamSubmissionsForMultipleCorrectionRounds() throws Exception {
        final var studentExams = prepareStudentExamsForConduction(false, true, NUMBER_OF_STUDENTS);

        // submit student exam with empty submissions
        for (final var studentExam : studentExams) {
            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExamRepository.save(studentExam);
        }
        // this test should be after the end date of the exam
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(10));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(7));
        exam2.setNumberOfCorrectionRoundsInExam(2);
        examRepository.save(exam2);

        request.postWithoutLocation("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams",
                Optional.empty(), HttpStatus.OK, null);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        Map<User, List<Exercise>> exercisesOfUser = studentExamService.getExercisesOfUserMap(new HashSet<>(studentExams));
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (final var studentParticipation : studentParticipations) {
                if (studentParticipation.findLatestSubmission().isPresent()) {
                    assertThat(studentParticipation.findLatestSubmission().get().getResults()).isNotNull().hasSize(exam2.getNumberOfCorrectionRoundsInExam());
                    for (var result : Objects.requireNonNull(studentParticipation.findLatestSubmission().get().getResults())) {
                        assertThat(result).isNotNull();
                        assertThat(result.getScore()).isZero();
                        assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                        result = resultRepository.findByIdWithEagerFeedbacks(result.getId()).orElseThrow();
                        assertThat(result.getFeedbacks()).extracting(Feedback::getDetailText).containsExactly("Empty submission");
                    }
                }
                else {
                    fail("StudentParticipation which is part of an unsubmitted StudentExam contains no submission or result after automatic assessment of unsubmitted student exams call.");
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessUnsubmittedStudentExams_forbidden() throws Exception {
        prepareStudentExamsForConduction(false, true, 1);
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(1));
        exam2 = examRepository.save(exam2);

        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        request.postWithoutLocation("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams", null,
                HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessUnsubmittedStudentExams_badRequest() throws Exception {
        prepareStudentExamsForConduction(false, true, 1);
        exam2 = examRepository.save(exam2);

        request.postWithoutLocation("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams", null,
                HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessExamWithSubmissionResult() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();

        // this test should be after the end date of the exam
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(1));
        examRepository.save(exam2);

        userUtilService.changeUser(studentExam.getUser().getLogin());
        var studentExamResponse = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);
        for (var exercise : studentExamResponse.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            final var submission = createSubmission(exercise);
            if (submission != null) {
                Result result = new Result();
                result.setExerciseId(exercise.getId());
                submission.addResult(result);
                Set<Submission> submissions = new HashSet<>();
                submissions.add(submission);
                participation.setSubmissions(submissions);
            }
        }

        request.postWithoutResponseBody("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamResponse, HttpStatus.OK);

        // check that the result was not injected and that the student exam was still submitted correctly

        var studentExamDatabase = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);
        assertThat(studentExamDatabase.isSubmitted()).isTrue();
        assertThat(studentExamDatabase.getSubmissionDate()).isNotNull();
        for (var exercise : studentExamDatabase.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            var iterator = participation.getSubmissions().iterator();
            if (iterator.hasNext()) {
                var submission = iterator.next();
                assertThat(submission.getLatestResult()).isNull();
            }
        }
        deleteExamWithInstructor(exam1);
    }

    private static Submission createSubmission(Exercise exercise) {
        return switch (exercise) {
            case ProgrammingExercise ignored -> new ProgrammingSubmission();
            case TextExercise ignored -> new TextSubmission();
            case ModelingExercise ignored -> new ModelingSubmission();
            case QuizExercise ignored -> new QuizSubmission();
            case null, default -> null;
        };
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitStudentExam_early() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();

        userUtilService.changeUser(studentExam.getUser().getLogin());
        var studentExamResponse = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);
        final List<ProgrammingExercise> exercisesToBeLocked = new ArrayList<>();
        final List<ProgrammingExerciseStudentParticipation> studentProgrammingParticipations = new ArrayList<>();

        for (var exercise : studentExamResponse.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                studentProgrammingParticipations.add((ProgrammingExerciseStudentParticipation) participation);
                exercisesToBeLocked.add(programmingExercise);
            }
        }

        // submit early
        request.postWithoutResponseBody("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamResponse, HttpStatus.OK);
        var submittedStudentExam = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExamResponse.getId() + "/summary",
                HttpStatus.OK, StudentExam.class);
        assertThat(submittedStudentExam.isSubmitted()).isTrue();
        assertThat(submittedStudentExam.getSubmissionDate()).isNotNull();

        // assert that all repositories of programming exercises have been locked
        assertThat(exercisesToBeLocked).hasSameSizeAs(studentProgrammingParticipations);
        deleteExamWithInstructor(exam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStudentExamForSummary_examSummaryPublicationDate() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();

        // configure a submission-overview publication date far in the future before the student conducts the exam
        exam2.setPublishResultsDate(null);
        exam2.setExamSummaryPublicationDate(ZonedDateTime.now().plusDays(1));
        exam2 = examRepository.save(exam2);

        final String conductionUrl = "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction";
        final String summaryUrl = "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/summary";

        userUtilService.changeUser(studentExam.getUser().getLogin());
        // a student who has NOT submitted yet must still be able to fetch the conduction even though the summary is not published yet (the gate must not break ongoing exams)
        var studentExamResponse = request.get(conductionUrl, HttpStatus.OK, StudentExam.class);
        // submit early so the summary would generally be accessible (it only requires the student exam to be submitted)
        request.postWithoutResponseBody("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamResponse, HttpStatus.OK);

        // 1) summary publication date in the future: the submitted student may NOT access the summary yet, and must not be able to re-fetch the exam content via conduction either
        request.get(summaryUrl, HttpStatus.FORBIDDEN, StudentExam.class);
        request.get(conductionUrl, HttpStatus.FORBIDDEN, StudentExam.class);

        // 2) summary publication date in the past: the student may access the summary
        exam2.setExamSummaryPublicationDate(ZonedDateTime.now().minusMinutes(1));
        exam2 = examRepository.save(exam2);
        var summary = request.get(summaryUrl, HttpStatus.OK, StudentExam.class);
        assertThat(summary.isSubmitted()).isTrue();

        // 3) summary publication date still in the future, but results are already published: the summary is available as a safeguard
        exam2.setExamSummaryPublicationDate(ZonedDateTime.now().plusDays(1));
        exam2.setPublishResultsDate(ZonedDateTime.now().minusMinutes(1));
        exam2 = examRepository.save(exam2);
        request.get(summaryUrl, HttpStatus.OK, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitStudentExam_realistic() throws Exception {
        List<StudentExam> studentExams = prepareStudentExamsForConduction(false, true, NUMBER_OF_STUDENTS);

        List<StudentExam> studentExamsAfterStart = new ArrayList<>();
        for (var studentExam : studentExams) {
            userUtilService.changeUser(studentExam.getUser().getLogin());
            var studentExamResponse = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                    HttpStatus.OK, StudentExam.class);

            for (var exercise : studentExamResponse.getExercises()) {
                saveSubmissionByExerciseType(exercise);
            }

            studentExamsAfterStart.add(studentExamResponse);
        }

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(1));
        exam2 = examRepository.save(exam2);

        jenkinsRequestMockProvider.reset();

        for (var studentExam : studentExamsAfterStart) {
            for (var exercise : studentExam.getExercises()) {
                var participation = exercise.getStudentParticipations().iterator().next();
                if (exercise instanceof ProgrammingExercise programmingExercise) {
                    // do another programming submission to check if the StudentExam after submit contains the new commit hash
                    var latestCommitHash = commitNewFileToParticipationRepo((ProgrammingExerciseStudentParticipation) participation);
                    programmingUpdatedCommitHashes.put(participation.getId(), latestCommitHash);
                    jenkinsRequestMockProvider.reset();
                    jenkinsRequestMockProvider.mockTriggerBuild(programmingExercise.getProjectKey(), ((ProgrammingExerciseStudentParticipation) participation).getBuildPlanId(),
                            false);
                    userUtilService.changeUser(studentExam.getUser().getLogin());
                    request.postWithoutLocation("/api/programming/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());
                    // do not add programming submission to participation, because we want to simulate, that the latest submission is not present
                }
            }
        }

        List<StudentExam> studentExamsAfterFinish = new ArrayList<>();
        for (var studentExamAfterStart : studentExamsAfterStart) {
            userUtilService.changeUser(studentExamAfterStart.getUser().getLogin());
            request.postWithoutResponseBody("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamAfterStart, HttpStatus.OK);
            var studentExamFinished = request.get(
                    "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExamAfterStart.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);
            // Check that all text/quiz/modeling submissions were saved and that submitted versions were created
            for (var exercise : studentExamFinished.getExercises()) {
                var participationAfterFinish = exercise.getStudentParticipations().iterator().next();
                var submissionAfterFinish = participationAfterFinish.getSubmissions().iterator().next();

                var exerciseAfterStart = studentExamAfterStart.getExercises().stream().filter(exAfterStart -> exAfterStart.getId().equals(exercise.getId())).findFirst()
                        .orElseThrow();
                var participationAfterStart = exerciseAfterStart.getStudentParticipations().iterator().next();
                var submissionAfterStart = participationAfterStart.getSubmissions().iterator().next();

                switch (exercise) {
                    case ModelingExercise ignored -> {
                        var modelingSubmissionAfterFinish = (ModelingSubmission) submissionAfterFinish;
                        var modelingSubmissionAfterStart = (ModelingSubmission) submissionAfterStart;
                        assertThat(modelingSubmissionAfterFinish).isEqualTo(modelingSubmissionAfterStart);
                        assertVersionedSubmission(modelingSubmissionAfterStart);
                        assertVersionedSubmission(modelingSubmissionAfterFinish);
                    }
                    case TextExercise ignored -> {
                        var textSubmissionAfterFinish = (TextSubmission) submissionAfterFinish;
                        var textSubmissionAfterStart = (TextSubmission) submissionAfterStart;
                        assertThat(textSubmissionAfterFinish).isEqualTo(textSubmissionAfterStart);
                        assertVersionedSubmission(textSubmissionAfterStart);
                        assertVersionedSubmission(textSubmissionAfterFinish);
                    }
                    case QuizExercise ignored -> {
                        var quizSubmissionAfterFinish = (QuizSubmission) submissionAfterFinish;
                        var quizSubmissionAfterStart = (QuizSubmission) submissionAfterStart;
                        assertThat(quizSubmissionAfterFinish).isEqualTo(quizSubmissionAfterStart);
                        assertVersionedSubmission(quizSubmissionAfterStart);
                        assertVersionedSubmission(quizSubmissionAfterFinish);
                    }
                    case ProgrammingExercise ignored -> {
                        var programmingSubmissionAfterStart = (ProgrammingSubmission) submissionAfterStart;
                        var programmingSubmissionAfterFinish = (ProgrammingSubmission) submissionAfterFinish;
                        var participationId = participationAfterStart.getId();
                        var expectedInitialHash = programmingInitialCommitHashes.get(participationId);
                        assertThat(expectedInitialHash).as("initial commit hash recorded for participation %s", participationId).isNotNull();
                        assertThat(programmingSubmissionAfterStart.getCommitHash()).isEqualTo(expectedInitialHash);
                        var expectedUpdatedHash = programmingUpdatedCommitHashes.get(participationAfterFinish.getId());
                        assertThat(expectedUpdatedHash).as("updated commit hash recorded for participation %s", participationAfterFinish.getId()).isNotNull();
                        assertThat(programmingSubmissionAfterFinish.getCommitHash()).isEqualTo(expectedUpdatedHash);
                    }
                    default -> {
                    }
                }

            }

            studentExamsAfterFinish.add(studentExamFinished);

            assertThat(studentExamFinished.isSubmitted()).isTrue();
            assertThat(studentExamFinished.getSubmissionDate()).isNotNull();
        }
        assertThat(studentExamsAfterFinish).hasSize(studentExamsAfterStart.size());

        deleteExamWithInstructor(exam1);
    }

    /**
     * Wire-contract guard for the exam conduction -> hand-in round trip.
     * <p>
     * The exam-taking client fetches the conduction response, keeps it typed as the full {@code StudentExam} model, and
     * later builds the hand-in body from it via {@code toSubmitStudentExamDTO}, which reads exactly
     * {@code exercises[].id}, {@code studentParticipations[].id} and {@code submissions[].{id, submissionExerciseType}}
     * plus the per-type content (text+language / model+explanationText / submittedAnswers). This test reproduces that
     * exact walk against the <em>raw JSON</em> the conduction endpoint returns: it asserts every id path the client
     * mapper depends on is present on the wire, then builds the slim submit body from those wire ids (injecting the
     * last-second answers a student would enter), posts it, and fresh-queries the database to prove the text, modeling
     * and quiz content survived the round trip.
     * <p>
     * Unlike the other submit tests, this one never echoes the full conduction entity back to the submit endpoint and
     * never saves through the dedicated per-exercise submission endpoints — the content reaches the server only through
     * the slim body assembled from the wire. That makes it the regression guard for the conduction-response DTO
     * migration: if a migrated conduction projection drops any id path the client mapper reads, the slim body can no
     * longer be assembled (the id assertions fail) or the content no longer round-trips.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testConductionWireToSlimSubmitBodyRoundTripPersistsContent() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();
        User student = studentExam.getUser();
        userUtilService.changeUser(student.getLogin());

        final HttpHeaders headers = getHttpHeadersForExamSession();
        // 1. Fetch the conduction response as the raw JSON the client receives.
        JsonNode conductionWire = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, JsonNode.class, headers);
        assertThat(conductionWire.hasNonNull("id")).as("conduction wire carries the student exam id").isTrue();
        assertThat(conductionWire.get("exercises")).as("conduction wire carries exercises").isNotNull();

        final String expectedText = "Round-trip text " + UUID.randomUUID();
        final String expectedModel = "{\"element\":\"round-trip model\"}";
        final String expectedExplanation = "Round-trip explanation";

        // 2. Build the slim submit body exactly as the client's toSubmitStudentExamDTO mapper does, reading only the wire
        // ids the mapper reads and injecting the per-type content a student would have entered.
        JsonMapper mapper = request.getObjectMapper();
        ObjectNode submitBody = mapper.createObjectNode();
        submitBody.put("id", conductionWire.get("id").asLong());
        ArrayNode submitExercises = submitBody.putArray("exercises");

        Long textSubmissionId = null;
        Long modelingSubmissionId = null;
        Long quizSubmissionId = null;
        Long mcQuestionId = null;
        Long mcOptionId = null;

        for (JsonNode exercise : conductionWire.get("exercises")) {
            assertThat(exercise.hasNonNull("id")).as("conduction wire exercise carries an id").isTrue();
            ObjectNode slimExercise = submitExercises.addObject();
            slimExercise.put("id", exercise.get("id").asLong());
            ArrayNode slimParticipations = slimExercise.putArray("studentParticipations");

            JsonNode wireParticipations = exercise.get("studentParticipations");
            if (wireParticipations == null) {
                continue;
            }
            for (JsonNode participation : wireParticipations) {
                assertThat(participation.hasNonNull("id")).as("conduction wire participation carries an id").isTrue();
                ObjectNode slimParticipation = slimParticipations.addObject();
                slimParticipation.put("id", participation.get("id").asLong());
                ArrayNode slimSubmissions = slimParticipation.putArray("submissions");

                JsonNode wireSubmissions = participation.get("submissions");
                if (wireSubmissions == null) {
                    continue;
                }
                for (JsonNode submission : wireSubmissions) {
                    String type = submission.path("submissionExerciseType").asString(null);
                    ObjectNode slimSubmission = slimSubmissions.addObject();
                    if (submission.hasNonNull("id")) {
                        slimSubmission.put("id", submission.get("id").asLong());
                    }
                    if (type != null) {
                        slimSubmission.put("submissionExerciseType", type);
                    }
                    if (type == null) {
                        continue;
                    }
                    switch (type) {
                        case "text" -> {
                            assertThat(submission.hasNonNull("id")).as("conduction wire text submission carries an id").isTrue();
                            textSubmissionId = submission.get("id").asLong();
                            slimSubmission.put("text", expectedText);
                        }
                        case "modeling" -> {
                            assertThat(submission.hasNonNull("id")).as("conduction wire modeling submission carries an id").isTrue();
                            modelingSubmissionId = submission.get("id").asLong();
                            slimSubmission.put("model", expectedModel);
                            slimSubmission.put("explanationText", expectedExplanation);
                        }
                        case "quiz" -> {
                            assertThat(submission.hasNonNull("id")).as("conduction wire quiz submission carries an id").isTrue();
                            quizSubmissionId = submission.get("id").asLong();
                            JsonNode questions = exercise.get("quizQuestions");
                            assertThat(questions).as("conduction wire quiz exercise carries quizQuestions").isNotNull();
                            ArrayNode answers = slimSubmission.putArray("submittedAnswers");
                            for (JsonNode question : questions) {
                                if ("multiple-choice".equals(question.path("type").asString()) && mcQuestionId == null) {
                                    JsonNode options = question.get("answerOptions");
                                    assertThat(options).as("conduction wire MC question carries answerOptions").isNotNull();
                                    assertThat(options.isEmpty()).as("conduction wire MC question exposes at least one option").isFalse();
                                    mcQuestionId = question.get("id").asLong();
                                    mcOptionId = options.get(0).get("id").asLong();
                                    ObjectNode answer = answers.addObject();
                                    answer.put("type", "multiple-choice");
                                    answer.putObject("quizQuestion").put("id", mcQuestionId);
                                    answer.putArray("selectedOptions").addObject().put("id", mcOptionId);
                                }
                            }
                        }
                        default -> {
                            // programming / file-upload carry no content the hand-in persists
                        }
                    }
                }
            }
        }

        assertThat(textSubmissionId).as("conduction wire exposed a text submission").isNotNull();
        assertThat(quizSubmissionId).as("conduction wire exposed a quiz submission").isNotNull();
        assertThat(mcOptionId).as("conduction wire exposed an MC question with a selectable option").isNotNull();

        // 3. Post the slim body — the exact shape the client assembles from the conduction wire.
        request.postWithoutResponseBody("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", submitBody, HttpStatus.OK);

        // 4. Fresh-query the database and assert the last-second content survived the round trip.
        StudentExam persisted = studentExamRepository.findById(studentExam.getId()).orElseThrow();
        assertThat(persisted.isSubmitted()).as("student exam is marked submitted after hand-in").isTrue();

        TextSubmission persistedText = (TextSubmission) submissionRepository.findById(textSubmissionId).orElseThrow();
        assertThat(persistedText.getText()).as("text hand-in content persisted from the slim body").isEqualTo(expectedText);

        if (modelingSubmissionId != null) {
            ModelingSubmission persistedModel = (ModelingSubmission) submissionRepository.findById(modelingSubmissionId).orElseThrow();
            assertThat(persistedModel.getModel()).as("modeling hand-in content persisted from the slim body").isEqualTo(expectedModel);
            assertThat(persistedModel.getExplanationText()).as("modeling explanation persisted from the slim body").isEqualTo(expectedExplanation);
        }

        QuizSubmission persistedQuiz = quizSubmissionTestRepository.findWithEagerSubmittedAnswersById(quizSubmissionId);
        assertThat(persistedQuiz.getSubmittedAnswers()).as("quiz hand-in answers persisted from the slim body").isNotEmpty();
        var mcAnswer = persistedQuiz.getSubmittedAnswers().stream().filter(answer -> answer instanceof MultipleChoiceSubmittedAnswer)
                .map(answer -> (MultipleChoiceSubmittedAnswer) answer).findFirst().orElseThrow();
        final Long expectedOptionId = mcOptionId;
        assertThat(mcAnswer.getSelectedOptions()).as("MC selected option persisted from the slim body").anyMatch(option -> expectedOptionId.equals(option.getId()));

        deleteExamWithInstructor(exam1);
    }

    private void saveSubmissionByExerciseType(Exercise exercise) throws Exception {
        var participation = exercise.getStudentParticipations().iterator().next();
        if (exercise instanceof ProgrammingExercise programmingExercise) {
            jenkinsRequestMockProvider.reset();
            jenkinsRequestMockProvider.mockTriggerBuild(programmingExercise.getProjectKey(), ((ProgrammingExerciseStudentParticipation) participation).getBuildPlanId(), false);
            request.postWithoutLocation("/api/programming/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());
            Optional<ProgrammingSubmission> programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(participation.getId());
            assertThat(programmingSubmission).isPresent();
            assertSensitiveInformationWasFilteredProgrammingExercise(programmingExercise);
            participation.getSubmissions().add(programmingSubmission.get());
            programmingInitialCommitHashes.put(participation.getId(), programmingSubmission.get().getCommitHash());
            return;
        }
        var submission = participation.getSubmissions().iterator().next();
        switch (exercise) {
            case ModelingExercise modelingExercise -> {
                // check that the submission was saved and that a submitted version was created
                String newModel = "This is a new model";
                String newExplanation = "This is an explanation";
                var modelingSubmission = (ModelingSubmission) submission;
                modelingSubmission.setModel(newModel);
                modelingSubmission.setExplanationText(newExplanation);
                request.put("/api/modeling/exercises/" + exercise.getId() + "/modeling-submissions", modelingSubmission, HttpStatus.OK);
                var savedModelingSubmission = request.get(
                        "/api/modeling/participations/" + exercise.getStudentParticipations().iterator().next().getId() + "/latest-modeling-submission", HttpStatus.OK,
                        ModelingSubmission.class);
                // check that the submission was saved
                assertThat(newModel).isEqualTo(savedModelingSubmission.getModel());
                assertSensitiveInformationWasFilteredModelingExercise(modelingExercise);
                // check that a submitted version was created
                assertVersionedSubmission(modelingSubmission);
            }
            case TextExercise textExercise -> {
                var textSubmission = (TextSubmission) submission;
                final var newText = "New Text";
                textSubmission.setText(newText);
                request.put("/api/text/exercises/" + exercise.getId() + "/text-submissions", toRequestDTO(textSubmission), HttpStatus.OK);
                var savedTextSubmission = (TextSubmission) submissionRepository.findById(textSubmission.getId()).orElseThrow();
                // check that the submission was saved
                assertThat(newText).isEqualTo(savedTextSubmission.getText());
                // check that a submitted version was created
                assertVersionedSubmission(textSubmission);
                assertSensitiveInformationWasFilteredTextExercise(textExercise);
            }
            case QuizExercise quizExercise -> {
                assertThat(quizExercise.getQuizQuestions()).hasSize(3);
                quizExercise.getQuizQuestions().forEach(quizQuestion -> {
                    assertThat(quizQuestion.getQuizQuestionStatistic()).isNull();
                    assertThat(quizQuestion.getExplanation()).isNull();
                    switch (quizQuestion) {
                        case MultipleChoiceQuestion mcQuestion -> mcQuestion.getAnswerOptions().forEach(answerOption -> {
                            assertThat(answerOption.getExplanation()).isNull();
                            assertThat(answerOption.isIsCorrect()).isNull();
                        });
                        case DragAndDropQuestion dndQuestion -> assertThat(dndQuestion.getCorrectMappings()).isNullOrEmpty();
                        case ShortAnswerQuestion saQuestion -> assertThat(saQuestion.getCorrectMappings()).isNullOrEmpty();
                        default -> {
                        }
                    }
                });

                submitQuizInExam(quizExercise, (QuizSubmission) submission);
            }
            case FileUploadExercise fileUploadExercise -> assertSensitiveInformationWasFilteredFileUploadExercise(fileUploadExercise);
            default -> {
            }
        }
    }

    private String commitNewFileToParticipationRepo(ProgrammingExerciseStudentParticipation participation) throws Exception {
        LocalVCRepositoryUri repositoryUri = new LocalVCRepositoryUri(participation.getRepositoryUri());
        Path cloneDirectory = tempFileUtilService.createTempDirectory(tempPath, "student-repo-" + participation.getId());
        Path remotePath = repositoryUri.getLocalRepositoryPath(localVCBasePath);
        try (Git git = Git.cloneRepository().setURI(remotePath.toUri().toString()).setDirectory(cloneDirectory.toFile()).call()) {
            String fileName = "update-" + UUID.randomUUID() + ".txt";
            FileUtils.writeStringToFile(cloneDirectory.resolve(fileName).toFile(), "updated content", java.nio.charset.StandardCharsets.UTF_8);
            git.add().addFilepattern(fileName).call();
            RevCommit commit = de.tum.cit.aet.artemis.localvc.service.GitService.commit(git).setMessage("Add " + fileName).call();
            git.push().call();
            return commit.getId().getName();
        }
        finally {
            RepositoryExportTestUtil.safeDeleteDirectory(cloneDirectory);
        }
    }

    private void submitQuizInExam(QuizExercise quizExercise, QuizSubmission quizSubmission) throws Exception {
        // check that the submission was saved and that a submitted version was created
        int dndDragItemIndex = 1;
        int dndLocationIndex = 2;
        String shortAnswerText = "New Short Answer Text";
        int saSpotIndex = 1;
        int mcSelectedOptionIndex = 0;
        quizExercise.getQuizQuestions().forEach(quizQuestion -> {
            if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                var submittedAnswer = new DragAndDropSubmittedAnswer();
                DragAndDropMapping dndMapping = new DragAndDropMapping();
                dndMapping.setDragItemIndex(dndDragItemIndex);
                dndMapping.setDragItem(dragAndDropQuestion.getDragItems().get(dndDragItemIndex));
                dndMapping.setDropLocationIndex(dndLocationIndex);
                dndMapping.setDropLocation(dragAndDropQuestion.getDropLocations().get(dndLocationIndex));
                submittedAnswer.addMappings(dndMapping);
                submittedAnswer.setQuizQuestion(dragAndDropQuestion);
                quizSubmission.getSubmittedAnswers().add(submittedAnswer);
            }
            else if (quizQuestion instanceof ShortAnswerQuestion shortAnswerQuestion) {
                var submittedAnswer = new ShortAnswerSubmittedAnswer();
                ShortAnswerSubmittedText shortAnswerSubmittedText = new ShortAnswerSubmittedText();
                shortAnswerSubmittedText.setText(shortAnswerText);
                shortAnswerSubmittedText.setSpot(shortAnswerQuestion.getSpots().get(saSpotIndex));
                submittedAnswer.setQuizQuestion(shortAnswerQuestion);
                submittedAnswer.addSubmittedTexts(shortAnswerSubmittedText);
                quizSubmission.getSubmittedAnswers().add(submittedAnswer);
            }
            else if (quizQuestion instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
                var answerOptions = multipleChoiceQuestion.getAnswerOptions();
                var submittedAnswer = new MultipleChoiceSubmittedAnswer();
                submittedAnswer.addSelectedOptions(answerOptions.get(mcSelectedOptionIndex));
                submittedAnswer.setQuizQuestion(quizQuestion);
                quizSubmission.getSubmittedAnswers().add(submittedAnswer);
            }
        });
        QuizSubmission savedQuizSubmission = request.putWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/exam", quizSubmission, QuizSubmission.class,
                HttpStatus.OK);
        // check the submission
        assertThat(savedQuizSubmission.getSubmittedAnswers()).isNotNull().isNotEmpty();
        quizExercise.getQuizQuestions().forEach(quizQuestion -> {
            SubmittedAnswer submittedAnswer = savedQuizSubmission.getSubmittedAnswerForQuestion(quizQuestion);
            if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer answer) {
                assertThat(answer.getSelectedOptions()).isNotNull().isNotEmpty();
                assertThat(answer.getSelectedOptions().iterator().next()).isNotNull();
                assertThat(answer.getSelectedOptions().iterator().next()).isEqualTo(((MultipleChoiceQuestion) quizQuestion).getAnswerOptions().get(mcSelectedOptionIndex));
            }
            else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer answer) {
                assertThat(answer.getSubmittedTexts()).isNotNull().isNotEmpty();
                assertThat(answer.getSubmittedTexts().iterator().next()).isNotNull();
                assertThat(answer.getSubmittedTexts().iterator().next().getText()).isEqualTo(shortAnswerText);
                assertThat(answer.getSubmittedTexts().iterator().next().getSpot()).isEqualTo(((ShortAnswerQuestion) quizQuestion).getSpots().get(saSpotIndex));
            }
            else if (submittedAnswer instanceof DragAndDropSubmittedAnswer answer) {
                assertThat(answer.getMappings()).isNotNull().isNotEmpty();
                assertThat(answer.getMappings().iterator().next()).isNotNull();
                assertThat(answer.getMappings().iterator().next().getDragItem()).isEqualTo(((DragAndDropQuestion) quizQuestion).getDragItems().get(dndDragItemIndex));
                assertThat(answer.getMappings().iterator().next().getDropLocation()).isEqualTo(((DragAndDropQuestion) quizQuestion).getDropLocations().get(dndLocationIndex));
            }
        });
        assertVersionedSubmission(quizSubmission);
    }

    private static TextSubmissionRequestDTO toRequestDTO(TextSubmission submission) {
        return new TextSubmissionRequestDTO(submission.getId(), submission.getText(), submission.getLanguage(), submission.isSubmitted());
    }

    private void assertVersionedSubmission(Submission submission) {
        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());
        var versionedSubmission = submissionVersionRepository.findLatestVersion(submission.getId());
        assertThat(versionedSubmission).isPresent();
        switch (submission) {
            case TextSubmission textSubmission -> assertThat(textSubmission.getText()).isEqualTo(versionedSubmission.get().getContent());
            case ModelingSubmission modelingSubmission -> assertThat("Model: " + modelingSubmission.getModel() + "; Explanation: " + modelingSubmission.getExplanationText())
                    .isEqualTo(versionedSubmission.get().getContent());
            case FileUploadSubmission fileUploadSubmission -> assertThat(fileUploadSubmission.getFilePath()).isEqualTo(versionedSubmission.get().getContent());
            default -> {
                assertThat(submission).isInstanceOf(QuizSubmission.class);

                /*
                 * The version content captures the student's submitted answers for audit purposes. Compare its
                 * structure (one JSON entry per submitted answer, with the discriminator type) to the local
                 * submission view. Deep field-by-field comparison would be brittle: the version is serialized
                 * from the server-managed entity (which carries fields like exerciseId derived from the back-ref
                 * to QuizExercise), while the local view is deserialized from the student-facing API response
                 * (which omits those server-only fields). What matters here is that an entry exists per answer
                 * and the answer types line up; the score/feedback/answer-specific assertions live in the
                 * dedicated quiz tests.
                 */
                try {
                    var versionTree = objectMapper.readTree(versionedSubmission.get().getContent());
                    assertThat(versionTree.isArray()).as("version content must be a JSON array of submitted answers").isTrue();
                    var quizSubmission = (QuizSubmission) submission;
                    assertThat(versionTree.size()).as("version must contain one entry per submitted answer").isEqualTo(quizSubmission.getSubmittedAnswers().size());
                    Map<String, Long> versionedTypeCounts = new HashMap<>();
                    versionTree.forEach(node -> versionedTypeCounts.merge(node.path("quizQuestion").path("type").asString(), 1L, Long::sum));
                    Map<String, Long> submittedTypeCounts = quizSubmission.getSubmittedAnswers().stream().collect(Collectors.groupingBy(answer -> {
                        var question = answer.getQuizQuestion();
                        if (question instanceof MultipleChoiceQuestion) {
                            return "multiple-choice";
                        }
                        if (question instanceof DragAndDropQuestion) {
                            return "drag-and-drop";
                        }
                        if (question instanceof ShortAnswerQuestion) {
                            return "short-answer";
                        }
                        return "unknown";
                    }, Collectors.counting()));
                    assertThat(versionedTypeCounts).as("version must reference the same per-type count of question types as the submission").isEqualTo(submittedTypeCounts);
                }
                catch (JacksonException e) {
                    fail("Exception thrown while parsing versioned submission content", e);
                }
                assertThat(submission).isEqualTo(versionedSubmission.get().getSubmission());
            }
        }
    }

    /**
     * Removes the id fields from the JSON string, so that the comparison between the submission and the versioned submission is easier.
     *
     * @param jsonString the JSON string to remove the id fields from
     * @return the JSON string without the id fields
     */
    private String removeIdFieldsFromJSONString(String jsonString) {
        return jsonString.replaceAll(" +\"id\"\\s*:\\s*[0-9]+,\n", "");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testStudentExamSummaryAsStudentBeforePublishResults_doFilter() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();
        StudentExam studentExamWithSubmissions = addExamExerciseSubmissionsForUser(exam2, studentExam.getUser().getLogin(), studentExam);

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(1));
        exam2 = examRepository.save(exam2);

        // submitExam
        request.postWithoutResponseBody("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamWithSubmissions, HttpStatus.OK);
        var studentExamFinished = request.get(
                "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExamWithSubmissions.getId() + "/summary", HttpStatus.OK,
                StudentExam.class);

        // Add results to all exercise submissions
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        for (var exercise : studentExamFinished.getExercises()) {
            if (exercise instanceof QuizExercise) {
                continue;
            }

            Participation participation = exercise.getStudentParticipations().iterator().next();
            participation.setExercise(exercise);
            Optional<Submission> latestSubmission = participation.findLatestSubmission();

            participationUtilService.addResultToSubmission(participation, latestSubmission.orElseThrow());
        }
        // evaluate quizzes
        request.postWithoutLocation("/api/exam/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/student-exams/evaluate-quiz-exercises", null, HttpStatus.OK,
                new HttpHeaders());

        // user tries to access exam summary
        userUtilService.changeUser(studentExam.getUser().getLogin());
        var studentExamSummary = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);

        // check that all relevant information is visible to the student
        for (final var exercise : studentExamSummary.getExercises()) {
            assertThat(participationUtilService.getResultsForParticipation(exercise.getStudentParticipations().iterator().next())).isEmpty();
            assertThat(exercise.getGradingInstructions()).isNull();
            assertThat(exercise.getGradingCriteria()).isEmpty();

            if (exercise instanceof QuizExercise quizExercise) {
                assertThat(quizExercise.getQuizQuestions()).hasSize(3);
                QuizSubmission submission = (QuizSubmission) exercise.getStudentParticipations().iterator().next().getSubmissions().iterator().next();
                assertThat(submission.getScoreInPoints()).isNull();
                submission.getSubmittedAnswers().forEach(submittedAnswer -> {
                    assertThat(submittedAnswer.getScoreInPoints()).isNull();
                    QuizQuestion question = submittedAnswer.getQuizQuestion();
                    if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                        ((ShortAnswerSubmittedAnswer) submittedAnswer).getSubmittedTexts().forEach(submittedText -> assertThat(submittedText.isIsCorrect()).isNull());
                    }
                    if (question != null) {
                        assertThat(question.getExplanation()).isNull();
                        assertThat(question.getQuizQuestionStatistic()).isNull();
                        if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                            ((ShortAnswerSubmittedAnswer) submittedAnswer).getSubmittedTexts().forEach(submittedText -> assertThat(submittedText.isIsCorrect()).isNull());
                            assertThat(((ShortAnswerQuestion) question).getCorrectMappings()).isEmpty();
                            assertThat(((ShortAnswerQuestion) question).getSolutions()).isEmpty();
                        }
                        if (question instanceof DragAndDropQuestion) {
                            assertThat(((DragAndDropQuestion) question).getCorrectMappings()).isEmpty();
                        }
                        if (question instanceof ShortAnswerQuestion) {
                            assertThat(((ShortAnswerQuestion) question).getCorrectMappings()).isEmpty();
                            assertThat(((ShortAnswerQuestion) question).getSolutions()).isEmpty();
                        }
                        if (question instanceof MultipleChoiceQuestion) {
                            ((MultipleChoiceQuestion) question).getAnswerOptions().forEach(answerOption -> {
                                assertThat(answerOption.isIsCorrect()).isNull();
                                assertThat(answerOption.getExplanation()).isNull();
                            });
                        }
                    }
                });
            }
            else {
                var participation = exercise.getStudentParticipations().iterator().next();
                assertThat(participationUtilService.getResultsForParticipation(participation)).isEmpty();
                assertThat(participation.getSubmissions().iterator().next().getResults()).isEmpty();
            }
        }
        deleteExamWithInstructor(exam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testStudentExamSummaryAsStudentAfterPublishResults_dontFilter() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);

        // users tries to access exam summary after results are published
        userUtilService.changeUser(studentExam.getUser().getLogin());
        var studentExamSummary = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);

        // check that all relevant information is visible to the student
        for (final var exercise : studentExamSummary.getExercises()) {
            assertThat(participationUtilService.getResultsForParticipation(exercise.getStudentParticipations().iterator().next())).isNotEmpty();
            assertThat(exercise.getGradingInstructions()).isNull();
            assertThat(exercise.getGradingCriteria()).isEmpty();

            if (exercise instanceof QuizExercise quizExercise) {
                assertThat(quizExercise.getQuizQuestions()).hasSize(3);
                QuizSubmission submission = (QuizSubmission) exercise.getStudentParticipations().iterator().next().getSubmissions().iterator().next();
                assertThat(submission.getScoreInPoints()).isNotNull();
                submission.getSubmittedAnswers().forEach(submittedAnswer -> {
                    assertThat(submittedAnswer.getScoreInPoints()).isNotNull();
                    if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                        ((ShortAnswerSubmittedAnswer) submittedAnswer).getSubmittedTexts().forEach(submittedText -> assertThat(submittedText.isIsCorrect()).isNotNull());
                    }
                    QuizQuestion question = submittedAnswer.getQuizQuestion();
                    if (question != null) {
                        assertThat(question.getExplanation()).isNotNull();
                        assertThat(question.getQuizQuestionStatistic()).isNull();
                        if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                            ((ShortAnswerSubmittedAnswer) submittedAnswer).getSubmittedTexts().forEach(submittedText -> assertThat(submittedText.isIsCorrect()).isNotNull());
                            assertThat(((ShortAnswerQuestion) question).getCorrectMappings()).isNotEmpty();
                            assertThat(((ShortAnswerQuestion) question).getSolutions()).isNotEmpty();
                        }
                        if (question instanceof DragAndDropQuestion) {
                            assertThat(((DragAndDropQuestion) question).getCorrectMappings()).isNotEmpty();
                        }
                        if (question instanceof ShortAnswerQuestion) {
                            assertThat(((ShortAnswerQuestion) question).getCorrectMappings()).isNotEmpty();
                            assertThat(((ShortAnswerQuestion) question).getSolutions()).isNotEmpty();
                        }
                        if (question instanceof MultipleChoiceQuestion) {
                            ((MultipleChoiceQuestion) question).getAnswerOptions().forEach(answerOption -> {
                                assertThat(answerOption.isIsCorrect()).isNotNull();
                                assertThat(answerOption.getExplanation()).isNotNull();
                            });
                        }
                    }
                });
            }
            else {
                var participation = exercise.getStudentParticipations().iterator().next();
                Set<Result> results = participationUtilService.getResultsForParticipation(participation);
                assertThat(results).hasSize(1);
                var result = results.iterator().next();
                assertThat(result.getAssessor()).as("no sensitive inforation get leaked").isNull();
            }
        }
        deleteExamWithInstructor(exam1);
    }

    /**
     * Wire-contract guard for the SUMMARY path's exercise-level quiz questions once results are published.
     * <p>
     * After the publish-results date the quiz summary UI ({@code quiz-exam-summary}, rendered with
     * {@code showResult = resultsPublished}) reads the correct answers off the exercise-level quiz questions to show
     * which options were right/wrong. The pre-DTO wire stopped masking quizzes after the publish date, so it carried
     * these solutions; the DTO projection must do the same or the published student sees no right/wrong on the quiz
     * summary. This pins that the published {@code /summary} wire carries the multiple-choice {@code isCorrect} /
     * {@code explanation}, the drag-and-drop {@code correctMappings} and the short-answer {@code correctMappings} on the
     * exercise-level questions — the exact fields the summary UI needs. Uses a genuinely answered, published quiz
     * fixture, since a prior wire dump using a non-published / answer-less fixture is exactly how this regressed.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSummaryWireServesQuizSolutionsAfterPublishResults() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);

        userUtilService.changeUser(studentExam.getUser().getLogin());
        JsonNode summaryWire = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/summary", HttpStatus.OK,
                JsonNode.class);

        boolean sawQuiz = false;
        for (JsonNode exercise : summaryWire.get("exercises")) {
            JsonNode questions = exercise.get("quizQuestions");
            if (questions == null) {
                continue;
            }
            sawQuiz = true;
            assertThat(questions).hasSize(3);
            for (JsonNode question : questions) {
                switch (question.get("type").asString()) {
                    case "multiple-choice" -> {
                        assertThat(question.hasNonNull("explanation")).as("published summary MC question must carry its explanation").isTrue();
                        JsonNode options = question.get("answerOptions");
                        assertThat(options).as("published summary MC question keeps its answer options").isNotNull();
                        long correctOptions = 0;
                        boolean sawOptionExplanation = false;
                        for (JsonNode option : options) {
                            if (option.path("isCorrect").asBoolean(false)) {
                                correctOptions++;
                            }
                            sawOptionExplanation |= option.hasNonNull("explanation");
                        }
                        assertThat(correctOptions).as("published summary MC wire must reveal the correct answer option via isCorrect").isGreaterThanOrEqualTo(1);
                        assertThat(sawOptionExplanation).as("published summary MC options must carry their explanation").isTrue();
                    }
                    case "drag-and-drop" -> assertThat(question.path("correctMappings").isEmpty()).as("published summary DnD wire must carry correctMappings").isFalse();
                    case "short-answer" -> assertThat(question.path("correctMappings").isEmpty()).as("published summary SA wire must carry correctMappings").isFalse();
                    default -> {
                    }
                }
            }
        }
        assertThat(sawQuiz).as("published summary wire exposed a quiz exercise to check").isTrue();
        deleteExamWithInstructor(exam1);
    }

    /**
     * Security-sensitive counterpart to {@link #testSummaryWireServesQuizSolutionsAfterPublishResults()}: before the
     * publish-results date the {@code /summary} endpoint is reachable (it only requires the student exam to be
     * submitted), and the exercise-level quiz questions must stay solution-hidden exactly as during conduction. This
     * pins that the not-yet-published summary wire leaks neither the multiple-choice {@code isCorrect} /
     * {@code explanation} nor the drag-and-drop / short-answer {@code correctMappings} / {@code solutions}, so making
     * the summary publish-aware never regresses the pre-publish masking.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSummaryWireMasksQuizSolutionsBeforePublishResults() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();
        StudentExam studentExamWithSubmissions = addExamExerciseSubmissionsForUser(exam2, studentExam.getUser().getLogin(), studentExam);

        // move to a point where the individual student exam has ended but is still in the grace period, so the student can submit
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(1));
        exam2 = examRepository.save(exam2);

        // submit as the student (addExamExerciseSubmissionsForUser already switched to the student user); results are NOT published
        request.postWithoutResponseBody("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamWithSubmissions, HttpStatus.OK);

        JsonNode summaryWire = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExamWithSubmissions.getId() + "/summary",
                HttpStatus.OK, JsonNode.class);

        boolean sawQuiz = false;
        for (JsonNode exercise : summaryWire.get("exercises")) {
            JsonNode questions = exercise.get("quizQuestions");
            if (questions == null) {
                continue;
            }
            sawQuiz = true;
            assertThat(questions).hasSize(3);
            for (JsonNode question : questions) {
                assertThat(question.has("explanation")).as("unpublished summary quiz question must not leak explanation").isFalse();
                switch (question.get("type").asString()) {
                    case "multiple-choice" -> {
                        for (JsonNode option : question.get("answerOptions")) {
                            assertThat(option.has("isCorrect")).as("unpublished summary MC option must not leak isCorrect").isFalse();
                            assertThat(option.has("explanation")).as("unpublished summary MC option must not leak explanation").isFalse();
                        }
                    }
                    case "drag-and-drop" -> assertThat(question.path("correctMappings").isEmpty()).as("unpublished summary DnD must not leak correctMappings").isTrue();
                    case "short-answer" -> {
                        assertThat(question.path("correctMappings").isEmpty()).as("unpublished summary SA must not leak correctMappings").isTrue();
                        assertThat(question.path("solutions").isEmpty()).as("unpublished summary SA must not leak solutions").isTrue();
                    }
                    default -> {
                    }
                }
            }
        }
        assertThat(sawQuiz).as("unpublished summary wire exposed a quiz exercise to mask-check").isTrue();

        // The detail projection follows the same publish gate (its summary consumers only render results once
        // published), so the not-yet-published instructor detail wire must stay solution-hidden too.
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        JsonNode detailWire = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExamWithSubmissions.getId(),
                HttpStatus.OK, JsonNode.class);
        assertExerciseWireMasksQuizSolutions(detailWire.get("studentExam").get("exercises"), "unpublished instructor detail");
        deleteExamWithInstructor(exam1);
    }

    /**
     * Test-run counterpart to {@link #testSummaryWireServesQuizSolutionsAfterPublishResults()}: test runs are exempt
     * from quiz masking (the server never strips their quiz solutions and the client's {@code resultsArePublished}
     * treats test runs as published immediately), so the test-run summary wire must carry the quiz solutions even
     * while the real exam's results are NOT yet published. Gating the summary projection only on
     * {@code areResultsPublishedYet()} regressed exactly this: an instructor finishing a test run saw no quiz
     * right/wrong on its summary.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTestRunSummaryWireServesQuizSolutionsBeforePublishResults() throws Exception {
        var testRun = createTestRun();
        // pin the interesting state: the real exam's results are NOT published, only the test-run exemption applies
        testRunExam.setPublishResultsDate(ZonedDateTime.now().plusDays(1));
        testRunExam = examRepository.save(testRunExam);

        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        var testRunResponse = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/test-runs/" + testRun.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);
        request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/student-exams/submit", testRunResponse, HttpStatus.OK, null);

        JsonNode summaryWire = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/student-exams/" + testRun.getId() + "/summary",
                HttpStatus.OK, JsonNode.class);

        assertExerciseWireCarriesQuizSolutions(summaryWire.get("exercises"), "unpublished test-run summary");

        // The instructor test-run summary route resolves its student exam via the DETAIL endpoint (getStudentExam ->
        // StudentExamWithGradeDTO.studentExam), so the detail projection must apply the same test-run exemption.
        JsonNode detailWire = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/student-exams/" + testRun.getId(), HttpStatus.OK,
                JsonNode.class);
        assertExerciseWireCarriesQuizSolutions(detailWire.get("studentExam").get("exercises"), "unpublished test-run detail");
    }

    /**
     * Regression for the reconstructed-participation merge. {@link de.tum.cit.aet.artemis.exam.service.StudentExamSubmitMapper}
     * hands {@code ExamQuizService.evaluateQuizParticipationsForTestRunAndTestExam} a participation carrying only the
     * fields the submit path needs (id, participant, exercise, testRun, INITIALIZED). Saving that id-bearing partial
     * entity merges it over the persisted row and wipes every column it does not carry.
     * <p>
     * The assertions re-read the row from the database after the hand-in has completed, so they see the persisted
     * columns rather than the warm in-memory object that would hide the clobber. {@code attempt} is the field that
     * tells repeated test-exam attempts apart; test exams reach this same code path.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitKeepsPersistedQuizParticipationMetadata() throws Exception {
        var testRun = createTestRun();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        var testRunResponse = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/test-runs/" + testRun.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);

        QuizExercise quizExercise = (QuizExercise) testRunResponse.getExercises().stream().filter(QuizExercise.class::isInstance).findFirst().orElseThrow();
        long participationId = quizExercise.getStudentParticipations().iterator().next().getId();

        // stamp the persisted row with the metadata the reconstruction never carries; truncated to milliseconds so the
        // values survive the database round-trip exactly (PostgreSQL keeps microseconds, not nanoseconds)
        ZonedDateTime initializationDate = ZonedDateTime.now().minusHours(2).truncatedTo(ChronoUnit.MILLIS);
        ZonedDateTime individualDueDate = ZonedDateTime.now().plusHours(2).truncatedTo(ChronoUnit.MILLIS);
        StudentParticipation beforeSubmit = studentParticipationRepository.findById(participationId).orElseThrow();
        beforeSubmit.setInitializationDate(initializationDate);
        beforeSubmit.setIndividualDueDate(individualDueDate);
        beforeSubmit.setAttempt(3);
        beforeSubmit.setPresentationScore(7.0);
        beforeSubmit.setInitializationState(InitializationState.FINISHED);
        studentParticipationRepository.save(beforeSubmit);

        request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/student-exams/submit", testRunResponse, HttpStatus.OK, null);

        StudentParticipation afterSubmit = studentParticipationRepository.findById(participationId).orElseThrow();
        assertThat(afterSubmit.getInitializationDate()).as("initializationDate must survive the hand-in").isNotNull();
        assertThat(afterSubmit.getInitializationDate().toInstant()).isEqualTo(initializationDate.toInstant());
        assertThat(afterSubmit.getIndividualDueDate()).as("individualDueDate must survive the hand-in").isNotNull();
        assertThat(afterSubmit.getIndividualDueDate().toInstant()).isEqualTo(individualDueDate.toInstant());
        assertThat(afterSubmit.getAttempt()).as("attempt must survive the hand-in, it numbers repeated test-exam attempts").isEqualTo(3);
        assertThat(afterSubmit.getPresentationScore()).as("presentationScore must survive the hand-in").isEqualTo(7.0);
        assertThat(afterSubmit.getInitializationState()).as("a FINISHED participation must not regress to INITIALIZED").isEqualTo(InitializationState.FINISHED);
    }

    /**
     * Submit-path counterpart to {@link StudentExamProjectionNullExerciseTest}, which pins only that the RESPONSE
     * projections drop null exercises. {@code StudentExam.exercises} is an {@code @OrderColumn} list, so a hole in
     * {@code exercise_order} materializes as a null element, and a null passes every {@code instanceof} filter on the
     * submit path. {@code ExamQuizService.evaluateQuizParticipationsForTestRunAndTestExam} runs after the exam was
     * already marked submitted and outside the caller's try/catch, so dereferencing the gap answered the hand-in with
     * a 500 on an exam the student can no longer resubmit.
     * <p>
     * Asserts the hand-in returns 200 and that the quiz which survived the gap was still evaluated (it got a result).
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitTestRunWithNullExerciseGapSucceedsAndStillEvaluatesQuiz() throws Exception {
        var testRun = createTestRun();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        var testRunResponse = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/test-runs/" + testRun.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);
        QuizExercise quizExercise = (QuizExercise) testRunResponse.getExercises().stream().filter(QuizExercise.class::isInstance).findFirst().orElseThrow();

        // punch a hole into exercise_order: prepending a null shifts every real exercise up one index and leaves
        // index 0 without a row, which is exactly how a gap loads back
        StudentExam persisted = studentExamRepository.findWithExercisesById(testRun.getId()).orElseThrow();
        List<Exercise> exercisesWithGap = new ArrayList<>(persisted.getExercises());
        exercisesWithGap.addFirst(null);
        persisted.setExercises(exercisesWithGap);
        studentExamRepository.save(persisted);
        assertThat(studentExamRepository.findWithExercisesById(testRun.getId()).orElseThrow().getExercises()).as("the fixture must really produce a null gap").containsNull();

        request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/student-exams/submit", testRunResponse, HttpStatus.OK, null);

        assertThat(studentExamRepository.findById(testRun.getId()).orElseThrow().isSubmitted()).isTrue();
        var quizParticipations = studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerSubmissionsResultsAndFeedbacks(quizExercise.getId(),
                userUtilService.getUserByLogin(TEST_PREFIX + "instructor1").getId());
        assertThat(quizParticipations).as("the quiz that survived the gap must still have a participation").isNotEmpty();
        assertThat(quizParticipations.stream().flatMap(participation -> participation.getSubmissions().stream()).flatMap(submission -> submission.getResults().stream()))
                .as("the quiz that survived the gap must still have been evaluated").isNotEmpty();
    }

    /**
     * Pins the configured exam metadata on the live conduction wire.
     * <p>
     * {@code moduleNumber}, {@code courseName} and {@code examiner} come straight off the {@code Exam} entity and are
     * rendered by {@code ExamStartInformationComponent} (one information box each, on the exam cover) and
     * {@code ExamGeneralInformationComponent} (the summary table). Both guard every field with a presence check, so a
     * projection that drops them degrades silently: HTTP 200, no error, and the metadata the instructor configured
     * simply never appears. {@code ExamForConductionDTO} is shared by the conduction, summary and detail payloads
     * (the latter two through {@code ExamForSummaryDTO}'s {@code @JsonUnwrapped}), so this one wire covers all three.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testConductionWireCarriesExamMetadata() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();
        exam2.setModuleNumber("IN2000");
        exam2.setCourseName("Introduction to Software Engineering");
        exam2.setExaminer("Prof. Dr. Stephan Krusche");
        exam2 = examRepository.save(exam2);

        userUtilService.changeUser(TEST_PREFIX + "student1");
        JsonNode conductionWire = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, JsonNode.class);

        JsonNode examNode = conductionWire.get("exam");
        assertThat(examNode).as("conduction wire must carry the exam").isNotNull();
        assertThat(examNode.path("moduleNumber").asString()).as("moduleNumber must reach the exam cover").isEqualTo("IN2000");
        assertThat(examNode.path("courseName").asString()).as("courseName must reach the exam cover").isEqualTo("Introduction to Software Engineering");
        assertThat(examNode.path("examiner").asString()).as("examiner must reach the exam cover").isEqualTo("Prof. Dr. Stephan Krusche");
        deleteExamWithInstructor(exam1);
    }

    /**
     * Pins that the conduction submission-policy projection resolves the concrete policy subtype through a real
     * Hibernate proxy, not only through the query-loaded instance the conduction path happens to attach.
     * <p>
     * {@code SubmissionPolicyForConductionDTO.of} derives the client's {@code type} discriminator with a {@code switch}
     * pattern match over {@link LockRepositoryPolicy} / {@link SubmissionPenaltyPolicy}. With a plain Hibernate proxy
     * that match would fail — the proxy would extend only the abstract base — and the student would silently lose the
     * policy display on a 200. It holds here because {@link SubmissionPolicy} is annotated {@code @ConcreteProxy}, so
     * Hibernate proxies the concrete subtype. This test is the guard on that annotation: delete {@code @ConcreteProxy}
     * and this fails, which is the signal to unproxy in the factory instead.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmissionPolicyProjectionResolvesConcreteTypeThroughHibernateProxy() {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = (ProgrammingExercise) course.getExercises().stream().findFirst().orElseThrow();
        LockRepositoryPolicy policy = new LockRepositoryPolicy();
        policy.setActive(true);
        policy.setSubmissionLimit(3);
        programmingExerciseUtilService.addSubmissionPolicyToExercise(policy, programmingExercise);

        // open-in-view is off, so the lazy association is only proxyable inside an explicit transaction
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ProgrammingExercise reloaded = (ProgrammingExercise) exerciseRepository.findById(programmingExercise.getId()).orElseThrow();
            SubmissionPolicy lazyPolicy = reloaded.getSubmissionPolicy();
            assertThat(lazyPolicy.getClass()).as("fixture must be a Hibernate proxy, otherwise this test does not cover the proxy path").isNotEqualTo(LockRepositoryPolicy.class);
            Hibernate.initialize(lazyPolicy);
            assertThat(Hibernate.isInitialized(lazyPolicy)).isTrue();

            var dto = SubmissionPolicyForConductionDTO.of(lazyPolicy);
            assertThat(dto).as("an initialized proxy must still project").isNotNull();
            assertThat(dto.type()).as("the discriminator the client's SubmissionPolicyType switches on must survive the proxy").isEqualTo("lock_repository");
            assertThat(dto.submissionLimit()).isEqualTo(3);
            assertThat(dto.active()).isTrue();
        });
    }

    /**
     * Conduction counterpart to {@link #testTestRunSummaryWireServesQuizSolutionsBeforePublishResults()}: the test-run
     * exemption is decided on the entity by {@code ExamService.loadQuizExercisesForStudentExam}, which masks only when
     * {@code !(areResultsPublishedYet() || isTestRun())}. A test run therefore reaches the conduction projection with
     * its solutions intact, and the projection must carry them through — that is what gives the instructor the
     * right/wrong preview the test run exists for. Projecting conduction unconditionally through the solution-hidden
     * quiz shape silently stripped them on a 200.
     * <p>
     * Note that a stripped test-run conduction wire still LOOKS solution-bearing at a glance: the short-answer
     * projection keeps its {@code solutions} array (only {@code correctMappings} is dropped) and drag-and-drop keeps
     * its {@code dragItems}. The assertion therefore checks the fields that actually reveal the answer — multiple-choice
     * {@code isCorrect} and {@code correctMappings} — rather than the presence of a "solutions" key.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTestRunConductionWireServesQuizSolutions() throws Exception {
        var testRun = createTestRun();
        // pin the interesting state: the real exam's results are NOT published, only the test-run exemption applies
        testRunExam.setPublishResultsDate(ZonedDateTime.now().plusDays(1));
        testRunExam = examRepository.save(testRunExam);

        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        JsonNode conductionWire = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/test-runs/" + testRun.getId() + "/conduction",
                HttpStatus.OK, JsonNode.class);

        assertExerciseWireCarriesQuizSolutions(conductionWire.get("exercises"), "test-run conduction");
    }

    /**
     * Guards the other half of {@link #testTestRunConductionWireServesQuizSolutions()}: threading the test-run
     * exemption into the conduction projection must not loosen the gate for a real student sitting the exam. A
     * student's conduction wire stays solution-hidden.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testStudentConductionWireMasksQuizSolutions() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();

        userUtilService.changeUser(TEST_PREFIX + "student1");
        JsonNode conductionWire = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, JsonNode.class);

        assertExerciseWireMasksQuizSolutions(conductionWire.get("exercises"), "student conduction");
        deleteExamWithInstructor(exam1);
    }

    /**
     * Pins the two student-facing programming fields on the live conduction wire.
     * <p>
     * {@code allowOnlineEditor} gates the embedded editor ({@code programming-exam-submission.component.html}) and the
     * "offline IDE only" branches of the exam navigation. The fixture is deliberately an ONLINE-EDITOR-ONLY exercise
     * ({@code allowOfflineIde = false}), which is the case that degrades worst: without the field the student gets a
     * 200 with no editor and no offline fallback either.
     * <p>
     * {@code submissionPolicy} feeds the remaining-submissions indicator
     * ({@code ProgrammingSubmissionPolicyStatusComponent} reads {@code active}, {@code submissionLimit}, {@code type}
     * and {@code exceedingPenalty}). {@code prepareStudentExamForConduction} loads the policy onto the exercise, so
     * dropping it from the projection means the backend keeps enforcing a limit the student cannot see. Non-default
     * values throughout, so a projection that emitted the field but not its contents still fails.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testConductionWireCarriesProgrammingEditorGateAndSubmissionPolicy() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();
        long programmingExerciseId = studentExam.getExercises().stream().filter(ProgrammingExercise.class::isInstance).findFirst().orElseThrow().getId();
        // reload a clean instance: the exercise hanging off the student exam carries a detached participations
        // collection, and saving that graph would cascade an orphan removal onto the student's participations
        ProgrammingExercise programmingExercise = (ProgrammingExercise) exerciseRepository.findById(programmingExerciseId).orElseThrow();
        programmingExercise.setAllowOnlineEditor(true);
        programmingExercise.setAllowOfflineIde(false);
        programmingExercise = (ProgrammingExercise) exerciseRepository.save(programmingExercise);

        SubmissionPenaltyPolicy submissionPolicy = new SubmissionPenaltyPolicy();
        submissionPolicy.setActive(true);
        submissionPolicy.setSubmissionLimit(5);
        submissionPolicy.setExceedingPenalty(2.0);
        programmingExerciseUtilService.addSubmissionPolicyToExercise(submissionPolicy, programmingExercise);

        userUtilService.changeUser(TEST_PREFIX + "student1");
        JsonNode conductionWire = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, JsonNode.class);

        JsonNode programmingNode = null;
        for (JsonNode exercise : conductionWire.get("exercises")) {
            if (exercise.path("id").asLong() == programmingExercise.getId()) {
                programmingNode = exercise;
            }
        }
        assertThat(programmingNode).as("conduction wire must carry the programming exercise").isNotNull();
        assertThat(programmingNode.path("allowOnlineEditor").asBoolean()).as("allowOnlineEditor must be on the conduction wire, else the online editor never renders").isTrue();

        JsonNode policyNode = programmingNode.get("submissionPolicy");
        assertThat(policyNode).as("conduction wire must carry the active submission policy").isNotNull();
        assertThat(policyNode.path("active").asBoolean()).isTrue();
        assertThat(policyNode.path("submissionLimit").asInt()).isEqualTo(5);
        assertThat(policyNode.path("type").asString()).as("type is the discriminator the client's SubmissionPolicyType switches on").isEqualTo("submission_penalty");
        assertThat(policyNode.path("exceedingPenalty").asDouble()).isEqualTo(2.0);
        deleteExamWithInstructor(exam1);
    }

    /**
     * Asserts that the given {@code exercises} wire node carries the quiz solutions the summary UI renders: at least one
     * multiple-choice option flagged {@code isCorrect} and non-empty {@code correctMappings} on drag-and-drop /
     * short-answer questions.
     */
    private static void assertExerciseWireCarriesQuizSolutions(JsonNode exercises, String context) {
        boolean sawQuizQuestionWithSolution = false;
        for (JsonNode exercise : exercises) {
            JsonNode questions = exercise.get("quizQuestions");
            if (questions == null) {
                continue;
            }
            for (JsonNode question : questions) {
                switch (question.get("type").asString()) {
                    case "multiple-choice" -> {
                        long correctOptions = 0;
                        for (JsonNode option : question.get("answerOptions")) {
                            if (option.path("isCorrect").asBoolean(false)) {
                                correctOptions++;
                            }
                        }
                        assertThat(correctOptions).as(context + " MC wire must reveal the correct answer option via isCorrect").isGreaterThanOrEqualTo(1);
                        sawQuizQuestionWithSolution = true;
                    }
                    case "drag-and-drop", "short-answer" -> {
                        assertThat(question.path("correctMappings").isEmpty()).as(context + " wire must carry correctMappings").isFalse();
                        sawQuizQuestionWithSolution = true;
                    }
                    default -> {
                    }
                }
            }
        }
        assertThat(sawQuizQuestionWithSolution).as(context + " wire exposed a quiz question to solution-check").isTrue();
    }

    /**
     * Asserts that the given {@code exercises} wire node keeps the quiz questions solution-hidden: no {@code explanation},
     * no multiple-choice {@code isCorrect}, empty drag-and-drop / short-answer {@code correctMappings}.
     */
    private static void assertExerciseWireMasksQuizSolutions(JsonNode exercises, String context) {
        boolean sawQuiz = false;
        for (JsonNode exercise : exercises) {
            JsonNode questions = exercise.get("quizQuestions");
            if (questions == null) {
                continue;
            }
            sawQuiz = true;
            for (JsonNode question : questions) {
                assertThat(question.has("explanation")).as(context + " quiz question must not leak explanation").isFalse();
                if ("multiple-choice".equals(question.get("type").asString())) {
                    for (JsonNode option : question.get("answerOptions")) {
                        assertThat(option.has("isCorrect")).as(context + " MC option must not leak isCorrect").isFalse();
                    }
                }
                else {
                    assertThat(question.path("correctMappings").isEmpty()).as(context + " must not leak correctMappings").isTrue();
                }
            }
        }
        assertThat(sawQuiz).as(context + " wire exposed a quiz exercise to mask-check").isTrue();
    }

    /**
     * Instructor-detail counterpart to {@link #testSummaryWireServesQuizSolutionsAfterPublishResults()}: the instructor
     * student-exam summary route resolves its student exam via {@code getStudentExam}
     * ({@code StudentExamWithGradeDTO.studentExam} = the detail projection) into the same shared summary component, so
     * once results are published the DETAIL wire must carry the quiz solutions as well — fixing only the student
     * {@code /summary} endpoint regressed exactly this instructor path.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorDetailWireServesQuizSolutionsAfterPublishResults() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);

        JsonNode detailWire = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId(), HttpStatus.OK,
                JsonNode.class);
        assertExerciseWireCarriesQuizSolutions(detailWire.get("studentExam").get("exercises"), "published instructor detail");
        deleteExamWithInstructor(exam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedStudentExamSummaryWithoutGradingScaleAsStudentAfterPublishResults() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);

        // users tries to access exam summary after results are published
        userUtilService.changeUser(studentExam.getUser().getLogin());

        var studentExamGradeInfoFromServer = request.get(
                "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/grade-summary", HttpStatus.OK,
                StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.maxPoints()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.maxBonusPoints()).isEqualTo(5.0);
        assertThat(studentExamGradeInfoFromServer.gradeType()).isNull();
        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallScoreAchieved()).isEqualTo(100.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGrade()).isNull();
        assertThat(studentExamGradeInfoFromServer.studentResult().hasPassed()).isFalse();
        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchievedInFirstCorrection()).isZero();
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGradeInFirstCorrection()).isNull();
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus()).isNull();
        assertThat(studentExamGradeInfoFromServer.studentExam().id()).isEqualTo(studentExam.getId());

        var studentExamFromServer = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);

        for (final var exercise : studentExamFromServer.getExercises()) {
            if (exercise instanceof QuizExercise) {
                assertThat(studentExamGradeInfoFromServer.achievedPointsPerExercise().get(exercise.getId())).isEqualTo(4.0);
            }
            else {
                assertThat(studentExamGradeInfoFromServer.achievedPointsPerExercise().get(exercise.getId())).isEqualTo(5.0);
            }
        }
        deleteExamWithInstructor(exam1);
    }

    @NonNull
    private StudentExam createStudentExamWithResultsAndAssessments(boolean setFields, int numberOfStudents) throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, setFields, numberOfStudents).getFirst();
        var exam = examRepository.findById(studentExam.getExam().getId()).orElseThrow();
        StudentExam studentExamWithSubmissions = addExamExerciseSubmissionsForUser(exam, studentExam.getUser().getLogin(), studentExam);

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam = examRepository.save(exam);

        // submitExam
        request.postWithoutResponseBody("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/student-exams/submit", studentExamWithSubmissions,
                HttpStatus.OK);
        var studentExamFinished = request.get(
                "/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExamWithSubmissions.getId() + "/summary", HttpStatus.OK,
                StudentExam.class);

        exam.setEndDate(ZonedDateTime.now());
        exam = examRepository.save(exam);

        // Add results to all exercise submissions
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        for (var exercise : studentExamFinished.getExercises()) {
            if (exercise instanceof QuizExercise) {
                continue;
            }

            Participation participation = exercise.getStudentParticipations().iterator().next();
            participation.setExercise(exercise);
            Optional<Submission> latestSubmission = participation.findLatestSubmission();

            participationUtilService.addResultToSubmission(participation, latestSubmission.orElseThrow());
        }
        exam.setPublishResultsDate(ZonedDateTime.now());
        exam = examRepository.save(exam);

        // evaluate quizzes
        request.postWithoutLocation("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises", null, HttpStatus.OK,
                new HttpHeaders());
        return studentExam;
    }

    private GradingScale createGradeScale(boolean isBonus) {
        GradingScale gradingScale;
        if (isBonus) {
            gradingScale = gradingScaleUtilService.generateGradingScaleWithStickyStep(new double[] { 60, 40, 50 }, Optional.of(new String[] { "0", "0.3", "0.6" }), true, 1);
            gradingScale.setGradeType(GradeType.BONUS);
        }
        else {
            gradingScale = gradingScaleUtilService.generateGradingScaleWithStickyStep(new double[] { 60, 25, 15, 50 }, Optional.of(new String[] { "5.0", "3.0", "1.0", "1.0" }),
                    true, 1);
        }
        gradingScaleRepository.save(gradingScale);
        return gradingScale;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedStudentExamSummaryWithGradingScaleAsStudentAfterPublishResults() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);

        GradingScale gradingScale = createGradeScale(false);
        gradingScale.setExam(exam2);
        gradingScaleRepository.save(gradingScale);

        // users tries to access exam summary after results are published
        userUtilService.changeUser(studentExam.getUser().getLogin());

        var studentExamGradeInfoFromServer = request.get(
                "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/grade-summary", HttpStatus.OK,
                StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.maxPoints()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.maxBonusPoints()).isEqualTo(5.0);
        assertThat(studentExamGradeInfoFromServer.gradeType()).isEqualTo(GradeType.GRADE);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallScoreAchieved()).isEqualTo(100.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGrade()).isEqualTo("1.0");
        assertThat(studentExamGradeInfoFromServer.studentResult().hasPassed()).isTrue();
        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchievedInFirstCorrection()).isZero();
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGradeInFirstCorrection()).isEqualTo("5.0");
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus()).isNull();
        assertThat(studentExamGradeInfoFromServer.studentExam().id()).isEqualTo(studentExam.getId());

        var studentExamFromServer = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);

        for (final var exercise : studentExamFromServer.getExercises()) {
            if (exercise instanceof QuizExercise) {
                assertThat(studentExamGradeInfoFromServer.achievedPointsPerExercise().get(exercise.getId())).isEqualTo(4.0);
            }
            else {
                assertThat(studentExamGradeInfoFromServer.achievedPointsPerExercise().get(exercise.getId())).isEqualTo(5.0);
            }
        }
        deleteExamWithInstructor(exam1);
    }

    private StudentExam addExamExerciseSubmissionsForUser(Exam exam, String userLogin, StudentExam studentExam) throws Exception {
        if (userLogin != null) {
            userUtilService.changeUser(userLogin);
        }
        // start exam conduction for a user
        var studentExamFromServer = request.get(
                "/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExam.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);

        for (var exercise : studentExamFromServer.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                commitNewFileToParticipationRepo((ProgrammingExerciseStudentParticipation) participation);
                jenkinsRequestMockProvider.reset();
                jenkinsRequestMockProvider.mockTriggerBuild(programmingExercise.getProjectKey(), ((ProgrammingExerciseStudentParticipation) participation).getBuildPlanId(), false);
                request.postWithoutLocation("/api/programming/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());
                Optional<ProgrammingSubmission> programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(participation.getId());
                programmingSubmission.ifPresent(submission -> participation.getSubmissions().add(submission));
                continue;
            }
            var submission = participation.getSubmissions().iterator().next();
            switch (exercise) {
                case ModelingExercise ignored -> {
                    // check that the submission was saved and that a submitted version was created
                    String newModel = "This is a new model";
                    var modelingSubmission = (ModelingSubmission) submission;
                    modelingSubmission.setModel(newModel);
                    request.put("/api/modeling/exercises/" + exercise.getId() + "/modeling-submissions", modelingSubmission, HttpStatus.OK);
                }
                case TextExercise ignored -> {
                    var textSubmission = (TextSubmission) submission;
                    final var newText = "New Text";
                    textSubmission.setText(newText);
                    request.put("/api/text/exercises/" + exercise.getId() + "/text-submissions", toRequestDTO(textSubmission), HttpStatus.OK);
                }
                case QuizExercise quizExercise -> submitQuizInExam(quizExercise, (QuizSubmission) submission);
                case FileUploadExercise ignored -> {
                    var fileUploadSubmission = (FileUploadSubmission) submission;
                    final var newFilePath = "path/to/file.txt";
                    fileUploadSubmission.setFilePath(newFilePath);
                    var file = new MockMultipartFile("file", "filename.pdf", "application/json", "some data".getBytes());
                    request.postWithMultipartFile("/api/fileupload/exercises/" + exercise.getId() + "/file-upload-submissions", submission, "submission", file,
                            FileUploadSubmission.class, HttpStatus.OK);
                }
                default -> {
                }
            }
        }
        return studentExamFromServer;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedStudentExamSummaryWithGradingScaleAsStudentBeforePublishResults() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);

        exam2.setPublishResultsDate(ZonedDateTime.now().plusDays(1));
        exam2 = examRepository.save(exam2);

        GradingScale gradingScale = createGradeScale(false);
        gradingScale.setExam(exam2);
        gradingScaleRepository.save(gradingScale);

        // users tries to access exam summary after results are published
        userUtilService.changeUser(studentExam.getUser().getLogin());

        request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/grade-summary", HttpStatus.FORBIDDEN,
                StudentExamWithGradeDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedStudentExamSummaryWithGradingScaleAsStudentAfterPublishResultsWithOwnUserId() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);

        GradingScale gradingScale = createGradeScale(false);
        gradingScale.setExam(exam2);
        gradingScaleRepository.save(gradingScale);

        // users tries to access exam summary after results are published
        userUtilService.changeUser(studentExam.getUser().getLogin());

        var studentExamGradeInfoFromServerForUserId = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId()
                + "/grade-summary?userId=" + studentExam.getUser().getId(), HttpStatus.OK, StudentExamWithGradeDTO.class);

        var studentExamGradeInfoFromServer = request.get(
                "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/grade-summary", HttpStatus.OK,
                StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServerForUserId.gradeType()).isEqualTo(studentExamGradeInfoFromServer.gradeType());
        assertThat(studentExamGradeInfoFromServerForUserId.studentResult().overallGrade()).isEqualTo(studentExamGradeInfoFromServer.studentResult().overallGrade());
        assertThat(studentExamGradeInfoFromServerForUserId.studentResult().overallPointsAchieved())
                .isEqualTo(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved());
        assertThat(studentExamGradeInfoFromServerForUserId.studentResult().hasPassed()).isEqualTo(studentExamGradeInfoFromServer.studentResult().hasPassed());
        assertThat(studentExamGradeInfoFromServer.studentExam().id()).isEqualTo(studentExam.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedStudentExamSummaryWithGradingScaleAsStudentAfterPublishResultsWithOtherUserId() throws Exception {
        exam2 = createStudentExamWithResultsAndAssessments(true, 2).getExam();

        GradingScale gradingScale = createGradeScale(false);
        gradingScale.setExam(exam2);
        gradingScaleRepository.save(gradingScale);

        // users try to access exam summary after results are published
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        userUtilService.changeUser(student1.getLogin());
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        // Note: student1 cannot see the grade summary for student2
        request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam1.getId() + "/grade-summary?userId=" + student2.getId(),
                HttpStatus.FORBIDDEN, StudentExamWithGradeDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedStudentExamSummaryWithGradingScaleAsInstructorAfterPublishResultsWithOtherUserId() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);
        exam2 = studentExam.getExam();

        GradingScale gradingScale = createGradeScale(false);
        gradingScale.setExam(exam2);
        gradingScaleRepository.save(gradingScale);

        var studentExamGradeInfoFromServer = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId()
                + "/grade-summary?userId=" + studentExam.getUser().getId(), HttpStatus.OK, StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.maxPoints()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.maxBonusPoints()).isEqualTo(5.0);
        assertThat(studentExamGradeInfoFromServer.gradeType()).isEqualTo(GradeType.GRADE);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallScoreAchieved()).isEqualTo(100.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGrade()).isEqualTo("1.0");
        assertThat(studentExamGradeInfoFromServer.studentResult().hasPassed()).isTrue();
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedStudentExamSummaryWithGradingScaleWithCorrectlyRoundedPoints() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);

        GradingScale gradingScale = createGradeScale(false);
        gradingScale.setExam(exam2);
        gradingScaleRepository.save(gradingScale);
        List<StudentParticipation> participations = studentParticipationRepository
                .findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionResultIgnoreTestRuns(studentExam.getUser().getId(), studentExam.getExercises());
        var latestResults = participations.stream().flatMap(participation -> participation.getSubmissions().stream().map(Submission::getLatestResult)).toList();
        for (var result : latestResults) {
            // First set all results to 0 since we don't want any additions to affect the manually assigned results below.
            result.setScore(0.0);
        }

        // The sum of the below scores have more than 1 digits after decimal due to how doubles are stored.
        // i.e. 0.3 + 0.3 + 0.3 = 0.8999999999999999
        latestResults.stream().limit(3).forEach(result -> {
            Exercise exercise = result.getSubmission().getParticipation().getExercise();
            exercise.setMaxPoints(100.0);  // To make points equal to scores for simplicity.
            result.setScore(0.3);
        });

        resultRepository.saveAll(latestResults);
        exerciseRepository.saveAll(latestResults.stream().map(result -> result.getSubmission().getParticipation().getExercise()).toList());

        // Assert prerequisites of this test case
        final int desiredAccuracyOfScores = 1;
        assertThat(studentExam.getExam().getCourse().getAccuracyOfScores()).isEqualTo(desiredAccuracyOfScores);

        double sumOfResultScores = latestResults.stream().mapToDouble(Result::getScore).sum();
        double expectedOverallPoints = RoundingUtil.roundToNDecimalPlaces(sumOfResultScores, desiredAccuracyOfScores);

        assertThat(sumOfResultScores).isNotEqualTo(expectedOverallPoints);

        // Assert actual computed result.
        userUtilService.changeUser(studentExam.getUser().getLogin());
        var studentExamGradeInfoFromServer = request.get(
                "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/grade-summary", HttpStatus.OK,
                StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved()).isEqualTo(expectedOverallPoints);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedFinalExamSummaryWithBonusExam(boolean asStudent) throws Exception {
        StudentExam finalStudentExam = createStudentExamWithResultsAndAssessments(false, 1);
        jenkinsRequestMockProvider.reset();
        StudentExam bonusStudentExam = createStudentExamWithResultsAndAssessments(false, 1);

        BonusStrategy bonusStrategy = BonusStrategy.GRADES_CONTINUOUS;

        Exam finalExam = configureFinalExamWithBonusExam(finalStudentExam, bonusStudentExam, bonusStrategy);

        String queryParam = "";
        if (asStudent) {
            // users tries to access exam summary after results are published
            userUtilService.changeUser(finalStudentExam.getUser().getLogin());
        }
        else {
            queryParam = "?userId=" + finalStudentExam.getUser().getId();
        }

        var studentExamGradeInfoFromServer = request.get(
                "/api/exam/courses/" + finalExam.getCourse().getId() + "/exams/" + finalExam.getId() + "/student-exams/" + finalStudentExam.getId() + "/grade-summary" + queryParam,
                HttpStatus.OK, StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.maxPoints()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.maxBonusPoints()).isEqualTo(5.0);
        assertThat(studentExamGradeInfoFromServer.gradeType()).isEqualTo(GradeType.GRADE);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved()).isEqualTo(24.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGrade()).isEqualTo("3.0");
        assertThat(studentExamGradeInfoFromServer.studentResult().hasPassed()).isTrue();
        assertThat(studentExamGradeInfoFromServer.studentResult().mostSeverePlagiarismVerdict()).isNull();
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().bonusStrategy()).isEqualTo(bonusStrategy);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().bonusFromTitle()).isEqualTo("Real exam 1");
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().studentPointsOfBonusSource()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().bonusGrade()).isEqualTo("0.3");
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().finalGrade()).isEqualTo("2.7");
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().mostSeverePlagiarismVerdict()).isNull();
    }

    @NonNull
    private Exam configureFinalExamWithBonusExam(StudentExam finalStudentExam, StudentExam bonusStudentExam, BonusStrategy bonusStrategy) {
        var finalExam = examRepository.findById(finalStudentExam.getExam().getId()).orElseThrow();
        var bonusExam = examRepository.findById(bonusStudentExam.getExam().getId()).orElseThrow();
        assertThat(finalExam.getId()).isNotEqualTo(bonusExam.getId());

        GradingScale finalExamGradingScale = createGradeScale(false);
        finalExamGradingScale.setExam(finalExam);
        finalExamGradingScale.setBonusStrategy(bonusStrategy);
        gradingScaleRepository.save(finalExamGradingScale);

        GradingScale bonusGradingScale = createGradeScale(true);
        bonusGradingScale.setExam(bonusExam);
        gradingScaleRepository.save(bonusGradingScale);

        double weight = bonusStrategy == BonusStrategy.POINTS ? 1.0 : -1.0;
        var bonus = BonusFactory.generateBonus(bonusStrategy, weight, bonusGradingScale.getId(), finalExamGradingScale.getId());
        bonusRepository.save(bonus);

        StudentParticipation participationWithLatestResult = studentParticipationRepository
                .findByExerciseIdAndStudentIdAndTestRunWithLatestResult(finalStudentExam.getExercises().getFirst().getId(), finalStudentExam.getUser().getId(), false)
                .orElseThrow();
        Result result = participationUtilService.getResultsForParticipation(participationWithLatestResult).iterator().next();
        result.setScore(0.0); // To reduce grade to a grade lower than the max grade.
        resultRepository.save(result);
        return finalExam;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedFinalExamSummaryWithBonusExamAndPlagiarismAsStudent() throws Exception {
        StudentExam finalStudentExam = createStudentExamWithResultsAndAssessments(false, 1);
        jenkinsRequestMockProvider.reset();
        StudentExam bonusStudentExam = createStudentExamWithResultsAndAssessments(false, 1);

        BonusStrategy bonusStrategy = BonusStrategy.POINTS;

        Exam finalExam = configureFinalExamWithBonusExam(finalStudentExam, bonusStudentExam, bonusStrategy);

        User student = finalStudentExam.getUser();

        var finalPlagiarismCase = new PlagiarismCase();
        finalPlagiarismCase.setStudent(student);
        Exercise exerciseWithPointDeduction = finalStudentExam.getExercises().get(1); // We get the second exercise because the first one has already 0 points.
        finalPlagiarismCase.setExercise(exerciseWithPointDeduction);
        finalPlagiarismCase.setVerdict(PlagiarismVerdict.POINT_DEDUCTION);
        finalPlagiarismCase.setVerdictPointDeduction(50);
        plagiarismCaseRepository.save(finalPlagiarismCase);

        var bonusPlagiarismCase = new PlagiarismCase();
        bonusPlagiarismCase.setStudent(student);
        bonusPlagiarismCase.setExercise(bonusStudentExam.getExercises().getFirst());
        bonusPlagiarismCase.setVerdict(PlagiarismVerdict.PLAGIARISM);
        plagiarismCaseRepository.save(bonusPlagiarismCase);

        // users tries to access exam summary after results are published
        userUtilService.changeUser(student.getLogin());

        var studentExamGradeInfoFromServer = request.get(
                "/api/exam/courses/" + finalExam.getCourse().getId() + "/exams/" + finalExam.getId() + "/student-exams/" + finalStudentExam.getId() + "/grade-summary",
                HttpStatus.OK, StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.maxPoints()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.maxBonusPoints()).isEqualTo(5.0);
        assertThat(studentExamGradeInfoFromServer.gradeType()).isEqualTo(GradeType.GRADE);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved()).isEqualTo(22.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGrade()).isEqualTo("3.0");
        assertThat(studentExamGradeInfoFromServer.studentResult().hasPassed()).isTrue();
        assertThat(studentExamGradeInfoFromServer.studentResult().mostSeverePlagiarismVerdict()).isEqualTo(PlagiarismVerdict.POINT_DEDUCTION);
        assertThat(studentExamGradeInfoFromServer.studentResult().exerciseGroupIdToExerciseResult().get(exerciseWithPointDeduction.getExerciseGroup().getId()).achievedPoints())
                .isEqualTo(2.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().bonusStrategy()).isEqualTo(bonusStrategy);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().bonusFromTitle()).isEqualTo("Real exam 1");
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().studentPointsOfBonusSource()).isZero();
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().bonusGrade()).isEqualTo(GradingScale.DEFAULT_PLAGIARISM_GRADE);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().finalPoints()).isEqualTo(22.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().finalGrade()).isEqualTo("3.0");
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().mostSeverePlagiarismVerdict()).isEqualTo(PlagiarismVerdict.PLAGIARISM);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedFinalExamSummaryWithPlagiarismAndNotParticipatedBonusExamAsStudent() throws Exception {
        StudentExam finalStudentExam = createStudentExamWithResultsAndAssessments(false, 1);
        jenkinsRequestMockProvider.reset();

        User student = finalStudentExam.getUser();
        final String noParticipationGrade = "NoParticipation";
        studentExam1.setSubmitted(false);
        studentExam1.setUser(student);
        studentExam1 = studentExamRepository.save(studentExam1);

        StudentExam bonusStudentExam = studentExam1;

        BonusStrategy bonusStrategy = BonusStrategy.POINTS;

        Exam finalExam = configureFinalExamWithBonusExam(finalStudentExam, bonusStudentExam, bonusStrategy);
        var bonusGradingScale = bonusRepository.findAllByBonusToExamId(finalExam.getId()).iterator().next().getSourceGradingScale();
        bonusGradingScale.setNoParticipationGrade(noParticipationGrade);
        gradingScaleRepository.save(bonusGradingScale);

        // users tries to access exam summary after results are published
        userUtilService.changeUser(student.getLogin());

        var studentExams = studentExamRepository.findAllWithExercisesByUserIdAndExamId(student.getId(), bonusGradingScale.getExam().getId());
        log.debug("Found {} student exams for student {} {} and exam {}", studentExams.size(), student.getId(), student.getLogin(), finalExam.getId());
        assertThat(studentExams).as("Found too many student exams" + studentExams).hasSize(1);

        var studentExamGradeInfoFromServer = request.get(
                "/api/exam/courses/" + finalExam.getCourse().getId() + "/exams/" + finalExam.getId() + "/student-exams/" + finalStudentExam.getId() + "/grade-summary",
                HttpStatus.OK, StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved()).isEqualTo(24.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGrade()).isEqualTo("3.0");

        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().studentPointsOfBonusSource()).isZero();
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().bonusGrade()).isEqualTo(noParticipationGrade);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().finalPoints()).isEqualTo(24.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().finalGrade()).isEqualTo("3.0");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithStudentExamsAfterConductionAndEvaluation() throws Exception {
        final var baseTime = ZonedDateTime.now();  // use a base time rather than repeated 'ZonedDateTime.now()' calls
        // to prevent potential time-related flakiness

        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();

        final StudentExam studentExamWithSubmissions = addExamExerciseSubmissionsForUser(exam2, studentExam.getUser().getLogin(), studentExam);

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam2.setStartDate(baseTime.minusMinutes(3));
        exam2 = examRepository.save(exam2);

        // submitExam
        request.postWithoutResponseBody("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamWithSubmissions, HttpStatus.OK);
        var studentExamFinished = request.get(
                "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExamWithSubmissions.getId() + "/summary", HttpStatus.OK,
                StudentExam.class);

        exam2.setEndDate(baseTime);
        exam2 = examRepository.save(exam2);

        // Add results to all exercise submissions (evaluation)
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        for (var exercise : studentExamFinished.getExercises()) {
            if (exercise instanceof QuizExercise) {
                continue;
            }

            Participation participation = exercise.getStudentParticipations().iterator().next();
            participation.setExercise(exercise);
            Optional<Submission> latestSubmission = participation.findLatestSubmission();

            participationUtilService.addResultToSubmission(participation, latestSubmission.orElseThrow());
        }
        exam2.setPublishResultsDate(baseTime);
        exam2 = examRepository.save(exam2);
        exam2 = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(exam2.getId());

        // evaluate quizzes
        request.postWithoutLocation("/api/exam/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/student-exams/evaluate-quiz-exercises", null, HttpStatus.OK,
                new HttpHeaders());

        jenkinsRequestMockProvider.reset();
        final ProgrammingExercise programmingExercise = (ProgrammingExercise) exam2.getExerciseGroups().get(6).getExercises().iterator().next();

        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());

        Set<User> users = exam2.getRegisteredUsers();
        mockDeleteProgrammingExercise(programmingExercise, users);

        await().atMost(Duration.ofMinutes(2)).pollInterval(10, TimeUnit.MILLISECONDS).until(participantScoreScheduleService::isIdle);
        request.delete("/api/exam/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId(), HttpStatus.OK);
        assertThat(examRepository.findById(exam2.getId())).as("Exam was deleted").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTestRun() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var testRun = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        request.delete("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-runs/" + testRun.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTestRunWithReferencedParticipationsDeleteOneParticipation() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var testRun1 = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        var testRun2 = new StudentExam();
        testRun2.setTestRun(true);
        testRun2.setExam(testRun1.getExam());
        testRun2.setUser(instructor);
        testRun2.setExercises(List.of(testRun1.getExercises().getFirst()));
        testRun2.setWorkingTime(testRun1.getWorkingTime());
        studentExamRepository.save(testRun2);
        request.delete("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-runs/" + testRun1.getId(), HttpStatus.OK);
        var testRunList = studentExamRepository.findAllTestRunsWithExercisesParticipationsSubmissionsResultsByExamId(exam.getId());
        assertThat(testRunList).hasSize(1);
        testRunList.getFirst().getExercises().forEach(exercise -> assertThat(exercise.getStudentParticipations()).isNotEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTestRunWithReferencedParticipationsDeleteNoParticipation() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var testRun1 = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        var testRun2 = new StudentExam();
        testRun2.setTestRun(true);
        testRun2.setExam(testRun1.getExam());
        testRun2.setUser(instructor);
        testRun2.setExercises(List.of(testRun1.getExercises().getFirst()));
        testRun2.setWorkingTime(testRun1.getWorkingTime());
        studentExamRepository.save(testRun2);
        request.delete("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-runs/" + testRun2.getId(), HttpStatus.OK);
        var testRunList = studentExamRepository.findAllTestRunsWithExercisesParticipationsSubmissionsResultsByExamId(exam.getId());
        assertThat(testRunList).hasSize(1);
        testRunList.getFirst().getExercises().forEach(exercise -> assertThat(exercise.getStudentParticipations()).isNotEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTestRunWithMissingParticipation() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var testRun = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        var participations = studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerSubmissions(testRun.getExercises().getFirst().getId(), instructor.getId());
        assertThat(participations).isNotEmpty();
        participationDeletionService.delete(participations.getFirst().getId(), true);
        request.delete("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-runs/" + testRun.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeleteTestRunAsTutor() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var testRun = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam1, instructor, exam1.getExerciseGroups());
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/test-runs/" + testRun.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestRun() throws Exception {
        createTestRun();
    }

    /**
     * The server resolves the requested exercise ids straight from the exercise repository, which accepts any id, so it
     * must reject ids that point outside the exam the test run belongs to. Otherwise an instructor could pull a foreign
     * exam's exercise into a test run of their own exam.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestRunRejectsExerciseOfAnotherExam() throws Exception {
        Exam ownExam = examUtilService.addTextModelingProgrammingExercisesToExam(examUtilService.addExam(course1), false, true);
        Exam foreignExam = examUtilService.addTextModelingProgrammingExercisesToExam(examUtilService.addExam(course1), false, true);

        List<Long> exerciseIds = new ArrayList<>(ownExam.getExerciseGroups().stream().map(exerciseGroup -> exerciseGroup.getExercises().iterator().next().getId()).toList());
        exerciseIds.add(foreignExam.getExerciseGroups().getFirst().getExercises().iterator().next().getId());
        CreateTestRunDTO testRunConfiguration = new CreateTestRunDTO(ownExam.getId(), exerciseIds, 6000);

        request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + ownExam.getId() + "/test-runs", testRunConfiguration, StudentExamDTO.class,
                HttpStatus.CONFLICT);
        assertThat(studentExamRepository.findAllByExamId_AndTestRunIsTrue(ownExam.getId())).isEmpty();
    }

    /**
     * the server invokes SecurityUtils.setAuthorizationObject() so after invoking this method you need to "login" the user again
     *
     * @return the created test run
     * @throws Exception if errors occur
     */
    private StudentExam createTestRun() throws Exception {
        testRunExam = examUtilService.addExam(course1);
        testRunExam = examUtilService.addTextModelingProgrammingExercisesToExam(testRunExam, false, true);
        return createTestRun(testRunExam);
    }

    private StudentExam createTestRun(Exam exam) throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        // the client builds this list by iterating the exam's exercise groups in order, picking one exercise per
        // group; the order must be preserved end-to-end since StudentExam.exercises is an @OrderColumn list
        List<Long> exerciseIds = exam.getExerciseGroups().stream().map(exerciseGroup -> exerciseGroup.getExercises().iterator().next().getId()).toList();
        CreateTestRunDTO testRunConfiguration = new CreateTestRunDTO(exam.getId(), exerciseIds, 6000);

        var testRunsInDbBefore = studentExamRepository.findAllByExamId_AndTestRunIsTrue(exam.getId());
        var newTestRun = request.postWithResponseBody("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-runs", testRunConfiguration,
                StudentExamDTO.class, HttpStatus.OK);
        var testRunsInDbAfter = studentExamRepository.findAllByExamId_AndTestRunIsTrue(exam.getId());
        assertThat(testRunsInDbAfter).hasSize(testRunsInDbBefore.size() + 1);
        assertThat(newTestRun.testRun()).isTrue();
        assertThat(newTestRun.workingTime()).isEqualTo(6000);
        assertThat(newTestRun.user()).isNotNull();
        assertThat(newTestRun.user().id()).isEqualTo(instructor.getId());
        // the nested exam is intentionally omitted from this endpoint's response (see StudentExamDTO#withUser)
        assertThat(newTestRun.exam()).isNull();

        // reload from the repository: verifies actual persistence (not just the echoed response) and, since
        // StudentExam.exercises is an @OrderColumn list, that the exact order of exerciseIds was preserved
        StudentExam persistedTestRun = studentExamRepository.findByIdWithExercisesElseThrow(newTestRun.id());
        assertThat(persistedTestRun.getExercises().stream().map(Exercise::getId).toList()).containsExactlyElementsOf(exerciseIds);
        assertThat(persistedTestRun.getUser().getId()).isEqualTo(instructor.getId());
        assertThat(persistedTestRun.getWorkingTime()).isEqualTo(6000);
        assertThat(persistedTestRun.isTestRun()).isTrue();
        return persistedTestRun;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTestExamTestRunConductionDoesNotCreateAdditionalParticipations() throws Exception {
        Exam testExam = examUtilService.addTestExam(course1);
        testExam = examUtilService.addTextModelingProgrammingExercisesToExam(testExam, false, true);
        StudentExam testRun = createTestRun(testExam);
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        Set<Long> participationIdsBeforeConduction = testRun.getExercises().stream()
                .flatMap(exercise -> studentParticipationRepository.findByExerciseIdAndStudentId(exercise.getId(), instructor.getId()).stream()).map(StudentParticipation::getId)
                .collect(Collectors.toSet());
        assertThat(participationIdsBeforeConduction).hasSize(testRun.getExercises().size());

        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam.getId() + "/test-runs/" + testRun.getId() + "/conduction", HttpStatus.OK, StudentExam.class);

        Set<Long> participationIdsAfterConduction = testRun.getExercises().stream()
                .flatMap(exercise -> studentParticipationRepository.findByExerciseIdAndStudentId(exercise.getId(), instructor.getId()).stream()).map(StudentParticipation::getId)
                .collect(Collectors.toSet());
        assertThat(participationIdsAfterConduction).isEqualTo(participationIdsBeforeConduction);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitTestRun() throws Exception {
        var testRun = createTestRun();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        var testRunResponse = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/test-runs/" + testRun.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);

        QuizExercise quizExercise = null;
        QuizSubmission quizSubmission = null;

        for (var exercise : testRunResponse.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            var submission = participation.getSubmissions().iterator().next();
            if (exercise instanceof QuizExercise) {
                quizExercise = (QuizExercise) exercise;
                quizSubmission = (QuizSubmission) submission;
                submitQuizInExam(quizExercise, quizSubmission);
            }
        }

        assertThat(quizExercise).isNotNull();
        request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/student-exams/submit", testRunResponse, HttpStatus.OK, null);
        testRunResponse = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/student-exams/" + testRunResponse.getId() + "/summary",
                HttpStatus.OK, StudentExam.class);

        checkQuizSubmission(quizExercise.getId(), quizSubmission.getId());

        // reconnect references so that the following method works
        testRunResponse.getExercises().forEach(exercise -> exercise.getStudentParticipations().forEach(studentParticipation -> studentParticipation.setExercise(exercise)));
        // invoke a second time to test the else case in this method
        SecurityUtils.setAuthorizationObject();
        examQuizService.evaluateQuizParticipationsForTestRunAndTestExam(testRunResponse);
        // make sure that no second result is created
        checkQuizSubmission(quizExercise.getId(), quizSubmission.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitTextExerciseDuringTestRun() throws Exception {
        // Create a test run and get conduction data (which creates participations + submissions)
        var testRun = createTestRun();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        var testRunResponse = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/test-runs/" + testRun.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);

        // Find the text exercise in the test run
        TextExercise textExercise = null;
        TextSubmission textSubmission = null;
        for (var exercise : testRunResponse.getExercises()) {
            if (exercise instanceof TextExercise) {
                textExercise = (TextExercise) exercise;
                assertThat(exercise.getStudentParticipations()).as("Text exercise should have participations").isNotEmpty();
                var participation = exercise.getStudentParticipations().iterator().next();
                assertThat(participation.getSubmissions()).as("Participation should have submissions").isNotEmpty();
                textSubmission = (TextSubmission) participation.getSubmissions().iterator().next();
                break;
            }
        }

        assertThat(textExercise).as("Test run should contain a text exercise").isNotNull();

        // Simulate the student saving the text submission during the exam (the code path that was broken for test runs)
        textSubmission.setText("Updated text submission during test run");
        request.put("/api/text/exercises/" + textExercise.getId() + "/text-submissions", toRequestDTO(textSubmission), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitModelingExerciseDuringTestRun() throws Exception {
        var testRun = createTestRun();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        var testRunResponse = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/test-runs/" + testRun.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);

        // Find the modeling exercise in the test run
        ModelingExercise modelingExercise = null;
        ModelingSubmission modelingSubmission = null;
        for (var exercise : testRunResponse.getExercises()) {
            if (exercise instanceof ModelingExercise) {
                modelingExercise = (ModelingExercise) exercise;
                assertThat(exercise.getStudentParticipations()).as("Modeling exercise should have participations").isNotEmpty();
                var participation = exercise.getStudentParticipations().iterator().next();
                assertThat(participation.getSubmissions()).as("Participation should have submissions").isNotEmpty();
                modelingSubmission = (ModelingSubmission) participation.getSubmissions().iterator().next();
                break;
            }
        }

        assertThat(modelingExercise).as("Test run should contain a modeling exercise").isNotNull();
        assertThat(modelingSubmission).as("Modeling exercise should have a submission").isNotNull();

        // Simulate saving the modeling submission during the exam test run
        modelingSubmission.setModel("{\"updated\": true}");
        request.put("/api/modeling/exercises/" + modelingExercise.getId() + "/modeling-submissions", modelingSubmission, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTestRunGradeSummaryDoesNotReturn404() throws Exception {
        StudentExam testRun = createTestRun();
        testRun.setSubmitted(true);
        testRun = studentExamRepository.save(testRun);
        Exam exam = testRun.getExam();
        exam.setPublishResultsDate(ZonedDateTime.now());
        exam.setExampleSolutionPublicationDate(ZonedDateTime.now().plusDays(2));

        List<ExerciseGroup> exerciseGroups = new ArrayList<>();
        testRun.getExercises().forEach((exercise -> exerciseGroups.add(exercise.getExerciseGroup())));

        exam.setExerciseGroups(exerciseGroups);
        exam = examRepository.save(exam);
        Exam finalExam = exam;
        exam = examRepository.findByCourseIdWithExerciseGroupsAndExercises(course1.getId()).stream().filter(entry -> finalExam.getId().equals(entry.getId())).findFirst()
                .orElseThrow();
        testRun = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, testRun.getUser(), exam.getExerciseGroups());
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        User instructor1 = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        StudentExamWithGradeDTO studentExamGradeInfoFromServer = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/student-exams/"
                + testRun.getId() + "/grade-summary?userId=" + instructor1.getId() + "&isTestRun=true", HttpStatus.OK, StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.achievedPointsPerExercise().size()).isEqualTo(testRunExam.getExerciseGroups().size());
    }

    private void checkQuizSubmission(long quizExerciseId, long quizSubmissionId) {

        assertThat(quizSubmissionTestRepository.findByParticipation_Exercise_Id(quizExerciseId)).hasSize(1);

        List<Result> results = resultRepository.findByExerciseIdOrderByCompletionDateAsc(quizExerciseId);
        assertThat(results).hasSize(1);
        var result = results.getFirst();
        assertThat(result.getSubmission().getId()).isEqualTo(quizSubmissionId);

        assertThat(result.getScore()).isEqualTo(44.4);
        var resultQuizSubmission = (QuizSubmission) result.getSubmission();
        resultQuizSubmission = quizSubmissionTestRepository.findWithEagerResultAndFeedbackById(resultQuizSubmission.getId()).orElseThrow();
        assertThat(resultQuizSubmission.getScoreInPoints()).isEqualTo(4D);
        var submittedAnswers = resultQuizSubmission.getSubmittedAnswers();
        for (SubmittedAnswer submittedAnswer : submittedAnswers) {
            // MC submitted answers 0 points as one correct and one false -> ALL_OR_NOTHING
            if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isEqualTo(4D);
            } // DND submitted answers 0 points as one correct and two false -> PROPORTIONAL_WITH_PENALTY
              // or
              // SA submitted answers 0 points as one correct and one false -> PROPORTIONAL_WITHOUT_PENALTY
            else if (submittedAnswer instanceof DragAndDropSubmittedAnswer || submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isZero();
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitAndUnSubmitStudentExamAfterExamIsOver() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(10));
        studentExam.setStartedAndStartDate(ZonedDateTime.now().minusMinutes(8));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(5));
        exam2 = examRepository.save(exam2);
        studentExam = studentExamRepository.save(studentExam);
        assertThat(studentExam.isSubmitted()).isFalse();
        assertThat(studentExam.getSubmissionDate()).isNull();

        // submitting the exam, although the endDate is over
        userUtilService.changeUser(TEST_PREFIX + "student1");
        request.put("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-submitted", null,
                HttpStatus.FORBIDDEN);
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        request.put("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-submitted", null,
                HttpStatus.FORBIDDEN);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-submitted", null,
                HttpStatus.CONFLICT);
        StudentExamDTO submitResponse = request.putWithResponseBody(
                "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-submitted", null, StudentExamDTO.class,
                HttpStatus.OK);
        assertThat(submitResponse.id()).isEqualTo(studentExam.getId());
        assertThat(submitResponse.submitted()).isTrue();
        assertThat(submitResponse.submissionDate()).isNotNull();
        // no exercise/user data leaks through this endpoint's response (see StudentExamDTO#of)
        assertThat(submitResponse.exam()).isNull();
        assertThat(submitResponse.user()).isNull();
        studentExam = studentExamRepository.findById(studentExam.getId()).orElseThrow();
        assertThat(studentExam.isSubmitted()).isTrue();
        assertThat(studentExam.getSubmissionDate()).isNotNull();

        // setting the exam to unsubmitted again
        userUtilService.changeUser(TEST_PREFIX + "student1");
        request.put("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-unsubmitted", null,
                HttpStatus.FORBIDDEN);
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        request.put("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-unsubmitted", null,
                HttpStatus.FORBIDDEN);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-unsubmitted", null,
                HttpStatus.CONFLICT);
        StudentExamDTO unsubmitResponse = request.putWithResponseBody(
                "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-unsubmitted", null, StudentExamDTO.class,
                HttpStatus.OK);
        assertThat(unsubmitResponse.id()).isEqualTo(studentExam.getId());
        assertThat(unsubmitResponse.submitted()).isFalse();
        assertThat(unsubmitResponse.submissionDate()).isNull();
        // no exercise/user data leaks through this endpoint's response (see StudentExamDTO#of)
        assertThat(unsubmitResponse.exam()).isNull();
        assertThat(unsubmitResponse.user()).isNull();
        studentExam = studentExamRepository.findById(studentExam.getId()).orElseThrow();
        assertThat(studentExam.isSubmitted()).isFalse();
        assertThat(studentExam.getSubmissionDate()).isNull();
    }

    // StudentExamResource - getStudentExamForTestExamForConduction
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForConduction_NoStudentExamFound() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + 5555L + "/conduction", HttpStatus.NOT_FOUND, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForConduction_NoExamAccess() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam2, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExam.getId() + "/conduction", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForConduction_NotVisible() throws Exception {
        Exam exam = examUtilService.addTestExam(course1);
        exam.setVisibleDate(ZonedDateTime.now().plusMinutes(60));
        examRepository.save(exam);
        StudentExam studentExam = examUtilService.addStudentExamForTestExam(exam, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExam.getId() + "/conduction", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetStudentExamForTestExamForConduction_UserIdMismatch() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExamForTestExam1.getId() + "/conduction",
                HttpStatus.FORBIDDEN, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetStudentExamForTestExamForConduction_realExam() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/conduction", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForConduction_success() throws Exception {
        StudentExam studentExamReceived = request.get(
                "/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExamForTestExam1.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);
        assertThat(studentExamReceived).isEqualTo(studentExamForTestExam1);
    }

    // StudentExamResource - getStudentExamsForCoursePerUser

    @Test
    @WithMockUser(username = OTHER_STUDENT, roles = "USER")
    void testGetStudentExamsForCoursePerUser_NoCourseAccess() throws Exception {
        examUtilService.addStudentExamForTestExam(testExam1, userUtilService.getUserByLogin(OTHER_STUDENT));
        request.getList("/api/exam/courses/" + course1.getId() + "/test-exams-per-user", HttpStatus.FORBIDDEN, StudentExamDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamsForCoursePerUser_success() throws Exception {
        examUtilService.addStudentExamForTestExam(exam2, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        List<StudentExamDTO> studentExamListReceived = request.getList("/api/exam/courses/" + course1.getId() + "/test-exams-per-user", HttpStatus.OK, StudentExamDTO.class);
        assertThat(studentExamListReceived).hasSizeGreaterThanOrEqualTo(2);
        assertThat(studentExamListReceived).extracting(StudentExamDTO::id).contains(studentExamForTestExam1.getId(), studentExamForTestExam2.getId());
        // the client reads exam.id/.course.id (setAccessRightsForCourse) and exam.workingTime/.testExam (working-time display)
        StudentExamDTO dtoForTestExam1 = studentExamListReceived.stream().filter(dto -> dto.id() == studentExamForTestExam1.getId()).findFirst().orElseThrow();
        StudentExamDTO dtoForTestExam2 = studentExamListReceived.stream().filter(dto -> dto.id() == studentExamForTestExam2.getId()).findFirst().orElseThrow();
        assertThat(dtoForTestExam1.exam()).isNotNull();
        assertThat(dtoForTestExam1.exam().id()).isEqualTo(testExam1.getId());
        assertThat(dtoForTestExam2.exam()).isNotNull();
        assertThat(dtoForTestExam2.exam().id()).isEqualTo(testExam2.getId());
        assertThat(studentExamListReceived).allSatisfy(dto -> {
            assertThat(dto.exam()).isNotNull();
            assertThat(dto.exam().testExam()).isTrue();
            assertThat(dto.exam().course()).isNotNull();
            assertThat(dto.exam().course().id()).isEqualTo(course1.getId());
            // user is intentionally omitted from this endpoint's response (see StudentExamDTO#withExam)
            assertThat(dto.user()).isNull();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamsForCoursePerUser_success_noStudentExams() throws Exception {
        course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        List<StudentExamDTO> studentExamListReceived = request.getList("/api/exam/courses/" + course2.getId() + "/test-exams-per-user", HttpStatus.OK, StudentExamDTO.class);
        assertThat(studentExamListReceived).isEmpty();
    }

    // StudentExamResource - getStudentExamForTestExamForSummary

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForSummary_NoStudentExamFound() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + 5555L + "/summary", HttpStatus.NOT_FOUND, StudentExam.class);
    }

    @Test
    @WithMockUser(username = OTHER_STUDENT, roles = "USER")
    void testGetStudentExamForTestExamForSummary_NoCourseAccess() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam1, userUtilService.getUserByLogin(OTHER_STUDENT));
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExam.getId() + "/summary", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = OTHER_STUDENT, roles = "USER")
    void testGetStudentExamForTestExamForSummary_NoExamAccess() throws Exception {
        Exam exam99 = examUtilService.addTestExam(course1);
        StudentExam studentExam99 = examUtilService.addStudentExamForTestExam(exam99, userUtilService.getUserByLogin(OTHER_STUDENT));
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExam99.getId() + "/summary", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForSummary_NotVisible() throws Exception {
        Exam exam = examUtilService.addTestExam(course1);
        exam.setVisibleDate(ZonedDateTime.now().plusMinutes(60));
        examRepository.save(exam);
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExamForTestExam2.getId() + "/summary", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetStudentExamForTestExamForSummary_UserIdMismatch() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExamForTestExam2.getId() + "/summary", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForSummary_realExam() throws Exception {
        studentExam1.setSubmitted(true);
        studentExamRepository.save(studentExam1);
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/summary", HttpStatus.OK, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForSummary_success() throws Exception {
        StudentExam studentExamReceived = request.get(
                "/api/exam/courses/" + course1.getId() + "/exams/" + testExam2.getId() + "/student-exams/" + studentExamForTestExam2.getId() + "/summary", HttpStatus.OK,
                StudentExam.class);
        assertThat(studentExamReceived).isEqualTo(studentExamForTestExam2);
    }

    // StudentExamRessource - GetStudentExamForConduction
    @Test
    @WithMockUser(username = OTHER_STUDENT, roles = "USER")
    void testGetStudentExamForConduction_notRegisteredInCourse() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExam1.getId() + "/conduction", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForConduction_studentExamNotExistent() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + 5555L + "/conduction", HttpStatus.NOT_FOUND, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForConduction_examIdNotMatching() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + 2 + "/student-exams/" + studentExam1.getId() + "/conduction", HttpStatus.CONFLICT,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForConduction_realExam() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForConduction_successful() throws Exception {
        StudentExam studentExamRetrieved = request.get(
                "/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExamForTestExam1.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);
        assertThat(studentExamRetrieved).isEqualTo(studentExamForTestExam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForConduction_notVisible() throws Exception {
        Exam testExam = examUtilService.addTestExam(course1);
        testExam.setVisibleDate(ZonedDateTime.now().plusMinutes(60));
        testExam = examRepository.save(testExam);
        StudentExam studentExam = examUtilService.addStudentExamWithUser(testExam, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam.getId() + "/student-exams/" + studentExam.getId() + "/conduction", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testConductionOfTestExam_successful() throws Exception {
        Exam testExamWithExercises = examUtilService.addTestExam(course1);
        testExamWithExercises = examUtilService.addTextModelingProgrammingExercisesToExam(testExamWithExercises, false, true);
        testExamWithExercises.setExamMaxPoints(19);
        testExamWithExercises.setVisibleDate(ZonedDateTime.now().minusHours(1));
        testExamWithExercises.setStartDate(ZonedDateTime.now().minusMinutes(30));
        testExamWithExercises.setWorkingTime(6000);
        var examUser5 = new ExamUser();
        examUser5.setExam(testExamWithExercises);
        examUser5.setUser(student1);
        examUser5 = examUserRepository.save(examUser5);
        testExamWithExercises.addExamUser(examUser5);
        testExamWithExercises = examRepository.save(testExamWithExercises);

        // Step 1: Call /start
        StudentExam studentExamForStart = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExamWithExercises.getId() + "/own-student-exam", HttpStatus.OK,
                StudentExam.class);

        assertThat(studentExamForStart.getUser()).isEqualTo(student1);
        assertThat(studentExamForStart.getExam().getId()).isEqualTo(testExamWithExercises.getId());
        assertThat(studentExamForStart.isStarted()).isNull();
        assertThat(studentExamForStart.isSubmitted()).isFalse();
        assertThat(studentExamForStart.getStartedDate()).isNull();
        assertThat(studentExamForStart.getSubmissionDate()).isNull();
        assertThat(studentExamForStart.getExercises()).hasSize(0);

        // Step 2: Call /conduction to get the exam with exercises and started date set
        StudentExam studentExamForConduction = request.get(
                "/api/exam/courses/" + course1.getId() + "/exams/" + testExamWithExercises.getId() + "/student-exams/" + studentExamForStart.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);

        assertThat(studentExamForConduction.getId()).isEqualTo(studentExamForStart.getId());
        assertThat(studentExamForConduction.getUser()).isEqualTo(student1);
        assertThat(studentExamForConduction.getExam().getId()).isEqualTo(testExamWithExercises.getId());
        assertThat(studentExamForConduction.isStarted()).isTrue();
        assertThat(studentExamForConduction.isSubmitted()).isFalse();
        // Acceptance range, startedDate is to be set to now()
        assertThat(ZonedDateTime.now().minusSeconds(10).isBefore(studentExamForConduction.getStartedDate())).isTrue();
        assertThat(ZonedDateTime.now().plusSeconds(10).isAfter(studentExamForConduction.getStartedDate())).isTrue();
        assertThat(studentExamForConduction.getSubmissionDate()).isNull();
        assertThat(studentExamForConduction.getExercises()).hasSize(3);
        QuizExercise quizExercise = (QuizExercise) studentExamForConduction.getExercises().get(2);
        assertThat(quizExercise.getQuizQuestions()).hasSize(3);

        Map<User, List<Exercise>> exercisesOfUser = studentExamService.getExercisesOfUserMap(Set.of(studentExamForConduction));
        final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionResultIgnoreTestRuns(student1.getId(),
                exercisesOfUser.get(student1));
        for (StudentParticipation studentParticipation : studentParticipations) {
            // Acceptance range, initialization Date is to be set to now()
            assertThat(ZonedDateTime.now().minusSeconds(10).isBefore(studentParticipation.getInitializationDate())).isTrue();
            assertThat(ZonedDateTime.now().plusSeconds(10).isAfter(studentParticipation.getInitializationDate())).isTrue();
            // Compare started date and initialization Date
            studentExamForConduction
                    .setStartedAndStartDate(ZonedDateTime.ofInstant(studentExamForConduction.getStartedDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
            studentParticipation
                    .setInitializationDate(ZonedDateTime.ofInstant(studentParticipation.getInitializationDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
            assertThat(studentParticipation.getInitializationDate()).isCloseTo(studentExamForConduction.getStartedDate(), within(1, ChronoUnit.SECONDS));
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLongestWorkingTimeForExam() throws Exception {
        // Step 1: Create mock student exams
        List<StudentExam> studentExams = prepareStudentExamsForConduction(false, true, NUMBER_OF_STUDENTS);

        // Step 2: Get the maximum working time among the exams to find the longest time
        final int longestWorkingTime = studentExams.stream().mapToInt(StudentExam::getWorkingTime).max().orElse(0);

        // When
        final int response = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/longest-working-time", HttpStatus.OK, Integer.class);

        // Then
        assertThat(response).isEqualTo(longestWorkingTime);
    }

    @Nested
    class ChangedAndUnchangedSubmissionsIntegrationTest {

        // the bare hand-in: read the student exam with its exercises, mark it submitted, and read the participations with
        // their submissions to compare the last-second changes. The separate identity read is gone, because the student
        // exam already carries its owner.
        private final int BASE_QUERY_COUNT = 5;

        // Measured baselines for the endpoints the exam simulation drives. They are upper bounds, so a new query fails
        // the build; lower them whenever a change removes one, and never raise one without saying in the PR what it
        // bought.
        //
        // NOTE on the submission endpoints: the submission-version write is @Async in production but its executor is a
        // SyncTaskExecutor under the test profile (see AsyncConfiguration#submissionVersionExecutor), so its INSERT is
        // counted here even though a real student never waits for it.

        // exam start: user with course roles, student exam with exercises, mark started, submission policies, quiz
        // questions, participations with latest submission and result, submitted answers, exam session insert + count
        private final int CONDUCTION_QUERY_COUNT = 8;

        // starting an exercise whose participation was already generated: exercise, user, student-exam working time
        // projection, existing participation, and the terminal save
        private final int START_PARTICIPATION_QUERY_COUNT = 7;

        // text autosave: result-exists probe, user with course roles, exercise, submission-gate projection, exam,
        // participations (twice, see below), scalar ownership check, participation state update, submission update,
        // plus the four statements of the submission-version write that only run on this thread under test
        private final int TEXT_AUTOSAVE_QUERY_COUNT = 14;

        private final int MODELING_AUTOSAVE_QUERY_COUNT = 14;

        // the quiz path additionally loads the quiz exercise with its question tree to re-resolve the submitted answers,
        // and its submission save stays a merge because it cascades to the submitted answers. It must NOT contain an
        // update of quiz_question: a student's submission may never write a shared question row (see
        // QuizQuestionContent#haveEqualPersistedForm), and this count is what keeps that write from coming back.
        private final int QUIZ_SUBMISSION_QUERY_COUNT = 17;

        // exam summary: user with course roles, student exam with its exercises' groups, exam, quiz questions,
        // participations with latest submission and result, submitted answers
        private final int SUMMARY_QUERY_COUNT = 9;

        private TextExercise textExercise;

        private ModelingExercise modeExercise;

        private QuizExercise quizExercise;

        private TextSubmission textSubmission;

        private ModelingSubmission modeSubmission;

        private QuizSubmission quizSubmission;

        private DragAndDropQuestion dragAndDropQuestion;

        private MultipleChoiceQuestion multipleChoiceQuestion;

        private ShortAnswerQuestion shortAnswerQuestion;

        private StudentExam studentExamForConduction;

        @BeforeEach
        void setUpStudentExamWithExercises() throws Exception {
            userUtilService.changeUser(TEST_PREFIX + "instructor1");

            // Add exercises to active exam
            exam1.setExamMaxPoints(19);
            exam1 = examUtilService.addExerciseGroupsAndExercisesToExam(exam1, false);

            // Generate student exam (the response masks the nested exam/user; re-fetch the single managed entity below)
            List<StudentExamDTO> studentExams = request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/generate-student-exams",
                    Optional.empty(), StudentExamDTO.class, HttpStatus.OK);
            assertThat(studentExams).hasSize(exam1.getExamUsers().size());
            assertThat(studentExamRepository.findByExamId(exam1.getId())).hasSize(1);

            // Prepare student exam
            ExamPrepareExercisesTestUtil.prepareExerciseStart(request, exam1, course1);
            StudentExam studentExam = studentExamRepository.findByExamId(exam1.getId()).iterator().next();
            userUtilService.changeUser(studentExam.getUser().getLogin());
            studentExamForConduction = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                    HttpStatus.OK, StudentExam.class);
            assertThat(studentExamForConduction.isStarted()).isTrue();

            // Get exercises for testing
            textExercise = ExerciseUtilService.getFirstExerciseWithType(studentExamForConduction, TextExercise.class);
            modeExercise = ExerciseUtilService.getFirstExerciseWithType(studentExamForConduction, ModelingExercise.class);
            quizExercise = ExerciseUtilService.getFirstExerciseWithType(studentExamForConduction, QuizExercise.class);

            // Get quiz questions for testing
            for (QuizQuestion quizQuestion : quizExercise.getQuizQuestions()) {
                if (quizQuestion instanceof DragAndDropQuestion && dragAndDropQuestion == null) {
                    dragAndDropQuestion = (DragAndDropQuestion) quizQuestion;
                }
                else if (quizQuestion instanceof MultipleChoiceQuestion && multipleChoiceQuestion == null) {
                    multipleChoiceQuestion = (MultipleChoiceQuestion) quizQuestion;
                }
                else if (quizQuestion instanceof ShortAnswerQuestion && shortAnswerQuestion == null) {
                    shortAnswerQuestion = (ShortAnswerQuestion) quizQuestion;
                }
            }
            assertThat(dragAndDropQuestion).isNotNull();
            assertThat(multipleChoiceQuestion).isNotNull();
            assertThat(shortAnswerQuestion).isNotNull();

            textSubmission = (TextSubmission) textExercise.getStudentParticipations().iterator().next().findLatestSubmission().orElseThrow();
            modeSubmission = (ModelingSubmission) modeExercise.getStudentParticipations().iterator().next().findLatestSubmission().orElseThrow();
            quizSubmission = (QuizSubmission) quizExercise.getStudentParticipations().iterator().next().findLatestSubmission().orElseThrow();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testConductionQueryCount() throws Exception {
            // Guards the exam-start endpoint against new queries and against N+1 regressions: this exam has several
            // exercises including a quiz, so a per-exercise or per-submission query would show up here as growth. The
            // user is loaded with its course roles, so the instructor check in the participation filter costs nothing.
            assertThatDb(
                    () -> request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/conduction",
                            HttpStatus.OK, StudentExam.class))
                    .hasBeenCalledAtMostTimes(CONDUCTION_QUERY_COUNT);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testStartParticipationQueryCount() throws Exception {
            // The exam participations were prepared up front, so this is the hot "participation already exists" path the
            // client hits on every (re)entry into an exercise.
            assertThatDb(() -> request.postWithResponseBody("/api/exercise/exercises/" + textExercise.getId() + "/participations", null, Participation.class, HttpStatus.CREATED))
                    .hasBeenCalledAtMostTimes(START_PARTICIPATION_QUERY_COUNT);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testTextSubmissionAutosaveQueryCount() throws Exception {
            textSubmission.setText("A changed answer that has to be persisted");
            assertThatDb(() -> {
                request.put("/api/text/exercises/" + textExercise.getId() + "/text-submissions", toRequestDTO(textSubmission), HttpStatus.OK);
                return null;
            }).hasBeenCalledAtMostTimes(TEXT_AUTOSAVE_QUERY_COUNT);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testModelingSubmissionAutosaveQueryCount() throws Exception {
            changeModelingSubmission("A changed model", "A changed explanation");
            assertThatDb(() -> {
                request.put("/api/modeling/exercises/" + modeExercise.getId() + "/modeling-submissions", modeSubmission, HttpStatus.OK);
                return null;
            }).hasBeenCalledAtMostTimes(MODELING_AUTOSAVE_QUERY_COUNT);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testQuizSubmissionQueryCount() throws Exception {
            getChangedAnswerOptions(List.of(0, 1));
            assertThatDb(() -> {
                request.put("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/exam", quizSubmission, HttpStatus.OK);
                return null;
            }).hasBeenCalledAtMostTimes(QUIZ_SUBMISSION_QUERY_COUNT);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testSummaryQueryCount() throws Exception {
            request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction, StudentExam.class,
                    HttpStatus.OK);
            assertThatDb(() -> request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary",
                    HttpStatus.OK, StudentExam.class)).hasBeenCalledAtMostTimes(SUMMARY_QUERY_COUNT);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testUnchangedSubmissionsDoNotChangeQueryCount() throws Exception {
            // The conduction quiz submission carries no answers, so the reconstruction skips the quiz question-tree load
            // (FIX 5) and no content actually changes, so nothing is persisted: the hand-in stays at the bare skeleton
            // cost. Tightened from BASE + QUIZ_RECONSTRUCTION to just BASE now that the empty-answer reload is elided.
            assertThatDb(() -> request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction,
                    StudentExam.class, HttpStatus.OK)).hasBeenCalledAtMostTimes(BASE_QUERY_COUNT);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testChangedAndSubmittedDoesNotChangeQueryCount() throws Exception {
            // Given
            final String changedAnswer = "This is a changed and submitted answer";
            textSubmission.setText(changedAnswer);
            request.put("/api/text/exercises/" + textExercise.getId() + "/text-submissions", toRequestDTO(textSubmission), HttpStatus.OK);

            final String changedModel = "This is a changed and submitted model";
            final String changedExplanation = "This is a changed and submitted explanation";
            changeModelingSubmission(changedModel, changedExplanation);
            request.put("/api/modeling/exercises/" + modeExercise.getId() + "/modeling-submissions", modeSubmission, HttpStatus.OK);

            DragAndDropMapping changedMapping = getchangedDragAndDropMapping(1, 0);

            final String text = "Changed and submitted short answer text";
            final int spotIndex = 0;
            ShortAnswerSubmittedText changedText = getChangedShortAnswerSubmittedText(text, spotIndex);

            final List<Integer> selectedOptionIndices = List.of(0, 1);
            List<AnswerOption> changedAnswerOptions = getChangedAnswerOptions(selectedOptionIndices);

            request.put("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/exam", quizSubmission, HttpStatus.OK);

            // Persisting the changed quiz, text and modeling submissions on top of the bare hand-in: the quiz question
            // tree for the DTO reconstruction, the submitted answers, and the writes themselves.
            final int changedSubmissionsQueryCount = 7;

            // When
            assertThatDb(() -> request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction,
                    StudentExam.class, HttpStatus.OK)).hasBeenCalledAtMostTimes(BASE_QUERY_COUNT + changedSubmissionsQueryCount);
            StudentExam submittedExam = request.get(
                    "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);

            // Then
            TextExercise textExerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, TextExercise.class);
            TextSubmission textSubmissionAfterExamSubmission = (TextSubmission) textExerciseAfterExamSubmission.getStudentParticipations().iterator().next().findLatestSubmission()
                    .orElseThrow();
            assertThat(textSubmissionAfterExamSubmission).isEqualTo(textSubmission);
            assertThat(textSubmissionAfterExamSubmission.getText()).isEqualTo(changedAnswer);
            assertVersionedSubmission(textSubmission);
            assertVersionedSubmission(textSubmissionAfterExamSubmission);

            ModelingExercise modeExerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, ModelingExercise.class);
            ModelingSubmission modeSubmissionAfterExamSubmission = (ModelingSubmission) modeExerciseAfterExamSubmission.getStudentParticipations().iterator().next()
                    .findLatestSubmission().orElseThrow();
            assertThat(modeSubmissionAfterExamSubmission).isEqualTo(modeSubmission);
            assertThat(modeSubmissionAfterExamSubmission.getModel()).isEqualTo(changedModel);
            assertThat(modeSubmissionAfterExamSubmission.getExplanationText()).isEqualTo(changedExplanation);
            assertVersionedSubmission(modeSubmission);
            assertVersionedSubmission(modeSubmissionAfterExamSubmission);

            QuizExercise quizExerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, QuizExercise.class);
            QuizSubmission quizSubmissionAfterExamSubmission = (QuizSubmission) quizExerciseAfterExamSubmission.getStudentParticipations().iterator().next().findLatestSubmission()
                    .orElseThrow();
            assertThat(quizSubmissionAfterExamSubmission).isEqualTo(quizSubmission);
            assertVersionedSubmission(quizSubmission);
            assertVersionedSubmission(quizSubmissionAfterExamSubmission);

            verifyDragAndDropSubmission(changedMapping, quizSubmissionAfterExamSubmission);
            verifyShortAnswerSubmission(changedText, quizSubmissionAfterExamSubmission);
            verifyMultipleChoiceSubmission(changedAnswerOptions, quizSubmissionAfterExamSubmission);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testPoisonedExerciseIsDroppedWhileHandInSucceedsAndOtherExercisesSave() throws Exception {
            // Governing principle: the exam is ALWAYS marked submitted, and a broken per-exercise submission degrades to
            // a logged drop of only that exercise's answers — never a 5xx and never data loss for other exercises. Here
            // the quiz submission references a non-existent submission id, so the per-exercise save rejects and swallows
            // it, while the healthy text change is still persisted and the exam is submitted.
            final String healthyAnswer = "This healthy text answer must survive a poisoned sibling exercise";
            textSubmission.setText(healthyAnswer);
            quizSubmission.setId(999_999_999L);

            // must return 200 (not 5xx): the hand-in is never aborted by a broken exercise
            request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction, StudentExam.class,
                    HttpStatus.OK);

            // the exam is marked submitted despite the poisoned quiz
            StudentExam submitted = studentExamRepository.findById(studentExamForConduction.getId()).orElseThrow();
            assertThat(submitted.isSubmitted()).isTrue();
            assertThat(submitted.getSubmissionDate()).isNotNull();

            // no data loss for the healthy sibling: the changed text answer is persisted
            StudentExam summary = request.get(
                    "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);
            TextExercise textExerciseAfter = ExerciseUtilService.getFirstExerciseWithType(summary, TextExercise.class);
            TextSubmission textSubmissionAfter = (TextSubmission) textExerciseAfter.getStudentParticipations().iterator().next().findLatestSubmission().orElseThrow();
            assertThat(textSubmissionAfter.getText()).isEqualTo(healthyAnswer);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testChangedAndNotSubmittedTextSubmission() throws Exception {
            // Given
            final String changedAnswer = "This is a changed answer";
            textSubmission.setText(changedAnswer);

            // When
            request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction, StudentExam.class,
                    HttpStatus.OK);
            StudentExam submittedExam = request.get(
                    "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);
            TextExercise exerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, TextExercise.class);
            TextSubmission submissionAfterExamSubmission = (TextSubmission) exerciseAfterExamSubmission.getStudentParticipations().iterator().next().findLatestSubmission()
                    .orElseThrow();

            // Then
            assertThat(submissionAfterExamSubmission).isEqualTo(textSubmission);
            assertThat(submissionAfterExamSubmission.getText()).isEqualTo(changedAnswer);
            assertVersionedSubmission(textSubmission);
            assertVersionedSubmission(submissionAfterExamSubmission);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testHandInPersistsTextSubmissionLanguageFromSlimDto() throws Exception {
            // Wire-parity regression guard: the slim submit DTO must carry the client-detected language. The downstream
            // save (saveSubmissionTextExercise) persists the reconstructed submission via a JPA merge that overwrites
            // every mapped column, so a language dropped from the DTO would be nulled out on this hand-in. The conduction
            // submission starts with a null language; here we set it the way the client does (predictLanguage) and change
            // the text so the content-equality short-circuit does not skip the save, hand in, then re-read the row.
            assertThat(textSubmission.getLanguage()).as("the conduction submission starts without a language").isNull();
            final String changedAnswer = "Dies ist eine geänderte Antwort auf Deutsch";
            textSubmission.setText(changedAnswer);
            textSubmission.setLanguage(Language.GERMAN);

            request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction, StudentExam.class,
                    HttpStatus.OK);

            // fresh query straight from the repository (not the summary DTO): both the text AND the language must have
            // been persisted, proving the language survived the slim-DTO round-trip and the overwriting merge.
            TextSubmission persisted = (TextSubmission) submissionRepository.findById(textSubmission.getId()).orElseThrow();
            assertThat(persisted.getText()).isEqualTo(changedAnswer);
            assertThat(persisted.getLanguage()).isEqualTo(Language.GERMAN);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testChangedAndNotSubmittedModelingSubmission() throws Exception {
            // Given
            final String changedModel = "This is a changed model";
            final String changedExplanation = "This is a changed explanation";
            changeModelingSubmission(changedModel, changedExplanation);

            // When
            request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction, StudentExam.class,
                    HttpStatus.OK);
            StudentExam submittedExam = request.get(
                    "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);
            ModelingExercise exerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, ModelingExercise.class);
            ModelingSubmission submissionAfterExamSubmission = (ModelingSubmission) exerciseAfterExamSubmission.getStudentParticipations().iterator().next().findLatestSubmission()
                    .orElseThrow();

            // Then
            assertThat(submissionAfterExamSubmission).isEqualTo(modeSubmission);
            assertThat(submissionAfterExamSubmission.getModel()).isEqualTo(changedModel);
            assertThat(submissionAfterExamSubmission.getExplanationText()).isEqualTo(changedExplanation);
            assertVersionedSubmission(modeSubmission);
            assertVersionedSubmission(submissionAfterExamSubmission);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testChangedAndNotSubmittedDragAndDropQuestionSubmission() throws Exception {
            // Given
            DragAndDropMapping changedMapping = getchangedDragAndDropMapping(0, 1);

            // When
            request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction, HttpStatus.OK);
            StudentExam submittedExam = request.get(
                    "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);
            QuizExercise exerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, QuizExercise.class);
            QuizSubmission submissionAfterExamSubmission = (QuizSubmission) exerciseAfterExamSubmission.getStudentParticipations().iterator().next().findLatestSubmission()
                    .orElseThrow();

            // Then
            assertThat(submissionAfterExamSubmission).isEqualTo(quizSubmission);
            assertVersionedSubmission(quizSubmission);
            assertVersionedSubmission(submissionAfterExamSubmission);

            verifyDragAndDropSubmission(changedMapping, submissionAfterExamSubmission);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testChangedAndNotSubmittedShortAnswerQuestionSubmission() throws Exception {
            // Given
            getChangedShortAnswerSubmittedText("First changed and submitted answer", 0);
            request.put("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/exam", quizSubmission, HttpStatus.OK);
            quizSubmission.removeSubmittedAnswers(quizSubmission.getSubmittedAnswers().iterator().next());

            final String text = "Changed short answer text";
            final int spotIndex = 1;
            ShortAnswerSubmittedText changedText = getChangedShortAnswerSubmittedText(text, spotIndex);

            // When
            request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction, HttpStatus.OK);
            StudentExam submittedExam = request.get(
                    "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);
            QuizExercise exerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, QuizExercise.class);
            QuizSubmission submissionAfterExamSubmission = (QuizSubmission) exerciseAfterExamSubmission.getStudentParticipations().iterator().next().findLatestSubmission()
                    .orElseThrow();

            // Then
            assertThat(submissionAfterExamSubmission).isEqualTo(quizSubmission);
            assertVersionedSubmission(quizSubmission);
            assertVersionedSubmission(submissionAfterExamSubmission);

            verifyShortAnswerSubmission(changedText, submissionAfterExamSubmission);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testChangedAndNotSubmittedMultipleChoiceQuestionSubmission() throws Exception {
            // Given
            final List<Integer> selectedOptionIndices = List.of(1);
            List<AnswerOption> changedAnswerOptions = getChangedAnswerOptions(selectedOptionIndices);

            // When
            request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction, HttpStatus.OK);
            StudentExam submittedExam = request.get(
                    "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);
            QuizExercise exerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, QuizExercise.class);
            QuizSubmission submissionAfterExamSubmission = (QuizSubmission) exerciseAfterExamSubmission.getStudentParticipations().iterator().next().findLatestSubmission()
                    .orElseThrow();

            // Then
            assertThat(submissionAfterExamSubmission).isEqualTo(quizSubmission);
            assertVersionedSubmission(quizSubmission);
            assertVersionedSubmission(submissionAfterExamSubmission);

            verifyMultipleChoiceSubmission(changedAnswerOptions, submissionAfterExamSubmission);
        }

        private void changeModelingSubmission(String changedModel, String changedExplanation) {
            modeSubmission.setModel(changedModel);
            modeSubmission.setExplanationText(changedExplanation);
        }

        private DragAndDropMapping getchangedDragAndDropMapping(int dndDragItemIndex, int dndDropLocationIndex) {
            DragAndDropMapping changedMapping = new DragAndDropMapping();

            changedMapping.setDragItemIndex(dndDragItemIndex);
            changedMapping.setDragItem(dragAndDropQuestion.getDragItems().get(dndDragItemIndex));

            changedMapping.setDropLocationIndex(dndDropLocationIndex);
            changedMapping.setDropLocation(dragAndDropQuestion.getDropLocations().get(dndDropLocationIndex));

            DragAndDropSubmittedAnswer changedAnswer = new DragAndDropSubmittedAnswer();
            changedAnswer.setQuizQuestion(dragAndDropQuestion);
            changedAnswer.addMappings(changedMapping);

            quizSubmission.getSubmittedAnswers().add(changedAnswer);
            return changedMapping;
        }

        private ShortAnswerSubmittedText getChangedShortAnswerSubmittedText(String text, int spotIndex) {
            ShortAnswerSubmittedText changedText = new ShortAnswerSubmittedText();
            changedText.setText(text);
            changedText.setSpot(shortAnswerQuestion.getSpots().get(spotIndex));

            ShortAnswerSubmittedAnswer changedAnswer = new ShortAnswerSubmittedAnswer();
            changedAnswer.setQuizQuestion(shortAnswerQuestion);
            changedAnswer.addSubmittedTexts(changedText);

            quizSubmission.getSubmittedAnswers().add(changedAnswer);
            return changedText;
        }

        private List<AnswerOption> getChangedAnswerOptions(List<Integer> selectedOptionIndices) {
            List<AnswerOption> answerOptions = multipleChoiceQuestion.getAnswerOptions();

            MultipleChoiceSubmittedAnswer changedAnswer = new MultipleChoiceSubmittedAnswer();
            selectedOptionIndices.forEach(selectedOptionIndex -> changedAnswer.addSelectedOptions(answerOptions.get(selectedOptionIndex)));
            changedAnswer.setQuizQuestion(multipleChoiceQuestion);

            quizSubmission.getSubmittedAnswers().add(changedAnswer);
            return selectedOptionIndices.stream().map(answerOptions::get).toList();
        }

        private void verifyDragAndDropSubmission(DragAndDropMapping changedMapping, QuizSubmission submissionAfterExamSubmission) {
            DragAndDropSubmittedAnswer answerAfterSubmission = (DragAndDropSubmittedAnswer) submissionAfterExamSubmission.getSubmittedAnswerForQuestion(dragAndDropQuestion);
            Comparator<DragAndDropMapping> dndMappingComparator = Comparator.comparing(DragAndDropMapping::getDragItemIndex)
                    .thenComparing(DragAndDropMapping::getDropLocationIndex);

            assertThat(answerAfterSubmission.getMappings()).hasSize(1);
            assertThat(answerAfterSubmission.getMappings().iterator().next()).usingComparator(dndMappingComparator).isEqualTo(changedMapping);
        }

        private void verifyShortAnswerSubmission(ShortAnswerSubmittedText changedText, QuizSubmission submissionAfterExamSubmission) {
            ShortAnswerSubmittedAnswer answerAfterSubmission = (ShortAnswerSubmittedAnswer) submissionAfterExamSubmission.getSubmittedAnswerForQuestion(shortAnswerQuestion);
            Comparator<ShortAnswerSubmittedText> saMappingComparator = Comparator.comparing(ShortAnswerSubmittedText::getText).thenComparing(saText -> saText.getSpot().getId());

            assertThat(answerAfterSubmission.getSubmittedTexts()).hasSize(1);
            assertThat(answerAfterSubmission.getSubmittedTexts().iterator().next()).usingComparator(saMappingComparator).isEqualTo(changedText);
        }

        private void verifyMultipleChoiceSubmission(List<AnswerOption> changedAnswerOption, QuizSubmission submissionAfterExamSubmission) {
            MultipleChoiceSubmittedAnswer answerAfterSubmission = (MultipleChoiceSubmittedAnswer) submissionAfterExamSubmission
                    .getSubmittedAnswerForQuestion(multipleChoiceQuestion);

            assertThat(answerAfterSubmission.toSelectedIds()).containsAll(changedAnswerOption.stream().map(AnswerOption::getId).collect(Collectors.toSet()));
        }

        private long participationId(Exercise exercise) {
            return exercise.getStudentParticipations().iterator().next().getId();
        }

        private ObjectNode idRef(long id) {
            return objectMapper.createObjectNode().put("id", id);
        }

        private ObjectNode participationNode(long participationId, ArrayNode submissions) {
            ObjectNode participation = objectMapper.createObjectNode();
            participation.put("id", participationId);
            participation.set("submissions", submissions);
            return participation;
        }

        private ObjectNode exerciseNode(long exerciseId, ArrayNode participations) {
            ObjectNode exercise = objectMapper.createObjectNode();
            exercise.put("id", exerciseId);
            exercise.set("studentParticipations", participations);
            return exercise;
        }

        private ArrayNode arrayOf(ObjectNode... nodes) {
            ArrayNode array = objectMapper.createArrayNode();
            for (ObjectNode node : nodes) {
                array.add(node);
            }
            return array;
        }

        /**
         * Trap #4 + slim-path proof: post the actual slim {@code SubmitStudentExamDTO} wire shape (NOT a full entity),
         * then reload from fresh queries and assert every answer type persisted exactly. A silently-dropped field would
         * leave the persisted content unchanged and this test would fail.
         */
        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testSubmitViaSlimDtoPersistsEveryAnswerTypeFromFreshQueries() throws Exception {
            long selectedOptionId = multipleChoiceQuestion.getAnswerOptions().get(1).getId();
            long dragItemId = dragAndDropQuestion.getDragItems().get(0).getId();
            long dropLocationId = dragAndDropQuestion.getDropLocations().get(1).getId();
            long spotId = shortAnswerQuestion.getSpots().get(0).getId();

            ObjectNode mcAnswer = objectMapper.createObjectNode();
            mcAnswer.put("type", "multiple-choice");
            mcAnswer.set("quizQuestion", idRef(multipleChoiceQuestion.getId()));
            mcAnswer.set("selectedOptions", arrayOf(idRef(selectedOptionId)));

            ObjectNode dndMapping = objectMapper.createObjectNode();
            dndMapping.set("dragItem", idRef(dragItemId));
            dndMapping.set("dropLocation", idRef(dropLocationId));
            ObjectNode dndAnswer = objectMapper.createObjectNode();
            dndAnswer.put("type", "drag-and-drop");
            dndAnswer.set("quizQuestion", idRef(dragAndDropQuestion.getId()));
            dndAnswer.set("mappings", arrayOf(dndMapping));

            ObjectNode saText = objectMapper.createObjectNode();
            saText.put("text", "slim short answer");
            saText.set("spot", idRef(spotId));
            ObjectNode saAnswer = objectMapper.createObjectNode();
            saAnswer.put("type", "short-answer");
            saAnswer.set("quizQuestion", idRef(shortAnswerQuestion.getId()));
            saAnswer.set("submittedTexts", arrayOf(saText));

            ObjectNode textSubmissionNode = objectMapper.createObjectNode();
            textSubmissionNode.put("submissionExerciseType", "text");
            textSubmissionNode.put("id", textSubmission.getId());
            textSubmissionNode.put("text", "slim text answer");

            ObjectNode modelingSubmissionNode = objectMapper.createObjectNode();
            modelingSubmissionNode.put("submissionExerciseType", "modeling");
            modelingSubmissionNode.put("id", modeSubmission.getId());
            modelingSubmissionNode.put("model", "slim model");
            modelingSubmissionNode.put("explanationText", "slim explanation");

            ObjectNode quizSubmissionNode = objectMapper.createObjectNode();
            quizSubmissionNode.put("submissionExerciseType", "quiz");
            quizSubmissionNode.put("id", quizSubmission.getId());
            quizSubmissionNode.set("submittedAnswers", arrayOf(mcAnswer, dndAnswer, saAnswer));

            ArrayNode exercises = arrayOf(exerciseNode(textExercise.getId(), arrayOf(participationNode(participationId(textExercise), arrayOf(textSubmissionNode)))),
                    exerciseNode(modeExercise.getId(), arrayOf(participationNode(participationId(modeExercise), arrayOf(modelingSubmissionNode)))),
                    exerciseNode(quizExercise.getId(), arrayOf(participationNode(participationId(quizExercise), arrayOf(quizSubmissionNode)))));
            ObjectNode body = objectMapper.createObjectNode();
            body.put("id", studentExamForConduction.getId());
            body.set("exercises", exercises);

            request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", body, HttpStatus.OK);

            // reload text + modeling from fresh repository queries
            TextSubmission reloadedText = (TextSubmission) submissionRepository.findById(textSubmission.getId()).orElseThrow();
            assertThat(reloadedText.getText()).isEqualTo("slim text answer");
            ModelingSubmission reloadedModeling = (ModelingSubmission) submissionRepository.findById(modeSubmission.getId()).orElseThrow();
            assertThat(reloadedModeling.getModel()).isEqualTo("slim model");
            assertThat(reloadedModeling.getExplanationText()).isEqualTo("slim explanation");

            // reload quiz answers via a fresh summary request and assert each answer type persisted
            StudentExam submittedExam = request.get(
                    "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);
            QuizSubmission reloadedQuiz = (QuizSubmission) ExerciseUtilService.getFirstExerciseWithType(submittedExam, QuizExercise.class).getStudentParticipations().iterator()
                    .next().findLatestSubmission().orElseThrow();
            MultipleChoiceSubmittedAnswer mc = (MultipleChoiceSubmittedAnswer) reloadedQuiz.getSubmittedAnswerForQuestion(multipleChoiceQuestion);
            assertThat(mc.toSelectedIds()).containsExactly(selectedOptionId);
            DragAndDropSubmittedAnswer dnd = (DragAndDropSubmittedAnswer) reloadedQuiz.getSubmittedAnswerForQuestion(dragAndDropQuestion);
            assertThat(dnd.getMappings()).hasSize(1);
            assertThat(dnd.getMappings().iterator().next().getDragItem().getId()).isEqualTo(dragItemId);
            assertThat(dnd.getMappings().iterator().next().getDropLocation().getId()).isEqualTo(dropLocationId);
            ShortAnswerSubmittedAnswer sa = (ShortAnswerSubmittedAnswer) reloadedQuiz.getSubmittedAnswerForQuestion(shortAnswerQuestion);
            assertThat(sa.getSubmittedTexts()).hasSize(1);
            assertThat(sa.getSubmittedTexts().iterator().next().getText()).isEqualTo("slim short answer");
            assertThat(sa.getSubmittedTexts().iterator().next().getSpot().getId()).isEqualTo(spotId);
        }

        /**
         * Trap #3: a raw body with explicit empty arrays (the shape a real client emits, which a DTO-serialized test
         * body would silently omit via {@code @JsonInclude(NON_EMPTY)}). The submit must return 200, mark the exam
         * submitted, and persist nothing — matching the early-return / skip semantics.
         */
        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testSubmitViaSlimDtoWithExplicitEmptyExercisesArrayMarksSubmittedWithoutSaving() throws Exception {
            String originalText = ((TextSubmission) submissionRepository.findById(textSubmission.getId()).orElseThrow()).getText();

            ObjectNode body = objectMapper.createObjectNode();
            body.put("id", studentExamForConduction.getId());
            body.set("exercises", objectMapper.createArrayNode());

            request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", body, HttpStatus.OK);

            assertThat(studentExamRepository.findById(studentExamForConduction.getId()).orElseThrow().isSubmitted()).isTrue();
            assertThat(((TextSubmission) submissionRepository.findById(textSubmission.getId()).orElseThrow()).getText()).isEqualTo(originalText);
        }

        /**
         * Trap #3, nested variant: an exercise whose participation carries an explicit empty submissions array, and a
         * quiz submission with an explicit empty submittedAnswers array. Both must be handled without error (skip / empty
         * rebuild), the exam still marked submitted, and the untouched text submission preserved.
         */
        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testSubmitViaSlimDtoWithExplicitEmptyNestedArraysIsSafe() throws Exception {
            String originalText = ((TextSubmission) submissionRepository.findById(textSubmission.getId()).orElseThrow()).getText();

            // text exercise participation with an explicit empty submissions array -> size != 1 -> skipped
            ObjectNode textParticipation = participationNode(participationId(textExercise), objectMapper.createArrayNode());
            // quiz submission with an explicit empty submittedAnswers array -> rebuilt as an empty answer set
            ObjectNode quizSubmissionNode = objectMapper.createObjectNode();
            quizSubmissionNode.put("submissionExerciseType", "quiz");
            quizSubmissionNode.put("id", quizSubmission.getId());
            quizSubmissionNode.set("submittedAnswers", objectMapper.createArrayNode());

            ArrayNode exercises = arrayOf(exerciseNode(textExercise.getId(), arrayOf(textParticipation)),
                    exerciseNode(quizExercise.getId(), arrayOf(participationNode(participationId(quizExercise), arrayOf(quizSubmissionNode)))));
            ObjectNode body = objectMapper.createObjectNode();
            body.put("id", studentExamForConduction.getId());
            body.set("exercises", exercises);

            request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", body, HttpStatus.OK);

            assertThat(studentExamRepository.findById(studentExamForConduction.getId()).orElseThrow().isSubmitted()).isTrue();
            assertThat(((TextSubmission) submissionRepository.findById(textSubmission.getId()).orElseThrow()).getText()).isEqualTo(originalText);
        }

        /**
         * Trap #2: a stale client tab opened before the DTO rollout still posts the full-entity {@code StudentExam}
         * body. It must deserialize losslessly into the DTO (matching discriminators + ignore-unknown) and persist the
         * changed answer exactly as the slim body would.
         */
        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testSubmitLegacyFullEntityBodyStillPersistsChangedText() throws Exception {
            textSubmission.setText("legacy full entity text");

            request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction, HttpStatus.OK);

            assertThat(((TextSubmission) submissionRepository.findById(textSubmission.getId()).orElseThrow()).getText()).isEqualTo("legacy full entity text");
        }
    }
}
