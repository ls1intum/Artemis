package de.tum.cit.aet.artemis.communication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.domain.SavedPostStatus;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.ChannelSubType;
import de.tum.cit.aet.artemis.communication.dto.AnswerPostResponseDTO;
import de.tum.cit.aet.artemis.communication.dto.ChannelDTO;
import de.tum.cit.aet.artemis.communication.dto.CommunicationCrudAction;
import de.tum.cit.aet.artemis.communication.dto.ConversationUserDTO;
import de.tum.cit.aet.artemis.communication.dto.ConversationWebsocketDTO;
import de.tum.cit.aet.artemis.communication.dto.CreatePostConversationDTO;
import de.tum.cit.aet.artemis.communication.dto.CreatePostDTO;
import de.tum.cit.aet.artemis.communication.dto.PostResponseDTO;
import de.tum.cit.aet.artemis.communication.dto.PostingDTO;
import de.tum.cit.aet.artemis.communication.dto.UpdatePostingDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.communication.test_repository.SavedPostTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.communication.web.CommunicationWebsocketTopics;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class ChannelVisibilityIntegrationTest extends AbstractConversationTest {

    private static final String TEST_PREFIX = "channelvisibility";

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private AnswerPostRepository answerPostRepository;

    @Autowired
    private SavedPostTestRepository savedPostRepository;

    @BeforeEach
    @Override
    void setupTestScenario() throws Exception {
        super.setupTestScenario();
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
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

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentCannotGetUnreleasedExerciseChannelByExerciseId() throws Exception {
        Channel channel = createChannel(ChannelSubType.EXERCISE, ZonedDateTime.now().plusDays(1));

        request.get(channelUrl(channel), HttpStatus.FORBIDDEN, ChannelDTO.class);
    }

    @ParameterizedTest
    @CsvSource({ "EXERCISE, false", "EXERCISE, true", "EXAM, false", "EXAM, true" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentCannotReadHiddenChannelMessagesEvenWithMembership(ChannelSubType type, boolean existingMember) throws Exception {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(1));
        if (existingMember) {
            conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");
        }
        var params = new LinkedMultiValueMap<String, String>();
        params.add("conversationIds", channel.getId().toString());

        request.getList(messagesUrl(), HttpStatus.FORBIDDEN, PostResponseDTO.class, params);

        assertThat(conversationParticipantRepository.existsByConversationIdAndUserId(channel.getId(), userRepository.getUser().getId())).isEqualTo(existingMember);
    }

    @ParameterizedTest
    @CsvSource({ "EXERCISE, false", "EXERCISE, true", "EXAM, false", "EXAM, true" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentCannotPostToHiddenChannelEvenWithMembership(ChannelSubType type, boolean existingMember) throws Exception {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(1));
        if (existingMember) {
            conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");
        }
        var post = new CreatePostDTO("Hidden channel", "", false, new CreatePostConversationDTO(channel.getId()));

        request.postWithResponseBody(messagesUrl(), post, PostResponseDTO.class, HttpStatus.FORBIDDEN);

        assertThat(conversationParticipantRepository.existsByConversationIdAndUserId(channel.getId(), userRepository.getUser().getId())).isEqualTo(existingMember);
    }

    @ParameterizedTest
    @EnumSource(value = ChannelSubType.class, names = { "EXERCISE", "EXAM" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentCannotJoinHiddenPublicChannel(ChannelSubType type) throws Exception {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(1));

        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/register", List.of(TEST_PREFIX + "student1"),
                HttpStatus.FORBIDDEN, null);

        assertThat(conversationParticipantRepository.existsByConversationIdAndUserId(channel.getId(), userRepository.getUser().getId())).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ChannelSubType.class, names = { "EXERCISE", "EXAM" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentCannotSearchMembersOfHiddenChannel(ChannelSubType type) throws Exception {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(1));

        request.getList("/api/communication/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search?loginOrName=&size=20", HttpStatus.FORBIDDEN,
                ConversationUserDTO.class);
    }

    @ParameterizedTest
    @EnumSource(value = ChannelSubType.class, names = { "EXERCISE", "EXAM" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void hiddenChannelUpdatesOnlyReachStaff(ChannelSubType type) {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(1));
        var student = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1").getUser();
        var tutor = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "tutor1").getUser();

        conversationService.notifyAllConversationMembersAboutUpdate(channel);

        verify(websocketMessagingService, never()).sendMessageToUser(eq(student.getLogin()),
                eq(CommunicationWebsocketTopics.CONVERSATION_MEMBERSHIP.at(exampleCourseId, student.getId())),
                argThat(payload -> payload instanceof ConversationWebsocketDTO update && update.action() != CommunicationCrudAction.DELETE));
        verify(websocketMessagingService).sendMessageToUser(eq(tutor.getLogin()), eq(CommunicationWebsocketTopics.CONVERSATION_MEMBERSHIP.at(exampleCourseId, tutor.getId())),
                any(ConversationWebsocketDTO.class));
    }

    @ParameterizedTest
    @EnumSource(value = ChannelSubType.class, names = { "EXERCISE", "EXAM" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentCannotReadHiddenThreadByUpdatingOwnPost(ChannelSubType type) throws Exception {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(1));
        Post post = createExistingPost(channel, "student1");
        createExistingReply(post, "instructor1");

        request.putWithResponseBody(messagesUrl() + "/" + post.getId(), new UpdatePostingDTO(post.getId(), "Edited question", "Question", false), PostResponseDTO.class,
                HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @EnumSource(value = ChannelSubType.class, names = { "EXERCISE", "EXAM" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentCannotReadHiddenReplyMetadataByUpdatingOwnReply(ChannelSubType type) throws Exception {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(1));
        AnswerPost reply = createExistingReply(createExistingPost(channel, "instructor1"), "student1");

        request.putWithResponseBody("/api/communication/courses/" + exampleCourseId + "/answer-messages/" + reply.getId(),
                new UpdatePostingDTO(reply.getId(), "Edited reply", null, false), AnswerPostResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @CsvSource({ "EXERCISE, POST, false", "EXERCISE, POST, true", "EXERCISE, ANSWER, false", "EXERCISE, ANSWER, true", "EXAM, POST, false", "EXAM, POST, true",
            "EXAM, ANSWER, false", "EXAM, ANSWER, true" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentCannotDeleteHiddenChannelMessages(ChannelSubType type, PostingType postingType, boolean moderator) throws Exception {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(1));
        var participant = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");
        participant.setIsModerator(moderator);
        conversationParticipantRepository.save(participant);
        Post post = createExistingPost(channel, moderator ? "instructor1" : "student1");
        AnswerPost reply = createExistingReply(post, moderator ? "instructor1" : "student1");

        String url = postingType == PostingType.POST ? messagesUrl() + "/" + post.getId() : "/api/communication/courses/" + exampleCourseId + "/answer-messages/" + reply.getId();
        request.delete(url, HttpStatus.FORBIDDEN);

        assertThat(conversationMessageRepository.existsById(post.getId())).isTrue();
        assertThat(answerPostRepository.existsById(reply.getId())).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "EXERCISE, favorite, isFavorite", "EXERCISE, hidden, isHidden", "EXERCISE, muted, isMuted", "EXAM, favorite, isFavorite", "EXAM, hidden, isHidden",
            "EXAM, muted, isMuted" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentPreferencesCannotCreateHiddenChannelMembership(ChannelSubType type, String preference, String parameter) throws Exception {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(1));

        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/" + preference + "?" + parameter + "=true",
                HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());

        assertThat(conversationParticipantRepository.existsByConversationIdAndUserId(channel.getId(), userRepository.getUser().getId())).isFalse();
    }

    @ParameterizedTest
    @CsvSource({ "EXERCISE, false", "EXERCISE, true", "EXAM, false", "EXAM, true" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void unreadMessagesIgnoreHiddenChannels(ChannelSubType type, boolean existingMember) throws Exception {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(1));
        if (existingMember) {
            var participant = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");
            participant.setLastRead(ZonedDateTime.now());
            participant.setUnreadMessagesCount(1L);
            conversationParticipantRepository.save(participant);
        }
        createExistingPost(channel, "instructor1");

        assertThat(request.get("/api/communication/courses/" + exampleCourseId + "/unread-messages", HttpStatus.OK, Boolean.class)).isFalse();

        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        assertThat(request.get("/api/communication/courses/" + exampleCourseId + "/unread-messages", HttpStatus.OK, Boolean.class)).isTrue();

        userUtilService.changeUser(TEST_PREFIX + "student1");
        createExistingPost(createChannel(type, ZonedDateTime.now().minusHours(1)), "instructor1");
        assertThat(request.get("/api/communication/courses/" + exampleCourseId + "/unread-messages", HttpStatus.OK, Boolean.class)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ChannelSubType.class, names = { "EXERCISE", "EXAM" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void markingAllChannelsReadDoesNotJoinHiddenChannels(ChannelSubType type) throws Exception {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(1));

        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/channels/mark-as-read", HttpStatus.OK, new LinkedMultiValueMap<>());

        assertThat(conversationParticipantRepository.existsByConversationIdAndUserId(channel.getId(), userRepository.getUser().getId())).isFalse();
    }

    @ParameterizedTest
    @CsvSource({ "EXERCISE, delete", "EXERCISE, archive", "EXERCISE, unarchive", "EXERCISE, register", "EXERCISE, deregister", "EXERCISE, grant-channel-moderator",
            "EXERCISE, revoke-channel-moderator", "EXAM, delete", "EXAM, archive", "EXAM, unarchive", "EXAM, register", "EXAM, deregister", "EXAM, grant-channel-moderator",
            "EXAM, revoke-channel-moderator" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentModeratorsCannotManageHiddenChannels(ChannelSubType type, String action) throws Exception {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(1));
        var moderator = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");
        moderator.setIsModerator(true);
        conversationParticipantRepository.save(moderator);
        if (!action.equals("register")) {
            var target = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student2");
            target.setIsModerator(action.equals("revoke-channel-moderator"));
            conversationParticipantRepository.save(target);
        }
        if (action.equals("unarchive")) {
            channel.setIsArchived(true);
            channelRepository.save(channel);
        }
        if (action.equals("delete")) {
            // A former staff member can retain creator/moderator status after becoming a course student.
            channel.setCreator(moderator.getUser());
            channelRepository.save(channel);
        }

        String channelUrl = "/api/communication/courses/" + exampleCourseId + "/channels/" + channel.getId();
        if (action.equals("delete")) {
            request.delete(channelUrl, HttpStatus.FORBIDDEN);
        }
        else {
            request.postWithoutResponseBody(channelUrl + "/" + action, List.of(TEST_PREFIX + "student2"), HttpStatus.FORBIDDEN);
        }

        assertThat(channelRepository.findByIdElseThrow(channel.getId()).getIsArchived()).isEqualTo(action.equals("unarchive"));
        var target = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channel.getId(),
                userUtilService.getUserByLogin(TEST_PREFIX + "student2").getId());
        if (action.equals("register")) {
            assertThat(target).isEmpty();
        }
        else {
            assertThat(target).get().extracting(participant -> participant.getIsModerator()).isEqualTo(action.equals("revoke-channel-moderator"));
        }
    }

    @ParameterizedTest
    @CsvSource({ "EXERCISE, POST", "EXERCISE, ANSWER", "EXAM, POST", "EXAM, ANSWER" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void savedPostsDoNotRevealHiddenChannelContent(ChannelSubType type, PostingType postingType) throws Exception {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(1));
        Post post = createExistingPost(channel, "instructor1");
        long postingId = postingType == PostingType.POST ? post.getId() : createExistingReply(post, "instructor1").getId();
        savedPostRepository.save(new SavedPost(userRepository.getUser(), postingId, postingType, SavedPostStatus.IN_PROGRESS, null));

        var savedPosts = request.getList("/api/communication/saved-posts?courseId=" + exampleCourseId + "&status=in_progress", HttpStatus.OK, PostingDTO.class);

        assertThat(savedPosts).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = ChannelSubType.class, names = { "EXERCISE", "EXAM" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void studentModeratorCannotReadHiddenChannelByUpdatingIt(ChannelSubType type) throws Exception {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(1));
        var participant = conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");
        participant.setIsModerator(true);
        conversationParticipantRepository.save(participant);
        var update = new ChannelDTO();
        update.setTopic("Updated topic");

        request.putWithResponseBody("/api/communication/courses/" + exampleCourseId + "/channels/" + channel.getId(), update, ChannelDTO.class, HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @CsvSource({ "EXERCISE, student1, -1", "EXAM, student1, -1", "EXERCISE, tutor1, 1", "EXAM, tutor1, 1", "EXERCISE, instructor1, 1", "EXAM, instructor1, 1" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void visibleChannelsAndStaffAccessRemainAvailable(ChannelSubType type, String login, int visibleInDays) throws Exception {
        Channel channel = createChannel(type, ZonedDateTime.now().plusDays(visibleInDays));
        userUtilService.changeUser(TEST_PREFIX + login);
        var params = new LinkedMultiValueMap<String, String>();
        params.add("conversationIds", channel.getId().toString());

        request.getList(messagesUrl(), HttpStatus.OK, PostResponseDTO.class, params);
        var post = new CreatePostDTO("Accessible channel", "", false, new CreatePostConversationDTO(channel.getId()));
        var response = request.postWithResponseBody(messagesUrl(), post, PostResponseDTO.class, HttpStatus.CREATED);
        assertThat(response.conversation().id()).isEqualTo(channel.getId());
    }

    private Channel createChannel(ChannelSubType type, ZonedDateTime visibleDate) {
        return switch (type) {
            case EXERCISE -> conversationUtilService
                    .addChannelToExercise(textExerciseUtilService.createIndividualTextExercise(exampleCourse, visibleDate, visibleDate.plusDays(2), visibleDate.plusDays(3)));
            case EXAM -> examUtilService.addExamChannel(examUtilService.addExam(exampleCourse, visibleDate, visibleDate.plusDays(2), visibleDate.plusDays(3)), "exam-channel");
            default -> throw new IllegalArgumentException("Unsupported channel type: " + type);
        };
    }

    private Post createExistingPost(Channel channel, String author) {
        var post = new Post();
        post.setConversation(channel);
        post.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + author));
        post.setContent("Existing question in a channel whose visibility was postponed");
        post.setVisibleForStudents(true);
        post.setCreationDate(ZonedDateTime.now().minusDays(1));
        return conversationMessageRepository.save(post);
    }

    private AnswerPost createExistingReply(Post post, String author) {
        var reply = new AnswerPost();
        reply.setPost(post);
        reply.setAuthor(userUtilService.getUserByLogin(TEST_PREFIX + author));
        reply.setContent("Reply written while the channel is hidden");
        reply.setCreationDate(ZonedDateTime.now());
        return answerPostRepository.save(reply);
    }

    private String messagesUrl() {
        return "/api/communication/courses/" + exampleCourseId + "/messages";
    }

    private String channelUrl(Channel channel) {
        return "/api/communication/courses/" + exampleCourseId + "/exercises/" + channel.getExercise().getId() + "/channel";
    }
}
