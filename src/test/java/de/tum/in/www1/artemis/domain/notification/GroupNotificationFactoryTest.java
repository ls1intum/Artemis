package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.config.Constants.TEST_CASES_DUPLICATE_NOTIFICATION;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.*;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.GroupNotificationFactory.createNotification;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
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

public class GroupNotificationFactoryTest {

    private static Lecture lecture;

    private static final Long LECTURE_ID = 0L;

    private static Course course;

    private static final Long COURSE_ID = 1L;

    private static Exam exam;

    private static final Long EXAM_ID = 27L;

    private static Attachment attachment;

    private static Exercise exercise;

    private static ProgrammingExercise programmingExercise;

    private static final Long EXERCISE_ID = 42L;

    private static final String EXERCISE_TITLE = "exercise title";

    private static Exercise examExercise;

    private static ExerciseGroup exerciseGroup;

    private static final String PROBLEM_STATEMENT = "problem statement";

    private static Post post;

    private static AnswerPost answerPost;

    private User user = new User();

    private String expectedTitle;

    private String expectedText;

    private NotificationTarget expectedTransientTarget;

    private NotificationPriority expectedPriority;

    private GroupNotification createdNotification;

    private NotificationType notificationType;

    private GroupNotificationType groupNotificationType = GroupNotificationType.STUDENT;

    private static String notificationText = "notification text";

    private static List<String> archiveErrors = new ArrayList();

    private enum Base {
        ATTACHMENT, EXERCISE, POST, COURSE, EXAM
    }

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

        exam = new Exam();
        exam.setId(EXAM_ID);
        exam.setCourse(course);

        exerciseGroup = new ExerciseGroup();
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

        post = new Post();
        post.setExercise(exercise);
        post.setLecture(lecture);

        answerPost = new AnswerPost();
        answerPost.setPost(post);
    }

    /**
     * Shared collection of assertions that check if the created notification is correct
     * @param createdNotification is the notification that should be checked for correctness.
     * @param expectedTitle is the expected title that the notification should have.
     * @param expectedText is the expected text that the notification should have.
     * @param expectedTransientTarget is the expected target that the notification should have.
     * @param expectedPriority is the expected priority that the notification should have.
     */
    private void checkCreatedNotification(GroupNotification createdNotification, String expectedTitle, String expectedText, NotificationTarget expectedTransientTarget,
            NotificationPriority expectedPriority) {
        assertThat(createdNotification.getTitle()).as("Created notification title should match expected one").isEqualTo(expectedTitle);
        assertThat(createdNotification.getText()).as("Created notification text should match expected one").isEqualTo(expectedText);
        assertThat(createdNotification.getTarget()).as("Created notification target should match expected one").isEqualTo(expectedTransientTarget.toJsonString());
        assertThat(createdNotification.getPriority()).as("Created notification priority should match expected one").isEqualTo(expectedPriority);
        assertThat(createdNotification.getAuthor()).as("Created notification author should match expected one").isEqualTo(user);
    }

    /**
     * Shortcut method to call the checks for the created notification that has a manually set notification text.
     */
    private void checkCreatedNotificationWithNotificationText() {
        checkCreatedNotification(createdNotification, expectedTitle, notificationText, expectedTransientTarget, expectedPriority);
    }

    /**
     * Shortcut method to call the checks for the created notification that has no manually set notification text but instead a different expected text.
     */
    private void checkCreatedNotificationWithoutNotificationText() {
        checkCreatedNotification(createdNotification, expectedTitle, expectedText, expectedTransientTarget, expectedPriority);
    }

    /**
     * Calls the real createNotification method of the groupNotificationFactory and tests if the result is correct.
     * Two notifications are created for those cases that might use a manually set notification text
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
                }
                else {
                    createdNotification = createNotification(exercise, user, groupNotificationType, notificationType, notificationText);
                    checkCreatedNotificationWithNotificationText();
                    createdNotification = createNotification(exercise, user, groupNotificationType, notificationType, null);
                }
                break;
            }
            case POST: {
                createdNotification = createNotification(post, user, groupNotificationType, notificationType, course);
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
    public void createNotificationBasedOnAttachment() {
        notificationType = ATTACHMENT_CHANGE;
        expectedTitle = ATTACHMENT_CHANGE_TITLE;
        expectedText = "Attachment \"" + attachment.getName() + "\" updated.";
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
    public void createNotificationBasedOnExercise_withNotificationType_ExerciseCreated() {
        notificationType = EXERCISE_RELEASED;
        expectedTitle = EXERCISE_RELEASED_TITLE;
        expectedText = "A new exercise \"" + exercise.getTitle() + "\" got released.";
        expectedTransientTarget = createExerciseTarget(exercise, EXERCISE_RELEASED_TEXT);
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
        expectedTransientTarget = createExerciseTarget(exercise, EXERCISE_UPDATED_TEXT);
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
        expectedTransientTarget = createExerciseTarget(exercise, EXERCISE_UPDATED_TEXT);
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
        expectedTransientTarget = createExerciseTarget(exercise, EXERCISE_UPDATED_TEXT);
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

        expectedTitle = LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE;
        expectedPriority = HIGH;
        expectedText = "Exam Exercise \"" + examExercise.getTitle() + "\" updated.";

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
        checkCreatedNotification(createdNotification, expectedTitle, null, expectedTransientTarget, HIGH);
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
        expectedTransientTarget = createExerciseTarget(exercise, EXERCISE_UPDATED_TEXT);
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
        expectedTransientTarget = createExercisePostTarget(post, course);
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
        expectedTransientTarget = createLecturePostTarget(post, course);
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
        expectedTransientTarget = createCoursePostTarget(post, course);
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
        expectedTransientTarget = createCoursePostTarget(post, course);
        createAndCheckNotification(Base.POST);
    }

    // Based on ResponsePost

    /**
     * Tests the functionality that deals with notifications that have the notification type of NEW_REPLY_FOR_EXERCISE_POST.
     * I.e. notifications that originate from a new reply for an exercise post.
     */
    @Test
    public void createNotificationBasedOnAnswerPost_withNotificationType_NewReplyForExercisePost() {
        notificationType = NEW_REPLY_FOR_EXERCISE_POST;
        expectedTitle = NEW_REPLY_FOR_EXERCISE_POST_TITLE;
        expectedText = "Exercise \"" + exercise.getTitle() + "\" got a new reply.";
        expectedPriority = MEDIUM;
        expectedTransientTarget = createExercisePostTarget(post, course);
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
        expectedTransientTarget = createLecturePostTarget(post, course);
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
        expectedTransientTarget = createCoursePostTarget(post, course);
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
        expectedTransientTarget = createCourseTarget(course, COURSE_ARCHIVE_UPDATED_TEXT);
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
        expectedTransientTarget = createCourseTarget(course, COURSE_ARCHIVE_UPDATED_TEXT);
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
        expectedTransientTarget = createCourseTarget(course, COURSE_ARCHIVE_UPDATED_TEXT);
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
        expectedTransientTarget = createCourseTarget(course, EXAM_ARCHIVE_UPDATED_TEXT);
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
        expectedTransientTarget = createCourseTarget(course, EXAM_ARCHIVE_UPDATED_TEXT);
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
        expectedTransientTarget = createCourseTarget(course, EXAM_ARCHIVE_UPDATED_TEXT);
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
        expectedTransientTarget = createDuplicateTestCaseTarget(exercise);
        createAndCheckNotification(Base.EXERCISE);
    }

}
