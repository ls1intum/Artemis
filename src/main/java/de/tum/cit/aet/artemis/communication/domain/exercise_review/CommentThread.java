package de.tum.cit.aet.artemis.communication.domain.exercise_review;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;

@Entity
@Table(name = "comment_thread")
public class CommentThread extends DomainObject {

    @Column(name = "group_id")
    private Long groupId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private CommentThreadLocationType targetType;

    @Column(name = "auxiliary_repo_id")
    private Long auxiliaryRepositoryId;

    @ManyToOne
    @JoinColumn(name = "initial_version_id")
    private ExerciseVersion initialVersion;

    @Column(name = "initial_commit_sha")
    private String initialCommitSha;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "initial_file_path")
    private String initialFilePath;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "initial_line_number")
    private Integer initialLineNumber;

    @Column(name = "outdated")
    private boolean outdated;

    @Column(name = "resolved")
    private boolean resolved;

    @OneToMany(mappedBy = "thread", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE, orphanRemoval = true)
    private Set<Comment> comments = new HashSet<>();

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public CommentThreadLocationType getTargetType() {
        return targetType;
    }

    public void setTargetType(CommentThreadLocationType targetType) {
        this.targetType = targetType;
    }

    public Long getAuxiliaryRepositoryId() {
        return auxiliaryRepositoryId;
    }

    public void setAuxiliaryRepositoryId(Long auxiliaryRepositoryId) {
        this.auxiliaryRepositoryId = auxiliaryRepositoryId;
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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getInitialFilePath() {
        return initialFilePath;
    }

    public void setInitialFilePath(String initialFilePath) {
        this.initialFilePath = initialFilePath;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public Integer getInitialLineNumber() {
        return initialLineNumber;
    }

    public void setInitialLineNumber(Integer initialLineNumber) {
        this.initialLineNumber = initialLineNumber;
    }

    public boolean isOutdated() {
        return outdated;
    }

    public void setOutdated(boolean outdated) {
        this.outdated = outdated;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public Set<Comment> getComments() {
        return comments;
    }

    public void setComments(Set<Comment> comments) {
        this.comments = comments;
    }
}
