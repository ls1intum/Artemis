package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.config.Constants.TEST_CASES_DUPLICATE_NOTIFICATION;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.*;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;

public class GroupNotificationFactoryTest {

    @Autowired
    private static GroupNotificationFactory groupNotificationFactory;

    @Mock
    private User user;

    @Mock
    private GroupNotificationType groupNotificationType;

    @Mock
    private static Lecture lecture;

    private static Long lectureId = 0L;

    @Mock
    private static Course course;

    private static Long courseId = 1L;

    @Mock
    private static Exam exam;

    private static Long examId = 27L;

    @Mock
    private static Attachment attachment;

    @Mock
    private static Exercise exercise;

    private static Long exerciseId = 42L;

    @Mock
    private static ProgrammingExercise programmingExercise;

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

    private static List<String> archiveErrors = new ArrayList();

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

        exam = mock(Exam.class);
        when(exam.getId()).thenReturn(examId);
        when(exam.getCourse()).thenReturn(course);

        exercise = mock(Exercise.class);
        when(exercise.getId()).thenReturn(exerciseId);
        when(exercise.getTitle()).thenReturn("exercise title");
        when(exercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);
        when(exercise.getExamViaExerciseGroupOrCourseMember()).thenReturn(exam);
        when(exercise.getProblemStatement()).thenReturn("problem statement");

        programmingExercise = mock(ProgrammingExercise.class);
        when(programmingExercise.getId()).thenReturn(exerciseId);
        when(programmingExercise.getTitle()).thenReturn("exercise title");
        when(programmingExercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);
        when(programmingExercise.getExamViaExerciseGroupOrCourseMember()).thenReturn(exam);
        when(programmingExercise.getProblemStatement()).thenReturn("problem statement");

        attachment = mock(Attachment.class);
        when(attachment.getLecture()).thenReturn(lecture);

        post = mock(Post.class);
        when(post.getExercise()).thenReturn(exercise);
        when(post.getLecture()).thenReturn(lecture);

