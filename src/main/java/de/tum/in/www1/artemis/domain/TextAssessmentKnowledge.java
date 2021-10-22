package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * TextAssessmentKnowledge is a domain class which will help us
 * to reuse feedback of existing assessments of text exercises and
 * hence improve tutor's experience by providing higher quantity of
 * feedback and also to improve student's experience by proving higher
 * quality feedback.
 */

@Entity
@Table(name = "text_assessment_knowledge")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextAssessmentKnowledge extends DomainObject {

    @OneToMany(mappedBy = "knowledge", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("knowledge")
    private Set<TextExercise> exercises = new HashSet<>();

    @OneToMany(mappedBy = "knowledge", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("knowledge")
    private Set<TextBlock> textBlocks = new HashSet<>();

    public Set<TextExercise> getExercises() {
        return exercises;
    }

    public Set<TextBlock> getTextBlocks() {
        return textBlocks;
    }
}
