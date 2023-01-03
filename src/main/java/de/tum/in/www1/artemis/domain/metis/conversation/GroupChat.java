package de.tum.in.www1.artemis.domain.metis.conversation;

import static de.tum.in.www1.artemis.domain.metis.conversation.ConversationSettings.MAX_GROUP_CHAT_PARTICIPANTS;

import java.util.Set;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;

@Entity
@DiscriminatorValue("G")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GroupChat extends Conversation {

    /**
     * The name of the group chat. Does not have to be unique in the course.
     */
    @Column(name = "name")
    @Size(min = 1, max = 20)
    private String name;

    @Override
    public void setConversationParticipants(Set<ConversationParticipant> conversationParticipant) {
        if (conversationParticipant.size() > MAX_GROUP_CHAT_PARTICIPANTS) {
            throw new IllegalArgumentException("Group chats can only have max 10 participants");
        }
        super.setConversationParticipants(conversationParticipant);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
