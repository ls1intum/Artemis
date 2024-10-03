package de.tum.cit.aet.artemis.communication.domain.conversation;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.annotation.CreatedDate;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversation")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("X")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = OneToOneChat.class, name = "oneToOneChat"),
    @JsonSubTypes.Type(value = GroupChat.class, name = "groupChat"),
    @JsonSubTypes.Type(value = Channel.class, name = "channel"),
})
// @formatter:on
public abstract class Conversation extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "creator_id")
    @JsonIncludeProperties({"id", "name"})
    private User creator;

    @JsonIgnoreProperties("conversation")
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    private Set<ConversationParticipant> conversationParticipants = new HashSet<>();

    @JsonIgnoreProperties("conversation")
    @OneToMany(mappedBy = "conversation", cascade = CascadeType.REMOVE, fetch = FetchType.LAZY)
    private Set<Post> posts = new HashSet<>();

    @ManyToOne
    @JsonIgnore
    private Course course;

    @ManyToOne
    @JsonIgnore
    private Exercise exercise;

    @CreatedDate
    @Column(name = "creation_date", updatable = false)
    private ZonedDateTime creationDate = ZonedDateTime.now();

    @Column(name = "last_message_date")
    private ZonedDateTime lastMessageDate;

    @Column(name = "name")
    private String name;

    public Conversation(Long id, User creator, Set<ConversationParticipant> conversationParticipants, Set<Post> posts, Course course, ZonedDateTime creationDate,
                        ZonedDateTime lastMessageDate) {
        this.setId(id);
        this.creator = creator;
        this.conversationParticipants = conversationParticipants;
        this.posts = posts;
        this.course = course;
        this.creationDate = creationDate;
        this.lastMessageDate = lastMessageDate;
    }

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

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public User getCreator() {
        return creator;
    }

    public void setCreator(User creator) {
        this.creator = creator;
    }

    public Set<Post> getPosts() {
        return posts;
    }

    public void setPosts(Set<Post> posts) {
        this.posts = posts;
    }

    public abstract Conversation copy();

    /**
     * @param sender the sender of the message
     * @return returns a human-readable name for this conversation, which can be used in notifications or emails.
     */
    public abstract String getHumanReadableNameForReceiver(User sender);

    /**
     * hide the details of the object, can be invoked before sending it as payload in a REST response or websocket message
     */
    public void hideDetails() {
        // the following values are sometimes not needed when sending payloads to the client, so we allow to remove them
        setConversationParticipants(new HashSet<>());
        setPosts(new HashSet<>());
        // TODO: this information is still needed in some places, we need to identify those and then set it to null
        // setCourse(null);
    }
}
