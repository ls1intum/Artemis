package de.tum.cit.aet.artemis.communication.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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

    /**
     * Kaynak mesajın ID'si (post veya answer_post).
     */
    @NotNull
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    /**
     * Kaynak mesajın tipi: 'post' veya 'answer_post'.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    /**
     * Hedef post. Nullable.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_post_id")
    @JsonIncludeProperties({ "id" })
    private Post destinationPost;

    /**
     * Hedef answer_post. Nullable.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_answer_id")
    @JsonIncludeProperties({ "id" })
    private AnswerPost destinationAnswerPost;

    public ForwardedMessage() {
    }

    public ForwardedMessage(Long sourceId, SourceType sourceType, Post destinationPost, AnswerPost destinationAnswerPost) {
        this.sourceId = sourceId;
        this.sourceType = sourceType;
        setDestinationPost(destinationPost);
        setDestinationAnswerPost(destinationAnswerPost);
    }

    // Getter ve Setter'lar

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
