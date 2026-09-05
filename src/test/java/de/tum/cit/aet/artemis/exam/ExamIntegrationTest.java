package de.tum.cit.aet.artemis.exam;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpStatus.CREATED;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.user.PasswordService;
import de.tum.cit.aet.artemis.account.util.UserFactory;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.service.ExampleSubmissionService;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.assessment.service.TutorParticipationService;
import de.tum.cit.aet.artemis.assessment.test_repository.TutorParticipationTestRepository;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.test_repository.UserCourseRoleTestRepository;
import de.tum.cit.aet.artemis.core.util.PageableSearchUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseWithIdDTO;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.domain.SuspiciousSessionReason;
import de.tum.cit.aet.artemis.exam.domain.event.WorkingTimeUpdateEvent;
import de.tum.cit.aet.artemis.exam.dto.CreateTestRunDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamChecklistDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamForAssessmentDashboardDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamForConductionDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamForQuestionPoolDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamImportDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamImportResultDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamInformationDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamScoresDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamSessionDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamSidebarDataDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamUpdateDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamWithExerciseGroupsDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamWithIdAndCourseDTO;
import de.tum.cit.aet.artemis.exam.dto.ExerciseGroupDTO;
import de.tum.cit.aet.artemis.exam.dto.ExerciseGroupImportResultDTO;
import de.tum.cit.aet.artemis.exam.dto.LockedExamSubmissionDTO;
import de.tum.cit.aet.artemis.exam.dto.StudentExamDTO;
import de.tum.cit.aet.artemis.exam.dto.StudentExamForConductionDTO;
import de.tum.cit.aet.artemis.exam.dto.SuspiciousExamSessionsDTO;
import de.tum.cit.aet.artemis.exam.dto.UpcomingExamDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.service.ExamDateService;
import de.tum.cit.aet.artemis.exam.service.ExamService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamLiveEventTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseForPlagiarismCasesOverviewDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseGroupWithIdAndExamDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.exercise.util.ImportedExerciseAssertions;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.util.ZipFileTestUtilService;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.test_repository.ModelingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExamIntegrationTest extends AbstractSpringIntegrationJenkinsLocalVCBatchTest {

    private static final String TEST_PREFIX = "examint";

    @Autowired
    private QuizExerciseTestRepository quizExerciseRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ModelingExerciseTestRepository modelingExerciseRepository;

    @Autowired
    private FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExamService examService;

    @Autowired
    private ExamLiveEventTestRepository examLiveEventRepository;

    @Autowired
    private ExamDateService examDateService;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private ZipFileTestUtilService zipFileTestUtilService;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private UserCourseRoleTestRepository userCourseRoleTestRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExampleSubmissionService exampleSubmissionService;

    @Autowired
    private TutorParticipationService tutorParticipationService;

    @Autowired
    private TutorParticipationTestRepository tutorParticipationRepository;

    @Autowired
    private SubmissionService submissionService;

    @Autowired(required = false)
    private WeaviateService weaviateService;

    @Autowired(required = false)
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    private Course course1;

    private Course course2;

    private Course course10;

    private Exam exam1;

    private Exam exam2;

    private static final int NUMBER_OF_STUDENTS = 4;

    private static final int NUMBER_OF_TUTORS = 1;

    private User student1;

    private User instructor;

    @BeforeAll
    void setup() {
        // setup users
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, 0, 1);

        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        // reset courses — must happen BEFORE outsider users are created so that
        // enrollPrefixedUsersInCourse (called inside addEmptyCourse) does not pick them up.
        course1 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        course2 = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);

        // Add users that are not in the course (created AFTER enrollment so they stay unenrolled)
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42", passwordService.hashPassword(UserFactory.USER_PASSWORD));
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor6", passwordService.hashPassword(UserFactory.USER_PASSWORD));
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor10", passwordService.hashPassword(UserFactory.USER_PASSWORD));

        // Enroll standard users (student1-4, tutor1, instructor1) in course1 and course2
        for (int i = 1; i <= NUMBER_OF_STUDENTS; i++) {
            var student = userUtilService.getUserByLogin(TEST_PREFIX + "student" + i);
            userUtilService.enrollUserInCourse(student, course1, CourseRole.STUDENT);
            userUtilService.enrollUserInCourse(student, course2, CourseRole.STUDENT);
        }
        var tutor1 = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        userUtilService.enrollUserInCourse(tutor1, course1, CourseRole.TEACHING_ASSISTANT);
        userUtilService.enrollUserInCourse(tutor1, course2, CourseRole.TEACHING_ASSISTANT);
        userUtilService.enrollUserInCourse(instructor, course1, CourseRole.INSTRUCTOR);
        userUtilService.enrollUserInCourse(instructor, course2, CourseRole.INSTRUCTOR);

        course10 = courseUtilService.createCourse();
        User instructor10 = userUtilService.getUserByLogin(TEST_PREFIX + "instructor10");
        userUtilService.enrollUserInCourse(instructor10, course10, CourseRole.INSTRUCTOR);

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 200;
    }

    @BeforeEach
    void initTestCase() {
        // reset exams
        exam1 = examUtilService.addExam(course1);
        examUtilService.addExamChannel(exam1, "exam1 channel");

        exam2 = examUtilService.addExamWithExerciseGroup(course1, true);
        examUtilService.addExamChannel(exam2, "exam2 channel");
    }

    private static final int LARGE_PAGE_SIZE_FOR_TESTS = 200;

    private MultiValueMap<String, String> getPageParams() {
        return getPageParams(0, LARGE_PAGE_SIZE_FOR_TESTS);
    }

    private MultiValueMap<String, String> getPageParams(int page) {
        return getPageParams(page, LARGE_PAGE_SIZE_FOR_TESTS);
    }

    private MultiValueMap<String, String> getPageParams(int page, int size) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("page", "0");
        params.add("size", String.valueOf(LARGE_PAGE_SIZE_FOR_TESTS));
        return params;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor10", roles = "INSTRUCTOR")
    void testGetAllActiveExams_Instructor() throws Exception {
        var now = ZonedDateTime.now();
        // add additional active exam
        var exam3 = examUtilService.addExam(course10, now.plusDays(1), now.plusDays(2), now.plusDays(3));
        // add additional exam not active
        var exam4 = examUtilService.addExam(course10, now.minusDays(10), now.plusDays(2), now.plusDays(3));

        List<Exam> activeExams = request.getList("/api/exam/exams/active", HttpStatus.OK, Exam.class, getPageParams());
        // only exam3 should be returned
        assertThat(activeExams).contains(exam3);
        assertThat(activeExams).doesNotContain(exam4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetAllActiveExams_Tutor() throws Exception {
        var now = ZonedDateTime.now();
        // Use course1 where tutor1 is enrolled; tutor1 is NOT enrolled in course10, so using course10
        // would return no results at all with the UCR-based course-enrollment check.
        // exam3: visible now (tutor sees it), exam4: visible tomorrow (not yet visible for TA), exam5: visible 10+ days ago (outside 7-day window)
        var exam3 = examUtilService.addExam(course1, now.minusDays(1), now, now.plusHours(2));
        var exam4 = examUtilService.addExam(course1, now.plusDays(1), now.plusDays(2), now.plusDays(3));

        // add additional exam not active (visibleDate more than 7 days in the past)
        var exam5 = examUtilService.addExam(course1, now.minusDays(10), now.plusDays(2), now.plusDays(3));

        List<Exam> activeExams = request.getList("/api/exam/exams/active", HttpStatus.OK, Exam.class, getPageParams());
        // exam3 should be returned (visible and active), exam4 and exam5 should not
        assertThat(activeExams).contains(exam3);
        assertThat(activeExams).doesNotContain(exam4);
        assertThat(activeExams).doesNotContain(exam5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExams() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 2);

        generateStudentExams(exam);

        verifyStudentsExamAndExercises(exam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateMissingStudentExams() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);

        generateStudentExams(exam);

        registerNewStudentsToExam(exam, 1);
        generateMissingStudentExams(exam, 1);
        verifyStudentsExamAndExercises(exam);

        generateMissingStudentExams(exam, 0);
        verifyStudentsExamAndExercises(exam);
    }

    private void registerNewStudentsToExam(Exam exam, int numberOfStudents) {
        examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, 2, 2 + numberOfStudents - 1);
    }

    private void generateStudentExams(Exam exam) throws Exception {
        JsonNode studentExamsJson = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(),
                JsonNode.class, HttpStatus.OK);
        assertThat(studentExamsJson).hasSize(exam.getExamUsers().size());
        assertSlimStudentExamWireContract(studentExamsJson);
        // the response masks the nested exam, so verify membership via the persisted student exams
        assertThat(studentExamRepository.findByExamId(exam.getId())).hasSize(exam.getExamUsers().size());
    }

    private void generateMissingStudentExams(Exam exam, int expectedMissingStudent) throws Exception {
        JsonNode missingStudentExamsJson = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-missing-student-exams",
                Optional.empty(), JsonNode.class, HttpStatus.OK);
        assertThat(missingStudentExamsJson).hasSize(expectedMissingStudent);
        assertSlimStudentExamWireContract(missingStudentExamsJson);
    }

    /**
     * Pins the raw wire contract generateStudentExams/generateMissingStudentExams promise: each element carries its
     * id and the non-default working-time scalar the client renders, but never leaks the full entity graph the
     * generation service touches (the owning student, the nested exam, or the exercises/sessions/participations
     * collections) -- a JsonNode-level check because entity-based deserialization silently tolerates extra fields.
     *
     * @param studentExamsJson the raw JSON array response from generate-student-exams / generate-missing-student-exams
     */
    private void assertSlimStudentExamWireContract(JsonNode studentExamsJson) {
        assertThat(studentExamsJson.isArray()).as("response is a JSON array").isTrue();
        for (JsonNode studentExamJson : studentExamsJson) {
            assertThat(studentExamJson.has("id")).as("id is present").isTrue();
            assertThat(studentExamJson.path("workingTime").asInt()).as("non-default scalar state (workingTime) is present").isEqualTo(120 * 60);
            assertThat(studentExamJson.has("user")).as("owning user must not be leaked").isFalse();
            assertThat(studentExamJson.has("exam")).as("nested exam must not be leaked").isFalse();
            assertThat(studentExamJson.has("exercises")).as("exercises must not be leaked").isFalse();
            assertThat(studentExamJson.has("examSessions")).as("exam sessions must not be leaked").isFalse();
            assertThat(studentExamJson.has("studentParticipations")).as("participations must not be leaked").isFalse();
        }
    }

    private void verifyStudentsExamAndExercises(Exam exam) throws Exception {
        List<StudentExamDTO> studentExams = request.getList("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams", HttpStatus.OK,
                StudentExamDTO.class);
        assertThat(studentExams).hasSize(exam.getExamUsers().size());
        for (StudentExamDTO studentExam : studentExams) {
            assertThat(studentExam.workingTime()).as("Working time is set correctly").isEqualTo(120 * 60);
        }
        verifyStudentExamsExercises(studentExams.stream().map(StudentExamDTO::id).toList(), exam.getNumberOfExercisesInExam());
    }

    private void verifyStudentExams(List<StudentExam> studentExams, int expectedNumberOfStudentExams) {
        assertThat(studentExams).hasSize(expectedNumberOfStudentExams);
        for (StudentExam studentExam : studentExams) {
            assertThat(studentExam.getWorkingTime()).as("Working time is set correctly").isEqualTo(120 * 60);
        }
    }

    private void verifyStudentExamsExercises(List<Long> ids, int expected) {
        List<StudentExam> studentExamsWithExercises = studentExamRepository.findAllWithEagerExercisesById(ids);
        // Without this completeness check the loop below passes vacuously when the lookup returns nothing, so a
        // response that carried the wrong ids (or none at all) would still be reported as green.
        assertThat(studentExamsWithExercises).as("every requested student exam must be loaded").hasSize(ids.size());
        assertThat(studentExamsWithExercises).extracting(StudentExam::getId).containsExactlyInAnyOrderElementsOf(ids);
        for (var studentExam : studentExamsWithExercises) {
            assertThat(studentExam.getExercises()).hasSize(expected);
        }
        // TODO: check exercise configuration, each mandatory exercise group has to appear, one optional exercise should appear
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsNoExerciseGroups_badRequest() throws Exception {
        Exam exam = examUtilService.addExam(course1, now().minusMinutes(5), now(), now().plusHours(2));

        // invoke generate student exams
        request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsNoExerciseNumber_badRequest() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);
        exam.setNumberOfExercisesInExam(null);
        examRepository.save(exam);

        // invoke generate student exams
        request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsNotEnoughExerciseGroups_badRequest() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);
        exam.setNumberOfExercisesInExam(exam.getNumberOfExercisesInExam() + 2);
        examRepository.save(exam);

        // invoke generate student exams
        request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsTooManyMandatoryExerciseGroups_badRequest() throws Exception {
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 2);
        exam.setNumberOfExercisesInExam(exam.getNumberOfExercisesInExam() - 2);
        examRepository.save(exam);

        // invoke generate student exams
        request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testSaveExamWithExerciseGroupWithExerciseToDatabase() {
        examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);
    }

    private void testAllPreAuthorize(Course course, Exam exam) throws Exception {
        Exam newExam = ExamFactory.generateExam(course1);
        request.post("/api/exam/courses/" + course.getId() + "/exams", ExamUpdateDTO.of(newExam), HttpStatus.FORBIDDEN);
        request.put("/api/exam/courses/" + course.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.FORBIDDEN);
        request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.FORBIDDEN, Exam.class);
        request.delete("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.FORBIDDEN);
        request.delete("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/reset", HttpStatus.FORBIDDEN);
        request.post("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/students", List.of(new StudentDTO(null, null, null, null, null)), HttpStatus.FORBIDDEN);
        request.delete("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent_shouldNotBeAuthorized() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course);

        testAllPreAuthorize(course, exam);
        ExamFactory.generateExam(course1);

        request.getList("/api/exam/courses/" + course1.getId() + "/exams", HttpStatus.FORBIDDEN, Exam.class);
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

        request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_asInstructor_returnsLocationHeader() throws Exception {
        Exam exam = ExamFactory.generateExam(course1, "examE");
        exam.setTitle("          Exam 123              ");

        URI savedExamUri = request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.CREATED);
        Exam savedExam = request.get(String.valueOf(savedExamUri), HttpStatus.OK, Exam.class);

        assertThat(savedExam.getTitle()).isEqualTo("Exam 123");
        verify(examAccessService).checkCourseAccessForInstructorElseThrow(course1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_asInstructor_returnsBody() throws Exception {
        final Exam exam = validExamWithCustomFieldValues();

        final Exam savedExam = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), Exam.class, HttpStatus.CREATED);

        checkCustomFieldValuesExamsAreEffectivelyEqual(savedExam, exam);
        // quizExamMaxPoints is a computed field not included in the DTO, so we don't assert it here
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_asInstructor_createsCourseMessagingChannel() throws Exception {
        Course course = courseUtilService.createCourseWithMessagingEnabled();
        // In the UCR-based authorization model, instructor1 must be explicitly enrolled in the
        // new course as INSTRUCTOR; otherwise the course-access check returns 403.
        userUtilService.enrollUserInCourse(instructor, course, CourseRole.INSTRUCTOR);
        Exam exam = ExamFactory.generateExam(course, "examG");

        Exam savedExam = request.postWithResponseBody("/api/exam/courses/" + course.getId() + "/exams", ExamUpdateDTO.of(exam), Exam.class, HttpStatus.CREATED);

        Channel channelFromDB = channelRepository.findChannelByExamId(savedExam.getId());
        assertThat(channelFromDB).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithId() throws Exception {
        // Test for bad request when exam id is already set.
        Exam examA = ExamFactory.generateExam(course1, "examA");

        examA.setId(55L);

        request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(examA), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_usesCourseFromPath() throws Exception {
        // With the DTO approach, the course is always taken from the path, not from the exam.
        // So creating an exam with a different course in the DTO should work,
        // and the exam should be associated with the course from the path.
        Exam examC = ExamFactory.generateExam(course1, "examC");

        Exam savedExam = request.postWithResponseBody("/api/exam/courses/" + course2.getId() + "/exams", ExamUpdateDTO.of(examC), Exam.class, HttpStatus.CREATED);
        assertThat(savedExam.getCourse().getId()).isEqualTo(course2.getId());
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
        // Test for bad request, when examSummaryPublicationDate is before the endDate
        Exam examH = ExamFactory.generateExam(course1);
        examH.setExamSummaryPublicationDate(examH.getEndDate().minusMinutes(5));
        // Test for bad request, when examSummaryPublicationDate is after the publishResultsDate
        Exam examI = ExamFactory.generateExam(course1);
        examI.setPublishResultsDate(examI.getEndDate().plusMinutes(30));
        examI.setExamSummaryPublicationDate(examI.getEndDate().plusMinutes(60));
        Exam examJ = ExamFactory.generateTestExam(course1);
        examJ.setVisibleDate(examJ.getStartDate());
        return List.of(examA, examB, examC, examD, examE, examF, examG, examH, examI, examJ);
    }

    @ParameterizedTest
    @MethodSource("provideExamsWithInvalidDates")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithInvalidDates(Exam exam) throws Exception {
        request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_withValidExamSummaryPublicationDate() throws Exception {
        Exam exam = ExamFactory.generateExam(course1, "examSummaryDate");
        exam.setPublishResultsDate(exam.getEndDate().plusHours(2));
        // summary publication date after the end date and no later than the publish results date is valid
        exam.setExamSummaryPublicationDate(exam.getEndDate().plusHours(1));

        Exam savedExam = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), Exam.class, HttpStatus.CREATED);

        assertThat(savedExam.getExamSummaryPublicationDate()).isNotNull();
        assertThat(savedExam.getExamSummaryPublicationDate()).isCloseTo(exam.getExamSummaryPublicationDate(), within(1, ChronoUnit.SECONDS));

        // update path (applyTo): changing the date persists
        savedExam.setExamSummaryPublicationDate(savedExam.getEndDate().plusMinutes(90));
        Exam updatedExam = request.putWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(savedExam), Exam.class, HttpStatus.OK);
        assertThat(updatedExam.getExamSummaryPublicationDate()).isCloseTo(savedExam.getExamSummaryPublicationDate(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExamWorkingTime_failsIfExtendedPastSummaryPublicationDate() throws Exception {
        Exam exam = ExamFactory.generateExam(course1, "examSummaryWorkingTime");
        // the submission overview becomes visible shortly after the end date
        exam.setExamSummaryPublicationDate(exam.getEndDate().plusMinutes(30));
        Exam createdExam = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), Exam.class, HttpStatus.CREATED);

        // extending the working time so the new end date would reach/pass the publication date must be rejected (it would let the summary publish while the exam still runs)
        request.patch("/api/exam/courses/" + course1.getId() + "/exams/" + createdExam.getId() + "/working-time", 3600, HttpStatus.BAD_REQUEST);

        // a smaller extension that keeps the end date before the publication date is allowed
        Exam updatedExam = request.patchWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + createdExam.getId() + "/working-time", 600, Exam.class,
                HttpStatus.OK);
        assertThat(updatedExam.getEndDate()).isBefore(updatedExam.getExamSummaryPublicationDate());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithExerciseGroups() throws Exception {
        // Note: With DTO approach, exercise groups are not passed, so this test now expects CREATED
        // since the conflict check happens after the exam is created from DTO
        Exam examD = ExamFactory.generateExam(course1, "examD");

        examD.addExerciseGroup(ExamFactory.generateExerciseGroup(true, exam1));

        // Exercise groups are not included in ExamUpdateDTO, so this won't cause a conflict
        // The exam will be created without exercise groups
        request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(examD), HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithoutCourse() throws Exception {
        Exam examB = ExamFactory.generateExam(course1, "examB");

        examB.setCourse(null);

        // Test for bad request when course is null - now the course comes from the path variable, not the DTO
        // so this test should now succeed since the course is taken from the path
        request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(examB), HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithExamMaxPointsTooHigh() throws Exception {
        Exam exam = ExamFactory.generateExam(course1, "examMaxPointsTest");
        exam.setExamMaxPoints(10000); // Max allowed is 9999

        request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "title=\"{0}\"")
    @NullSource
    @ValueSource(strings = { "", "   " })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithMissingOrBlankTitle(String title) throws Exception {
        // A missing (null), empty or whitespace-only title has to be rejected with a clean 400, not persisted and not failing while mapping the null title to the entity
        ObjectNode examJson = examBodyWithTitle(ExamUpdateDTO.of(ExamFactory.generateExam(course1, "examTitleValidationTest")), title);

        request.postAndExpectError("/api/exam/courses/" + course1.getId() + "/exams", examJson, HttpStatus.BAD_REQUEST, "examTitleEmpty");
    }

    @ParameterizedTest(name = "title=\"{0}\"")
    @NullSource
    @ValueSource(strings = { "", "   " })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_failsWithMissingOrBlankTitle(String title) throws Exception {
        ObjectNode examJson = examBodyWithTitle(ExamUpdateDTO.of(exam1), title);

        request.putAndExpectError("/api/exam/courses/" + course1.getId() + "/exams", examJson, HttpStatus.BAD_REQUEST, "examTitleEmpty");
    }

    @ParameterizedTest(name = "title=\"{0}\"")
    @NullSource
    @ValueSource(strings = { "", "   " })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExam_failsWithMissingOrBlankTitle(String title) throws Exception {
        ObjectNode examJson = examBodyWithTitle(ExamImportDTO.of(exam1, course1.getId()), title);

        request.postAndExpectError("/api/exam/courses/" + course1.getId() + "/exam-import", examJson, HttpStatus.BAD_REQUEST, "examTitleEmpty");
    }

    /**
     * Serialises the given exam DTO and overwrites its title, so a missing (null), empty or whitespace-only title can be sent as a raw request body.
     *
     * @param examDto the exam create/update/import DTO to serialise
     * @param title   the title to set, or null to omit it
     * @return the request body as a JSON object with the adjusted title
     */
    private ObjectNode examBodyWithTitle(Object examDto, String title) {
        ObjectNode examJson = request.getObjectMapper().valueToTree(examDto);
        if (title == null) {
            examJson.putNull("title");
        }
        else {
            examJson.put("title", title);
        }
        return examJson;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithGracePeriodTooHigh() throws Exception {
        Exam exam = ExamFactory.generateExam(course1, "examGracePeriodTest");
        exam.setGracePeriod(3601); // Max allowed is 3600 seconds

        request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithNumberOfExercisesTooHigh() throws Exception {
        Exam exam = ExamFactory.generateExam(course1, "examNumberOfExercisesTest");
        exam.setNumberOfExercisesInExam(101); // Max allowed is 100

        request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithWorkingTimeTooHigh() throws Exception {
        // Test with a test exam where workingTime is directly validated
        Exam exam = ExamFactory.generateExam(course1, "examWorkingTimeTest");
        exam.setTestExam(true);
        exam.setNumberOfCorrectionRoundsInExam(0);
        exam.setWorkingTime(2592001); // Max allowed is 2592000 seconds (30 days)

        request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithTitleTooLong() throws Exception {
        Exam exam = ExamFactory.generateExam(course1, "examTitleTest");
        exam.setTitle("a".repeat(256)); // Max allowed is 255 characters

        request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_succeedsWithTitleAtMaxLength() throws Exception {
        Exam exam = ExamFactory.generateExam(course1, "examTitleTest");
        String maxLengthTitle = "a".repeat(255); // Exactly the maximum allowed
        exam.setTitle(maxLengthTitle);

        Exam savedExam = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), Exam.class, HttpStatus.CREATED);

        assertThat(savedExam.getTitle()).isEqualTo(maxLengthTitle);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithStartTextTooLong() throws Exception {
        Exam exam = ExamFactory.generateExam(course1, "examStartTextTest");
        exam.setStartText("a".repeat(10001)); // Max allowed is 10000 characters

        request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_failsWithConfirmationEndTextTooLong() throws Exception {
        Exam exam = ExamFactory.generateExam(course1, "examConfirmationTextTest");
        exam.setConfirmationEndText("a".repeat(10001)); // Max allowed is 10000 characters

        request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateExam_succeedsWithTextAtMaxLength() throws Exception {
        Exam exam = ExamFactory.generateExam(course1, "examMaxTextTest");
        exam.setStartText("a".repeat(10000)); // Exactly the maximum allowed
        exam.setConfirmationEndText("b".repeat(10000));

        request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_failsWithExamMaxPointsTooHigh() throws Exception {
        exam1.setExamMaxPoints(10000); // Max allowed is 9999

        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam1), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_startDateChangeWithinConductionWindow_sendsScheduleUpdate() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExam(exam1);
        // Put the exam into the pre-start conduction window (start in 2 min) so a student could already be counting down.
        exam1.setVisibleDate(now().minusMinutes(1));
        exam1.setStartDate(now().plusMinutes(2));
        exam1.setEndDate(now().plusMinutes(62));
        exam1 = examRepository.save(exam1);

        // Shift start and end by the same amount: the working time stays the same, so only the schedule changes and the
        // regular working-time update path does not run.
        exam1.setStartDate(now().plusMinutes(3));
        exam1.setEndDate(now().plusMinutes(63));
        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam1), HttpStatus.OK);

        // A working time update carrying the new schedule must have been sent to the student exam (issue #13071).
        var examDb = examRepository.findById(exam1.getId()).orElseThrow();
        assertThat(examLiveEventRepository.findAllByStudentExamId(studentExam.getId())).anySatisfy(event -> {
            assertThat(event).isInstanceOf(WorkingTimeUpdateEvent.class);
            assertThat(((WorkingTimeUpdateEvent) event).getNewStartDate()).isEqualTo(examDb.getStartDate().toInstant());
            assertThat(((WorkingTimeUpdateEvent) event).getNewEndDate()).isEqualTo(examDb.getEndDate().toInstant());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_startDateChangeOutsideConductionWindow_doesNotSendScheduleUpdate() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExam(exam1);
        // Far-future exam: no student can be in the pre-start conduction window yet.
        exam1.setVisibleDate(now().plusDays(1));
        exam1.setStartDate(now().plusDays(1).plusMinutes(30));
        exam1.setEndDate(now().plusDays(1).plusMinutes(90));
        exam1 = examRepository.save(exam1);

        // Shift start and end by the same amount (working time unchanged) so only the schedule-update path could fire.
        exam1.setStartDate(now().plusDays(1).plusMinutes(35));
        exam1.setEndDate(now().plusDays(1).plusMinutes(95));
        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam1), HttpStatus.OK);

        // The exam is far in the future, so no schedule update must be persisted for the student exam (issue #13071).
        assertThat(examLiveEventRepository.findAllByStudentExamId(studentExam.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_failsWithGracePeriodTooHigh() throws Exception {
        exam1.setGracePeriod(3601); // Max allowed is 3600 seconds

        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam1), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_failsWithNumberOfExercisesTooHigh() throws Exception {
        exam1.setNumberOfExercisesInExam(101); // Max allowed is 100

        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam1), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_failsWithWorkingTimeTooHigh() throws Exception {
        // Create a test exam since testExam mode cannot be changed after creation
        Exam testExam = ExamFactory.generateTestExam(course1);
        testExam = examRepository.save(testExam);
        testExam.setWorkingTime(2592001); // Max allowed is 2592000 seconds (30 days)

        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(testExam), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_failsWithTitleTooLong() throws Exception {
        exam1.setTitle("a".repeat(256)); // Max allowed is 255 characters

        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam1), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_failsWithTextTooLong() throws Exception {
        exam1.setEndText("a".repeat(10001)); // Max allowed is 10000 characters

        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam1), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_failsWithoutId() throws Exception {
        // Update requires an ID - use entity without ID to simulate missing ID case
        Exam exam = ExamFactory.generateExam(course1, "exam1");
        exam.setTitle("Over 9000!");
        // Exam without ID should fail
        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_failsWithExamNotFound() throws Exception {
        // Exam with non-existent ID -> not found
        Exam exam = ExamFactory.generateExam(course1);
        exam.setId(999999L);
        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_failsWithExamCourseMismatch() throws Exception {
        // Exam belongs to course1 but URL has course2 -> conflict (course id mismatch)
        request.put("/api/exam/courses/" + course2.getId() + "/exams", ExamUpdateDTO.of(exam1), HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLockedSubmissionsForExam_failsWithExamCourseMismatch() throws Exception {
        // The locked submissions are loaded by exam id alone. Authorizing only the course would let an instructor
        // pair a course they manage with another course's exam and read that exam's submissions.
        request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam1.getId() + "/locked-submissions", HttpStatus.CONFLICT, LockedExamSubmissionDTO.class);
    }

    @ParameterizedTest
    @MethodSource("provideExamsWithInvalidDates")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_failsForInvalidDates(Exam exam) throws Exception {
        // Dates in the updated exam are not valid -> bad request
        Exam persistedExamWithSameMode = exam.isTestExam() ? examRepository.save(ExamFactory.generateTestExam(course1)) : exam1;
        exam.setId(persistedExamWithSameMode.getId());
        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_updatesExamTitle() throws Exception {
        // Update the exam -> ok
        exam1.setTitle("Best exam ever");
        var returnedExam = request.putWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam1), Exam.class, HttpStatus.OK);
        assertThat(returnedExam.getTitle()).isEqualTo(exam1.getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_changeTitleDuringConduction_shouldNotNotifyStudents() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExam(exam1);
        exam1.setTitle("Best exam ever");

        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam1), HttpStatus.OK);

        assertThat(examLiveEventRepository.findAllByStudentExamId(studentExam.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_changeEndDateSubSecondPrecision_shouldNotNotifyStudents() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExam(exam1);
        exam1.setEndDate(exam1.getEndDate().plusNanos(1));

        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam1), HttpStatus.OK);

        assertThat(examLiveEventRepository.findAllByStudentExamId(studentExam.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_changeEndDateDuringConduction_shouldNotifyStudents() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExam(exam1);
        exam1.setEndDate(exam1.getEndDate().plusHours(1));

        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam1), HttpStatus.OK);

        assertThat(examLiveEventRepository.findAllByStudentExamId(studentExam.getId())).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_exampleSolutionPublicationDateChanged() throws Exception {
        var modelingExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneModelingExercise("ClassDiagram", TEST_PREFIX);
        var examWithModelingEx = modelingExercise.getExerciseGroup().getExam();
        assertThat(modelingExercise.isExampleSolutionPublished()).isFalse();

        examUtilService.setVisibleStartAndEndDateOfExam(examWithModelingEx, now().minusHours(5), now().minusHours(4), now().minusHours(3));
        examWithModelingEx.setPublishResultsDate(now().minusHours(2));
        examWithModelingEx.setExampleSolutionPublicationDate(now().minusHours(1));

        request.put("/api/exam/courses/" + examWithModelingEx.getCourse().getId() + "/exams", ExamUpdateDTO.of(examWithModelingEx), HttpStatus.OK);

        Exam fetchedExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examWithModelingEx.getId());
        Exercise exercise = fetchedExam.getExerciseGroups().getFirst().getExercises().stream().findFirst().orElseThrow();
        assertThat(exercise.isExampleSolutionPublished()).isTrue();

        WeaviateTestUtil.assertExerciseExamDatesInWeaviate(weaviateService, modelingExercise.getId(), fetchedExam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_datesChangedReflectedInWeaviate() throws Exception {
        var modelingExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneModelingExercise("ClassDiagram", TEST_PREFIX);
        var exam = modelingExercise.getExerciseGroup().getExam();

        // Insert the exercise into Weaviate with initial dates
        if (searchableEntityWeaviateService != null) {
            searchableEntityWeaviateService.upsertExerciseAsync(ExerciseSearchableEntityDTO.fromExercise(modelingExercise));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> WeaviateTestUtil.assertExerciseExamDatesInWeaviate(weaviateService, modelingExercise.getId(), exam));
        }
        WeaviateTestUtil.assertExerciseExamDatesInWeaviate(weaviateService, modelingExercise.getId(), exam);

        // Update the exam visible, start and end dates
        ZonedDateTime newVisibleDate = now().minusHours(10);
        ZonedDateTime newStartDate = now().minusHours(8);
        ZonedDateTime newEndDate = now().minusHours(6);
        examUtilService.setVisibleStartAndEndDateOfExam(exam, newVisibleDate, newStartDate, newEndDate);

        request.put("/api/exam/courses/" + exam.getCourse().getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.OK);

        Exam fetchedExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
        WeaviateTestUtil.assertExerciseExamDatesInWeaviate(weaviateService, modelingExercise.getId(), fetchedExam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExam_asInstructor() throws Exception {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findByIdElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findByIdWithExamUsersElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> examRepository.findByIdWithExerciseGroupsElseThrow(Long.MAX_VALUE));

        assertThat(examRepository.findAllExercisesWithDetailsByExamId(Long.MAX_VALUE)).isEmpty();

        // The plain (withExerciseGroups=false) response is served as ExamDTO and must not trigger an eager fan-out.
        ExamDTO returnedExam = assertThatDb(() -> request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.OK, ExamDTO.class))
                .hasBeenCalledAtMostTimes(8);
        assertThat(returnedExam.id()).isEqualTo(exam1.getId());
        assertThat(returnedExam.course()).isNotNull();
        assertThat(returnedExam.course().id()).isEqualTo(course1.getId());
        // The embedded course must carry the id the client resolves access rights from (via the user's course roles).

        verify(examAccessService).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExam_asInstructor_WithTestRunQuizExerciseSubmissions() throws Exception {
        Exam exam = examUtilService.addExamWithExerciseGroup(course1, true);
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().getFirst();

        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setTestRun(true);

        QuizExercise quizExercise = QuizExerciseFactory.createQuizForExam(exerciseGroup);
        quizExercise.setStudentParticipations(Set.of(studentParticipation));
        studentParticipation.setExercise(quizExercise);

        exerciseRepository.save(quizExercise);
        studentParticipationRepository.save(studentParticipation);

        ExamWithExerciseGroupsDTO returnedExam = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "?withExerciseGroups=true", HttpStatus.OK,
                ExamWithExerciseGroupsDTO.class);

        assertThat(returnedExam.exerciseGroups()).isNotNull();
        assertThat(returnedExam.exerciseGroups())
                .anyMatch(group -> group.exercises() != null && group.exercises().stream().anyMatch(exercise -> Boolean.TRUE.equals(exercise.testRunParticipationsExist())));
        verify(examAccessService).checkCourseAndExamAccessForEditorElseThrow(course1.getId(), exam.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamWithExerciseGroups_returnsDetailsAndTransients() throws Exception {
        // Full exam with all exercise types; the programming exercise carries template + solution participations (with
        // build-plan ids), the quiz gets questions so the count is non-trivial.
        Exam exam = examWithAllExerciseTypesAndQuizQuestions();
        QuizExercise quizExercise = (QuizExercise) exam.getExerciseGroups().get(3).getExercises().iterator().next();
        int expectedQuestionCount = quizExercise.getQuizQuestions().size();

        ExamWithExerciseGroupsDTO returnedExam = assertThatDb(
                () -> request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "?withExerciseGroups=true", HttpStatus.OK, ExamWithExerciseGroupsDTO.class))
                .hasBeenCalledAtMostTimes(24);

        // Transient set by setExamProperties on the detailed path.
        assertThat(returnedExam.numberOfExamUsers()).isNotNull();
        assertThat(returnedExam.exerciseGroups()).isNotNull();
        var exercises = returnedExam.exerciseGroups().stream().filter(group -> group.exercises() != null).flatMap(group -> group.exercises().stream()).toList();
        // numberOfParticipations (transient) must be present on every exercise for the deletion summary.
        assertThat(exercises).isNotEmpty().allMatch(exercise -> exercise.numberOfParticipations() != null);

        // Quiz: the count-only projection carries exactly the persisted number of questions (read as .length client-side).
        var quizDto = exercises.stream().filter(exercise -> exercise.type() == ExerciseType.QUIZ).findFirst().orElseThrow();
        assertThat(quizDto.quizQuestions()).hasSize(expectedQuestionCount);
        // difficulty is mapped for the create-test-run modal cell (create-test-run-modal.component.html renders exercise.difficulty).
        assertThat(quizDto.difficulty()).isEqualTo(DifficultyLevel.HARD);
        // Each quiz-question stub carries the polymorphic discriminator (QuizQuestion's @JsonSubTypes names) so a client echo of this
        // graph deserializes back into the concrete subtype instead of throwing a 400 on the write paths (test-run / exercise-groups-order).
        assertThat(quizDto.quizQuestions()).allSatisfy(question -> assertThat(question.type()).isNotBlank());
        assertThat(quizDto.quizQuestions()).extracting(ExamWithExerciseGroupsDTO.ExamQuizQuestionDTO::type).containsExactlyInAnyOrder("multiple-choice", "drag-and-drop",
                "short-answer");

        // Programming: template and solution build-plan ids are carried for the exercise-group programming cell.
        var programmingDto = exercises.stream().filter(exercise -> exercise.type() == ExerciseType.PROGRAMMING).findFirst().orElseThrow();
        assertThat(programmingDto.templateParticipation()).isNotNull();
        assertThat(programmingDto.templateParticipation().buildPlanId()).isNotBlank();
        assertThat(programmingDto.solutionParticipation()).isNotNull();
        assertThat(programmingDto.solutionParticipation().buildPlanId()).isNotBlank();
        // Each participation stub carries its polymorphic discriminator (Participation's @JsonSubTypes names) so a client echo
        // of this graph deserializes back into the concrete Template/Solution participation instead of throwing a 400.
        assertThat(programmingDto.templateParticipation().type()).isEqualTo("template");
        assertThat(programmingDto.solutionParticipation().type()).isEqualTo("solution");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExam_editRoundTrip_preservesChannelNameAndAllClientFields() throws Exception {
        // The plain GET is the edit round-trip source: the client loads it, reads only the ExamDTO fields, rebuilds the
        // request via toExamUpdateDTO and PUTs it back. This test drives exactly that path and asserts nothing is lost.
        Exam exam = validExamWithCustomFieldValues();
        URI createdExamUri = request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.CREATED);

        ExamDTO loaded = request.get(String.valueOf(createdExamUri), HttpStatus.OK, ExamDTO.class);
        assertThat(loaded.channelName()).isEqualTo("scientific-channel-name");
        assertThat(loaded.examSummaryPublicationDate()).isNotNull();

        // Build the request the way the client's toExamUpdateDTO does: from the loaded response fields only.
        ExamUpdateDTO clientRequest = new ExamUpdateDTO(loaded.id(), loaded.title(), loaded.testExam(), loaded.examWithAttendanceCheck(), loaded.visibleDate(), loaded.startDate(),
                loaded.endDate(), loaded.publishResultsDate(), loaded.examStudentReviewStart(), loaded.examStudentReviewEnd(), loaded.gracePeriod(), loaded.workingTime(),
                loaded.startText(), loaded.endText(), loaded.confirmationStartText(), loaded.confirmationEndText(), loaded.examMaxPoints(), loaded.randomizeExerciseOrder(),
                loaded.numberOfExercisesInExam(), loaded.numberOfCorrectionRoundsInExam(), loaded.examiner(), loaded.moduleNumber(), loaded.courseName(),
                loaded.exampleSolutionPublicationDate(), loaded.examSummaryPublicationDate(), loaded.channelName());
        request.put("/api/exam/courses/" + course1.getId() + "/exams", clientRequest, HttpStatus.OK);

        // Re-load through the plain path and assert the round-tripped fields survived, channel name in particular.
        ExamDTO reloaded = request.get(String.valueOf(createdExamUri), HttpStatus.OK, ExamDTO.class);
        assertThat(reloaded.channelName()).isEqualTo("scientific-channel-name");
        assertThat(reloaded.testExam()).isFalse();
        assertThat(reloaded.title()).isEqualTo(loaded.title());
        assertThat(reloaded.examiner()).isEqualTo(loaded.examiner());
        assertThat(reloaded.courseName()).isEqualTo(loaded.courseName());
        assertThat(reloaded.examMaxPoints()).isEqualTo(loaded.examMaxPoints());
        assertThat(reloaded.workingTime()).isEqualTo(loaded.workingTime());
        assertThat(reloaded.numberOfExercisesInExam()).isEqualTo(loaded.numberOfExercisesInExam());
        assertThat(reloaded.numberOfCorrectionRoundsInExam()).isEqualTo(loaded.numberOfCorrectionRoundsInExam());
        assertThat(reloaded.randomizeExerciseOrder()).isEqualTo(loaded.randomizeExerciseOrder());
        assertThat(reloaded.startText()).isEqualTo(loaded.startText());
        assertThat(reloaded.confirmationEndText()).isEqualTo(loaded.confirmationEndText());
        assertThat(reloaded.startDate()).isEqualTo(loaded.startDate());
        assertThat(reloaded.endDate()).isEqualTo(loaded.endDate());
        assertThat(reloaded.examSummaryPublicationDate()).isEqualTo(loaded.examSummaryPublicationDate());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetExam_returnsGroupsWithParticipationCountsButNoExerciseDetails() throws Exception {
        // reset is served with withDetails=false: groups + transients are populated, but quiz questions and programming
        // participations are NOT hydrated. The ofReset factory must therefore omit them without touching the lazy fields.
        Exam exam = examWithAllExerciseTypesAndQuizQuestions();

        ExamWithExerciseGroupsDTO returnedExam = request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/reset", new LinkedMultiValueMap<>(), null,
                ExamWithExerciseGroupsDTO.class, HttpStatus.OK);

        assertThat(returnedExam.numberOfExamUsers()).isNotNull();
        assertThat(returnedExam.exerciseGroups()).isNotNull();
        var exercises = returnedExam.exerciseGroups().stream().filter(group -> group.exercises() != null).flatMap(group -> group.exercises().stream()).toList();
        assertThat(exercises).isNotEmpty().allMatch(exercise -> exercise.numberOfParticipations() != null);
        // The reset shape carries neither quiz-question stubs nor programming build-plan participations.
        assertThat(exercises).allMatch(exercise -> exercise.quizQuestions() == null);
        assertThat(exercises).allMatch(exercise -> exercise.templateParticipation() == null && exercise.solutionParticipation() == null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestRun_fromDetailedExamDto_createsTestRunFromExerciseIds() throws Exception {
        // Reproduces the create-test-run modal (CreateTestRunModalComponent): it loads the detailed exam from
        // GET ?withExerciseGroups=true and creates the test run from the exercises the instructor picked there.
        // The modal no longer echoes the fetched graph back: the endpoint takes a CreateTestRunDTO carrying only the
        // exam id, the ordered exercise ids and the working time, so the polymorphic quiz-question / participation
        // stubs never travel on the request. The typed-stub guarantee the echo relied on is still asserted below,
        // because the detailed GET response itself must keep carrying it (the exercise-groups page renders from it).
        Exam exam = examWithAllExerciseTypesAndQuizQuestions();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withExerciseGroups", "true");

        JsonNode examJson = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, JsonNode.class, params);
        // The fetched graph still carries a typed quiz-question stub.
        JsonNode fetchedQuizQuestions = findQuizQuestions(examJson.get("exerciseGroups"));
        assertThat(fetchedQuizQuestions).isNotNull();
        assertThat(fetchedQuizQuestions.get(0).get("type").asText()).isNotBlank();

        List<Long> exerciseIds = new ArrayList<>();
        for (JsonNode group : examJson.get("exerciseGroups")) {
            JsonNode groupExercises = group.get("exercises");
            if (groupExercises != null && !groupExercises.isEmpty()) {
                exerciseIds.add(groupExercises.get(0).get("id").asLong());
            }
        }
        assertThat(exerciseIds).isNotEmpty();

        // Creating the test run starts a participation for every picked exercise, so the programming exercise reaches the continuous integration service.
        // The mock server rejects new expectations once requests have been made, so reset it before registering them.
        jenkinsRequestMockProvider.reset();
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var examWithExercises = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(exam.getId());
        mockConnectorRequestsForStartParticipation(ExerciseUtilService.getFirstExerciseWithType(examWithExercises, ProgrammingExercise.class),
                instructor.getParticipantIdentifier(), Set.of(instructor), true);

        StudentExamDTO createdTestRun = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/test-runs",
                new CreateTestRunDTO(exam.getId(), exerciseIds, 6000), StudentExamDTO.class, HttpStatus.OK);

        assertThat(createdTestRun).isNotNull();
        assertThat(createdTestRun.id()).isPositive();
        assertThat(createdTestRun.workingTime()).isEqualTo(6000);
        // The test-run list template renders the owner, so the create response must carry it (StudentExamDTO.withUser).
        assertThat(createdTestRun.testRun()).isTrue();
        assertThat(createdTestRun.user()).isNotNull();
        assertThat(createdTestRun.user().id()).isEqualTo(instructor.getId());

        // The request only carries exercise ids, so what actually matters is that the server resolved them into the
        // persisted test run — in the order the instructor picked them, since that order drives the exam navigation.
        StudentExam persistedTestRun = studentExamRepository.findWithExercisesById(createdTestRun.id()).orElseThrow();
        assertThat(persistedTestRun.isTestRun()).isTrue();
        assertThat(persistedTestRun.getExercises()).extracting(Exercise::getId).containsExactlyElementsOf(exerciseIds);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateOrderOfExerciseGroups_keepsDetailedExamDtoGroupsWithQuizQuestions() throws Exception {
        // Reproduces the exercise-groups page reorder: the page renders from the detailed exam DTO (each group's
        // exercises include the quiz-question stubs, and the quiz cell reads quizQuestions?.length). The reorder now
        // sends only the ordered group ids and gets no body back, so the client keeps the groups it already holds
        // instead of re-rendering an echo. What must stay true is that the detailed GET still carries the questions,
        // both before the reorder and after re-fetching it.
        Exam exam = examWithAllExerciseTypesAndQuizQuestions();
        QuizExercise quizExercise = (QuizExercise) exam.getExerciseGroups().get(3).getExercises().iterator().next();
        int expectedQuestionCount = quizExercise.getQuizQuestions().size();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withExerciseGroups", "true");

        JsonNode examJson = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, JsonNode.class, params);
        JsonNode exerciseGroups = examJson.get("exerciseGroups");
        assertThat(findQuizQuestions(exerciseGroups)).isNotNull();

        List<Long> reorderedIds = new ArrayList<>();
        for (JsonNode group : exerciseGroups) {
            reorderedIds.addFirst(group.get("id").asLong());
        }
        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercise-groups-order", reorderedIds, HttpStatus.OK);

        JsonNode reloadedExam = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK, JsonNode.class, params);
        JsonNode reloadedGroups = reloadedExam.get("exerciseGroups");
        assertThat(reloadedGroups).hasSize(exerciseGroups.size());
        // The persisted order is the requested one, and the quiz exercise still carries its questions.
        List<Long> persistedIds = new ArrayList<>();
        reloadedGroups.forEach(group -> persistedIds.add(group.get("id").asLong()));
        assertThat(persistedIds).containsExactlyElementsOf(reorderedIds);
        JsonNode reloadedQuizQuestions = findQuizQuestions(reloadedGroups);
        assertThat(reloadedQuizQuestions).isNotNull();
        assertThat(reloadedQuizQuestions.size()).isEqualTo(expectedQuestionCount);
    }

    /**
     * Returns the first {@code quizQuestions} array found across the given exercise groups, or {@code null} if none carry one.
     */
    private static JsonNode findQuizQuestions(JsonNode exerciseGroups) {
        for (JsonNode group : exerciseGroups) {
            JsonNode exercises = group.get("exercises");
            if (exercises != null) {
                for (JsonNode exercise : exercises) {
                    if (exercise.hasNonNull("quizQuestions")) {
                        return exercise.get("quizQuestions");
                    }
                }
            }
        }
        return null;
    }

    /**
     * Shared fixture for the detailed-get and reset tests: a full exam with all exercise types whose quiz exercise
     * (exercise group index 3) carries persisted questions.
     *
     * @return the persisted exam
     */
    private Exam examWithAllExerciseTypesAndQuizQuestions() {
        Exam exam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndProgramming(course1);
        QuizExercise quizExercise = (QuizExercise) exam.getExerciseGroups().get(3).getExercises().iterator().next();
        // Pin a difficulty so the detailed DTO's difficulty mapping (read by the create-test-run modal) is asserted against a concrete value.
        quizExercise.setDifficulty(DifficultyLevel.HARD);
        QuizExerciseFactory.addQuestionsToQuizExercise(quizExercise);
        quizExerciseRepository.save(quizExercise);
        return exam;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamWithExerciseGroups_nullStartDate_doesNotThrow() throws Exception {
        // isStarted() dereferences startDate; a not-yet-scheduled exam (null startDate) must map without NPE, and the
        // computed started flag reads false (matching what the client renders for an absent flag).
        Exam exam = examUtilService.addExamWithExerciseGroup(course1, true);
        exam.setStartDate(null);
        examRepository.save(exam);

        ExamWithExerciseGroupsDTO returnedExam = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "?withExerciseGroups=true", HttpStatus.OK,
                ExamWithExerciseGroupsDTO.class);

        assertThat(returnedExam.startDate()).isNull();
        assertThat(returnedExam.started()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamsForCourse_asInstructor() throws Exception {
        var exams = request.getList("/api/exam/courses/" + course1.getId() + "/exams", HttpStatus.OK, Exam.class);
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
        assertThat(userCourseRoleTestRepository.existsByUser_IdAndCourse_IdAndRole(instructor.getId(), course1.getId(), CourseRole.INSTRUCTOR)).isTrue();
        // Seed an exam with a quiz exercise in a course the instructor manages: the endpoint filters to exams that
        // contain a quiz exercise and for which the caller has instructor access.
        Exam quizExam = examUtilService.addExamWithExerciseGroup(course1, true);
        ExerciseGroup exerciseGroup = quizExam.getExerciseGroups().getFirst();
        QuizExercise quizExercise = QuizExerciseFactory.createQuizForExam(exerciseGroup);
        exerciseRepository.save(quizExercise);

        // The only consumer of this endpoint is the "add existing questions from an exam" quiz picker, which reads
        // exactly id + title off each returned exam (option value + label) and nothing else — pin that wire contract.
        var exams = request.getList("/api/exam/courses/" + course1.getId() + "/exams-for-user", HttpStatus.OK, ExamForQuestionPoolDTO.class);
        assertThat(exams).isNotEmpty();
        assertThat(exams).allSatisfy(exam -> {
            assertThat(exam.id()).isPositive();
            assertThat(exam.title()).isNotBlank();
        });
        assertThat(exams).anySatisfy(exam -> {
            assertThat(exam.id()).isEqualTo(quizExam.getId());
            assertThat(exam.title()).isEqualTo(quizExam.getTitle());
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetCurrentAndUpcomingExams() throws Exception {
        // Two queries: one resolving the authenticated admin against the database, which @EnforceAdmin does on every
        // admin request, and one for the exams (the course is fetch-joined). Without the join, each exam row would
        // trigger a secondary select for its course, so this still guards the data-economy fix rather than just the
        // response shape.
        var exams = assertThatDb(() -> request.getList("/api/exam/admin/courses/upcoming-exams", HttpStatus.OK, UpcomingExamDTO.class)).hasBeenCalledAtMostTimes(2);
        ZonedDateTime currentDay = now().truncatedTo(ChronoUnit.DAYS);
        for (int i = 0; i < exams.size(); i++) {
            UpcomingExamDTO exam = exams.get(i);
            // Every returned exam carries the fields the admin overview table renders, with real data.
            assertThat(exam.id()).as("for exam with index %d", i).isNotNull();
            assertThat(exam.title()).as("for exam with index %d and id %d", i, exam.id()).isNotBlank();
            assertThat(exam.endDate()).as("for exam with index %d and id %d", i, exam.id()).isAfterOrEqualTo(currentDay);
            assertThat(exam.course()).as("for exam with index %d and id %d", i, exam.id()).isNotNull();
            assertThat(exam.course().id()).as("for exam with index %d and id %d", i, exam.id()).isNotNull();
            // The DTO no longer carries isTestCourse, so verify the query's test-course exclusion against the database.
            assertThat(examRepository.findByIdElseThrow(exam.id()).getCourse().isTestCourse()).as("for exam with index %d and id %d", i, exam.id()).isFalse();
        }
        // The response content reflects a concrete created exam (title / course / dates), not just a 200 status.
        UpcomingExamDTO createdExam = exams.stream().filter(exam -> exam.id().equals(exam1.getId())).findFirst().orElseThrow();
        assertThat(createdExam.title()).isEqualTo(exam1.getTitle());
        assertThat(createdExam.testExam()).isEqualTo(exam1.isTestExam());
        assertThat(createdExam.course().id()).isEqualTo(course1.getId());
        assertThat(createdExam.course().title()).isEqualTo(course1.getTitle());
        assertThat(createdExam.visibleDate()).isNotNull();
        assertThat(createdExam.startDate()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user", roles = "USER")
    void testGetCurrentAndUpcomingExamsForbiddenForUser() throws Exception {
        request.getList("/api/exam/admin/courses/upcoming-exams", HttpStatus.FORBIDDEN, UpcomingExamDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCurrentAndUpcomingExamsForbiddenForInstructor() throws Exception {
        request.getList("/api/exam/admin/courses/upcoming-exams", HttpStatus.FORBIDDEN, UpcomingExamDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetCurrentAndUpcomingExamsForbiddenForTutor() throws Exception {
        request.getList("/api/exam/admin/courses/upcoming-exams", HttpStatus.FORBIDDEN, UpcomingExamDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteEmptyExam_asInstructor() throws Exception {
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithChannel() throws Exception {
        Exam exam = examUtilService.addExam(course1);
        Channel examChannel = examUtilService.addExamChannel(exam, "test");

        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);

        Optional<Channel> examChannelAfterDelete = channelRepository.findById(examChannel.getId());
        assertThat(examChannelAfterDelete).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithExerciseGroupAndTextExercise_asInstructor() throws Exception {
        final TextExercise textExercise = exerciseRepository.save(TextExerciseFactory.generateTextExerciseForExam(exam2.getExerciseGroups().getFirst()));
        if (searchableEntityWeaviateService != null) {
            searchableEntityWeaviateService.upsertExerciseAsync(ExerciseSearchableEntityDTO.fromExercise(textExercise));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> WeaviateTestUtil.assertExerciseExistsInWeaviate(weaviateService, textExercise));
        }
        WeaviateTestUtil.assertExerciseExistsInWeaviate(weaviateService, textExercise);

        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam2.getId(), HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam2.getId());

        WeaviateTestUtil.assertExerciseNotInWeaviate(weaviateService, textExercise.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteExamThatDoesNotExist() throws Exception {
        request.delete("/api/exam/courses/" + course2.getId() + "/exams/654555", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetEmptyExam_asInstructor() throws Exception {
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/reset", HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetExamWithExerciseGroupAndTextExercise_asInstructor() throws Exception {
        TextExercise textExercise = TextExerciseFactory.generateTextExerciseForExam(exam2.getExerciseGroups().getFirst());
        exerciseRepository.save(textExercise);
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/reset", HttpStatus.OK);
        verify(examAccessService).checkCourseAndExamAccessForInstructorElseThrow(course1.getId(), exam2.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testResetExamThatDoesNotExist() throws Exception {
        request.delete("/api/exam/courses/" + course2.getId() + "/exams/654555/reset", HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetExamWithQuizExercise_asInstructor() throws Exception {
        QuizExercise quizExercise = QuizExerciseFactory.createQuizForExam(exam2.getExerciseGroups().getFirst());
        quizExerciseRepository.save(quizExercise);

        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/reset", HttpStatus.OK);
        quizExercise = (QuizExercise) exerciseRepository.findByIdElseThrow(quizExercise.getId());
        assertThat(quizExercise.getReleaseDate()).isNull();
        assertThat(quizExercise.getDueDate()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamWithOptions() throws Exception {
        Course course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Exam exam = examUtilService.addExamWithUser(course, student1, false, now().minusHours(3), now().minusHours(2), now().minusHours(1));
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, true, true);

        // 1. without options -> scalar-core ExamDTO, no exercise groups on the wire
        var exam1 = request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, ExamDTO.class);
        assertThat(exam1.id()).isEqualTo(exam.getId());

        // 2. with exercise groups -> ExamWithExerciseGroupsDTO
        var params = new LinkedMultiValueMap<String, String>();
        params.add("withExerciseGroups", "true");
        var exam2 = request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK, ExamWithExerciseGroupsDTO.class, params);
        assertThat(exam2.exerciseGroups()).hasSize(exam.getExerciseGroups().size());

        // Per-group exercise membership: each group must carry exactly its source exercises (by id) with matching types,
        // not merely the right count.
        for (int i = 0; i < exam.getExerciseGroups().size(); i++) {
            var expectedGroup = exam.getExerciseGroups().get(i);
            var actualGroup = exam2.exerciseGroups().get(i);
            assertThat(actualGroup.id()).isEqualTo(expectedGroup.getId());
            var expectedExercises = expectedGroup.getExercises();
            if (expectedExercises.isEmpty()) {
                assertThat(actualGroup.exercises()).isNullOrEmpty();
            }
            else {
                assertThat(actualGroup.exercises()).extracting(actualExercise -> actualExercise.id())
                        .containsExactlyInAnyOrderElementsOf(expectedExercises.stream().map(Exercise::getId).toList());
                assertThat(actualGroup.exercises()).allSatisfy(actualExercise -> {
                    Exercise expectedExercise = expectedExercises.stream().filter(candidate -> candidate.getId().equals(actualExercise.id())).findFirst().orElseThrow();
                    assertThat(actualExercise.type()).isEqualTo(expectedExercise.getExerciseType());
                });
            }
        }

        var quizExercises = exam2.exerciseGroups().get(1).exercises();
        assertThat(quizExercises).isNotEmpty()
                .allMatch(exercise -> exercise.type() == ExerciseType.QUIZ && exercise.quizQuestions() != null && !exercise.quizQuestions().isEmpty());

        var programming = exam2.exerciseGroups().get(6).exercises().iterator().next();
        assertThat(programming.type()).isEqualTo(ExerciseType.PROGRAMMING);
        assertThat(programming.templateParticipation()).isNotNull();
        assertThat(programming.templateParticipation().buildPlanId()).isNotBlank();
        assertThat(programming.solutionParticipation()).isNotNull();
        assertThat(programming.solutionParticipation().buildPlanId()).isNotBlank();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamForTestRunDashboard_forbidden() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exam-for-test-run-assessment-dashboard", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamForTestRunDashboard_badRequest() throws Exception {
        request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam1.getId() + "/exam-for-test-run-assessment-dashboard", HttpStatus.BAD_REQUEST, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithOneTestRuns() throws Exception {
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        request.delete("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithMultipleTestRuns() throws Exception {
        jenkinsRequestMockProvider.enableMockingOfRequests();

        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, true, true);
        mockDeleteProgrammingExercise(ExerciseUtilService.getFirstExerciseWithType(exam, ProgrammingExercise.class), Set.of(instructor));

        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        assertThat(studentExamRepository.findAllTestRunsByExamId(exam.getId())).hasSize(3);
        request.delete("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId(), HttpStatus.OK);
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

        request.delete("/api/core/admin/courses/" + course.getId(), HttpStatus.OK);

        assertThat(courseRepository.findById(course.getId())).isEmpty();
        assertThat(examRepository.findById(exam.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamForTestRunDashboard_ok() throws Exception {
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        ExamForAssessmentDashboardDTO dashboard = request.get(
                "/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/exam-for-test-run-assessment-dashboard", HttpStatus.OK,
                ExamForAssessmentDashboardDTO.class);
        var exercises = dashboard.exerciseGroups().stream().flatMap(exerciseGroup -> exerciseGroup.exercises().stream()).toList();
        assertThat(exercises).isNotEmpty();
        // the test-run dashboard does not compute the per-exercise assessment statistics (its client screen hides them),
        // so the transient stats stay absent on the wire
        assertThat(exercises).allSatisfy(exercise -> {
            assertThat(exercise.numberOfSubmissions()).isNull();
            assertThat(exercise.tutorParticipations()).isNull();
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForStart() throws Exception {
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        exam.setVisibleDate(ZonedDateTime.now().minusHours(1).minusMinutes(5));
        StudentExam response = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/own-student-exam", HttpStatus.OK, StudentExam.class);
        assertThat(response.getExam()).isEqualTo(exam);
        verify(examAccessService).getOrCreateStudentExamElseThrow(course1.getId(), exam.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetOwnStudentExam_returnsConductionDTOWithCoverFields() throws Exception {
        // Pins the own-student-exam wire contract the exam-conduction cover reads: the rich exam projection (markdown
        // cover texts, dates, working time, course id) plus the student's name, and that the exercise graph is NOT leaked.
        Exam exam = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        exam.setVisibleDate(ZonedDateTime.now().minusHours(1).minusMinutes(5));
        exam.setStartText("please-start-carefully");
        exam.setEndText("please-review-before-submitting");
        exam.setConfirmationStartText("I-confirm-start");
        exam.setConfirmationEndText("I-confirm-submit");
        exam.setExamMaxPoints(42);
        exam.setExaminer("Prof. Examiner");
        exam.setModuleNumber("IN0000");
        exam.setCourseName("Conduction Course");
        exam.setNumberOfExercisesInExam(3);
        exam.setGracePeriod(180);
        exam.setExamWithAttendanceCheck(true);
        // a delayed submission overview, so the summary-gate fields below are asserted against real values and not against null
        ZonedDateTime summaryPublicationDate = ZonedDateTime.now().plusDays(1);
        ZonedDateTime publishResultsDate = ZonedDateTime.now().plusDays(2);
        exam.setExamSummaryPublicationDate(summaryPublicationDate);
        exam.setPublishResultsDate(publishResultsDate);
        examRepository.save(exam);

        StudentExamForConductionDTO response = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/own-student-exam", HttpStatus.OK,
                StudentExamForConductionDTO.class);

        // the student-exam scalars the conduction UI reads
        assertThat(response.testRun()).isFalse();
        assertThat(response.workingTime()).isEqualTo(exam.getDuration());
        // the examined-student box reads user.name
        assertThat(response.user()).isNotNull();
        assertThat(response.user().name()).isEqualTo(student1.getName());
        // the rich exam projection the cover renders
        ExamForConductionDTO examDTO = response.exam();
        assertThat(examDTO).isNotNull();
        assertThat(examDTO.id()).isEqualTo(exam.getId());
        assertThat(examDTO.testExam()).isFalse();
        assertThat(examDTO.startDate()).isNotNull();
        assertThat(examDTO.startText()).isEqualTo("please-start-carefully");
        assertThat(examDTO.endText()).isEqualTo("please-review-before-submitting");
        assertThat(examDTO.confirmationStartText()).isEqualTo("I-confirm-start");
        assertThat(examDTO.confirmationEndText()).isEqualTo("I-confirm-submit");
        assertThat(examDTO.examMaxPoints()).isEqualTo(42);
        // the exam-start information box
        assertThat(examDTO.examiner()).isEqualTo("Prof. Examiner");
        assertThat(examDTO.moduleNumber()).isEqualTo("IN0000");
        assertThat(examDTO.courseName()).isEqualTo("Conduction Course");
        assertThat(examDTO.numberOfExercisesInExam()).isEqualTo(3);
        assertThat(examDTO.title()).isEqualTo(exam.getTitle());
        // the participation component computes the individual end date and the waiting-for-start state from these
        assertThat(examDTO.visibleDate()).isNotNull();
        assertThat(examDTO.endDate()).isNotNull();
        assertThat(examDTO.gracePeriod()).isEqualTo(180);
        assertThat(examDTO.workingTime()).isEqualTo(exam.getWorkingTime());
        assertThat(examDTO.examWithAttendanceCheck()).isTrue();
        // the client-side summary gate (isExamSummaryPublished) evaluates these two off this projection after a hand-in and
        // treats a missing examSummaryPublicationDate as "published", so both have to survive the DTO conversion
        assertThat(examDTO.examSummaryPublicationDate()).isNotNull();
        assertThat(examDTO.examSummaryPublicationDate().toInstant()).isCloseTo(summaryPublicationDate.toInstant(), within(1, ChronoUnit.SECONDS));
        assertThat(examDTO.publishResultsDate()).isNotNull();
        assertThat(examDTO.publishResultsDate().toInstant()).isCloseTo(publishResultsDate.toInstant(), within(1, ChronoUnit.SECONDS));
        // exam-cover reads exam.course.id for the attendance-check / conduction links
        assertThat(examDTO.course()).isNotNull();
        assertThat(examDTO.course().id()).isEqualTo(course1.getId());
        // Guards the whole projection rather than the fields above one by one: every record component the conduction flow
        // reads must be populated here, so a future refactor that drops one fails this test instead of silently shipping a
        // client that reads undefined. That is exactly how examSummaryPublicationDate and publishResultsDate went missing.
        assertThat(examDTO).hasNoNullFieldsOrProperties();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    @ValueSource(ints = { 0, 1, 2 })
    void testGetExamForExamAssessmentDashboard(int numberOfCorrectionRounds) throws Exception {
        // we need an exam from the past, otherwise the tutor won't have access
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(course, student1, now().minusHours(3), now().minusHours(2), now().minusHours(1));
        Exam exam = course.getExams().iterator().next();

        // Ensure the API endpoint works for all number of correctionRounds
        exam.setNumberOfCorrectionRoundsInExam(numberOfCorrectionRounds);
        examRepository.save(exam);

        ExamForAssessmentDashboardDTO receivedExam = request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/exam-for-assessment-dashboard", HttpStatus.OK,
                ExamForAssessmentDashboardDTO.class);

        // Test that the received exam has two text exercises
        assertThat(receivedExam.exerciseGroups().getFirst().exercises()).as("Two exercises are returned").hasSize(2);
        // Test that the received exam has zero quiz exercises, because quiz exercises do not need to be corrected manually
        // (an empty exercise list is dropped by NON_EMPTY and arrives as null, which the client guards with *ngIf)
        assertThat(receivedExam.exerciseGroups().get(1).exercises()).as("Zero exercises are returned").isNullOrEmpty();

        // Pin the assessment-statistics contract the tutor dashboard reads: each interesting exercise carries the
        // submission stat and the current tutor's participation (with a status) attached by the dashboard service.
        var firstExercise = receivedExam.exerciseGroups().getFirst().exercises().getFirst();
        assertThat(firstExercise.numberOfSubmissions()).as("submission stat is attached").isNotNull();
        assertThat(firstExercise.tutorParticipations()).as("tutor participation is attached").isNotNull().first().satisfies(tp -> assertThat(tp.status()).isNotNull());
        // Pin the course projection the client turns into access rights + complaint/feedback flags
        assertThat(receivedExam.course()).isNotNull();
        assertThat(receivedExam.course().id()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamForExamAssessmentDashboard_trainedTutorWithUnreviewedExample_bothCollectionsOnWire() throws Exception {
        // Reproduces the tutor-participation-graph's TRAINED-step colour decision (calculateClasses): it compares
        // exercise.exampleSubmissions against tutorParticipation.trainedExampleSubmissions (both filtered by
        // usedForTutorial) to tell a fully-trained tutor (lengths match -> green) from one with a newly-added example
        // still pending (lengths differ -> orange). Both collections must be present on the wire, with the right
        // ids, for the client to even make that comparison; a TRAINED tutor with an unreviewed newly-added example
        // must not silently render green.
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(course, student1, now().minusHours(3), now().minusHours(2), now().minusHours(1));
        Exam exam = course.getExams().iterator().next();
        Exam examWithExerciseGroups = examService.findByIdWithExerciseGroupsAndExercisesElseThrow(exam.getId(), false);
        TextExercise textExercise = (TextExercise) examWithExerciseGroups.getExerciseGroups().getFirst().getExercises().iterator().next();
        User tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");

        // Example #1: the tutor has already trained on (and assessed) this one.
        ExampleSubmission trainedExample = participationUtilService.generateExampleSubmission("trained example text", textExercise, false, true);
        trainedExample = exampleSubmissionService.save(trainedExample);
        var trainedResult = submissionService.saveNewEmptyResult(trainedExample.getSubmission(), textExercise.getId());
        trainedResult.setExampleResult(true);
        resultRepository.save(trainedResult);

        // Example #2: a newly-added example the tutor has NOT trained on yet -- the "unreviewed" example from the finding.
        ExampleSubmission untrainedExample = participationUtilService.generateExampleSubmission("untrained example text", textExercise, false, true);
        untrainedExample = exampleSubmissionService.save(untrainedExample);
        var untrainedResult = submissionService.saveNewEmptyResult(untrainedExample.getSubmission(), textExercise.getId());
        untrainedResult.setExampleResult(true);
        resultRepository.save(untrainedResult);

        // The tutor's status is TRAINED, but they have only trained on example #1.
        TutorParticipation tutorParticipation = tutorParticipationService.createNewParticipation(textExercise, tutor);
        tutorParticipation.setStatus(TutorParticipationStatus.TRAINED);
        tutorParticipation.addTrainedExampleSubmissions(trainedExample);
        tutorParticipationRepository.save(tutorParticipation);

        ExamForAssessmentDashboardDTO receivedExam = request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/exam-for-assessment-dashboard", HttpStatus.OK,
                ExamForAssessmentDashboardDTO.class);

        var receivedExercise = receivedExam.exerciseGroups().stream().flatMap(group -> group.exercises().stream()).filter(exercise -> exercise.id() == textExercise.getId())
                .findFirst().orElseThrow();
        assertThat(receivedExercise.exampleSubmissions()).as("both example submissions are on the wire").isNotNull()
                .extracting(ExamForAssessmentDashboardDTO.ExampleSubmissionForAssessmentDashboardDTO::id)
                .containsExactlyInAnyOrder(trainedExample.getId(), untrainedExample.getId());

        assertThat(receivedExercise.tutorParticipations()).isNotNull().hasSize(1);
        var receivedTutorParticipation = receivedExercise.tutorParticipations().getFirst();
        assertThat(receivedTutorParticipation.status()).isEqualTo(TutorParticipationStatus.TRAINED);
        assertThat(receivedTutorParticipation.trainedExampleSubmissions()).as("only the trained example is in the tutor's trained collection -- the untrained one is absent")
                .isNotNull().extracting(ExamForAssessmentDashboardDTO.ExampleSubmissionForAssessmentDashboardDTO::id).containsExactly(trainedExample.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamForExamAssessmentDashboard_beforeDueDate() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        exam.setEndDate(now().plusWeeks(1));
        examRepository.save(exam);

        request.get("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/exam-for-assessment-dashboard", HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetExamForExamAssessmentDashboard_asStudent_forbidden() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exam-for-assessment-dashboard", HttpStatus.FORBIDDEN, Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamForExamAssessmentDashboard_courseIdDoesNotMatch_badRequest() throws Exception {
        request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam1.getId() + "/exam-for-assessment-dashboard", HttpStatus.BAD_REQUEST, Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamForExamAssessmentDashboard_notFound() throws Exception {
        request.get("/api/exam/courses/-1/exams/-1/exam-for-assessment-dashboard", HttpStatus.NOT_FOUND, Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetExamForExamDashboard_NotTAOfCourse_forbidden() throws Exception {
        Exam exam = ExamFactory.generateExam(course10);
        examRepository.save(exam);

        request.get("/api/exam/courses/" + course10.getId() + "/exams/" + exam.getId() + "/exam-for-assessment-dashboard", HttpStatus.FORBIDDEN, Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetExamScore_tutorNotInCourse_forbidden() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/scores", HttpStatus.FORBIDDEN, ExamScoresDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamScore_tutor_forbidden() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/scores", HttpStatus.FORBIDDEN, ExamScoresDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamStatistics() throws Exception {
        ExamChecklistDTO actualStatistics = examService.getStatsForChecklist(exam1, true);
        ExamChecklistDTO returnedStatistics = request.get("/api/exam/courses/" + exam1.getCourse().getId() + "/exams/" + exam1.getId() + "/statistics", HttpStatus.OK,
                ExamChecklistDTO.class);
        assertThat(returnedStatistics.allExamExercisesAllStudentsPrepared()).isEqualTo(actualStatistics.allExamExercisesAllStudentsPrepared());
        assertThat(returnedStatistics.allExamExercisesAllStudentsPrepared()).isEqualTo(actualStatistics.allExamExercisesAllStudentsPrepared());
        assertThat(returnedStatistics.numberOfAllComplaints()).isEqualTo(actualStatistics.numberOfAllComplaints());
        assertThat(returnedStatistics.numberOfAllComplaintsDone()).isEqualTo(actualStatistics.numberOfAllComplaintsDone());
        assertThat(returnedStatistics.numberOfExamsStarted()).isEqualTo(actualStatistics.numberOfExamsStarted());
        assertThat(returnedStatistics.numberOfExamsSubmitted()).isEqualTo(actualStatistics.numberOfExamsSubmitted());
        assertThat(returnedStatistics.numberOfTestRuns()).isEqualTo(actualStatistics.numberOfTestRuns());
        assertThat(returnedStatistics.numberOfGeneratedStudentExams()).isEqualTo(actualStatistics.numberOfGeneratedStudentExams());
        assertThat(returnedStatistics.numberOfTotalExamAssessmentsFinishedByCorrectionRound()).isEqualTo(actualStatistics.numberOfTotalExamAssessmentsFinishedByCorrectionRound());
        assertThat(returnedStatistics.numberOfTotalParticipationsForAssessment()).isEqualTo(actualStatistics.numberOfTotalParticipationsForAssessment());
        assertThat(returnedStatistics.existsUnassessedQuizzes()).isEqualTo(actualStatistics.existsUnassessedQuizzes());
        assertThat(returnedStatistics.existsUnsubmittedExercises()).isEqualTo(actualStatistics.existsUnsubmittedExercises());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamStatistics_considersOnlyInitializedParticipationsForPreparedExercises() throws Exception {
        ExerciseGroup exerciseGroup = exam2.getExerciseGroups().getFirst();
        TextExercise textExercise = exerciseRepository.save(TextExerciseFactory.generateTextExerciseForExam(exerciseGroup));
        exerciseGroup.addExercise(textExercise);
        exam2.setNumberOfExercisesInExam(1);
        examRepository.save(exam2);

        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam2, student1);
        studentExam.addExercise(textExercise);
        studentExamRepository.save(studentExam);

        StudentParticipation failedPreparation = ParticipationFactory.generateStudentParticipation(InitializationState.UNINITIALIZED, textExercise, student1);
        studentParticipationRepository.save(failedPreparation);

        assertThat(studentParticipationRepository.countParticipationsByExerciseIdAndTestRun(textExercise.getId(), false)).isOne();
        assertThat(studentParticipationRepository.countInitializedParticipationsByExerciseIdAndExamIdIgnoreTestRuns(textExercise.getId(), exam2.getId())).isZero();

        ExamChecklistDTO returnedStatistics = request.get("/api/exam/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/statistics", HttpStatus.OK,
                ExamChecklistDTO.class);

        assertThat(returnedStatistics.numberOfGeneratedStudentExams()).isOne();
        assertThat(returnedStatistics.allExamExercisesAllStudentsPrepared()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLatestExamEndDate() throws Exception {
        // Setup exam and user

        // Set student exam without working time and save into database
        StudentExam studentExam = new StudentExam();
        studentExam.setUser(student1);
        studentExam.setTestRun(false);
        // A student exam belongs to an exam, so the link is set before it is stored
        exam2.addStudentExam(studentExam);
        studentExam = studentExamRepository.save(studentExam);
        exam2 = examRepository.save(exam2);

        // Get the latest exam end date DTO from server -> This returns the endDate as no specific student working time is set
        ExamInformationDTO examInfo = request.get("/api/exam/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/latest-end-date", HttpStatus.OK,
                ExamInformationDTO.class);
        // Check that latest end date is equal to endDate (no specific student working time). Do not check for equality as we lose precision when saving to the database
        assertThat(examInfo.latestIndividualEndDate()).isCloseTo(exam2.getEndDate(), within(1, ChronoUnit.SECONDS));

        // Set student exam with working time and save
        studentExam.setWorkingTime(3600);
        studentExamRepository.save(studentExam);

        // Get the latest exam end date DTO from server -> This returns the startDate + workingTime
        ExamInformationDTO examInfo2 = request.get("/api/exam/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/latest-end-date", HttpStatus.OK,
                ExamInformationDTO.class);
        // Check that latest end date is equal to startDate + workingTime
        assertThat(examInfo2.latestIndividualEndDate()).isCloseTo(exam2.getStartDate().plusHours(1), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor10", roles = "INSTRUCTOR")
    void testCourseAndExamAccessForInstructors_notInstructorInCourse_forbidden() throws Exception {
        // Instructor10 is not instructor for the course
        // Update exam
        request.put("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam1), HttpStatus.FORBIDDEN);
        // Get exam
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId(), HttpStatus.FORBIDDEN, Exam.class);
        // Generate student exams
        request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.FORBIDDEN);
        // Generate missing exams
        request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/generate-missing-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.FORBIDDEN);
        // Start exercises
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/start-exercises", null, HttpStatus.FORBIDDEN, null);
        // Add students to exam
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students", List.of(new StudentDTO(null, null, null, null, null)), HttpStatus.FORBIDDEN);
        // Delete student from exam
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.FORBIDDEN);
        // Update order of exerciseGroups
        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercise-groups-order", new ArrayList<ExerciseGroup>(), HttpStatus.FORBIDDEN);
        // Get the latest individual end date
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/latest-end-date", HttpStatus.FORBIDDEN, ExamInformationDTO.class);
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
        Course course = courseUtilService.createEnrolledCourseWithExamExercisesAndSubmissions(TEST_PREFIX);
        course.setEndDate(now().minusMinutes(5));
        course = courseRepository.save(course);

        request.put("/api/course/courses/" + course.getId() + "/archive", null, HttpStatus.OK);

        final var courseId = course.getId();
        await().atMost(Duration.ofSeconds(30)).until(() -> courseRepository.findById(courseId).orElseThrow().getCourseArchivePath() != null);

        var updatedCourse = courseRepository.findById(courseId).orElseThrow();
        assertThat(updatedCourse.getCourseArchivePath()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveExamAsInstructor() throws Exception {
        archiveExamAsInstructor();
    }

    private Course archiveExamAsInstructor() throws Exception {
        var course = courseUtilService.createEnrolledCourseWithExamExercisesAndSubmissions(TEST_PREFIX);
        var exam = examRepository.findByCourseId(course.getId()).stream().findFirst().orElseThrow();

        request.put("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/archive", null, HttpStatus.OK);

        final var examId = exam.getId();
        await().atMost(Duration.ofSeconds(30)).until(() -> examRepository.findById(examId).orElseThrow().getExamArchivePath() != null);

        var updatedExam = examRepository.findById(examId).orElseThrow();
        assertThat(updatedExam.getExamArchivePath()).isNotEmpty();
        return course;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testArchiveExamAsStudent_forbidden() throws Exception {
        Exam exam = examUtilService.addExam(course1);

        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/archive", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveExamBeforeEndDate_badRequest() throws Exception {
        Course course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        course.setEndDate(now().plusMinutes(5));
        course = courseRepository.save(course);

        Exam exam = examUtilService.addExam(course);
        exam = examRepository.save(exam);

        request.put("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/archive", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDownloadExamArchiveAsStudent_forbidden() throws Exception {
        request.get("/api/exam/courses/" + 1 + "/exams/" + 1 + "/download-archive", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDownloadExamArchiveAsTutor_forbidden() throws Exception {
        request.get("/api/exam/courses/" + 1 + "/exams/" + 1 + "/download-archive", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDownloadExamArchiveAsInstructor_not_found() throws Exception {
        // Return not found if the exam doesn't exist
        var downloadedArchive = request.get("/api/exam/courses/" + course1.getId() + "/exams/-1/download-archive", HttpStatus.NOT_FOUND, String.class);
        assertThat(downloadedArchive).isNull();

        // Returns not found if there is no archive
        downloadedArchive = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/download-archive", HttpStatus.NOT_FOUND, String.class);
        assertThat(downloadedArchive).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDownloadExamArchiveAsInstructorNotInCourse_forbidden() throws Exception {
        // Create an exam with no archive
        Course course = courseUtilService.createCourse();
        var exam = examUtilService.addExam(course);

        request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/download-archive", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDownloadExamArchiveAsInstructor() throws Exception {
        var course = archiveExamAsInstructor();

        // Download the archive
        var exam = examRepository.findByCourseId(course.getId()).stream().findFirst().orElseThrow();
        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("Content-Disposition", "attachment; filename=\"" + exam.getExamArchivePath() + "\"");
        var archive = request.getFile("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/download-archive", HttpStatus.OK, new LinkedMultiValueMap<>(),
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

        RepositoryExportTestUtil.safeDeleteDirectory(extractedArchiveDir);
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
        testGetExamTitleRegularTitle();
        testGetExamTitleStrippedTitle();
    }

    private void testGetExamTitleRegularTitle() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        exam.setTitle("Test Exam");
        exam = examRepository.save(exam);

        final var title = request.get("/api/exam/exams/" + exam.getId() + "/title", HttpStatus.OK, String.class);

        assertThat(title).isEqualTo("Test Exam");
    }

    private void testGetExamTitleStrippedTitle() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        exam.setTitle(" \r\r\n\n\t Test Exam title  \f \r \r\n \r\f\f\f   \r\f\t");
        exam = examRepository.save(exam);

        final var title = request.get("/api/exam/exams/" + exam.getId() + "/title", HttpStatus.OK, String.class);

        assertThat(title).isEqualTo("Test Exam title");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetExamTitleForNonExistingExam() throws Exception {
        request.get("/api/exam/exams/123124123123/title", HttpStatus.NOT_FOUND, String.class);
    }

    /// Creates a new Exam - this is outside the ExamFactory because I'm relying on the fact that
    /// exactly the fields set in this method are being set, which could be subject to change in the Factory.
    private Exam validExamWithCustomFieldValues() {
        Exam exam = ExamFactory.generateExam(course1);
        exam.setTitle("Exam Title");
        exam.setTestExam(false);
        /// Artemis truncates to 6 sub-second digits
        final var baseTime = ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        exam.setVisibleDate(baseTime.minusHours(1));
        exam.setStartDate(baseTime);
        exam.setEndDate(baseTime.plusHours(1));
        exam.setExamStudentReviewStart(baseTime.plusHours(12));
        exam.setExamStudentReviewEnd(baseTime.plusDays(1));
        exam.setExamSummaryPublicationDate(baseTime.plusHours(6));
        exam.setWorkingTime(60 * 60);
        exam.setExaminer("Prof. Dr. Stephan Krusche");
        exam.setStartText("Start Text");
        exam.setEndText("End Text");
        exam.setConfirmationStartText("Confirmation Start Text");
        exam.setConfirmationEndText("Confirmation End Text");
        exam.setExamMaxPoints(99);
        exam.setNumberOfExercisesInExam(4);
        exam.setRandomizeExerciseOrder(true);
        exam.setNumberOfCorrectionRoundsInExam(1);
        exam.setChannelName("scientific-channel-name");
        exam.setCourseName("Course Name");

        return exam;
    }

    /// Compares two exams on all fields that {@link ExamIntegrationTest#validExamWithCustomFieldValues()} sets
    private void checkCustomFieldValuesExamsAreEffectivelyEqual(Exam actualExam, Exam expectedExam) {
        assertThat(actualExam.getTitle()).isEqualTo(expectedExam.getTitle());
        assertThat(actualExam.getWorkingTime()).isEqualTo(expectedExam.getWorkingTime());
        assertThat(actualExam.getExaminer()).isEqualTo(expectedExam.getExaminer());
        assertThat(actualExam.getStartText()).isEqualTo(expectedExam.getStartText());
        assertThat(actualExam.getEndText()).isEqualTo(expectedExam.getEndText());
        assertThat(actualExam.getConfirmationStartText()).isEqualTo(expectedExam.getConfirmationStartText());
        assertThat(actualExam.getConfirmationEndText()).isEqualTo(expectedExam.getConfirmationEndText());
        assertThat(actualExam.getExamMaxPoints()).isEqualTo(expectedExam.getExamMaxPoints());
        assertThat(actualExam.getNumberOfExercisesInExam()).isEqualTo(expectedExam.getNumberOfExercisesInExam());
        assertThat(actualExam.getNumberOfCorrectionRoundsInExam()).isEqualTo(expectedExam.getNumberOfCorrectionRoundsInExam());
        assertThat(actualExam.getChannelName()).isEqualTo(expectedExam.getChannelName());
        assertThat(actualExam.getCourseName()).isEqualTo(expectedExam.getCourseName());

        assertThat(actualExam.isTestExam()).isFalse();
        assertThat(actualExam.getRandomizeExerciseOrder()).isTrue();

        /// For the times we need to give a slight tolerance because Artemis truncates the times to 6 sub-second digits
        assertThat(ChronoUnit.MILLIS.between(actualExam.getVisibleDate(), expectedExam.getVisibleDate())).isLessThan(1);
        assertThat(ChronoUnit.MILLIS.between(actualExam.getStartDate(), expectedExam.getStartDate())).isLessThan(1);
        assertThat(ChronoUnit.MILLIS.between(actualExam.getEndDate(), expectedExam.getEndDate())).isLessThan(1);
        assertThat(ChronoUnit.MILLIS.between(actualExam.getExamStudentReviewStart(), expectedExam.getExamStudentReviewStart())).isLessThan(1);
        assertThat(ChronoUnit.MILLIS.between(actualExam.getExamStudentReviewEnd(), expectedExam.getExamStudentReviewEnd())).isLessThan(1);
        assertThat(ChronoUnit.MILLIS.between(actualExam.getExamSummaryPublicationDate(), expectedExam.getExamSummaryPublicationDate())).isLessThan(1);

        assertThat(actualExam.getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateAndGetExam_asInstructor_returnsBody() throws Exception {
        Exam exam = validExamWithCustomFieldValues();

        final URI createdExamURI = request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.CREATED);

        /// GETS the "/api/exam/courses/{course-id}/exams/{exam-id}" endpoint
        final Exam receivedExam = request.get(String.valueOf(createdExamURI), HttpStatus.OK, Exam.class);

        checkCustomFieldValuesExamsAreEffectivelyEqual(receivedExam, exam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamExaminer() throws Exception {
        final var examinerName = "Prof. Dr. Stephan Krusche";
        Exam exam = ExamFactory.generateExam(course1);
        exam.setExaminer(examinerName);
        final URI receivedExamURI = request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.CREATED);

        /// GETS the "/api/exam/courses/{course-id}/exams/{exam-id}" endpoint
        final Exam requestedExam = request.get(String.valueOf(receivedExamURI), HttpStatus.OK, Exam.class);
        assertThat(requestedExam.getExaminer()).isEqualTo(examinerName);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateAndGetExamWithNullCorrectionRounds() throws Exception {
        testCreateAndGetExamWithCorrectionRoundsAndExpectedCreationStatus(false, null, 1, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateAndGetExamWithValidCorrectionRounds() throws Exception {
        // Real exams must have either 1 or 2 correction rounds; test exams must have exactly 0 correction rounds
        // Real exam - correction rounds = 1
        testCreateAndGetExamWithCorrectionRoundsAndExpectedCreationStatus(false, 1, 1, HttpStatus.CREATED);

        // Real exam - correction rounds = 2
        testCreateAndGetExamWithCorrectionRoundsAndExpectedCreationStatus(false, 2, 2, HttpStatus.CREATED);

        // Test exam - correction rounds = 0
        testCreateAndGetExamWithCorrectionRoundsAndExpectedCreationStatus(true, 0, 0, HttpStatus.CREATED);
    }

    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, -3, -2, -1, 0, 3, 4, 5, 1 << 20, Integer.MAX_VALUE })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateRealExamWithInvalidCorrectionRounds(Integer plannedCorrectionRounds) throws Exception {
        // Real exams must have either 1 or 2 correction rounds
        testCreateAndGetExamWithCorrectionRoundsAndExpectedCreationStatus(false, plannedCorrectionRounds, plannedCorrectionRounds, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, -3, -2, -1, 1, 2, 3, 4, 5, 1 << 20, Integer.MAX_VALUE })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestExamWithInvalidCorrectionRounds(Integer plannedCorrectionRounds) throws Exception {
        // Test exams must have exactly 0 correction rounds
        testCreateAndGetExamWithCorrectionRoundsAndExpectedCreationStatus(true, plannedCorrectionRounds, plannedCorrectionRounds, HttpStatus.BAD_REQUEST);
    }

    void testCreateAndGetExamWithCorrectionRoundsAndExpectedCreationStatus(boolean isTestExam, Integer plannedCorrectionRounds, int actualCorrectionRounds,
            HttpStatus expectedStatus) throws Exception {
        final Exam exam = isTestExam ? ExamFactory.generateTestExam(course1) : ExamFactory.generateExam(course1);
        exam.setNumberOfCorrectionRoundsInExam(plannedCorrectionRounds);
        assertThat(exam.getNumberOfCorrectionRoundsInExam()).isEqualTo(actualCorrectionRounds);
        final URI receivedExamURI = request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), expectedStatus);

        if (expectedStatus == HttpStatus.CREATED) {
            /// GETS the "/api/exam/courses/{course-id}/exams/{exam-id}" endpoint
            final Exam receivedExam = request.get(String.valueOf(receivedExamURI), HttpStatus.OK, Exam.class);
            assertThat(receivedExam.getNumberOfCorrectionRoundsInExam()).isEqualTo(actualCorrectionRounds);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetCourseNameNullName() throws Exception {
        final Exam exam = ExamFactory.generateExam(course1);
        exam.setCourseName(null);
        final URI receivedExamURI = request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.CREATED);

        /// GETS the "/api/exam/courses/{course-id}/exams/{exam-id}" endpoint
        Exam receivedExam = request.get(String.valueOf(receivedExamURI), HttpStatus.OK, Exam.class);
        assertThat(receivedExam.getCourseName()).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 60, 5 * 60, 5 * 24 * 60 * 60 })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIsVisibleToStudents(int beforeSeconds) throws Exception {
        final Exam exam = ExamFactory.generateExam(course1);
        exam.setVisibleDate(ZonedDateTime.now().minusSeconds(beforeSeconds));

        final URI receivedExamURI = request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.CREATED);

        /// GETS the "/api/exam/courses/{course-id}/exams/{exam-id}" endpoint
        final Exam receivedExam = request.get(String.valueOf(receivedExamURI), HttpStatus.OK, Exam.class);
        assertThat(receivedExam.isVisibleToStudents()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = { 15, 60, 5 * 60, 3 * 60 * 60, 5 * 24 * 60 * 60 })
    /* We don't want to test with too small values, or else the test might become flaky */
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testIsNotVisibleToStudents(int afterSeconds) throws Exception {
        final Exam exam = ExamFactory.generateExam(course1);
        final var visibleDate = ZonedDateTime.now().plusSeconds(afterSeconds);
        exam.setVisibleDate(visibleDate);

        final int workingTimeSeconds = 1000;
        exam.setStartDate(visibleDate.plusMinutes(5));
        exam.setEndDate(visibleDate.plusMinutes(5).plusSeconds(workingTimeSeconds));
        exam.setWorkingTime(workingTimeSeconds);

        final URI receivedExamURI = request.post("/api/exam/courses/" + course1.getId() + "/exams", ExamUpdateDTO.of(exam), HttpStatus.CREATED);

        /// GETS the "/api/exam/courses/{course-id}/exams/{exam-id}" endpoint
        final Exam receivedExam = request.get(String.valueOf(receivedExamURI), HttpStatus.OK, Exam.class);
        assertThat(receivedExam.isVisibleToStudents()).isFalse();
    }

    @Nested
    class IsAfterLastStudentExamEndedTest {

        private ZonedDateTime timeExamStart;

        private int examWorkingTime;

        private ZonedDateTime timeExamEnd;

        @BeforeEach
        void initializeTimes() {
            timeExamStart = ZonedDateTime.now().minusMinutes(60);
            examWorkingTime = 3000;  // 50 minutes
            timeExamEnd = timeExamStart.plusSeconds(examWorkingTime);
        }

        @Test
        void noParticipations() {
            // should default to regular end time
            Exam noParticipationsExam = examUtilService.addExam(course1);
            noParticipationsExam.setStartDate(timeExamStart);
            noParticipationsExam.setEndDate(timeExamEnd);
            noParticipationsExam.setWorkingTime(examWorkingTime);

            assertThat(noParticipationsExam.isAfterLatestStudentExamEnd()).isTrue();
        }

        @Test
        void noStudentHasTimeAdvantage() {
            Exam regularExam = examUtilService.addExam(course1);
            regularExam.setStartDate(timeExamStart);
            regularExam.setEndDate(timeExamEnd);
            regularExam.setWorkingTime(examWorkingTime);

            var studentExam1 = examUtilService.addStudentExamWithUser(regularExam, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
            var studentExam2 = examUtilService.addStudentExamWithUser(regularExam, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
            var studentExam3 = examUtilService.addStudentExamWithUser(regularExam, userUtilService.getUserByLogin(TEST_PREFIX + "student3"));
            var studentExam4 = examUtilService.addStudentExamWithUser(regularExam, userUtilService.getUserByLogin(TEST_PREFIX + "student4"));
            regularExam.addStudentExam(studentExam1);
            regularExam.addStudentExam(studentExam2);
            regularExam.addStudentExam(studentExam3);
            regularExam.addStudentExam(studentExam4);

            assertThat(regularExam.isAfterLatestStudentExamEnd()).isTrue();
        }

        @Test
        void someStudentsHaveTimeAdvantageNotEnoughToTriggerThreshold() {
            Exam exam = examUtilService.addExam(course1);
            exam.setStartDate(timeExamStart);
            exam.setEndDate(timeExamEnd);
            exam.setWorkingTime(examWorkingTime);

            var studentExam1 = examUtilService.addStudentExamWithUser(exam, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
            var studentExam2 = examUtilService.addStudentExamWithUserAndWorkingTime(exam, userUtilService.getUserByLogin(TEST_PREFIX + "student2"), examWorkingTime + 120);
            var studentExam3 = examUtilService.addStudentExamWithUser(exam, userUtilService.getUserByLogin(TEST_PREFIX + "student3"));
            var studentExam4 = examUtilService.addStudentExamWithUserAndWorkingTime(exam, userUtilService.getUserByLogin(TEST_PREFIX + "student4"), examWorkingTime + 240);
            exam.addStudentExam(studentExam1);
            exam.addStudentExam(studentExam2);
            exam.addStudentExam(studentExam3);
            exam.addStudentExam(studentExam4);

            assertThat(exam.isAfterLatestStudentExamEnd()).isTrue();
        }

        @Test
        void someStudentsHaveTimeAdvantageEnoughToTriggerThreshold() {
            Exam exam = examUtilService.addExam(course1);
            exam.setStartDate(timeExamStart);
            exam.setEndDate(timeExamEnd);
            exam.setWorkingTime(examWorkingTime);

            var studentExam1 = examUtilService.addStudentExamWithUser(exam, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
            var studentExam2 = examUtilService.addStudentExamWithUserAndWorkingTime(exam, userUtilService.getUserByLogin(TEST_PREFIX + "student2"), examWorkingTime + 120);
            var studentExam3 = examUtilService.addStudentExamWithUserAndWorkingTime(exam, userUtilService.getUserByLogin(TEST_PREFIX + "student3"), examWorkingTime + 300);
            var studentExam4 = examUtilService.addStudentExamWithUserAndWorkingTime(exam, userUtilService.getUserByLogin(TEST_PREFIX + "student4"), examWorkingTime + 1500);
            exam.addStudentExam(studentExam1);
            exam.addStudentExam(studentExam2);
            exam.addStudentExam(studentExam3);
            exam.addStudentExam(studentExam4);

            assertThat(exam.isAfterLatestStudentExamEnd()).isFalse();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testHasExamArchivePathBranchesAsInstructor() throws Exception {
        testHasExamArchivePathExpectStatus(HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testHasExamArchivePathBranchesAsTeachingAssistant() throws Exception {
        testHasExamArchivePathExpectStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testHasExamArchivePathBranchesAsStudent() throws Exception {
        testHasExamArchivePathExpectStatus(HttpStatus.FORBIDDEN);
    }

    void testHasExamArchivePathExpectStatus(HttpStatus expectedStatus) throws Exception {
        testHasExamArchivePath(null, false, expectedStatus);
        testHasExamArchivePath("", false, expectedStatus);
        testHasExamArchivePath("Path", true, expectedStatus);
        testHasExamArchivePath("Very long exam archive path", true, expectedStatus);
    }

    void testHasExamArchivePath(String examArchivePath, boolean expectExamArchivePath, HttpStatus expectedStatus) throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        exam.setExamArchivePath(examArchivePath);

        examRepository.save(exam);

        final ExamDTO receivedExam = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId(), expectedStatus, ExamDTO.class);
        if (expectedStatus == HttpStatus.OK) {
            // The archive button (also on the plain-get re-fetch path) reads examArchivePath to compute hasArchive.
            boolean hasArchive = receivedExam.examArchivePath() != null && !receivedExam.examArchivePath().isEmpty();
            assertThat(hasArchive).isEqualTo(expectExamArchivePath);
        }
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

        StudentExam receivedStudentExam = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/own-student-exam", HttpStatus.OK, StudentExam.class);
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
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/own-student-exam", HttpStatus.FORBIDDEN, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRetrieveOwnStudentExam_instructor() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/own-student-exam", HttpStatus.FORBIDDEN, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamForImportWithExercises_successful() throws Exception {
        ExerciseGroup quizGroup = exam2.getExerciseGroups().getFirst();
        QuizExercise quiz = QuizExerciseFactory.generateQuizExerciseForExam(quizGroup);
        QuizExerciseFactory.addAllQuestionTypesToQuizExercise(quiz);
        exerciseRepository.save(quiz);

        ExamWithExerciseGroupsDTO received = request.get("/api/exam/exams/" + exam2.getId(), HttpStatus.OK, ExamWithExerciseGroupsDTO.class);
        assertThat(received.id()).isEqualTo(exam2.getId());
        assertThat(received.title()).isEqualTo(exam2.getTitle());
        assertThat(received.testExam()).isEqualTo(exam2.isTestExam());
        // exam-import.component reads exam.course to decide isImportInSameCourse
        assertThat(received.course()).isNotNull();
        assertThat(received.course().id()).isEqualTo(course1.getId());
        assertThat(received.exerciseGroups()).hasSize(1);
        var group = received.exerciseGroups().getFirst();
        // the import modal renders and the body-builder re-posts group id/title/isMandatory
        assertThat(group.id()).isEqualTo(quizGroup.getId());
        assertThat(group.title()).isEqualTo(quizGroup.getTitle());
        assertThat(group.isMandatory()).isEqualTo(quizGroup.getIsMandatory());
        assertThat(group.exercises()).hasSize(1);
        var receivedExercise = group.exercises().getFirst();
        // convertExerciseGroupsToImportDTO re-posts exactly these fields; the polymorphic type discriminator is
        // load-bearing for the import-exercise-group echo (the exercise deserializes back into the Exercise hierarchy)
        assertThat(receivedExercise.id()).isEqualTo(quiz.getId());
        assertThat(receivedExercise.type()).isEqualTo(ExerciseType.QUIZ);
        assertThat(receivedExercise.title()).isEqualTo(quiz.getTitle());
        assertThat(receivedExercise.maxPoints()).isEqualTo(quiz.getMaxPoints());
        assertThat(receivedExercise.bonusPoints()).isEqualTo(quiz.getBonusPoints());
        // Quiz-question stubs keep length + polymorphic type so the echo round-trips (details are reloaded from source on import)
        assertThat(receivedExercise.quizQuestions()).hasSize(4);
        assertThat(receivedExercise.quizQuestions()).allSatisfy(question -> assertThat(question.type()).isNotBlank());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExerciseGroup_echoesQuizConfigScalars_survivesImport() throws Exception {
        // Reproduces the exercise-group import modal end to end: GET the import fetch, echo the raw exercise-group JSON
        // unchanged to import-exercise-group (exactly what exam-exercise-import.component does), then reload the
        // persisted quiz and assert its configuration was NOT silently reset. Regression guard for
        // QuizExerciseImportService#copyQuizExerciseBasis, which reads randomizeQuestionOrder, allowedNumberOfAttempts,
        // quizMode and duration off the posted skeleton (this echoed body), not off the original source exercise.
        Exam sourceExam = examUtilService.addExamWithExerciseGroup(course1, true);
        ExerciseGroup quizGroup = sourceExam.getExerciseGroups().getFirst();
        QuizExercise quiz = QuizExerciseFactory.generateQuizExerciseForExam(quizGroup);
        // Deliberately NON-DEFAULT quiz config (factory defaults are randomizeQuestionOrder=true,
        // allowedNumberOfAttempts=1, duration=10, quizMode=SYNCHRONIZED): NON_EMPTY only hides nulls/empties, but using
        // values that differ from every default proves the round-trip actually carried the poster's data instead of
        // coincidentally matching a fallback.
        quiz.setRandomizeQuestionOrder(false);
        quiz.setAllowedNumberOfAttempts(5);
        quiz.setDuration(999);
        quiz.setQuizMode(QuizMode.BATCHED);
        quizExerciseRepository.save(quiz);

        Exam targetExam = examUtilService.addExam(course1);

        ObjectMapper mapper = request.getObjectMapper();
        JsonNode examJson = request.get("/api/exam/exams/" + sourceExam.getId(), HttpStatus.OK, JsonNode.class);
        JsonNode exerciseGroupsJson = examJson.get("exerciseGroups");
        JsonNode fetchedQuiz = exerciseGroupsJson.get(0).get("exercises").get(0);
        // Sanity: the fetched skeleton actually carries the non-default scalars under test (i.e. Finding 1's fix is present).
        assertThat(fetchedQuiz.get("randomizeQuestionOrder").asBoolean()).isFalse();
        assertThat(fetchedQuiz.get("allowedNumberOfAttempts").asInt()).isEqualTo(5);
        assertThat(fetchedQuiz.get("duration").asInt()).isEqualTo(999);
        assertThat(fetchedQuiz.get("quizMode").asText()).isEqualTo("BATCHED");

        ExerciseGroupImportResultDTO importResult = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + targetExam.getId() + "/import-exercise-group",
                mapper.writeValueAsString(exerciseGroupsJson), true, ExerciseGroupImportResultDTO.class, HttpStatus.OK, null, null, null);

        long importedQuizId = importResult.exerciseGroups().getFirst().exercises().getFirst().id();
        QuizExercise importedQuiz = quizExerciseRepository.findByIdElseThrow(importedQuizId);
        assertThat(importedQuiz.isRandomizeQuestionOrder()).isFalse();
        assertThat(importedQuiz.getAllowedNumberOfAttempts()).isEqualTo(5);
        assertThat(importedQuiz.getDuration()).isEqualTo(999);
        assertThat(importedQuiz.getQuizMode()).isEqualTo(QuizMode.BATCHED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor10", roles = "INSTRUCTOR")
    void testGetExamForImportWithExercises_noInstructorAccess() throws Exception {
        request.get("/api/exam/exams/" + exam2.getId(), HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void testGetExamForImportWithExercises_noTutorAccess() throws Exception {
        request.get("/api/exam/exams/" + exam2.getId(), HttpStatus.FORBIDDEN, Exam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetExamForImportWithExercises_noEditorAccess() throws Exception {
        request.get("/api/exam/exams/" + exam2.getId(), HttpStatus.FORBIDDEN, Exam.class);
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
        final var result = request.getSearchResult("/api/exam/exams", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1).containsExactly(exam);
        // Pin the exact fields the exam-import table renders off each paged row: id, title, course.title and testExam.
        Exam foundExam = result.getResultsOnPage().getFirst();
        assertThat(foundExam.getTitle()).isEqualTo(title);
        assertThat(foundExam.isTestExam()).isEqualTo(exam.isTestExam());
        assertThat(foundExam.getCourse()).isNotNull();
        assertThat(foundExam.getCourse().getTitle()).isEqualTo(course1.getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllExamsOnPage_withExercises_asInstructor_returnsExams() throws Exception {
        var newExam = examUtilService.addTestExamWithExerciseGroup(course1, true);
        var searchTerm = "A very distinct title that should only ever exist once in the database";
        newExam.setTitle(searchTerm);
        examRepository.save(newExam);
        final SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(searchTerm);
        final var result = request.getSearchResult("/api/exam/exams?withExercises=true", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        List<Exam> foundExams = result.getResultsOnPage();
        assertThat(foundExams).hasSize(1).containsExactly(newExam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllExamsOnPage_withoutExercisesAndExamsNotLinkedToCourse_asInstructor_returnsNoExams() throws Exception {
        var title = "Another fancy exam search title for the exam which is not used somewhere else";
        Course course = courseUtilService.addEmptyCourse();
        var exam = examUtilService.addExamWithExerciseGroup(course, true);
        exam.setTitle(title);
        examRepository.save(exam);
        final SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(title);
        final var result = request.getSearchResult("/api/exam/exams", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAllExamsOnPage_withoutExercisesAndExamsNotLinkedToCourse_asAdmin_returnsExams() throws Exception {
        var title = "Yet another 3rd exam search title for the exam which is not used somewhere else";
        Course course = courseUtilService.addEmptyCourse();
        var exam = examUtilService.addExamWithExerciseGroup(course, true);
        exam.setTitle(title);
        examRepository.save(exam);
        final SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureSearch(title);
        final var result = request.getSearchResult("/api/exam/exams", HttpStatus.OK, Exam.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1).contains(exam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetAllExamsOnPage_asEditor_failsWithForbidden() throws Exception {
        // Creating and importing exams is instructor-only, and this import-source endpoint is scoped to instructor
        // courses, so editors must not be able to call it (otherwise they would always get an empty result).
        final SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/exam/exams", HttpStatus.FORBIDDEN, Exam.class, pageableSearchUtilService.searchMapping(search));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void testGetAllExamsOnPage_asTutor_failsWithForbidden() throws Exception {
        final SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/exam/exams", HttpStatus.FORBIDDEN, Exam.class, pageableSearchUtilService.searchMapping(search));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAllExamsOnPage_asStudent_failsWithForbidden() throws Exception {
        final SearchTermPageableSearchDTO<String> search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/exam/exams", HttpStatus.FORBIDDEN, Exam.class, pageableSearchUtilService.searchMapping(search));
    }
    // </editor-fold>

    // <editor-fold desc="Import">
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testImportExamWithExercises_asStudent_failsWithForbidden() throws Exception {
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exam-import", ExamImportDTO.of(exam1, course1.getId()), HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void testImportExamWithExercises_asTutor_failsWithForbidden() throws Exception {
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exam-import", ExamImportDTO.of(exam1, course1.getId()), HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_failsWithDateConflict() throws Exception {
        // Visible Date after Started Date
        final Exam examA = ExamFactory.generateExam(course1);
        examA.setVisibleDate(ZonedDateTime.now().plusHours(2));
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exam-import", ExamImportDTO.of(examA, course1.getId()), HttpStatus.BAD_REQUEST, null);

        // Started Date after End Date
        final Exam examC = ExamFactory.generateExam(course1);
        examC.setStartDate(ZonedDateTime.now().plusHours(2));
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exam-import", ExamImportDTO.of(examC, course1.getId()), HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_failsWithTitleTooLong() throws Exception {
        final Exam exam = ExamFactory.generateExam(course1);
        exam.setTitle("a".repeat(256)); // Max allowed is 255 characters
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exam-import", ExamImportDTO.of(exam, course1.getId()), HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_failsWithDecimalMaxPoints() throws Exception {
        // A fractional examMaxPoints must be rejected instead of being silently truncated on the import write path.
        final Exam exam = ExamFactory.generateExam(course1);
        final ObjectNode body = request.getObjectMapper().valueToTree(ExamImportDTO.of(exam, course1.getId()));
        body.put("examMaxPoints", 10.5);
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exam-import", body, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_failsWithTextTooLong() throws Exception {
        final Exam exam = ExamFactory.generateExam(course1);
        exam.setStartText("a".repeat(10001)); // Max allowed is 10000 characters
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exam-import", ExamImportDTO.of(exam, course1.getId()), HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_failsWithDateConflictTestExam() throws Exception {
        // Working Time larger than Working window
        final Exam examA = ExamFactory.generateTestExam(course1);
        examA.setWorkingTime(3 * 60 * 60);
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exam-import", ExamImportDTO.of(examA, course1.getId()), HttpStatus.BAD_REQUEST, null);

        // Working Time larger than Working window
        final Exam examB = ExamFactory.generateTestExam(course1);
        examB.setWorkingTime(0);
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exam-import", ExamImportDTO.of(examB, course1.getId()), HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_failsWithPointConflict() throws Exception {
        final Exam examA = ExamFactory.generateExam(course1);
        examA.setExamMaxPoints(-5);
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exam-import", ExamImportDTO.of(examA, course1.getId()), HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_failsWithCorrectionRoundConflict() throws Exception {
        // Correction round <= 0
        final Exam examA = ExamFactory.generateExam(course1);
        examA.setNumberOfCorrectionRoundsInExam(0);
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exam-import", ExamImportDTO.of(examA, course1.getId()), HttpStatus.BAD_REQUEST, null);

        // Correction round >= 2
        final Exam examB = ExamFactory.generateExam(course1);
        examB.setNumberOfCorrectionRoundsInExam(3);
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exam-import", ExamImportDTO.of(examB, course1.getId()), HttpStatus.BAD_REQUEST, null);

        // Correction round != 0 for test exam
        final Exam examC = ExamFactory.generateTestExam(course1);
        examC.setNumberOfCorrectionRoundsInExam(1);
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exam-import", ExamImportDTO.of(examC, course1.getId()), HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_successfulWithoutExercises() throws Exception {
        Exam exam = examUtilService.addExam(course1);

        exam.setChannelName("channelname-imported");
        ExamImportDTO importDTO = ExamImportDTO.of(exam, course1.getId());
        // The import response carries only the imported exam's id/title; re-fetch the persisted exam to assert its state.
        Long importedExamId = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exam-import", importDTO, ExamImportResultDTO.class, HttpStatus.CREATED).exam()
                .id();
        final Exam received = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(importedExamId);
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
        assertThat(received.getVisibleDate()).isCloseTo(exam.getVisibleDate(), within(1, ChronoUnit.SECONDS));
        exam.setStartDate(ZonedDateTime.ofInstant(exam.getStartDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        received.setStartDate(ZonedDateTime.ofInstant(received.getStartDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(received.getStartDate()).isCloseTo(exam.getStartDate(), within(1, ChronoUnit.SECONDS));
        exam.setEndDate(ZonedDateTime.ofInstant(exam.getEndDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        received.setEndDate(ZonedDateTime.ofInstant(received.getEndDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
        assertThat(received.getEndDate()).isCloseTo(exam.getEndDate(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_successfulWithExercises() throws Exception {
        Exam exam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        exam.setChannelName("testchannelname-imported");
        ExamImportDTO importDTO2 = ExamImportDTO.of(exam, course1.getId());
        Long importedExamId = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exam-import", importDTO2, ExamImportResultDTO.class, CREATED).exam().id();
        final Exam received = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(importedExamId);
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

        // Verify that content fields from the source exercises are preserved during import (not silently dropped)
        for (ExerciseGroup group : exerciseGroups) {
            for (Exercise importedExercise : group.getExercises()) {
                // Base fields that should be copied from the template exercise
                assertThat(importedExercise.getDifficulty()).as("difficulty must be preserved for " + importedExercise.getTitle()).isEqualTo(DifficultyLevel.MEDIUM);
                // Quiz maxPoints is overridden by QuizExerciseService.save() to equal the sum of question points
                if (!(importedExercise instanceof QuizExercise)) {
                    assertThat(importedExercise.getMaxPoints()).as("maxPoints must be preserved").isEqualTo(5.0);
                }

                switch (importedExercise) {
                    case ModelingExercise modeling -> {
                        assertThat(modeling.getProblemStatement()).as("problemStatement must be preserved").isEqualTo("Exam Problem Statement");
                        assertThat(modeling.getDiagramType()).as("diagramType must be preserved").isEqualTo(DiagramType.ClassDiagram);
                        assertThat(modeling.getExampleSolutionModel()).as("exampleSolutionModel must be preserved").isEqualTo("This is my example solution model");
                        assertThat(modeling.getExampleSolutionExplanation()).as("exampleSolutionExplanation must be preserved").isEqualTo("This is my example solution model");
                    }
                    case TextExercise text -> {
                        assertThat(text.getProblemStatement()).as("problemStatement must be preserved").isEqualTo("Exam Problem Statement");
                        assertThat(text.getExampleSolution()).as("exampleSolution must be preserved").isEqualTo("This is my example solution");
                    }
                    case FileUploadExercise fileUpload -> {
                        assertThat(fileUpload.getProblemStatement()).as("problemStatement must be preserved").isEqualTo("Exam Problem Statement");
                        assertThat(fileUpload.getFilePattern()).as("filePattern must be preserved").isEqualTo("png");
                    }
                    case QuizExercise quiz -> {
                        assertThat(quiz.isRandomizeQuestionOrder()).as("randomizeQuestionOrder must be preserved").isTrue();
                        assertThat(quiz.getAllowedNumberOfAttempts()).as("allowedNumberOfAttempts must be preserved").isEqualTo(1);
                        assertThat(quiz.getDuration()).as("duration must be preserved").isEqualTo(10);
                        // Quiz batches should NOT be imported for exam exercises (exam controls timing).
                        // Re-fetch with quizBatches eagerly loaded: the exam re-fetch above does not join them, so accessing
                        // the lazy collection directly on `quiz` would throw LazyInitializationException outside a session.
                        QuizExercise quizWithBatches = quizExerciseRepository.findWithEagerBatchesById(quiz.getId()).orElseThrow();
                        assertThat(quizWithBatches.getQuizBatches()).as("quiz batches must not be imported for exam exercises").isNullOrEmpty();
                    }
                    default -> {
                        // no additional assertions for other types
                    }
                }
            }
        }

        // Grading criteria and assessment type were previously unverified on this path. Grading criteria are lazy and
        // not serialized in the response, so reload each imported exercise and assert they were preserved from the source.
        for (ExerciseGroup group : exerciseGroups) {
            for (Exercise importedExercise : group.getExercises()) {
                Exercise reloaded = reloadWithGradingCriteria(importedExercise);
                if (!(reloaded instanceof QuizExercise)) {
                    assertThat(reloaded.getGradingCriteria()).as("grading criteria preserved for " + reloaded.getTitle()).isNotEmpty();
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExerciseGroupsToExistingExam_preservesAllContent() throws Exception {
        // Regression guard for the import-exercise-group path (which binds full request entities), where a quiz previously
        // failed to import because of a detached QuizPointStatistic. Verify every exercise type preserves its content here.
        Exam sourceExam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        Exam targetExam = examUtilService.addExam(course1);
        examUtilService.addExamChannel(targetExam, "import-eg-content");

        List<ExerciseGroupDTO> importedGroups = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + targetExam.getId() + "/import-exercise-group",
                sourceExam.getExerciseGroups(), ExerciseGroupImportResultDTO.class, HttpStatus.OK).exerciseGroups();

        // The response carries slim exercise summaries, so reload each imported exercise to assert on its content.
        List<Exercise> importedExercises = importedGroups.stream().filter(group -> group.exercises() != null).flatMap(group -> group.exercises().stream())
                .map(exercise -> exerciseRepository.findByIdElseThrow(exercise.id())).toList();
        assertThat(importedExercises).as("all four non-empty exercise groups imported an exercise").hasSize(4);

        Map<ExerciseType, Exercise> sourceByType = sourceExam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream())
                .collect(Collectors.toMap(Exercise::getExerciseType, exercise -> exercise));
        for (Exercise imported : importedExercises) {
            Exercise source = sourceByType.get(imported.getExerciseType());
            ImportedExerciseAssertions.assertContentPreserved(reloadWithGradingCriteria(source), reloadWithGradingCriteria(imported));
        }
    }

    /**
     * Reloads an exercise from the database with its grading criteria (and type-specific associations) initialized, so
     * the shared content assertions can inspect the lazy collections that the REST response does not serialize.
     */
    private Exercise reloadWithGradingCriteria(Exercise exercise) {
        return switch (exercise) {
            case TextExercise text -> textExerciseRepository.findByIdWithExampleSubmissionsAndResultsAndGradingCriteriaElseThrow(text.getId());
            case ModelingExercise modeling -> modelingExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(modeling.getId());
            case FileUploadExercise fileUpload -> fileUploadExerciseRepository.findWithGradingCriteriaByIdElseThrow(fileUpload.getId());
            case QuizExercise quiz -> quizExerciseRepository.findByIdWithQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaElseThrow(quiz.getId());
            default -> exercise;
        };
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_skipsFailedExerciseAndImportsTheRest() throws Exception {
        // Source exam has non-empty groups modelling, text, file upload, quiz (plus one empty group that is filtered out).
        // We make the quiz import fail; the other three exercises are imported. The import must still succeed overall
        // (no 5xx) and only skip the quiz, reporting it via the "skipped" list in the response body.
        Exam exam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        exam.setChannelName("partial-import-channel");

        // Make the quiz import fail by removing its source exercise after building the import payload: the quiz import
        // then cannot resolve the source exercise and yields Optional.empty (a real "source exercise no longer available"
        // failure). This deliberately avoids @MockitoSpyBean, which an ArchUnit rule forbids on concrete integration test
        // classes (it would force an extra Spring context). It also exercises the empty-result skip path directly.
        QuizExercise sourceQuiz = (QuizExercise) exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).filter(QuizExercise.class::isInstance)
                .findFirst().orElseThrow();
        String quizTitle = sourceQuiz.getTitle();
        ExamImportDTO importDTO = ExamImportDTO.of(exam, course1.getId());
        exerciseRepository.deleteById(sourceQuiz.getId());

        ExamImportResultDTO result = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exam-import", importDTO, ExamImportResultDTO.class,
                HttpStatus.CREATED);

        // The quiz could not resolve its (deleted) source exercise, so it is reported as skipped (cleanly not imported), not incomplete.
        assertThat(result.skippedExercises()).as("the skipped quiz title must be reported").contains(quizTitle);
        // No exercise failed partway, so the incomplete list is empty and omitted from the response (DTO uses @JsonInclude(NON_EMPTY)).
        assertThat(result.incompleteExercises()).as("no exercise must be reported as incomplete").isNullOrEmpty();

        // Re-fetch the created exam (its id is in the response body) and verify the persisted state.
        Exam importedExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(result.exam().id());

        // All exercises except the failing quiz were imported (modelling, text, file upload).
        long importedExerciseCount = importedExam.getExerciseGroups().stream().mapToLong(group -> group.getExercises().size()).sum();
        assertThat(importedExerciseCount).isEqualTo(3);
        // Lock the behavior to the intended failure path: the quiz (and only the quiz) was skipped.
        assertThat(importedExam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream())).as("the quiz must be the skipped exercise")
                .noneMatch(QuizExercise.class::isInstance);
        // The quiz group ended up empty (its only exercise was skipped) but is intentionally KEPT, not deleted: the failure
        // is reported via the skipped list, and an empty group is rejected later by validateForStudentExamGeneration. All
        // four imported groups are retained and the ordered exercise-group list stays intact (no null element).
        assertThat(importedExam.getExerciseGroups()).hasSize(4);
        assertThat(importedExam.getExerciseGroups()).as("the ordered exercise-group list must not contain a null").doesNotContainNull();
        assertThat(importedExam.getExerciseGroups()).filteredOn(group -> group.getExercises().isEmpty()).as("the emptied quiz group is retained").hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_retainsEmptiedMiddleExerciseGroup() throws Exception {
        // When a NON-LAST exercise group's exercises all fail to import, the (now empty) group is RETAINED in place rather
        // than deleted. The import must not mutate the ordered exercise-group list: all four imported groups stay, in order,
        // with no null element. (Deleting the emptied middle group used to leave a gap in the @OrderColumn
        // 'exercise_group_order' that Hibernate reloaded as a null element, corrupting the exam.)
        Exam exam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        exam.setChannelName("order-column-channel");

        // Fail the TEXT exercise (a middle group: modelling, text, file upload, quiz) by deleting its source exercise, so
        // its group is emptied in the middle of the ordered exercise-group list.
        TextExercise sourceText = (TextExercise) exam.getExerciseGroups().stream().flatMap(group -> group.getExercises().stream()).filter(TextExercise.class::isInstance)
                .findFirst().orElseThrow();
        String textTitle = sourceText.getTitle();
        ExamImportDTO importDTO = ExamImportDTO.of(exam, course1.getId());
        exerciseRepository.deleteById(sourceText.getId());

        ExamImportResultDTO result = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exam-import", importDTO, ExamImportResultDTO.class,
                HttpStatus.CREATED);
        assertThat(result.skippedExercises()).as("the skipped text exercise must be reported").contains(textTitle);

        // Re-fetch the created exam: the emptied middle group is retained and the ordered list contains no null element.
        Exam importedExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(result.exam().id());
        assertThat(importedExam.getExerciseGroups()).as("all imported groups are retained, including the emptied middle one").hasSize(4);
        assertThat(importedExam.getExerciseGroups()).as("the ordered exercise-group list must not contain a null").doesNotContainNull();
        assertThat(importedExam.getExerciseGroups()).filteredOn(group -> group.getExercises().isEmpty()).as("the emptied text group is retained").hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_reportsProgressOverWebsocket() throws Exception {
        Exam exam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndEmptyGroup(course1);
        exam.setChannelName("ws-progress-channel");
        ExamImportDTO importDTO = ExamImportDTO.of(exam, course1.getId());
        String importId = "test-import-id";

        // When a client supplies an importId, the importing user receives live progress on an import-specific websocket channel.
        request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exam-import?importId=" + importId, importDTO, ExamImportResultDTO.class, CREATED);

        verify(websocketMessagingService, atLeastOnce()).sendMessageToUser(eq(TEST_PREFIX + "instructor1"), eq("/topic/exam-import/" + importId), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithQuizExercise_successfulWithQuestions() throws Exception {
        Exam exam = examUtilService.addExamWithExerciseGroup(course1, false);
        ExerciseGroup quizGroup = exam.getExerciseGroups().getFirst();
        QuizExercise quiz = QuizExerciseFactory.generateQuizExerciseForExam(quizGroup);
        quiz.addQuestion(QuizExerciseFactory.createMultipleChoiceQuestionWithAllTypesOfAnswerOptions());
        quiz.addQuestion(QuizExerciseFactory.createShortAnswerQuestionWithRealisticText());
        quiz.addQuestion(QuizExerciseFactory.createSingleChoiceQuestion());
        quiz.addQuestion(QuizExerciseFactory.createDragAndDropQuestion());
        quizGroup.addExercise(quiz);
        exerciseRepository.save(quiz);

        ExamImportDTO quizImportDTO = ExamImportDTO.of(exam, course1.getId());
        Long importedExamId = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exam-import", quizImportDTO, ExamImportResultDTO.class, CREATED).exam().id();
        Exam received = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(importedExamId);
        assertThat(received.getExerciseGroups()).hasSize(1);

        ExerciseGroup receivedGroup = received.getExerciseGroups().getFirst();
        assertThat(receivedGroup.getExercises()).hasSize(1);
        QuizExercise exercise = (QuizExercise) receivedGroup.getExercises().iterator().next();

        exercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(exercise.getId());
        // Quiz questions should get imported into the exam
        assertThat(exercise.getQuizQuestions()).hasSize(4);
    }

    @Test
    @Disabled("Test requires actual LocalCI implementation since we converted LocalVC from mock to a real service.")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_successfulWithImportToOtherCourse() throws Exception {
        setupMocks();
        Exam exam = examUtilService.addExamWithModellingAndTextAndFileUploadAndQuizAndProgramming(course2);
        exam.setChannelName("testchannelname");

        ExamImportDTO otherCourseImportDTO = ExamImportDTO.of(exam, course1.getId());
        Long importedExamId = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exam-import", otherCourseImportDTO, ExamImportResultDTO.class, CREATED).exam()
                .id();
        final Exam received = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(importedExamId);
        assertThat(received.getExerciseGroups()).hasSize(5);

        for (int i = 0; i <= 4; i++) {
            Exercise expected = exam.getExerciseGroups().get(i).getExercises().stream().findFirst().orElseThrow();
            Exercise exerciseReceived = received.getExerciseGroups().get(i).getExercises().stream().findFirst().orElseThrow();
            assertThat(exerciseReceived.getExerciseGroup()).isNotEqualTo(expected.getExerciseGroup());
            assertThat(exerciseReceived.getTitle()).isEqualTo(expected.getTitle());
            assertThat(exerciseReceived.getId()).isNotEqualTo(expected.getId());
        }
        Exercise importedProgrammingExercise = received.getExerciseGroups().get(4).getExercises().iterator().next();
        ProgrammingExercise importedExerciseWithAllData = programmingExerciseRepository
                .findByIdWithEagerBuildConfigTestCasesStaticCodeAnalysisCategoriesAndTemplateAndSolutionParticipationsAndAuxReposAndBuildConfigAndGradingCriteria(
                        importedProgrammingExercise.getId())
                .orElseThrow();
        assertThat(importedExerciseWithAllData.getGradingCriteria()).hasSize(2);
    }

    private void setupMocks() {
        doReturn(null).when(continuousIntegrationService).checkIfProjectExists(anyString(), anyString());
        doNothing().when(continuousIntegrationService).createProjectForExercise(any(ProgrammingExercise.class));
        doReturn("build plan").when(continuousIntegrationService).copyBuildPlan(any(ProgrammingExercise.class), anyString(), any(ProgrammingExercise.class), anyString(),
                anyString(), anyBoolean());
        doNothing().when(continuousIntegrationService).updatePlanRepository(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        doNothing().when(continuousIntegrationService).enablePlan(anyString(), anyString());
        doNothing().when(continuousIntegrationTriggerService).triggerBuild(any());
    }
    // </editor-fold>

    // <editor-fold desc="Plagiarism">
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExercisesWithPotentialPlagiarismAsTutor_forbidden() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/exercises-with-potential-plagiarism", HttpStatus.FORBIDDEN, List.class);
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
        request.getSet("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.FORBIDDEN, SuspiciousExamSessionsDTO.class, params);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExercisesWithPotentialPlagiarismAsInstructorNotInCourse_forbidden() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course);

        request.get("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/exercises-with-potential-plagiarism", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetSuspiciousSessionsAsInstructorNotInCourse_forbidden() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("differentStudentExamsSameIPAddress", "true");
        params.add("differentStudentExamsSameBrowserFingerprint", "true");
        params.add("sameStudentExamDifferentIPAddresses", "false");
        params.add("sameStudentExamDifferentBrowserFingerprints", "false");
        params.add("ipOutsideOfRange", "false");

        request.getSet("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/suspicious-sessions", HttpStatus.FORBIDDEN, SuspiciousExamSessionsDTO.class, params);
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
                "/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/exercises-with-potential-plagiarism", HttpStatus.OK,
                ExerciseForPlagiarismCasesOverviewDTO.class);
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
        Set<SuspiciousExamSessionsDTO> suspiciousSessionTuples = request.getSet("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions",
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
        request.getSet("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.BAD_REQUEST, SuspiciousExamSessionsDTO.class,
                params);
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
        var suspiciousSessions = request.getSet("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.OK,
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
        suspiciousSessions = request.getSet("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.OK,
                SuspiciousExamSessionsDTO.class, params);
        assertThat(suspiciousSessions).hasSize(1);
        var suspiciousSessionTuple = suspiciousSessions.stream().findFirst().orElseThrow();
        assertThat(suspiciousSessionTuple.examSessions()).hasSize(2);
        suspiciousSessionTuple.examSessions().forEach(
                suspiciousSessionDTO -> assertThat(suspiciousSessionDTO.suspiciousReasons()).containsExactlyInAnyOrder(SuspiciousSessionReason.IP_ADDRESS_OUTSIDE_OF_RANGE));

        // test with subnet that contains both ips
        params.remove("ipSubnet");
        params.add("ipSubnet", subnetIncludingBothAddresses);
        suspiciousSessions = request.getSet("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.OK,
                SuspiciousExamSessionsDTO.class, params);
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
        assertThat(
                request.getSet("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.OK, SuspiciousExamSessionsDTO.class, params))
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
        Set<SuspiciousExamSessionsDTO> suspiciousSessionTuples = request.getSet("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions",
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
        var suspiciousSessions = request.getSet("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/suspicious-sessions", HttpStatus.OK,
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExamSidebarDataForRealExams() throws Exception {
        Course course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        Exam exam = examUtilService.addExam(course);
        Exam testExam = examUtilService.addTestExam(course);
        StudentExam studentExam1 = examUtilService.addStudentExamWithUser(exam, student1);
        examUtilService.addStudentExamWithUser(testExam, student1);
        Set<ExamSidebarDataDTO> examSidebarData = request.getSet("/api/exam/courses/" + course.getId() + "/real-exams-sidebar-data", HttpStatus.OK, ExamSidebarDataDTO.class);
        assertThat(examSidebarData).hasSize(1);
        ExamSidebarDataDTO element = examSidebarData.iterator().next();
        assertThat(element.id()).isEqualTo(exam.getId());
        assertThat(element.title()).isEqualTo(exam.getTitle());
        assertThat(element.workingTime()).isEqualTo(studentExam1.getWorkingTime());
        assertThat(element.startDate().withZoneSameInstant(ZoneId.systemDefault())).isCloseTo(exam.getStartDate(), within(1, ChronoUnit.SECONDS));
    }
}
