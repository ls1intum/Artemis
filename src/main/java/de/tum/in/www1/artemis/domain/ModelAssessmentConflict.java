package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;
import java.util.Set;

import javax.persistence.*;

import de.tum.in.www1.artemis.domain.enumeration.EscalationState;

@Entity
@Table(name = "model-assessment-conflict")
public class ModelAssessmentConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    private ConflictingResult causingConflictingResult;

    @Column(name = "state")
    private EscalationState state;

    @Column(name = "creationDate")
    private ZonedDateTime creationDate;

    @Column(name = "resolutionDate")
    private ZonedDateTime resolutionDate;

    @OneToMany
    @Column(name = "conflictingResults")
    private Set<ConflictingResult> resultsInConflict;

    public Long getId() {
        return id;
    }

    public ConflictingResult getCausingResult() {
        return causingResult;
    }

    public EscalationState getState() {
        return state;
    }

    public ZonedDateTime getCreationDate() {
        return creationDate;
    }

    public ZonedDateTime getResolutionDate() {
        return resolutionDate;
    }

    public Set<ConflictingResult> getResultsInConflict() {
        return resultsInConflict;
    }
}
