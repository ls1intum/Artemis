package de.tum.in.www1.artemis.notification;

import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.FILE_SUBMISSION_SUCCESSFUL_TITLE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.NotificationTarget;

class NotificationTargetFactoryTest {

    private static Post post;

    private static final Long POST_ID = 101L;

    private static Course course;

    private static final Long COURSE_ID = 42L;

    private static Lecture lecture;

    private static final Long LECTURE_ID = 27L;

    private static Exercise exercise;

    private static final Long EXERCISE_ID = 13L;

    private static ProgrammingExercise programmingExercise;

    private static Notification notification;

    private NotificationTarget notificationTransientTarget;

    private static final String BASE_URL = "https://artemistest.ase.in.tum.de";

    private static final String DISCUSSION_SEARCH_TEXT = "discussion?searchText=%23";

    private String resultingURL;

    private static final String PROBLEM_STATEMENT = "problem statement";

    private static final Long EXAM_ID = 27L;

    private static NotificationTarget originalTransientTargetWithProblemStatement;

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
        notification.setTransientAndStringTarget(notificationTransientTarget);
        resultingURL = extractNotificationUrl(notification, BASE_URL);
        assertThat(resultingURL).as("Resulting URL should be equal to expected URL").isEqualTo(expectedURL);
    }

    /**
     * Prepares the needed values and objects for testing
     */
    @BeforeAll
    static void setUp() {
        prepareOriginalTransientTargetWithProblemStatement();

        course = new Course();
        course.setId(COURSE_ID);

        post = new Post();
        post.setId(POST_ID);
        post.setCourse(course);

        lecture = new Lecture();
        lecture.setId(LECTURE_ID);
        lecture.setCourse(course);

        exercise = new TextExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setCourse(course);

        programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(EXERCISE_ID);
        programmingExercise.setCourse(course);

        notification = new GroupNotification();
    }

    /**
     * Auxiliary method to prepare the comparison value for the original transient notification target with problem statement
     * Expected value -> "{\"problemStatement\":\"PROBLEM STATEMENT\",\"exercise\":3,\"exam\":1,\"entity\":\"exams\",\"course\":1,\"mainPage\":\"courses\"}"
     */
    private static void prepareOriginalTransientTargetWithProblemStatement() {
        originalTransientTargetWithProblemStatement = new NotificationTarget(EXAMS_TEXT, COURSE_ID, COURSES_TEXT);
        originalTransientTargetWithProblemStatement.setProblemStatement(PROBLEM_STATEMENT);
        originalTransientTargetWithProblemStatement.setExerciseId(EXERCISE_ID);
        originalTransientTargetWithProblemStatement.setExamId(EXAM_ID);
    }

    /// extractNotificationUrl test (very important for e.g. MailService and Emails to contain valid Links)

    @Test
    void extractNotificationUrl_Posts_Announcement() { // e.g. used for announcementPostEmail.html
        resultingURL = extractNotificationUrl(post, BASE_URL);
        assertThat(resultingURL).as("Resulting Post URL should be equal to expected one").isEqualTo(EXPECTED_POST_URL);
    }

    @Test
    void extractNotificationUrl_NotificationType_AttachmentChanged() { // e.g. used for attachmentChangedEmail.html
        notificationTransientTarget = createAttachmentUpdatedTarget(lecture);
        mockExtractAndAssertNotificationTarget(EXPECTED_ATTACHMENT_CHANGED_URL);
    }

    @Test
    void extractNotificationUrl_NotificationType_DuplicateTestCases() { // e.g. used for duplicateTestCasesEmail.html
        notificationTransientTarget = createExamProgrammingExerciseOrTestCaseTarget(programmingExercise, DUPLICATE_TEST_CASE_TEXT);
        mockExtractAndAssertNotificationTarget(EXPECTED_DUPLICATE_TEST_CASES_URL);
    }

    @Test
    void extractNotificationUrl_NotificationType_ExerciseOpenForPractice() { // e.g. used for exerciseOpenForPracticeEmail.html
        notificationTransientTarget = createExerciseUpdatedTarget(exercise);
        mockExtractAndAssertNotificationTarget(EXPECTED_EXERCISE_URL);
    }

    @Test
    void extractNotificationUrl_NotificationType_ExerciseReleased() { // e.g. used for exerciseReleasedEmail.html
        notificationTransientTarget = createExerciseReleasedTarget(exercise);
        mockExtractAndAssertNotificationTarget(EXPECTED_EXERCISE_URL);
    }

    @Test
    void extractNotificationUrl_NotificationType_FileSubmissionSuccessful() { // e.g. used for fileSubmissionSuccessfulEmail.html
        notificationTransientTarget = createExerciseTarget(exercise, FILE_SUBMISSION_SUCCESSFUL_TITLE);
        mockExtractAndAssertNotificationTarget(EXPECTED_EXERCISE_URL);
    }
}
