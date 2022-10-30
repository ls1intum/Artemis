package de.tum.in.www1.artemis.domain.metis;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;

@Entity
@Table(name = "conversation")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Conversation extends DomainObject {

    // === START ADDED BY STEFAN ===

    /**
     * Note: Only for type channel {@link ConversationType#CHANNEL}
     */
    @Column(name = "name")
    @Size(min = 1, max = 20)
    @Nullable
    private String name;

    /**
     * Note: Default value is {@link ConversationType#DIRECT}
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    @NotNull
    private ConversationType type;

    // ToDo: Add properties concerning authorization like owner and how to make channels private / public
    // ToDo: Something like "invite_only"
    // ToDo: Add Description

    // === END ADDED BY STEFAN ===

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<ConversationParticipant> conversationParticipants = new HashSet<>();

    @ManyToOne
    @JsonIgnore
    private Course course;

    @CreatedDate
    @Column(name = "creation_date", updatable = false)
    private ZonedDateTime creationDate = ZonedDateTime.now();

    @Column(name = "last_message_date")
    private ZonedDateTime lastMessageDate;

    public Conversation() {
    }

    public Set<ConversationParticipant> getConversationParticipants() {
        return conversationParticipants;
    }

    public void setConversationParticipants(Set<ConversationParticipant> conversationParticipant) {
        this.conversationParticipants = conversationParticipant;
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

    @Nullable
    public String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public ConversationType getType() {
        return type;
    }

    public void setType(ConversationType type) {
        this.type = type;
    }
}
