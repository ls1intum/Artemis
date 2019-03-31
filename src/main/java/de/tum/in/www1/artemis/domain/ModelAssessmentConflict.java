package de.tum.in.www1.artemis.domain;

import java.time.ZonedDateTime;
import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.Size;

import de.tum.in.www1.artemis.domain.enumeration.EscalationState;

@Entity
@Table(name = "model-assessment-conflict")
public class ModelAssessmentConflict {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Result result;

    @Size(max = 50)
    @Column(name = "modelElementId")
    private String modelElementId;

    @Column(name = "state")
    private EscalationState state;

    @Column(name = "creationDate")
    private ZonedDateTime creationDate;

    @Column(name = "resolutionDate")
    private ZonedDateTime resolutionDate;

    @OneToMany
    @Column(name = "conflictingResults")
    private Set<ConflictingResult> conflictingResults;

    public Long getId() {
        return id;
    }

    public Result getResult() {
        return result;
    }

    public String getModelElementId() {
        return modelElementId;
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

    public Set<ConflictingResult> getConflictingResults() {
        return conflictingResults;
    }
}
