package de.tum.cit.aet.artemis.communication.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.CourseNotification;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.UserCourseNotificationStatus;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.communication.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.UserCourseNotificationStatusTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

class GroupNotificationServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "groupnotificationservice";

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private GroupNotificationScheduleService groupNotificationScheduleService;

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private CourseNotificationTestRepository courseNotificationRepository;

    @Autowired
    private UserCourseNotificationStatusTestRepository userCourseNotificationStatusTestRepository;

    private Exercise exercise;

    private Exercise updatedExercise;

    private Exercise examExercise;

    private QuizExercise quizExercise;

    private ProgrammingExercise programmingExercise;

    private Lecture lecture;

    private static final String LECTURE_TITLE = "lecture title";

    private static final String POST_TITLE = "post title";

    private static final String POST_CONTENT = "post content";

    private static final String ANSWER_POST_CONTENT = "answer post content";

    private Course course;

    private static final String COURSE_TITLE = "course title";

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

        Exam exam = examUtilService.addExam(course);
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

        Post post = new Post();
        post.setConversation(channel);
        post.setAuthor(instructor);
        post.setTitle(POST_TITLE);
        post.setContent(POST_CONTENT);

        AnswerPost answerPost = new AnswerPost();
        answerPost.setPost(post);
        answerPost.setAuthor(instructor);
        answerPost.setContent(ANSWER_POST_CONTENT);

        // explicitly change the user to prevent issues in the following server call due to userRepository.getUser() (@WithMockUser is not working here)
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        featureToggleService.disableFeature(Feature.CourseSpecificNotifications);
    }

    /// Exercise Update / Release & Scheduling related Tests

    // NotifyAboutExerciseUpdate

    /**
     * Test for notifyAboutExerciseUpdate method with an undefined release date
     */
    @Test
    void testNotifyAboutExerciseUpdate_undefinedReleaseDate() {
        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise);
    }

    /**
     * Test for notifyAboutExerciseUpdate method with a future release date
     */
    @Test
    void testNotifyAboutExerciseUpdate_futureReleaseDate() {
        exercise.setReleaseDate(FUTURE_TIME);
        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService, never()).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise);
    }

    /**
     * Test for notifyAboutExerciseUpdate method with a correct release date (now) for exam exercises
     */
    @Test
    void testNotifyAboutExerciseUpdate_correctReleaseDate_examExercise() {
        examExercise.setReleaseDate(CURRENT_TIME);
        groupNotificationService.notifyAboutExerciseUpdate(examExercise, null);
        verify(groupNotificationService, never()).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any());
    }

    /**
     * Test for notifyAboutExerciseUpdate method with a correct release date (now) for course exercises
     */
    @Test
    void testNotifyAboutExerciseUpdate_correctReleaseDate_courseExercise() {
        exercise.setReleaseDate(CURRENT_TIME);
        groupNotificationService.notifyAboutExerciseUpdate(exercise, null);
        verify(groupNotificationService, never()).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any());
        groupNotificationService.notifyAboutExerciseUpdate(exercise, NOTIFICATION_TEXT);
        verify(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(any());
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

    @Test
    void shouldCreateAttachmentChangeNotificationWhenCourseSpecificNotificationsEnabled() {
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        lecture = new Lecture();
        lecture.setCourse(course);

        attachment.setReleaseDate(CURRENT_TIME);
        attachment.setLecture(lecture);

        groupNotificationService.notifyStudentGroupAboutAttachmentChange(attachment);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            boolean hasAttachmentChangeNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(course.getId()))
                    .anyMatch(notification -> notification.getType() == 10);

            assertThat(hasAttachmentChangeNotification).isTrue();
        });
    }

    @Test
    void shouldCreateExercisePracticeNotificationWhenCourseSpecificNotificationsEnabled() {
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        groupNotificationService.notifyStudentGroupAboutExercisePractice(exercise);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            boolean hasExercisePracticeNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(course.getId()))
                    .anyMatch(notification -> notification.getType() == 6);

            assertThat(hasExercisePracticeNotification).isTrue();
        });
    }

    @Test
    void shouldCreateQuizExerciseStartedNotificationWhenCourseSpecificNotificationsEnabled() {
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        groupNotificationService.notifyStudentGroupAboutQuizExerciseStart(quizExercise);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            boolean hasQuizStartedNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(course.getId()))
                    .anyMatch(notification -> notification.getType() == 9);

            assertThat(hasQuizStartedNotification).isTrue();
        });
    }

    @Test
    void shouldCreateExerciseUpdateNotificationWhenCourseSpecificNotificationsEnabled() {
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        groupNotificationService.notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            boolean hasExerciseUpdateNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(course.getId()))
                    .anyMatch(notification -> notification.getType() == 8);

            assertThat(hasExerciseUpdateNotification).isTrue();
        });
    }

    @Test
    void shouldCreateExerciseUpdateForEditorsAndInstructorsWhenCourseSpecificNotificationsEnabled() {
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        groupNotificationService.notifyEditorAndInstructorGroupAboutExerciseUpdate(exercise);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            boolean hasExerciseUpdateNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(course.getId()))
                    .anyMatch(notification -> notification.getType() == 8);

            assertThat(hasExerciseUpdateNotification).isTrue();
        });
    }

    @Test
    void shouldCreateExerciseReleasedNotificationWhenCourseSpecificNotificationsEnabled() {
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        groupNotificationService.notifyAllGroupsAboutReleasedExercise(exercise);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            boolean hasExerciseReleasedNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(course.getId()))
                    .anyMatch(notification -> notification.getType() == 5);

            assertThat(hasExerciseReleasedNotification).isTrue();
        });
    }

    @Test
    void shouldCreateNewFeedbackRequestNotificationWhenCourseSpecificNotificationsEnabled() {
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        groupNotificationService.notifyTutorGroupAboutNewFeedbackRequest(exercise);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            boolean hasNewFeedbackRequestNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(course.getId()))
                    .anyMatch(notification -> notification.getType() == 11);

            assertThat(hasNewFeedbackRequestNotification).isTrue();
        });
    }

    @Test
    void shouldCreateDuplicateTestCaseNotificationWhenCourseSpecificNotificationsEnabled() {
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        exercise.setReleaseDate(FUTURE_TIME);
        exercise.setDueDate(FUTURISTIC_TIME);
        exerciseRepository.save(exercise);

        groupNotificationService.notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise(exercise);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            boolean hasDuplicateTestCaseNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(course.getId()))
                    .anyMatch(notification -> notification.getType() == 12);

            assertThat(hasDuplicateTestCaseNotification).isTrue();
        });
    }

    @Test
    void shouldCreateProgrammingTestCasesChangedNotificationWhenCourseSpecificNotificationsEnabled() {
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        programmingExercise.setCourse(course);

        groupNotificationService.notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise(programmingExercise);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            boolean hasProgrammingTestCasesChangedNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(course.getId()))
                    .anyMatch(notification -> notification.getType() == 16);

            assertThat(hasProgrammingTestCasesChangedNotification).isTrue();

            Optional<CourseNotification> programmingTestCasesChangedNotification = notifications.stream().filter(notification -> notification.getType() == 16).findFirst();

            assertThat(programmingTestCasesChangedNotification).isPresent();

            List<UserCourseNotificationStatus> statuses = userCourseNotificationStatusTestRepository
                    .findAllByCourseNotificationId(programmingTestCasesChangedNotification.get().getId());

            List<Long> recipientIds = statuses.stream().map(status -> status.getUser().getId()).toList();

            assertThat(recipientIds).contains(instructor.getId());
            assertThat(recipientIds).doesNotContain(student.getId());
        });
    }

    @Test
    void shouldCreateProgrammingBuildRunUpdateNotificationWhenCourseSpecificNotificationsEnabled() {
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        programmingExercise.setCourse(course);
        String notificationText = "Build run status has been updated";

        groupNotificationService.notifyEditorAndInstructorGroupsAboutBuildRunUpdate(programmingExercise);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            boolean hasProgrammingBuildRunUpdateNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(course.getId()))
                    .anyMatch(notification -> notification.getType() == 15);

            assertThat(hasProgrammingBuildRunUpdateNotification).isTrue();

            Optional<CourseNotification> programmingBuildRunUpdateNotification = notifications.stream().filter(notification -> notification.getType() == 15).findFirst();

            assertThat(programmingBuildRunUpdateNotification).isPresent();

            List<UserCourseNotificationStatus> statuses = userCourseNotificationStatusTestRepository
                    .findAllByCourseNotificationId(programmingBuildRunUpdateNotification.get().getId());

            List<Long> recipientIds = statuses.stream().map(status -> status.getUser().getId()).toList();

            assertThat(recipientIds).contains(instructor.getId());
            assertThat(recipientIds).doesNotContain(student.getId());
        });
    }
}
