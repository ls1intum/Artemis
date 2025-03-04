package de.tum.cit.aet.artemis.exam;

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
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exam.util.ExamPrepareExercisesTestUtil;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseTestService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVcTest;

class ProgrammingExamIntegrationTest extends AbstractSpringIntegrationJenkinsLocalVcTest {

    private static final String TEST_PREFIX = "programmingexamtest";

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private Course course1;

    private Exam exam1;

    private static final int NUMBER_OF_STUDENTS = 2;

    private static final int NUMBER_OF_TUTORS = 1;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, 0, 1);

        course1 = courseUtilService.addEmptyCourse();
        exam1 = examUtilService.addExam(course1);

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 200;
    }

    @AfterEach
    void tearDown() throws Exception {
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

        request.put("/api/exam/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);

        verify(instanceMessageSendService, never()).sendProgrammingExerciseSchedule(programmingEx.getId());
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

        request.put("/api/exam/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);

        verify(instanceMessageSendService, never()).sendProgrammingExerciseSchedule(programmingEx.getId());
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

        request.put("/api/exam/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);

        verify(instanceMessageSendService).sendProgrammingExerciseSchedule(programmingEx.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateExam_rescheduleProgramming_visibleDateChanged_shouldReschedule() throws Exception {
        var programmingEx = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        var examWithProgrammingEx = programmingEx.getExerciseGroup().getExam();
        examWithProgrammingEx.setVisibleDate(examWithProgrammingEx.getVisibleDate().plusSeconds(1));

        request.put("/api/exam/courses/" + examWithProgrammingEx.getCourse().getId() + "/exams", examWithProgrammingEx, HttpStatus.OK);

        verify(instanceMessageSendService).sendProgrammingExerciseSchedule(programmingEx.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExamsTemplateCombine() throws Exception {
        Exam examWithProgramming = examUtilService.addExerciseGroupsAndExercisesToExam(exam1, true);
        doNothing().when(gitService).combineAllCommitsOfRepositoryIntoOne(any());

        // invoke generate student exams
        request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + examWithProgramming.getId() + "/generate-student-exams", Optional.empty(),
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
        programming.setBuildConfig(programmingExerciseBuildConfigRepository.save(programming.getBuildConfig()));
        exerciseRepository.save(programming);

        doReturn(true).when(versionControlService).checkIfProjectExists(any(), any());
        doReturn(null).when(continuousIntegrationService).checkIfProjectExists(any(), any());

        request.performMvcRequest(
                post("/api/exam/courses/" + course1.getId() + "/exam-import").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(exam)))
                .andExpect(status().isBadRequest())
                .andExpect(result -> assertThat(result.getResolvedException()).hasMessage("Exam contains programming exercise(s) with invalid short name."));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @CsvSource({ "A,A,B,C", "A,B,C,C", "A,A,B,B" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportExamWithExercises_programmingExerciseSameShortNameOrTitle(String shortName1, String shortName2, String title1, String title2) throws Exception {
        Exam exam = ExamFactory.generateExamWithExerciseGroup(course1, true);
        ExerciseGroup exerciseGroup = exam.getExerciseGroups().getFirst();
        ProgrammingExercise exercise1 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup);
        ProgrammingExercise exercise2 = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup);

        exercise1.setShortName(shortName1);
        exercise2.setShortName(shortName2);
        exercise1.setTitle(title1);
        exercise2.setTitle(title2);

        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exam-import", exam, HttpStatus.BAD_REQUEST, null);
    }
}
