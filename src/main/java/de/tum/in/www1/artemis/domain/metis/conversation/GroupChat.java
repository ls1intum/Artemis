package de.tum.in.www1.artemis.domain.metis.conversation;

import static de.tum.in.www1.artemis.domain.metis.conversation.ConversationSettings.MAX_GROUP_CHAT_PARTICIPANTS;

import java.util.Set;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;

@Entity
@DiscriminatorValue("G")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GroupChat extends Conversation {

    @Override
    public void setConversationParticipants(Set<ConversationParticipant> conversationParticipant) {
        if (conversationParticipant.size() > MAX_GROUP_CHAT_PARTICIPANTS) {
            throw new IllegalArgumentException("Group chats can only have max 10 participants");
        }
        super.setConversationParticipants(conversationParticipant);
    }
}
