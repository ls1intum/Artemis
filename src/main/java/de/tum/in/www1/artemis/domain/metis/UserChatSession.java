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

@Entity
@Table(name = "user_chat_session")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class UserChatSession extends DomainObject {

    @ManyToOne
    @JsonIgnore
    private ChatSession chatSession;

    @ManyToOne
    @JsonIncludeProperties({ "id", "firstName", "lastName" })
    @NotNull
    private User user;

    @Column(name = "last_read")
    @NotNull
    private ZonedDateTime lastRead;

    @Column(name = "archived")
    private boolean archived;

    @Column(name = "deleted")
    private boolean deleted;

    public ChatSession getChatSession() {
        return chatSession;
    }

    public void setChatSession(ChatSession chatSession) {
        this.chatSession = chatSession;
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

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}
