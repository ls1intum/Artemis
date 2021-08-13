package de.tum.in.www1.artemis.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.exam.Exam;

public class GroupNotificationFactoryTest {

    @Mock
    private User user;

    @Mock
    private GroupNotificationType groupNotificationType;

    @Mock
    private static GroupNotificationFactory groupNotificationFactory;

    @Mock
    private static Lecture lecture;

    private static Long lectureId = 0L;

    @Mock
    private static Course course;

    private static Long courseId = 1L;

    @Mock
    private static Exam exam;

    @Mock
    private static Attachment attachment;

    @Mock
    private static Exercise exercise;

    private String expectedTitle;

    private String expectedText;

    private String expectedTarget;

    private NotificationPriority expectedPriority;

    private GroupNotification createdNotification;

    private NotificationType notificationType;

    private String notificationText = "notification text";

    @BeforeAll
    public static void setUp() {

        groupNotificationFactory = mock(GroupNotificationFactory.class, CALLS_REAL_METHODS);

        course = mock(Course.class);
        when(course.getId()).thenReturn(courseId);

        lecture = mock(Lecture.class);
        when(lecture.getId()).thenReturn(lectureId);
        when(lecture.getCourse()).thenReturn(course);

        exam = mock(Exam.class);
        when(exam.getId()).thenReturn(42L);

        exercise = mock(Exercise.class);
        when(exercise.getTitle()).thenReturn("exercise title");
        when(exercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);
        when(exercise.getExamViaExerciseGroupOrCourseMember()).thenReturn(exam);
        when(exercise.getProblemStatement()).thenReturn("problem statement");

        attachment = mock(Attachment.class);
        when(attachment.getLecture()).thenReturn(lecture);
    }

    private void checkCreatedNotification(GroupNotification createdNotification, String expectedTitle, String expectedText, String expectedTarget,
            NotificationPriority expectedPriority) {
        assertThat(createdNotification.getTitle()).isEqualTo(expectedTitle);
        assertThat(createdNotification.getText()).isEqualTo(expectedText);
        assertThat(createdNotification.getTarget()).isEqualTo(expectedTarget);
        assertThat(createdNotification.getPriority()).isEqualTo(expectedPriority);
        assertThat(createdNotification.getAuthor()).isEqualTo(user);
    }

    private void createAndCheckNotificationBasedOnExercise() {
        // with notification text
        createdNotification = groupNotificationFactory.createNotification(exercise, user, groupNotificationType, notificationType, notificationText);
        checkCreatedNotification(createdNotification, expectedTitle, notificationText, expectedTarget, expectedPriority);

        // without notification text
        createdNotification = groupNotificationFactory.createNotification(exercise, user, groupNotificationType, notificationType, null);
        checkCreatedNotification(createdNotification, expectedTitle, expectedText, expectedTarget, expectedPriority);
    }

    // Based on Attachment

    @Test
    public void createNotificationBasedOnAttachment() {

        notificationType = NotificationType.ATTACHMENT_CHANGE;
        expectedTitle = "Attachment updated";
        expectedText = "Attachment \"" + attachment.getName() + "\" updated.";
        expectedTarget = "{\"message\":\"attachmentUpdated\",\"id\":" + lectureId + ",\"entity\":\"lectures\",\"course\":" + courseId + ",\"mainPage\":\"courses\"}";
        expectedPriority = NotificationPriority.MEDIUM;

        // with notification text
        createdNotification = groupNotificationFactory.createNotification(attachment, user, groupNotificationType, notificationType, notificationText);
        checkCreatedNotification(createdNotification, expectedTitle, notificationText, expectedTarget, expectedPriority);

        // without notification text
        createdNotification = groupNotificationFactory.createNotification(attachment, user, groupNotificationType, notificationType, null);
        checkCreatedNotification(createdNotification, expectedTitle, expectedText, expectedTarget, expectedPriority);
    }

    // Based on Exercise

