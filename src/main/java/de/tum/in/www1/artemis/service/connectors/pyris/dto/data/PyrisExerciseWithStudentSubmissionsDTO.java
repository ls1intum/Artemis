package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import static de.tum.in.www1.artemis.service.util.ZonedDateTimeUtil.toInstant;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;

public record PyrisExerciseWithStudentSubmissionsDTO(long id, String title, ExerciseType type, ExerciseMode mode, double maxPoints, double bonusPoints,
        DifficultyLevel difficultyLevel, Instant releaseDate, Instant dueDate, IncludedInOverallScore inclusionMode, boolean presentationScoreEnabled,
        Set<PyrisStudentSubmissionDTO> submissions) {

    public static PyrisExerciseWithStudentSubmissionsDTO of(Exercise exercise) {
        var submissionDTOSet = exercise.getStudentParticipations().stream().filter(participation -> !participation.isTestRun())
                .flatMap(participation -> participation.getSubmissions().stream()).map(submission -> new PyrisStudentSubmissionDTO(submission.getSubmissionDate().toInstant(),
                        Optional.ofNullable(submission.getLatestResult()).map(Result::getScore).orElse(0D)))
                .collect(Collectors.toSet());

        return new PyrisExerciseWithStudentSubmissionsDTO(exercise.getId(), exercise.getTitle(), exercise.getExerciseType(), exercise.getMode(), exercise.getMaxPoints(),
                exercise.getBonusPoints(), exercise.getDifficulty(), toInstant(exercise.getReleaseDate()), toInstant(exercise.getDueDate()), exercise.getIncludedInOverallScore(),
                Boolean.TRUE.equals(exercise.getPresentationScoreEnabled()), submissionDTOSet);
    }
}
