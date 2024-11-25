package de.tum.cit.aet.artemis.communication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Check;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;

@Entity
@Table(name = "forwarded_message")
@Check(constraints = "((destination_post_id IS NOT NULL AND destination_answer_id IS NULL) OR (destination_post_id IS NULL AND destination_answer_id IS NOT NULL))")
public class ForwardedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @NotNull
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

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

    public ForwardedMessage(Long sourceId, SourceType sourceType, Post destinationPost, AnswerPost destinationAnswerPost) {
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

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public Post getDestinationPost() {
        return destinationPost;
    }

    public void setDestinationPost(Post post) {
        this.destinationPost = post;
    }

    public AnswerPost getDestinationAnswerPost() {
        return destinationAnswerPost;
    }

    public void setDestinationAnswerPost(AnswerPost answerPost) {
        this.destinationAnswerPost = answerPost;
    }

    @Override
    public String toString() {
        return "ForwardedMessage{" + "id=" + id + ", sourceId=" + sourceId + ", sourceType=" + sourceType + '}';
    }

}
