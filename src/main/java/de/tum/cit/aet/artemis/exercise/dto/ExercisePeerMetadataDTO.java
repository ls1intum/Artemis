package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;

/** Scalar projection for the comparison's release and assessment boundaries. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExercisePeerMetadataDTO(long id, String title, ExerciseType type, ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate,
        ExerciseMode mode, IncludedInOverallScore includedInOverallScore) {

    public ExercisePeerMetadataDTO(long id, String title, Class<? extends Exercise> type, ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate,
            ExerciseMode mode, IncludedInOverallScore includedInOverallScore) {
        this(id, title, ExerciseType.getExerciseTypeFromClass(type), releaseDate, dueDate, assessmentDueDate, mode, includedInOverallScore);
    }
}
