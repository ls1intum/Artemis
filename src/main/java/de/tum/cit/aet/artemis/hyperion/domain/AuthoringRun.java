package de.tum.cit.aet.artemis.hyperion.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Bounded activity metadata and references to existing exercise versions; never persisted as a database entity. */

public final class AuthoringRun implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public enum Kind {
        CREATE, ADAPT, VARIANT
    }

    public enum Status {
        QUEUED, SAVED, NEEDS_REVIEW, PARTIAL, ERROR, CANCELLED, UNKNOWN
    }

    private String jobId;

    private Long exerciseId;

    private Long sourceExerciseId;

    private Long ownerId;

    private Kind kind;

    private Status status = Status.QUEUED;

    private Instant startedAt;

    private Instant finishedAt;

    private Instant mutationStartedAt;

    private Long beforeVersionId;

    private Long afterVersionId;

    private String repositoryBranch;

    private Boolean liveExerciseChanged;

    private Instant restoreStartedAt;

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

    public AuthoringRun() {
    }

    public AuthoringRun(AuthoringRun source) {
        this.id = source.id;
        this.jobId = source.jobId;
        this.exerciseId = source.exerciseId;
        this.sourceExerciseId = source.sourceExerciseId;
        this.ownerId = source.ownerId;
        this.kind = source.kind;
        this.status = source.status;
        this.startedAt = source.startedAt;
        this.finishedAt = source.finishedAt;
        this.mutationStartedAt = source.mutationStartedAt;
        this.beforeVersionId = source.beforeVersionId;
        this.afterVersionId = source.afterVersionId;
        this.repositoryBranch = source.repositoryBranch;
        this.liveExerciseChanged = source.liveExerciseChanged;
        this.restoreStartedAt = source.restoreStartedAt;
        this.revertedAt = source.revertedAt;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public void setMutationStartedAt(Instant mutationStartedAt) {
        this.mutationStartedAt = mutationStartedAt;
    }

    public void setBeforeVersionId(Long beforeVersionId) {
        this.beforeVersionId = beforeVersionId;
    }

    public void setAfterVersionId(Long afterVersionId) {
        this.afterVersionId = afterVersionId;
    }

    public void setRepositoryBranch(String repositoryBranch) {
        this.repositoryBranch = repositoryBranch;
    }

    public void setLiveExerciseChanged(Boolean liveExerciseChanged) {
        this.liveExerciseChanged = liveExerciseChanged;
    }

    public void setRevertedAt(Instant revertedAt) {
        this.revertedAt = revertedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AuthoringRun run && Objects.equals(id, run.id) && Objects.equals(jobId, run.jobId) && Objects.equals(exerciseId, run.exerciseId)
                && Objects.equals(sourceExerciseId, run.sourceExerciseId) && Objects.equals(ownerId, run.ownerId) && Objects.equals(kind, run.kind)
                && Objects.equals(status, run.status) && Objects.equals(startedAt, run.startedAt) && Objects.equals(finishedAt, run.finishedAt)
                && Objects.equals(mutationStartedAt, run.mutationStartedAt) && Objects.equals(beforeVersionId, run.beforeVersionId)
                && Objects.equals(afterVersionId, run.afterVersionId) && Objects.equals(repositoryBranch, run.repositoryBranch)
                && Objects.equals(liveExerciseChanged, run.liveExerciseChanged) && Objects.equals(restoreStartedAt, run.restoreStartedAt)
                && Objects.equals(revertedAt, run.revertedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, jobId, exerciseId, sourceExerciseId, ownerId, kind, status, startedAt, finishedAt, mutationStartedAt, beforeVersionId, afterVersionId,
                repositoryBranch, liveExerciseChanged, restoreStartedAt, revertedAt);
    }
}
