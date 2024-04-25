package de.tum.in.www1.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseTestService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreScheduleService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.ExamPrepareExercisesTestUtil;

class ProgrammingExamIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "programmingexamtest";

    @Autowired
    private ExerciseRepository exerciseRepo;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Course course1;

    private Exam exam1;

    private static final int NUMBER_OF_STUDENTS = 2;

    private static final int NUMBER_OF_TUTORS = 1;

    private User student1;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, 0, 1);

        course1 = courseUtilService.addEmptyCourse();
        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        exam1 = examUtilService.addExam(course1);

        gitlabRequestMockProvider.enableMockingOfRequests();

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 200;
        participantScoreScheduleService.activate();
    }

    @AfterEach
    void tearDown() throws Exception {
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
        if (programmingExerciseTestService.exerciseRepo != null) {
            programmingExerciseTestService.tearDown();
        }

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 500;
        participantScoreScheduleService.shutdown();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_rescheduleProgramming_titleChanged_shouldNotReschedule() throws Exception {
        var programmingEx = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        var examWithProgrammingEx = programmingEx.getExerciseGroup().getExam();
        examWithProgrammingEx.setTitle("New title");

        request.put("/api/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);

        verify(instanceMessageSendService, never()).sendProgrammingExerciseSchedule(programmingEx.getId());
        verify(instanceMessageSendService, never()).sendRescheduleAllStudentExams(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_rescheduleProgramming_changeDateSubSecondPrecision_shouldNotReschedule() throws Exception {
        var programmingEx = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        var examWithProgrammingEx = programmingEx.getExerciseGroup().getExam();

        ZonedDateTime visibleDate = examWithProgrammingEx.getVisibleDate();
        ZonedDateTime startDate = examWithProgrammingEx.getStartDate();
        ZonedDateTime endDate = examWithProgrammingEx.getEndDate();
        examUtilService.setVisibleStartAndEndDateOfExam(examWithProgrammingEx, visibleDate.plusNanos(1), startDate.plusNanos(1), endDate.plusNanos(1));

        request.put("/api/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);

        verify(instanceMessageSendService, never()).sendProgrammingExerciseSchedule(programmingEx.getId());
        verify(instanceMessageSendService, never()).sendRescheduleAllStudentExams(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_rescheduleProgramming_visibleAndStartDateChanged_shouldReschedule() throws Exception {
        // Add a programming exercise to the exam and change the dates in order to invoke a rescheduling
        var programmingEx = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        var examWithProgrammingEx = programmingEx.getExerciseGroup().getExam();

        ZonedDateTime visibleDate = examWithProgrammingEx.getVisibleDate();
        ZonedDateTime startDate = examWithProgrammingEx.getStartDate();
        ZonedDateTime endDate = examWithProgrammingEx.getEndDate();
        examUtilService.setVisibleStartAndEndDateOfExam(examWithProgrammingEx, visibleDate.plusSeconds(1), startDate.plusSeconds(1), endDate);

        request.put("/api/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);

        verify(instanceMessageSendService).sendProgrammingExerciseSchedule(programmingEx.getId());
        verify(instanceMessageSendService, never()).sendRescheduleAllStudentExams(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_rescheduleProgramming_visibleDateChanged_shouldReschedule() throws Exception {
        var programmingEx = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        var examWithProgrammingEx = programmingEx.getExerciseGroup().getExam();
        examWithProgrammingEx.setVisibleDate(examWithProgrammingEx.getVisibleDate().plusSeconds(1));

        request.put("/api/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);

        verify(instanceMessageSendService).sendProgrammingExerciseSchedule(programmingEx.getId());
        verify(instanceMessageSendService, never()).sendRescheduleAllStudentExams(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_rescheduleProgramming_startDateChanged_shouldReschedule() throws Exception {
        var programmingEx = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        var examWithProgrammingEx = programmingEx.getExerciseGroup().getExam();

        ZonedDateTime visibleDate = examWithProgrammingEx.getVisibleDate();
        ZonedDateTime startDate = examWithProgrammingEx.getStartDate();
        ZonedDateTime endDate = examWithProgrammingEx.getEndDate();
        examUtilService.setVisibleStartAndEndDateOfExam(examWithProgrammingEx, visibleDate, startDate.plusSeconds(1), endDate);

        request.put("/api/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);

        verify(instanceMessageSendService).sendProgrammingExerciseSchedule(programmingEx.getId());
        verify(instanceMessageSendService, never()).sendRescheduleAllStudentExams(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_rescheduleProgramming_endDateChanged_shouldReschedule() throws Exception {
        var programmingEx = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        var examWithProgrammingEx = programmingEx.getExerciseGroup().getExam();

        ZonedDateTime visibleDate = examWithProgrammingEx.getVisibleDate();
        ZonedDateTime startDate = examWithProgrammingEx.getStartDate();
        ZonedDateTime endDate = examWithProgrammingEx.getEndDate();
        examUtilService.setVisibleStartAndEndDateOfExam(examWithProgrammingEx, visibleDate, startDate, endDate.plusMinutes(1));

        request.put("/api/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);

        verify(instanceMessageSendService, never()).sendProgrammingExerciseSchedule(any());
        verify(instanceMessageSendService).sendRescheduleAllStudentExams(examWithProgrammingEx.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExamWorkingTime_rescheduleProgramming_endDateChanged_shouldReschedule() throws Exception {
        var programmingEx = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        var examWithProgrammingEx = programmingEx.getExerciseGroup().getExam();

        var workingTimeExtensionSeconds = 60;

        request.patch("/api/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams/" + examWithProgrammingEx.getId() + "/working-time", workingTimeExtensionSeconds,
                HttpStatus.OK);

        verify(instanceMessageSendService, never()).sendProgrammingExerciseSchedule(any());
        verify(instanceMessageSendService).sendRescheduleAllStudentExams(examWithProgrammingEx.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void lockAllRepositories() throws Exception {
        Exam exam = examUtilService.addExamWithExerciseGroup(course1, true);
        Exam examWithExerciseGroups = examRepository.findWithExerciseGroupsAndExercisesById(exam.getId()).orElseThrow();
        ExerciseGroup exerciseGroup1 = examWithExerciseGroups.getExerciseGroups().get(0);

        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        programmingExerciseRepository.save(programmingExercise);

        ProgrammingExercise programmingExercise2 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        programmingExerciseRepository.save(programmingExercise2);

        Integer numOfLockedExercises = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/lock-all-repositories", Optional.empty(),
                Integer.class, HttpStatus.OK);

        assertThat(numOfLockedExercises).isEqualTo(2);

        verify(programmingExerciseScheduleService).lockAllStudentRepositories(programmingExercise);
        verify(programmingExerciseScheduleService).lockAllStudentRepositories(programmingExercise2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void lockAllRepositories_noInstructor() throws Exception {
        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/lock-all-repositories", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void unlockAllRepositories_preAuthNoInstructor() throws Exception {
        request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/unlock-all-repositories", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
    }

    // TODO enable again (Issue - https://github.com/ls1intum/Artemis/issues/8305)
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void unlockAllRepositories() throws Exception {
        assertThat(studentExamRepository.findStudentExam(new ProgrammingExercise(), null)).isEmpty();
        Exam exam = examUtilService.addExamWithExerciseGroup(course1, true);
        ExerciseGroup exerciseGroup1 = exam.getExerciseGroups().get(0);

        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        programmingExerciseRepository.save(programmingExercise);

        ProgrammingExercise programmingExercise2 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup1);
        programmingExerciseRepository.save(programmingExercise2);

        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var studentExam1 = examUtilService.addStudentExamWithUser(exam, student1, 10);
        studentExam1.setExercises(List.of(programmingExercise, programmingExercise2));
        var studentExam2 = examUtilService.addStudentExamWithUser(exam, student2, 0);
        studentExam2.setExercises(List.of(programmingExercise, programmingExercise2));
        studentExamRepository.saveAll(Set.of(studentExam1, studentExam2));

        var participationExSt1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        var participationExSt2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");

        var participationEx2St1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise2, TEST_PREFIX + "student1");
        var participationEx2St2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise2, TEST_PREFIX + "student2");

        assertThat(studentExamRepository.findStudentExam(programmingExercise, participationExSt1)).contains(studentExam1);
        assertThat(studentExamRepository.findStudentExam(programmingExercise, participationExSt2)).contains(studentExam2);
        assertThat(studentExamRepository.findStudentExam(programmingExercise2, participationEx2St1)).contains(studentExam1);
        assertThat(studentExamRepository.findStudentExam(programmingExercise2, participationEx2St2)).contains(studentExam2);

        mockConfigureRepository(programmingExercise, TEST_PREFIX + "student1", Set.of(student1), true);
        mockConfigureRepository(programmingExercise, TEST_PREFIX + "student2", Set.of(student2), true);
        mockConfigureRepository(programmingExercise2, TEST_PREFIX + "student1", Set.of(student1), true);
        mockConfigureRepository(programmingExercise2, TEST_PREFIX + "student2", Set.of(student2), true);

        Integer numOfUnlockedExercises = request.postWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + exam.getId() + "/unlock-all-repositories", Optional.empty(),
                Integer.class, HttpStatus.OK);

        assertThat(numOfUnlockedExercises).isEqualTo(2);

        verify(programmingExerciseScheduleService).unlockAllStudentRepositories(programmingExercise);
        verify(programmingExerciseScheduleService).unlockAllStudentRepositories(programmingExercise2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsTemplateCombine() throws Exception {
        Exam examWithProgramming = examUtilService.addExerciseGroupsAndExercisesToExam(exam1, true);
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());

        // invoke generate student exams
        request.postListWithResponseBody("/api/courses/" + course1.getId() + "/exams/" + examWithProgramming.getId() + "/generate-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.OK);

        verify(gitService, never()).combineAllCommitsOfRepositoryIntoOne(any());

        // invoke prepare exercise start
        ExamPrepareExercisesTestUtil.prepareExerciseStart(request, exam1, course1);

        verify(gitService, times(examUtilService.getNumberOfProgrammingExercises(exam1.getId()))).combineAllCommitsOfRepositoryIntoOne(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithProgrammingExercise_preCheckFailed() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        ExerciseGroup programmingGroup = ExamFactory.generateExerciseGroup(false, exam);
        exam = examRepository.save(exam);
        exam.setId(null);
        ProgrammingExercise programming = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(programmingGroup, ProgrammingLanguage.JAVA);
        programmingGroup.addExercise(programming);
        exerciseRepo.save(programming);

        doReturn(true).when(versionControlService).checkIfProjectExists(any(), any());
        doReturn(null).when(continuousIntegrationService).checkIfProjectExists(any(), any());

        request.performMvcRequest(post("/api/courses/" + course1.getId() + "/exam-import").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(exam)))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResolvedException()).hasMessage("Exam contains programming exercise(s) with invalid short name."));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @CsvSource({ "A,A,B,C", "A,B,C,C", "A,A,B,B" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_programmingExerciseSameShortNameOrTitle(String shortName1, String shortName2, String title1, String title2) throws Exception {
        Exam exam = ExamFactory.generateExamWithExerciseGroup(course1, true);
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().get(0);
        ProgrammingExercise exercise1 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup);
        ProgrammingExercise exercise2 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup);

        exercise1.setShortName(shortName1);
        exercise2.setShortName(shortName2);
        exercise1.setTitle(title1);
        exercise2.setTitle(title2);

        request.postWithoutLocation("/api/courses/" + course1.getId() + "/exam-import", exam, HttpStatus.BAD_REQUEST, null);
    }
}
