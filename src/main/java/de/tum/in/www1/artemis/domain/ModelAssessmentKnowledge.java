package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.modeling.ModelElement;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;

@Entity
@Table(name = "model_assessment_knowledge")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ModelAssessmentKnowledge extends DomainObject {

    @OneToMany(mappedBy = "knowledge", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("knowledge")
    private Set<ModelingExercise> exercises = new HashSet<>();

    @OneToMany(mappedBy = "knowledge", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("knowledge")
    private Set<ModelElement> modelElements = new HashSet<>();

    public Set<ModelingExercise> getExercises() {
        return exercises;
    }

    public Set<ModelElement> getElements() {
        return modelElements;
    }

}
