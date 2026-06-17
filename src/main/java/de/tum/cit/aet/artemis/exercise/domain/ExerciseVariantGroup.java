package de.tum.cit.aet.artemis.exercise.domain;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * An {@code ExerciseVariantGroup} bundles a set of {@link Exercise}s that are interchangeable variants of one another
 * (e.g. several versions of the same task that a student may choose between).
 * <p>
 * The group holds the settings that are shared across all of its variants:
 * <ul>
 * <li>{@link #maxPoints} – the cap on the points the group's variants contribute to the course score,</li>
 * <li>the date fields – a common timeline applied to every variant in the group.</li>
 * </ul>
 * It is owned directly by a {@code Course} (unidirectional {@code Course → ExerciseVariantGroup}); the owning side keeps
 * the {@code course_id} foreign key on this table. Aggregating the exercises is non-owning: the {@link Exercise}s keep
 * their own {@code Course} link and outlive the removal of the group.
 */
@Entity
@Table(name = "exercise_variant_group")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseVariantGroup extends DomainObject {

    @Column(name = "title")
    private String title;

    /**
     * The cap on the points the group contributes to the course score. The points of a student's variants are summed up
     * and then capped at this value during grade calculation, i.e. the contribution is
     * {@code min(sum(points of variants), maxPoints)}.
     */
    @Nullable
    @Column(name = "max_points")
    private Double maxPoints;

    @Nullable
    @Column(name = "release_date")
    private ZonedDateTime releaseDate;

    @Nullable
    @Column(name = "start_date")
    private ZonedDateTime startDate;

    @Nullable
    @Column(name = "due_date")
    private ZonedDateTime dueDate;

    @Nullable
    @Column(name = "assessment_due_date")
    private ZonedDateTime assessmentDueDate;

    @Nullable
    @Column(name = "example_solution_publication_date")
    private ZonedDateTime exampleSolutionPublicationDate;

    // Ignore "course" as well to break the Course -> exerciseVariantGroups -> group -> exercises -> exercise.course cycle,
    // mirroring the guard on Course.exercises.
    @OneToMany(mappedBy = "exerciseVariantGroup", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "exerciseVariantGroup", "course" }, allowSetters = true)
    private Set<Exercise> exercises = new HashSet<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title != null ? title.strip() : null;
    }

    @Nullable
    public Double getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(@Nullable Double maxPoints) {
        this.maxPoints = maxPoints;
    }

    @Nullable
    public ZonedDateTime getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(@Nullable ZonedDateTime releaseDate) {
        this.releaseDate = releaseDate;
    }

    @Nullable
    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(@Nullable ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    @Nullable
    public ZonedDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(@Nullable ZonedDateTime dueDate) {
        this.dueDate = dueDate;
    }

    @Nullable
    public ZonedDateTime getAssessmentDueDate() {
        return assessmentDueDate;
    }

    public void setAssessmentDueDate(@Nullable ZonedDateTime assessmentDueDate) {
        this.assessmentDueDate = assessmentDueDate;
    }

    @Nullable
    public ZonedDateTime getExampleSolutionPublicationDate() {
        return exampleSolutionPublicationDate;
    }

    public void setExampleSolutionPublicationDate(@Nullable ZonedDateTime exampleSolutionPublicationDate) {
        this.exampleSolutionPublicationDate = exampleSolutionPublicationDate;
    }

    public Set<Exercise> getExercises() {
        return exercises;
    }

    public void setExercises(Set<Exercise> exercises) {
        this.exercises = exercises;
    }

    public void addExercise(Exercise exercise) {
        this.exercises.add(exercise);
        exercise.setExerciseVariantGroup(this);
    }

    public void removeExercise(Exercise exercise) {
        this.exercises.remove(exercise);
        exercise.setExerciseVariantGroup(null);
    }

    @Override
    public String toString() {
        return "ExerciseVariantGroup{" + "id=" + getId() + ", title='" + title + "'" + ", maxPoints=" + maxPoints + "}";
    }
}
