package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;
import java.util.Set;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.enumeration.EscalationState;

/**
 * Representing a conflict between a newly assessed model element (causingResult) and already persisted assessed model elements (resultsInConflict) within the same similarity set.
 */
@Entity
@Table(name = "model-assessment-conflict")
public class ModelAssessmentConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * new model element assessment that caused a conflict with existing assessments
     */
    @OneToOne
    private ConflictingResult causingConflictingResult;

    /**
     * The escalation state, in which this conflict currently is
     */
    @Column(name = "state")
    private EscalationState state;

    /**
     * time at which the conflict arised
     */
    @Column(name = "creationDate")
    private ZonedDateTime creationDate;

    /**
     * time at which the conflict got resolved
     */
    @Column(name = "resolutionDate")
    private ZonedDateTime resolutionDate;

    /**
     * Already persisted model element assessments that are in conflict with the new model element assessment causingConflictingResult
     */
    @OneToMany
    @Column(name = "conflictingResults")
    private Set<ConflictingResult> resultsInConflict;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ConflictingResult getCausingResult() {
        return causingResult;
    }

    public void setCausingResult(ConflictingResult causingResult) {
        this.causingResult = causingResult;
    }

    public EscalationState getState() {
        return state;
    }

    public void setState(EscalationState state) {
        this.state = state;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(ZonedDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public ZonedDateTime getResolutionDate() {
        return resolutionDate;
    }

    public void setResolutionDate(ZonedDateTime resolutionDate) {
        this.resolutionDate = resolutionDate;
    }

    public Set<ConflictingResult> getResultsInConflict() {
        return resultsInConflict;
    }

    public void setResultsInConflict(Set<ConflictingResult> resultsInConflict) {
        this.resultsInConflict = resultsInConflict;
    }
}
