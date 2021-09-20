package de.tum.in.www1.artemis.domain.metis;

import java.time.ZonedDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.User;

/**
 * A Reaction on a Posting.
 */
@Entity
@ReactionConstraints
@Table(name = "reaction")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Reaction extends DomainObject {

    @ManyToOne
    private User user;

    @Column(name = "creation_date")
    private ZonedDateTime creationDate = ZonedDateTime.now();

    /**
     * Unique, identifying name of the emoji, such as "smiley"
     */
    @Column(name = "emoji_id")
    private String emojiId;

    @ManyToOne
    @JsonIncludeProperties({ "id" })
    private Post post;

    @ManyToOne
    @JsonIncludeProperties({ "id" })
    private AnswerPost answerPost;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public String getEmojiId() {
        return emojiId;
    }

    public void setEmojiId(String content) {
        this.emojiId = content;
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public AnswerPost getAnswerPost() {
        return answerPost;
    }

    public void setAnswerPost(AnswerPost answerPost) {
        this.answerPost = answerPost;
    }

    @Override
    public String toString() {
        return "Reaction{" + "id=" + getId() + ", emojiId='" + getEmojiId() + "'" + ", creationDate='" + getCreationDate() + "'" + "'" + "}";
    }
}
