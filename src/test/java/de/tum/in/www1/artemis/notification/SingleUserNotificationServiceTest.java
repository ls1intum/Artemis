package de.tum.in.www1.artemis.notification;

import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.List;

import javax.mail.internet.MimeMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.util.ModelFactory;

class SingleUserNotificationServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private SingleUserNotificationService singleUserNotificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    private User user;

    private FileUploadExercise fileUploadExercise;

    private Post post;

    private Course course;

    private Exercise exercise;

    private PlagiarismCase plagiarismCase;

    private Result result;

    /**
     * Sets up all needed mocks and their wanted behavior
     */
    @BeforeEach
    void setUp() {
        SecurityUtils.setAuthorizationObject();

        course = database.createCourse();

        List<User> users = database.addUsers(3, 0, 0, 0);
        user = users.get(0);

        exercise = new TextExercise();
        exercise.setCourse(course);
        exercise.setMaxPoints(10D);

        fileUploadExercise = new FileUploadExercise();
        fileUploadExercise.setCourse(course);

        Lecture lecture = new Lecture();
        lecture.setCourse(course);

        post = new Post();
        post.setExercise(exercise);
        post.setLecture(lecture);
        post.setAuthor(user);
        post.setCourse(course);

        PlagiarismSubmission<TextSubmissionElement> plagiarismSubmission = new PlagiarismSubmission<>();
        plagiarismSubmission.setStudentLogin(user.getLogin());

        TextPlagiarismResult plagiarismResult = new TextPlagiarismResult();
        plagiarismResult.setExercise(exercise);

        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        plagiarismComparison.setSubmissionA(plagiarismSubmission);
        plagiarismComparison.setPlagiarismResult(plagiarismResult);

        plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(exercise);

        result = new Result();
        result.setScore(1D);
        result.setCompletionDate(ZonedDateTime.now().minusMinutes(1));

        doNothing().when(javaMailSender).send(any(MimeMessage.class));
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    /**
     * Auxiliary method that checks if the groupNotificationRepository was called once successfully with the correct notification (type)
     *
     * @param expectedNotificationTitle is the title (NotificationTitleTypeConstants) of the expected notification
     */
    private void verifyRepositoryCallWithCorrectNotification(String expectedNotificationTitle) {
        Notification capturedNotification = notificationRepository.findAll().get(0);
        assertThat(capturedNotification.getTitle()).as("Title of the captured notification should be equal to the expected one").isEqualTo(expectedNotificationTitle);
    }

    /// General notify Tests

    /**
     * Tests if no notification (or email) is sent if the settings are deactivated
     * However, the notification has to be saved to the DB
     */
    @Test
    void testSendNoNotificationOrEmailWhenSettingsAreDeactivated() {
        notificationSettingRepository.save(new NotificationSetting(user, false, true, NOTIFICATION__EXERCISE_NOTIFICATION__NEW_REPLY_FOR_EXERCISE_POST));
        assertThat(notificationRepository.findAll()).as("No notifications should be present prior to the method call").isEmpty();

        singleUserNotificationService.notifyUserAboutNewReplyForExercise(post, course);

        assertThat(notificationRepository.findAll()).as("The notification should have been saved to the DB").hasSize(1);
        // no web app notification or email should be sent
        verify(messagingTemplate, times(0)).convertAndSend(any());
    }

    /**
     * Test for notifyStudentGroupAboutAttachmentChange method
     */
    @Test
    void testNotifyUserAboutNewAnswerForExercise() {
        singleUserNotificationService.notifyUserAboutNewReplyForExercise(post, course);
        verifyRepositoryCallWithCorrectNotification(NEW_REPLY_FOR_EXERCISE_POST_TITLE);
    }

    /**
     * Test for notifyUserAboutNewAnswerForLecture method
     */
    @Test
    void testNotifyUserAboutNewAnswerForLecture() {
        singleUserNotificationService.notifyUserAboutNewReplyForLecture(post, course);
        verifyRepositoryCallWithCorrectNotification(NEW_REPLY_FOR_LECTURE_POST_TITLE);
    }

    /**
     * Test for notifyUserAboutNewAnswerForCoursePost method
     */
    @Test
    void testNotifyUserAboutNewAnswerForCoursePost() {
        singleUserNotificationService.notifyUserAboutNewReplyForCoursePost(post, course);
        verifyRepositoryCallWithCorrectNotification(NEW_REPLY_FOR_COURSE_POST_TITLE);
    }

    /**
     * Test for notifyUserAboutSuccessfulFileUploadSubmission method
     */
    @Test
    void testNotifyUserAboutSuccessfulFileUploadSubmission() {
        notificationSettingRepository.save(new NotificationSetting(user, true, true, NOTIFICATION__EXERCISE_NOTIFICATION__FILE_SUBMISSION_SUCCESSFUL));
        singleUserNotificationService.notifyUserAboutSuccessfulFileUploadSubmission(fileUploadExercise, user);
        verifyRepositoryCallWithCorrectNotification(FILE_SUBMISSION_SUCCESSFUL_TITLE);
        verifyEmail();
    }

    // AssessedExerciseSubmission related

    /**
     * Test for notifyUserAboutAssessedExerciseSubmission method
     */
    @Test
    void testNotifyUserAboutAssessedExerciseSubmission() {
        NotificationSetting notificationSetting = new NotificationSetting(user, true, true, NOTIFICATION__EXERCISE_NOTIFICATION__EXERCISE_SUBMISSION_ASSESSED);
        notificationSettingRepository.save(notificationSetting);

        singleUserNotificationService.checkNotificationForAssessmentExerciseSubmission(exercise, user, result);

        verifyRepositoryCallWithCorrectNotification(EXERCISE_SUBMISSION_ASSESSED_TITLE);
        verifyEmail();
    }

    /**
     * Test for checkNotificationForAssessmentExerciseSubmission method with an undefined release date
     */
    @Test
    void testCheckNotificationForAssessmentExerciseSubmission_undefinedAssessmentDueDate() {
        exercise = ModelFactory.generateTextExercise(null, null, null, course);
        singleUserNotificationService.checkNotificationForAssessmentExerciseSubmission(exercise, user, result);
        verify(singleUserNotificationService, times(1)).checkNotificationForAssessmentExerciseSubmission(exercise, user, result);
    }

    /**
     * Test for checkNotificationForExerciseRelease method with a current or past release date
     */
    @Test
    void testCheckNotificationForAssessmentExerciseSubmission_currentOrPastAssessmentDueDate() {
        exercise = ModelFactory.generateTextExercise(null, null, ZonedDateTime.now(), course);
        singleUserNotificationService.checkNotificationForAssessmentExerciseSubmission(exercise, user, result);
        assertThat(notificationRepository.findAll()).as("One new notification should have been created").hasSize(1);
    }

    /**
     * Test for checkNotificationForExerciseRelease method with a future release date
     */
    @Test
    void testCheckNotificationForAssessmentExerciseSubmission_futureAssessmentDueDate() {
        exercise = ModelFactory.generateTextExercise(null, null, ZonedDateTime.now().plusHours(1), course);
        singleUserNotificationService.checkNotificationForAssessmentExerciseSubmission(exercise, user, result);
        assertThat(notificationRepository.findAll()).as("No new notification should have been created").isEmpty();
    }

    @Test
    void testNotifyUsersAboutAssessedExerciseSubmission() {
        Course testCourse = database.addCourseWithFileUploadExercise();
        Exercise testExercise = testCourse.getExercises().iterator().next();

        User studentWithParticipationAndSubmissionAndResult = database.getUserByLogin("student1");
        User studentWithParticipationButWithoutSubmission = database.getUserByLogin("student2");

        database.createParticipationSubmissionAndResult(testExercise.getId(), studentWithParticipationAndSubmissionAndResult, 10.0, 10.0, 50, true);
        database.createAndSaveParticipationForExercise(testExercise, studentWithParticipationButWithoutSubmission.getLogin());

        testExercise = exerciseRepository.findAllExercisesByCourseId(testCourse.getId()).iterator().next();

        singleUserNotificationService.notifyUsersAboutAssessedExerciseSubmission(testExercise);

        assertThat(notificationRepository.findAll()).as("Only one notification should have been created (for the user with a valid participation, submission, and result)")
                .hasSize(1);
    }

    // Plagiarism related

    /**
     * Test for notifyUserAboutNewPossiblePlagiarismCase method
     */
    @Test
    void testNotifyUserAboutNewPossiblePlagiarismCase() {
        // explicitly change the user to prevent issues in the following server call due to userRepository.getUser() (@WithMockUser is not working here)
        database.changeUser("student1");
        Post post = new Post();
        post.setPlagiarismCase(new PlagiarismCase());
        post.setContent("You plagiarized!");
        plagiarismCase.setPost(new Post());
        singleUserNotificationService.notifyUserAboutNewPlagiarismCase(plagiarismCase, user);
        verifyRepositoryCallWithCorrectNotification(NEW_PLAGIARISM_CASE_STUDENT_TITLE);
        verifyEmail();
    }

    /**
     * Test for notifyUserAboutFinalPlagiarismState method
     */
    @Test
    void testNotifyUserAboutFinalPlagiarismState() {
        // explicitly change the user to prevent issues in the following server call due to userRepository.getUser() (@WithMockUser is not working here)
        database.changeUser("student1");
        plagiarismCase.setVerdict(PlagiarismVerdict.NO_PLAGIARISM);
        singleUserNotificationService.notifyUserAboutPlagiarismCaseVerdict(plagiarismCase, user);
        verifyRepositoryCallWithCorrectNotification(PLAGIARISM_CASE_VERDICT_STUDENT_TITLE);
        verifyEmail();
    }

    /**
     * Checks if an email was created and send
     */
    private void verifyEmail() {
        verify(javaMailSender, timeout(1000).times(1)).send(any(MimeMessage.class));
    }
}
