package de.tum.in.www1.artemis.exam;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpStatus.CREATED;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.exam.SuspiciousSessionReason;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizGroup;
import de.tum.in.www1.artemis.domain.quiz.QuizPool;
import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamLiveEventRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExamUserRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.QuizPoolRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.service.QuizPoolService;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.exam.ExamAccessService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreScheduleService;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.user.UserFactory;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.PageableSearchUtilService;
import de.tum.in.www1.artemis.util.ZipFileTestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.CourseWithIdDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExamChecklistDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExamInformationDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExamScoresDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExamSessionDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExamWithIdAndCourseDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseForPlagiarismCasesOverviewDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseGroupWithIdAndExamDTO;
import de.tum.in.www1.artemis.web.rest.dto.SuspiciousExamSessionsDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExamIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "examint";

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamService examService;

    @Autowired
    private ExamLiveEventRepository examLiveEventRepository;

    @Autowired
    private ExamDateService examDateService;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private ZipFileTestUtilService zipFileTestUtilService;

    @Autowired
    private ExamAccessService examAccessService;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private QuizPoolService quizPoolService;

    @Autowired
    private QuizPoolRepository quizPoolRepository;

    private Course course1;

    private Course course2;

    private Course course10;

    private Exam exam1;

    private Exam exam2;

    private Exam exam3;

    private static final int NUMBER_OF_STUDENTS = 4;

    private static final int NUMBER_OF_TUTORS = 1;

    private User student1;

    private User instructor;

    @BeforeAll
    void setup() {
        // setup users
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, 0, 1);

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42", passwordService.hashPassword(UserFactory.USER_PASSWORD));
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor6", passwordService.hashPassword(UserFactory.USER_PASSWORD));
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor10", passwordService.hashPassword(UserFactory.USER_PASSWORD));

        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        // reset courses
        course1 = courseUtilService.addEmptyCourse();
        course2 = courseUtilService.addEmptyCourse();

        course10 = courseUtilService.createCourse();
        course10.setInstructorGroupName("instructor10-test-group");
        course10 = courseRepository.save(course10);

        User instructor10 = userUtilService.getUserByLogin(TEST_PREFIX + "instructor10");
        instructor10.setGroups(Set.of(course10.getInstructorGroupName()));
        userRepository.save(instructor10);

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 200;
        participantScoreScheduleService.activate();
    }

    @BeforeEach
    void initTestCase() {
        // reset exams
        exam1 = examUtilService.addExam(course1);
        examUtilService.addExamChannel(exam1, "exam1 channel");

        exam2 = examUtilService.addExamWithExerciseGroup(course1, true);
        examUtilService.addExamChannel(exam2, "exam2 channel");

        exam3 = examUtilService.addExamWithQuizPool(course1);

        gitlabRequestMockProvider.enableMockingOfRequests();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor10", roles = "INSTRUCTOR")
    void testGetAllActiveExams() throws Exception {
        // add additional active exam
        var exam3 = examUtilService.addExam(course10, ZonedDateTime.now().plusDays(1), ZonedDateTime.now().plusDays(2), ZonedDateTime.now().plusDays(3));

        // add additional exam not active
        examUtilService.addExam(course10, ZonedDateTime.now().minusDays(10), ZonedDateTime.now().plusDays(2), ZonedDateTime.now().plusDays(3));

        List<Exam> activeExams = request.getList("/api/exams/active", HttpStatus.OK, Exam.class);
        // only exam3 should be returned
        assertThat(activeExams).containsExactly(exam3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExams() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 2);

        generateStudentExams(exam);

        verifyStudentsExamAndExercisesAndQuizQuestions(exam, 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsWithQuizPool() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 2);
        setupQuizPoolWithQuestionsForExam(exam);

        generateStudentExams(exam);

        verifyStudentsExamAndExercisesAndQuizQuestions(exam, 4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsWithEmptyQuizPool() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 2);
        setupEmptyQuizPoolForExam(exam);

        generateStudentExams(exam);

        verifyStudentsExamAndExercisesAndQuizQuestions(exam, 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateMissingStudentExams() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);

        generateStudentExams(exam);

        registerNewStudentsToExam(exam, 1);
        generateMissingStudentExams(exam, 1);
        verifyStudentsExamAndExercisesAndQuizQuestions(exam, 0);

        generateMissingStudentExams(exam, 0);
        verifyStudentsExamAndExercisesAndQuizQuestions(exam, 0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateMissingStudentExamsWithQuizPool() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);
        setupQuizPoolWithQuestionsForExam(exam);

        generateStudentExams(exam);

        registerNewStudentsToExam(exam, 1);
        generateMissingStudentExams(exam, 1);
        verifyStudentsExamAndExercisesAndQuizQuestions(exam, 4);

        generateMissingStudentExams(exam, 0);
        verifyStudentsExamAndExercisesAndQuizQuestions(exam, 4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateMissingStudentExamsWithEmptyQuizPool() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);
        setupEmptyQuizPoolForExam(exam);

        generateStudentExams(exam);

        registerNewStudentsToExam(exam, 1);
        generateMissingStudentExams(exam, 1);
        verifyStudentsExamAndExercisesAndQuizQuestions(exam, 0);

        generateMissingStudentExams(exam, 0);
        verifyStudentsExamAndExercisesAndQuizQuestions(exam, 0);
    }

    private void setupEmptyQuizPoolForExam(Exam exam) {
        QuizPool quizPool = new QuizPool();
        quizPoolService.update(exam.getId(), quizPool);
    }

    private void setupQuizPoolWithQuestionsForExam(Exam exam) {
        QuizPool quizPool = new QuizPool();
        setupGroupsAndQuestionsForQuizPool(quizPool);
        quizPoolService.update(exam.getId(), quizPool);
    }

    private void setupGroupsAndQuestionsForQuizPool(QuizPool quizPool) {
        QuizGroup quizGroup0 = QuizExerciseFactory.createQuizGroup("Encapsulation");
        QuizGroup quizGroup1 = QuizExerciseFactory.createQuizGroup("Inheritance");
        QuizGroup quizGroup2 = QuizExerciseFactory.createQuizGroup("Polymorphism");
        QuizQuestion mcQuizQuestion0 = QuizExerciseFactory.createMultipleChoiceQuestionWithTitleAndGroup("MC 0", quizGroup0);
        QuizQuestion mcQuizQuestion1 = QuizExerciseFactory.createMultipleChoiceQuestionWithTitleAndGroup("MC 1", quizGroup0);
        QuizQuestion dndQuizQuestion0 = QuizExerciseFactory.createDragAndDropQuestionWithTitleAndGroup("DND 0", quizGroup1);
        QuizQuestion dndQuizQuestion1 = QuizExerciseFactory.createDragAndDropQuestionWithTitleAndGroup("DND 1", quizGroup2);
        QuizQuestion saQuizQuestion0 = QuizExerciseFactory.createShortAnswerQuestionWithTitleAndGroup("SA 0", null);
        quizPool.setQuizGroups(List.of(quizGroup0, quizGroup1, quizGroup2));
        quizPool.setQuizQuestions(List.of(mcQuizQuestion0, mcQuizQuestion1, dndQuizQuestion0, dndQuizQuestion1, saQuizQuestion0));
    }

    private void registerNewStudentsToExam(Exam exam, int numberOfStudents) {
        examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, 2, 2 + numberOfStudents - 1);
    }

    private void generateStudentExams(Exam exam) throws Exception {
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        for (var studentExam : studentExams) {
            assertThat(studentExam.getExam()).isEqualTo(exam);
        }
        verifyStudentExams(studentExams, exam.getExamUsers().size());
    }

    private void generateMissingStudentExams(Exam exam, int expectedMissingStudent) throws Exception {
        List<StudentExam> missingStudentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-missing-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(missingStudentExams).hasSize(expectedMissingStudent);
    }

    private void verifyStudentsExamAndExercisesAndQuizQuestions(Exam exam, int numberOfQuizQuestions) throws Exception {
        List<StudentExam> studentExams = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        verifyStudentExams(studentExams, exam.getExamUsers().size());

        verifyStudentExamsExercises(studentExams, exam.getNumberOfExercisesInExam());
        verifyStudentExamsQuizQuestions(studentExams, numberOfQuizQuestions);
    }

    private void verifyStudentExams(List<StudentExam> studentExams, int expectedNumberOfStudentExams) {
        assertThat(studentExams).hasSize(expectedNumberOfStudentExams);
        for (StudentExam studentExam : studentExams) {
            assertThat(studentExam.getWorkingTime()).as("Working time is set correctly").isEqualTo(120 * 60);
        }
    }

    private void verifyStudentExamsExercises(List<StudentExam> studentExams, int expected) {
        List<Long> ids = studentExams.stream().map(StudentExam::getId).toList();
        List<StudentExam> studentExamsWithExercises = studentExamRepository.findAllWithEagerExercisesById(ids);
        for (var studentExam : studentExamsWithExercises) {
            assertThat(studentExam.getExercises()).hasSize(expected);
        }
        // TODO: check exercise configuration, each mandatory exercise group has to appear, one optional exercise should appear
    }

    private void verifyStudentExamsQuizQuestions(List<StudentExam> studentExams, int expected) {
        List<Long> ids = studentExams.stream().map(StudentExam::getId).toList();
        List<StudentExam> studentExamsWithQuizQuestions = studentExamRepository.findAllWithEagerQuizQuestionsById(ids);
        for (var studentExam : studentExamsWithQuizQuestions) {
            assertThat(studentExam.getQuizQuestions()).hasSize(expected);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsNoExerciseGroups_badRequest() throws Exception {
        Exam exam = examUtilService.addExam(course1, now().minusMinutes(5), now(), now().plusHours(2));

        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsNoExerciseNumber_badRequest() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);
        exam.setNumberOfExercisesInExam(null);
        examRepository.save(exam);

        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsNotEnoughExerciseGroups_badRequest() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);
        exam.setNumberOfExercisesInExam(exam.getNumberOfExercisesInExam() + 2);
        examRepository.save(exam);

        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsTooManyMandatoryExerciseGroups_badRequest() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 2);
        exam.setNumberOfExercisesInExam(exam.getNumberOfExercisesInExam() - 2);
        examRepository.save(exam);

        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testSaveExamWithExerciseGroupWithExerciseToDatabase() {
        textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise();
    }

    private void testAllPreAuthorize(Course course, Exam exam) throws Exception {
        Exam newExam = ExamFactory.generateExam(course1);
        request.post("/api/courses/" + course.getId() + "/exams", newExam, HttpStatus.FORBIDDEN);
        request.put("/api/courses/" + course.getId() + "/exams", newExam, HttpStatus.FORBIDDEN);
        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.FORBIDDEN, Exam.class);
        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.FORBIDDEN);
        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/reset", HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/students/" + TEST_PREFIX + "student1", null, HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/students", Collections.singletonList(new StudentDTO(null, null, null, null, null)),
                HttpStatus.FORBIDDEN);
        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent_shouldNotBeAuthorized() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course);

        testAllPreAuthorize(course, exam);
        ExamFactory.generateExam(course1);

        request.getList("/api/courses/" + course1.getId() + "/exams", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor_shouldNotBeAuthorized() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course);

        testAllPreAuthorize(course, exam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor10", roles = "INSTRUCTOR")
    void testCreateExam_checkCourseAccess_instructorNotInCourse_failsWithForbidden() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);

        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_asInstructor_returnsLocationHeader() throws Exception {
        Exam exam = ExamFactory.generateExam(course1, "examE");
        exam.setTitle("          Exam 123              ");

        URI savedExamUri = request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.CREATED);
        Exam savedExam = request.get(String.valueOf(savedExamUri), HttpStatus.OK, Exam.class);

        assertThat(savedExam.getTitle()).isEqualTo("Exam 123");
        verify(examAccessService).checkCourseAccessForInstructorElseThrow(course1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_asInstructor_returnsBody() throws Exception {
        Exam exam = ExamFactory.generateExam(course1, "examF");

        Exam savedExam = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams", exam, Exam.class, HttpStatus.CREATED);

        assertThat(savedExam.getTitle()).isEqualTo(exam.getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_asInstructor_createsCourseMessagingChannel() throws Exception {
        Course course = courseUtilService.createCourseWithMessagingEnabled();
        Exam exam = ExamFactory.generateExam(course, "examG");

        Exam savedExam = request.postWithResponseBody("/api/courses/" + course.getId() + "/exams", exam, Exam.class, HttpStatus.CREATED);

        Channel channelFromDB = channelRepository.findChannelByExamId(savedExam.getId());
        assertThat(channelFromDB).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithId() throws Exception {
        // Test for bad request when exam id is already set.
        Exam examA = ExamFactory.generateExam(course1, "examA");

        examA.setId(55L);

        request.post("/api/courses/" + course1.getId() + "/exams", examA, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithCourseIdMismatch() throws Exception {
        // Test for bad request when course id deviates from course specified in route.
        Exam examC = ExamFactory.generateExam(course1, "examC");

        request.post("/api/courses/" + course2.getId() + "/exams", examC, HttpStatus.BAD_REQUEST);
    }

    private List<Exam> provideExamsWithInvalidDates() {
        // Test for bad request, visible date not set
        Exam examA = ExamFactory.generateExam(course1);
        examA.setVisibleDate(null);
        // Test for bad request, start date not set
        Exam examB = ExamFactory.generateExam(course1);
        examB.setStartDate(null);
        // Test for bad request, end date not set
        Exam examC = ExamFactory.generateExam(course1);
        examC.setEndDate(null);
        // Test for bad request, start date not after visible date
        Exam examD = ExamFactory.generateExam(course1);
        examD.setStartDate(examD.getVisibleDate());
        // Test for bad request, end date not after start date
        Exam examE = ExamFactory.generateExam(course1);
        examE.setEndDate(examE.getStartDate());
        // Test for bad request, when visibleDate equals the startDate
        Exam examF = ExamFactory.generateExam(course1);
        examF.setVisibleDate(examF.getStartDate());
        // Test for bad request, when exampleSolutionPublicationDate is before the visibleDate
        Exam examG = ExamFactory.generateExam(course1);
        examG.setExampleSolutionPublicationDate(examG.getVisibleDate().minusHours(1));
        return List.of(examA, examB, examC, examD, examE, examF, examG);
    }

    @ParameterizedTest
    @MethodSource("provideExamsWithInvalidDates")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithInvalidDates(Exam exam) throws Exception {
        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithExerciseGroups() throws Exception {
        // Test for conflict when user tries to create an exam with exercise groups.
        Exam examD = ExamFactory.generateExam(course1, "examD");

        examD.addExerciseGroup(ExamFactory.generateExerciseGroup(true, exam1));

        request.post("/api/courses/" + course1.getId() + "/exams", examD, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithoutCourse() throws Exception {
        Exam examB = ExamFactory.generateExam(course1, "examB");

        examB.setCourse(null);

        // Test for bad request when course is null.
        request.post("/api/courses/" + course1.getId() + "/exams", examB, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_createsExamWithoutId() throws Exception {
        // Create instead of update if no id was set
        Exam exam = ExamFactory.generateExam(course1, "exam1");
        exam.setTitle("Over 9000!");
        long examCountBefore = examRepository.count();
        Exam createdExam = request.putWithResponseBody("/api/courses/" + course1.getId() + "/exams", exam, Exam.class, HttpStatus.CREATED);

        assertThat(exam.getEndDate()).isEqualTo(createdExam.getEndDate());
        assertThat(exam.getStartDate()).isEqualTo(createdExam.getStartDate());
        assertThat(exam.getVisibleDate()).isEqualTo(createdExam.getVisibleDate());
        // Note: ZonedDateTime has problems with comparison due to time zone differences for values saved in the database and values not saved in the database
        assertThat(exam).usingRecursiveComparison().ignoringFields("id", "course", "endDate", "startDate", "visibleDate").isEqualTo(createdExam);
        assertThat(examCountBefore + 1).isEqualTo(examRepository.count());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_failsWithoutCourse() throws Exception {
        // No course is set -> bad request
        Exam exam = ExamFactory.generateExam(course1);
        exam.setId(1L);
        exam.setCourse(null);
        request.put("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_failsWithExamIdMismatch() throws Exception {
        // Course id in the updated exam and in the REST resource url do not match -> bad request
        Exam exam = ExamFactory.generateExam(course1);
        exam.setId(1L);
        request.put("/api/courses/" + course2.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @MethodSource("provideExamsWithInvalidDates")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_failsForInvalidDates(Exam exam) throws Exception {
        // Dates in the updated exam are not valid -> bad request
        exam.setId(1L);
        request.put("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_updatesExamTitle() throws Exception {
        // Update the exam -> ok
        exam1.setTitle("Best exam ever");
        var returnedExam = request.putWithResponseBody("/api/courses/" + course1.getId() + "/exams", exam1, Exam.class, HttpStatus.OK);
        assertThat(returnedExam).isEqualTo(exam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_changeTitleDuringConduction_shouldNotNotifyStudents() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExam(exam1);
        exam1.setTitle("Best exam ever");

        request.put("/api/courses/" + course1.getId() + "/exams", exam1, HttpStatus.OK);

        assertThat(examLiveEventRepository.findAllByStudentExamId(studentExam.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_changeEndDateSubSecondPrecision_shouldNotNotifyStudents() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExam(exam1);
        exam1.setEndDate(exam1.getEndDate().plusNanos(1));

        request.put("/api/courses/" + course1.getId() + "/exams", exam1, HttpStatus.OK);

        assertThat(examLiveEventRepository.findAllByStudentExamId(studentExam.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_changeEndDateDuringConduction_shouldNotifyStudents() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExam(exam1);
        exam1.setEndDate(exam1.getEndDate().plusHours(1));

        request.put("/api/courses/" + course1.getId() + "/exams", exam1, HttpStatus.OK);

        assertThat(examLiveEventRepository.findAllByStudentExamId(studentExam.getId())).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_rescheduleModeling_endDateChanged() throws Exception {
        var modelingExercise = modelingExerciseUtilService.addCourseExamExerciseGroupWithOneModelingExercise();
        var examWithModelingEx = modelingExercise.getExerciseGroup().getExam();

        ZonedDateTime visibleDate = examWithModelingEx.getVisibleDate();
        ZonedDateTime startDate = examWithModelingEx.getStartDate();
        ZonedDateTime endDate = examWithModelingEx.getEndDate();
        examUtilService.setVisibleStartAndEndDateOfExam(examWithModelingEx, visibleDate, startDate, endDate.plusSeconds(2));

        request.put("/api/courses/" + examWithModelingEx.getCourse().getId() + "/exams", examWithModelingEx, HttpStatus.OK);

        verify(instanceMessageSendService).sendModelingExerciseSchedule(modelingExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_rescheduleModeling_workingTimeChanged() throws Exception {
        var modelingExercise = modelingExerciseUtilService.addCourseExamExerciseGroupWithOneModelingExercise();
        var examWithModelingEx = modelingExercise.getExerciseGroup().getExam();

        ZonedDateTime visibleDate = examWithModelingEx.getVisibleDate();
        ZonedDateTime startDate = examWithModelingEx.getStartDate();
        ZonedDateTime endDate = examWithModelingEx.getEndDate();
        examUtilService.setVisibleStartAndEndDateOfExam(examWithModelingEx, visibleDate.plusHours(1), startDate.plusHours(2), endDate.plusHours(3));

        request.put("/api/courses/" + examWithModelingEx.getCourse().getId() + "/exams", examWithModelingEx, HttpStatus.OK);

        StudentExam studentExam = examUtilService.addStudentExam(examWithModelingEx);
        request.patch("/api/courses/" + examWithModelingEx.getCourse().getId() + "/exams/" + examWithModelingEx.getId() + "/student-exams/" + studentExam.getId() + "/working-time",
                3, HttpStatus.OK);
        verify(instanceMessageSendService, times(2)).sendModelingExerciseSchedule(modelingExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_exampleSolutionPublicationDateChanged() throws Exception {
        var modelingExercise = modelingExerciseUtilService.addCourseExamExerciseGroupWithOneModelingExercise();
        var examWithModelingEx = modelingExercise.getExerciseGroup().getExam();
        assertThat(modelingExercise.isExampleSolutionPublished()).isFalse();

        examUtilService.setVisibleStartAndEndDateOfExam(examWithModelingEx, now().minusHours(5), now().minusHours(4), now().minusHours(3));
        examWithModelingEx.setPublishResultsDate(now().minusHours(2));
        examWithModelingEx.setExampleSolutionPublicationDate(now().minusHours(1));

        request.put("/api/courses/" + examWithModelingEx.getCourse().getId() + "/exams", examWithModelingEx, HttpStatus.OK);

        Exam fetchedExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examWithModelingEx.getId());
        Exercise exercise = fetchedExam.getExerciseGroups().get(0).getExercises().stream().findFirst().orElseThrow();
        assertThat(exercise.isExampleSolutionPublished()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExam_asInstructor() throws Exception {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findByIdElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findByIdWithExamUsersElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findByIdWithExerciseGroupsElseThrow(Long.MAX_VALUE));

        assertThat(examRepository.findAllExercisesByExamId(Long.MAX_VALUE)).isEmpty();

        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.OK, Exam.class);

        verify(examAccessService).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExam_asInstructor_WithTestRunQuizExerciseSubmissions() throws Exception {
        Exam exam = examUtilService.addExamWithExerciseGroup(course1, true);
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().get(0);

        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setTestRun(true);

        QuizExercise quizExercise = QuizExerciseFactory.createQuizForExam(exerciseGroup);
        quizExercise.setStudentParticipations(Set.of(studentParticipation));
        studentParticipation.setExercise(quizExercise);

        exerciseRepository.save(quizExercise);
        studentParticipationRepository.save(studentParticipation);

        Exam returnedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "?withExerciseGroups=true", HttpStatus.OK, Exam.class);

        assertThat(returnedExam.getExerciseGroups()).anyMatch(groups -> groups.getExercises().stream().anyMatch(Exercise::getTestRunParticipationsExist));
        verify(examAccessService).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExam_asInstructor_WithQuizPool() throws Exception {
        Exam returnedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam3.getId() + "?withExerciseGroups=true", HttpStatus.OK, Exam.class);
        assertThat(returnedExam.getQuizExamMaxPoints()).isEqualTo(0);
        returnedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam3.getId() + "?withStudents=true", HttpStatus.OK, Exam.class);
        assertThat(returnedExam.getQuizExamMaxPoints()).isEqualTo(0);
        returnedExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam3.getId(), HttpStatus.OK, Exam.class);
        assertThat(returnedExam.getQuizExamMaxPoints()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamsForCourse_asInstructor() throws Exception {
        var exams = request.getList("/api/courses/" + course1.getId() + "/exams", HttpStatus.OK, Exam.class);
        verify(examAccessService).checkCourseAccessForTeachingAssistantElseThrow(course1.getId());

        for (int i = 0; i < exams.size(); i++) {
            Exam exam = exams.get(i);
            assertThat(exam.getCourse().getId()).as("for exam with index %d and id %d", i, exam.getId()).isEqualTo(course1.getId());
            assertThat(exam.getNumberOfExamUsers()).as("for exam with index %d and id %d", i, exam.getId()).isNotNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamsForUser_asInstructor() throws Exception {
        var exams = request.getList("/api/courses/" + course1.getId() + "/exams-for-user", HttpStatus.OK, Exam.class);
        assertThat(course1.getInstructorGroupName()).isIn(instructor.getGroups());

        for (int i = 0; i < exams.size(); i++) {
            Exam exam = exams.get(i);
            assertThat(exam.getCourse().getInstructorGroupName()).as("should be instructor for exam with index %d and id %d", i, exam.getId()).isIn(instructor.getGroups());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetCurrentAndUpcomingExams() throws Exception {
        var exams = request.getList("/api/admin/courses/upcoming-exams", HttpStatus.OK, Exam.class);
        ZonedDateTime currentDay = now().truncatedTo(ChronoUnit.DAYS);
        for (int i = 0; i < exams.size(); i++) {
            Exam exam = exams.get(i);
            assertThat(exam.getEndDate()).as("for exam with index %d and id %d", i, exam.getId()).isAfterOrEqualTo(currentDay);
            assertThat(exam.getCourse().isTestCourse()).as("for exam with index %d and id %d", i, exam.getId()).isFalse();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user", roles = "USER")
    void testGetCurrentAndUpcomingExamsForbiddenForUser() throws Exception {
        request.getList("/api/admin/courses/upcoming-exams", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCurrentAndUpcomingExamsForbiddenForInstructor() throws Exception {
        request.getList("/api/admin/courses/upcoming-exams", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetCurrentAndUpcomingExamsForbiddenForTutor() throws Exception {
        request.getList("/api/admin/courses/upcoming-exams", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteEmptyExam_asInstructor() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithChannel() throws Exception {
        Exam exam = examUtilService.addExam(course1);
        Channel examChannel = examUtilService.addExamChannel(exam, "test");

        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);

        Optional<Channel> examChannelAfterDelete = channelRepository.findById(examChannel.getId());
        assertThat(examChannelAfterDelete).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithQuizPool() throws Exception {
        Exam exam = examUtilService.addExamWithQuizPool(course1);

        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
        Optional<QuizPool> quizPool = quizPoolRepository.findByExamId(exam.getId());
        assertThat(quizPool.isPresent()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithExerciseGroupAndTextExercise_asInstructor() throws Exception {
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exam2.getExerciseGroups().get(0));
        exerciseRepository.save(textExercise);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam2.getId(), HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam2.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteExamThatDoesNotExist() throws Exception {
        request.delete("/api/courses/" + course2.getId() + "/exams/654555", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetEmptyExam_asInstructor() throws Exception {
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/reset", HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetExamWithExerciseGroupAndTextExercise_asInstructor() throws Exception {
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exam2.getExerciseGroups().get(0));
        exerciseRepository.save(textExercise);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/reset", HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam2.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testResetExamThatDoesNotExist() throws Exception {
        request.delete("/api/courses/" + course2.getId() + "/exams/654555/reset", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetExamWithQuizExercise_asInstructor() throws Exception {
        QuizExercise quizExercise = QuizExerciseFactory.createQuizForExam(exam2.getExerciseGroups().get(0));
        quizExerciseRepository.save(quizExercise);

        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/reset", HttpStatus.OK);
        quizExercise = (QuizExercise) exerciseRepository.findByIdElseThrow(quizExercise.getId());
        assertThat(quizExercise.getReleaseDate()).isNull();
        assertThat(quizExercise.getDueDate()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamWithOptions() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExamWithUser(course, student1, false, now().minusHours(3), now().minusHours(2), now().minusHours(1));
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, true, true);

        // Get the exam with all registered users
        // 1. without options
        var exam1 = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class);
        assertThat(exam1.getExamUsers()).isEmpty();
        assertThat(exam1.getExerciseGroups()).isEmpty();

        // 2. with students, without exercise groups
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withStudents", "true");
        var exam2 = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(exam2.getExamUsers()).hasSize(1);
        assertThat(exam2.getExerciseGroups()).isEmpty();

        // 3. with students, with exercise groups
        params.add("withExerciseGroups", "true");
        var exam3 = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(exam3.getExamUsers()).hasSize(1);
        assertThat(exam3.getExerciseGroups()).hasSize(exam.getExerciseGroups().size());
        for (int i = 0; i < exam3.getExerciseGroups().size(); i++) {
            assertThat(exam3.getExerciseGroups().get(i).getExercises()).isEqualTo(exam.getExerciseGroups().get(i).getExercises());
        }
        assertThat(exam3.getNumberOfExamUsers()).isNotNull().isEqualTo(1);

        // 4. without students, with exercise groups
        params = new LinkedMultiValueMap<>();
        params.add("withExerciseGroups", "true");
        var exam4 = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(exam4.getExamUsers()).isEmpty();
        assertThat(exam4.getExerciseGroups()).hasSize(exam.getExerciseGroups().size());

        for (int i = 0; i < exam3.getExerciseGroups().size(); i++) {
            var exercises = exam3.getExerciseGroups().get(i).getExercises();
            assertThat(exercises).isEqualTo(exam.getExerciseGroups().get(i).getExercises());
        }

        var quiz = exam4.getExerciseGroups().get(1).getExercises();
        assertThat(quiz).isNotEmpty().allMatch(exercise -> exercise instanceof QuizExercise quizExercise && !quizExercise.getQuizQuestions().isEmpty());

        ProgrammingExercise programming = (ProgrammingExercise) exam4.getExerciseGroups().get(6).getExercises().iterator().next();
        assertThat(programming.getTemplateParticipation()).isNotNull();
        assertThat(programming.getSolutionParticipation()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamForTestRunDashboard_forbidden() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exam-for-test-run-assessment-dashboard", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamForTestRunDashboard_badRequest() throws Exception {
        request.get("/api/courses/" + course2.getId() + "/exams/" + exam1.getId() + "/exam-for-test-run-assessment-dashboard", HttpStatus.BAD_REQUEST, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithOneTestRuns() throws Exception {
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        request.delete("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithMultipleTestRuns() throws Exception {
        gitlabRequestMockProvider.enableMockingOfRequests();
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);

        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, true, true);
        mockDeleteProgrammingExercise(exerciseUtilService.getFirstExerciseWithType(exam, ProgrammingExercise.class), Set.of(instructor));

        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        assertThat(studentExamRepository.findAllTestRunsByExamId(exam.getId())).hasSize(3);
        request.delete("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourseWithMultipleTestRuns() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course);

        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());

        assertThat(studentExamRepository.findAllTestRunsByExamId(exam.getId())).hasSize(3);

        request.delete("/api/admin/courses/" + course.getId(), HttpStatus.OK);

        assertThat(courseRepository.findById(course.getId())).isEmpty();
        assertThat(examRepository.findById(exam.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamForTestRunDashboard_ok() throws Exception {
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        exam = request.get("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/exam-for-test-run-assessment-dashboard", HttpStatus.OK, Exam.class);
        assertThat(exam.getExerciseGroups().stream().flatMap(exerciseGroup -> exerciseGroup.getExercises().stream()).toList()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForStart() throws Exception {
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        exam.setVisibleDate(ZonedDateTime.now().minusHours(1).minusMinutes(5));
        StudentExam response = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/own-student-exam", HttpStatus.OK, StudentExam.class);
        assertThat(response.getExam()).isEqualTo(exam);
        verify(examAccessService).getExamInCourseElseThrow(course1.getId(), exam.getId());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    @ValueSource(ints = { 0, 1, 2 })
    void testGetExamForExamAssessmentDashboard(int numberOfCorrectionRounds) throws Exception {
        // we need an exam from the past, otherwise the tutor won't have access
        Course course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(student1, now().minusHours(3), now().minusHours(2), now().minusHours(1));
        Exam exam = course.getExams().iterator().next();

        // Ensure the API endpoint works for all number of correctionRounds
        exam.setNumberOfCorrectionRoundsInExam(numberOfCorrectionRounds);
        examRepository.save(exam);

        Exam receivedExam = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/exam-for-assessment-dashboard", HttpStatus.OK, Exam.class);

        // Test that the received exam has two text exercises
        assertThat(receivedExam.getExerciseGroups().get(0).getExercises()).as("Two exercises are returned").hasSize(2);
        // Test that the received exam has zero quiz exercises, because quiz exercises do not need to be corrected manually
        assertThat(receivedExam.getExerciseGroups().get(1).getExercises()).as("Zero exercises are returned").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamForExamAssessmentDashboard_beforeDueDate() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        exam.setEndDate(now().plusWeeks(1));
        examRepository.save(exam);

        request.get("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/exam-for-assessment-dashboard", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetExamForExamAssessmentDashboard_asStudent_forbidden() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exam-for-assessment-dashboard", HttpStatus.FORBIDDEN, Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamForExamAssessmentDashboard_courseIdDoesNotMatch_badRequest() throws Exception {
        request.get("/api/courses/" + course2.getId() + "/exams/" + exam1.getId() + "/exam-for-assessment-dashboard", HttpStatus.BAD_REQUEST, Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamForExamAssessmentDashboard_notFound() throws Exception {
        request.get("/api/courses/-1/exams/-1/exam-for-assessment-dashboard", HttpStatus.NOT_FOUND, Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetExamForExamDashboard_NotTAOfCourse_forbidden() throws Exception {
        Exam exam = ExamFactory.generateExam(course10);
        examRepository.save(exam);

        request.get("/api/courses/" + course10.getId() + "/exams/" + exam.getId() + "/exam-for-assessment-dashboard", HttpStatus.FORBIDDEN, Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetExamScore_tutorNotInCourse_forbidden() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/scores", HttpStatus.FORBIDDEN, ExamScoresDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamScore_tutor_forbidden() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/scores", HttpStatus.FORBIDDEN, ExamScoresDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamStatistics() throws Exception {
        ExamChecklistDTO actualStatistics = examService.getStatsForChecklist(exam1, true);
        ExamChecklistDTO returnedStatistics = request.get("/api/courses/" + exam1.getCourse().getId() + "/exams/" + exam1.getId() + "/statistics", HttpStatus.OK,
                ExamChecklistDTO.class);
        assertThat(returnedStatistics.isAllExamExercisesAllStudentsPrepared()).isEqualTo(actualStatistics.isAllExamExercisesAllStudentsPrepared());
        assertThat(returnedStatistics.getAllExamExercisesAllStudentsPrepared()).isEqualTo(actualStatistics.getAllExamExercisesAllStudentsPrepared());
        assertThat(returnedStatistics.getNumberOfAllComplaints()).isEqualTo(actualStatistics.getNumberOfAllComplaints());
        assertThat(returnedStatistics.getNumberOfAllComplaintsDone()).isEqualTo(actualStatistics.getNumberOfAllComplaintsDone());
        assertThat(returnedStatistics.getNumberOfExamsStarted()).isEqualTo(actualStatistics.getNumberOfExamsStarted());
        assertThat(returnedStatistics.getNumberOfExamsSubmitted()).isEqualTo(actualStatistics.getNumberOfExamsSubmitted());
        assertThat(returnedStatistics.getNumberOfTestRuns()).isEqualTo(actualStatistics.getNumberOfTestRuns());
        assertThat(returnedStatistics.getNumberOfGeneratedStudentExams()).isEqualTo(actualStatistics.getNumberOfGeneratedStudentExams());
        assertThat(returnedStatistics.getNumberOfTotalExamAssessmentsFinishedByCorrectionRound())
                .isEqualTo(actualStatistics.getNumberOfTotalExamAssessmentsFinishedByCorrectionRound());
        assertThat(returnedStatistics.getNumberOfTotalParticipationsForAssessment()).isEqualTo(actualStatistics.getNumberOfTotalParticipationsForAssessment());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLatestExamEndDate() throws Exception {
        // Setup exam and user

        // Set student exam without working time and save into database
        StudentExam studentExam = new StudentExam();
        studentExam.setUser(student1);
        studentExam.setTestRun(false);
        studentExam = studentExamRepository.save(studentExam);

        // Add student exam to exam and save into database
        exam2.addStudentExam(studentExam);
        exam2 = examRepository.save(exam2);

        // Get the latest exam end date DTO from server -> This returns the endDate as no specific student working time is set
        ExamInformationDTO examInfo = request.get("/api/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/latest-end-date", HttpStatus.OK,
                ExamInformationDTO.class);
        // Check that latest end date is equal to endDate (no specific student working time). Do not check for equality as we lose precision when saving to the database
        assertThat(examInfo.latestIndividualEndDate()).isCloseTo(exam2.getEndDate(), within(1, ChronoUnit.SECONDS));

        // Set student exam with working time and save
        studentExam.setWorkingTime(3600);
        studentExamRepository.save(studentExam);

        // Get the latest exam end date DTO from server -> This returns the startDate + workingTime
        ExamInformationDTO examInfo2 = request.get("/api/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/latest-end-date", HttpStatus.OK,
                ExamInformationDTO.class);
        // Check that latest end date is equal to startDate + workingTime
        assertThat(examInfo2.latestIndividualEndDate()).isCloseTo(exam2.getStartDate().plusHours(1), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor10", roles = "INSTRUCTOR")
    void testCourseAndExamAccessForInstructors_notInstructorInCourse_forbidden() throws Exception {
        // Instructor10 is not instructor for the course
        // Update exam
        request.put("/api/courses/" + course1.getId() + "/exams", exam1, HttpStatus.FORBIDDEN);
        // Get exam
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.FORBIDDEN, Exam.class);
        // Add student to exam
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + TEST_PREFIX + "student1", null, HttpStatus.FORBIDDEN);
        // Generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.FORBIDDEN);
        // Generate missing exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/generate-missing-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.FORBIDDEN);
        // Start exercises
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/start-exercises", null, HttpStatus.FORBIDDEN, null);
        // Unlock all repositories
        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/unlock-all-repositories", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
        // Lock all repositories
        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/lock-all-repositories", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
        // Add students to exam
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students", Collections.singletonList(new StudentDTO(null, null, null, null, null)),
                HttpStatus.FORBIDDEN);
        // Delete student from exam
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.FORBIDDEN);
        // Update order of exerciseGroups
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups-order", new ArrayList<ExerciseGroup>(), HttpStatus.FORBIDDEN);
        // Get the latest individual end date
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/latest-end-date", HttpStatus.FORBIDDEN, ExamInformationDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLatestIndividualEndDate_noStudentExams() {
        final var now = now().truncatedTo(ChronoUnit.MINUTES);
        exam1.setStartDate(now.minusHours(2));
        exam1.setEndDate(now);
        final var exam = examRepository.save(exam1);
        final var latestIndividualExamEndDate = examDateService.getLatestIndividualExamEndDate(exam.getId());
        assertThat(latestIndividualExamEndDate.isEqual(exam.getEndDate())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllIndividualExamEndDates() {
        final var now = now().truncatedTo(ChronoUnit.MINUTES);
        exam1.setStartDate(now.minusHours(2));
        exam1.setEndDate(now);
        final var exam = examRepository.save(exam1);

        final var studentExam1 = new StudentExam();
        studentExam1.setExam(exam);
        studentExam1.setUser(student1);
        studentExam1.setWorkingTime(120);
        studentExam1.setTestRun(false);
        studentExamRepository.save(studentExam1);

        final var studentExam2 = new StudentExam();
        studentExam2.setExam(exam);
        studentExam2.setUser(student1);
        studentExam2.setWorkingTime(120);
        studentExam2.setTestRun(false);
        studentExamRepository.save(studentExam2);

        final var studentExam3 = new StudentExam();
        studentExam3.setExam(exam);
        studentExam3.setUser(student1);
        studentExam3.setWorkingTime(60);
        studentExam3.setTestRun(false);
        studentExamRepository.save(studentExam3);

        final var individualWorkingTimes = examDateService.getAllIndividualExamEndDates(exam.getId());
        assertThat(individualWorkingTimes).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIsExamOver_GracePeriod() {
        final var now = now().truncatedTo(ChronoUnit.MINUTES);
        exam1.setStartDate(now.minusHours(2));
        exam1.setEndDate(now);
        exam1.setGracePeriod(180);
        final var exam = examRepository.save(exam1);
        final var isOver = examDateService.isExamWithGracePeriodOver(exam.getId());
        assertThat(isOver).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveCourseWithExam() throws Exception {
        Course course = courseUtilService.createCourseWithExamExercisesAndSubmissions(TEST_PREFIX);
        course.setEndDate(now().minusMinutes(5));
        course = courseRepository.save(course);

        request.put("/api/courses/" + course.getId() + "/archive", null, HttpStatus.OK);

        final var courseId = course.getId();
        await().until(() -> courseRepository.findById(courseId).orElseThrow().getCourseArchivePath() != null);

        var updatedCourse = courseRepository.findById(courseId).orElseThrow();
        assertThat(updatedCourse.getCourseArchivePath()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveExamAsInstructor() throws Exception {
        archiveExamAsInstructor();
    }

    private Course archiveExamAsInstructor() throws Exception {
        var course = courseUtilService.createCourseWithExamExercisesAndSubmissions(TEST_PREFIX);
        var exam = examRepository.findByCourseId(course.getId()).stream().findFirst().orElseThrow();

        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/archive", null, HttpStatus.OK);

        final var examId = exam.getId();
        await().until(() -> examRepository.findById(examId).orElseThrow().getExamArchivePath() != null);

        var updatedExam = examRepository.findById(examId).orElseThrow();
        assertThat(updatedExam.getExamArchivePath()).isNotEmpty();
        return course;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testArchiveExamAsStudent_forbidden() throws Exception {
        Exam exam = examUtilService.addExam(course1);

        request.put("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/archive", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveExamBeforeEndDate_badRequest() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        course.setEndDate(now().plusMinutes(5));
        course = courseRepository.save(course);

        Exam exam = examUtilService.addExam(course);
        exam = examRepository.save(exam);

        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/archive", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDownloadExamArchiveAsStudent_forbidden() throws Exception {
        request.get("/api/courses/" + 1 + "/exams/" + 1 + "/download-archive", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDownloadExamArchiveAsTutor_forbidden() throws Exception {
        request.get("/api/courses/" + 1 + "/exams/" + 1 + "/download-archive", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDownloadExamArchiveAsInstructor_not_found() throws Exception {
        // Return not found if the exam doesn't exist
        var downloadedArchive = request.get("/api/courses/" + course1.getId() + "/exams/-1/download-archive", HttpStatus.NOT_FOUND, String.class);
        assertThat(downloadedArchive).isNull();

        // Returns not found if there is no archive
        downloadedArchive = request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/download-archive", HttpStatus.NOT_FOUND, String.class);
        assertThat(downloadedArchive).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDownloadExamArchiveAsInstructorNotInCourse_forbidden() throws Exception {
        // Create an exam with no archive
        Course course = courseUtilService.createCourse();
        course.setInstructorGroupName("some-group");
        course = courseRepository.save(course);
        var exam = examUtilService.addExam(course);

        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/download-archive", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDownloadExamArchiveAsInstructor() throws Exception {
        var course = archiveExamAsInstructor();

        // Download the archive
        var exam = examRepository.findByCourseId(course.getId()).stream().findFirst().orElseThrow();
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("Content-Disposition", "attachment; filename=\"" + exam.getExamArchivePath() + "\"");
        var archive = request.getFile("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/download-archive", HttpStatus.OK, new LinkedMultiValueMap<>(),
                expectedHeaders);
        assertThat(archive).isNotNull();

        // Extract the archive
        Path extractedArchiveDir = zipFileTestUtilService.extractZipFileRecursively(archive.getAbsolutePath());

        // Check that the dummy files we created exist in the archive.
        List<Path> filenames;
        try (var files = Files.walk(extractedArchiveDir)) {
            filenames = files.filter(Files::isRegularFile).map(Path::getFileName).toList();
        }

        var submissions = submissionRepository.findByParticipation_Exercise_ExerciseGroup_Exam_Id(exam.getId());

        var savedSubmission = submissions.stream().filter(submission -> submission instanceof FileUploadSubmission).findFirst().orElseThrow();
        assertSubmissionFilename(filenames, savedSubmission, ".png");

        savedSubmission = submissions.stream().filter(submission -> submission instanceof TextSubmission).findFirst().orElseThrow();
        assertSubmissionFilename(filenames, savedSubmission, ".txt");

        savedSubmission = submissions.stream().filter(submission -> submission instanceof ModelingSubmission).findFirst().orElseThrow();
        assertSubmissionFilename(filenames, savedSubmission, ".json");

        FileUtils.deleteDirectory(extractedArchiveDir.toFile());
        FileUtils.delete(archive);
    }

    private void assertSubmissionFilename(List<Path> expectedFilenames, Submission submission, String filenameExtension) {
        var studentParticipation = (StudentParticipation) submission.getParticipation();
        var exerciseTitle = submission.getParticipation().getExercise().getTitle();
        var studentLogin = studentParticipation.getStudent().orElseThrow().getLogin();
        var filename = exerciseTitle + "-" + studentLogin + "-" + submission.getId() + filenameExtension;
        assertThat(expectedFilenames).contains(Path.of(filename));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamTitleAsInstructor() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExamTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamTitleAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExamTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetExamTitleAsUser() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExamTitle();
    }

    private void testGetExamTitle() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        exam.setTitle("Test Exam");
        exam = examRepository.save(exam);

        final var title = request.get("/api/exams/" + exam.getId() + "/title", HttpStatus.OK, String.class);

        assertThat(title).isEqualTo(exam.getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetExamTitleForNonExistingExam() throws Exception {
        request.get("/api/exams/123124123123/title", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRetrieveOwnStudentExam_noInformationLeaked() throws Exception {
        Exam exam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        ExamUser examUser = new ExamUser();
        examUser.setUser(student1);
        exam.addExamUser(examUser);
        examUserRepository.save(examUser);
        StudentExam studentExam = examUtilService.addStudentExam(exam);
        studentExam.setUser(student1);
        studentExamRepository.save(studentExam);

        StudentExam receivedStudentExam = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/own-student-exam", HttpStatus.OK, StudentExam.class);
        assertThat(receivedStudentExam.getExercises()).isEmpty();
        assertThat(receivedStudentExam.getExam().getStudentExams()).isEmpty();
        assertThat(receivedStudentExam.getExam().getExamUsers()).isEmpty();
        assertThat(receivedStudentExam.getExam().getExerciseGroups()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRetrieveOwnStudentExam_noStudentExam() throws Exception {
        Exam exam = examUtilService.addExam(course1);

        var examUser1 = new ExamUser();
        examUser1.setExam(exam);
        examUser1.setUser(student1);
        examUser1 = examUserRepository.save(examUser1);
        exam.addExamUser(examUser1);
        examRepository.save(exam);
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/own-student-exam", HttpStatus.BAD_REQUEST, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRetrieveOwnStudentExam_instructor() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/own-student-exam", HttpStatus.BAD_REQUEST, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamForImportWithExercises_successful() throws Exception {
        ExerciseGroup quizGroup = exam2.getExerciseGroups().get(0);
        QuizExercise quiz = QuizExerciseFactory.generateQuizExerciseForExam(quizGroup);
        QuizExerciseFactory.addAllQuestionTypesToQuizExercise(quiz);
        exerciseRepository.save(quiz);

        Exam received = request.get("/api/exams/" + exam2.getId(), HttpStatus.OK, Exam.class);
        assertThat(received).isEqualTo(exam2);
        assertThat(received.getExerciseGroups()).hasSize(1);
        var group = received.getExerciseGroups().get(0);
        assertThat(group.getExercises()).hasSize(1);
        QuizExercise receivedExercise = (QuizExercise) group.getExercises().iterator().next();
        // Details like the quiz questions are needed for importing and should be included
        assertThat(receivedExercise.getQuizQuestions()).hasSize(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor10", roles = "INSTRUCTOR")
    void testGetExamForImportWithExercises_noInstructorAccess() throws Exception {
        request.get("/api/exams/" + exam2.getId(), HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void testGetExamForImportWithExercises_noTutorAccess() throws Exception {
        request.get("/api/exams/" + exam2.getId(), HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetExamForImportWithExercises_noEditorAccess() throws Exception {
        request.get("/api/exams/" + exam2.getId(), HttpStatus.FORBIDDEN, Exam.class);
    }

    // <editor-fold desc="Get All On Page">
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllExamsOnPage_withoutExercises_asInstructor_returnsExams() throws Exception {
        var title = "My fancy search title for the exam which is not used somewhere else";
        var exam = ExamFactory.generateExam(course1);
        exam.setTitle(title);
        examRepository.save(exam);
        final SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(title);
        final var result = request.getSearchResult("/api/exams", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1).containsExactly(exam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllExamsOnPage_withExercises_asInstructor_returnsExams() throws Exception {
        var newExam = examUtilService.addTestExamWithExerciseGroup(course1, true);
        var searchTerm = "A very distinct title that should only ever exist once in the database";
        newExam.setTitle(searchTerm);
        examRepository.save(newExam);
        final SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(searchTerm);
        final var result = request.getSearchResult("/api/exams?withExercises=true", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        List<Exam> foundExams = result.getResultsOnPage();
        assertThat(foundExams).hasSize(1).containsExactly(newExam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllExamsOnPage_withoutExercisesAndExamsNotLinkedToCourse_asInstructor_returnsNoExams() throws Exception {
        var title = "Another fancy exam search title for the exam which is not used somewhere else";
        Course course = courseUtilService.addEmptyCourse();
        course.setInstructorGroupName("non-instructors");
        courseRepository.save(course);
        var exam = examUtilService.addExamWithExerciseGroup(course, true);
        exam.setTitle(title);
        examRepository.save(exam);
        final SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(title);
        final var result = request.getSearchResult("/api/exams", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAllExamsOnPage_withoutExercisesAndExamsNotLinkedToCourse_asAdmin_returnsExams() throws Exception {
        var title = "Yet another 3rd exam search title for the exam which is not used somewhere else";
        Course course = courseUtilService.addEmptyCourse();
        course.setInstructorGroupName("non-instructors");
        courseRepository.save(course);
        var exam = examUtilService.addExamWithExerciseGroup(course, true);
        exam.setTitle(title);
        examRepository.save(exam);
        final SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(title);
        final var result = request.getSearchResult("/api/exams", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1).contains(exam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void testGetAllExamsOnPage_asTutor_failsWithForbidden() throws Exception {
        final SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/exams", HttpStatus.FORBIDDEN, Exam.class, pageableSearchUtilService.searchMapping(search));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllExamsOnPage_asStudent_failsWithForbidden() throws Exception {
        final SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/exams", HttpStatus.FORBIDDEN, Exam.class, pageableSearchUtilService.searchMapping(search));
    }
    // </editor-fold>

    // <editor-fold desc="Import">
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testImportExamWithExercises_asStudent_failsWithForbidden() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", exam1, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void testImportExamWithExercises_asTutor_failsWithForbidden() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", exam1, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_failsWithIdExists() throws Exception {
        final Exam exam = ExamFactory.generateExam(course1);

        exam.setId(2L);

        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", exam, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_failsWithCourseMismatch() throws Exception {
        // No Course
        final Exam examA = ExamFactory.generateExam(course1);
        examA.setCourse(null);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examA, HttpStatus.BAD_REQUEST, null);

        // Exam Course and REST-Course mismatch
        final Exam examB = ExamFactory.generateExam(course1);
        examB.setCourse(course2);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examB, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_failsWithDateConflict() throws Exception {
        // Visible Date after Started Date
        final Exam examA = ExamFactory.generateExam(course1);
        examA.setVisibleDate(ZonedDateTime.now().plusHours(2));
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examA, HttpStatus.BAD_REQUEST, null);

        // Visible Date missing
        final Exam examB = ExamFactory.generateExam(course1);
        examB.setVisibleDate(null);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examB, HttpStatus.BAD_REQUEST, null);

        // Started Date after End Date
        final Exam examC = ExamFactory.generateExam(course1);
        examC.setStartDate(ZonedDateTime.now().plusHours(2));
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examC, HttpStatus.BAD_REQUEST, null);

        // Started Date missing
        final Exam examD = ExamFactory.generateExam(course1);
        examD.setStartDate(null);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examD, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_failsWithDateConflictTestExam() throws Exception {
        // Working Time larger than Working window
        final Exam examA = ExamFactory.generateTestExam(course1);
        examA.setWorkingTime(3 * 60 * 60);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examA, HttpStatus.BAD_REQUEST, null);

        // Working Time larger than Working window
        final Exam examB = ExamFactory.generateTestExam(course1);
        examB.setWorkingTime(0);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examB, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_failsWithPointConflict() throws Exception {
        final Exam examA = ExamFactory.generateExam(course1);
        examA.setExamMaxPoints(-5);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examA, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_failsWithCorrectionRoundConflict() throws Exception {
        // Correction round <= 0
        final Exam examA = ExamFactory.generateExam(course1);
        examA.setNumberOfCorrectionRoundsInExam(0);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examA, HttpStatus.BAD_REQUEST, null);

        // Correction round >= 2
        final Exam examB = ExamFactory.generateExam(course1);
        examB.setNumberOfCorrectionRoundsInExam(3);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examB, HttpStatus.BAD_REQUEST, null);

        // Correction round != 0 for test exam
        final Exam examC = ExamFactory.generateTestExam(course1);
        examC.setNumberOfCorrectionRoundsInExam(1);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examC, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_successfulWithoutExercises() throws Exception {
        Exam exam = examUtilService.addExam(course1);
        exam.setId(null);

        exam.setChannelName("channelname-imported");
        final Exam received = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exam-import", exam, Exam.class, HttpStatus.CREATED);
        assertThat(received.getId()).isNotNull();
        assertThat(received.getTitle()).isEqualTo(exam.getTitle());
        assertThat(received.isTestExam()).isFalse();
        assertThat(received.getWorkingTime()).isEqualTo(3000);
        assertThat(received.getStartText()).isEqualTo("Start Text");
        assertThat(received.getEndText()).isEqualTo("End Text");
        assertThat(received.getConfirmationStartText()).isEqualTo("Confirmation Start Text");
        assertThat(received.getConfirmationEndText()).isEqualTo("Confirmation End Text");
        assertThat(received.getExamMaxPoints()).isEqualTo(90);
        assertThat(received.getNumberOfExercisesInExam()).isEqualTo(1);
        assertThat(received.getRandomizeExerciseOrder()).isFalse();
        assertThat(received.getNumberOfCorrectionRoundsInExam()).isEqualTo(1);
        assertThat(received.getCourse().getId()).isEqualTo(course1.getId());

        exam.setVisibleDate(ZonedDateTime.ofInstant(exam.getVisibleDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        received.setVisibleDate(ZonedDateTime.ofInstant(received.getVisibleDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(received.getVisibleDate()).isEqualToIgnoringSeconds(exam.getVisibleDate());
        exam.setStartDate(ZonedDateTime.ofInstant(exam.getStartDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        received.setStartDate(ZonedDateTime.ofInstant(received.getStartDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(received.getStartDate()).isEqualToIgnoringSeconds(exam.getStartDate());
        exam.setEndDate(ZonedDateTime.ofInstant(exam.getEndDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        received.setEndDate(ZonedDateTime.ofInstant(received.getEndDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(received.getEndDate()).isEqualToIgnoringSeconds(exam.getEndDate());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_successfulWithExercises() throws Exception {
        Exam exam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        exam.setId(null);
        exam.setChannelName("testchannelname-imported");
        final Exam received = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exam-import", exam, Exam.class, CREATED);
        assertThat(received.getId()).isNotNull();
        assertThat(received.getTitle()).isEqualTo(exam.getTitle());
        assertThat(received.getCourse()).isEqualTo(course1);
        assertThat(received.getCourse()).isEqualTo(exam.getCourse());
        assertThat(received.getExerciseGroups()).hasSize(4);

        List<ExerciseGroup> exerciseGroups = received.getExerciseGroups();
        for (int i = 0; i < exerciseGroups.size(); i++) {
            var exerciseGroup = exerciseGroups.get(i);
            assertThat(exerciseGroup.getTitle()).isEqualTo("Group " + i);
            assertThat(exerciseGroup.getIsMandatory()).isTrue();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithQuizExercise_successfulWithQuestions() throws Exception {
        Exam exam = examUtilService.addExamWithExerciseGroup(course1, false);
        ExerciseGroup quizGroup = exam.getExerciseGroups().get(0);
        QuizExercise quiz = QuizExerciseFactory.generateQuizExerciseForExam(quizGroup);
        quiz.addQuestions(QuizExerciseFactory.createMultipleChoiceQuestionWithAllTypesOfAnswerOptions());
        quiz.addQuestions(QuizExerciseFactory.createShortAnswerQuestionWithRealisticText());
        quiz.addQuestions(QuizExerciseFactory.createSingleChoiceQuestion());
        quizGroup.addExercise(quiz);
        exerciseRepository.save(quiz);

        exam.setId(null);
        final Exam received = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exam-import", exam, Exam.class, CREATED);
        assertThat(received.getExerciseGroups()).hasSize(1);

        ExerciseGroup receivedGroup = received.getExerciseGroups().get(0);
        assertThat(receivedGroup.getExercises()).hasSize(1);
        QuizExercise exercise = (QuizExercise) receivedGroup.getExercises().iterator().next();

        // The directly returned exam should not contain details like the quiz questions
        assertThat(exercise.getQuizQuestions()).isEmpty();

        exercise = quizExerciseRepository.findWithEagerQuestionsByIdOrElseThrow(exercise.getId());
        // Quiz questions should get imported into the exam
        assertThat(exercise.getQuizQuestions()).hasSize(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_successfulWithImportToOtherCourse() throws Exception {
        Exam exam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course2);
        exam.setCourse(course1);
        exam.setId(null);
        exam.setChannelName("testchannelname");
        final Exam received = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exam-import", exam, Exam.class, CREATED);
        assertThat(received.getExerciseGroups()).hasSize(4);

        for (int i = 0; i <= 3; i++) {
            Exercise expected = exam.getExerciseGroups().get(i).getExercises().stream().findFirst().orElseThrow();
            Exercise exerciseReceived = received.getExerciseGroups().get(i).getExercises().stream().findFirst().orElseThrow();
            assertThat(exerciseReceived.getExerciseGroup()).isNotEqualTo(expected.getExerciseGroup());
            assertThat(exerciseReceived.getTitle()).isEqualTo(expected.getTitle());
            assertThat(exerciseReceived.getId()).isNotEqualTo(expected.getId());
        }
    }
    // </editor-fold>

    // <editor-fold desc="Plagiarism">
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExercisesWithPotentialPlagiarismAsTutor_forbidden() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercises-with-potential-plagiarism", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetSuspiciousSessionsAsTutor_forbidden() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("differentStudentExamsSameIPAddress", "true");
        params.add("differentStudentExamsSameBrowserFingerprint", "true");
        params.add("sameStudentExamDifferentIPAddresses", "false");
        params.add("sameStudentExamDifferentBrowserFingerprints", "false");
        params.add("ipOutsideOfRange", "false");
        request.getSet("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.FORBIDDEN, SuspiciousExamSessionsDTO.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExercisesWithPotentialPlagiarismAsInstructorNotInCourse_forbidden() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course);
        courseUtilService.updateCourseGroups("abc", course, "");

        request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/exercises-with-potential-plagiarism", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetSuspiciousSessionsAsInstructorNotInCourse_forbidden() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course);
        courseUtilService.updateCourseGroups("abc", course, "");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("differentStudentExamsSameIPAddress", "true");
        params.add("differentStudentExamsSameBrowserFingerprint", "true");
        params.add("sameStudentExamDifferentIPAddresses", "false");
        params.add("sameStudentExamDifferentBrowserFingerprints", "false");
        params.add("ipOutsideOfRange", "false");

        request.getSet("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/suspicious-sessions", HttpStatus.FORBIDDEN, SuspiciousExamSessionsDTO.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExercisesWithPotentialPlagiarismAsInstructor() throws Exception {
        Exam exam = examUtilService.addExam(course1);
        List<ExerciseForPlagiarismCasesOverviewDTO> expectedExercises = new ArrayList<>();
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, true, true);
        exam.getExerciseGroups().forEach(exerciseGroup -> exerciseGroup.getExercises().forEach(exercise -> {
            if (exercise.getExerciseType() != ExerciseType.QUIZ && exercise.getExerciseType() != ExerciseType.FILE_UPLOAD) {
                var courseDTO = new CourseWithIdDTO(course1.getId());
                var examDTO = new ExamWithIdAndCourseDTO(exercise.getExerciseGroup().getExam().getId(), courseDTO);
                var exerciseGroupDTO = new ExerciseGroupWithIdAndExamDTO(exercise.getExerciseGroup().getId(), examDTO);
                expectedExercises.add(new ExerciseForPlagiarismCasesOverviewDTO(exercise.getId(), exercise.getTitle(), exercise.getType(), exerciseGroupDTO));
            }
        }));

        List<ExerciseForPlagiarismCasesOverviewDTO> exercises = request.getList(
                "/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercises-with-potential-plagiarism", HttpStatus.OK, ExerciseForPlagiarismCasesOverviewDTO.class);
        assertThat(exercises).hasSize(5);
        assertThat(exercises).containsExactlyInAnyOrderElementsOf(expectedExercises);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("provideAnalysisOptions")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetSuspiciousSessionsDifferentAsInstructor(boolean sameIpDifferentExams, boolean sameFingerprintDifferentExams, boolean differentIpSameExam,
            boolean differentFingerprintSameExam) throws Exception {
        prepareExamSessionsForTestCase(sameIpDifferentExams, sameFingerprintDifferentExams, differentIpSameExam, differentFingerprintSameExam);
        Set<SuspiciousSessionReason> suspiciousReasons = getSuspiciousReasons(sameIpDifferentExams, sameFingerprintDifferentExams, differentIpSameExam,
                differentFingerprintSameExam);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("differentStudentExamsSameIPAddress", sameIpDifferentExams ? "true" : "false");
        params.add("differentStudentExamsSameBrowserFingerprint", sameFingerprintDifferentExams ? "true" : "false");
        params.add("sameStudentExamDifferentIPAddresses", differentIpSameExam ? "true" : "false");
        params.add("sameStudentExamDifferentBrowserFingerprints", differentFingerprintSameExam ? "true" : "false");
        params.add("ipOutsideOfRange", "false");
        Set<SuspiciousExamSessionsDTO> suspiciousSessionTuples = request.getSet("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions",
                HttpStatus.OK, SuspiciousExamSessionsDTO.class, params);
        assertThat(suspiciousSessionTuples).hasSize(1);
        var suspiciousSessions = suspiciousSessionTuples.stream().findFirst().get();
        assertThat(suspiciousSessions.examSessions()).hasSize(2);
        var examSessions = suspiciousSessions.examSessions();
        assertThat(examSessions.stream().findFirst().orElseThrow().suspiciousReasons()).containsExactlyInAnyOrderElementsOf(suspiciousReasons);
    }

    private static Stream<Arguments> provideAnalysisOptions() {
        return Stream.of(Arguments.of(true, true, false, false), Arguments.of(false, true, false, false), Arguments.of(true, false, false, false),
                Arguments.of(false, false, true, false), Arguments.of(false, false, false, true), Arguments.of(false, false, true, true));
    }

    private Set<SuspiciousSessionReason> getSuspiciousReasons(boolean sameIpDifferentExam, boolean sameFingerprintDifferentExams, boolean differentIpSameExam,
            boolean differentFingerprintSameExam) {
        Set<SuspiciousSessionReason> suspiciousReasons = new HashSet<>();
        if (sameIpDifferentExam) {
            suspiciousReasons.add(SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS);
        }
        if (sameFingerprintDifferentExams) {
            suspiciousReasons.add(SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT);
        }
        if (differentIpSameExam) {
            suspiciousReasons.add(SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES);
        }
        if (differentFingerprintSameExam) {
            suspiciousReasons.add(SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS);
        }
        return suspiciousReasons;
    }

    private void prepareExamSessionsForTestCase(boolean sameIpDifferentExams, boolean sameFingerprintDifferentExams, boolean differentIpSameExam,
            boolean differentFingerprintSameExam) {
        final String ipAddress1 = "192.0.2.235";
        final String browserFingerprint1 = "5b2cc274f6eaf3a71647e1f85358ce32";

        final String ipAddress2 = "172.168.0.0";
        final String browserFingerprint2 = "5b2cc274f6eaf3a71647e1f85358ce31";

        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam1, student1);
        StudentExam studentExam2 = examUtilService.addStudentExamWithUser(exam1, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        StudentExam studentExam3 = examUtilService.addStudentExamWithUser(exam1, userUtilService.getUserByLogin(TEST_PREFIX + "student3"));
        StudentExam studentExam4 = examUtilService.addStudentExamWithUser(exam1, userUtilService.getUserByLogin(TEST_PREFIX + "student4"));
        if (sameIpDifferentExams && sameFingerprintDifferentExams) {
            examUtilService.addExamSessionToStudentExam(studentExam, "abc", ipAddress1, browserFingerprint1, "instanceId", "user-agent");
            examUtilService.addExamSessionToStudentExam(studentExam, "def", ipAddress1, browserFingerprint1, "instanceId", "user-agent");
            examUtilService.addExamSessionToStudentExam(studentExam2, "abc", ipAddress1, browserFingerprint1, "instanceId", "user-agent");
        }
        else {
            if (sameFingerprintDifferentExams) {
                examUtilService.addExamSessionToStudentExam(studentExam, "abc", ipAddress2, browserFingerprint1, "instanceId", "user-agent");
                examUtilService.addExamSessionToStudentExam(studentExam2, "abc", ipAddress1, browserFingerprint1, "instanceId", "user-agent");
            }
            if (sameIpDifferentExams) {
                examUtilService.addExamSessionToStudentExam(studentExam, "abc", ipAddress1, browserFingerprint1, "instanceId", "user-agent");
                examUtilService.addExamSessionToStudentExam(studentExam2, "def", ipAddress1, browserFingerprint2, "instanceId", "user-agent");
            }
        }
        if (differentIpSameExam && differentFingerprintSameExam) {
            examUtilService.addExamSessionToStudentExam(studentExam, "abc", ipAddress1, browserFingerprint1, "instanceId", "user-agent");
            examUtilService.addExamSessionToStudentExam(studentExam, "abc", ipAddress2, browserFingerprint2, "instanceId", "user-agent");
        }
        else {
            if (differentIpSameExam) {
                examUtilService.addExamSessionToStudentExam(studentExam, "abc", ipAddress1, browserFingerprint1, "instanceId", "user-agent");
                examUtilService.addExamSessionToStudentExam(studentExam, "abc", ipAddress2, browserFingerprint1, "instanceId", "user-agent");
            }
            if (differentFingerprintSameExam) {
                examUtilService.addExamSessionToStudentExam(studentExam, "abc", ipAddress1, browserFingerprint1, "instanceId", "user-agent");
                examUtilService.addExamSessionToStudentExam(studentExam, "abc", ipAddress1, browserFingerprint2, "instanceId", "user-agent");
            }
        }

        // add other unrelated exam sessions

        examUtilService.addExamSessionToStudentExam(studentExam3, "abc", "192.168.1.1", "5b2cc274f6eaf3a71647e1f85358ce34", "instanceId", "user-agent");
        examUtilService.addExamSessionToStudentExam(studentExam3, "abc", "192.168.1.1", "5b2cc274f6eaf3a71647e1f85358ce34", "instanceId", "user-agent");
        examUtilService.addExamSessionToStudentExam(studentExam4, "abc", "203.0.113.0", "5b2cc274f6eaf3a71647e1f85358ce35", "instanceId", "user-agent");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetSuspiciousSessionsIpOutsideOfRangeNoSubnetGivenBadRequest() throws Exception {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("differentStudentExamsSameIPAddress", "false");
        params.add("differentStudentExamsSameBrowserFingerprint", "false");
        params.add("sameStudentExamDifferentIPAddresses", "false");
        params.add("sameStudentExamDifferentBrowserFingerprints", "false");
        params.add("ipOutsideOfRange", "true");
        request.getSet("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.BAD_REQUEST, SuspiciousExamSessionsDTO.class, params);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @MethodSource("provideIpAddressesAndSubnets")
    void testGetSuspiciousSessionsIpOutsideOfRange(String ipAddress1, String ipAddress2, String subnetIncludingFirstAddress, String subnetIncludingNeitherAddress,
            String subnetIncludingBothAddresses) throws Exception {
        var studentExam1 = examUtilService.addStudentExamWithUser(exam1, student1);
        var studentExam2 = examUtilService.addStudentExamWithUser(exam1, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        examUtilService.addExamSessionToStudentExam(studentExam1, "abc", ipAddress1, "5b2cc274f6eaf3a71647e1f85358ce32", "instanceId", "user-agent");
        examUtilService.addExamSessionToStudentExam(studentExam2, "abc", ipAddress2, "5b2cc274f6eaf3a71647e1f85358ce32", "instanceId", "user-agent");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("differentStudentExamsSameIPAddress", "false");
        params.add("differentStudentExamsSameBrowserFingerprint", "false");
        params.add("sameStudentExamDifferentIPAddresses", "false");
        params.add("sameStudentExamDifferentBrowserFingerprints", "false");
        params.add("ipOutsideOfRange", "true");
        params.add("ipSubnet", subnetIncludingFirstAddress);
        // test with a subnet that includes the first but not the second ip
        var suspiciousSessions = request.getSet("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.OK,
                SuspiciousExamSessionsDTO.class, params);
        assertThat(suspiciousSessions).hasSize(1);
        SuspiciousExamSessionsDTO suspiciousExamSessionsDTO = suspiciousSessions.stream().findFirst().orElseThrow();
        assertThat(suspiciousExamSessionsDTO.examSessions()).hasSize(1);
        var examSessions = suspiciousExamSessionsDTO.examSessions();
        var suspiciousSession = examSessions.stream().findFirst().orElseThrow();
        assertThat(suspiciousSession.ipAddress()).isEqualTo(ipAddress2);
        assertThat(suspiciousSession.suspiciousReasons()).containsExactlyInAnyOrder(SuspiciousSessionReason.IP_ADDRESS_OUTSIDE_OF_RANGE);

        // test with a subnet that includes neither ips
        params.remove("ipSubnet");
        params.add("ipSubnet", subnetIncludingNeitherAddress);
        suspiciousSessions = request.getSet("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.OK, SuspiciousExamSessionsDTO.class,
                params);
        assertThat(suspiciousSessions).hasSize(1);
        var suspiciousSessionTuple = suspiciousSessions.stream().findFirst().orElseThrow();
        assertThat(suspiciousSessionTuple.examSessions()).hasSize(2);
        suspiciousSessionTuple.examSessions().forEach(
                suspiciousSessionDTO -> assertThat(suspiciousSessionDTO.suspiciousReasons()).containsExactlyInAnyOrder(SuspiciousSessionReason.IP_ADDRESS_OUTSIDE_OF_RANGE));

        // test with subnet that contains both ips
        params.remove("ipSubnet");
        params.add("ipSubnet", subnetIncludingBothAddresses);
        suspiciousSessions = request.getSet("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.OK, SuspiciousExamSessionsDTO.class,
                params);
        assertThat(suspiciousSessions).hasSize(0);
    }

    private static Stream<Arguments> provideIpAddressesAndSubnets() {
        return Stream.of(Arguments.of("192.168.1.10", "192.168.1.20", "192.168.1.0/28", "192.168.1.128/25", "192.168.1.0/24"),
                Arguments.of("2001:0db8:85a3:0000:0000:8a2e:0370:7330", "2001:0db8:85a3:0000:0000:8a2e:0370:7331", "2001:0db8:85a3:0000:0000:8a2e:0370:7330/128",
                        "2001:0db8:85a3:0000:0000:8a2e:0370:7000/128", "2001:0db8:85a3:0000:0000:8a2e:0370:7330/64"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @MethodSource("provideMixedIpAddressesAndSubnets")
    void testIpOutsideOfRangeMixedIPv4AndIPv6(String ipAddress1, String ipAddress2, String subnet) throws Exception {
        var studentExam1 = examUtilService.addStudentExamWithUser(exam1, student1);
        var studentExam2 = examUtilService.addStudentExamWithUser(exam1, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        examUtilService.addExamSessionToStudentExam(studentExam1, "abc", ipAddress1, "5b2cc274f6eaf3a71647e1f85358ce32", "instanceId", "user-agent");
        examUtilService.addExamSessionToStudentExam(studentExam2, "abc", ipAddress2, "5b2cc274f6eaf3a71647e1f85358ce32", "instanceId", "user-agent");
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("differentStudentExamsSameIPAddress", "false");
        params.add("differentStudentExamsSameBrowserFingerprint", "false");
        params.add("sameStudentExamDifferentIPAddresses", "false");
        params.add("sameStudentExamDifferentBrowserFingerprints", "false");
        params.add("ipOutsideOfRange", "true");
        params.add("ipSubnet", subnet);
        // the IP address matching IP address type (IPv4 or IPv6) is included in the subnet and the IP address in the other format is ignored --> 0
        assertThat(request.getSet("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.OK, SuspiciousExamSessionsDTO.class, params))
                .hasSize(0);

    }

    private static Stream<Arguments> provideMixedIpAddressesAndSubnets() {
        return Stream.of(Arguments.of("192.168.1.10", "2001:0db8:85a3:0000:0000:8a2e:0370:7331", "192.168.1.0/28"),
                Arguments.of("192.168.1.10", "2001:0db8:85a3:0000:0000:8a2e:0370:7330", "2001:0db8:85a3:0000:0000:8a2e:0370:7330/128"));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @MethodSource("provideAnalysisOptions")
    void testComparingForOtherCriterionThanGivenNoFalsePositives(boolean sameIpDifferentExams, boolean sameFingerprintDifferentExams, boolean differentIpSameExam,
            boolean differentFingerprintSameExam) throws Exception {
        prepareExamSessionsForTestCase(!sameIpDifferentExams, !sameFingerprintDifferentExams, !differentIpSameExam, !differentFingerprintSameExam);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("differentStudentExamsSameIPAddress", sameIpDifferentExams ? "true" : "false");
        params.add("differentStudentExamsSameBrowserFingerprint", sameFingerprintDifferentExams ? "true" : "false");
        params.add("sameStudentExamDifferentIPAddresses", differentIpSameExam ? "true" : "false");
        params.add("sameStudentExamDifferentBrowserFingerprints", differentFingerprintSameExam ? "true" : "false");
        params.add("ipOutsideOfRange", "false");
        Set<SuspiciousExamSessionsDTO> suspiciousSessionTuples = request.getSet("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions",
                HttpStatus.OK, SuspiciousExamSessionsDTO.class, params);
        if (!sameIpDifferentExams && sameFingerprintDifferentExams && !differentIpSameExam && !differentFingerprintSameExam) {
            assertThat(suspiciousSessionTuples).hasSize(1);
            var suspiciousSessions = suspiciousSessionTuples.stream().findFirst().get();
            assertThat(suspiciousSessions.examSessions()).hasSize(2);
            var examSessions = suspiciousSessions.examSessions();
            assertThat(examSessions.stream().findFirst().orElseThrow().suspiciousReasons())
                    .containsExactlyInAnyOrderElementsOf(Set.of(SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT));
        }
        else if (sameIpDifferentExams && !sameFingerprintDifferentExams && !differentIpSameExam && !differentFingerprintSameExam) {
            assertThat(suspiciousSessionTuples).hasSize(1);
            var suspiciousSessions = suspiciousSessionTuples.stream().findFirst().get();
            assertThat(suspiciousSessions.examSessions()).hasSize(2);
            var examSessions = suspiciousSessions.examSessions();
            assertThat(examSessions.stream().findFirst().orElseThrow().suspiciousReasons())
                    .containsExactlyInAnyOrderElementsOf(Set.of(SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS));
        }
        else {
            assertThat(suspiciousSessionTuples).hasSize(0);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSuspiciousSessionsAllOptionsCombined() throws Exception {
        prepareExamSessionsForTestCase(true, true, true, true);
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("differentStudentExamsSameIPAddress", "true");
        params.add("differentStudentExamsSameBrowserFingerprint", "true");
        params.add("sameStudentExamDifferentIPAddresses", "true");
        params.add("sameStudentExamDifferentBrowserFingerprints", "true");
        params.add("ipOutsideOfRange", "true");
        params.add("ipSubnet", "192.168.1.0/28");
        var suspiciousSessions = request.getSet("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.OK,
                SuspiciousExamSessionsDTO.class, params);
        assertThat(suspiciousSessions).hasSize(3);
        List<ExamSessionDTO> outsideOfRangeSessions = suspiciousSessions.stream().flatMap(suspiciousExamSessionsDTO -> suspiciousExamSessionsDTO.examSessions().stream())
                .filter(suspiciousSessionDTO -> suspiciousSessionDTO.suspiciousReasons().contains(SuspiciousSessionReason.IP_ADDRESS_OUTSIDE_OF_RANGE)).toList();
        assertThat(outsideOfRangeSessions).hasSize(4);
        List<ExamSessionDTO> sameIpAndFingerprintDifferentExams = suspiciousSessions.stream()
                .flatMap(suspiciousExamSessionsDTO -> suspiciousExamSessionsDTO.examSessions().stream())
                .filter(suspiciousSessionDTO -> suspiciousSessionDTO.suspiciousReasons().contains(SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_IP_ADDRESS)
                        && suspiciousSessionDTO.suspiciousReasons().contains(SuspiciousSessionReason.DIFFERENT_STUDENT_EXAMS_SAME_BROWSER_FINGERPRINT))
                .toList();
        assertThat(sameIpAndFingerprintDifferentExams).hasSize(2);
        List<ExamSessionDTO> sameStudentExamDifferentIpAndFingerprint = suspiciousSessions.stream()
                .flatMap(suspiciousExamSessionsDTO -> suspiciousExamSessionsDTO.examSessions().stream())
                .filter(suspiciousSessionDTO -> suspiciousSessionDTO.suspiciousReasons().contains(SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_IP_ADDRESSES)
                        && suspiciousSessionDTO.suspiciousReasons().contains(SuspiciousSessionReason.SAME_STUDENT_EXAM_DIFFERENT_BROWSER_FINGERPRINTS))
                .toList();
        assertThat(sameStudentExamDifferentIpAndFingerprint).hasSize(2);
    }
    // </editor-fold>
}
