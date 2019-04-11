package de.tum.in.www1.artemis.domain.modeling;

import java.time.ZonedDateTime;
import java.util.Set;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.enumeration.EscalationState;

/**
 * Representing a conflict between a newly assessed model element (causingResult) and already persisted assessed model elements (resultsInConflict) within the same similarity set.
 */
@Entity
@Table(name = "model_assessment_conflict")
public class ModelAssessmentConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * the conflicting result (i.e. model assessment) includes a link to the assessment and the concrete model element that caused a conflict with existing assessments
     */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    private ConflictingResult causingConflictingResult;

    /**
     * The escalation state, in which this conflict currently is
     */
    @Column(name = "state")
    private EscalationState state;

    /**
     * time at which the conflict happened
     */
    @Column(name = "creationDate")
    private ZonedDateTime creationDate;

    /**
     * time at which the conflict got resolved
     */
    @Column(name = "resolutionDate")
    private ZonedDateTime resolutionDate;

    /**
     * Already persisted results (i.e. element assessments) that are in conflict with the new assessment in causingConflictingResult
     */
    @OneToMany(mappedBy = "conflict", cascade = CascadeType.ALL, orphanRemoval = true)
    @Column(name = "conflictingResults")
    private Set<ConflictingResult> resultsInConflict;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ConflictingResult getCausingResult() {
        return causingConflictingResult;
    }

    public void setCausingConflictingResult(ConflictingResult causingResult) {
        this.causingConflictingResult = causingResult;
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
