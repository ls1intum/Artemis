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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ExamActionType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.exam.monitoring.ExamAction;
import de.tum.in.www1.artemis.domain.exam.monitoring.actions.*;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
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
        database.resetDatabase();
        examMonitoringScheduleService.stopSchedule();
        examMonitoringScheduleService.clearAllExamMonitoringData();
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
}
