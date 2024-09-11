package de.tum.cit.aet.artemis.programming.domain.hestia;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;

/**
 * An ExerciseHint.
 */
@Entity
@Table(name = "exercise_hint")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("T")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = ExerciseHint.class, name = "text"),
    @JsonSubTypes.Type(value = CodeHint.class, name = "code")
})
// @formatter:on
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseHint extends DomainObject {

    @Column(name = "title")
    private String title;

    // A short description of this hint, so the student knows what this hint is about before activating it
    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "content")
    private String content;

    @ManyToOne
    @JsonIgnoreProperties("exerciseHints")
    private ProgrammingExercise exercise;

    @ManyToOne
    @JsonIgnoreProperties("exerciseHints")
    private ProgrammingExerciseTask task;

    @OneToMany(mappedBy = "exerciseHint", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<ExerciseHintActivation> exerciseHintActivations = new HashSet<>();

    @Column(name = "display_threshold", columnDefinition = "TINYINT")
    @Min(0)
    @Max(100)
    private short displayThreshold = 3;

    @Transient
    private Integer currentUserRatingTransient;

    public String getTitle() {
        return title;
    }

    public ExerciseHint title(String title) {
        this.title = title;
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContent() {
        return content;
    }

    public ExerciseHint content(String content) {
        this.content = content;
        return this;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public ProgrammingExercise getExercise() {
        return exercise;
    }

    public ExerciseHint exercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public ProgrammingExerciseTask getProgrammingExerciseTask() {
        return task;
    }

    public void setProgrammingExerciseTask(ProgrammingExerciseTask programmingExerciseTask) {
        this.task = programmingExerciseTask;
    }

    public void setExercise(ProgrammingExercise exercise) {
        this.exercise = exercise;
    }

    public void removeContent() {
        this.content = null;
        this.title = null;
    }

    @Override
    public String toString() {
        return "ExerciseHint{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", content='" + getContent() + "'" + "}";
    }

    public Integer getCurrentUserRating() {
        return currentUserRatingTransient;
    }

    public void setCurrentUserRating(Integer currentUserRating) {
        this.currentUserRatingTransient = currentUserRating;
    }

    public Set<ExerciseHintActivation> getExerciseHintActivations() {
        return exerciseHintActivations;
    }

    public void setExerciseHintActivations(Set<ExerciseHintActivation> exerciseHintActivations) {
        this.exerciseHintActivations = exerciseHintActivations;
    }

    /**
     * Returns a threshold value that defines when this exercise hint is displayed to student participating in a programming exercise.
     * The algorithm defining if the hint is display is described in {@link de.tum.cit.aet.artemis.service.hestia.ExerciseHintService#getAvailableExerciseHints}
     *
     * @return the display threshold value
     */
    public short getDisplayThreshold() {
        return displayThreshold;
    }

    public void setDisplayThreshold(short displayThreshold) {
        this.displayThreshold = displayThreshold;
    }

    /**
     * Creates a copy of this hint including basic attributes, but excluding attributes referencing other models
     *
     * @return The copied hint
     */
    public ExerciseHint createCopy() {
        ExerciseHint copiedHint = new ExerciseHint();

        copiedHint.setDescription(this.getDescription());
        copiedHint.setContent(this.getContent());
        copiedHint.setTitle(this.getTitle());
        return copiedHint;
    }
}
