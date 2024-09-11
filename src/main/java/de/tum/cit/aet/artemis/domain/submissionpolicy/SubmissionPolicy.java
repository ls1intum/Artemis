package de.tum.cit.aet.artemis.domain.submissionpolicy;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;

/**
 * Represents an abstract submission policy.
 * A submission policy configures the parameters for imposing a penalty on programming exercise
 * participation submissions. The type of penalty is determined by the concrete type of the
 * submission policy. The system supports two types of policies:
 * <ol>
 * <li>Lock Repository: Locks the participation repository after x submissions</li>
 * <li>Submission Penalty: Reduces the possible achievable score after x submissions</li>
 * </ol>
 * More information can be found at {@link LockRepositoryPolicy} and {@link SubmissionPenaltyPolicy} respectively.
 */
@Entity
@Table(name = "submission_policy")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("SP")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = LockRepositoryPolicy.class, name = "lock_repository"),
    @JsonSubTypes.Type(value = SubmissionPenaltyPolicy.class, name = "submission_penalty")
})
// @formatter:on
public abstract class SubmissionPolicy extends DomainObject {

    @OneToOne(mappedBy = "submissionPolicy")
    private ProgrammingExercise programmingExercise;

    @Column(name = "submission_limit")
    private Integer submissionLimit;

    @Column(name = "active")
    private Boolean active;

    public Integer getSubmissionLimit() {
        return submissionLimit;
    }

    public void setSubmissionLimit(Integer submissionLimit) {
        this.submissionLimit = submissionLimit;
    }

    public Boolean isActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public ProgrammingExercise getProgrammingExercise() {
        return programmingExercise;
    }

    public void setProgrammingExercise(ProgrammingExercise programmingExercise) {
        this.programmingExercise = programmingExercise;
    }

    @Override
    public abstract String toString();
}
