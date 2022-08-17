package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ExamActionType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamActivity;
import de.tum.in.www1.artemis.domain.exam.monitoring.actions.*;
import de.tum.in.www1.artemis.repository.ExamActivityRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.service.exam.monitoring.ExamActionService;
import de.tum.in.www1.artemis.service.exam.monitoring.ExamActivityService;
import de.tum.in.www1.artemis.service.scheduled.cache.monitoring.ExamMonitoringScheduleService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.ExamActivityResource;

class ExamActivityIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExamMonitoringScheduleService examMonitoringScheduleService;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private ExamActivityResource examActivityResource;

    @Autowired
    private ExamActivityService examActivityService;

    @Autowired
    private ExamActivityRepository examActivityRepository;

    @Autowired
    private ExamActionService examActionService;

    @Autowired
    private ExerciseRepository exerciseRepo;

    private Course course;

    private Exam exam;

    private StudentExam studentExam;

    @BeforeEach
    void init() {
        List<User> users = database.addUsers(15, 5, 0, 1);
        course = database.addEmptyCourse();
        exam = database.addActiveExamWithRegisteredUser(course, users.get(0));
        exam.setMonitoring(true);
        exam = examRepository.save(exam);
        studentExam = database.addStudentExam(exam);
        studentExam.setWorkingTime(7200);
        studentExam.setUser(users.get(0));
        studentExamRepository.save(studentExam);
    }

    @AfterEach
    void tearDown() throws Exception {
        examMonitoringScheduleService.stopSchedule();
        examMonitoringScheduleService.clearAllExamMonitoringData();
        database.resetDatabase();
    }

    private ExamAction createExamActionBasedOnType(ExamActionType examActionType) {
        ExamAction examAction = null;
        switch (examActionType) {
            case STARTED_EXAM -> {
                examAction = new StartedExamAction();
                ((StartedExamAction) examAction).setSessionId(0L);
            }
            case ENDED_EXAM -> {
                examAction = new EndedExamAction();
            }
            case HANDED_IN_EARLY -> {
                examAction = new HandedInEarlyAction();
            }
            case CONTINUED_AFTER_HAND_IN_EARLY -> {
                examAction = new ContinuedAfterHandedInEarlyAction();
            }
            case SWITCHED_EXERCISE -> {
                examAction = new SwitchedExerciseAction();
                ((SwitchedExerciseAction) examAction).setExerciseId(0L);
            }
            case SAVED_EXERCISE -> {
                examAction = new SavedExerciseAction();
                ((SavedExerciseAction) examAction).setAutomatically(false);
                ((SavedExerciseAction) examAction).setFailed(true);
                ((SavedExerciseAction) examAction).setForced(false);
                ((SavedExerciseAction) examAction).setSubmissionId(0L);
            }
            case CONNECTION_UPDATED -> {
                examAction = new ConnectionUpdatedAction();
                ((ConnectionUpdatedAction) examAction).setConnected(false);
            }
        }
        examAction.setType(examActionType);
        examAction.setTimestamp(ZonedDateTime.now());
        examAction.setStudentExamId(studentExam.getId());
        return examAction;
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "student1", roles = "USER")
    @EnumSource(ExamActionType.class)
    void testCreateExamActivityInCache(ExamActionType examActionType) {
        ExamAction examAction = createExamActionBasedOnType(examActionType);

        examActivityResource.updatePerformedExamActions(exam.getId(), examAction);
        verify(this.websocketMessagingService).sendMessage("/topic/exam-monitoring/" + exam.getId() + "/action", examAction);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "student1", roles = "USER")
    @EnumSource(ExamActionType.class)
    void testExamActionPresentInCache(ExamActionType examActionType) {
        ExamAction examAction = createExamActionBasedOnType(examActionType);

        examActivityResource.updatePerformedExamActions(exam.getId(), examAction);
        verify(this.websocketMessagingService).sendMessage("/topic/exam-monitoring/" + exam.getId() + "/action", examAction);

        var examActivity = examMonitoringScheduleService.getExamActivityFromCache(exam.getId(), studentExam.getId());
        assertThat(examActivity).isNotNull();
        assertThat(examActivity.getExamActions().size()).isEqualTo(1);
        assertThat(new ArrayList<>(examActivity.getExamActions()).get(0).getType()).isEqualTo(examActionType);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "student1", roles = "USER")
    @EnumSource(ExamActionType.class)
    void testExamActionNotPresentInCache(ExamActionType examActionType) {
        ExamAction examAction = createExamActionBasedOnType(examActionType);

        examActivityResource.updatePerformedExamActions(exam.getId(), examAction);
        verify(this.websocketMessagingService).sendMessage("/topic/exam-monitoring/" + exam.getId() + "/action", examAction);

        examMonitoringScheduleService.executeExamActivitySaveTask(exam.getId());

        var examActivity = examMonitoringScheduleService.getExamActivityFromCache(exam.getId(), studentExam.getId());
        assertThat(examActivity).isNull();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testMultipleExamActions() {
        List<ExamAction> examActions = Arrays.stream(ExamActionType.values()).map(this::createExamActionBasedOnType).toList();

        for (ExamAction examAction : examActions) {
            examActivityResource.updatePerformedExamActions(exam.getId(), examAction);
            verify(this.websocketMessagingService).sendMessage("/topic/exam-monitoring/" + exam.getId() + "/action", examAction);
        }

        var examActivity = examMonitoringScheduleService.getExamActivityFromCache(exam.getId(), studentExam.getId());

        assertThat(examActivity).isNotNull();
        assertThat(examActivity.getExamActions().size()).isEqualTo(examActions.size());

        examMonitoringScheduleService.executeExamActivitySaveTask(exam.getId());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "student1", roles = "USER")
    @EnumSource(ExamActionType.class)
    void testExamWithMonitoringDisabled(ExamActionType examActionType) {
        ExamAction examAction = createExamActionBasedOnType(examActionType);

        exam.setMonitoring(false);
        examRepository.save(exam);

        examActivityResource.updatePerformedExamActions(exam.getId(), examAction);

        verify(this.websocketMessagingService).sendMessage("/topic/exam-monitoring/" + exam.getId() + "/action", examAction);

        // Currently, we don't apply any filtering - so there should be an activity and action in the cache
        var examActivity = examMonitoringScheduleService.getExamActivityFromCache(exam.getId(), studentExam.getId());
        assertThat(examActivity).isNotNull();
        assertThat(examActivity.getExamActions().size()).isEqualTo(1);
        assertThat(new ArrayList<>(examActivity.getExamActions()).get(0).getType()).isEqualTo(examActionType);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "admin", roles = "ADMIN")
    @EnumSource(ExamActionType.class)
    void testGetInitialExamActions(ExamActionType examActionType) throws Exception {
        ExamAction examAction = createExamActionBasedOnType(examActionType);

        examActivityResource.updatePerformedExamActions(exam.getId(), examAction);

        verify(this.websocketMessagingService).sendMessage("/topic/exam-monitoring/" + exam.getId() + "/action", examAction);

        List<ExamAction> examActions = request.getList("/api/exam-monitoring/" + exam.getId() + "/load-actions", HttpStatus.OK, ExamAction.class);

        assertEquals(1, examActions.size());

        var receivedAction = examActions.get(0);
        // We need to validate those values to be equal.
        assertEquals(examAction.getExamActivityId(), receivedAction.getExamActivityId());
        assertEquals(examAction.getStudentExamId(), receivedAction.getStudentExamId());
        assertEquals(examAction.getId(), receivedAction.getId());
        assertEquals(examAction.getType(), receivedAction.getType());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "admin", roles = "ADMIN")
    @ValueSource(booleans = { true, false })
    void testUpdateMonitoring(boolean monitoring) throws Exception {
        exam.setMonitoring(!monitoring);
        exam = examRepository.save(exam);

        var result = request.putWithResponseBody("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/statistics", monitoring, Boolean.class, HttpStatus.OK);

        assertEquals(result, monitoring);
        assertEquals(examRepository.findByIdElseThrow(exam.getId()).isMonitoring(), monitoring);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "student1", roles = "USER")
    @EnumSource(ExamActionType.class)
    void testExamActionSavedInDatabase(ExamActionType examActionType) {
        ExamAction examAction = createExamActionBasedOnType(examActionType);

        examActivityResource.updatePerformedExamActions(exam.getId(), examAction);

        var examActivity = examActivityService.findByStudentExamId(studentExam.getId());
        examMonitoringScheduleService.executeExamActivitySaveTask(exam.getId());
        var savedActions = examActionService.findByExamActivityId(examActivity.getId());

        assertThat(savedActions.size()).isEqualTo(1);
        assertThat(savedActions.get(0).getType()).isEqualTo(examActionType);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "student1", roles = "USER")
    @EnumSource(ExamActionType.class)
    void testExamActionNotSavedInDatabase(ExamActionType examActionType) {
        ExamAction examAction = createExamActionBasedOnType(examActionType);

        examActivityResource.updatePerformedExamActions(exam.getId(), examAction);

        var examActivity = examActivityService.findByStudentExamId(studentExam.getId());
        var savedActions = examActionService.findByExamActivityId(examActivity.getId());

        assertThat(savedActions.size()).isEqualTo(0);
    }

    private void prepareLargeTestWithVariableStudentExams(ArrayList<ExamAction> actions, ArrayList<StudentExam> studentExams, ArrayList<ExamActivity> examActivities,
            int numberOfStudentExams, int numberOfActionsEachPerType) {
        // Create Student Exams
        for (int i = 0; i < numberOfStudentExams; i++) {
            studentExams.add(database.addStudentExam(exam));
            studentExam.setWorkingTime(7200);
            studentExamRepository.save(studentExam);
        }

        // Create Actions
        for (int i = 0; i < numberOfStudentExams; ++i) {
            for (ExamActionType type : ExamActionType.values()) {
                for (int n = 0; n < numberOfActionsEachPerType; ++n) {
                    actions.add(createExamActionBasedOnType(type));
                }
            }
        }

        var types = ExamActionType.values().length;

        // Assign Student Exams to Actions
        for (int i = 0; i < numberOfStudentExams; i++) {
            for (int j = i * numberOfActionsEachPerType * types; j < numberOfActionsEachPerType * (i + 1) * types; j++) {
                var studentExam = studentExams.get(i);
                actions.get(j).setStudentExamId(studentExam.getId());
                actions.get(j).setExamActivityId(studentExam.getId());
            }
        }

        // Perform actions
        for (ExamAction action : actions) {
            examActivityResource.updatePerformedExamActions(exam.getId(), action);
        }

        // Expect ExamActivity saved
        for (StudentExam studentExam : studentExams) {
            var examActivity = examActivityService.findByStudentExamId(studentExam.getId());
            examActivities.add(examActivity);
        }

        // Save Exam Actions in Database
        examMonitoringScheduleService.executeExamActivitySaveTask(exam.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testMultipleExamActionSavedInDatabase() {
        var actions = new ArrayList<ExamAction>();
        var studentExams = new ArrayList<StudentExam>();
        var examActivities = new ArrayList<ExamActivity>();

        var numberOfStudentExams = 10;
        var numberOfActionsEachPerType = 10;

        prepareLargeTestWithVariableStudentExams(actions, studentExams, examActivities, numberOfStudentExams, numberOfActionsEachPerType);

        for (ExamActivity examActivity : examActivities) {
            var savedActions = examActionService.findByExamActivityId(examActivity.getId());
            assertEquals(numberOfActionsEachPerType * ExamActionType.values().length, savedActions.size());
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testMultipleExamActionSavedInDatabaseAndDeleteAfterward() {
        var actions = new ArrayList<ExamAction>();
        var studentExams = new ArrayList<StudentExam>();
        var examActivities = new ArrayList<ExamActivity>();

        var numberOfStudentExams = 10;
        var numberOfActionsEachPerType = 10;

        prepareLargeTestWithVariableStudentExams(actions, studentExams, examActivities, numberOfStudentExams, numberOfActionsEachPerType);

        // Delete Student Exams
        var length = studentExamRepository.findAll().size();
        studentExamRepository.deleteAll(studentExams);
        assertEquals(length - studentExams.size(), studentExamRepository.findAll().size());

        // All Actions should be deleted
        for (ExamActivity examActivity : examActivities) {
            var savedActions = examActionService.findByExamActivityId(examActivity.getId());
            assertEquals(0, savedActions.size());
        }
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testClearExamActionsAndKeepExercisesUnchanged() {
        exam = database.setupExamWithExerciseGroupsExercisesRegisteredStudents(this.course);
        exam.setMonitoring(true);
        exam = examRepository.save(exam);

        var actions = new ArrayList<ExamAction>();
        var studentExams = new ArrayList<StudentExam>();
        var examActivities = new ArrayList<ExamActivity>();

        var numberOfStudentExams = 10;
        var numberOfActionsEachPerType = 10;

        prepareLargeTestWithVariableStudentExams(actions, studentExams, examActivities, numberOfStudentExams, numberOfActionsEachPerType);

        var exercises = exerciseRepo.findAllExercisesByCourseId(this.course.getId());

        examActivityRepository.deleteAll();

        assertEquals(0, examActivityRepository.findAll().size());

        assertEquals(exercises, exerciseRepo.findAllExercisesByCourseId(this.course.getId()));
    }
}
