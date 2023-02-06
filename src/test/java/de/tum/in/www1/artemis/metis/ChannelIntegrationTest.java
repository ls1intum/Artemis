package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.service.tutorialgroups.TutorialGroupChannelManagementService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;

class ChannelIntegrationTest extends AbstractConversationTest {

    @Autowired
    TutorialGroupRepository tutorialGroupRepository;

    @Autowired
    TutorialGroupChannelManagementService tutorialGroupChannelManagementService;

    private static final String TEST_PREFIX = "chtest";

    @BeforeEach
    void setupTestScenario() throws Exception {
        super.setupTestScenario();
        this.database.addUsers(TEST_PREFIX, 2, 2, 1, 2);
        if (userRepository.findOneByLogin(testPrefix + "student42").isEmpty()) {
            userRepository.save(ModelFactory.generateActivatedUser(testPrefix + "student42"));
        }
        if (userRepository.findOneByLogin(testPrefix + "tutor42").isEmpty()) {
            userRepository.save(ModelFactory.generateActivatedUser(testPrefix + "tutor42"));
        }
        if (userRepository.findOneByLogin(testPrefix + "editor42").isEmpty()) {
            userRepository.save(ModelFactory.generateActivatedUser(testPrefix + "editor42"));
        }
        if (userRepository.findOneByLogin(testPrefix + "instructor42").isEmpty()) {
            userRepository.save(ModelFactory.generateActivatedUser(testPrefix + "instructor42"));
        }
    }

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createChannel_asInstructor_shouldCreateChannel(boolean isPublicChannel) throws Exception {
        isAllowedToCreateChannelTest(isPublicChannel, "instructor1");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createChannel_asTutor_shouldCreateChannel(boolean isPublicChannel) throws Exception {
        // given
        isAllowedToCreateChannelTest(isPublicChannel, "tutor1");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void createChannel_asEditor_shouldCreateChannel(boolean isPublicChannel) throws Exception {
        // given
        isAllowedToCreateChannelTest(isPublicChannel, "editor1");
    }

    private void isAllowedToCreateChannelTest(boolean isPublicChannel, String loginNameWithoutPrefix) throws Exception {
        // given
        var channelDTO = new ChannelDTO();
        channelDTO.setName(RandomConversationNameGenerator.generateRandomConversationName());
        channelDTO.setIsPublic(isPublicChannel);
        channelDTO.setIsAnnouncementChannel(false);
        channelDTO.setDescription("general channel");

        // when
        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/channels", channelDTO, ChannelDTO.class, HttpStatus.CREATED);
        // then
        this.assertChannelProperties(chat.getId(), channelDTO.getName(), null, channelDTO.getDescription(), channelDTO.getIsPublic(), false);
        var participants = assertParticipants(chat.getId(), 1, loginNameWithoutPrefix);
        // creator is automatically added as channel moderator
        assertThat(participants.stream().findFirst().get().getIsModerator()).isTrue();
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.CREATE, chat.getId(), loginNameWithoutPrefix);
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.CREATE);

        // cleanup
        conversationRepository.deleteById(chat.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createChannel_nameInvalid_shouldReturnBadRequest(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);

        var channelDTO = new ChannelDTO();
        channelDTO.setIsPublic(isPublicChannel);
        channelDTO.setIsAnnouncementChannel(false);
        channelDTO.setName(RandomConversationNameGenerator.generateRandomConversationName());
        channelDTO.setDescription("general channel");

        // when
        // duplicated name
        channelDTO.setName(channel.getName());
        expectCreateBadRequest(channelDTO);
        // empty name
        channelDTO.setName("");
        expectCreateBadRequest(channelDTO);
        // null name
        channelDTO.setName(null);
        expectCreateBadRequest(channelDTO);
        // regex not matching
        channelDTO.setName("general%%%%wow");
        expectCreateBadRequest(channelDTO);

        // then
        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createChannel_descriptionInvalid_shouldReturnBadRequest(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);

        var channelDTO = new ChannelDTO();
        channelDTO.setIsPublic(isPublicChannel);
        channelDTO.setIsAnnouncementChannel(false);
        channelDTO.setName(RandomConversationNameGenerator.generateRandomConversationName());
        channelDTO.setDescription("a".repeat(251));

        // when
        expectCreateBadRequest(channelDTO);
        // then
        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createChannel_asNonCourseInstructorOrTutorOrEditor_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channelDTO = new ChannelDTO();
        channelDTO.setName(RandomConversationNameGenerator.generateRandomConversationName());
        channelDTO.setIsPublic(isPublicChannel);
        channelDTO.setIsAnnouncementChannel(false);
        channelDTO.setDescription("general channel");

        // then
        expectCreateForbidden(channelDTO);
        database.changeUser(testPrefix + "instructor42");
        expectCreateForbidden(channelDTO);
        database.changeUser(testPrefix + "tutor42");
        expectCreateForbidden(channelDTO);
        database.changeUser(testPrefix + "editor42");
        expectCreateForbidden(channelDTO);

        verifyNoParticipantTopicWebsocketSent();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteChannel_asInstructor_shouldDeleteChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        // when
        database.changeUser(testPrefix + "instructor2");
        request.delete("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), HttpStatus.OK);
        // then
        assertThat(channelRepository.findById(channel.getId())).isEmpty();
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.DELETE, channel.getId(), "instructor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.DELETE);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteTutorialGroupChannel_asInstructor_shouldReturnBadRequest(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        var tutorialGroup = database.createTutorialGroup(exampleCourseId, "tg-channel-test", "LoremIpsum", 10, false, "Garching", Language.ENGLISH,
                userRepository.findOneByLogin(testPrefix + "tutor1").get(), Set.of());
        var channelFromDatabase = channelRepository.findById(channel.getId()).get();

        tutorialGroup.setTutorialGroupChannel(channelFromDatabase);
        tutorialGroup = tutorialGroupRepository.save(tutorialGroup);

        // when
        database.changeUser(testPrefix + "instructor2");
        request.delete("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), HttpStatus.BAD_REQUEST);
        // then
        assertThat(channelRepository.findById(channel.getId())).isNotEmpty();
        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        tutorialGroupChannelManagementService.deleteTutorialGroupChannel(tutorialGroup);
        tutorialGroupRepository.deleteById(tutorialGroup.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void deleteChannel_asCreator_shouldDeleteChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        // when
        request.delete("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), HttpStatus.OK);
        // then
        assertThat(channelRepository.findById(channel.getId())).isEmpty();
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.DELETE, channel.getId(), "tutor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.DELETE);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteChannel_asNonCourseInstructor_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        addUserAsChannelModerators(channel, "tutor2");

        // then
        database.changeUser(testPrefix + "student1");
        expectDeleteForbidden(channel.getId());
        database.changeUser(testPrefix + "tutor1");
        expectDeleteForbidden(channel.getId());
        database.changeUser(testPrefix + "tutor2");
        expectDeleteForbidden(channel.getId());
        database.changeUser(testPrefix + "editor1");
        expectDeleteForbidden(channel.getId());
        database.changeUser(testPrefix + "instructor42");
        expectDeleteForbidden(channel.getId());

        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateChannel_asUserWithChannelModerationRights_shouldUpdateChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        var updateDTO = new ChannelDTO();
        updateDTO.setName(RandomConversationNameGenerator.generateRandomConversationName());
        updateDTO.setDescription("new description");
        updateDTO.setTopic("new topic");
        addUserAsChannelModerators(channel, "tutor1");

        // then
        // every instructor automatically has moderation rights for every channel
        database.changeUser(testPrefix + "instructor2");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), updateDTO, ChannelDTO.class, HttpStatus.OK);
        this.assertChannelProperties(channel.getId(), updateDTO.getName(), updateDTO.getTopic(), updateDTO.getDescription(), isPublicChannel, false);
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE);
        resetWebsocketMock();

        // channel moderators can also update the channel
        updateDTO.setName(RandomConversationNameGenerator.generateRandomConversationName());
        updateDTO.setDescription("new description2");
        updateDTO.setTopic("new topic2");
        database.changeUser(testPrefix + "tutor1");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), updateDTO, ChannelDTO.class, HttpStatus.OK);
        this.assertChannelProperties(channel.getId(), updateDTO.getName(), updateDTO.getTopic(), updateDTO.getDescription(), isPublicChannel, false);
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateChannel_onArchivedChannel_shouldReturnOk(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        archiveChannel(channel.getId());

        var updateDTO = new ChannelDTO();
        updateDTO.setName(RandomConversationNameGenerator.generateRandomConversationName());
        updateDTO.setDescription("new description");
        updateDTO.setTopic("new topic");
        addUserAsChannelModerators(channel, "tutor1");

        // then
        database.changeUser(testPrefix + "instructor2");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), updateDTO, ChannelDTO.class, HttpStatus.OK);
        this.assertChannelProperties(channel.getId(), updateDTO.getName(), updateDTO.getTopic(), updateDTO.getDescription(), isPublicChannel, true);
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateChannel_asUserWithoutChannelModerationRights_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        var updateDTO = new ChannelDTO();
        updateDTO.setName(RandomConversationNameGenerator.generateRandomConversationName());
        updateDTO.setDescription("new description");
        updateDTO.setTopic("new topic");

        // then
        database.changeUser(testPrefix + "student1");
        expectUpdateForbidden(channel.getId(), updateDTO);
        database.changeUser(testPrefix + "tutor1");
        expectUpdateForbidden(channel.getId(), updateDTO);
        database.changeUser(testPrefix + "editor1");
        expectUpdateForbidden(channel.getId(), updateDTO);
        database.changeUser(testPrefix + "instructor42");
        expectUpdateForbidden(channel.getId(), updateDTO);

        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void archiveAndUnarchiveChannel_asUserWithoutChannelModerationRights_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);

        // then
        database.changeUser(testPrefix + "student1");
        expectArchivalChangeForbidden(channel, isPublicChannel, true);
        expectArchivalChangeForbidden(channel, isPublicChannel, false);
        database.changeUser(testPrefix + "tutor1");
        expectArchivalChangeForbidden(channel, isPublicChannel, true);
        expectArchivalChangeForbidden(channel, isPublicChannel, false);
        database.changeUser(testPrefix + "editor1");
        expectArchivalChangeForbidden(channel, isPublicChannel, true);
        expectArchivalChangeForbidden(channel, isPublicChannel, false);
        database.changeUser(testPrefix + "instructor42");
        expectArchivalChangeForbidden(channel, isPublicChannel, true);
        expectArchivalChangeForbidden(channel, isPublicChannel, false);

        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void archiveAndUnarchiveChannel_asUserWithChannelModerationRights_shouldArchiveChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        addUserAsChannelModerators(channel, "tutor1");

        // then
        // every instructor automatically has moderation rights for every channel
        database.changeUser(testPrefix + "instructor2");
        testArchivalChangeWorks(channel, isPublicChannel, true);
        testArchivalChangeWorks(channel, isPublicChannel, false);

        // channel moderators can also update the channel
        database.changeUser(testPrefix + "tutor1");
        testArchivalChangeWorks(channel, isPublicChannel, true);
        testArchivalChangeWorks(channel, isPublicChannel, false);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void grantRevokeChannelModeratorRole_asUserWithChannelModerationRights_shouldGrantRevokeChannelModeratorRole(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        addUserAsChannelModerators(channel, "tutor1");
        addUsersToConversation(channel.getId(), "student1");
        addUsersToConversation(channel.getId(), "student2");

        // then
        // every instructor automatically has moderation rights for every channel
        database.changeUser(testPrefix + "instructor2");
        testGrantRevokeChannelModeratorRoleWorks(channel, true);
        testGrantRevokeChannelModeratorRoleWorks(channel, false);

        // channel moderators can also grand and revoke channel moderation rights
        database.changeUser(testPrefix + "tutor1");
        testGrantRevokeChannelModeratorRoleWorks(channel, true);
        testGrantRevokeChannelModeratorRoleWorks(channel, false);

        // note: you can NOT revoke the channel moderator role of the creator to guarantee that there is always at least one channel moderator
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/revoke-channel-moderator", List.of(testPrefix + "instructor1"),
                HttpStatus.BAD_REQUEST);
        assertUsersAreChannelModerators(channel.getId(), "instructor1");

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void grantRevokeChannelModeratorRole_asUserWithoutChannelModerationRights_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);

        // then
        database.changeUser(testPrefix + "student1");
        expectGrantRevokeChannelModeratorRoleForbidden(channel, true);
        expectGrantRevokeChannelModeratorRoleForbidden(channel, false);
        database.changeUser(testPrefix + "tutor1");
        expectGrantRevokeChannelModeratorRoleForbidden(channel, true);
        expectGrantRevokeChannelModeratorRoleForbidden(channel, false);
        database.changeUser(testPrefix + "editor1");
        expectGrantRevokeChannelModeratorRoleForbidden(channel, true);
        expectGrantRevokeChannelModeratorRoleForbidden(channel, false);
        database.changeUser(testPrefix + "instructor42");
        expectGrantRevokeChannelModeratorRoleForbidden(channel, true);
        expectGrantRevokeChannelModeratorRoleForbidden(channel, false);
        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void registerUsersToChannel_asUserWithChannelModerationRights_shouldRegisterUsersToChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        addUserAsChannelModerators(channel, "tutor1");

        // then
        // every instructor automatically has moderation rights for every channel
        database.changeUser(testPrefix + "instructor2");
        testRegisterAndDeregisterUserWorks(channel, true);
        testRegisterAndDeregisterUserWorks(channel, false);

        // channel moderators can also grant and revoke channel moderator role
        database.changeUser(testPrefix + "tutor1");
        testRegisterAndDeregisterUserWorks(channel, true);
        testRegisterAndDeregisterUserWorks(channel, false);

        removeUsersFromConversation(channel.getId(), "student1", "student2", "tutor1");
        database.changeUser(testPrefix + "instructor1");
        var params = new LinkedMultiValueMap<String, String>();
        params.add("addAllStudents", String.valueOf(true));
        params.add("addAllTutors", String.valueOf(true));
        params.add("addAllInstructors", String.valueOf(true));

        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/register", HttpStatus.OK, params);
        var course = courseRepository.findByIdElseThrow(exampleCourseId);
        var allStudentLogins = userRepository.findAllInGroup(course.getStudentGroupName()).stream().map(User::getLogin).collect(Collectors.toSet());
        var allTutorLogins = userRepository.findAllInGroup(course.getTeachingAssistantGroupName()).stream().map(User::getLogin).collect(Collectors.toSet());
        var allEditorLogins = userRepository.findAllInGroup(course.getEditorGroupName()).stream().map(User::getLogin).collect(Collectors.toSet());
        var allInstructorLogins = userRepository.findAllInGroup(course.getInstructorGroupName()).stream().map(User::getLogin).collect(Collectors.toSet());
        var allUserLogins = new HashSet<>(allStudentLogins);
        allUserLogins.addAll(allTutorLogins);
        allUserLogins.addAll(allEditorLogins);
        allUserLogins.addAll(allInstructorLogins);
        String[] allUserLoginsArray = allUserLogins.toArray(new String[0]);

        allUserLoginsArray = Arrays.stream(allUserLoginsArray).filter(login -> login.startsWith(testPrefix)).map(login -> login.substring(testPrefix.length()))
                .toArray(String[]::new);

        assertUsersAreConversationMembers(channel.getId(), allUserLoginsArray);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void registerUsersToChannel_asUserWithoutChannelModerationRights_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);

        // then
        database.changeUser(testPrefix + "student1");
        expectRegisterDeregisterForbidden(channel, true);
        expectRegisterDeregisterForbidden(channel, false);
        database.changeUser(testPrefix + "tutor1");
        expectRegisterDeregisterForbidden(channel, true);
        expectRegisterDeregisterForbidden(channel, false);
        database.changeUser(testPrefix + "editor1");
        expectRegisterDeregisterForbidden(channel, true);
        expectRegisterDeregisterForbidden(channel, false);
        database.changeUser(testPrefix + "instructor42");
        expectRegisterDeregisterForbidden(channel, true);
        expectRegisterDeregisterForbidden(channel, false);
        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void leaveChannel_asNormalUser_canLeaveChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        addUsersToConversation(channel.getId(), "student1");

        // then
        database.changeUser(testPrefix + "student1");
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/deregister", List.of(testPrefix + "student1"), HttpStatus.OK);
        assertUserAreNotConversationMembers(channel.getId(), "student1");
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1");
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.DELETE, channel.getId(), "student1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE, MetisCrudAction.DELETE);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void leaveChannel_asCreator_shouldReturnBadRequest(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        // then
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/deregister", List.of(testPrefix + "instructor1"),
                HttpStatus.BAD_REQUEST);
        assertUsersAreConversationMembers(channel.getId(), "instructor1");
        verifyNoParticipantTopicWebsocketSent();

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void joinPublicChannel_asNormalUser_canJoinPublicChannel() throws Exception {
        // given
        var channel = createChannel(true);

        // then
        database.changeUser(testPrefix + "student1");
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/register", List.of(testPrefix + "student1"), HttpStatus.OK);
        assertUsersAreConversationMembers(channel.getId(), "student1");
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1");
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.CREATE, channel.getId(), "student1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE, MetisCrudAction.CREATE);

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void joinPrivateChannel_asNormalUser_canNotJoinPrivateChannel() throws Exception {
        // given
        var channel = createChannel(false);

        // then
        database.changeUser(testPrefix + "student1");
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/register", List.of(testPrefix + "student1"), HttpStatus.FORBIDDEN);
        assertUserAreNotConversationMembers(channel.getId(), "student1");

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void joinPrivateChannel_asInstructor_canJoinPrivateChannel() throws Exception {
        // given
        var channel = createChannel(false);

        // then
        database.changeUser(testPrefix + "instructor2");
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/register", List.of(testPrefix + "instructor2"), HttpStatus.OK);
        assertUsersAreConversationMembers(channel.getId(), "instructor2");

        // cleanup
        conversationRepository.deleteById(channel.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCourseChannelsOverview_asNormalUser_canSeeAllPublicChannelsAndPrivateChannelsWhereMember() throws Exception {
        // given
        var publicChannelWhereMember = createChannel(true, RandomConversationNameGenerator.generateRandomConversationName());
        addUsersToConversation(publicChannelWhereMember.getId(), "student1");
        var publicChannelWhereNotMember = createChannel(true, RandomConversationNameGenerator.generateRandomConversationName());
        var privateChannelWhereMember = createChannel(false, RandomConversationNameGenerator.generateRandomConversationName());
        addUsersToConversation(privateChannelWhereMember.getId(), "student1");
        var privateChannelWhereNotMember = createChannel(false, RandomConversationNameGenerator.generateRandomConversationName());

        // then
        database.changeUser(testPrefix + "student1");
        var channels = request.getList("/api/courses/" + exampleCourseId + "/channels/overview", HttpStatus.OK, ChannelDTO.class);
        assertThat(channels.stream().map(ChannelDTO::getId).collect(Collectors.toList())).contains(publicChannelWhereMember.getId(), publicChannelWhereNotMember.getId(),
                privateChannelWhereMember.getId());

        // cleanup
        conversationRepository.deleteById(privateChannelWhereMember.getId());
        conversationRepository.deleteById(publicChannelWhereNotMember.getId());
        conversationRepository.deleteById(publicChannelWhereMember.getId());
        conversationRepository.deleteById(privateChannelWhereNotMember.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCourseChannelsOverview_asCourseInstructor_canSeeAllPublicChannelsAndAllPrivateChannels() throws Exception {
        // given
        var publicChannelWhereMember = createChannel(true, RandomConversationNameGenerator.generateRandomConversationName());
        addUsersToConversation(publicChannelWhereMember.getId(), "student1");
        var publicChannelWhereNotMember = createChannel(true, RandomConversationNameGenerator.generateRandomConversationName());
        var privateChannelWhereMember = createChannel(false, RandomConversationNameGenerator.generateRandomConversationName());
        addUsersToConversation(privateChannelWhereMember.getId(), "student1");
        var privateChannelWhereNotMember = createChannel(false, RandomConversationNameGenerator.generateRandomConversationName());

        // then
        database.changeUser(testPrefix + "instructor2");
        var channels = request.getList("/api/courses/" + exampleCourseId + "/channels/overview", HttpStatus.OK, ChannelDTO.class);
        assertThat(channels.stream().map(ChannelDTO::getId).collect(Collectors.toList())).contains(publicChannelWhereMember.getId(), publicChannelWhereNotMember.getId(),
                privateChannelWhereMember.getId(), privateChannelWhereNotMember.getId());

        // cleanup
        conversationRepository.deleteById(privateChannelWhereMember.getId());
        conversationRepository.deleteById(publicChannelWhereNotMember.getId());
        conversationRepository.deleteById(publicChannelWhereMember.getId());
        conversationRepository.deleteById(privateChannelWhereNotMember.getId());
    }

    private void testArchivalChangeWorks(ChannelDTO channel, boolean isPublicChannel, boolean shouldArchive) throws Exception {
        // prepare channel in db
        if (shouldArchive) {
            this.unArchiveChannel(channel.getId());
        }
        else {
            this.archiveChannel(channel.getId());
        }
        var postfix = shouldArchive ? "/archive" : "/unarchive";
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, HttpStatus.OK, new LinkedMultiValueMap<>());
        this.assertChannelProperties(channel.getId(), channel.getName(), channel.getTopic(), channel.getDescription(), isPublicChannel, shouldArchive);
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE);
        resetWebsocketMock();
    }

    private void testRegisterAndDeregisterUserWorks(ChannelDTO channel, boolean shouldRegister) throws Exception {
        // prepare channel in db
        if (shouldRegister) {
            this.removeUsersFromConversation(channel.getId(), "student1", "student2");
        }
        else {
            this.addUsersToConversation(channel.getId(), "student1", "student2");
        }
        var postfix = shouldRegister ? "/register" : "/deregister";

        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, List.of(testPrefix + "student1", testPrefix + "student2"),
                HttpStatus.OK);
        if (shouldRegister) {
            assertUsersAreConversationMembers(channel.getId(), "student1", "student2");
        }
        else {
            assertUserAreNotConversationMembers(channel.getId(), "student1", "student2");
        }
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        if (shouldRegister) {
            verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.CREATE, channel.getId(), "student1", "student2");
            verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE, MetisCrudAction.CREATE);
        }
        else {
            verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.DELETE, channel.getId(), "student1", "student2");
            verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE, MetisCrudAction.DELETE);
        }
        resetWebsocketMock();
    }

    private void testGrantRevokeChannelModeratorRoleWorks(ChannelDTO channel, boolean shouldGrant) throws Exception {
        // prepare channel in db
        if (shouldGrant) {
            this.revokeChannelModeratorRole(channel.getId(), "student1");
            this.revokeChannelModeratorRole(channel.getId(), "student1");
        }
        else {
            this.grantChannelModeratorRole(channel.getId(), "student1");
            this.grantChannelModeratorRole(channel.getId(), "student1");
        }
        var postfix = shouldGrant ? "/grant-channel-moderator" : "/revoke-channel-moderator";

        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, List.of(testPrefix + "student1", testPrefix + "student2"),
                HttpStatus.OK);
        if (shouldGrant) {
            assertUsersAreChannelModerators(channel.getId(), "student1", "student2");
        }
        else {
            assertUserAreNotChannelModerators(channel.getId(), "student1", "student2");
        }
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE);
        resetWebsocketMock();
    }

    private void expectArchivalChangeForbidden(ChannelDTO channel, boolean isPublicChannel, boolean shouldArchive) throws Exception {
        // prepare channel in db
        if (shouldArchive) {
            this.unArchiveChannel(channel.getId());
        }
        else {
            this.archiveChannel(channel.getId());
        }
        var postfix = shouldArchive ? "/archive" : "/unarchive";
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        this.assertChannelProperties(channel.getId(), channel.getName(), channel.getTopic(), channel.getDescription(), isPublicChannel, !shouldArchive);
        verifyNoParticipantTopicWebsocketSent();
        resetWebsocketMock();
    }

    private void expectGrantRevokeChannelModeratorRoleForbidden(ChannelDTO channel, boolean shouldGrant) throws Exception {
        // prepare channel in db
        var postfix = shouldGrant ? "/grant-channel-moderator" : "/revoke-channel-moderator";
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, List.of(testPrefix + "student1", testPrefix + "student2"),
                HttpStatus.FORBIDDEN);
    }

    private void expectRegisterDeregisterForbidden(ChannelDTO channel, boolean shouldRegister) throws Exception {
        // prepare channel in db
        var postfix = shouldRegister ? "/register" : "/deregister";
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, List.of(testPrefix + "student1", testPrefix + "student2"),
                HttpStatus.FORBIDDEN);
    }

    private void expectCreateBadRequest(ChannelDTO channelDTO) throws Exception {
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/channels", channelDTO, ChannelDTO.class, HttpStatus.BAD_REQUEST);
    }

    private void expectCreateForbidden(ChannelDTO channelDTO) throws Exception {
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/channels", channelDTO, ChannelDTO.class, HttpStatus.FORBIDDEN);
    }

    private void expectDeleteForbidden(Long channelId) throws Exception {
        request.delete("/api/courses/" + exampleCourseId + "/channels/" + channelId, HttpStatus.FORBIDDEN);
    }

    private void expectUpdateForbidden(Long channelId, ChannelDTO updateDTO) throws Exception {
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channelId, updateDTO, ChannelDTO.class, HttpStatus.FORBIDDEN);
    }

    private void assertChannelProperties(Long channelId, String name, String topic, String description, Boolean isPublic, Boolean isArchived) {
        var channel = channelRepository.findById(channelId).orElseThrow();
        assertThat(channel.getName()).isEqualTo(name);
        assertThat(channel.getTopic()).isEqualTo(topic);
        assertThat(channel.getDescription()).isEqualTo(description);
        assertThat(channel.getIsPublic()).isEqualTo(isPublic);
        assertThat(channel.getIsArchived()).isEqualTo(isArchived);
    }

}
