package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.config.Constants.TEST_CASES_DUPLICATE_NOTIFICATION;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.HIGH;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.MEDIUM;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.GroupNotificationFactory.createAnnouncementNotification;
import static de.tum.in.www1.artemis.domain.notification.GroupNotificationFactory.createNotification;
import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.EXERCISE_RELEASED_TEXT;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.EXERCISE_UPDATED_TEXT;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;

class GroupNotificationFactoryTest {

    private static Lecture lecture;

    private static final Long LECTURE_ID = 0L;

    private static final String LECTURE_TITLE = "lecture title";

    private static Course course;

    private static final Long COURSE_ID = 1L;

    private static final String COURSE_TITLE = "course title";

    private static Exam exam;

    private static final Long EXAM_ID = 27L;

    private static Attachment attachment;

    private static Exercise exercise;

    private static ProgrammingExercise programmingExercise;

    private static final Long EXERCISE_ID = 42L;

    private static final String EXERCISE_TITLE = "exercise title";

    private static Exercise examExercise;

    private static final String PROBLEM_STATEMENT = "problem statement";

    private static Post post;

    private static final String POST_TITLE = "post title";

    private static final String POST_CONTENT = "post content";

    private static final String ANSWER_POST_CONTENT = "answer post content";

    private static User user = new User();

    private String expectedTitle;

    private String expectedText;

    private NotificationTarget expectedTransientTarget;

    private NotificationPriority expectedPriority;

    private GroupNotification createdNotification;

    private NotificationType notificationType;

    private final GroupNotificationType groupNotificationType = GroupNotificationType.STUDENT;

    private static String notificationText = "notification text";

    private static final List<String> archiveErrors = List.of("archive error 1", "archive error 2");

    private enum Base {
        ATTACHMENT, EXERCISE, POST, POST_REPLY, COURSE, EXAM
    }

    /**
     * sets up all needed mocks and their wanted behavior once for all test cases.
     */
    @BeforeAll
    static void setUp() {
        course = new Course();
        course.setId(COURSE_ID);
        course.setTitle(COURSE_TITLE);

        lecture = new Lecture();
        lecture.setId(LECTURE_ID);
        lecture.setCourse(course);
        lecture.setTitle(LECTURE_TITLE);

        exam = new Exam();
        exam.setId(EXAM_ID);
        exam.setCourse(course);

        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setExam(exam);

        exercise = new TextExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setTitle(EXERCISE_TITLE);
        exercise.setCourse(course);
        exercise.setProblemStatement(PROBLEM_STATEMENT);

        programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(EXERCISE_ID);
        programmingExercise.setTitle(EXERCISE_TITLE);
        programmingExercise.setCourse(course);
        programmingExercise.setProblemStatement(PROBLEM_STATEMENT);

        examExercise = new TextExercise();
        examExercise.setId(EXERCISE_ID);
        examExercise.setTitle(EXERCISE_TITLE);
        examExercise.setCourse(course);
        examExercise.setExerciseGroup(exerciseGroup);
        examExercise.setProblemStatement(PROBLEM_STATEMENT);

        attachment = new Attachment();
        attachment.setLecture(lecture);
        attachment.setExercise(exercise);

        user = new User();
        user.setFirstName("John");
        user.setLastName("Smith");

        post = new Post();
        post.setConversation(new Channel());
        post.setAuthor(user);
        post.setTitle(POST_TITLE);
        post.setContent(POST_CONTENT);

        AnswerPost answerPost = new AnswerPost();
        answerPost.setPost(post);
        answerPost.setAuthor(user);
        answerPost.setContent(ANSWER_POST_CONTENT);
    }

    /**
     * Shared collection of assertions that check if the created notification is correct
     *
     * @param createdNotification     is the notification that should be checked for correctness.
     * @param expectedTitle           is the expected title that the notification should have.
     * @param expectedText            is the expected text that the notification should have.
     * @param expectedTransientTarget is the expected target that the notification should have.
     * @param expectedPriority        is the expected priority that the notification should have.
     */
    private void checkCreatedNotification(GroupNotification createdNotification, String expectedTitle, String expectedText, NotificationTarget expectedTransientTarget,
            NotificationPriority expectedPriority, boolean expectedTextIsPlaceholder) {
        assertThat(createdNotification.getTitle()).as("Created notification title should match expected one").isEqualTo(expectedTitle);
        assertThat(createdNotification.getText()).as("Created notification text should match expected one").isEqualTo(expectedText);
        assertThat(createdNotification.getTextIsPlaceholder()).as("Created notification placeholder flag should match expected one").isEqualTo(expectedTextIsPlaceholder);
        assertThat(createdNotification.getTarget()).as("Created notification target should match expected one").isEqualTo(expectedTransientTarget.toJsonString());
        assertThat(createdNotification.getPriority()).as("Created notification priority should match expected one").isEqualTo(expectedPriority);
        assertThat(createdNotification.getAuthor()).as("Created notification author should match expected one").isEqualTo(user);
    }

