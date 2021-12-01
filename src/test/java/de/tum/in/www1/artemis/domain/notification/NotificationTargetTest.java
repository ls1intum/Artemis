package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.FILE_SUBMISSION_SUCCESSFUL_TITLE;
import static de.tum.in.www1.artemis.service.notifications.NotificationTargetService.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.service.notifications.NotificationTargetService;

/**
 * NotificationTarget is extensively used in the MailService to create Emails based on Notifications
 * Every NotificationType that has a corresponding Email should be tested here as well to ensure valid email links
 */
public class NotificationTargetTest {

    @Autowired
    private static NotificationTargetService notificationTargetProvider;

    @Mock
    private static Post post;

    private static final Long POST_ID = 101L;

    @Mock
    private static Course course;

    private static final Long COURSE_ID = 42L;

    @Mock
    private static Lecture lecture;

    private static final Long LECTURE_ID = 27L;

    @Mock
    private static Exercise exercise;

    private static final Long EXERCISE_ID = 13L;

    @Mock
    private static ProgrammingExercise programmingExercise;

    @Mock
    private static Notification notification;

    private String notificationTarget;

    private static final String BASE_URL = "https://artemistest.ase.in.tum.de/";

    private static final String DISCUSSION_SEARCH_TEXT = "discussion?searchText=%23";

    private String resultingURL;

    /// Auxiliary constants

    private static final String BASE_URL_COURSES_COURSE_ID = BASE_URL + "/" + COURSES_TEXT + "/" + COURSE_ID;

    // expected/correct URLs

    // e.g. https://artemistest.ase.in.tum.de/courses/477/discussion?searchText=%232000
    private static final String EXPECTED_POST_URL = BASE_URL_COURSES_COURSE_ID + "/" + DISCUSSION_SEARCH_TEXT + POST_ID;

    // e.g. https://artemistest.ase.in.tum.de/courses/477/lectures/199
    private static final String EXPECTED_ATTACHMENT_CHANGED_URL = BASE_URL_COURSES_COURSE_ID + "/" + LECTURES_TEXT + "/" + LECTURE_ID;

    // e.g. https://artemistest.ase.in.tum.de/course-management/477/programming-exercises/13355
    private static final String EXPECTED_DUPLICATE_TEST_CASES_URL = BASE_URL + "/" + COURSE_MANAGEMENT_TEXT + "/" + COURSE_ID + "/" + PROGRAMMING_EXERCISES_TEXT + "/"
            + EXERCISE_ID;

    // e.g. https://artemistest.ase.in.tum.de/courses/477/exercises/13311
    private static final String EXPECTED_EXERCISE_URL = BASE_URL_COURSES_COURSE_ID + "/" + EXERCISES_TEXT + "/" + EXERCISE_ID;

    /**
     * Auxiliary method to mock, extract and check the notificationTarget
     */
    private void mockExtractAndAssertNotificationTarget(String expectedURL) {
        when(notification.getTarget()).thenReturn(notificationTarget);
        resultingURL = NotificationTarget.extractNotificationUrl(notification, BASE_URL);
        assertThat(resultingURL).isEqualTo(expectedURL);
    }

    /**
     * sets up all needed mocks and their wanted behavior once for all test cases.
     */
    @BeforeAll
    public static void setUp() {
        notificationTargetProvider = new NotificationTargetService();

        course = mock(Course.class);
        when(course.getId()).thenReturn(COURSE_ID);

        post = mock(Post.class);
        when(post.getId()).thenReturn(POST_ID);
        when(post.getCourse()).thenReturn(course);

        lecture = mock(Lecture.class);
        when(lecture.getId()).thenReturn(LECTURE_ID);
        when(lecture.getCourse()).thenReturn(course);

        exercise = mock(Exercise.class);
        when(exercise.getId()).thenReturn(EXERCISE_ID);
        when(exercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);

        programmingExercise = mock(ProgrammingExercise.class);
        when(programmingExercise.getId()).thenReturn(EXERCISE_ID);
        when(programmingExercise.getCourseViaExerciseGroupOrCourseMember()).thenReturn(course);

        notification = mock(Notification.class);
    }

    /// extractNotificationUrl test (very important for e.g. MailService and Emails to contain valid Links)

    @Test
    public void extractNotificationUrl_Posts_Announcement() { // e.g. used for announcementPostEmail.html
        resultingURL = NotificationTarget.extractNotificationUrl(post, BASE_URL);
        assertThat(resultingURL).isEqualTo(EXPECTED_POST_URL);
    }

    @Test
    public void extractNotificationUrl_NotificationType_AttachmentChanged() { // e.g. used for attachmentChangedEmail.html
        notificationTarget = notificationTargetProvider.getAttachmentUpdatedTarget(lecture);
        mockExtractAndAssertNotificationTarget(EXPECTED_ATTACHMENT_CHANGED_URL);
    }

    @Test
    public void extractNotificationUrl_NotificationType_DuplicateTestCases() { // e.g. used for duplicateTestCasesEmail.html
        notificationTarget = notificationTargetProvider.getExamProgrammingExerciseOrTestCaseTarget(programmingExercise, DUPLICATE_TEST_CASE_TEXT);
        mockExtractAndAssertNotificationTarget(EXPECTED_DUPLICATE_TEST_CASES_URL);
    }

    @Test
    public void extractNotificationUrl_NotificationType_ExerciseOpenForPractice() { // e.g. used for exerciseOpenForPracticeEmail.html
        notificationTarget = notificationTargetProvider.getExerciseUpdatedTarget(exercise);
        mockExtractAndAssertNotificationTarget(EXPECTED_EXERCISE_URL);
    }

    @Test
    public void extractNotificationUrl_NotificationType_ExerciseReleased() { // e.g. used for exerciseReleasedEmail.html
        notificationTarget = notificationTargetProvider.getExerciseReleasedTarget(exercise);
        mockExtractAndAssertNotificationTarget(EXPECTED_EXERCISE_URL);
    }

    @Test
    public void extractNotificationUrl_NotificationType_FileSubmissionSuccessful() { // e.g. used for fileSubmissionSuccessfulEmail.html
        notificationTarget = notificationTargetProvider.getExerciseTarget(exercise, FILE_SUBMISSION_SUCCESSFUL_TITLE);
        mockExtractAndAssertNotificationTarget(EXPECTED_EXERCISE_URL);
    }
}
