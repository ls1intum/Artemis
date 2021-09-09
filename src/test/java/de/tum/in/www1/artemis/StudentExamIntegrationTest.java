package de.tum.in.www1.artemis;

import static de.tum.in.www1.artemis.domain.enumeration.BuildPlanType.*;
import static de.tum.in.www1.artemis.util.TestConstants.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.programmingexercise.ProgrammingExerciseTestService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.exam.ExamQuizService;
import de.tum.in.www1.artemis.service.exam.StudentExamService;
import de.tum.in.www1.artemis.util.LocalRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public class StudentExamIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private ExamSessionRepository examSessionRepository;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private SubmissionVersionRepository submissionVersionRepository;

    @Autowired
    private ExamQuizService examQuizService;

    @Autowired
    private QuizSubmissionRepository quizSubmissionRepository;

    @Autowired
    private ParticipationService participationService;

    @Autowired
    private StudentExamService studentExamService;

    @Autowired
    private ObjectMapper objectMapper;

    private List<User> users;

    private Course course1;

    private Course course2;

    private Exam exam1;

    private Exam exam2;

    private Exam testRunExam;

    private StudentExam studentExam1;

    private final List<LocalRepository> studentRepos = new ArrayList<>();

    @BeforeEach
    public void initTestCase() throws Exception {
        users = programmingExerciseTestService.setupTestUsers(10, 1, 0, 2);
        users.remove(database.getUserByLogin("admin")); // the admin is not registered for the course and therefore cannot access the student exam so we need to remove it
        course1 = database.addEmptyCourse();
        exam1 = database.addActiveExamWithRegisteredUser(course1, users.get(1));
        exam1.addRegisteredUser(users.get(0));
        exam1 = examRepository.save(exam1);
        Exam exam2 = database.addExam(course1);
        studentExam1 = database.addStudentExam(exam1);
        studentExam1.setWorkingTime(7200);
        studentExam1.setUser(users.get(0));
        studentExamRepository.save(studentExam1);
        database.addStudentExam(exam2);
        // TODO: all parts using programmingExerciseTestService should also be provided for Gitlab+Jenkins
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
    }

    @AfterEach
    public void resetDatabase() throws Exception {
        programmingExerciseTestService.tearDown();
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();

        for (var repo : studentRepos) {
            repo.resetLocalRepo();
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testFindOne() {
        assertThrows(EntityNotFoundException.class, () -> studentExamRepository.findByIdElseThrow(Long.MAX_VALUE));
        assertThat(studentExamRepository.findByIdElseThrow(studentExam1.getId())).isEqualTo(studentExam1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testFindOneWithExercisesByUserIdAndExamId() {
        var studentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(Long.MAX_VALUE, exam1.getId());
        assertThat(studentExam).isEmpty();
        studentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(users.get(0).getId(), exam1.getId());
        assertThat(studentExam).isPresent();
        assertThat(studentExam.get()).isEqualTo(studentExam1);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testFindAllDistinctWorkingTimesByExamId() {
        assertThat(studentExamRepository.findAllDistinctWorkingTimesByExamId(Long.MAX_VALUE)).isEqualTo(Set.of());
        assertThat(studentExamRepository.findAllDistinctWorkingTimesByExamId(exam1.getId())).isEqualTo(Set.of(studentExam1.getWorkingTime()));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testFindMaxWorkingTimeById() {
        assertThrows(EntityNotFoundException.class, () -> studentExamRepository.findMaxWorkingTimeByExamIdElseThrow(Long.MAX_VALUE));
        assertThat(studentExamRepository.findMaxWorkingTimeByExamIdElseThrow(exam1.getId())).isEqualTo(studentExam1.getWorkingTime());
    }

    private void deleteExam1WithInstructor() throws Exception {
        // change back to instructor user
        database.changeUser("instructor1");
        // Clean up to prevent exceptions during reset database
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId(), HttpStatus.FORBIDDEN, StudentExam.class);
        request.getList("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams", HttpStatus.FORBIDDEN, StudentExam.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetStudentExam_asInstructor() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId(), HttpStatus.OK, StudentExam.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetStudentExamsForExam_asInstructor() throws Exception {
        List<StudentExam> studentExams = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExams.size()).isEqualTo(2);
    }

    private List<StudentExam> prepareStudentExamsForConduction(boolean early) throws Exception {
        ZonedDateTime examVisibleDate;
        ZonedDateTime examStartDate;
        ZonedDateTime examEndDate;
        if (early) {
            examStartDate = ZonedDateTime.now().plusHours(1);
            examEndDate = ZonedDateTime.now().plusHours(3);
        }
        else {
            // If the exam is prepared only 5 minutes before the release date, the repositories of the students are unlocked as well.
            examStartDate = ZonedDateTime.now().plusMinutes(1 + Constants.SECONDS_AFTER_RELEASE_DATE_FOR_UNLOCKING_STUDENT_EXAM_REPOS);
            examEndDate = ZonedDateTime.now().plusMinutes(3 + Constants.SECONDS_AFTER_RELEASE_DATE_FOR_UNLOCKING_STUDENT_EXAM_REPOS);
        }

        examVisibleDate = ZonedDateTime.now().minusMinutes(10 + Constants.SECONDS_AFTER_RELEASE_DATE_FOR_UNLOCKING_STUDENT_EXAM_REPOS);
        // --> 2 min = 120s working time

        bambooRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.enableMockingOfRequests(true);

        course2 = database.addEmptyCourse();
        exam2 = database.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        exam2 = database.addExerciseGroupsAndExercisesToExam(exam2, true);

        // register users
        Set<User> registeredStudents = users.stream().filter(user -> user.getLogin().contains("student")).collect(Collectors.toSet());
        exam2.setRegisteredUsers(registeredStudents);
        exam2.setRandomizeExerciseOrder(false);
        exam2 = examRepository.save(exam2);

        // generate individual student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(exam2.getRegisteredUsers().size());
        assertThat(studentExamRepository.findAll()).hasSize(registeredStudents.size() + 3); // we generate three additional student exams in the @Before method

        // start exercises

        List<ProgrammingExercise> programmingExercises = new ArrayList<>();
        for (var exercise : exam2.getExerciseGroups().get(6).getExercises()) {
            var programmingExercise = (ProgrammingExercise) exercise;
            programmingExercises.add(programmingExercise);

            programmingExerciseTestService.setupRepositoryMocks(programmingExercise);
            for (var user : exam2.getRegisteredUsers()) {
                var repo = new LocalRepository();
                repo.configureRepos("studentRepo", "studentOriginRepo");
                programmingExerciseTestService.setupRepositoryMocksParticipant(programmingExercise, user.getLogin(), repo);
                studentRepos.add(repo);
            }
        }

        for (var programmingExercise : programmingExercises) {
            for (var user : users) {
                mockConnectorRequestsForStartParticipation(programmingExercise, user.getParticipantIdentifier(), Set.of(user), true, HttpStatus.CREATED);
            }
        }

        Integer noGeneratedParticipations = request.postWithResponseBody("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/start-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);

        assertThat(noGeneratedParticipations).isEqualTo(registeredStudents.size() * exam2.getExerciseGroups().size());

        if (!early) {
            // simulate "wait" for exam to start
            exam2.setStartDate(ZonedDateTime.now());
            exam2.setEndDate(ZonedDateTime.now().plusMinutes(2));
            examRepository.save(exam2);
        }

        return studentExams;
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetStudentExamForConduction() throws Exception {
        List<StudentExam> studentExams = prepareStudentExamsForConduction(false);

        for (var studentExam : studentExams) {
            var user = studentExam.getUser();
            database.changeUser(user.getLogin());
            final HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "foo");
            headers.set("X-Artemis-Client-Fingerprint", "bar");
            headers.set("X-Forwarded-For", "10.0.28.1");
            var response = request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/conduction", HttpStatus.OK, StudentExam.class, headers);
            assertThat(response).isEqualTo(studentExam);
            assertThat(response.isStarted()).isTrue();
            assertThat(response.getExercises().size()).isEqualTo(exam2.getNumberOfExercisesInExam());
            var textExercise = (TextExercise) response.getExercises().get(0);
            var quizExercise = (QuizExercise) response.getExercises().get(1);
            assertThat(textExercise.getStudentParticipations().size()).isEqualTo(1);
            var participation1 = textExercise.getStudentParticipations().iterator().next();
            assertThat(participation1.getParticipant()).isEqualTo(user);
            assertThat(participation1.getSubmissions()).hasSize(1);
            assertThat(quizExercise.getStudentParticipations().size()).isEqualTo(1);
            var participation2 = quizExercise.getStudentParticipations().iterator().next();
            assertThat(participation2.getParticipant()).isEqualTo(user);
            assertThat(participation2.getSubmissions()).hasSize(1);

            // Ensure that student exam was marked as started
            assertThat(studentExamRepository.findById(studentExam.getId()).get().isStarted()).isTrue();

            // Check that sensitive information has been removed
            assertThat(textExercise.getGradingCriteria()).isEmpty();
            assertThat(textExercise.getGradingInstructions()).isEqualTo(null);
            assertThat(textExercise.getSampleSolution()).isEqualTo(null);

            // Check that sensitive information has been removed
            assertThat(quizExercise.getGradingCriteria()).isEmpty();
            assertThat(quizExercise.getGradingInstructions()).isEqualTo(null);
            assertThat(quizExercise.getQuizQuestions().size()).isEqualTo(3);

            for (QuizQuestion question : quizExercise.getQuizQuestions()) {
                if (question instanceof MultipleChoiceQuestion) {
                    assertThat(((MultipleChoiceQuestion) question).getAnswerOptions()).hasSize(2);
                    for (AnswerOption answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                        assertThat(answerOption.getExplanation()).isNull();
                        assertThat(answerOption.isIsCorrect()).isNull();
                    }
                }
                else if (question instanceof DragAndDropQuestion) {
                    assertThat(((DragAndDropQuestion) question).getCorrectMappings()).hasSize(0);
                }
                else if (question instanceof ShortAnswerQuestion) {
                    assertThat(((ShortAnswerQuestion) question).getCorrectMappings()).hasSize(0);
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
            assertThat(optionalExamSession.get().getIpAddress().toNormalizedString()).isEqualTo("10.0.28.1");
        }

        deleteExam1WithInstructor();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetTestRunForConduction() throws Exception {
        var instructor = database.getUserByLogin("instructor1");
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        // --> 2 min = 120s working time

        course2 = database.addEmptyCourse();
        exam2 = database.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        var exam = database.addTextModelingProgrammingExercisesToExam(exam2, true, false);
        final var testRun = database.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        assertThat(testRun.isTestRun()).isTrue();

        var response = request.get("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-run/" + testRun.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);
        assertThat(response).isEqualTo(testRun);
        assertThat(response.isStarted()).isTrue();
        assertThat(response.isTestRun()).isTrue();
        assertThat(response.getExercises().size()).isEqualTo(exam.getNumberOfExercisesInExam());
        // Ensure that student exam was marked as started
        assertThat(studentExamRepository.findById(testRun.getId()).get().isStarted()).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testFindAllTestRunsForExam() throws Exception {
        var instructor = database.getUserByLogin("instructor1");
        var instructor2 = database.getUserByLogin("instructor2");
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        // --> 2 min = 120s working time

        course2 = database.addEmptyCourse();
        exam2 = database.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        var exam = database.addTextModelingProgrammingExercisesToExam(exam2, true, false);
        database.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        database.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor2, exam.getExerciseGroups());

        List<StudentExam> response = request.getList("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-runs/", HttpStatus.OK, StudentExam.class);
        assertThat(response.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllTestRunSubmissionsForExercise() throws Exception {
        var instructor = database.getUserByLogin("instructor1");
        course2 = database.addEmptyCourse();
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        exam2 = database.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        var exam = database.addTextModelingProgrammingExercisesToExam(exam2, false, false);
        var testRun = database.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        List<Submission> response = request.getList("/api/exercises/" + testRun.getExercises().get(0).getId() + "/test-run-submissions", HttpStatus.OK, Submission.class);
        assertThat(response).isNotEmpty();
        assertThat((response.get(0).getParticipation()).isTestRun()).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllTestRunSubmissionsForExercise_notExamExercise() throws Exception {
        course2 = database.addEmptyCourse();
        var exercise = database.addProgrammingExerciseToCourse(course2, false);
        request.getList("/api/exercises/" + exercise.getId() + "/test-run-submissions", HttpStatus.FORBIDDEN, Submission.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllTestRunSubmissionsForExercise_notInstructor() throws Exception {
        var instructor = database.getUserByLogin("instructor1");
        course2 = database.addEmptyCourse();
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        exam2 = database.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        var exam = database.addTextModelingProgrammingExercisesToExam(exam2, false, false);
        var testRun = database.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        database.changeUser("student2");
        request.getList("/api/exercises/" + testRun.getExercises().get(0).getId() + "/test-run-submissions", HttpStatus.FORBIDDEN, Submission.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllTestRunSubmissionsForExercise_noTestRunSubmissions() throws Exception {
        course2 = database.addEmptyCourse();
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        exam2 = database.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        var exam = database.addTextModelingProgrammingExercisesToExam(exam2, false, false);
        final var latestSubmissions = request.getList("/api/exercises/" + exam.getExerciseGroups().get(0).getExercises().iterator().next().getId() + "/test-run-submissions",
                HttpStatus.OK, Submission.class);
        assertThat(latestSubmissions).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetWorkingTimesNoStudentExams() {
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(5);
        var examEndDate = ZonedDateTime.now().plusMinutes(20);

        Course course = database.addEmptyCourse();
        Exam exam = database.addExam(course, examVisibleDate, examStartDate, examEndDate);
        exam = database.addExerciseGroupsAndExercisesToExam(exam, true);

        // register user
        exam.setRegisteredUsers(new HashSet<>(users));
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetWorkingTimesDifferentStudentExams() throws Exception {
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(5);
        var examEndDate = ZonedDateTime.now().plusMinutes(20);

        Course course = database.addEmptyCourse();
        Exam exam = database.addExam(course, examVisibleDate, examStartDate, examEndDate);
        exam = database.addExerciseGroupsAndExercisesToExam(exam, true);

        // register user
        exam.setRegisteredUsers(new HashSet<>(users));
        exam.setRandomizeExerciseOrder(false);
        exam = examRepository.save(exam);

        // generate individual student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.OK);

        // Modify working times

        var expectedWorkingTimes = new HashSet<Integer>();
        int maxWorkingTime = (int) Duration.between(examStartDate, examEndDate).getSeconds();

        for (int i = 0; i < studentExams.size(); i++) {
            if (i % 2 == 0)
                maxWorkingTime += 35;
            expectedWorkingTimes.add(maxWorkingTime);

            var studentExam = studentExams.get(i);
            studentExam.setWorkingTime(maxWorkingTime);
            studentExamRepository.save(studentExam);
        }

        assertThat(studentExamRepository.findMaxWorkingTimeByExamId(exam.getId())).contains(maxWorkingTime);
        assertThat(studentExamRepository.findAllDistinctWorkingTimesByExamId(exam.getId())).containsExactlyInAnyOrderElementsOf(expectedWorkingTimes);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateWorkingTime() throws Exception {
        int newWorkingTime = 180 * 60;
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        exam1 = examRepository.save(exam1);
        StudentExam result = request.patchWithResponseBody(
                "/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time", newWorkingTime, StudentExam.class,
                HttpStatus.OK);
        assertThat(result.getWorkingTime()).isEqualTo(newWorkingTime);
        assertThat(studentExamRepository.findById(studentExam1.getId()).get().getWorkingTime()).isEqualTo(newWorkingTime);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateWorkingTimeInvalid() throws Exception {
        int newWorkingTime = 0;
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        exam1 = examRepository.save(exam1);
        request.patchWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time", newWorkingTime,
                StudentExam.class, HttpStatus.BAD_REQUEST);
        // working time did not change
        var studentExamDB = studentExamRepository.findById(studentExam1.getId()).get();
        assertThat(studentExamDB.getWorkingTime()).isEqualTo(studentExam1.getWorkingTime());

        newWorkingTime = -10;
        request.patchWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time", newWorkingTime,
                StudentExam.class, HttpStatus.BAD_REQUEST);
        // working time did not change
        studentExamDB = studentExamRepository.findById(studentExam1.getId()).get();
        assertThat(studentExamDB.getWorkingTime()).isEqualTo(studentExam1.getWorkingTime());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateWorkingTimeLate() throws Exception {
        int newWorkingTime = 180 * 60;
        exam1.setVisibleDate(ZonedDateTime.now());
        exam1 = examRepository.save(exam1);
        request.patchWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time", newWorkingTime,
                StudentExam.class, HttpStatus.BAD_REQUEST);
        // working time did not change
        var studentExamDB = studentExamRepository.findById(studentExam1.getId()).get();
        assertThat(studentExamDB.getWorkingTime()).isEqualTo(studentExam1.getWorkingTime());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSubmitStudentExam_alreadySubmitted() throws Exception {
        studentExam1.setSubmitted(true);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.CONFLICT);
        studentExamRepository.save(studentExam1);
        studentExam1.setSubmitted(false);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSubmitStudentExam_notInTime() throws Exception {
        studentExam1.setSubmitted(false);
        studentExamRepository.save(studentExam1);
        // Forbidden because user tried to submit before start
        exam1.setStartDate(ZonedDateTime.now().plusHours(1));
        examRepository.save(exam1);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.FORBIDDEN);
        // Forbidden because user tried to submit after end
        exam1.setStartDate(ZonedDateTime.now().minusHours(5));
        examRepository.save(exam1);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSubmitStudentExam_differentUser() throws Exception {
        studentExam1.setSubmitted(false);
        studentExamRepository.save(studentExam1);
        // Forbidden because user object is wrong
        User student2 = database.getUserByLogin("student2");
        studentExam1.setUser(student2);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.FORBIDDEN);

        User student1 = database.getUserByLogin("student1");
        studentExam1 = studentExamRepository.findByIdElseThrow(studentExam1.getId());
        assertThat(studentExam1.getUser()).isEqualTo(student1);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSubmitStudentExam() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.OK, null);
        StudentExam submittedStudentExam = studentExamRepository.findById(studentExam1.getId()).get();
        // Ensure that student exam has been marked as submitted
        assertThat(submittedStudentExam.isSubmitted()).isTrue();
        // Ensure that student exam has been set
        assertThat(submittedStudentExam.getSubmissionDate()).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSubmitExamOtherUser_forbidden() throws Exception {
        prepareStudentExamsForConduction(false);
        database.changeUser("student1");
        var studentExamResponse = request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/conduction", HttpStatus.OK, StudentExam.class);
        studentExamResponse.setExercises(null);
        // use a different user
        database.changeUser("student2");
        request.post("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamResponse, HttpStatus.FORBIDDEN);
        deleteExam1WithInstructor();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testgetExamTooEarly_forbidden() throws Exception {
        prepareStudentExamsForConduction(true);

        database.changeUser("student1");

        request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/conduction", HttpStatus.FORBIDDEN, StudentExam.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAssessUnsubmittedStudentExams() throws Exception {
        prepareStudentExamsForConduction(false);
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(10));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(8));
        exam2 = examRepository.save(exam2);

        request.postWithoutLocation("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams", Optional.empty(),
                HttpStatus.OK, null);
        database.changeUser("instructor1");
        Set<StudentExam> unsubmittedStudentExams = studentExamRepository.findAllUnsubmittedWithExercisesByExamId(exam2.getId());
        Map<User, List<Exercise>> exercisesOfUser = studentExamService.getExercisesOfUserMap(unsubmittedStudentExams);
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (final var studentParticipation : studentParticipations) {
                if (studentParticipation.findLatestSubmission().isPresent()) {
                    var result = studentParticipation.findLatestSubmission().get().getLatestResult();
                    assertThat(result).isNotNull();
                    assertThat(result.getScore()).isEqualTo(0);
                    assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                    result = resultRepository.findByIdWithEagerFeedbacks(result.getId()).get();
                    assertThat(result.getFeedbacks()).isNotEmpty();
                    assertThat(result.getFeedbacks().get(0).getDetailText()).isEqualTo("You did not submit your exam");
                }
                else {
                    fail("StudentParticipation which is part of an unsubmitted StudentExam contains no submission or result after automatic assessment of unsubmitted student exams call.");
                }
            }
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAssessUnsubmittedStudentExamsForMultipleCorrectionRounds() throws Exception {
        prepareStudentExamsForConduction(false);
        exam2.setNumberOfCorrectionRoundsInExam(2);
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(10));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(8));
        exam2 = examRepository.save(exam2);

        request.postWithoutLocation("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams", Optional.empty(),
                HttpStatus.OK, null);
        database.changeUser("instructor1");
        Set<StudentExam> unsubmittedStudentExams = studentExamRepository.findAllUnsubmittedWithExercisesByExamId(exam2.getId());
        Map<User, List<Exercise>> exercisesOfUser = studentExamService.getExercisesOfUserMap(unsubmittedStudentExams);
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (final var studentParticipation : studentParticipations) {
                if (studentParticipation.findLatestSubmission().isPresent()) {
                    assertThat(Objects.requireNonNull(studentParticipation.findLatestSubmission().get().getResults()).size()).isEqualTo(exam2.getNumberOfCorrectionRoundsInExam());
                    for (var result : Objects.requireNonNull(studentParticipation.findLatestSubmission().get().getResults())) {
                        assertThat(result).isNotNull();
                        assertThat(result.getScore()).isEqualTo(0);
                        assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                        result = resultRepository.findByIdWithEagerFeedbacks(result.getId()).get();
                        assertThat(result.getFeedbacks()).isNotEmpty();
                        assertThat(result.getFeedbacks().get(0).getDetailText()).isEqualTo("You did not submit your exam");
                    }
                }
                else {
                    fail("StudentParticipation which is part of an unsubmitted StudentExam contains no submission or result after automatic assessment of unsubmitted student exams call.");
                }
            }
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAssessEmptyExamSubmissions() throws Exception {
        final var studentExams = prepareStudentExamsForConduction(false);
        // submit student exam with empty submissions
        for (final var studentExam : studentExams) {
            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExamRepository.save(studentExam);
        }
        // this test should be after the end date of the exam
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(10));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(7));
        examRepository.save(exam2);

        request.postWithoutLocation("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams", Optional.empty(),
                HttpStatus.OK, null);
        database.changeUser("instructor1");
        Map<User, List<Exercise>> exercisesOfUser = studentExamService.getExercisesOfUserMap(new HashSet<>(studentExams));
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (final var studentParticipation : studentParticipations) {
                if (studentParticipation.findLatestSubmission().isPresent()) {
                    var result = studentParticipation.findLatestSubmission().get().getLatestResult();
                    assertThat(result).isNotNull();
                    assertThat(result.getScore()).isEqualTo(0);
                    assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                    result = resultRepository.findByIdWithEagerFeedbacks(result.getId()).get();
                    assertThat(result.getFeedbacks()).isNotEmpty();
                    assertThat(result.getFeedbacks().get(0).getDetailText()).isEqualTo("Empty submission");
                }
                else {
                    fail("StudentParticipation which is part of an unsubmitted StudentExam contains no submission or result after automatic assessment of unsubmitted student exams call.");
                }
            }
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAssessEmptyExamSubmissionsForMultipleCorrectionRounds() throws Exception {
        final var studentExams = prepareStudentExamsForConduction(false);
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

        request.postWithoutLocation("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams", Optional.empty(),
                HttpStatus.OK, null);
        database.changeUser("instructor1");
        Map<User, List<Exercise>> exercisesOfUser = studentExamService.getExercisesOfUserMap(new HashSet<>(studentExams));
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (final var studentParticipation : studentParticipations) {
                if (studentParticipation.findLatestSubmission().isPresent()) {
                    assertThat(Objects.requireNonNull(studentParticipation.findLatestSubmission().get().getResults()).size()).isEqualTo(exam2.getNumberOfCorrectionRoundsInExam());
                    for (var result : Objects.requireNonNull(studentParticipation.findLatestSubmission().get().getResults())) {
                        assertThat(result).isNotNull();
                        assertThat(result.getScore()).isEqualTo(0);
                        assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                        result = resultRepository.findByIdWithEagerFeedbacks(result.getId()).get();
                        assertThat(result.getFeedbacks()).isNotEmpty();
                        assertThat(result.getFeedbacks().get(0).getDetailText()).isEqualTo("Empty submission");
                    }
                }
                else {
                    fail("StudentParticipation which is part of an unsubmitted StudentExam contains no submission or result after automatic assessment of unsubmitted student exams call.");
                }
            }
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAssessUnsubmittedStudentExams_forbidden() throws Exception {
        prepareStudentExamsForConduction(false);
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(1));
        exam2 = examRepository.save(exam2);

        database.changeUser("tutor1");
        request.postWithoutLocation("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams", null,
                HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAssessUnsubmittedStudentExams_badRequest() throws Exception {
        prepareStudentExamsForConduction(false);
        exam2 = examRepository.save(exam2);

        request.postWithoutLocation("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams", null,
                HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testAssessExamWithSubmissionResult() throws Exception {

        List<StudentExam> studentExams = prepareStudentExamsForConduction(false);

        // this test should be after the end date of the exam
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(1));
        examRepository.save(exam2);

        database.changeUser(studentExams.get(0).getUser().getLogin());
        var studentExamResponse = request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/conduction", HttpStatus.OK, StudentExam.class);
        for (var exercise : studentExamResponse.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            Submission submission = null;
            if (exercise instanceof ProgrammingExercise) {
                submission = new ProgrammingSubmission();
            }
            else if (exercise instanceof TextExercise) {
                submission = new TextSubmission();
            }
            else if (exercise instanceof ModelingExercise) {
                submission = new ModelingSubmission();
            }
            else if (exercise instanceof QuizExercise) {
                submission = new QuizSubmission();
            }
            if (submission != null) {
                submission.addResult(new Result());
                Set<Submission> submissions = new HashSet<>();
                submissions.add(submission);
                participation.setSubmissions(submissions);
            }
        }

        studentExamResponse = request.postWithResponseBody("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamResponse,
                StudentExam.class, HttpStatus.OK);
        assertThat(studentExamResponse.isSubmitted()).isTrue();
        assertThat(studentExamResponse.getSubmissionDate()).isNotNull();

        // check that the result was not injected and that the student exam was still submitted correctly

        var studentExamDatabase = request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/conduction", HttpStatus.OK, StudentExam.class);
        for (var exercise : studentExamDatabase.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            var iterator = participation.getSubmissions().iterator();
            if (iterator.hasNext()) {
                var submission = iterator.next();
                assertThat(submission.getLatestResult()).isNull();
            }
        }
        deleteExam1WithInstructor();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSubmitStudentExam_early() throws Exception {
        List<StudentExam> studentExams = prepareStudentExamsForConduction(false);

        // we have to reset the mock provider and enable it again so that we can mock additional requests below
        bitbucketRequestMockProvider.reset();
        bitbucketRequestMockProvider.enableMockingOfRequests(true);

        database.changeUser(studentExams.get(0).getUser().getLogin());
        var studentExamResponse = request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/conduction", HttpStatus.OK, StudentExam.class);
        final List<ProgrammingExercise> exercisesToBeLocked = new ArrayList<>();
        final List<ProgrammingExerciseStudentParticipation> studentProgrammingParticipations = new ArrayList<>();

        for (var exercise : studentExamResponse.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                studentProgrammingParticipations.add((ProgrammingExerciseStudentParticipation) participation);
                exercisesToBeLocked.add(programmingExercise);
                final var repositorySlug = (programmingExercise.getProjectKey() + "-" + participation.getParticipantIdentifier()).toLowerCase();
                bitbucketRequestMockProvider.mockSetRepositoryPermissionsToReadOnly(repositorySlug, programmingExercise.getProjectKey(), participation.getStudents());
            }
        }

        // submit early
        var submittedStudentExam = request.postWithResponseBody("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamResponse,
                StudentExam.class, HttpStatus.OK);
        assertThat(submittedStudentExam.isSubmitted()).isTrue();
        assertThat(submittedStudentExam.getSubmissionDate()).isNotNull();

        // assert that the user cannot submit again
        request.post("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamResponse, HttpStatus.CONFLICT);

        // assert that all repositories of programming exercises have been locked
        assert exercisesToBeLocked.size() == studentProgrammingParticipations.size();
        for (int i = 0; i < exercisesToBeLocked.size(); i++) {
            verify(programmingExerciseParticipationService, atLeastOnce()).lockStudentRepository(exercisesToBeLocked.get(i), studentProgrammingParticipations.get(i));
        }
        deleteExam1WithInstructor();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSubmitStudentExam_realistic() throws Exception {

        List<StudentExam> studentExams = prepareStudentExamsForConduction(false);

        List<StudentExam> studentExamsAfterStart = new ArrayList<>();
        for (var studentExam : studentExams) {
            database.changeUser(studentExam.getUser().getLogin());
            var studentExamResponse = request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/conduction", HttpStatus.OK, StudentExam.class);

            for (var exercise : studentExamResponse.getExercises()) {
                var participation = exercise.getStudentParticipations().iterator().next();
                if (exercise instanceof ProgrammingExercise) {
                    doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
                    bambooRequestMockProvider.reset();
                    bambooRequestMockProvider.enableMockingOfRequests(true);
                    bambooRequestMockProvider.mockTriggerBuild((ProgrammingExerciseParticipation) participation);
                    request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());
                    Optional<ProgrammingSubmission> programmingSubmission = programmingSubmissionRepository
                            .findFirstByParticipationIdOrderByLegalSubmissionDateDesc(participation.getId());
                    assertThat(programmingSubmission).isPresent();
                    participation.getSubmissions().add(programmingSubmission.get());
                    continue;
                }
                var submission = participation.getSubmissions().iterator().next();
                if (exercise instanceof ModelingExercise) {
                    // check that the submission was saved and that a submitted version was created
                    String newModel = "This is a new model";
                    String newExplanation = "This is an explanation";
                    var modelingSubmission = (ModelingSubmission) submission;
                    modelingSubmission.setModel(newModel);
                    modelingSubmission.setExplanationText(newExplanation);
                    request.put("/api/exercises/" + exercise.getId() + "/modeling-submissions", modelingSubmission, HttpStatus.OK);
                    var savedModelingSubmission = request.get(
                            "/api/participations/" + exercise.getStudentParticipations().iterator().next().getId() + "/latest-modeling-submission", HttpStatus.OK,
                            ModelingSubmission.class);
                    // check that the submission was saved
                    assertThat(newModel).isEqualTo(savedModelingSubmission.getModel());
                    // check that a submitted version was created
                    assertVersionedSubmission(modelingSubmission);
                }
                else if (exercise instanceof TextExercise) {
                    var textSubmission = (TextSubmission) submission;
                    final var newText = "New Text";
                    textSubmission.setText(newText);
                    request.put("/api/exercises/" + exercise.getId() + "/text-submissions", textSubmission, HttpStatus.OK);
                    var savedTextSubmission = (TextSubmission) submissionRepository.findById(textSubmission.getId()).get();
                    // check that the submission was saved
                    assertThat(newText).isEqualTo(savedTextSubmission.getText());
                    // check that a submitted version was created
                    assertVersionedSubmission(textSubmission);
                }
                else if (exercise instanceof QuizExercise) {
                    submitQuizInExam((QuizExercise) exercise, (QuizSubmission) submission);
                }
            }

            studentExamsAfterStart.add(studentExamResponse);
        }

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(1));
        exam2 = examRepository.save(exam2);

        // reset to exchange expectation for newCommitHash
        bambooRequestMockProvider.reset();
        bambooRequestMockProvider.enableMockingOfRequests(true);
        final String newCommitHash = "2ec6050142b9c187909abede819c083c8745c19b";
        final ObjectId newCommitHashObjectId = ObjectId.fromString(newCommitHash);

        for (var studentExam : studentExamsAfterStart) {
            for (var exercise : studentExam.getExercises()) {
                var participation = exercise.getStudentParticipations().iterator().next();
                if (exercise instanceof ProgrammingExercise) {
                    // do another programming submission to check if the StudentExam after submit contains the new commit hash
                    doReturn(newCommitHashObjectId).when(gitService).getLastCommitHash(any());
                    bambooRequestMockProvider.reset();
                    bambooRequestMockProvider.enableMockingOfRequests(true);
                    bambooRequestMockProvider.mockTriggerBuild((ProgrammingExerciseParticipation) participation);
                    database.changeUser(studentExam.getUser().getLogin());
                    request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());
                    // do not add programming submission to participation, because we want to simulate, that the latest submission is not present
                }
            }
        }

        List<StudentExam> studentExamsAfterFinish = new ArrayList<>();
        for (var studentExamAfterStart : studentExamsAfterStart) {
            database.changeUser(studentExamAfterStart.getUser().getLogin());
            var studentExamFinished = request.postWithResponseBody("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamAfterStart,
                    StudentExam.class, HttpStatus.OK);
            // Check that all text/quiz/modeling submissions were saved and that submitted versions were created
            for (var exercise : studentExamFinished.getExercises()) {
                var participationAfterFinish = exercise.getStudentParticipations().iterator().next();
                var submissionAfterFinish = participationAfterFinish.getSubmissions().iterator().next();

                var exerciseAfterStart = studentExamAfterStart.getExercises().stream().filter(exAfterStart -> exAfterStart.getId().equals(exercise.getId())).findFirst().get();
                var participationAfterStart = exerciseAfterStart.getStudentParticipations().iterator().next();
                var submissionAfterStart = participationAfterStart.getSubmissions().iterator().next();

                if (exercise instanceof ModelingExercise) {
                    var modelingSubmissionAfterFinish = (ModelingSubmission) submissionAfterFinish;
                    var modelingSubmissionAfterStart = (ModelingSubmission) submissionAfterStart;
                    assertThat(modelingSubmissionAfterFinish).isEqualTo(modelingSubmissionAfterStart);
                    assertVersionedSubmission(modelingSubmissionAfterStart);
                    assertVersionedSubmission(modelingSubmissionAfterFinish);
                }
                else if (exercise instanceof TextExercise) {
                    var textSubmissionAfterFinish = (TextSubmission) submissionAfterFinish;
                    var textSubmissionAfterStart = (TextSubmission) submissionAfterStart;
                    assertThat(textSubmissionAfterFinish).isEqualTo(textSubmissionAfterStart);
                    assertVersionedSubmission(textSubmissionAfterStart);
                    assertVersionedSubmission(textSubmissionAfterFinish);
                }
                else if (exercise instanceof QuizExercise) {
                    var quizSubmissionAfterFinish = (QuizSubmission) submissionAfterFinish;
                    var quizSubmissionAfterStart = (QuizSubmission) submissionAfterStart;
                    assertThat(quizSubmissionAfterFinish).isEqualTo(quizSubmissionAfterStart);
                    assertVersionedSubmission(quizSubmissionAfterStart);
                    assertVersionedSubmission(quizSubmissionAfterFinish);
                }
                else if (exercise instanceof ProgrammingExercise) {
                    var programmingSubmissionAfterStart = (ProgrammingSubmission) submissionAfterStart;
                    var programmingSubmissionAfterFinish = (ProgrammingSubmission) submissionAfterFinish;
                    // assert that we did not update the submission prematurely
                    assertThat(programmingSubmissionAfterStart.getCommitHash()).isEqualTo(COMMIT_HASH_STRING);
                    // assert that we get the correct commit hash after submit
                    assertThat(programmingSubmissionAfterFinish.getCommitHash()).isEqualTo(newCommitHash);
                }

            }

            studentExamsAfterFinish.add(studentExamFinished);

            assertThat(studentExamFinished.isSubmitted()).isTrue();
            assertThat(studentExamFinished.getSubmissionDate()).isNotNull();
        }
        // The method lockStudentRepository will only be called if the student hands in early (see separate test)
        verify(programmingExerciseParticipationService, never()).lockStudentRepository(any(), any());
        assertThat(studentExamsAfterFinish).hasSize(studentExamsAfterStart.size());

        deleteExam1WithInstructor();
    }

    private void submitQuizInExam(QuizExercise quizExercise, QuizSubmission quizSubmission) throws Exception {
        // check that the submission was saved and that a submitted version was created
        int dndDragItemIndex = 1;
        int dndLocationIndex = 2;
        String shortAnswerText = "New Short Answer Text";
        int saSpotIndex = 1;
        int mcSelectedOptionIndex = 0;
        quizExercise.getQuizQuestions().forEach(quizQuestion -> {
            if (quizQuestion instanceof DragAndDropQuestion) {
                var submittedAnswer = new DragAndDropSubmittedAnswer();
                DragAndDropMapping dndMapping = new DragAndDropMapping();
                dndMapping.setDragItemIndex(dndDragItemIndex);
                dndMapping.setDragItem(((DragAndDropQuestion) quizQuestion).getDragItems().get(dndDragItemIndex));
                dndMapping.setDropLocationIndex(dndLocationIndex);
                dndMapping.setDropLocation(((DragAndDropQuestion) quizQuestion).getDropLocations().get(dndLocationIndex));
                submittedAnswer.getMappings().add(dndMapping);
                submittedAnswer.setQuizQuestion(quizQuestion);
                quizSubmission.getSubmittedAnswers().add(submittedAnswer);
            }
            else if (quizQuestion instanceof ShortAnswerQuestion) {
                var submittedAnswer = new ShortAnswerSubmittedAnswer();
                ShortAnswerSubmittedText shortAnswerSubmittedText = new ShortAnswerSubmittedText();
                shortAnswerSubmittedText.setText(shortAnswerText);
                shortAnswerSubmittedText.setSpot(((ShortAnswerQuestion) quizQuestion).getSpots().get(saSpotIndex));
                submittedAnswer.getSubmittedTexts().add(shortAnswerSubmittedText);
                submittedAnswer.setQuizQuestion(quizQuestion);
                quizSubmission.getSubmittedAnswers().add(submittedAnswer);
            }
            else if (quizQuestion instanceof MultipleChoiceQuestion) {
                var answerOptions = ((MultipleChoiceQuestion) quizQuestion).getAnswerOptions();
                var submittedAnswer = new MultipleChoiceSubmittedAnswer();
                submittedAnswer.addSelectedOptions(answerOptions.get(mcSelectedOptionIndex));
                submittedAnswer.setQuizQuestion(quizQuestion);
                quizSubmission.getSubmittedAnswers().add(submittedAnswer);
            }
        });
        QuizSubmission savedQuizSubmission = request.putWithResponseBody("/api/exercises/" + quizExercise.getId() + "/submissions/exam", quizSubmission, QuizSubmission.class,
                HttpStatus.OK);
        // check the submission
        assertThat(savedQuizSubmission.getSubmittedAnswers()).isNotNull();
        assertThat(savedQuizSubmission.getSubmittedAnswers().size()).isGreaterThan(0);
        quizExercise.getQuizQuestions().forEach(quizQuestion -> {
            SubmittedAnswer submittedAnswer = savedQuizSubmission.getSubmittedAnswerForQuestion(quizQuestion);
            if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer answer) {
                assertThat(answer.getSelectedOptions()).isNotNull();
                assertThat(answer.getSelectedOptions().size()).isGreaterThan(0);
                assertThat(answer.getSelectedOptions().iterator().next()).isNotNull();
                assertThat(answer.getSelectedOptions().iterator().next()).isEqualTo(((MultipleChoiceQuestion) quizQuestion).getAnswerOptions().get(mcSelectedOptionIndex));
            }
            else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer answer) {
                assertThat(answer.getSubmittedTexts()).isNotNull();
                assertThat(answer.getSubmittedTexts().size()).isGreaterThan(0);
                assertThat(answer.getSubmittedTexts().iterator().next()).isNotNull();
                assertThat(answer.getSubmittedTexts().iterator().next().getText()).isEqualTo(shortAnswerText);
                assertThat(answer.getSubmittedTexts().iterator().next().getSpot()).isEqualTo(((ShortAnswerQuestion) quizQuestion).getSpots().get(saSpotIndex));
            }
            else if (submittedAnswer instanceof DragAndDropSubmittedAnswer answer) {
                assertThat(answer.getMappings()).isNotNull();
                assertThat(answer.getMappings().size()).isGreaterThan(0);
                assertThat(answer.getMappings().iterator().next()).isNotNull();
                assertThat(answer.getMappings().iterator().next().getDragItem()).isEqualTo(((DragAndDropQuestion) quizQuestion).getDragItems().get(dndDragItemIndex));
                assertThat(answer.getMappings().iterator().next().getDropLocation()).isEqualTo(((DragAndDropQuestion) quizQuestion).getDropLocations().get(dndLocationIndex));
            }
        });
        assertVersionedSubmission(quizSubmission);
    }

    private void assertVersionedSubmission(Submission submission) {
        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());
        var versionedSubmission = submissionVersionRepository.findLatestVersion(submission.getId());
        assert versionedSubmission.isPresent();
        if (submission instanceof TextSubmission) {
            assertThat(((TextSubmission) submission).getText()).isEqualTo(versionedSubmission.get().getContent());
        }
        else if (submission instanceof ModelingSubmission modelingSubmission) {
            assertThat("Model: " + modelingSubmission.getModel() + "; Explanation: " + modelingSubmission.getExplanationText()).isEqualTo(versionedSubmission.get().getContent());
        }
        else if (submission instanceof FileUploadSubmission) {
            assertThat(((FileUploadSubmission) submission).getFilePath()).isEqualTo(versionedSubmission.get().getContent());
        }
        else {
            assert submission instanceof QuizSubmission;
            String submittedAnswersAsString;
            try {
                submittedAnswersAsString = objectMapper.writeValueAsString(((QuizSubmission) submission).getSubmittedAnswers());
            }
            catch (Exception e) {
                submittedAnswersAsString = submission.toString();
            }
            assertThat(submittedAnswersAsString).isEqualTo(versionedSubmission.get().getContent());
            assertThat(submission).isEqualTo(versionedSubmission.get().getSubmission());
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testStudentExamSummaryAsStudentBeforePublishResults_doFilter() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false).get(0);
        StudentExam studentExamWithSubmissions = addExamExerciseSubmissionsForUser(exam2, studentExam.getUser().getLogin());

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(1));
        exam2 = examRepository.save(exam2);

        // submitExam
        var studentExamFinished = request.postWithResponseBody("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamWithSubmissions,
                StudentExam.class, HttpStatus.OK);

        // Add results to all exercise submissions
        database.changeUser("instructor1");
        for (var exercise : studentExamFinished.getExercises()) {
            if (exercise instanceof QuizExercise) {
                continue;
            }

            Participation participation = exercise.getStudentParticipations().iterator().next();
            Optional<Submission> latestSubmission = participation.findLatestSubmission();

            database.addResultToParticipation(participation, latestSubmission.get());
        }
        // evaluate quizzes
        request.postWithoutLocation("/api/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/student-exams/evaluate-quiz-exercises", null, HttpStatus.OK,
                new HttpHeaders());

        // user tries to access exam summary
        database.changeUser(studentExam.getUser().getLogin());
        var studentExamSummary = request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/summary", HttpStatus.OK, StudentExam.class);

        // check that no results or other sensitive information is visible for the student
        for (final var exercise : studentExamSummary.getExercises()) {
            assertThat(exercise.getStudentParticipations().iterator().next().getResults()).isEmpty();
            assertThat(exercise.getGradingInstructions()).isNull();
            assertThat(exercise.getGradingCriteria()).isEmpty();

            if (exercise instanceof QuizExercise) {
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
        }
        deleteExam1WithInstructor();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testStudentExamSummaryAsStudentAfterPublishResults_dontFilter() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false).get(0);
        StudentExam studentExamWithSubmissions = addExamExerciseSubmissionsForUser(exam2, studentExam.getUser().getLogin());

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2 = examRepository.save(exam2);

        // submitExam
        var studentExamFinished = request.postWithResponseBody("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamWithSubmissions,
                StudentExam.class, HttpStatus.OK);

        exam2.setEndDate(ZonedDateTime.now());
        exam2 = examRepository.save(exam2);

        // Add results to all exercise submissions
        database.changeUser("instructor1");
        for (var exercise : studentExamFinished.getExercises()) {
            if (exercise instanceof QuizExercise) {
                continue;
            }

            Participation participation = exercise.getStudentParticipations().iterator().next();
            Optional<Submission> latestSubmission = participation.findLatestSubmission();

            database.addResultToParticipation(participation, latestSubmission.get());
        }
        exam2.setPublishResultsDate(ZonedDateTime.now());
        exam2 = examRepository.save(exam2);

        // evaluate quizzes
        request.postWithoutLocation("/api/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/student-exams/evaluate-quiz-exercises", null, HttpStatus.OK,
                new HttpHeaders());

        // users tries to access exam summary after results are published
        database.changeUser(studentExam.getUser().getLogin());
        var studentExamSummary = request.get("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/summary", HttpStatus.OK, StudentExam.class);

        // check that all relevant information is visible to the student
        for (final var exercise : studentExamSummary.getExercises()) {
            assertThat(exercise.getStudentParticipations().iterator().next().getResults()).isNotEmpty();
            assertThat(exercise.getGradingInstructions()).isNull();
            assertThat(exercise.getGradingCriteria()).isEmpty();

            if (exercise instanceof QuizExercise) {
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
        }
        deleteExam1WithInstructor();
    }

    private StudentExam addExamExerciseSubmissionsForUser(Exam exam, String userLogin) throws Exception {
        if (userLogin != null) {
            database.changeUser(userLogin);
        }
        // start exam conduction for a user
        var studentExam = request.get("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam2.getId() + "/student-exams/conduction", HttpStatus.OK, StudentExam.class);

        for (var exercise : studentExam.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            if (exercise instanceof ProgrammingExercise) {
                doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
                bambooRequestMockProvider.reset();
                bambooRequestMockProvider.enableMockingOfRequests(true);
                bambooRequestMockProvider.mockTriggerBuild((ProgrammingExerciseParticipation) participation);
                request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());
                Optional<ProgrammingSubmission> programmingSubmission = programmingSubmissionRepository
                        .findFirstByParticipationIdOrderByLegalSubmissionDateDesc(participation.getId());
                programmingSubmission.ifPresent(submission -> participation.getSubmissions().add(submission));
                continue;
            }
            var submission = participation.getSubmissions().iterator().next();
            if (exercise instanceof ModelingExercise) {
                // check that the submission was saved and that a submitted version was created
                String newModel = "This is a new model";
                var modelingSubmission = (ModelingSubmission) submission;
                modelingSubmission.setModel(newModel);
                request.put("/api/exercises/" + exercise.getId() + "/modeling-submissions", modelingSubmission, HttpStatus.OK);
            }
            else if (exercise instanceof TextExercise) {
                var textSubmission = (TextSubmission) submission;
                final var newText = "New Text";
                textSubmission.setText(newText);
                request.put("/api/exercises/" + exercise.getId() + "/text-submissions", textSubmission, HttpStatus.OK);
            }
            else if (exercise instanceof QuizExercise) {
                submitQuizInExam((QuizExercise) exercise, (QuizSubmission) submission);
            }
        }
        return studentExam;
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteExamWithStudentExamsAfterConductionAndEvaluation() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false).get(0);

        final StudentExam studentExamWithSubmissions = addExamExerciseSubmissionsForUser(exam2, studentExam.getUser().getLogin());

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2 = examRepository.save(exam2);

        // submitExam
        var studentExamFinished = request.postWithResponseBody("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamWithSubmissions,
                StudentExam.class, HttpStatus.OK);

        exam2.setEndDate(ZonedDateTime.now());
        exam2 = examRepository.save(exam2);

        // Add results to all exercise submissions (evaluation)
        database.changeUser("instructor1");
        for (var exercise : studentExamFinished.getExercises()) {
            if (exercise instanceof QuizExercise) {
                continue;
            }

            Participation participation = exercise.getStudentParticipations().iterator().next();
            Optional<Submission> latestSubmission = participation.findLatestSubmission();

            database.addResultToParticipation(participation, latestSubmission.get());
        }
        exam2.setPublishResultsDate(ZonedDateTime.now());
        exam2 = examRepository.save(exam2);

        // evaluate quizzes
        request.postWithoutLocation("/api/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/student-exams/evaluate-quiz-exercises", null, HttpStatus.OK,
                new HttpHeaders());

        bitbucketRequestMockProvider.reset();
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bambooRequestMockProvider.reset();
        bambooRequestMockProvider.enableMockingOfRequests(true);
        final ProgrammingExercise programmingExercise = (ProgrammingExercise) exam2.getExerciseGroups().get(6).getExercises().iterator().next();
        final String projectKey = programmingExercise.getProjectKey();
        programmingExerciseTestService.setupRepositoryMocks(programmingExercise);

        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());

        List<String> studentLogins = new ArrayList<>();
        for (final User user : exam2.getRegisteredUsers()) {
            studentLogins.add(user.getLogin());
        }
        bambooRequestMockProvider.mockDeleteBambooBuildProject(projectKey);
        List<String> planNames = new ArrayList<>(studentLogins);
        planNames.add(TEMPLATE.getName());
        planNames.add(SOLUTION.getName());
        for (final String planName : planNames) {
            bambooRequestMockProvider.mockDeleteBambooBuildPlan(projectKey + "-" + planName.toUpperCase(), false);
        }
        List<String> repoNames = new ArrayList<>(studentLogins);

        for (final var repoType : RepositoryType.values()) {
            bitbucketRequestMockProvider.mockDeleteRepository(projectKey, programmingExercise.generateRepositoryName(repoType), false);
        }

        for (final var repoName : repoNames) {
            bitbucketRequestMockProvider.mockDeleteRepository(projectKey, (projectKey + "-" + repoName).toLowerCase(), false);
        }
        bitbucketRequestMockProvider.mockDeleteProject(projectKey, false);
        request.delete("/api/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId(), HttpStatus.OK);
        assertThat(examRepository.findById(exam2.getId())).as("Exam was deleted").isEmpty();

        deleteExam1WithInstructor();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteTestRun() throws Exception {
        var instructor = database.getUserByLogin("instructor1");
        var exam = database.addExam(course1);
        exam = database.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var testRun = database.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        request.delete("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-run/" + testRun.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteTestRunWithReferencedParticipationsDeleteOneParticipation() throws Exception {
        var instructor = database.getUserByLogin("instructor1");
        var exam = database.addExam(course1);
        exam = database.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var testRun1 = database.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        var testRun2 = new StudentExam();
        testRun2.setTestRun(true);
        testRun2.setExam(testRun1.getExam());
        testRun2.setUser(instructor);
        testRun2.setExercises(List.of(testRun1.getExercises().get(0)));
        testRun2.setWorkingTime(testRun1.getWorkingTime());
        studentExamRepository.save(testRun2);
        request.delete("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-run/" + testRun1.getId(), HttpStatus.OK);
        var testRunList = studentExamRepository.findAllTestRunsWithExercisesParticipationsSubmissionsResultsByExamId(exam.getId());
        assertThat(testRunList.size()).isEqualTo(1);
        testRunList.get(0).getExercises().forEach(exercise -> assertThat(exercise.getStudentParticipations()).isNotEmpty());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteTestRunWithReferencedParticipationsDeleteNoParticipation() throws Exception {
        var instructor = database.getUserByLogin("instructor1");
        var exam = database.addExam(course1);
        exam = database.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var testRun1 = database.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        var testRun2 = new StudentExam();
        testRun2.setTestRun(true);
        testRun2.setExam(testRun1.getExam());
        testRun2.setUser(instructor);
        testRun2.setExercises(List.of(testRun1.getExercises().get(0)));
        testRun2.setWorkingTime(testRun1.getWorkingTime());
        studentExamRepository.save(testRun2);
        request.delete("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-run/" + testRun2.getId(), HttpStatus.OK);
        var testRunList = studentExamRepository.findAllTestRunsWithExercisesParticipationsSubmissionsResultsByExamId(exam.getId());
        assertThat(testRunList.size()).isEqualTo(1);
        testRunList.get(0).getExercises().forEach(exercise -> assertThat(exercise.getStudentParticipations()).isNotEmpty());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteTestRunWithMissingParticipation() throws Exception {
        var instructor = database.getUserByLogin("instructor1");
        var exam = database.addExam(course1);
        exam = database.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var testRun = database.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        var participations = studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerLegalSubmissions(testRun.getExercises().get(0).getId(), instructor.getId());
        assertThat(participations).isNotEmpty();
        participationService.delete(participations.get(0).getId(), false, false);
        request.delete("/api/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-run/" + testRun.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testDeleteTestRunAsTutor() throws Exception {
        var instructor = database.getUserByLogin("instructor1");
        var testRun = database.setupTestRunForExamWithExerciseGroupsForInstructor(exam1, instructor, exam1.getExerciseGroups());
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/test-run/" + testRun.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateTestRun() throws Exception {
        createTestRun();
    }

    /**
     * the server invokes SecurityUtils.setAuthorizationObject() so after invoking this method you need to "login" the user again
     * @return the created test run
     * @throws Exception if errors occur
     */
    private StudentExam createTestRun() throws Exception {
        var instructor = database.getUserByLogin("instructor1");
        StudentExam testRun = new StudentExam();
        testRun.setExercises(new ArrayList<>());
        testRunExam = database.addExam(course1);
        testRunExam = database.addTextModelingProgrammingExercisesToExam(testRunExam, false, true);
        testRunExam.getExerciseGroups().forEach(exerciseGroup -> testRun.getExercises().add(exerciseGroup.getExercises().iterator().next()));
        testRun.setExam(testRunExam);
        testRun.setWorkingTime(6000);
        testRun.setUser(instructor);

        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/test-run", testRun, StudentExam.class, HttpStatus.OK);
        var testRunsInDb = studentExamRepository.findAllByExamId_AndTestRunIsTrue(testRunExam.getId());
        assertThat(testRunsInDb.size()).isEqualTo(1);
        var testRunInDb = testRunsInDb.get(0);
        assertThat(testRunInDb.isTestRun()).isEqualTo(true);
        assertThat(testRunInDb.getWorkingTime()).isEqualTo(6000);
        assertThat(testRunInDb.getUser()).isEqualTo(instructor);
        return testRunInDb;
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSubmitTestRun() throws Exception {
        var testRun = createTestRun();
        database.changeUser("instructor1");
        var testRunResponse = request.get("/api/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/test-run/" + testRun.getId() + "/conduction", HttpStatus.OK,
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
        testRunResponse = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/student-exams/submit", testRunResponse,
                StudentExam.class, HttpStatus.OK, null);
        checkQuizSubmission(quizExercise.getId(), quizSubmission.getId());

        // reconnect references so that the following method works
        testRunResponse.getExercises().forEach(exercise -> exercise.getStudentParticipations().forEach(studentParticipation -> studentParticipation.setExercise(exercise)));
        // invoke a second time to test the else case in this method
        SecurityUtils.setAuthorizationObject();
        examQuizService.evaluateQuizParticipationsForTestRun(testRunResponse);
        // make sure that no second result is created
        checkQuizSubmission(quizExercise.getId(), quizSubmission.getId());
    }

    private void checkQuizSubmission(long quizExerciseId, long quizSubmissionId) {

        assertThat(quizSubmissionRepository.count()).isEqualTo(1);

        List<Result> results = resultRepository.findByParticipationExerciseIdOrderByCompletionDateAsc(quizExerciseId);
        assertThat(results.size()).isEqualTo(1);
        var result = results.get(0);
        assertThat(result.getSubmission().getId()).isEqualTo(quizSubmissionId);

        assertThat(result.getResultString()).isEqualTo("4 of 9 points");
        var resultQuizSubmission = (QuizSubmission) result.getSubmission();
        assertThat(resultQuizSubmission.getScoreInPoints()).isEqualTo(4D);
        var submittedAnswers = resultQuizSubmission.getSubmittedAnswers();
        for (SubmittedAnswer submittedAnswer : submittedAnswers) {
            // MC submitted answers 0 points as one correct and one false -> ALL_OR_NOTHING
            if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isEqualTo(4D);
            } // DND submitted answers 0 points as one correct and two false -> PROPORTIONAL_WITH_PENALTY
            else if (submittedAnswer instanceof DragAndDropSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isEqualTo(0D);
            } // SA submitted answers 0 points as one correct and one false -> PROPORTIONAL_WITHOUT_PENALTY
            else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isEqualTo(0D);
            }
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testSubmitAndUnSubmitStudentExamAfterExamIsOver() throws Exception {
        final var studentExams = prepareStudentExamsForConduction(false);
        var studentExam = studentExams.get(0);

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(10));
        studentExam.setStarted(true);
        studentExam.setStartedDate(ZonedDateTime.now().minusMinutes(8));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(5));
        exam2 = examRepository.save(exam2);
        studentExam = studentExamRepository.save(studentExam);
        assertThat(studentExam.isSubmitted()).isFalse();
        assertThat(studentExam.getSubmissionDate()).isNull();

        // submitting the exam, although the endDate is over
        database.changeUser("student1");
        request.put("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-submitted", null, HttpStatus.FORBIDDEN);
        database.changeUser("tutor1");
        request.put("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-submitted", null, HttpStatus.FORBIDDEN);
        database.changeUser("instructor1");
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-submitted", null, HttpStatus.CONFLICT);
        request.put("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-submitted", null, HttpStatus.OK);
        studentExam = studentExamRepository.findById(studentExam.getId()).orElseThrow();
        assertThat(studentExam.isSubmitted()).isTrue();
        assertThat(studentExam.getSubmissionDate()).isNotNull();

        // setting the exam to unsubmitted again
        database.changeUser("student1");
        request.put("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-unsubmitted", null, HttpStatus.FORBIDDEN);
        database.changeUser("tutor1");
        request.put("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-unsubmitted", null, HttpStatus.FORBIDDEN);
        database.changeUser("instructor1");
        request.put("/api/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-unsubmitted", null, HttpStatus.CONFLICT);
        request.put("/api/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-unsubmitted", null, HttpStatus.OK);
        studentExam = studentExamRepository.findById(studentExam.getId()).orElseThrow();
        assertThat(studentExam.isSubmitted()).isFalse();
        assertThat(studentExam.getSubmissionDate()).isNull();
    }
}
