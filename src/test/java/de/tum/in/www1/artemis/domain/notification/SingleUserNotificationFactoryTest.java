package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.HIGH;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.MEDIUM;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.*;
import static de.tum.in.www1.artemis.domain.notification.SingleUserNotificationFactory.createNotification;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.NotificationPriority;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.plagiarism.*;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;

class SingleUserNotificationFactoryTest {

    private static final Long LECTURE_ID = 0L;

    private static final String LECTURE_TITLE = "lecture title";

    private static Course course;

    private static final Long COURSE_ID = 12L;

    private static final String COURSE_TITLE = "course title";

    private static Exercise exercise;

    private static final Long EXERCISE_ID = 42L;

    private static final String EXERCISE_TITLE = "exercise title";

    private static final String PROBLEM_STATEMENT = "problem statement";

    private static final String POST_TITLE = "post title";

    private static final String POST_CONTENT = "post content";

    private static final String ANSWER_POST_CONTENT = "answer post content";

    private static final ZonedDateTime CURRENT_TIME = ZonedDateTime.now();

    private static TutorialGroup tutorialGroup;

    private static final Long TUTORIAL_GROUP_ID = 21L;

    private static final String TUTORIAL_GROUP_TITLE = "tutorial group title";

    private static User teachingAssistant;

    private static User instructor;

    private static User tutorialGroupStudent;

    private String expectedTitle;

    private String expectedText;

    private boolean expectedIsPlaceHolder = true;

    private String expectedPlaceholderValues;

    private NotificationTarget expectedTransientTarget;

    private NotificationPriority expectedPriority;

    private SingleUserNotification createdNotification;

    private NotificationType notificationType;

    private static User cheatingUser;

    private static final String USER_LOGIN = "de27sms";

    private static PlagiarismSubmission plagiarismSubmission;

    private static Set<PlagiarismSubmission<?>> plagiarismSubmissionSet;

    private static PlagiarismCase plagiarismCase;

    private static final String DATA_EXPORTS = "data-exports";

    private static DataExport dataExport;

    /**
     * sets up all needed mocks and their wanted behavior once for all test cases.
     */
    @BeforeAll
    static void setUp() {
        course = new Course();
        course.setId(COURSE_ID);
        course.setTitle(COURSE_TITLE);

        Lecture lecture = new Lecture();
        lecture.setId(LECTURE_ID);
        lecture.setCourse(course);
        lecture.setTitle(LECTURE_TITLE);

        exercise = new TextExercise();
        exercise.setId(EXERCISE_ID);
        exercise.setTitle(EXERCISE_TITLE);
        exercise.setCourse(course);
        exercise.setProblemStatement(PROBLEM_STATEMENT);

        cheatingUser = new User();
        cheatingUser.setLogin(USER_LOGIN);

        PlagiarismResult plagiarismResult = new TextPlagiarismResult();
        plagiarismResult.setExercise(exercise);

        plagiarismSubmission = new PlagiarismSubmission();
        plagiarismSubmission.setStudentLogin(USER_LOGIN);

        plagiarismSubmissionSet = new HashSet<>();
        plagiarismSubmissionSet.add(plagiarismSubmission);

        PlagiarismComparison plagiarismComparison = new PlagiarismComparison();
        plagiarismComparison.setPlagiarismResult(plagiarismResult);
        plagiarismComparison.setSubmissionA(plagiarismSubmission);

        plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(exercise);
        plagiarismCase.setStudent(cheatingUser);
        plagiarismCase.setPlagiarismSubmissions(plagiarismSubmissionSet);

        teachingAssistant = new User();
        teachingAssistant.setFirstName("John");
        teachingAssistant.setLastName("Doe");

        tutorialGroup = new TutorialGroup();
        tutorialGroup.setCourse(course);
        tutorialGroup.setId(TUTORIAL_GROUP_ID);
        tutorialGroup.setTitle(TUTORIAL_GROUP_TITLE);
        tutorialGroup.setTeachingAssistant(teachingAssistant);

        tutorialGroupStudent = new User();
        tutorialGroupStudent.setFirstName("Jane");
        tutorialGroupStudent.setLastName("Doe");

        instructor = new User();
        instructor.setFirstName("John");
        instructor.setLastName("Smith");

        Post post = new Post();
        post.setConversation(new Channel());
        post.setAuthor(instructor);
        post.setTitle(POST_TITLE);
        post.setContent(POST_CONTENT);
        post.setCreationDate(CURRENT_TIME);

        AnswerPost answerPost = new AnswerPost();
        answerPost.setPost(post);
        answerPost.setAuthor(instructor);
        answerPost.setContent(ANSWER_POST_CONTENT);
        answerPost.setCreationDate(CURRENT_TIME);
        dataExport = new DataExport();
        dataExport.setUser(tutorialGroupStudent);
    }

