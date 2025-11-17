package de.tum.cit.aet.artemis.communication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Check;
import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

@Entity
@Table(name = "forwarded_message")
@Check(constraints = "((destination_post_id IS NOT NULL AND destination_answer_id IS NULL) OR (destination_post_id IS NULL AND destination_answer_id IS NOT NULL))")
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
    private Post destinationPost;

    @ManyToOne
    @JoinColumn(name = "destination_answer_id")
    @JsonIncludeProperties({ "id" })
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
