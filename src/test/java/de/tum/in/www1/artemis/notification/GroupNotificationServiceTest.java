package de.tum.in.www1.artemis.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.*;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.quizexercise.QuizExerciseFactory;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseFactory;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationScheduleService;
import de.tum.in.www1.artemis.user.UserUtilService;

class GroupNotificationServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "groupnotificationservice";

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private GroupNotificationScheduleService groupNotificationScheduleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private Exercise exercise;

    private Exercise updatedExercise;

    private Exercise examExercise;

    private QuizExercise quizExercise;

    private ProgrammingExercise programmingExercise;

    private Lecture lecture;

    private static final String LECTURE_TITLE = "lecture title";

    private Post post;

    private static final String POST_TITLE = "post title";

    private static final String POST_CONTENT = "post content";

    private AnswerPost answerPost;

    private static final String ANSWER_POST_CONTENT = "answer post content";

    private Course course;

    private static final String COURSE_TITLE = "course title";

    private Exam exam;

    private User student;

    private User instructor;

    private int notificationCountBeforeTest;

    // Problem statement of an exam exercise where the length is larger than the allowed max notification target size in the db
    // allowed <= 255, this one has ~ 500
    private static final String EXAM_PROBLEM_STATEMENT = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore "
            + "et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. "
            + "Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, "
            + "consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, "
            + "sed diam voluptua. At vero eos et accusam et justo duo dolores et e";

    private Attachment attachment;

    private List<String> archiveErrors;

    private static final String NOTIFICATION_TEXT = "notificationText";

    private static final ZonedDateTime FUTURISTIC_TIME = ZonedDateTime.now().plusHours(2);

    private static final ZonedDateTime FUTURE_TIME = ZonedDateTime.now().plusHours(1);

    private static final ZonedDateTime CURRENT_TIME = ZonedDateTime.now();

    private static final ZonedDateTime PAST_TIME = ZonedDateTime.now().minusHours(1);

    private static final ZonedDateTime ANCIENT_TIME = ZonedDateTime.now().minusHours(2);

    private static final int NUMBER_OF_ALL_GROUPS = 4;

    /**
     * Sets up all needed mocks and their wanted behavior.
     */
    @BeforeEach
    void setUp() {
        course = courseUtilService.createCourse();
        course.setInstructorGroupName(TEST_PREFIX + "instructors");
        course.setTeachingAssistantGroupName(TEST_PREFIX + "tutors");
        course.setEditorGroupName(TEST_PREFIX + "editors");
        course.setStudentGroupName(TEST_PREFIX + "students");
        course.setTitle(COURSE_TITLE);

        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);

        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student.setGroups(Set.of(TEST_PREFIX + "students"));
        userRepository.save(student);

        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        instructor.setGroups(Set.of(TEST_PREFIX + "instructors"));
        userRepository.save(instructor);

        archiveErrors = new ArrayList<>();

        exam = examUtilService.addExam(course);
        examRepository.save(exam);

        lecture = new Lecture();
        lecture.setCourse(course);
        lecture.setTitle(LECTURE_TITLE);

        exercise = TextExerciseFactory.generateTextExercise(null, null, null, course);
        exerciseRepository.save(exercise);
        updatedExercise = TextExerciseFactory.generateTextExercise(null, null, null, course);
        exerciseRepository.save(updatedExercise);

        attachment = new Attachment();
        attachment.setExercise(exercise);

        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setExam(exam);

        examExercise = new TextExercise();
        examExercise.setExerciseGroup(exerciseGroup);
        examExercise.setProblemStatement(EXAM_PROBLEM_STATEMENT);

        quizExercise = QuizExerciseFactory.createQuiz(course, null, null, QuizMode.SYNCHRONIZED);
        exerciseRepository.save(quizExercise);

        programmingExercise = new ProgrammingExercise();
        programmingExercise.setCourse(course);
        Channel channel = new Channel();
        channel.setId(123L);
        channel.setName("test");

        post = new Post();
        post.setConversation(channel);
        post.setAuthor(instructor);
        post.setTitle(POST_TITLE);
        post.setContent(POST_CONTENT);

        answerPost = new AnswerPost();
        answerPost.setPost(post);
        answerPost.setAuthor(instructor);
        answerPost.setContent(ANSWER_POST_CONTENT);

        // explicitly change the user to prevent issues in the following server call due to userRepository.getUser() (@WithMockUser is not working here)
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        // store the current notification count to let tests work even if notifications are created in other tests
        notificationCountBeforeTest = notificationRepository.findAll().size();
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAllInBatch();
    }

    /**
     * Auxiliary method that checks if the groupNotificationRepository was called successfully with the correct notification (type)
     *
     * @param numberOfGroupsAndCalls    indicates the expected number of notifications created/saved.
     *                                      This number depends on the number of different groups. For each different group one separate call is needed.
     * @param expectedNotificationTitle is the title (NotificationTitleTypeConstants) of the expected notification
     * @return last captured notification
     */
    private Notification verifyRepositoryCallWithCorrectNotificationAndReturnNotification(int numberOfGroupsAndCalls, String expectedNotificationTitle) {
        return verifyRepositoryCallWithCorrectNotificationAndReturnNotificationAtIndex(numberOfGroupsAndCalls, expectedNotificationTitle, 0);
    }

    /**
     * Auxiliary method that checks if the groupNotificationRepository was called successfully with the correct notification (type)
     *
     * @param numberOfGroupsAndCalls    indicates the expected number of notifications created/saved.
     *                                      This number depends on the number of different groups. For each different group one separate call is needed.
     * @param expectedNotificationTitle is the title (NotificationTitleTypeConstants) of the expected notification
     * @param index                     the notification at this index (counting from the end of the notifications list) gets returned
     * @return the captured notification at the given index
     */
    private Notification verifyRepositoryCallWithCorrectNotificationAndReturnNotificationAtIndex(int numberOfGroupsAndCalls, String expectedNotificationTitle, int index) {
        await().untilAsserted(
                () -> assertThat(notificationRepository.findAll()).as("The number of created notifications should be the same as the number of notified groups/authorities")
                        .hasSize(numberOfGroupsAndCalls + notificationCountBeforeTest));

        List<Notification> capturedNotifications = notificationRepository.findAll();
        Notification lastCapturedNotification = capturedNotifications.get(capturedNotifications.size() - 1);
        assertThat(lastCapturedNotification.getTitle()).as("The title of the captured notification should be equal to the expected one").isEqualTo(expectedNotificationTitle);

        return index <= 0 ? lastCapturedNotification : capturedNotifications.get(capturedNotifications.size() - 1 - index);
    }

    /// Exercise Update / Release & Scheduling related Tests

    // NotifyAboutExerciseUpdate

    /**
     * Test for notifyAboutExerciseUpdate method with an undefined release date
     */
    @Test
    void testNotifyAboutExerciseUpdate_undefinedReleaseDate() {
        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
    }

    /**
     * Test for notifyAboutExerciseUpdate method with a future release date
     */
    @Test
    void testNotifyAboutExerciseUpdate_futureReleaseDate() {
        exercise.setReleaseDate(FUTURE_TIME);
        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService, never()).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
    }

    /**
     * Test for notifyAboutExerciseUpdate method with a correct release date (now) for exam exercises
     */
    @Test
    void testNotifyAboutExerciseUpdate_correctReleaseDate_examExercise() {
        examExercise.setReleaseDate(CURRENT_TIME);
        groupNotificationService.notifyAboutExerciseUpdate(examExercise, null);
        verify(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());
    }

    /**
     * Test for notifyAboutExerciseUpdate method with a correct release date (now) for course exercises
     */
    @Test
    void testNotifyAboutExerciseUpdate_correctReleaseDate_courseExercise() {
        exercise.setReleaseDate(CURRENT_TIME);
        groupNotificationService.notifyAboutExerciseUpdate(exercise, null);
        verify(groupNotificationService, never()).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());
        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());
    }

    /// CheckNotificationForExerciseRelease

    /**
     * Test for checkNotificationForExerciseRelease method with an undefined release date
     */
    @Test
    void testCheckNotificationForExerciseRelease_undefinedReleaseDate() {
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(exercise);
        verify(groupNotificationService, timeout(1500)).notifyAllGroupsAboutReleasedExercise(any());
    }

    /**
     * Test for checkNotificationForExerciseRelease method with a current or past release date
     */
    @Test
    void testCheckNotificationForExerciseRelease_currentOrPastReleaseDate() {
        exercise.setReleaseDate(CURRENT_TIME);
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(exercise);
        verify(groupNotificationService, timeout(1500)).notifyAllGroupsAboutReleasedExercise(any());
    }

    /**
     * Test for checkNotificationForExerciseRelease method with a future release date
     */
    @Test
    void testCheckNotificationForExerciseRelease_futureReleaseDate() {
        exercise.setReleaseDate(FUTURE_TIME);
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(exercise);
        verify(instanceMessageSendService, timeout(1500)).sendExerciseReleaseNotificationSchedule(any());
    }

    /// CheckAndCreateAppropriateNotificationsWhenUpdatingExercise

    /**
     * Auxiliary method to set the needed mocks and testing utilities for checkAndCreateAppropriateNotificationsWhenUpdatingExercise method
     */
    private void testCheckNotificationForExerciseReleaseHelper(ZonedDateTime dateOfInitialExercise, ZonedDateTime dateOfUpdatedExercise,
            boolean expectNotifyAboutExerciseReleaseNow, boolean expectSchedulingAtRelease, boolean expectNotifyUsersAboutAssessedExerciseSubmissionNow,
            boolean expectSchedulingAtAssessmentDueDate) {
        exercise.setReleaseDate(dateOfInitialExercise);
        exercise.setAssessmentDueDate(dateOfInitialExercise);
        updatedExercise.setReleaseDate(dateOfUpdatedExercise);
        updatedExercise.setAssessmentDueDate(dateOfUpdatedExercise);

        groupNotificationScheduleService.checkAndCreateAppropriateNotificationsWhenUpdatingExercise(exercise, updatedExercise, NOTIFICATION_TEXT);

        verify(groupNotificationService).notifyAboutExerciseUpdate(any(), any());

        // Exercise Released Notifications
        verify(groupNotificationService, times(expectNotifyAboutExerciseReleaseNow ? 1 : 0)).notifyAllGroupsAboutReleasedExercise(any());
        verify(instanceMessageSendService, times(expectSchedulingAtRelease ? 1 : 0)).sendExerciseReleaseNotificationSchedule(any());

        // Assessed Exercise Submitted Notifications
        verify(singleUserNotificationService, times(expectNotifyUsersAboutAssessedExerciseSubmissionNow ? 1 : 0)).notifyUsersAboutAssessedExerciseSubmission(any());
        verify(instanceMessageSendService, times(expectSchedulingAtAssessmentDueDate ? 1 : 0)).sendAssessedExerciseSubmissionNotificationSchedule(any());

        // needed to reset the verify() call counter
        reset(groupNotificationService);
        reset(singleUserNotificationService);
        reset(instanceMessageSendService);
    }

    /**
     * Test for checkAndCreateAppropriateNotificationsWhenUpdatingExercise method based on a decision matrix
     */
    @Test
    void testCheckAndCreateAppropriateNotificationsWhenUpdatingExercise() {
        testCheckNotificationForExerciseReleaseHelper(null, null, false, false, false, false);
        testCheckNotificationForExerciseReleaseHelper(null, PAST_TIME, false, false, false, false);
        testCheckNotificationForExerciseReleaseHelper(null, CURRENT_TIME, false, false, false, false);
        testCheckNotificationForExerciseReleaseHelper(null, FUTURE_TIME, false, true, false, true);

        testCheckNotificationForExerciseReleaseHelper(PAST_TIME, null, false, false, false, false);
        testCheckNotificationForExerciseReleaseHelper(PAST_TIME, ANCIENT_TIME, false, false, false, false);
        testCheckNotificationForExerciseReleaseHelper(PAST_TIME, PAST_TIME, false, false, false, false); // same time -> no change
        testCheckNotificationForExerciseReleaseHelper(PAST_TIME, CURRENT_TIME, false, false, false, false);
        testCheckNotificationForExerciseReleaseHelper(PAST_TIME, FUTURE_TIME, false, true, false, true);

        testCheckNotificationForExerciseReleaseHelper(CURRENT_TIME, null, false, false, false, false);
        testCheckNotificationForExerciseReleaseHelper(CURRENT_TIME, PAST_TIME, false, false, false, false);
        testCheckNotificationForExerciseReleaseHelper(CURRENT_TIME, CURRENT_TIME, false, false, false, false); // same time -> no change
        testCheckNotificationForExerciseReleaseHelper(CURRENT_TIME, FUTURE_TIME, false, true, false, true);

        testCheckNotificationForExerciseReleaseHelper(FUTURE_TIME, null, true, false, true, false);
        testCheckNotificationForExerciseReleaseHelper(FUTURE_TIME, PAST_TIME, true, false, true, false);
        testCheckNotificationForExerciseReleaseHelper(FUTURE_TIME, CURRENT_TIME, true, false, true, false);
        testCheckNotificationForExerciseReleaseHelper(FUTURE_TIME, FUTURE_TIME, false, false, false, false); // same time -> no change
        testCheckNotificationForExerciseReleaseHelper(FUTURE_TIME, FUTURISTIC_TIME, false, true, false, true);
    }

    /// General notifyGroupX Tests

    /**
     * Auxiliary method that creates and saves the needed notification setting for email testing
     *
     * @param user                          who should be notified / receive an email
     * @param notificationSettingIdentifier of the corresponding notification type
     */
    private void prepareNotificationSettingForTest(User user, String notificationSettingIdentifier) {
        NotificationSetting notificationSetting = new NotificationSetting(user, true, true, true, notificationSettingIdentifier);
        notificationSettingRepository.save(notificationSetting);
    }

    /**
     * Checks if an email was created and send
     */
    private void verifyEmail() {
        verify(javaMailSender, timeout(1500)).createMimeMessage();
    }

    /**
     * Checks if a push to android and iOS was created and send
     */
    private void verifyPush(Notification notification, Set<User> users, Object notificationSubject) {
        verify(applePushNotificationService, timeout(1500)).sendNotification(notification, users, notificationSubject);
        verify(firebasePushNotificationService, timeout(1500)).sendNotification(notification, users, notificationSubject);
    }

    /**
     * Test for notifyStudentGroupAboutAttachmentChange method with a future release date
     */
    @Test
    void testNotifyStudentGroupAboutAttachmentChange_futureReleaseDate() {
        var countBefore = notificationRepository.count();
        attachment.setReleaseDate(FUTURE_TIME);
        groupNotificationService.notifyStudentGroupAboutAttachmentChange(attachment, NOTIFICATION_TEXT);
        var countAfter = notificationRepository.count();
        assertThat(countAfter).as("No notification should be created/saved").isEqualTo(countBefore);
    }

    /**
     * Test for notifyStudentGroupAboutAttachmentChange method with a non future release date
     */
    @Test
    void testNotifyStudentGroupAboutAttachmentChange_nonFutureReleaseDate() {
        lecture = new Lecture();
        lecture.setCourse(course);

        attachment.setReleaseDate(CURRENT_TIME);
        attachment.setLecture(lecture);

        prepareNotificationSettingForTest(student, NOTIFICATION__LECTURE_NOTIFICATION__ATTACHMENT_CHANGES);

        groupNotificationService.notifyStudentGroupAboutAttachmentChange(attachment, NOTIFICATION_TEXT);
        Notification notification = verifyRepositoryCallWithCorrectNotificationAndReturnNotification(1, ATTACHMENT_CHANGE_TITLE);

        verifyEmail();
        verifyPush(notification, Set.of(student), attachment);
    }

    @Test
    void testNotifyStudentGroupAboutExercisePractice() {
        prepareNotificationSettingForTest(student, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_OPEN_FOR_PRACTICE);
        groupNotificationService.notifyStudentGroupAboutExercisePractice(exercise);
        Notification notification = verifyRepositoryCallWithCorrectNotificationAndReturnNotification(1, EXERCISE_PRACTICE_TITLE);

        verifyEmail();
        verifyPush(notification, Set.of(student), exercise);
    }

    @Test
    void testNotifyStudentGroupAboutQuizExerciseStart() {
        groupNotificationService.notifyStudentGroupAboutQuizExerciseStart(quizExercise);
        verifyRepositoryCallWithCorrectNotificationAndReturnNotification(1, QUIZ_EXERCISE_STARTED_TITLE);
    }

    @Test
    void testNotifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate() {
        groupNotificationService.notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotificationAndReturnNotification(3, EXERCISE_UPDATED_TITLE);
    }

    @Test
    void testNotifyAllGroupsAboutReleasedExercise() {
        prepareNotificationSettingForTest(student, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_RELEASED);
        groupNotificationService.notifyAllGroupsAboutReleasedExercise(exercise);
        verifyRepositoryCallWithCorrectNotificationAndReturnNotification(NUMBER_OF_ALL_GROUPS, EXERCISE_RELEASED_TITLE);
        verify(javaMailSender, timeout(1500).atLeastOnce()).createMimeMessage();
    }

    @Test
    void testNotifyEditorAndInstructorGroupAboutExerciseUpdate() {
        groupNotificationService.notifyEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotificationAndReturnNotification(2, EXERCISE_UPDATED_TITLE);
    }

    @Test
    void testNotifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise() {
        groupNotificationService.notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise(programmingExercise, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotificationAndReturnNotification(2, DUPLICATE_TEST_CASE_TITLE);
    }

    @Test
    void testNotifyInstructorGroupAboutIllegalSubmissionsForExercise() {
        groupNotificationService.notifyInstructorGroupAboutIllegalSubmissionsForExercise(exercise, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotificationAndReturnNotification(1, ILLEGAL_SUBMISSION_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutChangedTestCasesForProgrammingExercise method
     */
    @Test
    void testNotifyInstructorGroupAboutChangedTestCasesForProgrammingExercise() {
        prepareNotificationSettingForTest(instructor, NOTIFICATION__EDITOR_NOTIFICATION__PROGRAMMING_TEST_CASES_CHANGED);
        groupNotificationService.notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise(programmingExercise);
        verifyRepositoryCallWithCorrectNotificationAndReturnNotification(2, PROGRAMMING_TEST_CASES_CHANGED_TITLE);
    }

    // Course Archiving

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type CourseArchiveStarted
     */
    @Test
    void testNotifyInstructorGroupAboutCourseArchiveState_CourseArchiveStarted() {
        groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, COURSE_ARCHIVE_STARTED, archiveErrors);
        verifyRepositoryCallWithCorrectNotificationAndReturnNotification(1, COURSE_ARCHIVE_STARTED_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type CourseArchiveFailed
     */
    @Test
    void testNotifyInstructorGroupAboutCourseArchiveState_CourseArchiveFailed() {
        groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, COURSE_ARCHIVE_FAILED, archiveErrors);
        verifyRepositoryCallWithCorrectNotificationAndReturnNotification(1, COURSE_ARCHIVE_FAILED_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type CourseArchiveFinished
     */
    @Test
    void testNotifyInstructorGroupAboutCourseArchiveState_CourseArchiveFinished() {
        groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, COURSE_ARCHIVE_FINISHED, archiveErrors);
        verifyRepositoryCallWithCorrectNotificationAndReturnNotification(1, COURSE_ARCHIVE_FINISHED_TITLE);
    }

    // Exam Archiving

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type ExamArchiveStarted
     */
    @Test
    void testNotifyInstructorGroupAboutCourseArchiveState_ExamArchiveStarted() {
        groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, EXAM_ARCHIVE_STARTED, archiveErrors);
        verifyRepositoryCallWithCorrectNotificationAndReturnNotification(1, EXAM_ARCHIVE_STARTED_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type ExamArchiveFailed
     */
    @Test
    void testNotifyInstructorGroupAboutCourseArchiveState_ExamArchiveFailed() {
        groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, EXAM_ARCHIVE_FAILED, archiveErrors);
        verifyRepositoryCallWithCorrectNotificationAndReturnNotification(1, EXAM_ARCHIVE_FAILED_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type ExamArchiveFinished
     */
    @Test
    void testNotifyInstructorGroupAboutCourseArchiveState_ExamArchiveFinished() {
        groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, EXAM_ARCHIVE_FINISHED, archiveErrors);
        verifyRepositoryCallWithCorrectNotificationAndReturnNotification(1, EXAM_ARCHIVE_FINISHED_TITLE);
    }
}
