package de.tum.cit.aet.artemis.communication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;

/**
 * A message forwarded to exactly one destination: a post or an answer post, never both and never neither.
 * <p>
 * That invariant is enforced in two places, neither of which is this class's mapping. The constructor rejects a pair
 * that breaks it, and the database holds {@code CHECK_DESTINATION_POST_OR_ANSWER}, created by
 * {@code 20260910212533_changelog.xml}. The entity used to declare the same rule with Hibernate's {@code @Check}, which
 * never reached any database: {@code ddl-auto} is {@code none} in every profile, so Hibernate emits no schema at all.
 * The annotation is gone rather than replaced, because the JPA spelling of it would be inert for the same reason.
 */
@Entity
@Table(name = "forwarded_message")
public class ForwardedMessage extends DomainObject {

    @Column(name = "source_id", nullable = false)
    private long sourceId;

    @NonNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private PostingType sourceType;

    @ManyToOne
    @JoinColumn(name = "destination_post_id")
    @JsonIncludeProperties({ "id" })
    @Parent(enforcedBy = "CHECK_DESTINATION_POST_OR_ANSWER")
    private Post destinationPost;

    @ManyToOne
    @JoinColumn(name = "destination_answer_id")
    @JsonIncludeProperties({ "id" })
    @Parent(enforcedBy = "CHECK_DESTINATION_POST_OR_ANSWER")
    private AnswerPost destinationAnswerPost;

    public ForwardedMessage() {
    }

    public ForwardedMessage(Long sourceId, PostingType sourceType, Post destinationPost, AnswerPost destinationAnswerPost) {
        if (sourceId == null) {
            throw new IllegalArgumentException("sourceId cannot be null");
        }
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType cannot be null");
        }
        if ((destinationPost == null && destinationAnswerPost == null) || (destinationPost != null && destinationAnswerPost != null)) {
            throw new IllegalArgumentException("Exactly one destination must be non-null");
        }
        this.sourceId = sourceId;
        this.sourceType = sourceType;
        this.destinationPost = destinationPost;
        this.destinationAnswerPost = destinationAnswerPost;
    }

    public long getSourceId() {
        return sourceId;
    }

    public void setSourceId(long sourceId) {
        this.sourceId = sourceId;
    }

    public PostingType getSourceType() {
        return sourceType;
    }

    public void setSourceType(PostingType sourceType) {
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType cannot be null");
        }
        this.sourceType = sourceType;
    }

    public Post getDestinationPost() {
        return destinationPost;
    }

    public void setDestinationPost(Post post) {
        if (post != null && this.destinationAnswerPost != null) {
            throw new IllegalStateException("Cannot set both destination post and answer post");
        }
        this.destinationPost = post;
    }

    public AnswerPost getDestinationAnswerPost() {
        return destinationAnswerPost;
    }

    public void setDestinationAnswerPost(AnswerPost answerPost) {
        if (answerPost != null && this.destinationPost != null) {
            throw new IllegalStateException("Cannot set both destination post and answer post");
        }
        this.destinationAnswerPost = answerPost;
    }

}
