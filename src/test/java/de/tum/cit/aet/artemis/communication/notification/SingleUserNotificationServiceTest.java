package de.tum.cit.aet.artemis.communication.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.CourseNotification;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.communication.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.communication.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.communication.test_repository.UserCourseNotificationStatusTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DataExport;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.plagiarism.domain.text.TextPlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.domain.text.TextSubmissionElement;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;

class SingleUserNotificationServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "singleusernotification";

    @Autowired
    private SingleUserNotificationService singleUserNotificationService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private CourseNotificationTestRepository courseNotificationRepository;

    @Autowired
    private UserCourseNotificationStatusTestRepository userCourseNotificationStatusTestRepository;

    private User user;

    private Post post;

    private static final String POST_TITLE = "post title";

    private static final String POST_CONTENT = "post content";

    private static final String ANSWER_POST_CONTENT = "answer post content";

    private Course course;

    private static final String COURSE_TITLE = "course title";

    private static final String LECTURE_TITLE = "lecture title";

    private Exercise exercise;

    private PlagiarismCase plagiarismCase;

    /**
     * Sets up all needed mocks and their wanted behavior
     */
    @BeforeEach
    void setUp() {
        SecurityUtils.setAuthorizationObject();

        course = courseUtilService.createCourse();
        course.setTitle(COURSE_TITLE);

        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 0);
        user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        User userTwo = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        User userThree = userUtilService.getUserByLogin(TEST_PREFIX + "student3");

        exercise = new TextExercise();
        exercise.setCourse(course);
        exercise.setMaxPoints(10D);

        FileUploadExercise fileUploadExercise = new FileUploadExercise();
        fileUploadExercise.setCourse(course);

        Lecture lecture = new Lecture();
        lecture.setCourse(course);
        lecture.setTitle(LECTURE_TITLE);

        Channel channel = new Channel();
        channel.setCourse(course);
        channel.setName("test");
        channel.setCreator(userTwo);
        channel.setCreationDate(ZonedDateTime.now());

        post = new Post();
        post.setId(1L);
        post.setAuthor(userTwo);
        post.setConversation(channel);
        post.setTitle(POST_TITLE);
        post.setContent(POST_CONTENT);

        Post answerPostPost = new Post();
        answerPostPost.setConversation(channel);
        answerPostPost.setAuthor(userTwo);
        answerPostPost.setId(2L);
        AnswerPost answerPost = new AnswerPost();
        answerPost.setId(1L);
        answerPost.setPost(answerPostPost);
        answerPost.setAuthor(userThree);
        answerPost.setContent(ANSWER_POST_CONTENT);

        PlagiarismSubmission<TextSubmissionElement> plagiarismSubmission = new PlagiarismSubmission<>();
        plagiarismSubmission.setStudentLogin(user.getLogin());

        TextPlagiarismResult plagiarismResult = new TextPlagiarismResult();
        plagiarismResult.setExercise(exercise);

        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        plagiarismComparison.setSubmissionA(plagiarismSubmission);
        plagiarismComparison.setPlagiarismResult(plagiarismResult);

        plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(exercise);

        Result result = new Result();
        result.setScore(1D);
        result.setCompletionDate(ZonedDateTime.now().minusMinutes(1));

        TutorialGroup tutorialGroup = new TutorialGroup();
        tutorialGroup.setCourse(course);
        tutorialGroup.setTeachingAssistant(userTwo);

        OneToOneChat oneToOneChat = new OneToOneChat();
        oneToOneChat.setCourse(course);
        oneToOneChat.setCreator(userTwo);
        oneToOneChat.setCreationDate(ZonedDateTime.now());
        ConversationParticipant conversationParticipant1 = new ConversationParticipant();
        conversationParticipant1.setUser(user);
        ConversationParticipant conversationParticipant2 = new ConversationParticipant();
        conversationParticipant2.setUser(userTwo);
        oneToOneChat.setConversationParticipants(Set.of(conversationParticipant1, conversationParticipant2));

        GroupChat groupChat = new GroupChat();
        groupChat.setCourse(course);
        groupChat.setCreator(userTwo);
        groupChat.setCreationDate(ZonedDateTime.now());
        ConversationParticipant conversationParticipant3 = new ConversationParticipant();
        conversationParticipant3.setUser(userThree);
        groupChat.setConversationParticipants(Set.of(conversationParticipant1, conversationParticipant2, conversationParticipant3));

        channel.setConversationParticipants(Set.of(conversationParticipant1, conversationParticipant2, conversationParticipant3));

        DataExport dataExport = new DataExport();
        dataExport.setUser(user);

        featureToggleService.disableFeature(Feature.CourseSpecificNotifications);
    }

    @Test
    void shouldCreateNewCpcPlagiarismCaseNotificationWhenCourseSpecificNotificationsEnabled() {
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        exercise.setTitle("CPC Plagiarism Test Exercise");
        plagiarismCase.setExercise(exercise);
        plagiarismCase.setPost(post);

        singleUserNotificationService.notifyUserAboutNewContinuousPlagiarismControlPlagiarismCase(plagiarismCase, user);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            var hasNewCpcPlagiarismCaseNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(course.getId()))
                    .anyMatch(notification -> notification.getType() == 13);

            assertThat(hasNewCpcPlagiarismCaseNotification).isTrue();

            Optional<CourseNotification> newCpcPlagiarismCaseNotification = notifications.stream().filter(notification -> notification.getType() == 13).findFirst();

            assertThat(newCpcPlagiarismCaseNotification).isPresent();
            assertThat(userCourseNotificationStatusTestRepository.wasNotificationSentOnlyToUser(newCpcPlagiarismCaseNotification.get().getId(), user.getId())).isTrue();
        });

        featureToggleService.disableFeature(Feature.CourseSpecificNotifications);
    }

    @Test
    void shouldCreateNewPlagiarismCaseNotificationWhenCourseSpecificNotificationsEnabled() {
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        exercise.setTitle("Plagiarism Test Exercise");
        plagiarismCase.setPost(post);
        plagiarismCase.setExercise(exercise);

        singleUserNotificationService.notifyUserAboutNewPlagiarismCase(plagiarismCase, user);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            var hasNewPlagiarismCaseNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(course.getId()))
                    .anyMatch(notification -> notification.getType() == 14);

            assertThat(hasNewPlagiarismCaseNotification).isTrue();

            // Verify the notification was sent only to the correct user
            Optional<CourseNotification> newPlagiarismCaseNotification = notifications.stream().filter(notification -> notification.getType() == 14).findFirst();

            assertThat(newPlagiarismCaseNotification).isPresent();
            assertThat(userCourseNotificationStatusTestRepository.wasNotificationSentOnlyToUser(newPlagiarismCaseNotification.get().getId(), user.getId())).isTrue();
        });

        featureToggleService.disableFeature(Feature.CourseSpecificNotifications);
    }

    @Test
    void shouldCreatePlagiarismCaseVerdictNotificationWhenCourseSpecificNotificationsEnabled() {
        featureToggleService.enableFeature(Feature.CourseSpecificNotifications);

        exercise.setTitle("Plagiarism Test Exercise");
        plagiarismCase.setVerdict(PlagiarismVerdict.NO_PLAGIARISM);
        plagiarismCase.setPost(post);
        plagiarismCase.setExercise(exercise);

        singleUserNotificationService.notifyUserAboutPlagiarismCaseVerdict(plagiarismCase, user);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            var hasNewPlagiarismVerdictNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(course.getId()))
                    .anyMatch(notification -> notification.getType() == 17);

            assertThat(hasNewPlagiarismVerdictNotification).isTrue();

            var newPlagiarismVerdictNotification = notifications.stream().filter(notification -> notification.getType() == 17).findFirst();

            assertThat(newPlagiarismVerdictNotification).isPresent();
            assertThat(userCourseNotificationStatusTestRepository.wasNotificationSentOnlyToUser(newPlagiarismVerdictNotification.get().getId(), user.getId())).isTrue();
        });

        featureToggleService.disableFeature(Feature.CourseSpecificNotifications);
    }
}
