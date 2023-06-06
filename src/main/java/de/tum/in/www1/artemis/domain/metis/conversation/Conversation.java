package de.tum.in.www1.artemis.domain.metis.conversation;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.Post;

@Entity
@Table(name = "conversation")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("X")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = OneToOneChat.class, name = "oneToOneChat"), @JsonSubTypes.Type(value = GroupChat.class, name = "groupChat"),
        @JsonSubTypes.Type(value = Channel.class, name = "channel"), })
public abstract class Conversation extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "creator_id")
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

    @CreatedDate
    @Column(name = "creation_date", updatable = false)
    private ZonedDateTime creationDate = ZonedDateTime.now();

    @Column(name = "last_message_date")
    private ZonedDateTime lastMessageDate;

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

    public String getHumanReadableName(User sender) {
        if (this instanceof Channel channel) {
            return channel.getName();
        }
        else if (this instanceof GroupChat groupChat) {
            if (groupChat.getName() == null || groupChat.getName().isBlank()) {
                return groupChat.getConversationParticipants().stream().map((x) -> x.getUser().getName()).collect(Collectors.joining());
            }
            else {
                return groupChat.getName();
            }
        }
        else if (this instanceof OneToOneChat) {
            return sender.getName();
        }
        else {
            return "";
        }
    }
}
