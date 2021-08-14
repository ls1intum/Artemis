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
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;

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

    @Mock
    private static Post post;

    @Mock
    private static AnswerPost answerPost;

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

        post = mock(Post.class);
        when(post.getExercise()).thenReturn(exercise);
        when(post.getLecture()).thenReturn(lecture);

        answerPost = mock(AnswerPost.class);
        when(answerPost.getPost()).thenReturn(post);
    }

    private void checkCreatedNotification(GroupNotification createdNotification, String expectedTitle, String expectedText, String expectedTarget,
            NotificationPriority expectedPriority) {
        assertThat(createdNotification.getTitle()).isEqualTo(expectedTitle);
        assertThat(createdNotification.getText()).isEqualTo(expectedText);
        assertThat(createdNotification.getTarget()).isEqualTo(expectedTarget);
        assertThat(createdNotification.getPriority()).isEqualTo(expectedPriority);
        assertThat(createdNotification.getAuthor()).isEqualTo(user);
    }

    private void checkCreatedNotificationWithNotificationText() {
        checkCreatedNotification(createdNotification, expectedTitle, notificationText, expectedTarget, expectedPriority);
    }

    private void checkCreatedNotificationWithoutNotificationText() {
        checkCreatedNotification(createdNotification, expectedTitle, expectedText, expectedTarget, expectedPriority);
    }

    private String createDefaultExpectedTarget(String message, String entity) {
        return "{\"message\":\"" + message + "\",\"id\":" + exercise.getId() + ",\"entity\":\"" + entity + "\",\"course\":" + courseId + ",\"mainPage\":\"courses\"}";
    }

    private enum Base {
        ATTACHMENT, EXERCISE, POST, ANSWER_POST
    }

    /** Calls the real createNotification method of the groupNotificationFactory and tests if the result is correct.
     * Usually two notifications are created for each case with and without the use of a notification text
     * @param base is the first input parameter used in the respective factory method to create the group notification.
     */
    private void createAndCheckNotification(Base base) {
        switch (base) {
            case ATTACHMENT: {
                createdNotification = groupNotificationFactory.createNotification(attachment, user, groupNotificationType, notificationType, notificationText);
                checkCreatedNotificationWithNotificationText();
                createdNotification = groupNotificationFactory.createNotification(attachment, user, groupNotificationType, notificationType, null);
                checkCreatedNotificationWithoutNotificationText();
                break;
            }
            case EXERCISE: {
                createdNotification = groupNotificationFactory.createNotification(exercise, user, groupNotificationType, notificationType, notificationText);
                checkCreatedNotificationWithNotificationText();
                createdNotification = groupNotificationFactory.createNotification(exercise, user, groupNotificationType, notificationType, null);
                checkCreatedNotificationWithoutNotificationText();
                break;
            }
            case POST: {
                // post base method call does not utilize manually set notification text, thus only one notification is created
                createdNotification = groupNotificationFactory.createNotification(post, user, groupNotificationType, notificationType);
                checkCreatedNotification(createdNotification, expectedTitle, expectedText, expectedTarget, expectedPriority);
                break;
            }
            case ANSWER_POST: {
                // answer post also do not use manually set notification texts
                createdNotification = groupNotificationFactory.createNotification(answerPost, user, groupNotificationType, notificationType);
                checkCreatedNotification(createdNotification, expectedTitle, expectedText, expectedTarget, expectedPriority);
                break;
            }
        }
    }

    // Based on Attachment

    @Test
    public void createNotificationBasedOnAttachment() {

        notificationType = NotificationType.ATTACHMENT_CHANGE;
        expectedTitle = "Attachment updated";
        expectedText = "Attachment \"" + attachment.getName() + "\" updated.";
        expectedTarget = "{\"message\":\"attachmentUpdated\",\"id\":" + lectureId + ",\"entity\":\"lectures\",\"course\":" + courseId + ",\"mainPage\":\"courses\"}";
        expectedPriority = NotificationPriority.MEDIUM;

        createAndCheckNotification(Base.ATTACHMENT);
    }

    // Based on Exercise

    @Test
    public void createNotificationBasedOnExercise_withNotificationType_ExerciseCreated() {

        notificationType = NotificationType.EXERCISE_CREATED;
        expectedTitle = "Exercise created";
        expectedText = "A new exercise \"" + exercise.getTitle() + "\" got created.";
        expectedTarget = createDefaultExpectedTarget("exerciseCreated", "exercises");
        expectedPriority = NotificationPriority.MEDIUM;

        createAndCheckNotification(Base.EXERCISE);
    }

    @Test
    public void createNotificationBasedOnExercise_withNotificationType_ExercisePractice() {

        notificationType = NotificationType.EXERCISE_PRACTICE;
        expectedTitle = "Exercise open for practice";
        expectedText = "Exercise \"" + exercise.getTitle() + "\" is now open for practice.";
        expectedTarget = createDefaultExpectedTarget("exerciseUpdated", "exercises");
        expectedPriority = NotificationPriority.MEDIUM;

        createAndCheckNotification(Base.EXERCISE);
    }

    @Test
    public void createNotificationBasedOnExercise_withNotificationType_QuizExerciseStarted() {

        notificationType = NotificationType.QUIZ_EXERCISE_STARTED;
        expectedTitle = "Quiz started";
        expectedText = "Quiz \"" + exercise.getTitle() + "\" just started.";
        expectedTarget = createDefaultExpectedTarget("exerciseUpdated", "exercises");
        expectedPriority = NotificationPriority.MEDIUM;

        createAndCheckNotification(Base.EXERCISE);
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

        // EXERCISE_UPDATED's implementation differs from the other types therefore the testing has to be adjusted (more explicit)

        // with notification text -> exam popup
        createdNotification = groupNotificationFactory.createNotification(exercise, user, groupNotificationType, notificationType, notificationText);
        checkCreatedNotificationWithNotificationText();

        // without notification text -> silent exam update (expectedText = null)
        createdNotification = groupNotificationFactory.createNotification(exercise, user, groupNotificationType, notificationType, null);
        checkCreatedNotification(createdNotification, expectedTitle, null, expectedTarget, NotificationPriority.HIGH);

        // set behavior back to course exercise
        when(exercise.isExamExercise()).thenReturn(false);
    }

    @Test
    public void createNotificationBasedOnExercise_withNotificationType_ExerciseUpdated_CourseExercise() {

        notificationType = NotificationType.EXERCISE_UPDATED;

        expectedTitle = "Exercise updated";
        expectedText = "Exercise \"" + exercise.getTitle() + "\" updated.";
        expectedPriority = NotificationPriority.MEDIUM;
        expectedTarget = createDefaultExpectedTarget("exerciseUpdated", "exercises");

        createAndCheckNotification(Base.EXERCISE);
    }

    // Based on Post

    @Test
    public void createNotificationBasedOnPost_withNotificationType_NewPostForExercise() {

        notificationType = NotificationType.NEW_POST_FOR_EXERCISE;

        expectedTitle = "New Post";
        expectedText = "Exercise \"" + exercise.getTitle() + "\" got a new post.";
        expectedPriority = NotificationPriority.MEDIUM;
        expectedTarget = createDefaultExpectedTarget("newPost", "exercises");

        createAndCheckNotification(Base.POST);
    }

    @Test
    public void createNotificationBasedOnPost_withNotificationType_NewPostForLecture() {

        notificationType = NotificationType.NEW_POST_FOR_LECTURE;

        expectedTitle = "New Post";
        expectedText = "Lecture \"" + lecture.getTitle() + "\" got a new post.";
        expectedPriority = NotificationPriority.MEDIUM;
        expectedTarget = createDefaultExpectedTarget("newPost", "lectures");

        createAndCheckNotification(Base.POST);
    }

    // Based on AnswerPost

    @Test
    public void createNotificationBasedOnPost_withNotificationType_NewAnswerPostForExercise() {

        notificationType = NotificationType.NEW_ANSWER_POST_FOR_EXERCISE;

        expectedTitle = "New Reply";
        expectedText = "Exercise \"" + exercise.getTitle() + "\" got a new reply.";
        expectedPriority = NotificationPriority.MEDIUM;
        expectedTarget = createDefaultExpectedTarget("newAnswerPost", "exercises");

        createAndCheckNotification(Base.ANSWER_POST);
    }
}
