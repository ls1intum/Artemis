package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;

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
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.dto.ExamImportDTO;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseTestService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;

class ProgrammingExamIntegrationTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

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

    private String createdProjectKey;

    private static final int NUMBER_OF_STUDENTS = 2;

    private static final int NUMBER_OF_TUTORS = 1;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, 0, 1);

        course1 = courseUtilService.addEmptyCourse();
        examUtilService.addExam(course1);

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 200;
    }

    @AfterEach
    void tearDown() throws Exception {
        jenkinsRequestMockProvider.reset();
        if (programmingExerciseTestService.exerciseRepo != null) {
            programmingExerciseTestService.tearDown();
        }
        // Clean up LocalVC project created by versionControlService.createProjectForExercise
        if (createdProjectKey != null) {
            RepositoryExportTestUtil.deleteLocalVcProjectIfPresent(localVCBasePath, createdProjectKey);
            createdProjectKey = null;
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
    void testImportExamWithSingleProgrammingExercise_successful() throws Exception {
        programmingExerciseTestService.setup(this, versionControlService);

        Course sourceCourse = courseUtilService.addEmptyCourse();
        Exam sourceExam = examUtilService.addExamWithExerciseGroup(sourceCourse, true);
        ProgrammingExercise sourceExercise = programmingExerciseUtilService.addProgrammingExerciseToExam(sourceExam, 0);
        programmingExerciseUtilService.addTestCasesToProgrammingExercise(sourceExercise);
        sourceExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences(sourceExercise);

        programmingExerciseTestService.setupRepositoryMocks(sourceExercise, programmingExerciseTestService.sourceExerciseRepo, programmingExerciseTestService.sourceSolutionRepo,
                programmingExerciseTestService.sourceTestRepo, programmingExerciseTestService.sourceAuxRepo);
        exerciseRepository.save(sourceExercise);

        doReturn(null).when(continuousIntegrationService).checkIfProjectExists(any(), any());
        doNothing().when(continuousIntegrationService).createProjectForExercise(any());
        doReturn("build-plan").when(continuousIntegrationService).copyBuildPlan(any(), any(), any(), any(), any(), anyBoolean());
        doNothing().when(continuousIntegrationService).enablePlan(any(), any());
        doNothing().when(continuousIntegrationService).updatePlanRepository(any(), any(), any(), any(), any(), any(), any());
        doNothing().when(continuousIntegrationTriggerService).triggerBuild(any());

        ExamImportDTO importDTO = ExamImportDTO.of(sourceExam, course1.getId());

        final Exam received = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exam-import", importDTO, Exam.class, HttpStatus.CREATED);

        assertThat(received.getExerciseGroups()).hasSize(1);
        Exercise importedExercise = received.getExerciseGroups().getFirst().getExercises().stream().findFirst().orElseThrow();
        assertThat(importedExercise).isInstanceOf(ProgrammingExercise.class);
        ProgrammingExercise importedProgrammingExercise = programmingExerciseUtilService.loadProgrammingExerciseWithEagerReferences((ProgrammingExercise) importedExercise);

        assertThat(importedProgrammingExercise.getId()).isNotEqualTo(sourceExercise.getId());
        assertThat(importedProgrammingExercise.getTitle()).isEqualTo(sourceExercise.getTitle());
        assertThat(importedProgrammingExercise.getShortName()).isEqualTo(sourceExercise.getShortName());
        assertThat(importedProgrammingExercise.getProgrammingLanguage()).isEqualTo(sourceExercise.getProgrammingLanguage());
        assertThat(importedProgrammingExercise.getProjectType()).isEqualTo(sourceExercise.getProjectType());
        assertThat(importedProgrammingExercise.getPackageName()).isEqualTo(sourceExercise.getPackageName());
        assertThat(importedProgrammingExercise.isExamExercise()).isTrue();
        assertThat(importedProgrammingExercise.getExerciseGroup().getExam().getCourse()).isEqualTo(course1);
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

        versionControlService.createProjectForExercise(programming);
        createdProjectKey = programming.getProjectKey();
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
