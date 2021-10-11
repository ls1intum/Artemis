package de.tum.in.www1.artemis.domain.submissionpolicy;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;

/**
 * Represents an abstract submission policy.
 * A submission policy configures the parameters for imposing a penalty on programming exercise
 * participation submissions. The type of penalty is determined by the concrete type of the
 * submission policy. The system supports two types of policies:
 * <ol>
 *     <li>Lock Repository: Locks the participation repository after x submissions</li>
 *     <li>Submission Penalty: Reduces the possible achievable score after x submissions</li>
 * </ol>
 * More information can be found at {@link LockRepositoryPolicy} and {@link SubmissionPenaltyPolicy} respectively.
 */
@Entity
@Table(name = "submission_policy")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("SP")
@DiscriminatorOptions(force = true)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSubTypes({ @JsonSubTypes.Type(value = LockRepositoryPolicy.class, name = "lock_repository"),
        @JsonSubTypes.Type(value = SubmissionPenaltyPolicy.class, name = "submission_penalty") })
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

    public abstract String toString();
}