    /**
     * Calls the real createNotification method of the singleUserNotificationFactory and tests if the result is correct for Exercise notifications.
     */
    private void createAndCheckExerciseNotification() {
        createdNotification = createNotification(exercise, notificationType, null);
        checkNotification();
    }

    /**
     * Calls the real createNotification method of the singleUserNotificationFactory and tests if the result is correct for plagiarism related notifications.
     */
    private void createAndCheckPlagiarismNotification() {
        createdNotification = createNotification(plagiarismCase, notificationType, cheatingUser, null);
        checkNotification();
    }

    private void createAndCheckDataExportNotification() {
        createdNotification = createNotification(dataExport, notificationType, dataExport.getUser());
        checkNotification();
    }

    private void createAndCheckTutorialGroupNotification(User responsibleUser) {
        createdNotification = createNotification(tutorialGroup, notificationType, Set.of(tutorialGroupStudent), responsibleUser);
        checkNotification();
    }

    /**
     * Tests if the resulting notification is correct.
     */
    private void checkNotification() {
        assertThat(createdNotification.getTitle()).as("Created notification title should be equal to the expected one").isEqualTo(expectedTitle);
        assertThat(createdNotification.getText()).as("Created notification text should be equal to the expected one").isEqualTo(expectedText);
        assertThat(createdNotification.getTextIsPlaceholder()).as("Created notification placeholder flag should match expected one").isEqualTo(expectedIsPlaceHolder);
        assertThat(createdNotification.getPlaceholderValues()).as("Created notification placeholders should be equal to the expected ones").isEqualTo(expectedPlaceholderValues);
        assertThat(createdNotification.getTarget()).as("Created notification target should be equal to the expected one").isEqualTo(expectedTransientTarget.toJsonString());
        assertThat(createdNotification.getPriority()).as("Created notification priority should be equal to the expected one").isEqualTo(expectedPriority);
        assertThat(createdNotification.getAuthor()).as("Created notification author should be equal to the expected one").isNull();
    }

    /// Test for Notifications based on Exercises

