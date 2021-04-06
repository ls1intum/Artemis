package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.enumeration.CategoryState;

/**
 * Entity for storing static code analysis categories and their settings.
 */
@Entity
@Table(name = "static_code_analysis_category")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class StaticCodeAnalysisCategory extends DomainObject {

    @Column(name = "name")
    private String name;

    @Column(name = "penalty")
    private Double penalty;

    @Column(name = "max_penalty")
    private Double maxPenalty;

    @Enumerated(EnumType.STRING)
    @Column(name = "state")
    private CategoryState state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("staticCodeAnalysisCategories")
    private ProgrammingExercise exercise;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPenalty() {
        return penalty;
    }

    public void setPenalty(Double penalty) {
        this.penalty = penalty;
    }

    public Double getMaxPenalty() {
        return maxPenalty;
    }

    public void setMaxPenalty(Double maxPenalty) {
        this.maxPenalty = maxPenalty;
    }

    public CategoryState getState() {
        return state;
    }

    public void setState(CategoryState state) {
        this.state = state;
    }

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public void setProgrammingExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }

    @Override
    public String toString() {
        return "StaticCodeAnalysisCategory{" + "id=" + getId() + ", name='" + name + '\'' + ", penalty=" + penalty + ", maxPenalty=" + maxPenalty + ", state=" + state + '}';
    }
}
