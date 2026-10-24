package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.ChannelSubType;
import de.tum.cit.aet.artemis.communication.dto.AnswerPostResponseDTO;
import de.tum.cit.aet.artemis.communication.dto.ChannelDTO;
import de.tum.cit.aet.artemis.communication.dto.CommunicationCrudAction;
import de.tum.cit.aet.artemis.communication.dto.ConversationWebsocketDTO;
import de.tum.cit.aet.artemis.communication.dto.CreateAnswerPostDTO;
import de.tum.cit.aet.artemis.communication.dto.CreatePostConversationDTO;
import de.tum.cit.aet.artemis.communication.dto.CreatePostDTO;
import de.tum.cit.aet.artemis.communication.dto.ParentPostDTO;
import de.tum.cit.aet.artemis.communication.dto.PostResponseDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.communication.web.CommunicationWebsocketTopics;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.notification.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ChannelNotificationVisibilityIntegrationTest extends AbstractConversationTest {

    private static final String TEST_PREFIX = "channelnotificationvisibility";

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private CourseNotificationTestRepository courseNotificationRepository;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @BeforeEach
    @Override
    void setupTestScenario() throws Exception {
        super.setupTestScenario();
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        userUtilService.enrollPrefixedUsersInCourse(exampleCourse, TEST_PREFIX);
    }

    @AfterEach
    void tearDown() {
        conversationRepository.findAllByCourseId(exampleCourseId).forEach(conversation -> conversationService.deleteConversation(conversation.getId()));
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @ParameterizedTest
    @CsvSource({ "EXERCISE, register, 19", "EXAM, register, 19", "EXERCISE, deregister, 20", "EXAM, deregister, 20", "EXERCISE, delete, 18", "EXAM, delete, 18" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void hiddenChannelMembershipNotificationsOnlyReachStaff(ChannelSubType type, String action, short notificationType) throws Exception {
        Channel channel = createHiddenChannel(type);
        List<String> recipients = List.of(TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");
        if (!action.equals("register")) {
            recipients.forEach(login -> conversationUtilService.addParticipantToConversation(channel, login));
        }
        String channelUrl = "/api/communication/courses/" + exampleCourseId + "/channels/" + channel.getId();

        if (action.equals("delete")) {
            request.delete(channelUrl, HttpStatus.OK);
        }
        else {
            request.postWithoutResponseBody(channelUrl + "/" + action, recipients, HttpStatus.OK);
        }

        assertOnlyStaffReceived(notificationType);
    }

    @ParameterizedTest
    @EnumSource(value = ChannelSubType.class, names = { "EXERCISE", "EXAM" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void hiddenChannelAnnouncementNotificationsOnlyReachStaff(ChannelSubType type) throws Exception {
        Channel channel = createHiddenChannel(type);
        channel.setIsAnnouncementChannel(true);
        channelRepository.save(channel);
        addMessageParticipants(channel);

        postMessage(channel, "Announcement about a hidden item");

        assertOnlyStaffReceived((short) 4);
    }

    @ParameterizedTest
    @EnumSource(value = ChannelSubType.class, names = { "EXERCISE", "EXAM" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void mentionsInHiddenChannelsOnlyNotifyStaffEvenIfStudentIsMember(ChannelSubType type) throws Exception {
        Channel channel = createHiddenChannel(type);
        addMessageParticipants(channel);
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");

        postMessage(channel, "[user]" + student.getName() + "(" + student.getLogin() + ")[/user] [user]" + tutor.getName() + "(" + tutor.getLogin() + ")[/user]");

        assertOnlyStaffReceived((short) 3);
    }

    @ParameterizedTest
    @EnumSource(value = ChannelSubType.class, names = { "EXERCISE", "EXAM" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void repliesInHiddenChannelsOnlyNotifyStaffParticipants(ChannelSubType type) throws Exception {
        Channel channel = createHiddenChannel(type);
        addMessageParticipants(channel);
        // Seed a discussion that existed before the item's visibility date was postponed.
        var post = new Post();
        post.setConversation(channel);
        post.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        post.setContent("Existing student question");
        post.setVisibleForStudents(true);
        post.setCreationDate(ZonedDateTime.now().minusDays(1));
        post = conversationMessageRepository.save(post);
        var previousReply = new AnswerPost();
        previousReply.setPost(post);
        previousReply.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"));
        previousReply.setContent("Existing tutor reply");
        previousReply.setCreationDate(ZonedDateTime.now().minusHours(1));
        answerPostRepository.save(previousReply);

        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/answer-messages",
                new CreateAnswerPostDTO("Staff discussion while the channel is hidden", new ParentPostDTO(post.getId())), AnswerPostResponseDTO.class, HttpStatus.CREATED);

        assertOnlyStaffReceived((short) 2);
    }

    @ParameterizedTest
    @EnumSource(value = ChannelSubType.class, names = { "EXERCISE", "EXAM" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void hiddenChannelUpdatesRemoveStudentCacheWithoutRevealingDetails(ChannelSubType type) {
        Channel channel = createHiddenChannel(type);
        var student = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1").getUser();

        conversationService.notifyAllConversationMembersAboutUpdate(channel);

        var payload = ArgumentCaptor.forClass(ConversationWebsocketDTO.class);
        verify(websocketMessagingService).sendMessageToUser(eq(student.getLogin()), eq(CommunicationWebsocketTopics.CONVERSATION_MEMBERSHIP.at(exampleCourseId, student.getId())),
                payload.capture());
        assertThat(payload.getValue().action()).isEqualTo(CommunicationCrudAction.DELETE);
        assertThat(payload.getValue().conversation()).isInstanceOfSatisfying(ChannelDTO.class, dto -> {
            assertThat(dto.getId()).isEqualTo(channel.getId());
            assertThat(dto.getName()).isNull();
            assertThat(dto.getDescription()).isNull();
            assertThat(dto.getTopic()).isNull();
            assertThat(dto.getSubTypeReferenceId()).isNull();
            assertThat(dto.getCreator()).isNull();
            assertThat(dto.getCreationDate()).isNull();
        });
    }

    private void addMessageParticipants(Channel channel) {
        List.of("student1", "tutor1", "instructor1").forEach(login -> conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + login));
    }

    private void postMessage(Channel channel, String content) throws Exception {
        var post = new CreatePostDTO(content, "Hidden item", false, new CreatePostConversationDTO(channel.getId()));
        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/messages", post, PostResponseDTO.class, HttpStatus.CREATED);
    }

    private void assertOnlyStaffReceived(short notificationType) {
        var tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> assertThat(courseNotificationRepository.findCourseNotificationsByUserIdAndCourseIdAndStatusNotArchived(tutor.getId(), exampleCourseId, Pageable.unpaged()))
                        .anySatisfy(notification -> {
                            assertThat(notification.notificationType()).isEqualTo(notificationType);
                        }));
        // Status rows for all recipients are committed together, so the tutor's row also proves that recipient delivery finished.
        assertThat(courseNotificationRepository.findCourseNotificationsByUserIdAndCourseIdAndStatusNotArchived(student.getId(), exampleCourseId, Pageable.unpaged()))
                .noneSatisfy(notification -> assertThat(notification.notificationType()).isEqualTo(notificationType));
    }

    private Channel createHiddenChannel(ChannelSubType type) {
        ZonedDateTime visibleDate = ZonedDateTime.now().plusDays(1);
        return switch (type) {
            case EXERCISE -> conversationUtilService
                    .addChannelToExercise(textExerciseUtilService.createIndividualTextExercise(exampleCourse, visibleDate, visibleDate.plusDays(2), visibleDate.plusDays(3)));
            case EXAM -> examUtilService.addExamChannel(examUtilService.addExam(exampleCourse, visibleDate, visibleDate.plusDays(2), visibleDate.plusDays(3)), "exam-channel");
            default -> throw new IllegalArgumentException("Unsupported channel type: " + type);
        };
    }
}
