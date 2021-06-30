package de.tum.in.www1.artemis.domain.metis;

import java.time.ZonedDateTime;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
    private User author;

    @Column(name = "creation_date")
    private ZonedDateTime creationDate;

    @Lob
    @Column(name = "content")
    private String content;

    // To be used with introduction of Metis
    @Lob
    @Column(name = "tokenized_content")
    private String tokenizedContent;

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

    public abstract Course getCourse();

    public abstract Set<Reaction> getReactions();

    public abstract void setReactions(Set<Reaction> reactions);

    public abstract void addReaction(Reaction reaction);
}
