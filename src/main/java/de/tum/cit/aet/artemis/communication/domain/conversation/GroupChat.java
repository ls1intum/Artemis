package de.tum.cit.aet.artemis.communication.domain.conversation;

import static de.tum.cit.aet.artemis.communication.ConversationSettings.MAX_GROUP_CHAT_PARTICIPANTS;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.Size;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;

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

    public GroupChat(Long id, User creator, Set<ConversationParticipant> conversationParticipants, Set<Post> posts, Course course, ZonedDateTime creationDate,
            ZonedDateTime lastMessageDate, String name) {
        super(id, creator, conversationParticipants, posts, course, creationDate, lastMessageDate);
        this.name = StringUtils.isBlank(name) ? generateName() : name;
    }

    public GroupChat() {
    }

    /**
     * Generates a name for the group chat.
     *
     * @return the generated name
     */
    public String generateName() {
        String generatedName = getConversationParticipants().stream().map((participant) -> participant.getUser().getName()).collect(Collectors.joining(", "));

        // The name should be human-readable, so we limit it for very long lists and add "…" to hint that the string is not complete.
        if (generatedName.length() >= Constants.GROUP_CONVERSATION_HUMAN_READABLE_NAME_LIMIT) {
            generatedName = generatedName.substring(0, Constants.GROUP_CONVERSATION_HUMAN_READABLE_NAME_LIMIT) + "…";
        }
        return generatedName;
    }

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
        return getName();
    }

    @Override
    public Conversation copy() {
        return new GroupChat(getId(), getCreator(), getConversationParticipants(), getPosts(), getCourse(), getCreationDate(), getLastMessageDate(), getName());
    }
}
