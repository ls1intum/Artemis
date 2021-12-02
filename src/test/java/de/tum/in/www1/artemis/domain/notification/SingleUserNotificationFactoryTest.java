package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.ExerciseType.TEXT;
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

public class SingleUserNotificationFactoryTest {

    @Autowired
    private static SingleUserNotificationFactory singleUserNotificationFactory;

    @Mock
    private User user;

    @Mock
    private static Lecture lecture;

    private static Long lectureId = 0L;

    @Mock
    private static Course course;

    private static Long courseId = 1L;

    @Mock
    private static Exercise exercise;

    private static Long exerciseId = 42L;

    @Mock
    private static Post post;

    @Mock
    private static AnswerPost answerPost;

    private static final String POST_NOTIFICATION_TEXT = "Your post got replied.";

    private String expectedTitle;

    private String expectedText;

    private String expectedTarget;

    private NotificationPriority expectedPriority;

    private SingleUserNotification createdNotification;

    private NotificationType notificationType;

    /**
     * sets up all needed mocks and their wanted behavior once for all test cases.
     */
    @BeforeAll
    public static void setUp() {
        course = mock(Course.class);
        when(course.getId()).thenReturn(courseId);

        lecture = mock(Lecture.class);
        when(lecture.getId()).thenReturn(lectureId);
        when(lecture.getCourse()).thenReturn(course);

        exercise = mock(Exercise.class);
        when(exercise.getId()).thenReturn(exerciseId);
        when(exercise.getTitle()).thenReturn("exercise title");
        when(exercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);
        when(exercise.getProblemStatement()).thenReturn("problem statement");
        when(exercise.getExerciseType()).thenReturn(TEXT);

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
        assertThat(createdNotification.getTarget()).isEqualTo(expectedTarget);
        assertThat(createdNotification.getPriority()).isEqualTo(expectedPriority);
        assertThat(createdNotification.getAuthor()).isEqualTo(user);
    }

    /**
     * Auxiliary method to create the most common expected target with specific properties.
     * @param message is the message that should be included in the notification's target.
     * @param entity is the entity that should be pointed at in the notification's target.
     * @param relevantIdForCurrentTestCase is the id of a relevant object that should be part of the notification's target.
     * @return is the final notification target as a String.
     */
    private String createDefaultExpectedTarget(String message, String entity, Long relevantIdForCurrentTestCase) {
        return "{\"message\":\"" + message + "\",\"id\":" + relevantIdForCurrentTestCase + ",\"entity\":\"" + entity + "\",\"course\":" + courseId + ",\"mainPage\":\"courses\"}";
    }

    /**
     * Auxiliary method to create the most common expected target for Post Notifications with specific properties.
     * @param postId is the id of the post
     * @param relevantType can be "exerciseId" or "lectureId"
     * @param idForRelevantType is the id of the exercise or lecture
     * @param courseId is the course id that is needed for the url
     * @return is the final notification target as a String.
     */
    private String createExpectedTargetForPosts(Long postId, String relevantType, Long idForRelevantType, Long courseId) {
        return "{\"id\":" + postId + ",\"" + relevantType + "\":" + idForRelevantType + ",\"course\":" + courseId + "}";
    }

    /**
     * Auxiliary method to create the most common expected target for course wide Post Notifications with specific properties.
     * @param postId is the id of the post
     * @param courseId is the course id that is needed for the url
     * @return is the final notification target as a String.
     */
    private String createExpectedTargetForCourseWidePosts(Long postId, Long courseId) {
        return "{\"id\":" + postId + ",\"course\":" + courseId + "}";
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
        expectedTarget = createExpectedTargetForPosts(post.getId(), "exerciseId", post.getExercise().getId(), courseId);
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
        expectedTarget = createExpectedTargetForPosts(post.getId(), "lectureId", post.getLecture().getId(), courseId);
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
        expectedTarget = createExpectedTargetForCourseWidePosts(post.getId(), courseId);
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
        expectedTarget = createDefaultExpectedTarget(FILE_SUBMISSION_SUCCESSFUL_TITLE, "exercises", exerciseId);
        createAndCheckExerciseNotification();
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXERCISE_SUBMISSION_ASSESSED.
     * I.e. notifications that originate when a user's exercise submission has been assessed.
     */
    @Test
    public void createNotification_withNotificationType_() {
        notificationType = EXERCISE_SUBMISSION_ASSESSED;
        expectedTitle = EXERCISE_SUBMISSION_ASSESSED_TITLE;
        expectedText = "Your submission for the " + exercise.getExerciseTypeAsReadableString() + " exercise \"" + exercise.getTitle() + "\" has been assessed.";
        expectedPriority = MEDIUM;
        expectedTarget = createDefaultExpectedTarget(EXERCISE_SUBMISSION_ASSESSED_TITLE, "exercises", exerciseId);
        createAndCheckExerciseNotification();
    }
}
