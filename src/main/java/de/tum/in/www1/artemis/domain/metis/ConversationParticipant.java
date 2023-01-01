package de.tum.in.www1.artemis.domain.metis;

import java.time.ZonedDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;

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
     * Currently only used for {@link de.tum.in.www1.artemis.domain.metis.conversation.Channel}
     */
    @Column(name = "is_moderator")
    private Boolean isModerator;

    @Column(name = "is_favorite")
    private Boolean isFavorite;

    @Column(name = "is_hidden")
    private Boolean isHidden;

    @Column(name = "last_read")
    private ZonedDateTime lastRead;

    @Column(name = "unread_messages_count")
    private Long unreadMessagesCount;

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
}
