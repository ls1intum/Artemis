package de.tum.in.www1.artemis.service;

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
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.service.notifications.NotificationSettingsService;

public class GroupNotificationServiceTest {

    @Autowired
    private static GroupNotificationService groupNotificationService;

    @Captor
    private static ArgumentCaptor<Notification> notificationCaptor;

    private Notification capturedNotification;

    @Mock
    private static UserRepository userRepository;

    @Mock
    private static User user;

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

    @Mock
    private static Exercise exercise;

    private final static Long EXERCISE_ID = 13L;

    @Mock
    private static QuizExercise quizExercise;

    @Mock
    private static ProgrammingExercise programmingExercise;

    @Mock
    private static Lecture lecture;

    @Mock
    private static Post post;

    @Mock
    private static Course course;

    private final static Long COURSE_ID = 27L;

    @Mock
    private static Exam exam;

    @Mock
    private static Attachment attachment;

    private static List<String> archiveErrors;

    private final static Long EXAM_ID = 42L;

    private final static String NOTIFICATION_TEXT = "notificationText";

    private final static int NUMBER_OF_ALL_GROUPS = 4;

    private enum ExerciseStatus {
        COURSE_EXERCISE_STATUS, EXAM_EXERCISE_STATUS
    }

    /**
     * Auxiliary method to set the correct mock behavior for exercise status
     * @param exerciseStatus indicates if the exercise is a course or exam exercise
     */
    private void setExerciseStatus(ExerciseStatus exerciseStatus) {
        when(exercise.isExamExercise()).thenReturn(exerciseStatus == ExerciseStatus.EXAM_EXERCISE_STATUS);
        when(exercise.isCourseExercise()).thenReturn(exerciseStatus == ExerciseStatus.COURSE_EXERCISE_STATUS);
    }

    /**
     * Sets up all needed mocks and their wanted behavior once for all test cases.
     * These are the common mocks and structures which behavior is fixed and will not change
     */
    @BeforeAll
    public static void setUp() {
        userRepository = mock(UserRepository.class);

        notificationCaptor = ArgumentCaptor.forClass(Notification.class);

        groupNotificationRepository = mock(GroupNotificationRepository.class);

        messagingTemplate = mock(SimpMessageSendingOperations.class);

        notificationSettingsService = mock(NotificationSettingsService.class);

        groupNotificationService = spy(new GroupNotificationService(groupNotificationRepository, messagingTemplate, userRepository, mailService, notificationSettingsService));

        user = mock(User.class);

        archiveErrors = new ArrayList<>();

        course = mock(Course.class);
        when(course.getId()).thenReturn(COURSE_ID);

        exam = mock(Exam.class);
        when(exam.getId()).thenReturn(EXAM_ID);
        when(exam.getCourse()).thenReturn(course);

        lecture = mock(Lecture.class);
        when(lecture.getCourse()).thenReturn(course);

        attachment = mock(Attachment.class);

        exercise = mock(Exercise.class);

        quizExercise = mock(QuizExercise.class);
        when(quizExercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);

        programmingExercise = mock(ProgrammingExercise.class);
        when(programmingExercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);

        post = mock(Post.class);
        when(post.getExercise()).thenReturn(exercise);
        when(post.getLecture()).thenReturn(lecture);

        instanceMessageSendService = mock(InstanceMessageSendService.class);
        doNothing().when(instanceMessageSendService).sendExerciseReleaseNotificationSchedule(EXERCISE_ID);
    }

    /**
     * Prepares and cleans the mocks that are modified during the tests
     */
    @BeforeEach
    public void cleanMocks() {
        reset(exercise);
        when(exercise.getExamViaExerciseGroupOrCourseMember()).thenReturn(exam);
        when(exercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);

        reset(groupNotificationService);

        reset(attachment);
        when(attachment.getLecture()).thenReturn(lecture);

        reset(groupNotificationRepository);
        when(groupNotificationRepository.save(any())).thenReturn(null);

        capturedNotification = null;
    }

