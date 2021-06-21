package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "assessment_knowledge")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AssessmentKnowledge extends DomainObject{

    @OneToMany(mappedBy = "knowledge", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("knowledge")
    private Set<Exercise> exercises = new HashSet<>();

    @OneToMany(mappedBy = "knowledge", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("knowledge")
    private Set<Feedback> feedbacks = new HashSet<>();

    @OneToMany(mappedBy = "knowledge", fetch = FetchType.LAZY)
    @Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    @JsonIgnoreProperties("knowledge")
    private Set<TextBlock> textBlocks = new HashSet<>();

    public Set<Exercise> getExercises() {
        return exercises;
    }

    public AssessmentKnowledge addExercises(Exercise exercise) {
        this.exercises.add(exercise);
        exercise.setKnowledge(this);
        return this;
    }

    public Set<Feedback> getFeedbacks() {
        return feedbacks;
    }

    public AssessmentKnowledge addFeedbacks(Feedback feedback) {
        this.feedbacks.add(feedback);
        feedback.setKnowledge(this);
        return this;
    }

    public Set<TextBlock> getTextBlocks() {
        return textBlocks;
    }

    public AssessmentKnowledge addTextBlocks(TextBlock textBlock) {
        this.textBlocks.add(textBlock);
        textBlock.setKnowledge(this);
        return this;
    }
}
