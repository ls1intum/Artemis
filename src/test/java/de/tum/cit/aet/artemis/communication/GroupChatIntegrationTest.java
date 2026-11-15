package de.tum.cit.aet.artemis.communication;

import static de.tum.cit.aet.artemis.core.util.WebsocketDestinationMatchers.topicMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserFactory;
import de.tum.cit.aet.artemis.communication.dto.CommunicationCrudAction;
import de.tum.cit.aet.artemis.communication.dto.GroupChatCreationDTO;
import de.tum.cit.aet.artemis.communication.dto.GroupChatDTO;
import de.tum.cit.aet.artemis.communication.dto.PostBroadcastDTO;
import de.tum.cit.aet.artemis.communication.repository.conversation.GroupChatRepository;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketDestination;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.notification.domain.CourseNotification;
import de.tum.cit.aet.artemis.notification.test_repository.CourseNotificationTestRepository;
import de.tum.cit.aet.artemis.notification.test_repository.UserCourseNotificationStatusTestRepository;

class GroupChatIntegrationTest extends AbstractConversationTest {

    private static final String TEST_PREFIX = "grtest";

    private static final String OTHER_PREFIX = TEST_PREFIX + "other";

    private static final int NUMBER_OF_STUDENTS = 11;

    @Autowired
    private GroupChatRepository groupChatRepository;

    @Autowired
    private CourseNotificationTestRepository courseNotificationRepository;

    @Autowired
    private UserCourseNotificationStatusTestRepository userCourseNotificationStatusRepository;

    @BeforeEach
    @Override
    void setupTestScenario() throws Exception {
        super.setupTestScenario();
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 0, 0, 0);
        if (userRepository.findOneByLogin(OTHER_PREFIX + "student42").isEmpty()) {
            userRepository.save(UserFactory.generateActivatedUser(OTHER_PREFIX + "student42"));
        }
        userUtilService.enrollPrefixedUsersInCourse(exampleCourse, TEST_PREFIX);
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startGroupChat_asStudent1WithStudent2AndStudent3_shouldCreateGroupChat() throws Exception {
        // when
        var chat = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats",
                new GroupChatCreationDTO(List.of(testPrefix + "student2", testPrefix + "student3")), GroupChatDTO.class, HttpStatus.CREATED);
        // then
        assertThat(chat).isNotNull();
        assertParticipants(chat.getId(), 3, "student1", "student2", "student3");
        // all conversation participants should be notified that the conversation has been "created"
        verifyMultipleParticipantTopicWebsocketSent(CommunicationCrudAction.CREATE, chat.getId(), "student2", "student3");
        verifyNoParticipantTopicWebsocketSentExceptAction(CommunicationCrudAction.CREATE);

