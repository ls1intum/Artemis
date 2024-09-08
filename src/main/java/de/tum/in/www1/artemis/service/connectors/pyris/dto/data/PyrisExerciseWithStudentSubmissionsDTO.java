package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import static de.tum.in.www1.artemis.service.util.TimeUtil.toInstant;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisExerciseWithStudentSubmissionsDTO(long id, String title, ExerciseType type, ExerciseMode mode, double maxPoints, double bonusPoints,
        DifficultyLevel difficultyLevel, Instant releaseDate, Instant dueDate, IncludedInOverallScore inclusionMode, boolean presentationScoreEnabled,
        Set<PyrisStudentSubmissionDTO> submissions) {

    /**
     * Convert an exercise to a PyrisExerciseWithStudentSubmissionsDTO.
     *
     * @param exercise The exercise to convert.
     * @return The converted exercise.
     */
    public static PyrisExerciseWithStudentSubmissionsDTO of(Exercise exercise) {
        var submissionDTOSet = exercise.getStudentParticipations().stream().filter(participation -> !participation.isTestRun())
                .flatMap(participation -> participation.getSubmissions().stream()).map(submission -> new PyrisStudentSubmissionDTO(toInstant(submission.getSubmissionDate()),
                        Optional.ofNullable(submission.getLastResult()).map(Result::getScore).orElse(0D)))
                .collect(Collectors.toSet());

        return new PyrisExerciseWithStudentSubmissionsDTO(exercise.getId(), exercise.getTitle(), exercise.getExerciseType(), exercise.getMode(), exercise.getMaxPoints(),
                exercise.getBonusPoints(), exercise.getDifficulty(), toInstant(exercise.getReleaseDate()), toInstant(exercise.getDueDate()), exercise.getIncludedInOverallScore(),
                Boolean.TRUE.equals(exercise.getPresentationScoreEnabled()), submissionDTOSet);
    }
}
