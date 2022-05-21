package de.tum.in.www1.artemis.domain.hestia;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorOptions;

import com.fasterxml.jackson.annotation.*;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.Exercise;

/**
 * An ExerciseHint.
 */
@Entity
@Table(name = "exercise_hint")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING)
@DiscriminatorValue("T")
@DiscriminatorOptions(force = true)
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = ExerciseHint.class, name = "text"), @JsonSubTypes.Type(value = CodeHint.class, name = "code") })
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
    private Exercise exercise;

    @ManyToOne
    @JsonIgnoreProperties("exerciseHints")
    private ProgrammingExerciseTask task;

    @OneToMany(mappedBy = "exerciseHint", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<UserExerciseHintActivation> userExerciseHintActivations = new HashSet<>();

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

    public Exercise getExercise() {
        return exercise;
    }

    public ExerciseHint exercise(Exercise exercise) {
        this.exercise = exercise;
        return this;
    }

    public ProgrammingExerciseTask getProgrammingExerciseTask() {
        return task;
    }

    public void setProgrammingExerciseTask(ProgrammingExerciseTask programmingExerciseTask) {
        this.task = programmingExerciseTask;
    }

    public void setExercise(Exercise exercise) {
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

    public Set<UserExerciseHintActivation> getUserExerciseHintActivations() {
        return userExerciseHintActivations;
    }

    public void setUserExerciseHintActivations(Set<UserExerciseHintActivation> userExerciseHintActivations) {
        this.userExerciseHintActivations = userExerciseHintActivations;
    }

    /**
     * Returns a threshold value that defines when this exercise hint is displayed to student participating in a programming exercise.
     * The algorithm defining if the hint is display is described in {@link de.tum.in.www1.artemis.service.hestia.ExerciseHintService#getAvailableExerciseHints}
     * Note: This value is currently fixed but planned to be adjustable.
     * @return the display threshold value
     */
    public int getDisplayThreshold() {
        return 3;
    }
}
