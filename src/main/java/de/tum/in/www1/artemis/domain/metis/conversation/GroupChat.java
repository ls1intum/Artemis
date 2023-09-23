package de.tum.in.www1.artemis.domain.metis.conversation;

import static de.tum.in.www1.artemis.domain.metis.conversation.ConversationSettings.MAX_GROUP_CHAT_PARTICIPANTS;

import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.validation.constraints.Size;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.User;
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

    @Override
    public String getHumanReadableNameForReceiver(User sender) {
        if (StringUtils.isBlank(getName())) {
            final String generatedName = getConversationParticipants().stream().map((participant) -> participant.getUser().getName()).collect(Collectors.joining(", "));

            // The name should be human-readable, so we limit it for very long lists and add "…" to hint that the string is not complete.
            if (generatedName.length() >= Constants.GROUP_CONVERSATION_HUMAN_READABLE_NAME_LIMIT) {
                return generatedName.substring(0, Constants.GROUP_CONVERSATION_HUMAN_READABLE_NAME_LIMIT) + "…";
            }
            else {
                return generatedName;
            }
        }
        else {
            return getName();
        }
    }
}
