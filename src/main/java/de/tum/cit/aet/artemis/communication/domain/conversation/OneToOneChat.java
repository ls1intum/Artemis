package de.tum.cit.aet.artemis.communication.domain.conversation;

import static de.tum.cit.aet.artemis.communication.ConversationSettings.MAX_ONE_TO_ONE_CHAT_PARTICIPANTS;

import java.time.ZonedDateTime;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.course.domain.Course;

@Entity
@DiscriminatorValue("O")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OneToOneChat extends Conversation {

    /**
     * Identifies the pair of users of the chat, see {@link #pairKeyOf(long, long)}. Together with the course it is unique, so the database refuses a second chat of the same
     * pair, however many requests start one at the same time. It is null for a chat whose other user was deleted.
     */
    @Column(name = "participant_pair_key", length = 40)
    @JsonIgnore
    private String participantPairKey;

    public OneToOneChat(Long id, User creator, Set<ConversationParticipant> conversationParticipants, Set<Post> posts, Course course, ZonedDateTime creationDate,
            ZonedDateTime lastMessageDate) {
        super(id, creator, conversationParticipants, posts, course, creationDate, lastMessageDate);
    }

    public OneToOneChat() {
    }

    /**
     * Builds the key of the pair of two users. It is the same whichever user comes first.
     *
     * @param userIdA the id of the first user
     * @param userIdB the id of the second user
     * @return the lower user id and the higher user id, separated by a dash
     */
    public static String pairKeyOf(long userIdA, long userIdB) {
        return Math.min(userIdA, userIdB) + "-" + Math.max(userIdA, userIdB);
    }

    public String getParticipantPairKey() {
        return participantPairKey;
    }

    public void setParticipantPairKey(String participantPairKey) {
        this.participantPairKey = participantPairKey;
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
