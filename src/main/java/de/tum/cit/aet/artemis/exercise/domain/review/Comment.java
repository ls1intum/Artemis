package de.tum.cit.aet.artemis.exercise.domain.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.dto.review.CommentContentDTO;

@Entity
@Table(name = "comment")
public class Comment extends AbstractAuditingEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private CommentThread thread;

    @ManyToOne
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

}
