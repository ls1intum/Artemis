package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;

class ChannelIntegrationTest extends AbstractConversationTest {

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createChannel_asInstructor_shouldCreateChannel(boolean isPublicChannel) throws Exception {
        // given
        var channelDTO = new ChannelDTO();
        channelDTO.setName("general");
        channelDTO.setIsPublic(isPublicChannel);
        channelDTO.setDescription("general channel");

        // when
        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/channels", channelDTO, ChannelDTO.class, HttpStatus.CREATED);
        // then
        this.assertChannelProperties(chat.getId(), channelDTO.getName(), null, channelDTO.getDescription(), channelDTO.getIsPublic(), false);
        var participants = assertParticipants(chat.getId(), 1, "instructor1");
        assertThat(participants.stream().findFirst().get().getIsAdmin()).isTrue();
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.CREATE, chat.getId(), "instructor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.CREATE);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createChannel_nameInvalid_shouldReturnBadRequest(boolean isPublicChannel) throws Exception {
        // given
        createChannel(isPublicChannel);

        var channelDTO = new ChannelDTO();
        channelDTO.setIsPublic(isPublicChannel);
        channelDTO.setName("newname");
        channelDTO.setDescription("general channel");

        // when
        // duplicated name
        channelDTO.setName("general");
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
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "student1", roles = "USER")
    void createChannel_asNonCourseInstructor_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channelDTO = new ChannelDTO();
        channelDTO.setName("general");
        channelDTO.setIsPublic(isPublicChannel);
        channelDTO.setDescription("general channel");

        // then
        expectCreateForbidden(channelDTO);
        database.changeUser("tutor1");
        expectCreateForbidden(channelDTO);
        database.changeUser("editor1");
        expectCreateForbidden(channelDTO);
        database.changeUser("instructor42");
        expectCreateForbidden(channelDTO);

