package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ConversationDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ConversationUserDTO;

class ConversationIntegrationTest extends AbstractConversationTest {

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getConversationsOfUser_shouldReturnConversationsWhereMember() throws Exception {
        // given
        var channel = createChannel(false);
        addUsersToConversation(channel.getId(), "tutor1");
        var groupChat = createGroupChat("tutor1");
        hideConversation(groupChat.getId(), "tutor1");
        var oneToOneChat = createAndPostInOneToOneChat("tutor1");
        favoriteConversation(oneToOneChat.getId(), "tutor1");
        var channel2 = createChannel(false, "channel2");

        // then
        database.changeUser("tutor1");
        var convOfUsers = request.getList("/api/courses/" + exampleCourseId + "/conversations", HttpStatus.OK, ConversationDTO.class);
        assertThat(convOfUsers).hasSize(3); // the channel2 is not returned because the user is not a member
        assertThat(convOfUsers).extracting(ConversationDTO::getId).containsExactlyInAnyOrder(channel.getId(), groupChat.getId(), oneToOneChat.getId());
        for (var conv : convOfUsers) {
            if (conv.getId().equals(channel.getId())) {
                assertThat(conv.getIsFavorite()).isFalse();
                assertThat(conv.getIsHidden()).isFalse();
            }
            else if (conv.getId().equals(groupChat.getId())) {
                assertThat(conv.getIsFavorite()).isFalse();
                assertThat(conv.getIsHidden()).isTrue();
            }
            else if (conv.getId().equals(oneToOneChat.getId())) {
                assertThat(conv.getIsFavorite()).isTrue();
                assertThat(conv.getIsHidden()).isFalse();
            }
            assertConversationDTOTransientProperties(conv, false, true, false, false);
        }
        grantChannelAdminRights(channel.getId(), "tutor1");
        convOfUsers = request.getList("/api/courses/" + exampleCourseId + "/conversations", HttpStatus.OK, ConversationDTO.class);
        // check that the channel admin rights are correctly set
        convOfUsers.stream().filter(conv -> conv.getId().equals(channel.getId())).findFirst().ifPresent(conv -> assertThat(((ChannelDTO) conv).getIsChannelAdmin()).isTrue());
        // check that creator is correctly set
        database.changeUser("instructor1");
        var convOfInstructor = request.getList("/api/courses/" + exampleCourseId + "/conversations", HttpStatus.OK, ConversationDTO.class);
        // should be creator of all conversations
        assertThat(convOfInstructor).hasSize(4);
        assertThat(convOfInstructor).extracting(ConversationDTO::getId).containsExactlyInAnyOrder(channel.getId(), groupChat.getId(), oneToOneChat.getId(), channel2.getId());
        for (var conv : convOfInstructor) {
            assertThat(conv.getIsFavorite()).isFalse();
            assertThat(conv.getIsHidden()).isFalse();
            assertConversationDTOTransientProperties(conv, true, true, true, true);
        }
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void switchFavoriteStatus_shouldSwitchFavoriteStatus() throws Exception {
        // given
        var channel = createChannel(false);
        addUsersToConversation(channel.getId(), "tutor1");
        // then
        database.changeUser("tutor1");
        var trueParams = new LinkedMultiValueMap<String, String>();
        trueParams.add("isFavorite", String.valueOf(true));
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/favorite", HttpStatus.OK, trueParams);
        this.assertFavoriteStatus(channel.getId(), "tutor1", true);
        var falseParams = new LinkedMultiValueMap<String, String>();
        falseParams.add("isFavorite", String.valueOf(false));
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/favorite", HttpStatus.OK, falseParams);
        this.assertFavoriteStatus(channel.getId(), "tutor1", false);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void switchHiddenStatus_shouldSwitchHiddenStatus() throws Exception {
        // given
        var channel = createChannel(false);
        addUsersToConversation(channel.getId(), "tutor1");
        // then
        database.changeUser("tutor1");
        var trueParams = new LinkedMultiValueMap<String, String>();
        trueParams.add("isHidden", String.valueOf(true));
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/hidden", HttpStatus.OK, trueParams);
        this.assertHiddenStatus(channel.getId(), "tutor1", true);
        var falseParams = new LinkedMultiValueMap<String, String>();
        falseParams.add("isHidden", String.valueOf(false));
        request.postWithoutResponseBody("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/hidden", HttpStatus.OK, falseParams);
        this.assertHiddenStatus(channel.getId(), "tutor1", false);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void searchMembersOfConversation_shouldFindMembersWhereLoginOrNameMatches() throws Exception {
        var channel = createChannel(false);
        // add students 1-10 to the channel
        for (int i = 1; i <= 5; i++) {
            addUsersToConversation(channel.getId(), "student" + i);
        }
        // add editor1
        addUsersToConversation(channel.getId(), "editor1");
        addUsersToConversation(channel.getId(), "tutor1");
        grantChannelAdminRights(channel.getId(), "tutor1");

        // search for students
        database.changeUser("tutor1");
        // <server>/api/courses/:courseId/conversations/:conversationId/members/search?loginOrName=:searchTerm&sort=firstName,asc&sort=lastName,asc&page=0&size=10
        // optional filter attribute to further : filter=INSTRUCTOR or EDITOR or TUTOR or STUDENT or CHANNEL_ADMIN
        var params = new LinkedMultiValueMap<String, String>();
        params.add("loginOrName", "");
        params.add("sort", "firstName,asc");
        params.add("sort", "lastName,asc");
        params.add("page", "0");
        params.add("size", "20");
        var members = request.getList("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search", HttpStatus.OK, ConversationUserDTO.class,
                params);
        assertThat(members).hasSize(8);
        assertThat(members).extracting(ConversationUserDTO::getLogin).containsExactlyInAnyOrder("student1", "student2", "student3", "student4", "student5", "tutor1", "instructor1",
                "editor1");
        // same request but now we only search for editor1
        params.set("loginOrName", "editor1");
        members = request.getList("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search", HttpStatus.OK, ConversationUserDTO.class, params);
        assertThat(members).hasSize(1);
        assertThat(members).extracting(ConversationUserDTO::getLogin).containsExactlyInAnyOrder("editor1");
        params.set("loginOrName", "");
        // same request but now we only search for students
        params.set("filter", "STUDENT");
        members = request.getList("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search", HttpStatus.OK, ConversationUserDTO.class, params);
        assertThat(members).hasSize(5);
        assertThat(members).extracting(ConversationUserDTO::getLogin).containsExactlyInAnyOrder("student1", "student2", "student3", "student4", "student5");
        // same request but now we only search for tutors
        params.set("filter", "TUTOR");
        members = request.getList("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search", HttpStatus.OK, ConversationUserDTO.class, params);
        assertThat(members).hasSize(1);
        assertThat(members).extracting(ConversationUserDTO::getLogin).containsExactlyInAnyOrder("tutor1");
        // same request but now we only search for editors
        params.set("filter", "EDITOR");
        members = request.getList("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search", HttpStatus.OK, ConversationUserDTO.class, params);
        assertThat(members).hasSize(1);
        assertThat(members).extracting(ConversationUserDTO::getLogin).containsExactlyInAnyOrder("editor1");
        // same request but now we only search for instructors
        params.set("filter", "INSTRUCTOR");
        members = request.getList("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search", HttpStatus.OK, ConversationUserDTO.class, params);
        assertThat(members).hasSize(1);
        assertThat(members).extracting(ConversationUserDTO::getLogin).containsExactlyInAnyOrder("instructor1");
        // same request but now we only search for channel admins
        params.set("filter", "CHANNEL_ADMIN");
        members = request.getList("/api/courses/" + exampleCourseId + "/conversations/" + channel.getId() + "/members/search", HttpStatus.OK, ConversationUserDTO.class, params);
        assertThat(members).hasSize(2);
        assertThat(members).extracting(ConversationUserDTO::getLogin).containsExactlyInAnyOrder("tutor1", "instructor1");
    }

    private void assertConversationDTOTransientProperties(ConversationDTO conversationDTO, Boolean isCreator, Boolean isMember, Boolean hasChannelAdminRights,
            Boolean isChannelAdmin) {
        assertThat(conversationDTO.getIsCreator()).isEqualTo(isCreator);
        assertThat(conversationDTO.getIsMember()).isEqualTo(isMember);

        if (conversationDTO instanceof ChannelDTO) {
            assertThat(((ChannelDTO) conversationDTO).getHasChannelAdminRights()).isEqualTo(hasChannelAdminRights);
            assertThat(((ChannelDTO) conversationDTO).getIsChannelAdmin()).isEqualTo(isChannelAdmin);
        }
    }

    private void assertFavoriteStatus(Long channelId, String userLogin, Boolean expectedFavoriteStatus) throws Exception {
        var user = database.getUserByLogin(userLogin);
        var participant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channelId, user.getId());
        assertThat(participant.get().getIsFavorite()).isEqualTo(expectedFavoriteStatus);
    }

    private void assertHiddenStatus(Long channelId, String userLogin, Boolean expectedHiddenStatus) throws Exception {
        var user = database.getUserByLogin(userLogin);
        var participant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channelId, user.getId());
        assertThat(participant.get().getIsHidden()).isEqualTo(expectedHiddenStatus);
    }

}
