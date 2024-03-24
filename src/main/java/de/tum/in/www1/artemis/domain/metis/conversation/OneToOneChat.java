package de.tum.in.www1.artemis.domain.metis.conversation;

import static de.tum.in.www1.artemis.domain.metis.conversation.ConversationSettings.MAX_ONE_TO_ONE_CHAT_PARTICIPANTS;

import java.time.ZonedDateTime;
import java.util.Set;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.Post;

@Entity
@DiscriminatorValue("O")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OneToOneChat extends Conversation {

    public OneToOneChat(Long id, User creator, Set<ConversationParticipant> conversationParticipants, Set<Post> posts, Course course, ZonedDateTime creationDate,
            ZonedDateTime lastMessageDate) {
        super(id, creator, conversationParticipants, posts, course, creationDate, lastMessageDate);
    }

    public OneToOneChat() {
    }

    @Override
    public void setConversationParticipants(Set<ConversationParticipant> conversationParticipant) {
        if (conversationParticipant.size() > MAX_ONE_TO_ONE_CHAT_PARTICIPANTS) {
            throw new IllegalArgumentException("OneToOneChat can only have max two participants");
        }
        super.setConversationParticipants(conversationParticipant);
    }

    @Override
    public String getHumanReadableNameForReceiver(User sender) {
        return sender.getName();
    }

    @Override
    public Conversation copy() {
        return new OneToOneChat(getId(), getCreator(), getConversationParticipants(), getPosts(), getCourse(), getCreationDate(), getLastMessageDate());
    }
}