        verifyNoParticipantTopicWebsocketSent();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteChannel_asInstructor_shouldDeleteChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        // when
        request.delete("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), HttpStatus.OK);
        // then
        assertThat(channelRepository.findById(channel.getId())).isEmpty();
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.DELETE, channel.getId(), "instructor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.DELETE);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteChannel_asNonCourseInstructor_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        addUserAsChannelAdmin(channel, "tutor2");

        // then
        database.changeUser("student1");
        expectDeleteForbidden(channel.getId());
        database.changeUser("tutor1");
        expectDeleteForbidden(channel.getId());
        database.changeUser("tutor2");
        expectDeleteForbidden(channel.getId());
        database.changeUser("editor1");
        expectDeleteForbidden(channel.getId());
        database.changeUser("instructor42");
        expectDeleteForbidden(channel.getId());

        verifyNoParticipantTopicWebsocketSent();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateChannel_asUserWithChannelAdminRights_shouldUpdateChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        var updateDTO = new ChannelDTO();
        updateDTO.setName("newname");
        updateDTO.setDescription("new description");
        updateDTO.setTopic("new topic");
        addUserAsChannelAdmin(channel, "tutor1");

        // then
        // every instructor automatically has admin rights for every channel
        database.changeUser("instructor2");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), updateDTO, ChannelDTO.class, HttpStatus.OK);
        this.assertChannelProperties(channel.getId(), updateDTO.getName(), updateDTO.getTopic(), updateDTO.getDescription(), isPublicChannel, false);
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE);
        resetWebsocketMock();

        // channel admins can also update the channel
        updateDTO.setName("newname2");
        updateDTO.setDescription("new description2");
        updateDTO.setTopic("new topic2");
        database.changeUser("tutor1");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), updateDTO, ChannelDTO.class, HttpStatus.OK);
        this.assertChannelProperties(channel.getId(), updateDTO.getName(), updateDTO.getTopic(), updateDTO.getDescription(), isPublicChannel, false);
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateChannel_onArchivedChannel_shouldReturnOk(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        archiveChannel(channel.getId());

        var updateDTO = new ChannelDTO();
        updateDTO.setName("newname");
        updateDTO.setDescription("new description");
        updateDTO.setTopic("new topic");
        addUserAsChannelAdmin(channel, "tutor1");

        // then
        database.changeUser("instructor2");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId(), updateDTO, ChannelDTO.class, HttpStatus.OK);
        this.assertChannelProperties(channel.getId(), updateDTO.getName(), updateDTO.getTopic(), updateDTO.getDescription(), isPublicChannel, true);
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1", "tutor1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateChannel_asUserWithoutChannelAdminRights_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        var updateDTO = new ChannelDTO();
        updateDTO.setName("newname");
        updateDTO.setDescription("new description");
        updateDTO.setTopic("new topic");

        // then
        database.changeUser("student1");
        expectUpdateForbidden(channel.getId(), updateDTO);
        database.changeUser("tutor1");
        expectUpdateForbidden(channel.getId(), updateDTO);
        database.changeUser("editor1");
        expectUpdateForbidden(channel.getId(), updateDTO);
        database.changeUser("instructor42");
        expectUpdateForbidden(channel.getId(), updateDTO);

        verifyNoParticipantTopicWebsocketSent();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void archiveAndUnarchiveChannel_asUserWithoutChannelAdminRights_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);

        // then
        database.changeUser("student1");
        expectArchivalChangeForbidden(channel, isPublicChannel, true);
        expectArchivalChangeForbidden(channel, isPublicChannel, false);
        database.changeUser("tutor1");
        expectArchivalChangeForbidden(channel, isPublicChannel, true);
        expectArchivalChangeForbidden(channel, isPublicChannel, false);
        database.changeUser("editor1");
        expectArchivalChangeForbidden(channel, isPublicChannel, true);
        expectArchivalChangeForbidden(channel, isPublicChannel, false);
        database.changeUser("instructor42");
        expectArchivalChangeForbidden(channel, isPublicChannel, true);
        expectArchivalChangeForbidden(channel, isPublicChannel, false);

        verifyNoParticipantTopicWebsocketSent();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void archiveAndUnarchiveChannel_asUserWithChannelAdminRights_shouldArchiveChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        addUserAsChannelAdmin(channel, "tutor1");

        // then
        // every instructor automatically has admin rights for every channel
        database.changeUser("instructor2");
        testArchivalChangeWorks(channel, isPublicChannel, true);
        testArchivalChangeWorks(channel, isPublicChannel, false);

        // channel admins can also update the channel
        database.changeUser("tutor1");
        testArchivalChangeWorks(channel, isPublicChannel, true);
        testArchivalChangeWorks(channel, isPublicChannel, false);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void grantRevokeChannelAdminRights_asUserWithChannelAdminRights_shouldGrantRevokeChannelAdmin(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        addUserAsChannelAdmin(channel, "tutor1");
        addUserToChannel(channel, "student1");
        addUserToChannel(channel, "student2");

        // then
        // every instructor automatically has admin rights for every channel
        database.changeUser("instructor2");
        testGrantRevokeChannelAdminRightsWorks(channel, true);
        testGrantRevokeChannelAdminRightsWorks(channel, false);

        // channel admins can also grand and revoke channel admin rights
        database.changeUser("tutor1");
        testGrantRevokeChannelAdminRightsWorks(channel, true);
        testGrantRevokeChannelAdminRightsWorks(channel, false);

        // note: you can NOT revoke channel admin rights of the creator to guarantee that there is always at least one channel admin
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/revoke-channel-admin", List.of("instructor1"),
                HttpStatus.BAD_REQUEST);
        assertUsersAreChannelAdmin(channel.getId(), "instructor1");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void grantRevokeChannelAdminRights_asUserWithoutChannelAdminRights_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);

        // then
        database.changeUser("student1");
        expectGrantRevokeChannelAdminRightsForbidden(channel, true);
        expectGrantRevokeChannelAdminRightsForbidden(channel, false);
        database.changeUser("tutor1");
        expectGrantRevokeChannelAdminRightsForbidden(channel, true);
        expectGrantRevokeChannelAdminRightsForbidden(channel, false);
        database.changeUser("editor1");
        expectGrantRevokeChannelAdminRightsForbidden(channel, true);
        expectGrantRevokeChannelAdminRightsForbidden(channel, false);
        database.changeUser("instructor42");
        expectGrantRevokeChannelAdminRightsForbidden(channel, true);
        expectGrantRevokeChannelAdminRightsForbidden(channel, false);
        verifyNoParticipantTopicWebsocketSent();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void registerUsersToChannel_asUserWithChannelAdminRights_shouldRegisterUsersToChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        addUserAsChannelAdmin(channel, "tutor1");

        // then
        // every instructor automatically has admin rights for every channel
        database.changeUser("instructor2");
        testRegisterAndDeregisterUserWorks(channel, true);
        testRegisterAndDeregisterUserWorks(channel, false);

        // channel admins can also grant and revoke channel admin rights
        database.changeUser("tutor1");
        testRegisterAndDeregisterUserWorks(channel, true);
        testRegisterAndDeregisterUserWorks(channel, false);

        removeUsersFromConversation(channel.getId(), "student1", "student2", "tutor1");
        database.changeUser("instructor1");
        var params = new LinkedMultiValueMap<String, String>();
        params.add("addAllStudents", String.valueOf(true));
        params.add("addAllTutors", String.valueOf(true));
        params.add("addAllEditors", String.valueOf(true));
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
        assertUsersAreConversationMembers(channel.getId(), allUserLoginsArray);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void registerUsersToChannel_asUserWithoutChannelAdminRights_shouldReturnForbidden(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);

        // then
        database.changeUser("student1");
        expectRegisterDeregisterForbidden(channel, true);
        expectRegisterDeregisterForbidden(channel, false);
        database.changeUser("tutor1");
        expectRegisterDeregisterForbidden(channel, true);
        expectRegisterDeregisterForbidden(channel, false);
        database.changeUser("editor1");
        expectRegisterDeregisterForbidden(channel, true);
        expectRegisterDeregisterForbidden(channel, false);
        database.changeUser("instructor42");
        expectRegisterDeregisterForbidden(channel, true);
        expectRegisterDeregisterForbidden(channel, false);
        verifyNoParticipantTopicWebsocketSent();
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void leaveChannel_asNormalUser_canLeaveChannel(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        addUsersToConversation(channel.getId(), "student1");

        // then
        database.changeUser("student1");
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/deregister", List.of("student1"), HttpStatus.OK);
        assertUserAreNotConversationMembers(channel.getId(), "student1");
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1");
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.DELETE, channel.getId(), "student1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE, MetisCrudAction.DELETE);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void leaveChannel_asCreator_shouldReturnBadRequest(boolean isPublicChannel) throws Exception {
        // given
        var channel = createChannel(isPublicChannel);
        // then
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/deregister", List.of("instructor1"), HttpStatus.BAD_REQUEST);
        assertUsersAreConversationMembers(channel.getId(), "instructor1");
        verifyNoParticipantTopicWebsocketSent();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void joinPublicChannel_asNormalUser_canJoinPublicChannel() throws Exception {
        // given
        var channel = createChannel(true);

        // then
        database.changeUser("student1");
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/register", List.of("student1"), HttpStatus.OK);
        assertUsersAreConversationMembers(channel.getId(), "student1");
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.UPDATE, channel.getId(), "instructor1");
        verifyMultipleParticipantTopicWebsocketSent(MetisCrudAction.CREATE, channel.getId(), "student1");
        verifyNoParticipantTopicWebsocketSentExceptAction(MetisCrudAction.UPDATE, MetisCrudAction.CREATE);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void joinPrivateChannel_asNormalUser_canNotJoinPrivateChannel() throws Exception {
        // given
        var channel = createChannel(false);

        // then
        database.changeUser("student1");
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/register", List.of("student1"), HttpStatus.FORBIDDEN);
        assertUserAreNotConversationMembers(channel.getId(), "student1");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void joinPrivateChannel_asInstructor_canJoinPrivateChannel() throws Exception {
        // given
        var channel = createChannel(false);

        // then
        database.changeUser("instructor2");
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + "/register", List.of("instructor2"), HttpStatus.OK);
        assertUsersAreConversationMembers(channel.getId(), "instructor2");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getCourseChannelsOverview_asNormalUser_canSeeAllPublicChannelsAndPrivateChannelsWhereMember() throws Exception {
        // given
        var publicChannelWhereMember = createChannel(true, "public1");
        addUsersToConversation(publicChannelWhereMember.getId(), "student1");
        var publicChannelWhereNotMember = createChannel(true, "public2");
        var privateChannelWhereMember = createChannel(false, "private1");
        addUsersToConversation(privateChannelWhereMember.getId(), "student1");
        createChannel(false, "private2");

        // then
        database.changeUser("student1");
        var channnels = request.getList("/api/courses/" + exampleCourseId + "/channels/overview", HttpStatus.OK, ChannelDTO.class);
        assertThat(channnels).hasSize(3);
        assertThat(channnels.stream().map(ChannelDTO::getId).collect(Collectors.toList())).containsExactlyInAnyOrder(publicChannelWhereMember.getId(),
                publicChannelWhereNotMember.getId(), privateChannelWhereMember.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getCourseChannelsOverview_asCourseInstructor_canSeeAllPublicChannelsAndAllPrivateChannels() throws Exception {
        // given
        var publicChannelWhereMember = createChannel(true, "public1");
        addUsersToConversation(publicChannelWhereMember.getId(), "student1");
        var publicChannelWhereNotMember = createChannel(true, "public2");
        var privateChannelWhereMember = createChannel(false, "private1");
        addUsersToConversation(privateChannelWhereMember.getId(), "student1");
        var privateChannelWhereNotMember = createChannel(false, "private2");

        // then
        database.changeUser("instructor2");
        var channnels = request.getList("/api/courses/" + exampleCourseId + "/channels/overview", HttpStatus.OK, ChannelDTO.class);
        assertThat(channnels).hasSize(4);
        assertThat(channnels.stream().map(ChannelDTO::getId).collect(Collectors.toList())).containsExactlyInAnyOrder(publicChannelWhereMember.getId(),
                publicChannelWhereNotMember.getId(), privateChannelWhereMember.getId(), privateChannelWhereNotMember.getId());
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

        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, List.of("student1", "student2"), HttpStatus.OK);
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

    private void testGrantRevokeChannelAdminRightsWorks(ChannelDTO channel, boolean shouldGrant) throws Exception {
        // prepare channel in db
        if (shouldGrant) {
            this.revokeChannelAdminRights(channel.getId(), "student1");
            this.revokeChannelAdminRights(channel.getId(), "student1");
        }
        else {
            this.grantChannelAdminRights(channel.getId(), "student1");
            this.grantChannelAdminRights(channel.getId(), "student1");
        }
        var postfix = shouldGrant ? "/grant-channel-admin" : "/revoke-channel-admin";

        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, List.of("student1", "student2"), HttpStatus.OK);
        if (shouldGrant) {
            assertUsersAreChannelAdmin(channel.getId(), "student1", "student2");
        }
        else {
            assertUserAreNotChannelAdmin(channel.getId(), "student1", "student2");
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

    private void addUserAsChannelAdmin(ChannelDTO channel, String login) {
        var newAdmin = userRepository.findOneByLogin(login).get();
        var adminParticipant = new ConversationParticipant();
        adminParticipant.setIsAdmin(true);
        adminParticipant.setUser(newAdmin);
        adminParticipant.setConversation(this.channelRepository.findById(channel.getId()).get());
        conversationParticipantRepository.save(adminParticipant);
    }

    private void addUserToChannel(ChannelDTO channel, String login) {
        var newParticipant = userRepository.findOneByLogin(login).get();
        var participant = new ConversationParticipant();
        participant.setIsAdmin(false);
        participant.setUser(newParticipant);
        participant.setConversation(this.channelRepository.findById(channel.getId()).get());
        conversationParticipantRepository.save(participant);
    }

    private void expectGrantRevokeChannelAdminRightsForbidden(ChannelDTO channel, boolean shouldGrant) throws Exception {
        // prepare channel in db
        var postfix = shouldGrant ? "/grant-channel-admin" : "/revoke-channel-admin";
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, List.of("student1", "student2"), HttpStatus.FORBIDDEN);
    }

    private void expectRegisterDeregisterForbidden(ChannelDTO channel, boolean shouldRegister) throws Exception {
        // prepare channel in db
        var postfix = shouldRegister ? "/register" : "/deregister";
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/channels/" + channel.getId() + postfix, List.of("student1", "student2"), HttpStatus.FORBIDDEN);
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

    private void archiveChannel(Long channelId) {
        var dbChannel = channelRepository.findById(channelId).get();
        dbChannel.setIsArchived(true);
        channelRepository.save(dbChannel);
    }

    private void unArchiveChannel(Long channelId) {
        var dbChannel = channelRepository.findById(channelId).get();
        dbChannel.setIsArchived(false);
        channelRepository.save(dbChannel);
    }

    private void revokeChannelAdminRights(Long channelId, String userLogin) {
        var user = userRepository.findOneByLogin(userLogin).get();
        var participant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channelId, user.getId()).get();
        participant.setIsAdmin(false);
        conversationParticipantRepository.save(participant);
    }

    private void grantChannelAdminRights(Long channelId, String userLogin) {
        var user = userRepository.findOneByLogin(userLogin).get();
        var participant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channelId, user.getId()).get();
        participant.setIsAdmin(true);
        conversationParticipantRepository.save(participant);
    }

    private ChannelDTO createChannel(boolean isPublicChannel) throws Exception {
        return createChannel(isPublicChannel, "general");
    }

    private ChannelDTO createChannel(boolean isPublicChannel, String name) throws Exception {
        var channelDTO = new ChannelDTO();
        channelDTO.setName(name);
        channelDTO.setIsPublic(isPublicChannel);
        channelDTO.setDescription("general channel");

        var chat = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/channels", channelDTO, ChannelDTO.class, HttpStatus.CREATED);
        resetWebsocketMock();
        return chat;
    }

    private void assertChannelProperties(Long channelId, String name, String topic, String description, Boolean isPublic, Boolean isArchived) {
        var channel = channelRepository.findById(channelId).orElseThrow();
        assertThat(channel.getName()).isEqualTo(name);
        assertThat(channel.getTopic()).isEqualTo(topic);
        assertThat(channel.getDescription()).isEqualTo(description);
        assertThat(channel.getIsPublic()).isEqualTo(isPublic);
        assertThat(channel.getIsArchived()).isEqualTo(isArchived);
    }

    private void assertUsersAreChannelAdmin(Long channelId, String... userLogin) {
        var channelAdmins = getParticipants(channelId).stream().filter(ConversationParticipant::getIsAdmin).map(ConversationParticipant::getUser);
        assertThat(channelAdmins).extracting(User::getLogin).contains(userLogin);
    }

    private void assertUserAreNotChannelAdmin(Long channelId, String... userLogin) {
        var channelAdmins = getParticipants(channelId).stream().filter(ConversationParticipant::getIsAdmin).map(ConversationParticipant::getUser);
        assertThat(channelAdmins).extracting(User::getLogin).doesNotContain(userLogin);
    }
}