    /**
     * Auxiliary method that checks if the groupNotificationRepository was called once successfully with the correct notification (type)
     * @param numberOfGroupsAndCalls indicates the expected number of notifications created/saved.
     * This number depends on the number of different groups. For each different group one separate call is needed.
     * @param expectedNotificationTitle is the title (NotificationTitleTypeConstants) of the expected notification
     */
    private void verifyRepositoryCallWithCorrectNotification(int numberOfGroupsAndCalls, String expectedNotificationTitle) {
        verify(groupNotificationRepository, times(numberOfGroupsAndCalls)).save(notificationCaptor.capture());
        capturedNotification = notificationCaptor.getValue();
        assertThat(capturedNotification.getTitle()).isEqualTo(expectedNotificationTitle);
    }

    /// Exercise Update / Release & Scheduling related Tests

    // NotifyAboutExerciseUpdate

    /**
    * Test for notifyAboutExerciseUpdate method with an undefined release date
    */
    @Test
    public void testNotifyAboutExerciseUpdate_undefinedReleaseDate() {
        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService, times(0)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
    }

    /**
    * Test for notifyAboutExerciseUpdate method with an future release date
    */
    @Test
    public void testNotifyAboutExerciseUpdate_futureReleaseDate() {
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now().plusHours(1));
        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService, times(0)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
    }

    /**
    * Test for notifyAboutExerciseUpdate method with a correct release date (now) for exam exercises
    */
    @Test
    public void testNotifyAboutExerciseUpdate_correctReleaseDate_examExercise() {
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now());
        setExerciseStatus(ExerciseStatus.EXAM_EXERCISE_STATUS);
        doNothing().when(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);

        groupNotificationService.notifyAboutExerciseUpdate(exercise, null);

        verify(groupNotificationService, times(1)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());
    }

    /**
    * Test for notifyAboutExerciseUpdate method with a correct release date (now) for course exercises
    */
    @Test
    public void testNotifyAboutExerciseUpdate_correctReleaseDate_courseExercise() {
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now());
        setExerciseStatus(ExerciseStatus.COURSE_EXERCISE_STATUS);
        doNothing().when(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);

        groupNotificationService.notifyAboutExerciseUpdate(exercise, null);
        verify(groupNotificationService, times(0)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());

        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService, times(1)).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any(), any());
    }

    // CheckNotificationForExerciseRelease

    /**
     * Auxiliary methods for testing the checkNotificationForExerciseRelease
     */
    private void prepareMocksForCheckNotificationForExerciseReleaseTesting() {
        setExerciseStatus(ExerciseStatus.COURSE_EXERCISE_STATUS);
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
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now());
        groupNotificationService.checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        verify(groupNotificationService, times(1)).notifyAllGroupsAboutReleasedExercise(any());
    }

    /**
    * Test for checkNotificationForExerciseRelease method with a future release date
    */
    @Test
    public void testCheckNotificationForExerciseRelease_futureReleaseDate() {
        prepareMocksForCheckNotificationForExerciseReleaseTesting();
        when(exercise.getReleaseDate()).thenReturn(ZonedDateTime.now().plusHours(1));
        groupNotificationService.checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        verify(instanceMessageSendService, times(1)).sendExerciseReleaseNotificationSchedule(any());
    }

    // CheckAndCreateAppropriateNotificationsWhenUpdatingExercise

    /**
    * Test for checkAndCreateAppropriateNotificationsWhenUpdatingExercise method
    */
    @Test
    public void testCheckAndCreateAppropriateNotificationsWhenUpdatingExercise() {
        doNothing().when(groupNotificationService).notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        doNothing().when(groupNotificationService).checkNotificationForExerciseRelease(exercise, instanceMessageSendService);
        groupNotificationService.checkAndCreateAppropriateNotificationsWhenUpdatingExercise(exercise, NOTIFICATION_TEXT, instanceMessageSendService);
        verify(groupNotificationService, times(1)).notifyAboutExerciseUpdate(any(), any());
        verify(groupNotificationService, times(1)).checkNotificationForExerciseRelease(any(), any());
    }

    /// General notifyGroupX Tests

    /**
     * Test for notifyStudentGroupAboutAttachmentChange method with a future release date
     */
    @Test
    public void testNotifyStudentGroupAboutAttachmentChange_futureReleaseDate() {
        when(attachment.getReleaseDate()).thenReturn(ZonedDateTime.now().plusHours(1));
        groupNotificationService.notifyStudentGroupAboutAttachmentChange(attachment, NOTIFICATION_TEXT);
        verify(groupNotificationRepository, times(0)).save(any());
    }

    /**
     * Test for notifyStudentGroupAboutAttachmentChange method with a non future release date
     */
    @Test
    public void testNotifyStudentGroupAboutAttachmentChange_nonFutureReleaseDate() {
        when(attachment.getReleaseDate()).thenReturn(ZonedDateTime.now());
        groupNotificationService.notifyStudentGroupAboutAttachmentChange(attachment, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotification(1, NotificationTitleTypeConstants.ATTACHMENT_CHANGE_TITLE);
    }

    /**
     * Test for notifyStudentGroupAboutExercisePractice method
     */
    @Test
    public void testNotifyStudentGroupAboutExercisePractice() {
        groupNotificationService.notifyStudentGroupAboutExercisePractice(exercise);
        verifyRepositoryCallWithCorrectNotification(1, NotificationTitleTypeConstants.EXERCISE_PRACTICE_TITLE);
    }

    /**
     * Test for notifyStudentGroupAboutQuizExerciseStart method
     */
    @Test
    public void testNotifyStudentGroupAboutQuizExerciseStart() {
        groupNotificationService.notifyStudentGroupAboutQuizExerciseStart(quizExercise);
        verifyRepositoryCallWithCorrectNotification(1, NotificationTitleTypeConstants.QUIZ_EXERCISE_STARTED_TITLE);
    }

    /**
     * Test for notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate method
     */
    @Test
    public void testNotifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate() {
        groupNotificationService.notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotification(3, NotificationTitleTypeConstants.EXERCISE_UPDATED_TITLE);
    }

    /**
     * Test for notifyAllGroupsAboutReleasedExercise method
     */
    @Test
    public void testNotifyAllGroupsAboutReleasedExercise() {
        groupNotificationService.notifyAllGroupsAboutReleasedExercise(exercise);
        verifyRepositoryCallWithCorrectNotification(NUMBER_OF_ALL_GROUPS, NotificationTitleTypeConstants.EXERCISE_RELEASED_TITLE);
    }

    /**
     * Test for notifyEditorAndInstructorGroupAboutExerciseUpdate method
     */
    @Test
    public void testNotifyEditorAndInstructorGroupAboutExerciseUpdate() {
        groupNotificationService.notifyEditorAndInstructorGroupAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotification(2, NotificationTitleTypeConstants.EXERCISE_UPDATED_TITLE);
    }

    /**
     * Test for notifyEditorAndInstructorGroupAboutExerciseUpdate method
     */
    @Test
    public void testNotifyAllGroupsAboutNewPostForExercise() {
        groupNotificationService.notifyAllGroupsAboutNewPostForExercise(post, course);
        verifyRepositoryCallWithCorrectNotification(NUMBER_OF_ALL_GROUPS, NotificationTitleTypeConstants.NEW_EXERCISE_POST_TITLE);
    }

    /**
     * Test for notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise method
     */
    @Test
    public void testNotifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise() {
        groupNotificationService.notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise(programmingExercise, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotification(2, NotificationTitleTypeConstants.DUPLICATE_TEST_CASE_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutIllegalSubmissionsForExercise method
     */
    @Test
    public void testNotifyInstructorGroupAboutIllegalSubmissionsForExercise() {
        groupNotificationService.notifyInstructorGroupAboutIllegalSubmissionsForExercise(exercise, NOTIFICATION_TEXT);
        verifyRepositoryCallWithCorrectNotification(1, NotificationTitleTypeConstants.ILLEGAL_SUBMISSION_TITLE);
    }

    /**
     * Test for notifyAllGroupsAboutNewPostForLecture method
     */
    @Test
    public void testNotifyAllGroupsAboutNewPostForLecture() {
        groupNotificationService.notifyAllGroupsAboutNewPostForLecture(post, course);
        verifyRepositoryCallWithCorrectNotification(NUMBER_OF_ALL_GROUPS, NotificationTitleTypeConstants.NEW_LECTURE_POST_TITLE);
    }

    /**
     * Test for notifyAllGroupsAboutNewCoursePost method
     */
    @Test
    public void testNotifyAllGroupsAboutNewCoursePost() {
        groupNotificationService.notifyAllGroupsAboutNewCoursePost(post, course);
        verifyRepositoryCallWithCorrectNotification(NUMBER_OF_ALL_GROUPS, NotificationTitleTypeConstants.NEW_COURSE_POST_TITLE);
    }

    /**
     * Test for notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForCoursePost method
     */
    @Test
    public void testNotifyTutorAndEditorAndInstructorGroupAboutNewAnswerForCoursePost() {
        groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForCoursePost(post, course);
        verifyRepositoryCallWithCorrectNotification(3, NotificationTitleTypeConstants.NEW_REPLY_FOR_COURSE_POST_TITLE);
    }

    /**
     * Test for notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForExercise method
     */
    @Test
    public void testNotifyTutorAndEditorAndInstructorGroupAboutNewAnswerForExercise() {
        groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForExercise(post, course);
        verifyRepositoryCallWithCorrectNotification(3, NotificationTitleTypeConstants.NEW_REPLY_FOR_EXERCISE_POST_TITLE);
    }

    /**
     * Test for notifyAllGroupsAboutNewAnnouncement method
     */
    @Test
    public void testNotifyAllGroupsAboutNewAnnouncement() {
        groupNotificationService.notifyAllGroupsAboutNewAnnouncement(post, course);
        verifyRepositoryCallWithCorrectNotification(NUMBER_OF_ALL_GROUPS, NotificationTitleTypeConstants.NEW_ANNOUNCEMENT_POST_TITLE);
    }

    /**
     * Test for notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForLecture method
     */
    @Test
    public void testNotifyTutorAndEditorAndInstructorGroupAboutNewAnswerForLecture() {
        groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForLecture(post, course);
        verifyRepositoryCallWithCorrectNotification(3, NotificationTitleTypeConstants.NEW_REPLY_FOR_LECTURE_POST_TITLE);
    }

    // Course Archiving

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type CourseArchiveStarted
     */
    @Test
    public void testNotifyInstructorGroupAboutCourseArchiveState_CourseArchiveStarted() {
        groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, NotificationType.COURSE_ARCHIVE_STARTED, archiveErrors);
        verifyRepositoryCallWithCorrectNotification(1, NotificationTitleTypeConstants.COURSE_ARCHIVE_STARTED_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type CourseArchiveFailed
     */
    @Test
    public void testNotifyInstructorGroupAboutCourseArchiveState_CourseArchiveFailed() {
        groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, NotificationType.COURSE_ARCHIVE_FAILED, archiveErrors);
        verifyRepositoryCallWithCorrectNotification(1, NotificationTitleTypeConstants.COURSE_ARCHIVE_FAILED_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type CourseArchiveFinished
     */
    @Test
    public void testNotifyInstructorGroupAboutCourseArchiveState_CourseArchiveFinished() {
        groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, NotificationType.COURSE_ARCHIVE_FINISHED, archiveErrors);
        verifyRepositoryCallWithCorrectNotification(1, NotificationTitleTypeConstants.COURSE_ARCHIVE_FINISHED_TITLE);
    }

    // Exam Archiving

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type ExamArchiveStarted
     */
    @Test
    public void testNotifyInstructorGroupAboutCourseArchiveState_ExamArchiveStarted() {
        groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, NotificationType.EXAM_ARCHIVE_STARTED, archiveErrors);
        verifyRepositoryCallWithCorrectNotification(1, NotificationTitleTypeConstants.EXAM_ARCHIVE_STARTED_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type ExamArchiveFailed
     */
    @Test
    public void testNotifyInstructorGroupAboutCourseArchiveState_ExamArchiveFailed() {
        groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, NotificationType.EXAM_ARCHIVE_FAILED, archiveErrors);
        verifyRepositoryCallWithCorrectNotification(1, NotificationTitleTypeConstants.EXAM_ARCHIVE_FAILED_TITLE);
    }

    /**
     * Test for notifyInstructorGroupAboutCourseArchiveState method for the notification type ExamArchiveFinished
     */
    @Test
    public void testNotifyInstructorGroupAboutCourseArchiveState_ExamArchiveFinished() {
        groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, NotificationType.EXAM_ARCHIVE_FINISHED, archiveErrors);
        verifyRepositoryCallWithCorrectNotification(1, NotificationTitleTypeConstants.EXAM_ARCHIVE_FINISHED_TITLE);
    }
}
