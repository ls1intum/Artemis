package de.tum.in.www1.artemis.domain.metis.conversation;

import static de.tum.in.www1.artemis.domain.metis.conversation.ConversationSettings.MAX_ONE_TO_ONE_CHAT_PARTICIPANTS;

import java.util.Set;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;

@Entity
@DiscriminatorValue("O")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OneToOneChat extends Conversation {

    @Override
    public void setConversationParticipants(Set<ConversationParticipant> conversationParticipant) {
        if (conversationParticipant.size() > MAX_ONE_TO_ONE_CHAT_PARTICIPANTS) {
            throw new IllegalArgumentException("OneToOneChat can only have max two participants");
        }
        super.setConversationParticipants(conversationParticipant);
    }
}
