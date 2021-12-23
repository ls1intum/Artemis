package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.MailService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;

public class GroupNotificationServiceTest {

    @Autowired
    private static GroupNotificationService groupNotificationService;

    @Captor
    private static ArgumentCaptor<Notification> notificationCaptor;

    private Notification capturedNotification;

    @Mock
    private static UserRepository userRepository;

    private static User user;

    private static List<User> users = new ArrayList<>();

    @Mock
    private static GroupNotificationRepository groupNotificationRepository;

    @Mock
    private static SimpMessageSendingOperations messagingTemplate;

    @Mock
    private static InstanceMessageSendService instanceMessageSendService;

    @Mock
    private static MailService mailService;

    @Mock
    private static NotificationSettingsService notificationSettingsService;

    private static Exercise exercise;

    private static Exercise updatedExercise;

    private static Exercise examExercise;

    private static ExerciseGroup exerciseGroup;

    private static QuizExercise quizExercise;

    private final static Long EXERCISE_ID = 13L;

    private static ProgrammingExercise programmingExercise;

    private static Lecture lecture;

    private static Post post;

    static AnswerPost answerPost;

    private static Course course;

    private final static Long COURSE_ID = 27L;

    private static Exam exam;

    // Problem statement of an exam exercise where the length is larger than the allowed max notification target size in the db
    // allowed <= 255, this one has ~ 500
    private static final String EXAM_PROBLEM_STATEMENT = "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore "
            + "et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. "
            + "Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, "
            + "consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, "
            + "sed diam voluptua. At vero eos et accusam et justo duo dolores et e";

    private static Attachment attachment;

    private static List<String> archiveErrors;

    private final static Long EXAM_ID = 42L;

    private final static String NOTIFICATION_TEXT = "notificationText";

    private final static ZonedDateTime FUTURISTIC_TIME = ZonedDateTime.now().plusHours(2);

    private final static ZonedDateTime FUTURE_TIME = ZonedDateTime.now().plusHours(1);

    private final static ZonedDateTime CURRENT_TIME = ZonedDateTime.now();

    private final static ZonedDateTime PAST_TIME = ZonedDateTime.now().minusHours(1);

    private final static ZonedDateTime ANCIENT_TIME = ZonedDateTime.now().minusHours(2);

    private final static int NUMBER_OF_ALL_GROUPS = 4;

    /**
     * Sets up all needed mocks and their wanted behavior once for all test cases.
     * These are the common mocks and structures which behavior is fixed and will not change
     */
    @BeforeAll
    public static void setUp() {
        mailService = mock(MailService.class);
        doNothing().when(mailService).sendNotificationEmailForMultipleUsers(any(), any(), any());

        course = new Course();
        course.setId(COURSE_ID);

        user = new User();

        users.add(user);

        userRepository = mock(UserRepository.class);
        when(userRepository.getStudents(any())).thenReturn(users);
        when(userRepository.getInstructors(any())).thenReturn(users);
        when(userRepository.getEditors(any())).thenReturn(users);

        notificationCaptor = ArgumentCaptor.forClass(Notification.class);

        groupNotificationRepository = mock(GroupNotificationRepository.class);
        when(groupNotificationRepository.save(any())).thenReturn(null);

        messagingTemplate = mock(SimpMessageSendingOperations.class);

        notificationSettingsService = mock(NotificationSettingsService.class);

        groupNotificationService = spy(new GroupNotificationService(groupNotificationRepository, messagingTemplate, userRepository, mailService, notificationSettingsService));

        archiveErrors = new ArrayList<>();

        course = new Course();
        course.setId(COURSE_ID);

        exam = new Exam();
        exam.setId(EXAM_ID);
        exam.setCourse(course);

        lecture = new Lecture();
        lecture.setCourse(course);

        attachment = new Attachment();

        exercise = new TextExercise();
        updatedExercise = new TextExercise();

        exerciseGroup = new ExerciseGroup();
        exerciseGroup.setExam(exam);

        examExercise = new TextExercise();
        examExercise.setExerciseGroup(exerciseGroup);
        examExercise.setProblemStatement(EXAM_PROBLEM_STATEMENT);

        quizExercise = new QuizExercise();
        quizExercise.setCourse(course);

        programmingExercise = new ProgrammingExercise();
        programmingExercise.setCourse(course);

        post = new Post();
        post.setExercise(exercise);
        post.setLecture(lecture);

        answerPost = new AnswerPost();
        answerPost.setPost(post);

        instanceMessageSendService = mock(InstanceMessageSendService.class);
        doNothing().when(instanceMessageSendService).sendExerciseReleaseNotificationSchedule(EXERCISE_ID);
    }

