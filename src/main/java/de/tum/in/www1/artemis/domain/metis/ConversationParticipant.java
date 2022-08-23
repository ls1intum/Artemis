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

    @Column(name = "last_read")
    private ZonedDateTime lastRead;

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
}
