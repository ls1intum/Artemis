package de.tum.cit.aet.artemis.hyperion.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;

/** Durable run identity and links to canonical exercise versions; transient progress stays in replay storage. */
@Entity
@Table(name = "hyperion_authoring_run")
public class AuthoringRun extends DomainObject {

    public enum Kind {
        CREATE, ADAPT, VARIANT
    }

    public enum Status {
        QUEUED, SAVED, NEEDS_REVIEW, PARTIAL, ERROR, CANCELLED, UNKNOWN
    }

    @Column(name = "job_id", nullable = false, updatable = false, unique = true, length = 36)
    private String jobId;

    @Parent
    @Column(name = "exercise_id", nullable = false, updatable = false)
    private Long exerciseId;

    @Column(name = "source_exercise_id", updatable = false)
    private Long sourceExerciseId;

    @Column(name = "owner_id", updatable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 16)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private Status status = Status.QUEUED;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "mutation_started_at")
    private Instant mutationStartedAt;

    @Column(name = "before_version_id")
    private Long beforeVersionId;

    @Column(name = "after_version_id")
    private Long afterVersionId;

    @Column(name = "repository_branch")
    private String repositoryBranch;

    @Column(name = "live_exercise_changed")
    private Boolean liveExerciseChanged;

    @Column(name = "restore_started_at")
    private Instant restoreStartedAt;

    @Column(name = "reverted_at")
    private Instant revertedAt;

    public Instant getRestoreStartedAt() {
        return restoreStartedAt;
    }

    public void setRestoreStartedAt(Instant restoreStartedAt) {
        this.restoreStartedAt = restoreStartedAt;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Long getSourceExerciseId() {
        return sourceExerciseId;
    }

    public void setSourceExerciseId(Long sourceExerciseId) {
        this.sourceExerciseId = sourceExerciseId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getMutationStartedAt() {
        return mutationStartedAt;
    }

    public Long getBeforeVersionId() {
        return beforeVersionId;
    }

    public Long getAfterVersionId() {
        return afterVersionId;
    }

    public String getRepositoryBranch() {
        return repositoryBranch;
    }

    public Boolean getLiveExerciseChanged() {
        return liveExerciseChanged;
    }

    public Instant getRevertedAt() {
        return revertedAt;
    }
}
