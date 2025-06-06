package de.tum.cit.aet.artemis.communication.domain;

import java.time.ZonedDateTime;
import java.util.Set;

import jakarta.annotation.Nullable;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Base class for messages {@link Post} and answer messages {@link AnswerPost} in the communication system.
 */
@MappedSuperclass
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(value = { "author" }, allowGetters = true) // author field is not deserialized
public abstract class Posting extends DomainObject {

    @ManyToOne
    // Avoid to leak too much information, only the name + image (for display) and the id (for comparison) is needed)
    @JsonIncludeProperties({ "id", "name", "imageUrl" })
    private User author;

    @CreatedDate
    @Column(name = "creation_date", updatable = false)
    private ZonedDateTime creationDate = ZonedDateTime.now();

    /**
     * Holds the time when the content of this posting has been updated the last time, or null if the content has never been updated.
     */
    @Column(name = "updated_date")
    private ZonedDateTime updatedDate = null;

    // Note: this number should be the same as in posting-create-edit.directive.ts
    @Size(max = 5000)
    @Column(name = "content", length = 5000)
    private String content;

    @Transient
    private UserRole authorRoleTransient;

    @JsonProperty
    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public ZonedDateTime getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(ZonedDateTime updatedDate) {
        this.updatedDate = updatedDate;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public UserRole getAuthorRole() {
        return authorRoleTransient;
    }

    public void setAuthorRole(UserRole authorRole) {
        this.authorRoleTransient = authorRole;
    }

    public abstract Set<Reaction> getReactions();

    public abstract void setReactions(Set<Reaction> reactions);

    public abstract void addReaction(Reaction reaction);

    public abstract void removeReaction(Reaction reaction);

    @Transient
    @Nullable
    public abstract Course getCoursePostingBelongsTo();

    public abstract Conversation getConversation();
}
