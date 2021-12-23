package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.*;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;
import static de.tum.in.www1.artemis.domain.notification.SingleUserNotificationFactory.createNotification;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;

public class SingleUserNotificationFactoryTest {

    private static Lecture lecture;

    private static final Long LECTURE_ID = 0L;

    private static Course course;

    private static final Long COURSE_ID = 12L;

    private static Exercise exercise;

    private static final Long EXERCISE_ID = 42L;

    private static final String EXERCISE_TITLE = "exercise title";

    private static final String PROBLEM_STATEMENT = "problem statement";

    private static Post post;

    private static AnswerPost answerPost;

    private static final String POST_NOTIFICATION_TEXT = "Your post got replied.";

    private String expectedTitle;

    private String expectedText;

    private NotificationTarget expectedTransientTarget;

    private NotificationPriority expectedPriority;

    private SingleUserNotification createdNotification;

    private NotificationType notificationType;

    private User user = null;

    private static User cheatingUser;

    private final static String USER_LOGIN = "de27sms";

    private static PlagiarismComparison plagiarismComparison;

    private static PlagiarismResult plagiarismResult;

    private static PlagiarismSubmission plagiarismSubmission;

    private static final String PLAGIARISM_INSTRUCTOR_STATEMENT = "You definitely plagiarised! Your answers are identical!";

    /**
     * sets up all needed mocks and their wanted behavior once for all test cases.
     */
    @BeforeAll
    public static void setUp() {
        course = new Course();
        course.setId(COURSE_ID);

        lecture = new Lecture();
        lecture.setId(LECTURE_ID);
        lecture.setCourse(course);

        exercise = new TextExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setTitle(EXERCISE_TITLE);
        exercise.setCourse(course);
        exercise.setProblemStatement(PROBLEM_STATEMENT);

        post = new Post();
        post.setExercise(exercise);
        post.setLecture(lecture);

        answerPost = new AnswerPost();
        answerPost.setPost(post);

        cheatingUser = new User();
        cheatingUser.setLogin(USER_LOGIN);

        plagiarismResult = new TextPlagiarismResult();
        plagiarismResult.setExercise(exercise);

        plagiarismSubmission = new PlagiarismSubmission();
        plagiarismSubmission.setStudentLogin(USER_LOGIN);

        plagiarismComparison = new PlagiarismComparison();
        plagiarismComparison.setInstructorStatementA(PLAGIARISM_INSTRUCTOR_STATEMENT);
        plagiarismComparison.setPlagiarismResult(plagiarismResult);
        plagiarismComparison.setSubmissionA(plagiarismSubmission);
    }

    /// Test for Notifications based on Posts

    /**
     * Calls the real createNotification method of the singleUserNotificationFactory and tests if the result is correct for Post notifications.
     */
    private void createAndCheckPostNotification() {
        createdNotification = createNotification(post, notificationType, course);
        checkNotification();
    }

    /**
     * Calls the real createNotification method of the singleUserNotificationFactory and tests if the result is correct for Exercise notifications.
     */
    private void createAndCheckExerciseNotification() {
        createdNotification = createNotification(exercise, notificationType, user);
        checkNotification();
    }

    /**
     * Calls the real createNotification method of the singleUserNotificationFactory and tests if the result is correct for plagiarism related notifications.
     */
    private void createAndCheckPlagiarismNotification() {
        createdNotification = createNotification(plagiarismComparison, notificationType, cheatingUser, user);
        checkNotification();
    }

    /**
     * Tests if the resulting notification is correct.
     */
    private void checkNotification() {
        assertThat(createdNotification.getTitle()).as("Created notification title should be equal to the expected one").isEqualTo(expectedTitle);
        assertThat(createdNotification.getText()).as("Created notification text should be equal to the expected one").isEqualTo(expectedText);
        assertThat(createdNotification.getTarget()).as("Created notification target should be equal to the expected one").isEqualTo(expectedTransientTarget.toJsonString());
        assertThat(createdNotification.getPriority()).as("Created notification priority should be equal to the expected one").isEqualTo(expectedPriority);
        assertThat(createdNotification.getAuthor()).as("Created notification author should be equal to the expected one").isEqualTo(user);
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
        expectedTransientTarget = createExercisePostTarget(post, course);
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
        expectedTransientTarget = createLecturePostTarget(post, course);
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
        expectedTransientTarget = createCoursePostTarget(post, course);
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
        expectedTransientTarget = createExerciseTarget(exercise, FILE_SUBMISSION_SUCCESSFUL_TITLE);
        createAndCheckExerciseNotification();
    }

    /// Test for Notifications based on Plagiarism

    /**
     * Tests the functionality that deals with notifications that have the notification type of NEW_POSSIBLE_PLAGIARISM_CASE_STUDENT.
     * I.e. notifications that originate when an instructor sets his statement concerning the plagiarism comparison for one of both student sides.
     */
    @Test
    public void createNotification_withNotificationType_NewPossiblePlagiarismCaseStudent() {
        notificationType = NEW_POSSIBLE_PLAGIARISM_CASE_STUDENT;
        expectedTitle = NEW_POSSIBLE_PLAGIARISM_CASE_STUDENT_TITLE;
        expectedText = PLAGIARISM_INSTRUCTOR_STATEMENT;
        expectedPriority = HIGH;
        expectedTransientTarget = createPlagiarismCaseTarget(plagiarismComparison.getId(), COURSE_ID);
        createAndCheckPlagiarismNotification();
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of PLAGIARISM_CASE_FINAL_STATE_STUDENT.
     * I.e. notifications that originate when an instructor sets the final state of a plagiarism comparison.
     */
    @Test
    public void createNotification_withNotificationType_PlagiarismCaseFinalStateStudent() {
        notificationType = PLAGIARISM_CASE_FINAL_STATE_STUDENT;
        expectedTitle = PLAGIARISM_CASE_FINAL_STATE_STUDENT_TITLE;
        expectedText = "Your plagiarism case concerning the " + plagiarismResult.getExercise().getExerciseType().toString().toLowerCase() + " exercise \""
                + plagiarismResult.getExercise().getTitle() + "\"" + " has a final verdict.";
        expectedPriority = HIGH;
        expectedTransientTarget = createPlagiarismCaseTarget(plagiarismComparison.getId(), COURSE_ID);
        createAndCheckPlagiarismNotification();
    }
}
