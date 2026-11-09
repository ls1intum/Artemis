package de.tum.cit.aet.artemis.assessment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;

/**
 * A weighted criterion of the rubric of a presentation assessment.
 */
@Entity
@Table(name = "presentation_rubric_criterion")
public class PresentationRubricCriterion extends DomainObject {

    public static final String ENTITY_NAME = "presentationRubricCriterion";

    /** The longest name of a criterion. Keep in sync with MAX_NAME_LENGTH in presentation-rubric.util.ts. */
    public static final int MAX_NAME_LENGTH = 255;

    /** The longest description of a criterion or a level. Keep in sync with MAX_DESCRIPTION_LENGTH in presentation-rubric.util.ts. */
    public static final int MAX_DESCRIPTION_LENGTH = 1000;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_assessment_id", nullable = false)
    @Parent
    private PresentationAssessment presentationAssessment;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "name", nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "description", length = MAX_DESCRIPTION_LENGTH)
    private String description;

    @Column(name = "weight", nullable = false)
    private int weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "scale", nullable = false, length = 20)
    private PresentationRubricScale scale;

    public PresentationAssessment getPresentationAssessment() {
        return presentationAssessment;
    }

    public void setPresentationAssessment(PresentationAssessment presentationAssessment) {
        this.presentationAssessment = presentationAssessment;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public PresentationRubricScale getScale() {
        return scale;
    }

    public void setScale(PresentationRubricScale scale) {
        this.scale = scale;
    }
}
