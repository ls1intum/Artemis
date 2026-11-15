package de.tum.cit.aet.artemis.exercise.domain;

import static de.tum.cit.aet.artemis.core.util.DateUtil.validateStrictDateSequence;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/** The common timeline of an exercise and the variant group whose dates its members adopt. */
@MappedSuperclass
public abstract class ExerciseTimeline extends DomainObject {

    @Column(name = "release_date")
    @Nullable
    private ZonedDateTime releaseDate;

    @Column(name = "start_date")
    @Nullable
    private ZonedDateTime startDate;

    @Column(name = "due_date")
    @Nullable
    private ZonedDateTime dueDate;

    @Column(name = "assessment_due_date")
    @Nullable
    private ZonedDateTime assessmentDueDate;

    @Column(name = "example_solution_publication_date")
    @Nullable
    private ZonedDateTime exampleSolutionPublicationDate;

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

    /** Shared strict ordering; exercise-specific exam restrictions and error responses stay with the callers. */
    boolean hasValidTimelineDates() {
        boolean releaseDateValid = validateStrictDateSequence(List.of(), getReleaseDate(),
                Arrays.asList(getStartDate(), getDueDate(), getAssessmentDueDate(), getExampleSolutionPublicationDate()));
        boolean startDateValid = validateStrictDateSequence(Collections.singletonList(getReleaseDate()), getStartDate(),
                Arrays.asList(getDueDate(), getAssessmentDueDate(), getExampleSolutionPublicationDate()));
        boolean dueDateValid = validateStrictDateSequence(Arrays.asList(getReleaseDate(), getStartDate()), getDueDate(),
                Arrays.asList(getAssessmentDueDate(), getExampleSolutionPublicationDate()));
        boolean assessmentDueDateValid = validateAssessmentDueDate();
        boolean exampleSolutionPublicationDateValid = validateStrictDateSequence(Arrays.asList(getReleaseDate(), getStartDate(), getDueDate(), getAssessmentDueDate()),
                getExampleSolutionPublicationDate(), List.of());

        return releaseDateValid && startDateValid && dueDateValid && assessmentDueDateValid && exampleSolutionPublicationDateValid;
    }

    private boolean validateAssessmentDueDate() {
        if (getAssessmentDueDate() == null) {
            return true;
        }
        if (getDueDate() == null) {
            return false;
        }
        return validateStrictDateSequence(Arrays.asList(getReleaseDate(), getStartDate(), getDueDate()), getAssessmentDueDate(),
                Collections.singletonList(getExampleSolutionPublicationDate()));
    }

}