        // cleanup
        var conversation = groupChatRepository.findById(chat.getId()).orElseThrow();
        conversationRepository.delete(conversation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startGroupChat_loginOfUserWithoutCourseRole_isNotAddedToTheChat() throws Exception {
        var chat = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats",
                new GroupChatCreationDTO(List.of(testPrefix + "student2", OTHER_PREFIX + "student42")), GroupChatDTO.class, HttpStatus.CREATED);

        assertParticipants(chat.getId(), 2, "student1", "student2");

        // cleanup
        var conversation = groupChatRepository.findById(chat.getId()).orElseThrow();
        conversationRepository.delete(conversation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startGroupChat_onlyLoginsOfUsersWithoutCourseRole_shouldReturnBadRequest() throws Exception {
        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats", new GroupChatCreationDTO(List.of(OTHER_PREFIX + "student42")),
                GroupChatDTO.class, HttpStatus.BAD_REQUEST);
        verifyNoParticipantTopicWebsocketSent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void registerUsersToGroupChat_loginOfUserWithoutCourseRole_isNotAdded() throws Exception {
        GroupChatDTO chat = createGroupChatWithStudent1To3();

        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/register",
                List.of(OTHER_PREFIX + "student42", testPrefix + "student4"), HttpStatus.OK);

        assertParticipants(chat.getId(), 4, "student1", "student2", "student3", "student4");

        // cleanup
        var conversation = groupChatRepository.findById(chat.getId()).orElseThrow();
        conversationRepository.delete(conversation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startGroupChat_invalidNumberOfChatPartners_shouldReturnBadRequest() throws Exception {
        // chat with too many users
        // then
        var loginList = new ArrayList<String>();
        for (int i = 2; i <= NUMBER_OF_STUDENTS; i++) {
            loginList.add(testPrefix + "student" + i);
        }
        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats", new GroupChatCreationDTO(loginList), GroupChatDTO.class,
                HttpStatus.BAD_REQUEST);
        // chat with too few users
        // then
        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats", new GroupChatCreationDTO(List.of()), GroupChatDTO.class,
                HttpStatus.BAD_REQUEST);
        verifyNoParticipantTopicWebsocketSent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startGroupChat_legacyLoginArray_shouldCreateGroupChat() throws Exception {
        var response = request
                .performMvcRequest(post("/api/communication/courses/{courseId}/group-chats", exampleCourseId).contentType(MediaType.APPLICATION_JSON)
                        .content(request.getObjectMapper().writeValueAsString(List.of(testPrefix + "student2", testPrefix + "student3"))))
                .andExpect(status().isCreated()).andReturn().getResponse();
        request.restoreSecurityContext();
        var chat = request.getObjectMapper().readValue(response.getContentAsString(), GroupChatDTO.class);

        assertThat(chat).isNotNull();
        assertThat(response.getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/group-chats/" + chat.getId());
        assertParticipants(chat.getId(), 3, "student1", "student2", "student3");
        verifyMultipleParticipantTopicWebsocketSent(CommunicationCrudAction.CREATE, chat.getId(), "student2", "student3");
        verifyNoParticipantTopicWebsocketSentExceptAction(CommunicationCrudAction.CREATE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startGroupChat_bodyWithoutMemberLogins_shouldReturnBadRequest() throws Exception {
        String url = "/api/communication/courses/" + exampleCourseId + "/group-chats";
        request.postWithResponseBody(url, Map.of(), GroupChatDTO.class, HttpStatus.BAD_REQUEST);
        request.postWithResponseBody(url, Map.of("memberLogins", testPrefix + "student2"), GroupChatDTO.class, HttpStatus.BAD_REQUEST);
        verifyNoParticipantTopicWebsocketSent();
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "student42", roles = "USER")
    void startGroupChat_notAllowedAsNotStudentInCourse_shouldReturnBadRequest() throws Exception {
        // then
        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats",
                new GroupChatCreationDTO(List.of(testPrefix + "student2", testPrefix + "student3")), GroupChatDTO.class, HttpStatus.FORBIDDEN);
        verifyNoParticipantTopicWebsocketSent();
    }

    @ParameterizedTest
    @EnumSource(value = CourseInformationSharingConfiguration.class, names = { "COMMUNICATION_ONLY", "DISABLED" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void startGroupChat_messagingFeatureDeactivated_shouldReturnForbidden(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        startGroupChat_messagingDeactivated(courseInformationSharingConfiguration);

    }

    void startGroupChat_messagingDeactivated(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);

        request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats",
                new GroupChatCreationDTO(List.of(testPrefix + "student2", testPrefix + "student3")), GroupChatDTO.class, HttpStatus.FORBIDDEN);

        // active messaging again
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void postInGroupChat_firstPostReceivedByAllMembers() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        var post = this.postInConversation(chat.getId(), "student1");
        // then
        // The broadcast wraps the entity in a cycle-free PostBroadcastDTO (see PostingService.broadcastForPost).
        verify(websocketMessagingService, timeout(10000).times(3)).sendMessage(aCanonicalPostBroadcastTopic(),
                (Object) argThat(argument -> argument instanceof PostBroadcastDTO broadcast && post.id().equals(broadcast.post().id())));
        verifyNoParticipantTopicWebsocketSentExceptAction(CommunicationCrudAction.NEW_MESSAGE);

        // cleanup
        var conversation = groupChatRepository.findById(chat.getId()).orElseThrow();
        conversationMessageRepository.deleteById(post.id());
        conversationRepository.delete(conversation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void updateGroupChat_updateName_shouldUpdateName() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        chat.setName("updated");
        request.putWithResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId(), chat, GroupChatDTO.class, HttpStatus.OK);
        // then
        var updatedGroupChat = groupChatRepository.findById(chat.getId()).orElseThrow();
        assertParticipants(updatedGroupChat.getId(), 3, "student1", "student2", "student3");
        assertThat(updatedGroupChat.getName()).isEqualTo("updated");
        verifyMultipleParticipantTopicWebsocketSent(CommunicationCrudAction.UPDATE, chat.getId(), "student1", "student2", "student3");
        verifyNoParticipantTopicWebsocketSentExceptAction(CommunicationCrudAction.UPDATE);

        // cleanup
        var conversation = groupChatRepository.findById(chat.getId()).orElseThrow();
        conversationRepository.delete(conversation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void updateGroupChat_withoutTypeReturnsBadRequest() throws Exception {
        var chat = createGroupChatWithStudent1To3();
        request.performMvcRequest(
                put("/api/communication/courses/{courseId}/group-chats/{groupChatId}", exampleCourseId, chat.getId()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"missing-type"}
                        """)).andExpect(status().isBadRequest());
        request.restoreSecurityContext();
        conversationRepository.deleteById(chat.getId());
    }

    @ParameterizedTest
    @EnumSource(value = CourseInformationSharingConfiguration.class, names = { "COMMUNICATION_ONLY", "DISABLED" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void updateGroupChat_messagingFeatureDeactivated_shouldReturnForbidden(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        updateGroupChat_messagingDeactivated(courseInformationSharingConfiguration);

    }

    void updateGroupChat_messagingDeactivated(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        var chat = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats",
                new GroupChatCreationDTO(List.of(testPrefix + "student2", testPrefix + "student3")), GroupChatDTO.class, HttpStatus.CREATED);
        chat.setName("updated");

        setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);
        request.putWithResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId(), chat, GroupChatDTO.class, HttpStatus.FORBIDDEN);

        // active messaging again
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);

        // cleanup
        var conversation = groupChatRepository.findById(chat.getId()).orElseThrow();
        conversationRepository.delete(conversation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void updateGroupChat_notAMemberOfGroupChat_shouldReturnForbidden() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        chat.setName("updated");
        userUtilService.changeUser(OTHER_PREFIX + "student42");
        request.putWithResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId(), chat, GroupChatDTO.class, HttpStatus.FORBIDDEN);
        // then
        var groupChat = groupChatRepository.findById(chat.getId()).orElseThrow();
        assertParticipants(groupChat.getId(), 3, "student1", "student2", "student3");
        assertThat(groupChat.getName()).isNotEqualTo("updated");
        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        var conversation = groupChatRepository.findById(chat.getId()).orElseThrow();
        conversationRepository.delete(conversation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void registerUsersToGroupChat_memberOfGroupChat_shouldAddUsers() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        // Note: adding student1 and student2 again should not be a problem (silently ignored)
        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/register",
                List.of(testPrefix + "student1", testPrefix + "student2", testPrefix + "student4", testPrefix + "student5"), HttpStatus.OK);
        // then
        assertParticipants(chat.getId(), 5, "student1", "student2", "student3", "student4", "student5");
        verifyMultipleParticipantTopicWebsocketSent(CommunicationCrudAction.CREATE, chat.getId(), "student4", "student5");
        verifyMultipleParticipantTopicWebsocketSent(CommunicationCrudAction.UPDATE, chat.getId(), "student1", "student2", "student3");
        verifyNoParticipantTopicWebsocketSentExceptAction(CommunicationCrudAction.CREATE, CommunicationCrudAction.UPDATE);

        // cleanup
        var conversation = groupChatRepository.findById(chat.getId()).orElseThrow();
        conversationRepository.delete(conversation);
    }

    @ParameterizedTest
    @EnumSource(value = CourseInformationSharingConfiguration.class, names = { "COMMUNICATION_ONLY", "DISABLED" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void registerDeregisterUsersToGroupChat_messagingFeatureDeactivated_shouldReturnForbidden(CourseInformationSharingConfiguration courseInformationSharingConfiguration)
            throws Exception {
        registerDeregisterUsersToGroupChat_messagingDeactivated(courseInformationSharingConfiguration);

    }

    void registerDeregisterUsersToGroupChat_messagingDeactivated(CourseInformationSharingConfiguration courseInformationSharingConfiguration) throws Exception {
        var chat = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats",
                new GroupChatCreationDTO(List.of(testPrefix + "student2", testPrefix + "student3")), GroupChatDTO.class, HttpStatus.CREATED);
        setCourseInformationSharingConfiguration(courseInformationSharingConfiguration);

        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/register", List.of(testPrefix + "student2"),
                HttpStatus.FORBIDDEN);
        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/deregister", List.of(testPrefix + "student2"),
                HttpStatus.FORBIDDEN);

        assertParticipants(chat.getId(), 3, "student1", "student2", "student3");

        // cleanup
        var conversation = groupChatRepository.findById(chat.getId()).orElseThrow();
        conversationRepository.delete(conversation);
        setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void registerUsersToGroupChat_asNonMember_shouldReturnForbidden() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        userUtilService.changeUser(OTHER_PREFIX + "student42");
        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/register",
                List.of(testPrefix + "student1", testPrefix + "student2", testPrefix + "student4", testPrefix + "student5"), HttpStatus.FORBIDDEN);

        // then
        assertParticipants(chat.getId(), 3, "student1", "student2", "student3");

        // cleanup
        var conversation = groupChatRepository.findById(chat.getId()).orElseThrow();
        conversationRepository.delete(conversation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void registerUsersToGroupChat_wouldBreakGroupChatLimit_shouldReturnBadRequest() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        var loginList = new ArrayList<String>();
        for (int i = 4; i <= NUMBER_OF_STUDENTS; i++) {
            loginList.add(testPrefix + "student" + i);
        }
        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/register", loginList, HttpStatus.BAD_REQUEST);
        assertParticipants(chat.getId(), 3, "student1", "student2", "student3");
        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        var conversation = groupChatRepository.findById(chat.getId()).orElseThrow();
        conversationRepository.delete(conversation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deregisterUsersFromGroupChat_asMember_shouldRemoveMembers() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/deregister", List.of(testPrefix + "student2"),
                HttpStatus.OK);
        // then
        assertParticipants(chat.getId(), 2, "student1", "student3");
        verifyMultipleParticipantTopicWebsocketSent(CommunicationCrudAction.DELETE, chat.getId(), "student2");
        verifyMultipleParticipantTopicWebsocketSent(CommunicationCrudAction.UPDATE, chat.getId(), "student1", "student3");
        verifyNoParticipantTopicWebsocketSentExceptAction(CommunicationCrudAction.DELETE, CommunicationCrudAction.UPDATE);

        // cleanup
        var conversation = groupChatRepository.findById(chat.getId()).orElseThrow();
        conversationRepository.delete(conversation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deregisterUsersFromGroupChat_selfDeregistration_shouldRemoveRequestingMember() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/deregister", List.of(testPrefix + "student1"),
                HttpStatus.OK);
        // then
        assertParticipants(chat.getId(), 2, "student2", "student3");
        verifyMultipleParticipantTopicWebsocketSent(CommunicationCrudAction.DELETE, chat.getId(), "student1");
        verifyMultipleParticipantTopicWebsocketSent(CommunicationCrudAction.UPDATE, chat.getId(), "student2", "student3");
        verifyNoParticipantTopicWebsocketSentExceptAction(CommunicationCrudAction.DELETE, CommunicationCrudAction.UPDATE);

        // cleanup
        var conversation = groupChatRepository.findById(chat.getId()).orElseThrow();
        conversationRepository.delete(conversation);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deregisterUsersFromGroupChat_asNonMember_shouldReturnForbidden() throws Exception {
        // given
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        // when
        userUtilService.changeUser(OTHER_PREFIX + "student42");
        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/deregister", List.of(testPrefix + "student2"),
                HttpStatus.FORBIDDEN);
        // then
        assertParticipants(chat.getId(), 3, "student1", "student2", "student3");
        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        var conversation = groupChatRepository.findById(chat.getId()).orElseThrow();
        conversationRepository.delete(conversation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnExistingGroupChatWhenChatAlreadyExists() throws Exception {
        var chat1 = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats",
                new GroupChatCreationDTO(List.of(testPrefix + "student2", testPrefix + "student3")), GroupChatDTO.class, HttpStatus.CREATED);

        var chat2 = request.postWithResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats",
                new GroupChatCreationDTO(List.of(testPrefix + "student2", testPrefix + "student3")), GroupChatDTO.class, HttpStatus.CREATED);

        assertThat(chat1).isNotNull();
        assertThat(chat2).isNotNull();
        assertThat(chat1.getId()).isEqualTo(chat2.getId());
        assertParticipants(chat1.getId(), 3, "student1", "student2", "student3");

        var conversation = groupChatRepository.findById(chat1.getId()).orElseThrow();
        conversationRepository.delete(conversation);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldSendAddedToChannelNotificationWhenRegisteringUsersToGroupChat() throws Exception {

        GroupChatDTO chat = createGroupChatWithStudent1To3();

        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/register",
                List.of(testPrefix + "student4", testPrefix + "student5"), HttpStatus.OK);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            boolean hasAddedToChannelNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(exampleCourseId))
                    .anyMatch(notification -> notification.getType() == 19);

            assertThat(hasAddedToChannelNotification).isTrue();
        });

        conversationRepository.deleteById(chat.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldSendRemovedFromChannelNotificationWhenUnregisteringUsersFromGroupChat() throws Exception {
        GroupChatDTO chat = createGroupChatWithStudent1To3();

        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/deregister", List.of(testPrefix + "student2"),
                HttpStatus.OK);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            List<CourseNotification> notifications = courseNotificationRepository.findAll();

            boolean hasRemovedFromChannelNotification = notifications.stream().filter(notification -> notification.getCourse().getId().equals(exampleCourseId))
                    .anyMatch(notification -> notification.getType() == 20);

            assertThat(hasRemovedFromChannelNotification).isTrue();
        });

        conversationRepository.deleteById(chat.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deregisterUsersFromGroupChat_withLoginsOfNoParticipants_shouldOnlyNotifyTheRemovedParticipants() throws Exception {
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        Set<Long> notificationsBefore = removedFromChannelNotificationIds();
        // student5 belongs to the course without being a participant, the other user does not belong to the course
        List<String> logins = List.of(testPrefix + "student2", testPrefix + "student5", OTHER_PREFIX + "student42");

        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/deregister", logins, HttpStatus.OK);

        assertParticipants(chat.getId(), 2, "student1", "student3");
        long removedUserId = userRepository.findOneByLogin(testPrefix + "student2").orElseThrow().getId();
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Set<Long> newNotifications = removedFromChannelNotificationIds();
            newNotifications.removeAll(notificationsBefore);
            assertThat(newNotifications).hasSize(1);
            Set<Long> recipientIds = userCourseNotificationStatusRepository.findAllByCourseNotificationId(newNotifications.iterator().next()).stream()
                    .map(status -> status.getUser().getId()).collect(Collectors.toSet());
            assertThat(recipientIds).containsExactly(removedUserId);
        });

        conversationRepository.deleteById(chat.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deregisterUsersFromGroupChat_withMoreLoginsThanAGroupChatCanHaveParticipants_shouldReturnBadRequest() throws Exception {
        GroupChatDTO chat = createGroupChatWithStudent1To3();
        List<String> logins = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            logins.add(testPrefix + "student" + i);
        }

        request.postWithoutResponseBody("/api/communication/courses/" + exampleCourseId + "/group-chats/" + chat.getId() + "/deregister", logins, HttpStatus.BAD_REQUEST);

        assertParticipants(chat.getId(), 3, "student1", "student2", "student3");
        conversationRepository.deleteById(chat.getId());
    }

    private Set<Long> removedFromChannelNotificationIds() {
        return courseNotificationRepository.findAll().stream().filter(notification -> notification.getCourse().getId().equals(exampleCourseId) && notification.getType() == 20)
                .map(CourseNotification::getId).collect(Collectors.toCollection(HashSet::new));
    }

    private GroupChatDTO createGroupChatWithStudent1To3() throws Exception {
        return this.createGroupChat("student2", "student3");
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