    /**
     * Tests the functionality that deals with notifications that have the notification type of FILE_SUBMIT_SUCCESSFUL.
     * I.e. notifications that originate when a user successfully submitted a file upload exercise.
     */
    @Test
    void createNotification_withNotificationType_FileSubmitSuccessful() {
        notificationType = FILE_SUBMISSION_SUCCESSFUL;
        expectedTitle = FILE_SUBMISSION_SUCCESSFUL_TITLE;
        expectedText = FILE_SUBMISSION_SUCCESSFUL_TEXT;
        expectedPlaceholderValues = "[\"" + course.getTitle() + "\",\"" + exercise.getTitle() + "\"]";
        expectedPriority = MEDIUM;
        expectedTransientTarget = createExerciseTarget(exercise, FILE_SUBMISSION_SUCCESSFUL_TITLE);
        createAndCheckExerciseNotification();
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of EXERCISE_SUBMISSION_ASSESSED.
     * I.e. notifications that originate when a user's exercise submission has been assessed.
     */
    @Test
    void createNotification_withNotificationType_() {
        notificationType = EXERCISE_SUBMISSION_ASSESSED;
        expectedTitle = EXERCISE_SUBMISSION_ASSESSED_TITLE;
        expectedText = EXERCISE_SUBMISSION_ASSESSED_TEXT;
        expectedPlaceholderValues = "[\"" + course.getTitle() + "\",\"" + exercise.getExerciseType().getExerciseTypeAsReadableString() + "\",\"" + exercise.getTitle() + "\"]";
        expectedPriority = MEDIUM;
        expectedTransientTarget = createExerciseTarget(exercise, EXERCISE_SUBMISSION_ASSESSED_TITLE);
        createAndCheckExerciseNotification();
    }

    /// Test for Notifications based on Plagiarism

    /**
     * Tests the functionality that deals with notifications that have the notification type of NEW_PLAGIARISM_CASE_STUDENT.
     * I.e. notifications that originate when an instructor sets his statement concerning the plagiarism case for the student.
     */
    @Test
    void createNotification_withNotificationType_NewPlagiarismCaseStudent() {
        notificationType = NEW_PLAGIARISM_CASE_STUDENT;
        expectedTitle = NEW_PLAGIARISM_CASE_STUDENT_TITLE;
        expectedText = NEW_PLAGIARISM_CASE_STUDENT_TEXT;
        expectedPlaceholderValues = "[\"" + course.getTitle() + "\",\"" + plagiarismCase.getExercise().getExerciseType().toString().toLowerCase() + "\",\""
                + plagiarismCase.getExercise().getTitle() + "\"]";
        expectedPriority = HIGH;
        expectedTransientTarget = createPlagiarismCaseTarget(plagiarismCase.getId(), COURSE_ID);
        createAndCheckPlagiarismNotification();
    }

    /**
     * Tests the functionality that deals with notifications that have the notification type of PLAGIARISM_CASE_VERDICT_STUDENT.
     * I.e. notifications that originate when an instructor sets the verdict of a plagiarism case.
     */
    @Test
    void createNotification_withNotificationType_PlagiarismCaseVerdictStudent() {
        notificationType = PLAGIARISM_CASE_VERDICT_STUDENT;
        expectedTitle = PLAGIARISM_CASE_VERDICT_STUDENT_TITLE;
        expectedText = PLAGIARISM_CASE_VERDICT_STUDENT_TEXT;
        expectedPlaceholderValues = "[\"" + course.getTitle() + "\",\"" + plagiarismCase.getExercise().getExerciseType().toString().toLowerCase() + "\",\""
                + plagiarismCase.getExercise().getTitle() + "\"]";
        expectedPriority = HIGH;
        expectedTransientTarget = createPlagiarismCaseTarget(plagiarismCase.getId(), COURSE_ID);
        createAndCheckPlagiarismNotification();
    }

    /// Test for Notifications based on Tutorial Groups
    @ParameterizedTest
    @MethodSource("provideTutorialGroupTestParameters")
    void createNotification_withNotificationType_TutorialGroupNotifications(NotificationType notificationType, String expectedTitle, String expectedText,
            String expectedPlaceholderValues, User responsibleUser, Boolean isManagement, Boolean isDetailPage) {
        this.notificationType = notificationType;
        this.expectedTitle = expectedTitle;
        this.expectedText = expectedText;
        this.expectedPlaceholderValues = expectedPlaceholderValues;
        expectedPriority = MEDIUM;
        expectedTransientTarget = createTutorialGroupTarget(tutorialGroup, COURSE_ID, isManagement, isDetailPage);
        createAndCheckTutorialGroupNotification(responsibleUser);
    }

    @Test
    void createNotification_withNotificationType_DataExportCreated() {
        notificationType = DATA_EXPORT_CREATED;
        expectedTitle = DATA_EXPORT_CREATED_TITLE;
        expectedText = DATA_EXPORT_CREATED_TEXT;
        expectedPriority = MEDIUM;
        expectedIsPlaceHolder = true;
        expectedTransientTarget = createDataExportCreatedTarget(dataExport, DATA_EXPORTS);
        createAndCheckDataExportNotification();
    }

    @Test
    void createNotification_withNotificationType_DataExportFailed() {
        notificationType = DATA_EXPORT_FAILED;
        expectedTitle = DATA_EXPORT_FAILED_TITLE;
        expectedText = DATA_EXPORT_FAILED_TEXT;
        expectedIsPlaceHolder = true;
        expectedPriority = HIGH;
        expectedTransientTarget = createDataExportFailedTarget(DATA_EXPORTS);
        createAndCheckDataExportNotification();
    }

    private static Stream<Arguments> provideTutorialGroupTestParameters() {
        return Stream.of(
                Arguments.of(TUTORIAL_GROUP_REGISTRATION_STUDENT, TUTORIAL_GROUP_REGISTRATION_STUDENT_TITLE, TUTORIAL_GROUP_REGISTRATION_STUDENT_TEXT,
                        "[\"" + course.getTitle() + "\",\"" + tutorialGroup.getTitle() + "\",\"" + teachingAssistant.getName() + "\"]", teachingAssistant, false, true),
                Arguments.of(TUTORIAL_GROUP_DEREGISTRATION_STUDENT, TUTORIAL_GROUP_DEREGISTRATION_STUDENT_TITLE, TUTORIAL_GROUP_DEREGISTRATION_STUDENT_TEXT,
                        "[\"" + course.getTitle() + "\",\"" + tutorialGroup.getTitle() + "\",\"" + teachingAssistant.getName() + "\"]", teachingAssistant, false, true),
                Arguments.of(TUTORIAL_GROUP_REGISTRATION_TUTOR, TUTORIAL_GROUP_REGISTRATION_TUTOR_TITLE, TUTORIAL_GROUP_REGISTRATION_TUTOR_TEXT,
                        "[\"" + course.getTitle() + "\",\"" + tutorialGroupStudent.getName() + "\",\"" + tutorialGroup.getTitle() + "\",\"" + instructor.getName() + "\"]",
                        instructor, true, true),
                Arguments.of(TUTORIAL_GROUP_DEREGISTRATION_TUTOR, TUTORIAL_GROUP_DEREGISTRATION_TUTOR_TITLE, TUTORIAL_GROUP_DEREGISTRATION_TUTOR_TEXT,
                        "[\"" + course.getTitle() + "\",\"" + tutorialGroupStudent.getName() + "\",\"" + tutorialGroup.getTitle() + "\",\"" + instructor.getName() + "\"]",
                        instructor, true, true),
                Arguments.of(TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR, TUTORIAL_GROUP_REGISTRATION_MULTIPLE_TUTOR_TITLE, TUTORIAL_GROUP_REGISTRATION_MULTIPLE_TUTOR_TEXT,
                        "[\"" + course.getTitle() + "\",\"" + 1 + "\",\"" + tutorialGroup.getTitle() + "\",\"" + instructor.getName() + "\"]", instructor, true, true),
                Arguments.of(TUTORIAL_GROUP_ASSIGNED, TUTORIAL_GROUP_ASSIGNED_TITLE, TUTORIAL_GROUP_ASSIGNED_TEXT,
                        "[\"" + course.getTitle() + "\",\"" + tutorialGroup.getTitle() + "\",\"" + instructor.getName() + "\"]", instructor, true, true),
                Arguments.of(TUTORIAL_GROUP_UNASSIGNED, TUTORIAL_GROUP_UNASSIGNED_TITLE, TUTORIAL_GROUP_UNASSIGNED_TEXT,
                        "[\"" + course.getTitle() + "\",\"" + tutorialGroup.getTitle() + "\",\"" + instructor.getName() + "\"]", instructor, true, true));
    }

}
