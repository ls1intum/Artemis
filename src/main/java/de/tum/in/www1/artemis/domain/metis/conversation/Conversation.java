package de.tum.in.www1.artemis.domain.metis.conversation;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;
import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;

@Entity
@Table(name = "conversation")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("X")
@DiscriminatorOptions(force = true)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = GroupChat.class, name = "groupChat"), @JsonSubTypes.Type(value = Channel.class, name = "channel") })
public abstract class Conversation extends DomainObject {

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

    /**
     * This transient fields is set to the number of participants in this conversation
     */
    @Transient
    @JsonSerialize
    private Integer numberOfMembers;

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

    public Integer getNumberOfMembers() {
        return numberOfMembers;
    }

    public void setNumberOfMembers(Integer numberOfMembers) {
        this.numberOfMembers = numberOfMembers;
    }

}
