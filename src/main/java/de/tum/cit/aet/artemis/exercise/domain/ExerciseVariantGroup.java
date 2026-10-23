package de.tum.cit.aet.artemis.exercise.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * An {@code ExerciseVariantGroup} bundles a set of {@link Exercise}s that are interchangeable variants of one another
 * (e.g. several versions of the same task that a student may choose between).
 * <p>
 * The group holds the settings that are shared across all of its variants:
 * <ul>
 * <li>{@link #maxPoints} – the cap on the points the group's variants contribute to the course score,</li>
 * <li>the date fields – a common timeline applied to every variant in the group.</li>
 * </ul>
 * Owned directly by a {@code Course} (unidirectional, {@code course_id} FK on this table); the aggregated {@link Exercise}s
 * are non-owning and outlive the group's removal.
 * <p>
 * A {@link de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise}'s "build and test student submissions after due
 * date" is <em>not</em> shared: it is derived per exercise from the due date and build plan, so each member keeps its own.
 */
@Entity
@Table(name = "exercise_variant_group")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExerciseVariantGroup extends ExerciseTimeline {

    @Column(name = "title", nullable = false)
    private String title;

    /** Cap on the group's contribution to the course score: {@code min(sum(points of variants), maxPoints)}. */
    @Nullable
    @Column(name = "max_points")
    private Double maxPoints;

    /**
     * The course the group belongs to. The key lives here rather than on the course so that a group cannot exist
     * without one: a course-less group is invisible to every course query and would linger forever.
     */
    @ManyToOne
    @JoinColumn(name = "course_id", nullable = false)
    @JsonIgnore
    @Parent
    private Course course;

    // Ignore "course" as well to break the Course -> exerciseVariantGroups -> group -> exercises -> exercise.course cycle,
    // mirroring the guard on Course.exercises.
    @OneToMany(mappedBy = "exerciseVariantGroup", fetch = FetchType.LAZY)
    @JsonIgnoreProperties(value = { "exerciseVariantGroup", "course" }, allowSetters = true)
    private Set<Exercise> exercises = new HashSet<>();

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(@NonNull String title) {
        this.title = title.strip();
    }

    @Nullable
    public Double getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(@Nullable Double maxPoints) {
        this.maxPoints = maxPoints;
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

    /**
     * Whether this group's timeline fields are internally consistent, mirroring {@link Exercise#validateBaseDates()}. All
     * configured dates must follow the strict ordering used for course exercises, and an assessment due date requires a due
     * date.
     *
     * @return {@code true} if the set dates do not contradict each other
     */
    public boolean areDatesValid() {
        return hasValidTimelineDates();
    }

    /** Like {@link #areDatesValid()}, but throws so create/update callers reject an inconsistent timeline instead of saving it. */
    public void validateDates() {
        if (!areDatesValid()) {
            throw new BadRequestAlertException("The group dates are not valid", "exerciseVariantGroup", "noValidDates");
        }
    }

    @Override
    public String toString() {
        return "ExerciseVariantGroup{" + "id=" + getId() + ", title='" + title + "'" + ", maxPoints=" + maxPoints + "}";
    }
}
