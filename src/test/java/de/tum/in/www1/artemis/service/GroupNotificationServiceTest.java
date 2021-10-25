package de.tum.in.www1.artemis.service;

import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;

public class GroupNotificationServiceTest {

    @Autowired
    private static GroupNotificationService groupNotificationService;

    @Mock
    private static UserRepository userRepository;

    @Mock
    private static GroupNotificationRepository groupNotificationRepository;

    @Mock
    private static SimpMessageSendingOperations messagingTemplate;

    @Mock
    private static InstanceMessageSendService instanceMessageSendService;

    @Mock
    private static Exercise exercise;

    private final static Long exerciseId = 13L;

    @Mock
    private static Course course;

    private final static Long courseId = 27L;

    @Mock
    private static Exam exam;

    private final static Long examId = 42L;

    private final static String notificationText = "notificationText";

    private enum ExerciseStatus {
        courseExerciseStatus, examExerciseStatus;
    }

    /**
     * Auxiliary method to set the correct mock behavior for exercise status
     * @param exerciseStatus indicates if the exercise is a course or exam exercise
     */
    private void setExerciseStatus(ExerciseStatus exerciseStatus) {
        when(exercise.isExamExercise()).thenReturn(exerciseStatus == ExerciseStatus.examExerciseStatus);
        when(exercise.isCourseExercise()).thenReturn(exerciseStatus == ExerciseStatus.courseExerciseStatus);
    }

    /**
     * Sets up all needed mocks and their wanted behavior once for all test cases.
     * These are the common mocks and structures which behavior is fixed and will not change
     */
    @BeforeAll
    public static void setUp() {
        userRepository = mock(UserRepository.class);

        groupNotificationRepository = mock(GroupNotificationRepository.class);
        when(groupNotificationRepository.save(any())).thenReturn(null);

        messagingTemplate = mock(SimpMessageSendingOperations.class);

        groupNotificationService = spy(new GroupNotificationService(groupNotificationRepository, messagingTemplate, userRepository));

        exam = mock(Exam.class);
        when(exam.getId()).thenReturn(examId);

        course = mock(Course.class);
        when(course.getId()).thenReturn(courseId);

        exercise = mock(Exercise.class);

        instanceMessageSendService = mock(InstanceMessageSendService.class);
        doNothing().when(instanceMessageSendService).sendExerciseReleaseNotificationSchedule(exerciseId);
    }

    /**
     * Prepares and cleans the mocks that are modified during the tests
     */
    @BeforeEach
    public void cleanMocks() {
        reset(exercise);
        when(exercise.getExamViaExerciseGroupOrCourseMember()).thenReturn(exam);
        when(exercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);

        reset(groupNotificationService);
    }

    /// NotifyAboutExerciseUpdate

    @Test
    public void testNotifyAboutExerciseUpdate_undefinedReleaseDate() {
        groupNotificationService.notifyAboutExerciseUpdate(exercise, notificationText);
        verify(groupNotificationService, times(0)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, notificationText);
    }

    @Test
    public void testNotifyAboutExerciseUpdate_futureReleaseDate() {
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now().plusHours(1));
        groupNotificationService.notifyAboutExerciseUpdate(exercise, notificationText);
        verify(groupNotificationService, times(0)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, notificationText);
    }

    @Test
    public void testNotifyAboutExerciseUpdate_correctReleaseDate_examExercise() {
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now());
        setExerciseStatus(ExerciseStatus.examExerciseStatus);
        doNothing().when(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, notificationText);

        groupNotificationService.notifyAboutExerciseUpdate(exercise, null);

        verify(groupNotificationService, times(1)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());
    }

    @Test
    public void testNotifyAboutExerciseUpdate_correctReleaseDate_courseExercise() {
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now());
        setExerciseStatus(ExerciseStatus.courseExerciseStatus);
        doNothing().when(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, notificationText);

        groupNotificationService.notifyAboutExerciseUpdate(exercise, null);
        verify(groupNotificationService, times(0)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());

        groupNotificationService.notifyAboutExerciseUpdate(exercise, notificationText);
        verify(groupNotificationService, times(1)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());
    }

    /// CheckNotificationForExerciseRelease

    /**
     * Auxiliary methods for testing the checkNotificationForExerciseRelease
     */
    private void prepareMocksForCheckNotificationForExerciseReleaseTesting() {
        setExerciseStatus(ExerciseStatus.courseExerciseStatus);
        doNothing().when(groupNotificationService).notifyAllGroupsAboutReleasedExercise(exercise);
    }

    @Test
    public void testCheckNotificationForExerciseRelease_undefinedReleaseDate() {
        prepareMocksForCheckNotificationForExerciseReleaseTesting();
        groupNotificationService.checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutReleasedExercise(any());
    }

    @Test
    public void testCheckNotificationForExerciseRelease_currentOrPastReleaseDate() {
        prepareMocksForCheckNotificationForExerciseReleaseTesting();
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now());
        groupNotificationService.checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutReleasedExercise(any());
    }

    @Test
    public void testCheckNotificationForExerciseRelease_futureReleaseDate() {
        prepareMocksForCheckNotificationForExerciseReleaseTesting();
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now().plusHours(1));
        groupNotificationService.checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        verify(instanceMessageSendService, times(1)).sendExerciseReleaseNotificationSchedule(any());
    }

    /// CheckAndCreateAppropriateNotificationsWhenUpdatingExercise

    @Test
    public void testCheckAndCreateAppropriateNotificationsWhenUpdatingExercise() {
        doNothing().when(groupNotificationService).notifyAboutExerciseUpdate(exercise, notificationText);
        doNothing().when(groupNotificationService).checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        groupNotificationService.checkAndCreateAppropriateNotificationsWhenUpdatingExercise(exercise, notificationText, instanceMessageSendService);
        verify(groupNotificationService, times(1)).notifyAboutExerciseUpdate(any(), any());
        verify(groupNotificationService, times(1)).checkNotificationForExerciseRelease(any(), any());
    }

}
