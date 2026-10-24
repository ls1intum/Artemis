package de.tum.cit.aet.artemis.communication;

import static de.tum.cit.aet.artemis.core.util.WebsocketDestinationMatchers.topicMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserFactory;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.communication.dto.CommunicationCrudAction;
import de.tum.cit.aet.artemis.communication.dto.OneToOneChatCreationDTO;
import de.tum.cit.aet.artemis.communication.dto.OneToOneChatDTO;
import de.tum.cit.aet.artemis.communication.dto.PostBroadcastDTO;
import de.tum.cit.aet.artemis.communication.service.conversation.OneToOneChatService;
import de.tum.cit.aet.artemis.communication.test_repository.OneToOneChatTestRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketDestination;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;

class OneToOneChatIntegrationTest extends AbstractConversationTest {

    private static final String TEST_PREFIX = "ootest";

    private static final String OTHER_PREFIX = TEST_PREFIX + "other";

    @Autowired
    private OneToOneChatTestRepository oneToOneChatRepository;

    @Autowired
    private OneToOneChatService oneToOneChatService;

    @BeforeEach
    @Override
    void setupTestScenario() throws Exception {
        super.setupTestScenario();
        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 0);
        if (userRepository.findOneByLogin(OTHER_PREFIX + "student42").isEmpty()) {
            userRepository.save(UserFactory.generateActivatedUser(OTHER_PREFIX + "student42"));
        }
        userUtilService.enrollPrefixedUsersInCourse(exampleCourse, TEST_PREFIX);
    }

    @AfterEach
    void tearDown() {
        // Do not use conversationMessageRepository.deleteAll() here:
        // In Hibernate 6.6, loading all Post entities and removing them directly causes
        // TransientObjectException during flush, because the parent Conversation entities
        // remain managed in the session with stale lazy 'posts' collection references.
        // Instead, rely on cascade = CascadeType.REMOVE from Conversation -> Post.
        conversationRepository.deleteAllByCourseId(exampleCourseId);
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCreateMultipleOneToOneChatsWhenDifferentLoginsAreProvided() throws Exception {
        // when
        var chat1 = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", new OneToOneChatCreationDTO(null, testPrefix + "student2"),
                OneToOneChatDTO.class, HttpStatus.CREATED);

        var chat2 = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student3"), OneToOneChatDTO.class,
                HttpStatus.CREATED);
        // then
        assertThat(chat1).isNotNull();
        assertParticipants(chat1.getId(), 2, "student1", "student2");
        assertThat(chat2).isNotNull();
        assertParticipants(chat2.getId(), 2, "student1", "student3");
        // members of the created one to one chat are only notified in case the first message within the conversation is created
        verifyNoParticipantTopicWebsocketSent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldUseExistingOneToOneChatWhenChatAlreadyExists() throws Exception {
        var chat = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class,
                HttpStatus.CREATED);

        userUtilService.changeUser(TEST_PREFIX + "student2");
        var sameChat = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student1"), OneToOneChatDTO.class,
                HttpStatus.CREATED);

        assertThat(chat.getId()).isNotNull();
        assertThat(chat.getId()).isEqualTo(sameChat.getId());
        assertThat(chat.getCreator().getLogin()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(sameChat.getCreator().getLogin()).isEqualTo(TEST_PREFIX + "student2");
        assertParticipants(chat.getId(), 2, "student1", "student2");
        assertParticipants(sameChat.getId(), 2, "student1", "student2");

        // members of the created one to one chat are only notified in case the first message within the conversation is created
        verifyNoParticipantTopicWebsocketSent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldStoreTheKeyOfThePairWhateverTheOrderOfTheUsers() throws Exception {
        var chat = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class,
                HttpStatus.CREATED);

        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var storedChat = oneToOneChatRepository.findById(chat.getId()).orElseThrow();
        assertThat(storedChat.getParticipantPairKey()).isEqualTo(OneToOneChat.pairKeyOf(student1.getId(), student2.getId()))
                .isEqualTo(OneToOneChat.pairKeyOf(student2.getId(), student1.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestWhenTheChatPartnerIsTheRequestingUser() throws Exception {
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", new OneToOneChatCreationDTO(student1.getId(), null),
                OneToOneChatDTO.class, HttpStatus.BAD_REQUEST);

        assertThat(oneToOneChatRepository.findAll().stream().filter(chat -> chat.getCourse().getId().equals(exampleCourseId))).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCompleteAChatThatWasLeftWithoutParticipants() throws Exception {
        // A request that failed after inserting the chat left it behind without participants. The pair is not blocked by it: the next request completes it.
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var halfCreatedChat = new OneToOneChat();
        halfCreatedChat.setCourse(exampleCourse);
        halfCreatedChat.setCreator(student1);
        halfCreatedChat.setParticipantPairKey(OneToOneChat.pairKeyOf(student1.getId(), student2.getId()));
        halfCreatedChat = oneToOneChatRepository.save(halfCreatedChat);

        var chat = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class,
                HttpStatus.CREATED);

        assertThat(chat.getId()).isEqualTo(halfCreatedChat.getId());
        assertParticipants(chat.getId(), 2, "student1", "student2");
    }

    @Test
    void shouldCreateExactlyOneChatWhenSeveralRequestsStartItAtTheSameTime() throws Exception {
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        int requests = 8;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        try {
            var start = new CountDownLatch(1);
            List<Future<Long>> chatIds = new ArrayList<>();
            for (int request = 0; request < requests; request++) {
                // both users start the chat, as they do when they write to each other for the first time
                var requester = request % 2 == 0 ? student1 : student2;
                var partner = request % 2 == 0 ? student2 : student1;
                chatIds.add(executor.submit(() -> {
                    start.await();
                    return SecurityUtils.runAs(requester, () -> oneToOneChatService.startOneToOneChat(exampleCourse, requester, partner)).getId();
                }));
            }
            start.countDown();

            Set<Long> distinctChatIds = new HashSet<>();
            for (var chatId : chatIds) {
                // get() rethrows an error of the request, which this test must not see
                distinctChatIds.add(chatId.get(60, TimeUnit.SECONDS));
            }

            assertThat(distinctChatIds).hasSize(1);
            assertThat(oneToOneChatRepository.findAll().stream().filter(chat -> chat.getCourse().getId().equals(exampleCourseId))).hasSize(1);
            assertParticipants(distinctChatIds.iterator().next(), 2, "student1", "student2");
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestWhenSupplyingInsufficientAmountOfLogins() throws Exception {
        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2", testPrefix + "student3"),
                OneToOneChatDTO.class, HttpStatus.BAD_REQUEST);
        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", List.of(), OneToOneChatDTO.class, HttpStatus.BAD_REQUEST);
        verifyNoParticipantTopicWebsocketSent();

    }

    @ParameterizedTest
    @EnumSource(value = CourseInformationSharingConfiguration.class, names = { "COMMUNICATION_ONLY", "DISABLED" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnForbiddenWhenMessagingIsDisabled(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        startOneToOneChat_messagingDeactivated(courseInformationSharingConfiguration);
    }

    void startOneToOneChat_messagingDeactivated(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);

        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class,
                HttpStatus.FORBIDDEN);

        // active messaging again
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "student42", roles = "USER")
    void shouldReturnBadRequestWhenStudentIsNotAllowedInCourse() throws Exception {
        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnExistingChatWhenChatAlreadyExists() throws Exception {
        var chat = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class,
                HttpStatus.CREATED);
        var chat2 = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class,
                HttpStatus.CREATED);
        assertThat(chat).isNotNull();
        assertThat(chat2).isNotNull();
        assertThat(chat.getId()).isEqualTo(chat2.getId());
        assertParticipants(chat.getId(), 2, "student1", "student2");

        verifyNoParticipantTopicWebsocketSent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldNotifyChatPartnerAboutNewConversationWhenChatIsStarted() throws Exception {
        // when
        var chat = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", List.of(testPrefix + "student2"), OneToOneChatDTO.class,
                HttpStatus.CREATED);
        var post = this.postInConversation(chat.getId(), "student1");
        // then
        verifyMultipleParticipantTopicWebsocketSent(CommunicationCrudAction.CREATE, chat.getId(), "student1", "student2");
        // The broadcast wraps the entity in a cycle-free PostBroadcastDTO (see PostingService.broadcastForPost);
        // match by post id since record equality between PostResponseDTO and Post entity wouldn't hold.
        verify(websocketMessagingService, timeout(10000).times(2)).sendMessage(aCanonicalPostBroadcastTopic(),
                (Object) argThat(argument -> argument instanceof PostBroadcastDTO broadcast && post.id().equals(broadcast.post().id())));
        verifyNoParticipantTopicWebsocketSentExceptAction(CommunicationCrudAction.CREATE, CommunicationCrudAction.NEW_MESSAGE);

    }

    // PR

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCreateOneToOneChatWhenRequestedWithUserId() throws Exception {
        Long student2Id = userRepository.findOneByLogin(testPrefix + "student2").orElseThrow().getId();

        var chat = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats/" + student2Id, null, OneToOneChatDTO.class,
                HttpStatus.CREATED);

        assertThat(chat).isNotNull();
        assertParticipants(chat.getId(), 2, "student1", "student2");
        verifyNoParticipantTopicWebsocketSent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnExistingChatWhenRequestedWithUserIdAndChatExists() throws Exception {
        Long student2Id = userRepository.findOneByLogin(testPrefix + "student2").orElseThrow().getId();

        var chat1 = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats/" + student2Id, null, OneToOneChatDTO.class,
                HttpStatus.CREATED);

        var chat2 = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats/" + student2Id, null, OneToOneChatDTO.class,
                HttpStatus.CREATED);

        assertThat(chat1).isNotNull();
        assertThat(chat2).isNotNull();
        assertThat(chat1.getId()).isEqualTo(chat2.getId());
        assertParticipants(chat1.getId(), 2, "student1", "student2");
        verifyNoParticipantTopicWebsocketSent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnNotFoundWhenUnknownUserIdIsPassed() throws Exception {
        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats/99999", null, OneToOneChatDTO.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldCreateOneToOneChatWhenRequestedWithUserIdInBody() throws Exception {
        Long student2Id = userRepository.findOneByLogin(testPrefix + "student2").orElseThrow().getId();

        var chat = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", Map.of("userId", student2Id), OneToOneChatDTO.class,
                HttpStatus.CREATED);

        assertThat(chat).isNotNull();
        assertParticipants(chat.getId(), 2, "student1", "student2");
        verifyNoParticipantTopicWebsocketSent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestWhenUserIdInBodyIsFractional() throws Exception {
        // A fractional userId must be rejected, not silently truncated (e.g. 1.9 -> 1), which could otherwise start a chat with a different user than the body names.
        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats", Map.of("userId", 1.9), OneToOneChatDTO.class, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @EnumSource(value = CourseInformationSharingConfiguration.class, names = { "COMMUNICATION_ONLY", "DISABLED" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnForbiddenWhenMessagingIsDisabledAndUserIdIsSupplied(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        Long student2Id = userRepository.findOneByLogin(testPrefix + "student2").orElseThrow().getId();

        setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);

        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats/" + student2Id, null, OneToOneChatDTO.class, HttpStatus.FORBIDDEN);

        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "student42", roles = "USER")
    void shouldReturnForbiddenWhenStudentIsNotInCourse() throws Exception {
        Long student2Id = userRepository.findOneByLogin(testPrefix + "student2").orElseThrow().getId();

        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/one-to-one-chats/" + student2Id, null, OneToOneChatDTO.class, HttpStatus.FORBIDDEN);
    }

    /**
     * Matches the two destinations a post broadcast legitimately uses: the per-user conversation topic for a private
     * conversation, and the course-wide communication topic for a course-wide channel. Which of the two applies depends
     * on the conversation under test, and some helpers here cover both, so this matcher accepts either shape but
     * nothing else - in particular neither the retired legacy mirror topic nor an unrelated destination, both
     * of which a bare {@code anyString()} would have accepted.
     *
     * @return a Mockito matcher for a canonical post broadcast destination
     */
    private static WebsocketDestination aCanonicalPostBroadcastTopic() {
        return topicMatching("/topic/user/\\d+/notifications/conversations|/topic/communication/courses/\\d+");
    }

}
