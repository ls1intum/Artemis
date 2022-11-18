package de.tum.in.www1.artemis.domain.metis;

import java.time.ZonedDateTime;
import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.User;

/**
 * A METIS Posting.
 */
@MappedSuperclass
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(value = { "author" }, allowGetters = true) // author field is not deserialized
public abstract class Posting extends DomainObject {

    @ManyToOne
    // Avoid to leak too much information, only the name (for display) and the id (for comparison) is needed)
    @JsonIncludeProperties({ "id", "name" })
    private User author;

    @CreatedDate
    @Column(name = "creation_date", updatable = false)
    private ZonedDateTime creationDate = ZonedDateTime.now();

    // TODO: in the future we should allow longer posts with more than 1000 characters
    @Size(max = 1000)
    @Column(name = "content", length = 1000)
    private String content;

    // To be used as soon as more advanced strategies for post similarity comparisons are developed
    @Size(max = 1000)
    @Column(name = "tokenized_content", length = 1000)
    private String tokenizedContent;

    @Transient
    private UserRole authorRoleTransient;

    public String getTokenizedContent() {
        return tokenizedContent;
    }

    public void setTokenizedContent(String tokenizedContent) {
        this.tokenizedContent = tokenizedContent;
    }

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
    public abstract Course getCoursePostingBelongsTo();
}
