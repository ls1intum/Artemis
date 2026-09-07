package de.tum.cit.aet.artemis.exercise.domain;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
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
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

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
public class ExerciseVariantGroup extends DomainObject {

    @Column(name = "title", nullable = false)
    private String title;

    /** Cap on the group's contribution to the course score: {@code min(sum(points of variants), maxPoints)}. */
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
        this.title = Objects.requireNonNull(title, "title must not be null").strip();
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

    /**
     * Whether this group's timeline fields are internally consistent, mirroring {@link Exercise#validateDates()}
     * (release &lt;= start &lt;= due, assessment due not before release or due). The example solution date only needs to
     * follow the release date; the stricter "not before due date" rule depends on {@code IncludedInOverallScore}, which a
     * group lacks, so it is enforced per member when the timeline is applied.
     *
     * @return {@code true} if the set dates do not contradict each other
     */
    public boolean areDatesValid() {
        //@formatter:off
        return isNotAfterAndNotNull(releaseDate, dueDate)
                && isNotAfterAndNotNull(releaseDate, startDate)
                && isNotAfterAndNotNull(startDate, dueDate)
                && isValidAssessmentDueDate(startDate, dueDate, assessmentDueDate)
                && isValidAssessmentDueDate(releaseDate, dueDate, assessmentDueDate)
                && isNotAfterAndNotNull(releaseDate, exampleSolutionPublicationDate);
        //@formatter:on
    }

    /** Like {@link #areDatesValid()}, but throws so create/update callers reject an inconsistent timeline instead of saving it. */
    public void validateDates() {
        if (!areDatesValid()) {
            throw new BadRequestAlertException("The group dates are not valid", "exerciseVariantGroup", "noValidDates");
        }
    }

    private static boolean isValidAssessmentDueDate(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate) {
        if (assessmentDueDate == null) {
            return true;
        }
        // There cannot be an assessmentDueDate without a dueDate.
        if (dueDate == null) {
            return false;
        }
        return isNotAfterAndNotNull(dueDate, assessmentDueDate) && isNotAfterAndNotNull(releaseDate, assessmentDueDate);
    }

    private static boolean isNotAfterAndNotNull(ZonedDateTime previousDate, ZonedDateTime laterDate) {
        if (previousDate == null || laterDate == null) {
            return true;
        }
        return !previousDate.isAfter(laterDate);
    }

    @Override
    public String toString() {
        return "ExerciseVariantGroup{" + "id=" + getId() + ", title='" + title + "'" + ", maxPoints=" + maxPoints + "}";
    }
}
