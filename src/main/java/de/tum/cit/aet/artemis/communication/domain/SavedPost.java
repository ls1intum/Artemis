package de.tum.cit.aet.artemis.communication.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

@Entity
@Table(name = "saved_post")
public class SavedPost extends DomainObject {

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Enumerated
    @Column(name = "post_type", nullable = false)
    private PostingType postType;

    @Enumerated
    @Column(name = "status", nullable = false)
    private SavedPostStatus status;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    public SavedPost() {
    }

    public SavedPost(User user, Long postId, PostingType postType, SavedPostStatus status, ZonedDateTime completedAt) {
        this.user = user;
        this.postId = postId;
        this.postType = postType;
        this.status = status;
        this.completedAt = completedAt;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public void setStatus(SavedPostStatus status) {
        this.status = status;
    }

    public User getUser() {
        return user;
    }

    public SavedPostStatus getStatus() {
        return status;
    }

    public void setCompletedAt(ZonedDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public void setPostType(PostingType postType) {
        this.postType = postType;
    }

    public PostingType getPostType() {
        return postType;
    }

    public ZonedDateTime getCompletedAt() {
        return completedAt;
    }
}
