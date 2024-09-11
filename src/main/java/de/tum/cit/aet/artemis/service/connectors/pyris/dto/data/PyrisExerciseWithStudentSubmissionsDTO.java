package de.tum.cit.aet.artemis.service.connectors.pyris.dto.data;

import static de.tum.cit.aet.artemis.service.util.TimeUtil.toInstant;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;

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
                        Optional.ofNullable(submission.getLatestResult()).map(Result::getScore).orElse(0D)))
                .collect(Collectors.toSet());

        return new PyrisExerciseWithStudentSubmissionsDTO(exercise.getId(), exercise.getTitle(), exercise.getExerciseType(), exercise.getMode(), exercise.getMaxPoints(),
                exercise.getBonusPoints(), exercise.getDifficulty(), toInstant(exercise.getReleaseDate()), toInstant(exercise.getDueDate()), exercise.getIncludedInOverallScore(),
                Boolean.TRUE.equals(exercise.getPresentationScoreEnabled()), submissionDTOSet);
    }
}
