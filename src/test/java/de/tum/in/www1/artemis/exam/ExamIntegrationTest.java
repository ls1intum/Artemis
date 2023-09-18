package de.tum.in.www1.artemis.exam;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.CREATED;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.exam.*;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
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
import de.tum.in.www1.artemis.web.rest.dto.*;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class ExamIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "examintegration";

    @Autowired
    private QuizExerciseRepository quizExerciseRepository;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExamService examService;

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

    private Course course1;

    private Course course2;

    private Course course10;

    private Exam exam1;

    private Exam exam2;

    private static final int NUMBER_OF_STUDENTS = 2;

    private static final int NUMBER_OF_TUTORS = 1;

    private User student1;

    private User instructor;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, 0, 1);
        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42", passwordService.hashPassword(UserFactory.USER_PASSWORD));
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor6", passwordService.hashPassword(UserFactory.USER_PASSWORD));
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor10", passwordService.hashPassword(UserFactory.USER_PASSWORD));

        course1 = courseUtilService.addEmptyCourse();
        course2 = courseUtilService.addEmptyCourse();

        course10 = courseUtilService.createCourse();
        course10.setInstructorGroupName("instructor10-test-group");
        course10 = courseRepo.save(course10);

        User instructor10 = userUtilService.getUserByLogin(TEST_PREFIX + "instructor10");
        instructor10.setGroups(Set.of(course10.getInstructorGroupName()));
        userRepo.save(instructor10);

        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        exam1 = examUtilService.addExam(course1);
        examUtilService.addExamChannel(exam1, "exam1 channel");
        exam2 = examUtilService.addExamWithExerciseGroup(course1, true);
        examUtilService.addExamChannel(exam2, "exam2 channel");

        bitbucketRequestMockProvider.enableMockingOfRequests();

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 200;
        participantScoreScheduleService.activate();
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

        // invoke generate student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(exam.getExamUsers().size());
        for (StudentExam studentExam : studentExams) {
            assertThat(studentExam.getWorkingTime()).as("Working time is set correctly").isEqualTo(120 * 60);
        }

        for (var studentExam : studentExams) {
            assertThat(studentExam.getExercises()).hasSize(exam.getNumberOfExercisesInExam());
            assertThat(studentExam.getExam()).isEqualTo(exam);
            // TODO: check exercise configuration, each mandatory exercise group has to appear, one optional exercise should appear
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateMissingStudentExams() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);
        // Generate student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(studentExams).hasSize(exam.getExamUsers().size());

        // Register one new students
        examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, 2, 2);

        // Generate individual exams for the two missing students
        List<StudentExam> missingStudentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-missing-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);
        assertThat(missingStudentExams).hasSize(1);

        // Fetch student exams
        List<StudentExam> studentExamsDB = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamsDB).hasSize(exam.getExamUsers().size());

        // Another request should not create any exams
        missingStudentExams = request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-missing-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.OK);
        assertThat(missingStudentExams).isEmpty();

        studentExamsDB = request.getList("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamsDB).hasSize(exam.getExamUsers().size());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testSaveExamWithExerciseGroupWithExerciseToDatabase() {
        textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
        ExamFactory.generateExam(course1);
        request.getList("/api/courses/" + course1.getId() + "/exams", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.FORBIDDEN);
        request.put("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.FORBIDDEN);
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.FORBIDDEN, Exam.class);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.FORBIDDEN);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/reset", HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + TEST_PREFIX + "student1", null, HttpStatus.FORBIDDEN);
        request.post("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students", Collections.singletonList(new StudentDTO(null, null, null, null, null)),
                HttpStatus.FORBIDDEN);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor10", roles = "INSTRUCTOR")
    void testCreateExam_checkCourseAccess_InstructorNotInCourse_forbidden() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_asInstructor() throws Exception {
        // Test for bad request when exam id is already set.
        Exam examA = ExamFactory.generateExam(course1, "examA");
        examA.setId(55L);
        request.post("/api/courses/" + course1.getId() + "/exams", examA, HttpStatus.BAD_REQUEST);
        // Test for bad request when course is null.
        Exam examB = ExamFactory.generateExam(course1, "examB");
        examB.setCourse(null);
        request.post("/api/courses/" + course1.getId() + "/exams", examB, HttpStatus.BAD_REQUEST);
        // Test for bad request when course deviates from course specified in route.
        Exam examC = ExamFactory.generateExam(course1, "examC");
        request.post("/api/courses/" + course2.getId() + "/exams", examC, HttpStatus.BAD_REQUEST);
        // Test invalid dates
        List<Exam> examsWithInvalidDate = createExamsWithInvalidDates(course1);
        for (var exam : examsWithInvalidDate) {
            request.post("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
        }
        // Test for conflict when user tries to create an exam with exercise groups.
        Exam examD = ExamFactory.generateExam(course1, "examD");
        examD.addExerciseGroup(ExamFactory.generateExerciseGroup(true, exam1));
        request.post("/api/courses/" + course1.getId() + "/exams", examD, HttpStatus.CONFLICT);

        courseUtilService.enableMessagingForCourse(course1);
        // Test examAccessService.
        Exam examE = ExamFactory.generateExam(course1, "examE");
        examE.setTitle("          Exam 123              ");
        URI examUri = request.post("/api/courses/" + course1.getId() + "/exams", examE, HttpStatus.CREATED);
        Exam savedExam = request.get(String.valueOf(examUri), HttpStatus.OK, Exam.class);
        assertThat(savedExam.getTitle()).isEqualTo("Exam 123");
        verify(examAccessService).checkCourseAccessForInstructorElseThrow(course1.getId());

        Channel channelFromDB = channelRepository.findChannelByExamId(savedExam.getId());
        assertThat(channelFromDB).isNotNull();
    }

    private List<Exam> createExamsWithInvalidDates(Course course) {
        // Test for bad request, visible date not set
        Exam examA = ExamFactory.generateExam(course);
        examA.setVisibleDate(null);
        // Test for bad request, start date not set
        Exam examB = ExamFactory.generateExam(course);
        examB.setStartDate(null);
        // Test for bad request, end date not set
        Exam examC = ExamFactory.generateExam(course);
        examC.setEndDate(null);
        // Test for bad request, start date not after visible date
        Exam examD = ExamFactory.generateExam(course);
        examD.setStartDate(examD.getVisibleDate());
        // Test for bad request, end date not after start date
        Exam examE = ExamFactory.generateExam(course);
        examE.setEndDate(examE.getStartDate());
        // Test for bad request, when visibleDate equals the startDate
        Exam examF = ExamFactory.generateExam(course);
        examF.setVisibleDate(examF.getStartDate());
        // Test for bad request, when exampleSolutionPublicationDate is before the visibleDate
        Exam examG = ExamFactory.generateExam(course);
        examG.setExampleSolutionPublicationDate(examG.getVisibleDate().minusHours(1));
        return List.of(examA, examB, examC, examD, examE, examF, examG);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_asInstructor() throws Exception {
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
        // No course is set -> bad request
        exam = ExamFactory.generateExam(course1);
        exam.setId(1L);
        exam.setCourse(null);
        request.put("/api/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
        // Course id in the updated exam and in the REST resource url do not match -> bad request
        exam = ExamFactory.generateExam(course1);
        exam.setId(1L);
        request.put("/api/courses/" + course2.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
        // Dates in the updated exam are not valid -> bad request
        List<Exam> examsWithInvalidDate = createExamsWithInvalidDates(course1);
        for (var examWithInvDate : examsWithInvalidDate) {
            examWithInvDate.setId(1L);
            request.put("/api/courses/" + course1.getId() + "/exams", examWithInvDate, HttpStatus.BAD_REQUEST);
        }
        // Update the exam -> ok
        exam1.setTitle("Best exam ever");
        var returnedExam = request.putWithResponseBody("/api/courses/" + course1.getId() + "/exams", exam1, Exam.class, HttpStatus.OK);
        assertThat(returnedExam).isEqualTo(exam1);
        verify(instanceMessageSendService, never()).sendProgrammingExerciseSchedule(any());
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
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExamWithExerciseGroup(course, true);
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().get(0);

        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setTestRun(true);

        QuizExercise quizExercise = QuizExerciseFactory.createQuizForExam(exerciseGroup);
        quizExercise.setStudentParticipations(Set.of(studentParticipation));
        studentParticipation.setExercise(quizExercise);

        exerciseRepo.save(quizExercise);
        studentParticipationRepository.save(studentParticipation);

        Exam returnedExam = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "?withExerciseGroups=true", HttpStatus.OK, Exam.class);

        assertThat(returnedExam.getExerciseGroups()).anyMatch(groups -> groups.getExercises().stream().anyMatch(Exercise::getTestRunParticipationsExist));
        verify(examAccessService).checkCourseAndExamAccessForEditorElseThrow(course.getId(), exam.getId());
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
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
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
        Course course = courseUtilService.createCourse();
        Exam exam = examUtilService.addExam(course);
        Channel examChannel = examUtilService.addExamChannel(exam, "test");

        request.delete("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);

        Optional<Channel> examChannelAfterDelete = channelRepository.findById(examChannel.getId());
        assertThat(examChannelAfterDelete).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithExerciseGroupAndTextExercise_asInstructor() throws Exception {
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exam2.getExerciseGroups().get(0));
        exerciseRepo.save(textExercise);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam2.getId(), HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam2.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
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
        exerciseRepo.save(textExercise);
        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/reset", HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam2.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testResetExamThatDoesNotExist() throws Exception {
        request.delete("/api/courses/" + course2.getId() + "/exams/654555/reset", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetExamWithQuizExercise_asInstructor() throws Exception {
        QuizExercise quizExercise = QuizExerciseFactory.createQuizForExam(exam2.getExerciseGroups().get(0));
        quizExerciseRepository.save(quizExercise);

        request.delete("/api/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/reset", HttpStatus.OK);
        quizExercise = (QuizExercise) exerciseRepo.findByIdElseThrow(quizExercise.getId());
        assertThat(quizExercise.getReleaseDate()).isNull();
        assertThat(quizExercise.getDueDate()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamWithOptions() throws Exception {
        Course course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(student1, now().minusHours(3), now().minusHours(2), now().minusHours(1));
        var exam = examRepository.findWithExerciseGroupsAndExercisesById(course.getExams().iterator().next().getId()).orElseThrow();
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
        assertThat(exam3.getExerciseGroups().get(0).getExercises()).hasSize(exam.getExerciseGroups().get(0).getExercises().size());
        assertThat(exam3.getExerciseGroups().get(1).getExercises()).hasSize(exam.getExerciseGroups().get(1).getExercises().size());
        assertThat(exam3.getNumberOfExamUsers()).isNotNull().isEqualTo(1);

        // 4. without students, with exercise groups
        params = new LinkedMultiValueMap<>();
        params.add("withExerciseGroups", "true");
        var exam4 = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, Exam.class, params);
        assertThat(exam4.getExamUsers()).isEmpty();
        assertThat(exam4.getExerciseGroups()).hasSize(exam.getExerciseGroups().size());
        assertThat(exam4.getExerciseGroups().get(0).getExercises()).hasSize(exam.getExerciseGroups().get(0).getExercises().size());
        assertThat(exam4.getExerciseGroups().get(1).getExercises()).hasSize(exam.getExerciseGroups().get(1).getExercises().size());
        exam4.getExerciseGroups().get(1).getExercises().forEach(exercise -> {
            assertThat(exercise.getNumberOfParticipations()).isNotNull();
            assertThat(exercise.getNumberOfParticipations()).isZero();
        });
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
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bambooRequestMockProvider.enableMockingOfRequests(true);

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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteCourseWithMultipleTestRuns() throws Exception {
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        assertThat(studentExamRepository.findAllTestRunsByExamId(exam.getId())).hasSize(3);
        request.delete("/api/courses/" + exam.getCourse().getId(), HttpStatus.OK);
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
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        exam.setVisibleDate(ZonedDateTime.now().minusHours(1).minusMinutes(5));
        StudentExam response = request.get("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/start", HttpStatus.OK, StudentExam.class);
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
        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/unlock-all-repositories", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
        // Lock all repositories
        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/lock-all-repositories", Optional.empty(), Integer.class,
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
        Course course = courseUtilService.createCourseWithExamAndExercises(TEST_PREFIX);
        course.setEndDate(now().minusMinutes(5));
        course = courseRepo.save(course);

        request.put("/api/courses/" + course.getId() + "/archive", null, HttpStatus.OK);

        final var courseId = course.getId();
        await().until(() -> courseRepo.findById(courseId).orElseThrow().getCourseArchivePath() != null);

        var updatedCourse = courseRepo.findById(courseId).orElseThrow();
        assertThat(updatedCourse.getCourseArchivePath()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveExamAsInstructor() throws Exception {
        archiveExamAsInstructor();
    }

    private Course archiveExamAsInstructor() throws Exception {
        var course = courseUtilService.createCourseWithExamAndExercises(TEST_PREFIX);
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
        Course course = courseUtilService.addEmptyCourse();
        course.setEndDate(now().minusMinutes(5));
        course = courseRepo.save(course);

        Exam exam = examUtilService.addExam(course);
        exam = examRepository.save(exam);

        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/archive", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveExamBeforeEndDate_badRequest() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        course.setEndDate(now().plusMinutes(5));
        course = courseRepo.save(course);

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
        // Create an exam with no archive
        Course course = courseUtilService.createCourse();
        course = courseRepo.save(course);

        // Return not found if the exam doesn't exist
        var downloadedArchive = request.get("/api/courses/" + course.getId() + "/exams/-1/download-archive", HttpStatus.NOT_FOUND, String.class);
        assertThat(downloadedArchive).isNull();

        // Returns not found if there is no archive
        var exam = examUtilService.addExam(course);
        downloadedArchive = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/download-archive", HttpStatus.NOT_FOUND, String.class);
        assertThat(downloadedArchive).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDownloadExamArchiveAsInstructorNotInCourse_forbidden() throws Exception {
        // Create an exam with no archive
        Course course = courseUtilService.createCourse();
        course.setInstructorGroupName("some-group");
        course = courseRepo.save(course);
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
        zipFileTestUtilService.extractZipFileRecursively(archive.getAbsolutePath());
        String extractedArchiveDir = archive.getPath().substring(0, archive.getPath().length() - 4);

        // Check that the dummy files we created exist in the archive.
        List<Path> filenames;
        try (var files = Files.walk(Path.of(extractedArchiveDir))) {
            filenames = files.filter(Files::isRegularFile).map(Path::getFileName).toList();
        }

        var submissions = submissionRepository.findByParticipation_Exercise_ExerciseGroup_Exam_Id(exam.getId());

        var savedSubmission = submissions.stream().filter(submission -> submission instanceof FileUploadSubmission).findFirst().orElseThrow();
        assertSubmissionFilename(filenames, savedSubmission, ".png");

        savedSubmission = submissions.stream().filter(submission -> submission instanceof TextSubmission).findFirst().orElseThrow();
        assertSubmissionFilename(filenames, savedSubmission, ".txt");

        savedSubmission = submissions.stream().filter(submission -> submission instanceof ModelingSubmission).findFirst().orElseThrow();
        assertSubmissionFilename(filenames, savedSubmission, ".json");
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
        Course course = courseUtilService.createCourse();
        Exam exam = ExamFactory.generateExam(course);
        exam.setTitle("Test Exam");
        exam = examRepository.save(exam);
        course.addExam(exam);
        courseRepo.save(course);

        final var title = request.get("/api/exams/" + exam.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(exam.getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetExamTitleForNonExistingExam() throws Exception {
        request.get("/api/exams/123124123123/title", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamForImportWithExercises_successful() throws Exception {
        Exam received = request.get("/api/exams/" + exam2.getId(), HttpStatus.OK, Exam.class);
        assertThat(received).isEqualTo(exam2);
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllExamsOnPage_WithoutExercises_instructor_successful() throws Exception {
        var title = "My fancy search title for the exam which is not used somewhere else";
        var exam = ExamFactory.generateExam(course1);
        exam.setTitle(title);
        examRepository.save(exam);
        final PageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(title);
        final var result = request.getSearchResult("/api/exams", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1).containsExactly(exam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllExamsOnPage_WithExercises_instructor_successful() throws Exception {
        var newExam = examUtilService.addTestExamWithExerciseGroup(course1, true);
        var searchTerm = "A very distinct title that should only ever exist once in the database";
        newExam.setTitle(searchTerm);
        examRepository.save(newExam);
        final PageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(searchTerm);
        final var result = request.getSearchResult("/api/exams?withExercises=true", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        List<Exam> foundExams = result.getResultsOnPage();
        assertThat(foundExams).hasSize(1).containsExactly(newExam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllExamsOnPage_WithoutExercisesAndExamsNotLinkedToCourse_instructor_successful() throws Exception {
        var title = "Another fancy exam search title for the exam which is not used somewhere else";
        Course course3 = courseUtilService.addEmptyCourse();
        course3.setInstructorGroupName("non-instructors");
        courseRepo.save(course3);
        var exam = examUtilService.addExamWithExerciseGroup(course3, true);
        exam.setTitle(title);
        examRepository.save(exam);
        final PageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(title);
        final var result = request.getSearchResult("/api/exams", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAllExamsOnPage_WithoutExercisesAndExamsNotLinkedToCourse_admin_successful() throws Exception {
        var title = "Yet another 3rd exam search title for the exam which is not used somewhere else";
        Course course3 = courseUtilService.addEmptyCourse();
        course3.setInstructorGroupName("non-instructors");
        courseRepo.save(course3);
        var exam = examUtilService.addExamWithExerciseGroup(course3, true);
        exam.setTitle(title);
        examRepository.save(exam);
        final PageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(title);
        final var result = request.getSearchResult("/api/exams", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1).contains(exam);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void testGetAllExamsOnPage_tutor() throws Exception {
        final PageableSearchDTO<String> search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/exams", HttpStatus.FORBIDDEN, Exam.class, pageableSearchUtilService.searchMapping(search));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllExamsOnPage_student() throws Exception {
        final PageableSearchDTO<String> search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/exams", HttpStatus.FORBIDDEN, Exam.class, pageableSearchUtilService.searchMapping(search));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testImportExamWithExercises_student() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", exam1, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void testImportExamWithExercises_tutor() throws Exception {
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", exam1, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_idExists() throws Exception {
        final Exam exam = ExamFactory.generateExam(course1);
        exam.setId(2L);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", exam, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_courseMismatch() throws Exception {
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
    void testImportExamWithExercises_dateConflict() throws Exception {
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
    void testImportExamWithExercises_dateConflictTestExam() throws Exception {
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
    void testImportExamWithExercises_pointConflict() throws Exception {
        final Exam examA = ExamFactory.generateExam(course1);
        examA.setExamMaxPoints(-5);
        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", examA, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_correctionRoundConflict() throws Exception {
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExercisesWithPotentialPlagiarismAsTutor_forbidden() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercises-with-potential-plagiarism", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetSuspiciousSessionsAsTutor_forbidden() throws Exception {
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.FORBIDDEN, Set.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExercisesWithPotentialPlagiarismAsInstructorNotInCourse_forbidden() throws Exception {
        courseUtilService.updateCourseGroups("abc", course1, "");
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercises-with-potential-plagiarism", HttpStatus.FORBIDDEN, List.class);
        courseUtilService.updateCourseGroups(TEST_PREFIX, course1, "");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetSuspiciousSessionsAsInstructorNotInCourse_forbidden() throws Exception {
        courseUtilService.updateCourseGroups("abc", course1, "");
        request.get("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.FORBIDDEN, Set.class);
        courseUtilService.updateCourseGroups(TEST_PREFIX, course1, "");
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetSuspiciousSessionsAsInstructor() throws Exception {
        final String userAgent1 = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1.2 Safari/605.1.15";
        final String ipAddress1 = "192.0.2.235";
        final String browserFingerprint1 = "5b2cc274f6eaf3a71647e1f85358ce32";
        final String sessionToken1 = "abc";
        final String userAgent2 = "Mozilla/5.0 (Linux; Android 10; SM-G960F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.141 Mobile Safari/537.36";
        final String ipAddress2 = "172.168.0.0";
        final String browserFingerprint2 = "5b2cc274f6eaf3a71647e1f85358ce31";
        final String sessionToken2 = "def";
        Exam exam = examUtilService.addExam(course1);
        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        StudentExam studentExam2 = examUtilService.addStudentExamWithUser(exam, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        ExamSession firstExamSessionStudent1 = examUtilService.addExamSessionToStudentExam(studentExam, sessionToken1, ipAddress1, browserFingerprint1, "instanceId", userAgent1);
        examUtilService.addExamSessionToStudentExam(studentExam2, sessionToken2, ipAddress2, browserFingerprint2, "instance2Id", userAgent2);
        ExamSession secondExamSessionStudent1 = examUtilService.addExamSessionToStudentExam(studentExam2, sessionToken1, ipAddress1, browserFingerprint1, "instanceId", userAgent1);
        Set<SuspiciousSessionReason> suspiciousReasons = Set.of(SuspiciousSessionReason.SAME_BROWSER_FINGERPRINT, SuspiciousSessionReason.SAME_IP_ADDRESS);
        firstExamSessionStudent1.setSuspiciousReasons(suspiciousReasons);
        secondExamSessionStudent1.setSuspiciousReasons(suspiciousReasons);
        Set<SuspiciousExamSessionsDTO> suspiciousSessionTuples = request.getSet("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/suspicious-sessions",
                HttpStatus.OK, SuspiciousExamSessionsDTO.class);
        assertThat(suspiciousSessionTuples).hasSize(1);
        var suspiciousSessions = suspiciousSessionTuples.stream().findFirst().get();
        assertThat(suspiciousSessions.examSessions()).hasSize(2);
        assertThat(suspiciousSessions.examSessions()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("createdDate")
                .containsExactlyInAnyOrderElementsOf(ExamFactory.createExpectedExamSessionDTOs(firstExamSessionStudent1, secondExamSessionStudent1));
    }
}
