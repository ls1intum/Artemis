package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.*;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.service.notifications.NotificationTargetProvider;

public class SingleUserNotificationFactoryTest {

    @Autowired
    private static SingleUserNotificationFactory singleUserNotificationFactory;

    @Autowired
    private static NotificationTargetProvider notificationTargetProvider;

    @Mock
    private User user;

    @Mock
    private static Lecture lecture;

    private static final Long LECTURE_ID = 0L;

    @Mock
    private static Course course;

    private static final Long COURSE_ID = 1L;

    @Mock
    private static Exercise exercise;

    private static final Long EXERCISE_ID = 42L;

    @Mock
    private static Post post;

    @Mock
    private static AnswerPost answerPost;

    private static final String POST_NOTIFICATION_TEXT = "Your post got replied.";

    private String expectedTitle;

    private String expectedText;

    private NotificationTarget expectedTransientTarget;

    private NotificationPriority expectedPriority;

    private SingleUserNotification createdNotification;

    private NotificationType notificationType;

    /**
     * sets up all needed mocks and their wanted behavior once for all test cases.
     */
    @BeforeAll
    public static void setUp() {
        notificationTargetProvider = new NotificationTargetProvider();

        course = mock(Course.class);
        when(course.getId()).thenReturn(COURSE_ID);

        lecture = mock(Lecture.class);
        when(lecture.getId()).thenReturn(LECTURE_ID);
        when(lecture.getCourse()).thenReturn(course);

        exercise = mock(Exercise.class);
        when(exercise.getId()).thenReturn(EXERCISE_ID);
        when(exercise.getTitle()).thenReturn("exercise title");
        when(exercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);
        when(exercise.getProblemStatement()).thenReturn("problem statement");

        post = mock(Post.class);
        when(post.getExercise()).thenReturn(exercise);
        when(post.getLecture()).thenReturn(lecture);

        answerPost = mock(AnswerPost.class);
        when(answerPost.getPost()).thenReturn(post);

    }

    /// Test for Notifications based on Posts

    /**
     * Calls the real createNotification method of the singleUserNotificationFactory and tests if the result is correct for Post notifications.
     */
    private void createAndCheckPostNotification() {
        createdNotification = singleUserNotificationFactory.createNotification(post, notificationType, course);
        checkNotification();
    }

    /**
     * Calls the real createNotification method of the singleUserNotificationFactory and tests if the result is correct for Exercise notifications.
     */
    private void createAndCheckExerciseNotification() {
        createdNotification = singleUserNotificationFactory.createNotification(exercise, notificationType, user);
        checkNotification();
    }

    /**
     * Tests if the resulting notification is correct.
     */
    private void checkNotification() {
        assertThat(createdNotification.getTitle()).isEqualTo(expectedTitle);
        assertThat(createdNotification.getText()).isEqualTo(expectedText);
        assertThat(createdNotification.getTarget()).isEqualTo(expectedTransientTarget.toJsonString());
        assertThat(createdNotification.getPriority()).isEqualTo(expectedPriority);
        assertThat(createdNotification.getAuthor()).isEqualTo(user);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of NEW_REPLY_FOR_EXERCISE_POST.
     * I.e. notifications that originate from a new reply for an exercise post.
     */
    @Test
    public void createNotification_withNotificationType_NewReplyForExercisePost() {
        notificationType = NEW_REPLY_FOR_EXERCISE_POST;
        expectedTitle = NEW_REPLY_FOR_EXERCISE_POST_TITLE;
        expectedText = POST_NOTIFICATION_TEXT;
        expectedPriority = MEDIUM;
        expectedTransientTarget = notificationTargetProvider.getExercisePostTarget(post, course);
        createAndCheckPostNotification();
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of NEW_REPLY_FOR_LECTURE_POST.
     * I.e. notifications that originate from a new reply for a lecture post.
     */
    @Test
    public void createNotification_withNotificationType_NewReplyForLecturePost() {
        notificationType = NEW_REPLY_FOR_LECTURE_POST;
        expectedTitle = NEW_REPLY_FOR_LECTURE_POST_TITLE;
        expectedText = POST_NOTIFICATION_TEXT;
        expectedPriority = MEDIUM;
        expectedTransientTarget = notificationTargetProvider.getLecturePostTarget(post, course);
        createAndCheckPostNotification();
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of NEW_REPLY_FOR_COURSE_POST.
     * I.e. notifications that originate from a new reply for a course post.
     */
    @Test
    public void createNotification_withNotificationType_NewReplyForCoursePost() {
        notificationType = NEW_REPLY_FOR_COURSE_POST;
        expectedTitle = NEW_REPLY_FOR_COURSE_POST_TITLE;
        expectedText = POST_NOTIFICATION_TEXT;
        expectedPriority = MEDIUM;
        expectedTransientTarget = notificationTargetProvider.getCoursePostTarget(post, course);
        createAndCheckPostNotification();
    }

    /// Test for Notifications based on Exercises

    /**
     * Tests the functionality that deals with notifications that have the notification type of FILE_SUBMIT_SUCCESSFUL.
     * I.e. notifications that originate when a user successfully submitted a file upload exercise.
     */
    @Test
    public void createNotification_withNotificationType_FileSubmitSuccessful() {
        notificationType = FILE_SUBMISSION_SUCCESSFUL;
        expectedTitle = FILE_SUBMISSION_SUCCESSFUL_TITLE;
        expectedText = "Your file for the exercise \"" + exercise.getTitle() + "\" was successfully submitted.";
        expectedPriority = MEDIUM;
        expectedTransientTarget = notificationTargetProvider.getExerciseTarget(exercise, FILE_SUBMISSION_SUCCESSFUL_TITLE);
        createAndCheckExerciseNotification();
    }
}
