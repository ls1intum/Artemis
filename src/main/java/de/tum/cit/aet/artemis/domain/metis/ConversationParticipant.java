package de.tum.cit.aet.artemis.domain.metis;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.metis.conversation.Conversation;

@Entity
@Table(name = "conversation_participant")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ConversationParticipant extends DomainObject {

    @ManyToOne
    @JsonIgnore
    private Conversation conversation;

    @ManyToOne
    @JsonIncludeProperties({ "id", "firstName", "lastName" })
    @NotNull
    private User user;

    /**
     * Currently only used for {@link de.tum.cit.aet.artemis.domain.metis.conversation.Channel}
     */
    @Column(name = "is_moderator")
    private Boolean isModerator;

    @Column(name = "is_favorite")
    private Boolean isFavorite;

    @Column(name = "is_hidden")
    private Boolean isHidden;

    @Column(name = "is_muted")
    private boolean isMuted;

    @Column(name = "last_read")
    private ZonedDateTime lastRead;

    @Column(name = "unread_messages_count")
    private Long unreadMessagesCount;

    /**
     * Creates a ConversationParticipant object for the provided user and conversation. The returned participant is not
     * a moderator, hasn't hidden the conversation, hasn't marked the conversation as favorite, has 0 unread messages
     * and a last read date set to 2 years into the past.
     *
     * @param user         the user for the participant
     * @param conversation the conversation for the participant
     * @return participant with default value
     */
    public static ConversationParticipant createWithDefaultValues(User user, Conversation conversation) {
        ConversationParticipant participant = new ConversationParticipant();
        participant.setUser(user);
        participant.setConversation(conversation);
        participant.setIsModerator(false);
        participant.setIsFavorite(false);
        participant.setIsHidden(false);
        participant.setIsMuted(false);
        // set the last reading time of a participant in the past when creating conversation for the first time!
        participant.setLastRead(ZonedDateTime.now().minusYears(2));
        participant.setUnreadMessagesCount(0L);
        return participant;
    }

    public Long getUnreadMessagesCount() {
        return unreadMessagesCount;
    }

    public void setUnreadMessagesCount(Long unreadMessagesCount) {
        this.unreadMessagesCount = unreadMessagesCount;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ZonedDateTime getLastRead() {
        return lastRead;
    }

    public void setLastRead(ZonedDateTime lastRead) {
        this.lastRead = lastRead;
    }

    public void filterSensitiveInformation() {
        setLastRead(null);
    }

    public Boolean getIsModerator() {
        return isModerator;
    }

    public void setIsModerator(Boolean isModerator) {
        this.isModerator = isModerator;
    }

    public Boolean getIsFavorite() {
        return isFavorite;
    }

    public void setIsFavorite(Boolean favorite) {
        isFavorite = favorite;
    }

    public Boolean getIsHidden() {
        return isHidden;
    }

    public void setIsHidden(Boolean hidden) {
        isHidden = hidden;
    }

    public boolean getIsMuted() {
        return isMuted;
    }

    public void setIsMuted(boolean isMuted) {
        this.isMuted = isMuted;
    }
}
