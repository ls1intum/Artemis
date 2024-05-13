package de.tum.in.www1.artemis.service.connectors.pyris.dto.data;

import java.time.Instant;

import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;

public record PyrisExerciseDTO(long id, String title, ExerciseType type, ExerciseMode mode, double maxPoints, double bonusPoints, DifficultyLevel difficultyLevel,
        Instant releaseDate, Instant dueDate, IncludedInOverallScore inclusionMode, boolean presentationScoreEnabled

) {
}
