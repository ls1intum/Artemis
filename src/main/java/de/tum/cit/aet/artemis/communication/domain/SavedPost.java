package de.tum.cit.aet.artemis.communication.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.User;

@Entity
@Table(name = "saved_post")
public class SavedPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "post_type", nullable = false)
    private String postType;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "completed_at", nullable = true)
    private ZonedDateTime completedAt;

    public SavedPost() {
    }

    public SavedPost(User user, Long postId, String postType, String status, ZonedDateTime completedAt) {
        this.user = user;
        this.postId = postId;
        this.postType = postType;
        this.status = status;
        this.completedAt = completedAt;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public User getUser() {
        return user;
    }

    public SavedPostStatus getStatus() {
        return SavedPostStatus.fromDatabaseKey(status);
    }

    public void setCompletedAt(ZonedDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public void setPostType(String postType) {
        this.postType = postType;
    }

    public PostingType getPostType() {
        return PostingType.fromDatabaseKey(postType);
    }

    public ZonedDateTime getCompletedAt() {
        return completedAt;
    }
}