    /**
     * Prepares and cleans the mocks and variables that are modified during the tests
     */
    @BeforeEach
    public void cleanMocksAndVariables() {
        reset(groupNotificationService);

        reset(notificationSettingsService);

        reset(groupNotificationRepository);
        when(groupNotificationRepository.save(any())).thenReturn(null);

        reset(messagingTemplate);

        exercise = new TextExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setCourse(course);
    }

    /**
     * Auxiliary method that checks if the groupNotificationRepository was called successfully with the correct notification (type)
     * @param numberOfGroupsAndCalls indicates the expected number of notifications created/saved.
     * This number depends on the number of different groups. For each different group one separate call is needed.
     * @param expectedNotificationTitle is the title (NotificationTitleTypeConstants) of the expected notification
     */
    private void verifyRepositoryCallWithCorrectNotification(int numberOfGroupsAndCalls, String expectedNotificationTitle) {
        verify(groupNotificationRepository, times(numberOfGroupsAndCalls)).save(notificationCaptor.capture());
        capturedNotification = notificationCaptor.getValue();
        assertThat(capturedNotification.getTitle()).as("The title of the captured notification should be equal to the expected one").isEqualTo(expectedNotificationTitle);
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
        doNothing().when(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(examExercise, NOTIFICATION_TEXT);

        groupNotificationService.notifyAboutExerciseUpdate(examExercise, null);

        verify(groupNotificationService, times(1)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());
    }

    /**
    * Test for notifyAboutExerciseUpdate method with a correct release date (now) for course exercises
    */
    @Test
    public void testNotifyAboutExerciseUpdate_correctReleaseDate_courseExercise() {
        exercise.setReleaseDate(CURRENT_TIME);
        doNothing().when(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);

        groupNotificationService.notifyAboutExerciseUpdate(exercise, null);
        verify(groupNotificationService, times(0)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());

        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService, times(1)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());
    }

    /// CheckNotificationForExerciseRelease

    /**
     * Auxiliary methods for testing the checkNotificationForExerciseRelease
     */
    private void prepareMocksForCheckNotificationForExerciseReleaseTesting() {
        doNothing().when(groupNotificationService).notifyAllGroupsAboutReleasedExercise(exercise);
    }

    /**
    * Test for checkNotificationForExerciseRelease method with an undefined release date
    */
    @Test
    public void testCheckNotificationForExerciseRelease_undefinedReleaseDate() {
        prepareMocksForCheckNotificationForExerciseReleaseTesting();
        groupNotificationService.checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutReleasedExercise(any());
    }

    /**
    * Test for checkNotificationForExerciseRelease method with a current or past release date
    */
    @Test
    public void testCheckNotificationForExerciseRelease_currentOrPastReleaseDate() {
        prepareMocksForCheckNotificationForExerciseReleaseTesting();
        exercise.setReleaseDate(CURRENT_TIME);
        groupNotificationService.checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutReleasedExercise(any());
    }

    /**
    * Test for checkNotificationForExerciseRelease method with a future release date
    */
    @Test
    public void testCheckNotificationForExerciseRelease_futureReleaseDate() {
        prepareMocksForCheckNotificationForExerciseReleaseTesting();
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
        doNothing().when(groupNotificationService).notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        doNothing().when(groupNotificationService).checkNotificationForExerciseRelease(exercise, instanceMessageSendService);

        groupNotificationService.checkAndCreateAppropriateNotificationsWhenUpdatingExercise(exercise, updatedExercise, NOTIFICATION_TEXT, instanceMessageSendService);

        verify(groupNotificationService, times(1)).notifyAboutExerciseUpdate(any(), any());
        verify(groupNotificationService, times(expectNotifyAboutExerciseRelease ? 1 : 0)).checkNotificationForExerciseRelease(any(), any());

        cleanMocksAndVariables();
    }