        answerPost = mock(AnswerPost.class);
        when(answerPost.getPost()).thenReturn(post);
    }

    /**
     * Shared collection of assertions that check if the created notification is correct
     * @param createdNotification is the notification that should be checked for correctness.
     * @param expectedTitle is the expected title that the notification should have.
     * @param expectedText is the expected text that the notification should have.
     * @param expectedTarget is the expected target that the notification should have.
     * @param expectedPriority is the expected priority that the notification should have.
     */
    private void checkCreatedNotification(GroupNotification createdNotification, String expectedTitle, String expectedText, String expectedTarget,
            NotificationPriority expectedPriority) {
        assertThat(createdNotification.getTitle()).isEqualTo(expectedTitle);
        assertThat(createdNotification.getText()).isEqualTo(expectedText);
        assertThat(createdNotification.getTarget()).isEqualTo(expectedTarget);
        assertThat(createdNotification.getPriority()).isEqualTo(expectedPriority);
        assertThat(createdNotification.getAuthor()).isEqualTo(user);
    }

    /**
     * Shortcut method to call the checks for the created notification that has a manually set notification text.
     */
    private void checkCreatedNotificationWithNotificationText() {
        checkCreatedNotification(createdNotification, expectedTitle, notificationText, expectedTarget, expectedPriority);
    }

    /**
     * Shortcut method to call the checks for the created notification that has no manually set notification text but instead a different expected text.
     */
    private void checkCreatedNotificationWithoutNotificationText() {
        checkCreatedNotification(createdNotification, expectedTitle, expectedText, expectedTarget, expectedPriority);
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
     * Auxiliary method to create the most common expected target with specific properties.
     * @param message is the message that should be included in the notification's target.
     * @param entity is the entity that should be pointed at in the notification's target.
     * @param relevantIdForCurrentTestCase is the id of a relevant object that should be part of the notification's target.
     * @param mainPage is the mainPage that should be opened in the end
     * @return is the final notification target as a String.
     */
    private String createDefaultExpectedTarget(String message, String entity, Long relevantIdForCurrentTestCase, String mainPage) {
        return "{\"message\":\"" + message + "\",\"id\":" + relevantIdForCurrentTestCase + ",\"entity\":\"" + entity + "\",\"course\":" + courseId + ",\"mainPage\":\"" + mainPage
                + "\"}";
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
     * Auxiliary method to create the most common expected target for Course Post Notifications with specific properties.
     * @param postId is the id of the post
     * @param courseId is the course id that is needed for the url
     * @return is the final notification target as a String.
     */
    private String createExpectedTargetForPosts(Long postId, Long courseId) {
        return "{\"id\":" + postId + ",\"course\":" + courseId + "}";
    }

    private enum Base {
        ATTACHMENT, EXERCISE, POST, COURSE, EXAM
    }

    /**
     * Calls the real createNotification method of the groupNotificationFactory and tests if the result is correct.
     * Two notifications are created for those cases that might use a manually set notification text
     * @param base is the first input parameter used in the respective factory method to create the group notification.
     */
    private void createAndCheckNotification(Base base) {
        switch (base) {
            case ATTACHMENT: {
                createdNotification = groupNotificationFactory.createNotification(attachment, user, groupNotificationType, notificationType, notificationText);
                checkCreatedNotificationWithNotificationText();
                createdNotification = groupNotificationFactory.createNotification(attachment, user, groupNotificationType, notificationType, null);
                break;
            }
            case EXERCISE: {
                if (notificationType == DUPLICATE_TEST_CASE) {
                    createdNotification = groupNotificationFactory.createNotification(programmingExercise, user, groupNotificationType, notificationType, notificationText);
                    checkCreatedNotificationWithNotificationText();
                    // duplicate test cases always have a notification text
                }
                else {
                    createdNotification = groupNotificationFactory.createNotification(exercise, user, groupNotificationType, notificationType, notificationText);
                    checkCreatedNotificationWithNotificationText();
                    createdNotification = groupNotificationFactory.createNotification(exercise, user, groupNotificationType, notificationType, null);
                }
                break;
            }
            case POST: {
                createdNotification = groupNotificationFactory.createNotification(post, user, groupNotificationType, notificationType, course);
                break;
            }
            case COURSE: {
                createdNotification = groupNotificationFactory.createNotification(course, user, groupNotificationType, notificationType, archiveErrors);
                break;
            }
            case EXAM: {
                createdNotification = groupNotificationFactory.createNotification(exam, user, groupNotificationType, notificationType, archiveErrors);
                break;
            }
        }
        checkCreatedNotificationWithoutNotificationText();
    }

    // Based on Attachment

    /**
     * Tests the functionality of the group notification factory that deals with notifications that originate from attachments.
     */
    @Test
    public void createNotificationBasedOnAttachment() {
        notificationType = ATTACHMENT_CHANGE;
        expectedTitle = ATTACHMENT_CHANGE_TITLE;
        expectedText = "Attachment \"" + attachment.getName() + "\" updated.";
        expectedTarget = createDefaultExpectedTarget("attachmentUpdated", "lectures", lectureId);
        expectedPriority = MEDIUM;
        createAndCheckNotification(Base.ATTACHMENT);
    }

    // Based on Exercise

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXERCISE_CREATED.
     * I.e. notifications that originate from a (newly) created exercise.
     */
    @Test
    public void createNotificationBasedOnExercise_withNotificationType_ExerciseCreated() {
        notificationType = EXERCISE_RELEASED;
        expectedTitle = EXERCISE_RELEASED_TITLE;
        expectedText = "A new exercise \"" + exercise.getTitle() + "\" got released.";
        expectedTarget = createDefaultExpectedTarget("exerciseReleased", "exercises", exerciseId);
        expectedPriority = MEDIUM;
        createAndCheckNotification(Base.EXERCISE);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXERCISE_PRACTICE.
     * I.e. notifications that originate from (quiz)exercises that were (just) opened for practice.
     */
    @Test
    public void createNotificationBasedOnExercise_withNotificationType_ExercisePractice() {
        notificationType = EXERCISE_PRACTICE;
        expectedTitle = EXERCISE_PRACTICE_TITLE;
        expectedText = "Exercise \"" + exercise.getTitle() + "\" is now open for practice.";
        expectedTarget = createDefaultExpectedTarget("exerciseUpdated", "exercises", exerciseId);
        expectedPriority = MEDIUM;
        createAndCheckNotification(Base.EXERCISE);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of QUIZ_EXERCISE_STARTED.
     * I.e. notifications that originate from (just) started quiz exercises.
     */
    @Test
    public void createNotificationBasedOnExercise_withNotificationType_QuizExerciseStarted() {
        notificationType = QUIZ_EXERCISE_STARTED;
        expectedTitle = QUIZ_EXERCISE_STARTED_TITLE;
        expectedText = "Quiz \"" + exercise.getTitle() + "\" just started.";
        expectedTarget = createDefaultExpectedTarget("exerciseUpdated", "exercises", exerciseId);
        expectedPriority = MEDIUM;
        createAndCheckNotification(Base.EXERCISE);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of PROGRAMMING_TEST_CASES_CHANGED.
     * I.e. notifications that originate from changed test cases for programming exercises (after at least one student already started the exercise).
     */
    @Test
    public void createNotificationBasedOnExercise_withNotificationType_ProgrammingTestCasesChanged() {
        notificationType = PROGRAMMING_TEST_CASES_CHANGED;
        expectedTitle = PROGRAMMING_TEST_CASES_CHANGED_TITLE;
        expectedText = "The test cases of the programming exercise \"" + exercise.getTitle() + "\" in the course \"" + exercise.getCourseViaExerciseGroupOrCourseMember().getTitle()
                + "\" were updated." + " The students' submissions should be rebuilt and tested in order to create new results.";
        expectedTarget = createDefaultExpectedTarget("exerciseUpdated", "exercises", exerciseId);
        expectedPriority = MEDIUM;
        createAndCheckNotification(Base.EXERCISE);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXERCISE_UPDATED and are part of an exam.
     * I.e. notifications that originate from an updated exam exercise.
     */
    @Test
    public void createNotificationBasedOnExercise_withNotificationType_ExerciseUpdated_ExamExercise() {
        notificationType = EXERCISE_UPDATED;

        when(exercise.isExamExercise()).thenReturn(true);

        expectedTitle = LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE;
        expectedPriority = HIGH;
        expectedText = "Exam Exercise \"" + exercise.getTitle() + "\" updated.";
        expectedTarget = "{\"problemStatement\":\"" + exercise.getProblemStatement() + "\",\"exercise\":" + exerciseId + ",\"exam\":" + examId + ",\"entity\":\"exams\",\"course\":"
                + courseId + ",\"mainPage\":\"courses\"}";

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

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXERCISE_UPDATED and are course exercises.
     * I.e. notifications that originate from an updated course exercise (not part of an exam).
     */
    @Test
    public void createNotificationBasedOnExercise_withNotificationType_ExerciseUpdated_CourseExercise() {
        notificationType = EXERCISE_UPDATED;
        expectedTitle = EXERCISE_UPDATED_TITLE;
        expectedText = "Exercise \"" + exercise.getTitle() + "\" updated.";
        expectedPriority = MEDIUM;
        expectedTarget = createDefaultExpectedTarget("exerciseUpdated", "exercises", exerciseId);
        createAndCheckNotification(Base.EXERCISE);
    }

    // Based on Post

    /**
     * Tests the functionality that deals with notifications that have the notification type of NEW_EXERCISE_POST.
     * I.e. notifications that originate from a new post concerning an exercise.
     */
    @Test
    public void createNotificationBasedOnPost_withNotificationType_NewExercisePost() {
        notificationType = NEW_EXERCISE_POST;
        expectedTitle = NEW_EXERCISE_POST_TITLE;
        expectedText = "Exercise \"" + exercise.getTitle() + "\" got a new post.";
        expectedPriority = MEDIUM;
        expectedTarget = createExpectedTargetForPosts(post.getId(), "exerciseId", post.getExercise().getId(), courseId);
        createAndCheckNotification(Base.POST);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of NEW_LECTURE_POST.
     * I.e. notifications that originate from a new post concerning a lecture.
     */
    @Test
    public void createNotificationBasedOnPost_withNotificationType_NewLecturePost() {
        notificationType = NEW_LECTURE_POST;
        expectedTitle = NEW_LECTURE_POST_TITLE;
        expectedText = "Lecture \"" + lecture.getTitle() + "\" got a new post.";
        expectedPriority = MEDIUM;
        expectedTarget = createExpectedTargetForPosts(post.getId(), "lectureId", post.getLecture().getId(), courseId);
        createAndCheckNotification(Base.POST);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of NEW_COURSE_POST.
     * I.e. notifications that originate from a new course wide post.
     */
    @Test
    public void createNotificationBasedOnPost_withNotificationType_NewCoursePost() {
        notificationType = NEW_COURSE_POST;
        expectedTitle = NEW_COURSE_POST_TITLE;
        expectedText = "Course \"" + course.getTitle() + "\" got a new course-wide post.";
        expectedPriority = MEDIUM;
        expectedTarget = createExpectedTargetForPosts(post.getId(), courseId);
        createAndCheckNotification(Base.POST);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of NEW_ANNOUNCEMENT_POST.
     * I.e. notifications that originate from a new announcement post.
     */
    @Test
    public void createNotificationBasedOnPost_withNotificationType_NewAnnouncementPost() {
        notificationType = NEW_ANNOUNCEMENT_POST;
        expectedTitle = NEW_ANNOUNCEMENT_POST_TITLE;
        expectedText = "Course \"" + course.getTitle() + "\" got a new announcement.";
        expectedPriority = MEDIUM;
        expectedTarget = createExpectedTargetForPosts(post.getId(), courseId);
        createAndCheckNotification(Base.POST);
    }

    // Based on ResponsePost

    /**
     * Tests the functionality that deals with notifications that have the notification type of NEW_REPLY_FOR_EXERCISE_POST.
     * I.e. notifications that originate from a new reply for a exercise post.
     */
    @Test
    public void createNotificationBasedOnAnswerPost_withNotificationType_NewReplyForExercisePost() {
        notificationType = NEW_REPLY_FOR_EXERCISE_POST;
        expectedTitle = NEW_REPLY_FOR_EXERCISE_POST_TITLE;
        expectedText = "Exercise \"" + exercise.getTitle() + "\" got a new reply.";
        expectedPriority = MEDIUM;
        expectedTarget = createExpectedTargetForPosts(post.getId(), "exerciseId", post.getExercise().getId(), courseId);
        createAndCheckNotification(Base.POST);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of NEW_REPLY_FOR_LECTURE_POST.
     * I.e. notifications that originate from a new reply for a lecture post.
     */
    @Test
    public void createNotificationBasedOnAnswerPost_withNotificationType_NewResponseForLecturePost() {
        notificationType = NEW_REPLY_FOR_LECTURE_POST;
        expectedTitle = NEW_REPLY_FOR_LECTURE_POST_TITLE;
        expectedText = "Lecture \"" + lecture.getTitle() + "\" got a new reply.";
        expectedPriority = MEDIUM;
        expectedTarget = createExpectedTargetForPosts(post.getId(), "lectureId", post.getLecture().getId(), courseId);
        createAndCheckNotification(Base.POST);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of NEW_REPLY_FOR_COURSE_POST.
     * I.e. notifications that originate from a new reply for a course wide post.
     */
    @Test
    public void createNotificationBasedOnAnswerPost_withNotificationType_NewResponseForCoursePost() {
        notificationType = NEW_REPLY_FOR_COURSE_POST;
        expectedTitle = NEW_REPLY_FOR_COURSE_POST_TITLE;
        expectedText = "Course-wide post in course \"" + course.getTitle() + "\" got a new reply.";
        expectedPriority = MEDIUM;
        expectedTarget = createExpectedTargetForPosts(post.getId(), courseId);
        createAndCheckNotification(Base.POST);
    }

    // Based on Course

    /**
     * Tests the functionality that deals with notifications that have the notification type of COURSE_ARCHIVE_STARTED.
     * I.e. notifications that are created when the process of archiving a course has been started.
     */
    @Test
    public void createNotificationBasedOnCourse_withNotificationType_CourseArchiveStarted() {
        notificationType = COURSE_ARCHIVE_STARTED;
        expectedTitle = COURSE_ARCHIVE_STARTED_TITLE;
        expectedText = "The course \"" + course.getTitle() + "\" is being archived.";
        expectedPriority = MEDIUM;
        expectedTarget = createDefaultExpectedTarget("courseArchiveUpdated", "courses", courseId);
        createAndCheckNotification(Base.COURSE);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of COURSE_ARCHIVE_FINISHED.
     * I.e. notifications that are created when the process of archiving a course has finished.
     */
    @Test
    public void createNotificationBasedOnCourse_withNotificationType_CourseArchiveFinished() {
        notificationType = COURSE_ARCHIVE_FINISHED;
        expectedTitle = COURSE_ARCHIVE_FINISHED_TITLE;
        expectedText = "The course \"" + course.getTitle() + "\" has been archived.";
        expectedPriority = MEDIUM;
        expectedTarget = createDefaultExpectedTarget("courseArchiveUpdated", "courses", courseId);
        createAndCheckNotification(Base.COURSE);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of COURSE_ARCHIVE_FAILED.
     * I.e. notifications that are created when the process of archiving a course has failed.
     */
    @Test
    public void createNotificationBasedOnCourse_withNotificationType_CourseArchiveFailed() {
        notificationType = COURSE_ARCHIVE_FAILED;
        expectedTitle = COURSE_ARCHIVE_FAILED_TITLE;
        expectedText = "The was a problem archiving course \"" + course.getTitle() + "\": <br/><br/>" + String.join("<br/><br/>", archiveErrors);
        expectedPriority = MEDIUM;
        expectedTarget = createDefaultExpectedTarget("courseArchiveUpdated", "courses", courseId);
        createAndCheckNotification(Base.COURSE);
    }

    // Based on Exam

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXAM_ARCHIVE_STARTED.
     * I.e. notifications that are created when the process of archiving an exam has been started.
     */
    @Test
    public void createNotificationBasedOnExam_withNotificationType_ExamArchiveStarted() {
        notificationType = EXAM_ARCHIVE_STARTED;
        expectedTitle = EXAM_ARCHIVE_STARTED_TITLE;
        expectedText = "The exam \"" + exam.getTitle() + "\" is being archived.";
        expectedPriority = MEDIUM;
        expectedTarget = createDefaultExpectedTarget("examArchiveUpdated", "courses", courseId);
        createAndCheckNotification(Base.EXAM);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXAM_ARCHIVE_FINISHED.
     * I.e. notifications that are created when the process of archiving an exam has finished.
     */
    @Test
    public void createNotificationBasedOnExam_withNotificationType_ExamArchiveFinished() {
        notificationType = EXAM_ARCHIVE_FINISHED;
        expectedTitle = EXAM_ARCHIVE_FINISHED_TITLE;
        expectedText = "The exam \"" + exam.getTitle() + "\" has been archived.";
        expectedPriority = MEDIUM;
        expectedTarget = createDefaultExpectedTarget("examArchiveUpdated", "courses", courseId);
        createAndCheckNotification(Base.EXAM);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXAM_ARCHIVE_FAILED.
     * I.e. notifications that are created when the process of archiving an exam has failed.
     */
    @Test
    public void createNotificationBasedOnExam_withNotificationType_ExamArchiveFailed() {
        notificationType = EXAM_ARCHIVE_FAILED;
        expectedTitle = EXAM_ARCHIVE_FAILED_TITLE;
        expectedText = "The was a problem archiving exam \"" + exam.getTitle() + "\": <br/><br/>" + String.join("<br/><br/>", archiveErrors);
        expectedPriority = MEDIUM;
        expectedTarget = createDefaultExpectedTarget("examArchiveUpdated", "courses", courseId);
        createAndCheckNotification(Base.EXAM);
    }

    // Critical Situations (e.g. Duplicate Test Cases)

    /**
     * Tests the functionality that deals with notifications that have the notification type of DUPLICATE_TEST_CASE.
     * I.e. notifications that are created when duplicate test cases (in programming exercises) occur.
     */
    @Test
    public void createNotificationBasedOnExam_withNotificationType_DuplicateTestCase() {
        notificationType = DUPLICATE_TEST_CASE;
        expectedTitle = DUPLICATE_TEST_CASE_TITLE;
        Set<String> duplicateFeedbackNames = Set.of("TestCaseA", "TestCaseB");
        notificationText = TEST_CASES_DUPLICATE_NOTIFICATION + String.join(", ", duplicateFeedbackNames);
        expectedText = notificationText;
        expectedPriority = HIGH;
        expectedTarget = createDefaultExpectedTarget("duplicateTestCase", "programming-exercises", exerciseId, "course-management");
        createAndCheckNotification(Base.EXERCISE);
    }

}
