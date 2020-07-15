package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class StudentExamIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ExamRepository examRepository;

    @Autowired
    StudentExamRepository studentExamRepository;

    @Autowired
    ExamSessionRepository examSessionRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    private List<User> users;

    private Course course1;

    private Exam exam1;

    private StudentExam studentExam1;

    @BeforeEach
    public void initTestCase() {
        users = database.addUsers(10, 1, 1);
        users.remove(database.getUserByLogin("admin")); // the admin is not registered for the course and therefore cannot access the student exam so we need to remove it
        course1 = database.addEmptyCourse();
        exam1 = database.addActiveExamWithRegisteredUser(course1, users.get(0));
        Exam exam2 = database.addExam(course1);
        studentExam1 = database.addStudentExam(exam1);
        studentExam1.setWorkingTime(7200);
        studentExam1.setUser(users.get(0));
        studentExamRepository.save(studentExam1);
        database.addStudentExam(exam2);
    }

    @AfterEach
    public void resetDatabase() throws Exception {

        // change back to instructor user
        database.changeUser("instructor1");
        // Clean up to prevent exceptions during reset database
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.OK);

        database.resetDatabase();
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
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/" + studentExam1.getId(), HttpStatus.FORBIDDEN, StudentExam.class);
        request.getList("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams", HttpStatus.FORBIDDEN, StudentExam.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetStudentExam_asInstructor() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/" + studentExam1.getId(), HttpStatus.OK, StudentExam.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetStudentExamsForExam_asInstructor() throws Exception {
        List<StudentExam> studentExams = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExams.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetStudentExamForConduction() throws Exception {
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(5);
        var examEndDate = ZonedDateTime.now().plusMinutes(20);

        Course course = database.addEmptyCourse();
        Exam exam = database.addExam(course, examVisibleDate, examStartDate, examEndDate);
        exam = database.addExerciseGroupsAndExercisesToExam(exam, examStartDate, examEndDate);

        // register user
        exam.setRegisteredUsers(new HashSet<>(users));
        exam.setRandomizeExerciseOrder(false);
        exam = examRepository.save(exam);

        // generate individual student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(exam.getRegisteredUsers().size());

        assertThat(studentExamRepository.findAll()).hasSize(users.size() + 3); // we generate two additional student exams in the @Before method

        // start exercises

        Integer noGeneratedParticipations = request.postWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/start-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);
        assertThat(noGeneratedParticipations).isEqualTo(users.size() * exam.getExerciseGroups().size());

        // TODO: also write a 2nd test where the submission already contains some content

        for (var studentExam : studentExams) {
            var user = studentExam.getUser();
            database.changeUser(user.getLogin());
            final HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "foo");
            headers.set("X-Artemis-Client-Fingerprint", "bar");
            headers.set("X-Forwarded-For", "10.0." + studentExam.getId() + ".1");
            var response = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/studentExams/conduction", HttpStatus.OK, StudentExam.class, headers);
            assertThat(response).isEqualTo(studentExam);
            assertThat(response.isStarted()).isTrue();
            assertThat(response.getExercises().size()).isEqualTo(4);
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
            // TODO: check that other parts of the solution for quiz questions are not available
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
            assertThat(optionalExamSession.get().getIpAddress().toNormalizedString()).isEqualTo("10.0." + studentExam.getId() + ".1");

            // TODO: add other exercises, programming, modeling and file upload
        }

        // change back to instructor user
        database.changeUser("instructor1");
        // Make sure delete also works if so many objects have been created before
        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetWorkingTimesNoStudentExams() throws Exception {
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(5);
        var examEndDate = ZonedDateTime.now().plusMinutes(20);

        Course course = database.addEmptyCourse();
        Exam exam = database.addExam(course, examVisibleDate, examStartDate, examEndDate);
        exam = database.addExerciseGroupsAndExercisesToExam(exam, examStartDate, examEndDate);

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
        exam = database.addExerciseGroupsAndExercisesToExam(exam, examStartDate, examEndDate);

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

        SecurityUtils.setAuthorizationObject(); // TODO why do we get an exception here without that?
        assertThat(studentExamRepository.findMaxWorkingTimeByExamId(exam.getId())).contains(maxWorkingTime);
        assertThat(studentExamRepository.findAllDistinctWorkingTimesByExamId(exam.getId())).containsExactlyInAnyOrderElementsOf(expectedWorkingTimes);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateWorkingTime() throws Exception {
        int newWorkingTime = 180 * 60;
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        exam1 = examRepository.save(exam1);
        StudentExam result = request.patchWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/" + studentExam1.getId() + "/workingTime",
                newWorkingTime, StudentExam.class, HttpStatus.OK);
        assertThat(result.getWorkingTime()).isEqualTo(newWorkingTime);
        assertThat(studentExamRepository.findById(studentExam1.getId()).get().getWorkingTime()).isEqualTo(newWorkingTime);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateWorkingTimeInvalid() throws Exception {
        int newWorkingTime = 0;
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        exam1 = examRepository.save(exam1);
        request.patchWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/" + studentExam1.getId() + "/workingTime", newWorkingTime,
                StudentExam.class, HttpStatus.BAD_REQUEST);
        // working time did not change
        var studentExamDB = studentExamRepository.findById(studentExam1.getId()).get();
        assertThat(studentExamDB.getWorkingTime()).isEqualTo(studentExam1.getWorkingTime());

        newWorkingTime = -10;
        request.patchWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/" + studentExam1.getId() + "/workingTime", newWorkingTime,
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
        request.patchWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/" + studentExam1.getId() + "/workingTime", newWorkingTime,
                StudentExam.class, HttpStatus.BAD_REQUEST);
        // working time did not change
        var studentExamDB = studentExamRepository.findById(studentExam1.getId()).get();
        assertThat(studentExamDB.getWorkingTime()).isEqualTo(studentExam1.getWorkingTime());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSubmitStudentExam_alreadySubmitted() throws Exception {
        studentExam1.setSubmitted(true);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/submit", studentExam1, HttpStatus.CONFLICT);
        studentExamRepository.save(studentExam1);
        studentExam1.setSubmitted(false);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/submit", studentExam1, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSubmitStudentExam_notInTime() throws Exception {
        studentExam1.setSubmitted(false);
        studentExamRepository.save(studentExam1);
        // Forbidden because user tried to submit before start
        exam1.setStartDate(ZonedDateTime.now().plusHours(1));
        examRepository.save(exam1);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/submit", studentExam1, HttpStatus.FORBIDDEN);
        // Forbidden because user tried to submit after end
        exam1.setStartDate(ZonedDateTime.now().minusHours(5));
        examRepository.save(exam1);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/submit", studentExam1, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSubmitStudentExam() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/studentExams/submit", studentExam1, HttpStatus.OK, null);
        StudentExam submittedStudentExam = studentExamRepository.findById(studentExam1.getId()).get();
        // Ensure that student exam has been marked as submitted
        assertThat(submittedStudentExam.isSubmitted()).isTrue();
        // Ensure that student exam has been set
        assertThat(submittedStudentExam.getSubmissionDate()).isNotNull();
    }
}
