package de.tum.cit.aet.artemis.exercise.domain.review;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentContentDTO;

@Entity
@Table(name = "comment")
@EntityListeners(AuditingEntityListener.class)
public class Comment extends DomainObject {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private CommentThread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CommentType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", columnDefinition = "json", nullable = false)
    private CommentContentDTO content;

    @ManyToOne
    @JoinColumn(name = "initial_version_id")
    private ExerciseVersion initialVersion;

    @Column(name = "initial_commit_sha")
    private String initialCommitSha;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private Instant createdDate = Instant.now();

    @LastModifiedDate
    @Column(name = "last_modified_date")
    private Instant lastModifiedDate = Instant.now();

    public CommentThread getThread() {
        return thread;
    }

    public void setThread(CommentThread thread) {
        this.thread = thread;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public CommentType getType() {
        return type;
    }

    public void setType(CommentType type) {
        this.type = type;
    }

    public CommentContentDTO getContent() {
        return content;
    }

    public void setContent(CommentContentDTO content) {
        this.content = content;
    }

    public ExerciseVersion getInitialVersion() {
        return initialVersion;
    }

    public void setInitialVersion(ExerciseVersion initialVersion) {
        this.initialVersion = initialVersion;
    }

    public String getInitialCommitSha() {
        return initialCommitSha;
    }

    public void setInitialCommitSha(String initialCommitSha) {
        this.initialCommitSha = initialCommitSha;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(Instant lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }
}
