package de.tum.cit.aet.artemis.communication.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.data.annotation.CreatedDate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * A Reaction on a Posting.
 * A reaction has to be unique with regard to user_id emoji_id and id of the posting it belongs to,
 * i.e. every user can only react once on a posting with a certain emoji.
 */
@Entity
@ReactionConstraints
@Table(name = "reaction", uniqueConstraints = { @UniqueConstraint(columnNames = { "emoji_id", "user_id", "post_id" }),
        @UniqueConstraint(columnNames = { "emoji_id", "user_id", "answer_post_id" }) })
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Reaction extends DomainObject {

    @ManyToOne
    // Avoid to leak too much information, only the name (for display) and the id (for comparison) is needed)
    @JsonIncludeProperties({ "id", "name" })
    private User user;

    @CreatedDate
    @Column(name = "creation_date", updatable = false)
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