    /**
     * Shortcut method to call the checks for the created notification that has a manually set notification text.
     */
    private void checkCreatedNotificationWithNotificationText() {
        checkCreatedNotification(createdNotification, expectedTitle, notificationText, expectedTransientTarget, expectedPriority, false);
    }

    /**
     * Shortcut method to call the checks for the created notification that has no manually set notification text but instead a different expected text.
     */
    private void checkCreatedNotificationWithoutNotificationText() {
        checkCreatedNotification(createdNotification, expectedTitle, expectedText, expectedTransientTarget, expectedPriority, true);
    }

    /**
     * Calls the real createNotification method of the groupNotificationFactory and tests if the result is correct.
     * Two notifications are created for those cases that might use a manually set notification text
     *
     * @param base is the first input parameter used in the respective factory method to create the group notification.
     */
    private void createAndCheckNotification(Base base) {
        switch (base) {
            case ATTACHMENT: {
                createdNotification = createNotification(attachment, user, groupNotificationType, notificationType, notificationText);
                checkCreatedNotificationWithNotificationText();
                createdNotification = createNotification(attachment, user, groupNotificationType, notificationType, null);
                break;
            }
            case EXERCISE: {
                if (notificationType == DUPLICATE_TEST_CASE) {
                    createdNotification = createNotification(programmingExercise, user, groupNotificationType, notificationType, notificationText);
                    checkCreatedNotificationWithNotificationText();
                    // duplicate test cases always have a notification text
                    return;
                }
                else {
                    createdNotification = createNotification(exercise, user, groupNotificationType, notificationType, notificationText);
                    checkCreatedNotificationWithNotificationText();
                    createdNotification = createNotification(exercise, user, groupNotificationType, notificationType, null);
                }
                break;
            }
            case POST: {
                createdNotification = createAnnouncementNotification(post, user, groupNotificationType, course);
                break;
            }
            case COURSE: {
                createdNotification = createNotification(course, user, groupNotificationType, notificationType, archiveErrors);
                break;
            }
            case EXAM: {
                createdNotification = createNotification(exam, user, groupNotificationType, notificationType, archiveErrors);
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
    void createNotificationBasedOnAttachment() {
        notificationType = ATTACHMENT_CHANGE;
        expectedTitle = ATTACHMENT_CHANGE_TITLE;
        expectedText = ATTACHMENT_CHANGE_TEXT;
        expectedTransientTarget = createLectureTarget(lecture, ATTACHMENT_UPDATED_TEXT);
        expectedPriority = MEDIUM;
        createAndCheckNotification(Base.ATTACHMENT);
    }

    // Based on Exercise

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXERCISE_CREATED.
     * I.e. notifications that originate from a (newly) created exercise.
     */
    @Test
    void createNotificationBasedOnExercise_withNotificationType_ExerciseCreated() {
        notificationType = EXERCISE_RELEASED;
        expectedTitle = EXERCISE_RELEASED_TITLE;
        expectedText = NotificationConstants.EXERCISE_RELEASED_TEXT;
        expectedTransientTarget = createExerciseTarget(exercise, EXERCISE_RELEASED_TEXT);
        expectedPriority = MEDIUM;
        createAndCheckNotification(Base.EXERCISE);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXERCISE_PRACTICE.
     * I.e. notifications that originate from (quiz)exercises that were (just) opened for practice.
     */
    @Test
    void createNotificationBasedOnExercise_withNotificationType_ExercisePractice() {
        notificationType = EXERCISE_PRACTICE;
        expectedTitle = EXERCISE_PRACTICE_TITLE;
        expectedText = EXERCISE_PRACTICE_TEXT;
        expectedTransientTarget = createExerciseTarget(exercise, EXERCISE_UPDATED_TEXT);
        expectedPriority = MEDIUM;
        createAndCheckNotification(Base.EXERCISE);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of QUIZ_EXERCISE_STARTED.
     * I.e. notifications that originate from (just) started quiz exercises.
     */
    @Test
    void createNotificationBasedOnExercise_withNotificationType_QuizExerciseStarted() {
        notificationType = QUIZ_EXERCISE_STARTED;
        expectedTitle = QUIZ_EXERCISE_STARTED_TITLE;
        expectedText = QUIZ_EXERCISE_STARTED_TEXT;
        expectedTransientTarget = createExerciseTarget(exercise, EXERCISE_UPDATED_TEXT);
        expectedPriority = MEDIUM;
        createAndCheckNotification(Base.EXERCISE);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of PROGRAMMING_TEST_CASES_CHANGED.
     * I.e. notifications that originate from changed test cases for programming exercises (after at least one student already started the exercise).
     */
    @Test
    void createNotificationBasedOnExercise_withNotificationType_ProgrammingTestCasesChanged() {
        notificationType = PROGRAMMING_TEST_CASES_CHANGED;
        expectedTitle = PROGRAMMING_TEST_CASES_CHANGED_TITLE;
        expectedText = PROGRAMMING_TEST_CASES_CHANGED_TEXT;
        expectedTransientTarget = createExerciseTarget(exercise, EXERCISE_UPDATED_TEXT);
        expectedPriority = MEDIUM;
        createAndCheckNotification(Base.EXERCISE);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXERCISE_UPDATED and are part of an exam.
     * I.e. notifications that originate from an updated exam exercise.
     */
    @Test
    void createNotificationBasedOnExercise_withNotificationType_ExerciseUpdated_ExamExercise() {
        notificationType = EXERCISE_UPDATED;

        expectedTitle = LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE;
        expectedPriority = HIGH;
        expectedText = LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TEXT;

        expectedTransientTarget = new NotificationTarget(EXAMS_TEXT, COURSE_ID, COURSES_TEXT);
        expectedTransientTarget.setProblemStatement(examExercise.getProblemStatement());
        expectedTransientTarget.setExerciseId(EXERCISE_ID);
        expectedTransientTarget.setExamId(EXAM_ID);

        // EXERCISE_UPDATED's implementation differs from the other types therefore the testing has to be adjusted (more explicit)

        // with notification text -> exam popup
        createdNotification = createNotification(examExercise, user, groupNotificationType, notificationType, notificationText);
        checkCreatedNotificationWithNotificationText();

        // without notification text -> silent exam update (expectedText = null)
        createdNotification = createNotification(examExercise, user, groupNotificationType, notificationType, null);
        checkCreatedNotification(createdNotification, expectedTitle, null, expectedTransientTarget, HIGH, false);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXERCISE_UPDATED and are course exercises.
     * I.e. notifications that originate from an updated course exercise (not part of an exam).
     */
    @Test
    void createNotificationBasedOnExercise_withNotificationType_ExerciseUpdated_CourseExercise() {
        notificationType = EXERCISE_UPDATED;
        expectedTitle = EXERCISE_UPDATED_TITLE;
        expectedText = NotificationConstants.EXERCISE_UPDATED_TEXT;
        expectedPriority = MEDIUM;
        expectedTransientTarget = createExerciseTarget(exercise, EXERCISE_UPDATED_TEXT);
        createAndCheckNotification(Base.EXERCISE);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of NEW_ANNOUNCEMENT_POST.
     * I.e. notifications that originate from a new announcement post.
     */
    @Test
    void createNotificationBasedOnPost_withNotificationType_NewAnnouncementPost() {
        notificationType = NEW_ANNOUNCEMENT_POST;
        expectedTitle = NEW_ANNOUNCEMENT_POST_TITLE;
        expectedText = NEW_ANNOUNCEMENT_POST_TEXT;
        expectedPriority = MEDIUM;
        expectedTransientTarget = createCoursePostTarget(post, course);
        createAndCheckNotification(Base.POST);
    }

    // Based on Course

    /**
     * Tests the functionality that deals with notifications that have the notification type of COURSE_ARCHIVE_STARTED.
     * I.e. notifications that are created when the process of archiving a course has been started.
     */
    @Test
    void createNotificationBasedOnCourse_withNotificationType_CourseArchiveStarted() {
        notificationType = COURSE_ARCHIVE_STARTED;
        expectedTitle = COURSE_ARCHIVE_STARTED_TITLE;
        expectedText = COURSE_ARCHIVE_STARTED_TEXT;
        expectedPriority = MEDIUM;
        expectedTransientTarget = createCourseTarget(course, COURSE_ARCHIVE_UPDATED_TEXT);
        createAndCheckNotification(Base.COURSE);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of COURSE_ARCHIVE_FINISHED.
     * I.e. notifications that are created when the process of archiving a course has finished.
     */
    @Test
    void createNotificationBasedOnCourse_withNotificationType_CourseArchiveFinished() {
        notificationType = COURSE_ARCHIVE_FINISHED;
        expectedTitle = COURSE_ARCHIVE_FINISHED_TITLE;
        expectedText = COURSE_ARCHIVE_FINISHED_WITH_ERRORS_TEXT;
        expectedPriority = MEDIUM;
        expectedTransientTarget = createCourseTarget(course, COURSE_ARCHIVE_UPDATED_TEXT);
        createAndCheckNotification(Base.COURSE);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of COURSE_ARCHIVE_FAILED.
     * I.e. notifications that are created when the process of archiving a course has failed.
     */
    @Test
    void createNotificationBasedOnCourse_withNotificationType_CourseArchiveFailed() {
        notificationType = COURSE_ARCHIVE_FAILED;
        expectedTitle = COURSE_ARCHIVE_FAILED_TITLE;
        expectedText = COURSE_ARCHIVE_FAILED_TEXT;
        expectedPriority = MEDIUM;
        expectedTransientTarget = createCourseTarget(course, COURSE_ARCHIVE_UPDATED_TEXT);
        createAndCheckNotification(Base.COURSE);
    }

    // Based on Exam

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXAM_ARCHIVE_STARTED.
     * I.e. notifications that are created when the process of archiving an exam has been started.
     */
    @Test
    void createNotificationBasedOnExam_withNotificationType_ExamArchiveStarted() {
        notificationType = EXAM_ARCHIVE_STARTED;
        expectedTitle = EXAM_ARCHIVE_STARTED_TITLE;
        expectedText = EXAM_ARCHIVE_STARTED_TEXT;
        expectedPriority = MEDIUM;
        expectedTransientTarget = createCourseTarget(course, EXAM_ARCHIVE_UPDATED_TEXT);
        createAndCheckNotification(Base.EXAM);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXAM_ARCHIVE_FINISHED.
     * I.e. notifications that are created when the process of archiving an exam has finished.
     */
    @Test
    void createNotificationBasedOnExam_withNotificationType_ExamArchiveFinished() {
        notificationType = EXAM_ARCHIVE_FINISHED;
        expectedTitle = EXAM_ARCHIVE_FINISHED_TITLE;
        expectedText = EXAM_ARCHIVE_FINISHED_WITH_ERRORS_TEXT;
        expectedPriority = MEDIUM;
        expectedTransientTarget = createCourseTarget(course, EXAM_ARCHIVE_UPDATED_TEXT);
        createAndCheckNotification(Base.EXAM);
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXAM_ARCHIVE_FAILED.
     * I.e. notifications that are created when the process of archiving an exam has failed.
     */
    @Test
    void createNotificationBasedOnExam_withNotificationType_ExamArchiveFailed() {
        notificationType = EXAM_ARCHIVE_FAILED;
        expectedTitle = EXAM_ARCHIVE_FAILED_TITLE;
        expectedText = EXAM_ARCHIVE_FAILED_TEXT;
        expectedPriority = MEDIUM;
        expectedTransientTarget = createCourseTarget(course, EXAM_ARCHIVE_UPDATED_TEXT);
        createAndCheckNotification(Base.EXAM);
    }

    // Critical Situations (e.g. Duplicate Test Cases)

    /**
     * Tests the functionality that deals with notifications that have the notification type of DUPLICATE_TEST_CASE.
     * I.e. notifications that are created when duplicate test cases (in programming exercises) occur.
     */
    @Test
    void createNotificationBasedOnExam_withNotificationType_DuplicateTestCase() {
        notificationType = DUPLICATE_TEST_CASE;
        expectedTitle = DUPLICATE_TEST_CASE_TITLE;
        Set<String> duplicateFeedbackNames = Set.of("TestCaseA", "TestCaseB");
        notificationText = TEST_CASES_DUPLICATE_NOTIFICATION + String.join(", ", duplicateFeedbackNames);
        expectedText = notificationText;
        expectedPriority = HIGH;
        expectedTransientTarget = createDuplicateTestCaseTarget(exercise);
        createAndCheckNotification(Base.EXERCISE);
    }

}
