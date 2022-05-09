package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.exam.StudentExamService;
import de.tum.in.www1.artemis.service.exam.monitoring.ExamActionService;
import de.tum.in.www1.artemis.service.exam.monitoring.ExamActivityService;
import de.tum.in.www1.artemis.service.scheduled.cache.monitoring.ExamMonitoringScheduleService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ExamActivityIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExamMonitoringScheduleService examMonitoringScheduleService;

    @Autowired
    private ExamService examService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentExamService studentExamService;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private RequestUtilService request;

    @Autowired
    private ExamActivityService examActivityService;

    @Autowired
    private ExamActionService examActionService;

    private List<User> users;

    private Course course;

    private Exam exam;

    private StudentExam studentExam;

    @BeforeEach
    public void init() {
        users = database.addUsers(15, 5, 0, 1);
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
    public void tearDown() throws Exception {
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
        return examAction;
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "student1", roles = "USER")
    @EnumSource(ExamActionType.class)
    public void testCreateExamActivityInCacheAndSaveInDatabase(ExamActionType examActionType) throws Exception {
        ExamAction examAction = createExamActionBasedOnType(examActionType);

        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExam.getId() + "/actions", List.of(examAction), HttpStatus.OK);

        var examActivity = examActivityService.findByStudentExamId(studentExam.getId());
        assertThat(examActivity).isNotNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "student1", roles = "USER")
    @EnumSource(ExamActionType.class)
    public void testExamActionSavedInDatabase(ExamActionType examActionType) throws Exception {
        ExamAction examAction = createExamActionBasedOnType(examActionType);

        request.put("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExam.getId() + "/actions", List.of(examAction), HttpStatus.OK);

        var examActivity = examActivityService.findByStudentExamId(studentExam.getId());
        examMonitoringScheduleService.executeExamActivitySaveTask(exam.getId());
        var savedAction = examActionService.findByExamActivityId(examActivity.getId());

        assertThat(savedAction.getType()).isEqualTo(examActionType);
    }
}