    /**
    * Test for checkAndCreateAppropriateNotificationsWhenUpdatingExercise method based on a decision matrix
    */
    @Test
    public void testCheckAndCreateAppropriateNotificationsWhenUpdatingExercise_unchangedReleaseDate_undefinedReleaseDates() {
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
     * Test for notifyStudentGroupAboutAttachmentChange method with a future release date
     */
    @Test
    public void testNotifyStudentGroupAboutAttachmentChange_futureReleaseDate() {
        attachment.setReleaseDate(FUTURE_TIME);
        groupNotificationService.notifyStudentGroupAboutAttachmentChange(attachment, NOTIFICATION_TEXT);
        verify(groupNotificationRepository, times(0)).save(any());
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

        groupNotificationService.notifyStudentGroupAboutAttachmentChange(attachment, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotification(1, ATTACHMENT_CHANGE_TITLE);
    }

    /**
     * Test for notifyStudentGroupAboutExercisePractice method
     */
    @Test
    public void testNotifyStudentGroupAboutExercisePractice() {
        groupNotificationService.notifyStudentGroupAboutExercisePractice(exercise);
        verifyRepositoryCallWithCorrectNotification(1, EXERCISE_PRACTICE_TITLE);
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
        groupNotificationService.notifyAllGroupsAboutReleasedExercise(exercise);
        verifyRepositoryCallWithCorrectNotification(NUMBER_OF_ALL_GROUPS, EXERCISE_RELEASED_TITLE);
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
        groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForCoursePost(post, answerPost, course);
        verifyRepositoryCallWithCorrectNotification(3, NEW_REPLY_FOR_COURSE_POST_TITLE);
    }

    /**
     * Test for notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForExercise method
     */
    @Test
    public void testNotifyTutorAndEditorAndInstructorGroupAboutNewAnswerForExercise() {
        groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForExercise(post, answerPost, course);
        verifyRepositoryCallWithCorrectNotification(3, NEW_REPLY_FOR_EXERCISE_POST_TITLE);
    }

    /**
     * Test for notifyAllGroupsAboutNewAnnouncement method
     */
    @Test
    public void testNotifyAllGroupsAboutNewAnnouncement() {
        groupNotificationService.notifyAllGroupsAboutNewAnnouncement(post, course);
        verifyRepositoryCallWithCorrectNotification(NUMBER_OF_ALL_GROUPS, NEW_ANNOUNCEMENT_POST_TITLE);
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
        groupNotificationService.notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise(programmingExercise);
        verifyRepositoryCallWithCorrectNotification(2, PROGRAMMING_TEST_CASES_CHANGED_TITLE);
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

    /// Save & Send related Tests

    // Exam Exercise Update

    /**
     * Basic Test for saveAndSend method for an exam exercise update (notification)
     * Checks if a correct notification was created and no settings or email functionality was invoked
     */
    @Test
    public void testSaveAndSend_ExamExerciseUpdate_basics() {
        groupNotificationService.notifyAboutExerciseUpdate(examExercise, NOTIFICATION_TEXT);

        verify(groupNotificationRepository, times(3)).save(any());
        verify(messagingTemplate, times(3)).convertAndSend(any(), notificationCaptor.capture());
        capturedNotification = notificationCaptor.getValue();
        assertThat(capturedNotification.getTitle()).as("The title of the captured notification should be equal to the one for live exam updated")
                .isEqualTo(LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE);

        // there should be no interaction with settings or email services
        verify(notificationSettingsService, times(0)).checkNotificationTypeForEmailSupport(any());
    }

    /**
     * Test for saveAndSend method for an exam exercise update (notification)
     * Checks if the notification target contains the problem statement for transmitting it to the user via WebSocket
     */
    @Test
    public void testSaveAndSend_ExamExerciseUpdate_correctTargetForSendingViaWebSocket() {
        groupNotificationService.notifyAboutExerciseUpdate(examExercise, NOTIFICATION_TEXT);

        verify(messagingTemplate, times(3)).convertAndSend(any(), notificationCaptor.capture());
        capturedNotification = notificationCaptor.getValue();

        // The notification target of notification that will be sent to the user via webapp at runtime should contain the problem statement again
        assertThat(capturedNotification.getTarget().length())
                .as("The problem statement of the captured notification which will be send via WebSocket should contain the entire problem statement")
                .isGreaterThanOrEqualTo(EXAM_PROBLEM_STATEMENT.length());
    }

    // Course related Notifications -> should use Settings & Email Services

    /**
     * Test for saveAndSend method for an exam exercise update (notification)
     * Checks if the notification target contains the problem statement for transmitting it to the user via WebSocket
     */
    @Test
    public void testSaveAndSend_CourseRelatedNotifications() {
        when(notificationSettingsService.checkNotificationTypeForEmailSupport(any())).thenReturn(true);
        when(notificationSettingsService.checkIfNotificationOrEmailIsAllowedBySettingsForGivenUser(any(), any(), any())).thenReturn(true);

        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);

        // inside private saveAndSend method
        verify(groupNotificationRepository, times(3)).save(any());
        verify(messagingTemplate, times(3)).convertAndSend(any(), (Notification) any());
        verify(notificationSettingsService, times(3)).checkNotificationTypeForEmailSupport(any());

        // inside private prepareSendingGroupEmail method
        verify(userRepository, times(1)).getStudents(course);
        verify(userRepository, times(1)).getInstructors(course);
        verify(userRepository, times(1)).getEditors(course);

        // inside private prepareGroupNotificationEmail
        verify(mailService, times(3)).sendNotificationEmailForMultipleUsers(any(), any(), any());
    }

}