    @Test
    public void createNotificationBasedOnExercise_withNotificationType_ExerciseCreated() {

        notificationType = NotificationType.EXERCISE_CREATED;
        expectedTitle = "Exercise created";
        expectedText = "A new exercise \"" + exercise.getTitle() + "\" got created.";
        expectedTarget = "{\"message\":\"exerciseCreated\",\"id\":" + exercise.getId() + ",\"entity\":\"exercises\",\"course\":" + courseId + ",\"mainPage\":\"courses\"}";
        expectedPriority = NotificationPriority.MEDIUM;

        createAndCheckNotificationBasedOnExercise();
    }

    @Test
    public void createNotificationBasedOnExercise_withNotificationType_ExercisePractice() {

        notificationType = NotificationType.EXERCISE_PRACTICE;
        expectedTitle = "Exercise open for practice";
        expectedText = "Exercise \"" + exercise.getTitle() + "\" is now open for practice.";
        expectedTarget = "{\"message\":\"exerciseUpdated\",\"id\":" + exercise.getId() + ",\"entity\":\"exercises\",\"course\":" + courseId + ",\"mainPage\":\"courses\"}";
        expectedPriority = NotificationPriority.MEDIUM;

        createAndCheckNotificationBasedOnExercise();
    }

    @Test
    public void createNotificationBasedOnExercise_withNotificationType_QuizExerciseStarted() {

        notificationType = NotificationType.QUIZ_EXERCISE_STARTED;
        expectedTitle = "Quiz started";
        expectedText = "Quiz \"" + exercise.getTitle() + "\" just started.";
        expectedTarget = "{\"message\":\"exerciseUpdated\",\"id\":" + exercise.getId() + ",\"entity\":\"exercises\",\"course\":" + courseId + ",\"mainPage\":\"courses\"}";
        expectedPriority = NotificationPriority.MEDIUM;

        createAndCheckNotificationBasedOnExercise();
    }

    @Test
    public void createNotificationBasedOnExercise_withNotificationType_ExerciseUpdated_ExamExercise() {

        notificationType = NotificationType.EXERCISE_UPDATED;

        when(exercise.isExamExercise()).thenReturn(true);

        expectedTitle = Constants.LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE;
        expectedPriority = NotificationPriority.HIGH;
        expectedText = "Exam Exercise \"" + exercise.getTitle() + "\" updated.";
        expectedTarget = "{\"problemStatement\":\"" + exercise.getProblemStatement() + "\",\"exercise\":" + exercise.getId() + ",\"exam\":" + exam.getId()
                + ",\"entity\":\"exams\",\"course\":" + courseId + ",\"mainPage\":\"courses\"}";

        // with notification text -> exam popup
        createdNotification = groupNotificationFactory.createNotification(exercise, user, groupNotificationType, notificationType, notificationText);
        checkCreatedNotification(createdNotification, expectedTitle, notificationText, expectedTarget, NotificationPriority.HIGH);

        // without notification text -> silent exam update (expectedText = null)
        createdNotification = groupNotificationFactory.createNotification(exercise, user, groupNotificationType, notificationType, null);
        checkCreatedNotification(createdNotification, expectedTitle, null, expectedTarget, NotificationPriority.HIGH);

        when(exercise.isExamExercise()).thenReturn(false);
    }

    @Test
    public void createNotificationBasedOnExercise_withNotificationType_ExerciseUpdated_CourseExercise() {

        notificationType = NotificationType.EXERCISE_UPDATED;

        expectedTitle = "Exercise updated";
        expectedText = "Exercise \"" + exercise.getTitle() + "\" updated.";
        expectedPriority = NotificationPriority.MEDIUM;
        expectedTarget = "{\"message\":\"exerciseUpdated\",\"id\":" + exercise.getId() + ",\"entity\":\"exercises\",\"course\":" + courseId + ",\"mainPage\":\"courses\"}";

        createAndCheckNotificationBasedOnExercise();
    }
}
