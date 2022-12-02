package de.tum.in.www1.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ChannelDTO;
import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ConversationDTO;

public class ConversationIntegrationTest extends AbstractConversationTest {

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getConversationsOfUser_shouldReturnConversationsWhereMember() throws Exception {
        // given
        var channel = createChannel(false);
        addUsersToConversation(channel.getId(), "tutor1");
        var groupChat = createGroupChat("tutor1");
        var oneToOneChat = createAndPostInOneToOneChat("tutor1");

        // then
        database.changeUser("tutor1");
        var convOfUsers = request.getList("/api/courses/" + exampleCourseId + "/conversations", HttpStatus.OK, ConversationDTO.class);
        assertThat(convOfUsers).hasSize(3);
        assertThat(convOfUsers).extracting(ConversationDTO::getId).containsExactlyInAnyOrder(channel.getId(), groupChat.getId(), oneToOneChat.getId());
        for (var conv : convOfUsers) {
            assertConversationDTOTransientProperties(conv, false, false, false, true, false, false);
        }
    }

    private void assertConversationDTOTransientProperties(ConversationDTO conversationDTO, Boolean isFavorite, Boolean isHidden, Boolean isCreator, Boolean isMember,
            Boolean hasChannelAdminRights, Boolean isChannelAdmin) {
        assertThat(conversationDTO.getIsFavorite()).isEqualTo(isFavorite);
        assertThat(conversationDTO.getIsHidden()).isEqualTo(isHidden);
        assertThat(conversationDTO.getIsCreator()).isEqualTo(isCreator);
        assertThat(conversationDTO.getIsMember()).isEqualTo(isMember);

        if (conversationDTO instanceof ChannelDTO) {
            assertThat(((ChannelDTO) conversationDTO).getHasChannelAdminRights()).isEqualTo(hasChannelAdminRights);
            assertThat(((ChannelDTO) conversationDTO).getIsChannelAdmin()).isEqualTo(isChannelAdmin);
        }
    }
}
