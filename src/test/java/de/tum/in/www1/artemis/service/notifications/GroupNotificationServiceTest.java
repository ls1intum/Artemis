package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.NotificationRepository;
import de.tum.in.www1.artemis.repository.NotificationSettingRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class GroupNotificationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ExamRepository examRepository;

    private Notification capturedNotification;

    private Exercise exercise;

    private Exercise updatedExercise;

    private Exercise examExercise;

    private QuizExercise quizExercise;

    private ProgrammingExercise programmingExercise;

    private Lecture lecture;

    private Post post;

    private AnswerPost answerPost;

    private Course course;

    private Exam exam;

    private User student;

    private User instructor;

    // Problem statement of an exam exercise where the length is larger than the allowed max notification target size in the db
    // allowed <= 255, this one has ~ 500
    private static final String EXAM_PROBLEM_STATEMENT = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore "
            + "et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. "
            + "Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, "
            + "consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, "
            + "sed diam voluptua. At vero eos et accusam et justo duo dolores et e";

    private Attachment attachment;

    private List<String> archiveErrors;

    private final static Long EXAM_ID = 42L;

    private final static String NOTIFICATION_TEXT = "notificationText";

    private final static ZonedDateTime FUTURISTIC_TIME = ZonedDateTime.now().plusHours(2);

    private final static ZonedDateTime FUTURE_TIME = ZonedDateTime.now().plusHours(1);

    private final static ZonedDateTime CURRENT_TIME = ZonedDateTime.now();

    private final static ZonedDateTime PAST_TIME = ZonedDateTime.now().minusHours(1);

    private final static ZonedDateTime ANCIENT_TIME = ZonedDateTime.now().minusHours(2);

    private final static int NUMBER_OF_ALL_GROUPS = 4;

    /**
     * Sets up all needed mocks and their wanted behavior.
     */
    @BeforeEach
    public void setUp() {
        course = database.createCourse();

        database.addUsers(1, 0, 0, 1);

        student = database.getUserByLogin("student1");

        instructor = database.getUserByLogin("instructor1");

        archiveErrors = new ArrayList<>();

        exam = database.addExam(course);
        examRepository.save(exam);

        lecture = new Lecture();
        lecture.setCourse(course);

        attachment = new Attachment();

        exercise = ModelFactory.generateTextExercise(null, null, null, course);
        exerciseRepository.save(exercise);
        updatedExercise = ModelFactory.generateTextExercise(null, null, null, course);
        exerciseRepository.save(updatedExercise);

        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setExam(exam);

        examExercise = new TextExercise();
        examExercise.setExerciseGroup(exerciseGroup);
        examExercise.setProblemStatement(EXAM_PROBLEM_STATEMENT);

        quizExercise = database.createQuiz(course, null, null);
        exerciseRepository.save(quizExercise);

        programmingExercise = new ProgrammingExercise();
        programmingExercise.setCourse(course);

        post = new Post();
        post.setExercise(exercise);
        post.setLecture(lecture);
        post.setCourse(course);

        answerPost = new AnswerPost();
        answerPost.setPost(post);

        // explicitly change the user to prevent issues in the following server call due to userRepository.getUser() (@WithMockUser is not working here)
        database.changeUser("instructor1");
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    /**
     * Auxiliary method that checks if the groupNotificationRepository was called successfully with the correct notification (type)
     * @param numberOfGroupsAndCalls indicates the expected number of notifications created/saved.
     * This number depends on the number of different groups. For each different group one separate call is needed.
     * @param expectedNotificationTitle is the title (NotificationTitleTypeConstants) of the expected notification
     */
    private void verifyRepositoryCallWithCorrectNotification(int numberOfGroupsAndCalls, String expectedNotificationTitle) {
        List<Notification> capturedNotifications = notificationRepository.findAll();
        capturedNotification = capturedNotifications.get(0);
        assertThat(capturedNotification.getTitle()).as("The title of the captured notification should be equal to the expected one").isEqualTo(expectedNotificationTitle);
        assertThat(capturedNotifications.size()).as("The number of created notification should be the same as the number of notified groups/authorities")
                .isEqualTo(numberOfGroupsAndCalls);
    }

    /// Exercise Update / Release & Scheduling related Tests

    // NotifyAboutExerciseUpdate

    /**
    * Test for notifyAboutExerciseUpdate method with an undefined release date
    */
    @Test
    public void testNotifyAboutExerciseUpdate_undefinedReleaseDate() {
        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService, times(1)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
    }

    /**
    * Test for notifyAboutExerciseUpdate method with a future release date
    */
    @Test
    public void testNotifyAboutExerciseUpdate_futureReleaseDate() {
        exercise.setReleaseDate(FUTURE_TIME);
        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService, times(0)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
    }

    /**
    * Test for notifyAboutExerciseUpdate method with a correct release date (now) for exam exercises
    */
    @Test
    public void testNotifyAboutExerciseUpdate_correctReleaseDate_examExercise() {
        examExercise.setReleaseDate(CURRENT_TIME);
        groupNotificationService.notifyAboutExerciseUpdate(examExercise, null);
        verify(groupNotificationService, times(1)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());
    }

    /**
    * Test for notifyAboutExerciseUpdate method with a correct release date (now) for course exercises
    */
    @Test
    public void testNotifyAboutExerciseUpdate_correctReleaseDate_courseExercise() {
        exercise.setReleaseDate(CURRENT_TIME);
        groupNotificationService.notifyAboutExerciseUpdate(exercise, null);
        verify(groupNotificationService, times(0)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());
        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService, times(1)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());
    }

    /// CheckNotificationForExerciseRelease

    /**
    * Test for checkNotificationForExerciseRelease method with an undefined release date
    */
    @Test
    public void testCheckNotificationForExerciseRelease_undefinedReleaseDate() {
        groupNotificationService.checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutReleasedExercise(any());
    }

    /**
    * Test for checkNotificationForExerciseRelease method with a current or past release date
    */
    @Test
    public void testCheckNotificationForExerciseRelease_currentOrPastReleaseDate() {
        exercise.setReleaseDate(CURRENT_TIME);
        groupNotificationService.checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutReleasedExercise(any());
    }

    /**
    * Test for checkNotificationForExerciseRelease method with a future release date
    */
    @Test
    public void testCheckNotificationForExerciseRelease_futureReleaseDate() {
        exercise.setReleaseDate(FUTURE_TIME);
        groupNotificationService.checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        verify(instanceMessageSendService, times(1)).sendExerciseReleaseNotificationSchedule(any());
    }

    /// CheckAndCreateAppropriateNotificationsWhenUpdatingExercise

    /**
     * Auxiliary method to set the needed mocks and testing utilities for checkAndCreateAppropriateNotificationsWhenUpdatingExercise method
     */
    private void testCheckNotificationForExerciseReleaseHelper(ZonedDateTime dueDateOfInitialExercise, ZonedDateTime dueDateOfUpdatedExercise,
            boolean expectNotifyAboutExerciseRelease) {
        exercise.setReleaseDate(dueDateOfInitialExercise);
        updatedExercise.setReleaseDate(dueDateOfUpdatedExercise);

        groupNotificationService.checkAndCreateAppropriateNotificationsWhenUpdatingExercise(exercise, updatedExercise, NOTIFICATION_TEXT, instanceMessageSendService);

        verify(groupNotificationService, times(1)).notifyAboutExerciseUpdate(any(), any());
        verify(groupNotificationService, times(expectNotifyAboutExerciseRelease ? 1 : 0)).checkNotificationForExerciseRelease(any(), any());

        // needed to reset the verify() call counter
        reset(groupNotificationService);
    }

    /**
    * Test for checkAndCreateAppropriateNotificationsWhenUpdatingExercise method based on a decision matrix
    */
    @Test
    public void testCheckAndCreateAppropriateNotificationsWhenUpdatingExercise() {
        testCheckNotificationForExerciseReleaseHelper(null, null, false);
        testCheckNotificationForExerciseReleaseHelper(null, PAST_TIME, false);
        testCheckNotificationForExerciseReleaseHelper(null, CURRENT_TIME, false);
        testCheckNotificationForExerciseReleaseHelper(null, FUTURE_TIME, true);

        testCheckNotificationForExerciseReleaseHelper(PAST_TIME, null, false);
        testCheckNotificationForExerciseReleaseHelper(PAST_TIME, ANCIENT_TIME, false);
        testCheckNotificationForExerciseReleaseHelper(PAST_TIME, PAST_TIME, false); // same time -> no change
        testCheckNotificationForExerciseReleaseHelper(PAST_TIME, CURRENT_TIME, false);
        testCheckNotificationForExerciseReleaseHelper(PAST_TIME, FUTURE_TIME, true);

        testCheckNotificationForExerciseReleaseHelper(CURRENT_TIME, null, false);
        testCheckNotificationForExerciseReleaseHelper(CURRENT_TIME, PAST_TIME, false);
        testCheckNotificationForExerciseReleaseHelper(CURRENT_TIME, CURRENT_TIME, false); // same time -> no change
        testCheckNotificationForExerciseReleaseHelper(CURRENT_TIME, FUTURE_TIME, true);

        testCheckNotificationForExerciseReleaseHelper(FUTURE_TIME, null, true);
        testCheckNotificationForExerciseReleaseHelper(FUTURE_TIME, PAST_TIME, true);
        testCheckNotificationForExerciseReleaseHelper(FUTURE_TIME, CURRENT_TIME, true);
        testCheckNotificationForExerciseReleaseHelper(FUTURE_TIME, FUTURE_TIME, false); // same time -> no change
        testCheckNotificationForExerciseReleaseHelper(FUTURE_TIME, FUTURISTIC_TIME, true);
    }

    /// General notifyGroupX Tests

    /**
     * Auxiliary method that creates and saves the needed notification setting for email testing
     *
     * @param user who should be notified / receive an email
     * @param notificationSettingIdentifier of the corresponding notification type
     */
    private void prepareNotificationSettingForTest(User user, String notificationSettingIdentifier) {
        NotificationSetting notificationSetting = new NotificationSetting(user, true, true, notificationSettingIdentifier);
        notificationSettingRepository.save(notificationSetting);
    }

    /**
     * Checks if an email was created and send
     */
    private void verifyEmail() {
        verify(javaMailSender, timeout(1000).times(1)).createMimeMessage();
    }

    /**
     * Test for notifyStudentGroupAboutAttachmentChange method with a future release date
     */
    @Test
    public void testNotifyStudentGroupAboutAttachmentChange_futureReleaseDate() {
        attachment.setReleaseDate(FUTURE_TIME);
        groupNotificationService.notifyStudentGroupAboutAttachmentChange(attachment, NOTIFICATION_TEXT);
        assertThat(notificationRepository.findAll().size()).as("No notification should be created/saved").isEqualTo(0);
    }

    /**
     * Test for notifyStudentGroupAboutAttachmentChange method with a non future release date
     */
    @Test
    public void testNotifyStudentGroupAboutAttachmentChange_nonFutureReleaseDate() {
        lecture = new Lecture();
        lecture.setCourse(course);

        attachment.setReleaseDate(CURRENT_TIME);
        attachment.setLecture(lecture);

        prepareNotificationSettingForTest(student, NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES);

        groupNotificationService.notifyStudentGroupAboutAttachmentChange(attachment, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotification(1, ATTACHMENT_CHANGE_TITLE);

        verifyEmail();
    }

    /**
     * Test for notifyStudentGroupAboutExercisePractice method
     */
    @Test
    public void testNotifyStudentGroupAboutExercisePractice() {
        prepareNotificationSettingForTest(student, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE);
        groupNotificationService.notifyStudentGroupAboutExercisePractice(exercise);
        verifyRepositoryCallWithCorrectNotification(1, EXERCISE_PRACTICE_TITLE);
        verifyEmail();
    }

    /**
     * Test for notifyStudentGroupAboutQuizExerciseStart method
     */
    @Test
    public void testNotifyStudentGroupAboutQuizExerciseStart() {
        groupNotificationService.notifyStudentGroupAboutQuizExerciseStart(quizExercise);
        verifyRepositoryCallWithCorrectNotification(1, QUIZ_EXERCISE_STARTED_TITLE);
    }

    /**
     * Test for notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate method
     */
    @Test
    public void testNotifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate() {
        groupNotificationService.notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotification(3, EXERCISE_UPDATED_TITLE);
    }

    /**
     * Test for notifyAllGroupsAboutReleasedExercise method
     */
    @Test
    public void testNotifyAllGroupsAboutReleasedExercise() {
        prepareNotificationSettingForTest(student, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED);
        groupNotificationService.notifyAllGroupsAboutReleasedExercise(exercise);
        verifyRepositoryCallWithCorrectNotification(NUMBER_OF_ALL_GROUPS, EXERCISE_RELEASED_TITLE);
        verifyEmail();
    }

    /**
     * Test for notifyEditorAndInstructorGroupAboutExerciseUpdate method
     */
    @Test
    public void testNotifyEditorAndInstructorGroupAboutExerciseUpdate() {
        groupNotificationService.notifyEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotification(2, EXERCISE_UPDATED_TITLE);
    }

    /**
     * Test for notifyEditorAndInstructorGroupAboutExerciseUpdate method
     */
    @Test
    public void testNotifyAllGroupsAboutNewPostForExercise() {
        groupNotificationService.notifyAllGroupsAboutNewPostForExercise(post, course);
        verifyRepositoryCallWithCorrectNotification(NUMBER_OF_ALL_GROUPS, NEW_EXERCISE_POST_TITLE);
    }

    /**
     * Test for notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise method
     */
    @Test
    public void testNotifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise() {
        groupNotificationService.notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise(programmingExercise, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotification(2, DUPLICATE_TEST_CASE_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutIllegalSubmissionsForExercise method
     */
    @Test
    public void testNotifyInstructorGroupAboutIllegalSubmissionsForExercise() {
        groupNotificationService.notifyInstructorGroupAboutIllegalSubmissionsForExercise(exercise, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotification(1, ILLEGAL_SUBMISSION_TITLE);
    }

    /**
     * Test for notifyAllGroupsAboutNewPostForLecture method
     */
    @Test
    public void testNotifyAllGroupsAboutNewPostForLecture() {
        groupNotificationService.notifyAllGroupsAboutNewPostForLecture(post, course);
        verifyRepositoryCallWithCorrectNotification(NUMBER_OF_ALL_GROUPS, NEW_LECTURE_POST_TITLE);
    }

    /**
     * Test for notifyAllGroupsAboutNewCoursePost method
     */
    @Test
    public void testNotifyAllGroupsAboutNewCoursePost() {
        groupNotificationService.notifyAllGroupsAboutNewCoursePost(post, course);
        verifyRepositoryCallWithCorrectNotification(NUMBER_OF_ALL_GROUPS, NEW_COURSE_POST_TITLE);
    }

    /**
     * Test for notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForCoursePost method
     */
    @Test
    public void testNotifyTutorAndEditorAndInstructorGroupAboutNewAnswerForCoursePost() {
        groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewReplyForCoursePost(post, answerPost, course);
        verifyRepositoryCallWithCorrectNotification(3, NEW_REPLY_FOR_COURSE_POST_TITLE);
    }

    /**
     * Test for notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForExercise method
     */
    @Test
    public void testNotifyTutorAndEditorAndInstructorGroupAboutNewAnswerForExercise() {
        groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewReplyForExercise(post, answerPost, course);
        verifyRepositoryCallWithCorrectNotification(3, NEW_REPLY_FOR_EXERCISE_POST_TITLE);
    }

    /**
     * Test for notifyAllGroupsAboutNewAnnouncement method
     */
    @Test
    public void testNotifyAllGroupsAboutNewAnnouncement() {
        prepareNotificationSettingForTest(student, NOTIFICATION__COURSE_WIDE_DISCUSSION__NEW_ANNOUNCEMENT_POST);
        groupNotificationService.notifyAllGroupsAboutNewAnnouncement(post, course);
        verifyRepositoryCallWithCorrectNotification(NUMBER_OF_ALL_GROUPS, NEW_ANNOUNCEMENT_POST_TITLE);
        verifyEmail();
    }

    /**
     * Test for notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForLecture method
     */
    @Test
    public void testNotifyTutorAndEditorAndInstructorGroupAboutNewAnswerForLecture() {
        groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForLecture(post, answerPost, course);
        verifyRepositoryCallWithCorrectNotification(3, NEW_REPLY_FOR_LECTURE_POST_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutChangedTestCasesForProgrammingExercise method
     */
    @Test
    public void testNotifyInstructorGroupAboutChangedTestCasesForProgrammingExercise() {
        prepareNotificationSettingForTest(instructor, NOTIFICATION__EDITOR_NOTIFICATION__PROGRAMMING_TEST_CASES_CHANGED);
        groupNotificationService.notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise(programmingExercise);
        verifyRepositoryCallWithCorrectNotification(2, PROGRAMMING_TEST_CASES_CHANGED_TITLE);
        verifyEmail();
    }

    // Course Archiving

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type CourseArchiveStarted
     */
    @Test
    public void testNotifyInstructorGroupAboutCourseArchiveState_CourseArchiveStarted() {
        groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, COURSE_ARCHIVE_STARTED, archiveErrors);
        verifyRepositoryCallWithCorrectNotification(1, COURSE_ARCHIVE_STARTED_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type CourseArchiveFailed
     */
    @Test
    public void testNotifyInstructorGroupAboutCourseArchiveState_CourseArchiveFailed() {
        groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, COURSE_ARCHIVE_FAILED, archiveErrors);
        verifyRepositoryCallWithCorrectNotification(1, COURSE_ARCHIVE_FAILED_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type CourseArchiveFinished
     */
    @Test
    public void testNotifyInstructorGroupAboutCourseArchiveState_CourseArchiveFinished() {
        groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, COURSE_ARCHIVE_FINISHED, archiveErrors);
        verifyRepositoryCallWithCorrectNotification(1, COURSE_ARCHIVE_FINISHED_TITLE);
    }

    // Exam Archiving

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type ExamArchiveStarted
     */
    @Test
    public void testNotifyInstructorGroupAboutCourseArchiveState_ExamArchiveStarted() {
        groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, EXAM_ARCHIVE_STARTED, archiveErrors);
        verifyRepositoryCallWithCorrectNotification(1, EXAM_ARCHIVE_STARTED_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type ExamArchiveFailed
     */
    @Test
    public void testNotifyInstructorGroupAboutCourseArchiveState_ExamArchiveFailed() {
        groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, EXAM_ARCHIVE_FAILED, archiveErrors);
        verifyRepositoryCallWithCorrectNotification(1, EXAM_ARCHIVE_FAILED_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type ExamArchiveFinished
     */
    @Test
    public void testNotifyInstructorGroupAboutCourseArchiveState_ExamArchiveFinished() {
        groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, EXAM_ARCHIVE_FINISHED, archiveErrors);
        verifyRepositoryCallWithCorrectNotification(1, EXAM_ARCHIVE_FINISHED_TITLE);
    }
}
