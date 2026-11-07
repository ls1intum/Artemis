package de.tum.cit.aet.artemis.metis.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;

/**
 * The Metis comprehension check of one student on one programming exercise: the lifecycle of the questions generated
 * from that student's submission, from the first request for generation through the student's answers to completion.
 *
 * <p>
 * One row per graded student participation, which the schema keeps unique. The participation is the parent: every path
 * that deletes a participation (deleting the exercise, resetting a course or an exam, the retention job, account
 * deletion) reaches this row and its questions through the cascade without any service hook. Team, practice and exam
 * participations are out of scope for version 1; the schema does not enforce that.
 *
 * <p>
 * The row records which repository state the questions were generated from and by which model and prompt, so that a
 * later regeneration or an evaluation can tell the runs apart. The questions themselves, with their answers and
 * ratings, are {@link MetisQuestion} rows pointing here.
 */
@Entity
@Table(name = "metis_check")
public class MetisCheck extends DomainObject {

    /** The student participation the check is about. A parent: the check has no meaning without it and cascades away with it. */
    @Column(name = "participation_id", nullable = false)
    @Parent
    private long participationId;

    /** Where the check is in its lifecycle. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private MetisCheckStatus status = MetisCheckStatus.PENDING;

    /** The commit of the student's repository the questions were generated from, or null before generation. */
    @Nullable
    @Column(name = "commit_hash", length = 40)
    private String commitHash;

    /** The model id reported by the LLM endpoint that generated the questions, or null before generation. */
    @Nullable
    @Column(name = "llm_model", length = 100)
    private String llmModel;

    /** The version of the prompt template used for generation, or null before generation. */
    @Nullable
    @Column(name = "prompt_version", length = 32)
    private String promptVersion;

    /** Why the check is {@link MetisCheckStatus#FAILED} or {@link MetisCheckStatus#SKIPPED}, for example no submission or an empty diff; null otherwise. */
    @Nullable
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    /** When the questions were generated, or null until then. */
    @Nullable
    @Column(name = "generated_at")
    private ZonedDateTime generatedAt;

    /** When the student was shown the first question, or null until then. */
    @Nullable
    @Column(name = "started_at")
    private ZonedDateTime startedAt;

    /** When the student finished the check, or null until then. */
    @Nullable
    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    public long getParticipationId() {
        return participationId;
    }

    public void setParticipationId(long participationId) {
        this.participationId = participationId;
    }

    public MetisCheckStatus getStatus() {
        return status;
    }

    public void setStatus(MetisCheckStatus status) {
        this.status = status;
    }

    @Nullable
    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(@Nullable String commitHash) {
        this.commitHash = commitHash;
    }

    @Nullable
    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(@Nullable String llmModel) {
        this.llmModel = llmModel;
    }

    @Nullable
    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(@Nullable String promptVersion) {
        this.promptVersion = promptVersion;
    }

    @Nullable
    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(@Nullable String failureReason) {
        this.failureReason = failureReason;
    }

    @Nullable
    public ZonedDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(@Nullable ZonedDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    @Nullable
    public ZonedDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(@Nullable ZonedDateTime startedAt) {
        this.startedAt = startedAt;
    }

    @Nullable
    public ZonedDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(@Nullable ZonedDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
