package de.tum.cit.aet.artemis.communication.domain;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * An AnswerPost.
 */
@Entity
@Table(name = "answer_post")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AnswerPost extends Posting {

    @Column(name = "resolves_post", columnDefinition = "boolean default false")
    private Boolean resolvesPost = false;

    @OneToMany(mappedBy = "answerPost", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<Reaction> reactions = new HashSet<>();

    @ManyToOne
    @JsonIncludeProperties({ "id", "exercise", "lecture", "course", "courseWideContext", "conversation", "author" })
    private Post post;

    @Transient
    private boolean isSaved = false;

    @JsonProperty("resolvesPost")
    public Boolean doesResolvePost() {
        return resolvesPost;
    }

    @Column(name = "has_forwarded_messages")
    private boolean hasForwardedMessages;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "verified", nullable = false)
    private boolean verified = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_id")
    @JsonIncludeProperties({ "id", "login", "firstName", "lastName" })
    private User verifiedBy;

    @Column(name = "verified_at")
    private ZonedDateTime verifiedAt;

    public boolean getHasForwardedMessages() {
        return hasForwardedMessages;
    }

    public void setHasForwardedMessages(boolean hasForwardedMessages) {
        this.hasForwardedMessages = hasForwardedMessages;
    }

    public void setResolvesPost(Boolean resolvesPost) {
        this.resolvesPost = resolvesPost;
    }

    @Override
    public Set<Reaction> getReactions() {
        return reactions;
    }

    @Override
    public void setReactions(Set<Reaction> reactions) {
        this.reactions = reactions;
    }

    @Override
    public void addReaction(Reaction reaction) {
        this.reactions.add(reaction);
    }

    @Override
    public void removeReaction(Reaction reaction) {
        this.reactions.remove(reaction);
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    @JsonProperty("isSaved")
    public boolean getIsSaved() {
        return isSaved;
    }

    public void setIsSaved(boolean isSaved) {
        this.isSaved = isSaved;
    }

    @JsonIgnore
    public Conversation getConversation() {
        return getPost().getConversation();
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public User getVerifiedBy() {
        return verifiedBy;
    }

    public void setVerifiedBy(User verifiedBy) {
        this.verifiedBy = verifiedBy;
    }

    public ZonedDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void setVerifiedAt(ZonedDateTime verifiedAt) {
        this.verifiedAt = verifiedAt;
    }

    /**
     * @return true if this is an Iris-generated reply whose confidence fell below the auto-publish
     *         threshold and which has not yet been approved by a tutor. Such replies must not be
     *         shown to students.
     */
    @JsonIgnore
    public boolean isUnverifiedIrisReply() {
        return !verified && getAuthor() != null && getAuthor().isBot();
    }

    /**
     * Helper method to extract the course an AnswerPost belongs to, which is found in different locations based on the parent Post's context
     *
     * @return the course AnswerPost belongs to
     */
    @Override
    public Course getCoursePostingBelongsTo() {
        return this.post.getCoursePostingBelongsTo();
    }

    @Override
    public String toString() {
        return "AnswerPost{" + "id=" + getId() + ", content='" + getContent() + "'" + ", creationDate='" + getCreationDate() + "'" + ", resolvesPosts='" + doesResolvePost() + "'"
                + "}";
    }
}
