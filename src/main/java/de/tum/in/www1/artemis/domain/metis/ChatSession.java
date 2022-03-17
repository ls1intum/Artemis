package de.tum.in.www1.artemis.domain.metis;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "chat_session")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ChatSession extends DomainObject {

    @OneToMany(mappedBy = "chatSession", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<UserChatSession> userChatSessions = new HashSet<>();

    @ManyToOne
    @JsonIncludeProperties({ "id" })
    @NotNull
    private Course course;

    @CreatedDate
    @Column(name = "creation_date", updatable = false)
    private ZonedDateTime creationDate = ZonedDateTime.now();

    @Column(name = "last_message_date", updatable = false)
    private ZonedDateTime lastMessageDate = ZonedDateTime.now();

    public Set<UserChatSession> getUserChatSessions() {
        return userChatSessions;
    }

    public void setUserChatSessions(Set<UserChatSession> userChatSessions) {
        this.userChatSessions = userChatSessions;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public ZonedDateTime getLastMessageDate() {
        return lastMessageDate;
    }

    public void setLastMessageDate(ZonedDateTime lastMessageDate) {
        this.lastMessageDate = lastMessageDate;
    }
}
