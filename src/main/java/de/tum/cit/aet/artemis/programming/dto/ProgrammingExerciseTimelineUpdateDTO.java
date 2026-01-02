package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * DTO for updating the timeline of a programming exercise.
 * Contains only the date-related fields that can be updated via the timeline endpoint.
 *
 * @param id                                         the ID of the programming exercise (required)
 * @param releaseDate                                the release date of the exercise
 * @param startDate                                  the start date of the exercise
 * @param dueDate                                    the due date of the exercise
 * @param assessmentType                             the assessment type
 * @param assessmentDueDate                          the assessment due date
 * @param exampleSolutionPublicationDate             the date when the example solution is published
 * @param buildAndTestStudentSubmissionsAfterDueDate the date when student submissions are built and tested after due date
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseTimelineUpdateDTO(@NotNull Long id, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, AssessmentType assessmentType,
        ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate) {

    /**
     * Applies the timeline values from this DTO to an existing ProgrammingExercise entity.
     *
     * @param exercise the programming exercise to update
     */
    public void applyTo(ProgrammingExercise exercise) {
        exercise.setReleaseDate(releaseDate);
        exercise.setStartDate(startDate);
        exercise.setDueDate(dueDate);
        exercise.setAssessmentType(assessmentType);
        exercise.setAssessmentDueDate(assessmentDueDate);
        exercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(buildAndTestStudentSubmissionsAfterDueDate);
    }
}
