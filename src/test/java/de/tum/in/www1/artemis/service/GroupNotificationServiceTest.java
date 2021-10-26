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

    private final static Long EXERCISE_ID = 13L;

    @Mock
    private static Course course;

    private final static Long COURSE_ID = 27L;

    @Mock
    private static Exam exam;

    private final static Long EXAM_ID = 42L;

    private final static String NOTIFICATION_TEXT = "notificationText";

    private enum ExerciseStatus {
        COURSE_EXERCISE_STATUS, EXAM_EXERCISE_STATUS
    }

    /**
     * Auxiliary method to set the correct mock behavior for exercise status
     * @param exerciseStatus indicates if the exercise is a course or exam exercise
     */
    private void setExerciseStatus(ExerciseStatus exerciseStatus) {
        when(exercise.isExamExercise()).thenReturn(exerciseStatus == ExerciseStatus.EXAM_EXERCISE_STATUS);
        when(exercise.isCourseExercise()).thenReturn(exerciseStatus == ExerciseStatus.COURSE_EXERCISE_STATUS);
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
        when(exam.getId()).thenReturn(EXAM_ID);

        course = mock(Course.class);
        when(course.getId()).thenReturn(COURSE_ID);

        exercise = mock(Exercise.class);

        instanceMessageSendService = mock(InstanceMessageSendService.class);
        doNothing().when(instanceMessageSendService).sendExerciseReleaseNotificationSchedule(EXERCISE_ID);
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

    /**
    * Test for notifyAboutExerciseUpdate method with an undefined release date
    */
    @Test
    public void testNotifyAboutExerciseUpdate_undefinedReleaseDate() {
        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService, times(0)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
    }

    /**
    * Test for notifyAboutExerciseUpdate method with an future release date
    */
    @Test
    public void testNotifyAboutExerciseUpdate_futureReleaseDate() {
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now().plusHours(1));
        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService, times(0)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
    }

    /**
    * Test for notifyAboutExerciseUpdate method with a correct release date (now) for exam exercises
    */
    @Test
    public void testNotifyAboutExerciseUpdate_correctReleaseDate_examExercise() {
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now());
        setExerciseStatus(ExerciseStatus.EXAM_EXERCISE_STATUS);
        doNothing().when(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);

        groupNotificationService.notifyAboutExerciseUpdate(exercise, null);

        verify(groupNotificationService, times(1)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());
    }

    /**
    * Test for notifyAboutExerciseUpdate method with a correct release date (now) for course exercises
    */
    @Test
    public void testNotifyAboutExerciseUpdate_correctReleaseDate_courseExercise() {
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now());
        setExerciseStatus(ExerciseStatus.COURSE_EXERCISE_STATUS);
        doNothing().when(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);

        groupNotificationService.notifyAboutExerciseUpdate(exercise, null);
        verify(groupNotificationService, times(0)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());

        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService, times(1)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());
    }

    /// CheckNotificationForExerciseRelease

    /**
     * Auxiliary methods for testing the checkNotificationForExerciseRelease
     */
    private void prepareMocksForCheckNotificationForExerciseReleaseTesting() {
        setExerciseStatus(ExerciseStatus.COURSE_EXERCISE_STATUS);
        doNothing().when(groupNotificationService).notifyAllGroupsAboutReleasedExercise(exercise);
    }

    /**
    * Test for checkNotificationForExerciseRelease method with an undefined release date
    */
    @Test
    public void testCheckNotificationForExerciseRelease_undefinedReleaseDate() {
        prepareMocksForCheckNotificationForExerciseReleaseTesting();
        groupNotificationService.checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutReleasedExercise(any());
    }

    /**
    * Test for checkNotificationForExerciseRelease method with a current or past release date
    */
    @Test
    public void testCheckNotificationForExerciseRelease_currentOrPastReleaseDate() {
        prepareMocksForCheckNotificationForExerciseReleaseTesting();
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now());
        groupNotificationService.checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutReleasedExercise(any());
    }

    /**
    * Test for checkNotificationForExerciseRelease method with an future release date
    */
    @Test
    public void testCheckNotificationForExerciseRelease_futureReleaseDate() {
        prepareMocksForCheckNotificationForExerciseReleaseTesting();
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now().plusHours(1));
        groupNotificationService.checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        verify(instanceMessageSendService, times(1)).sendExerciseReleaseNotificationSchedule(any());
    }

    /// CheckAndCreateAppropriateNotificationsWhenUpdatingExercise

    /**
    * Test for checkAndCreateAppropriateNotificationsWhenUpdatingExercise method
    */
    @Test
    public void testCheckAndCreateAppropriateNotificationsWhenUpdatingExercise() {
        doNothing().when(groupNotificationService).notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        doNothing().when(groupNotificationService).checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        groupNotificationService.checkAndCreateAppropriateNotificationsWhenUpdatingExercise(exercise, NOTIFICATION_TEXT, instanceMessageSendService);
        verify(groupNotificationService, times(1)).notifyAboutExerciseUpdate(any(), any());
        verify(groupNotificationService, times(1)).checkNotificationForExerciseRelease(any(), any());
    }

}
