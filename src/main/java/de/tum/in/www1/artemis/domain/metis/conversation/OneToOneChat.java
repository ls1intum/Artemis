package de.tum.in.www1.artemis.domain.metis.conversation;

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
        if (conversationParticipant.size() > 2) {
            throw new IllegalArgumentException("OneToOneChat can only have max two participants");
        }
        super.setConversationParticipants(conversationParticipant);
    }
}
