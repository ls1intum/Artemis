package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;

/** Common scalar metadata carried by exercise update requests; title and statement rules remain type-specific. */
public interface ExerciseMetadataDTO {

    String channelName();

    Set<String> categories();

    DifficultyLevel difficulty();

    Double maxPoints();

    Double bonusPoints();

    IncludedInOverallScore includedInOverallScore();

    ZonedDateTime releaseDate();

    ZonedDateTime startDate();

    ZonedDateTime dueDate();

    ZonedDateTime assessmentDueDate();

    Boolean allowComplaintsForAutomaticAssessments();

    Boolean presentationScoreEnabled();

    Boolean secondCorrectionEnabled();

    /**
     * Applies PUT metadata, including clearing nullable dates and score settings.
     *
     * @param exercise exercise to update
     */
    default void applyMetadataTo(Exercise exercise) {
        exercise.setChannelName(channelName());
        exercise.setCategories(categories());
        exercise.setDifficulty(difficulty());
        exercise.setMaxPoints(maxPoints());
        exercise.setBonusPoints(bonusPoints());
        exercise.setIncludedInOverallScore(includedInOverallScore());
        exercise.setReleaseDate(releaseDate());
        exercise.setStartDate(startDate());
        exercise.setDueDate(dueDate());
        exercise.setAssessmentDueDate(assessmentDueDate());
    }

    /**
     * Optional assessment switches preserve stored values when absent.
     *
     * @param exercise exercise to update
     */
    default void applyAssessmentFlagsTo(Exercise exercise) {
        if (allowComplaintsForAutomaticAssessments() != null) {
            exercise.setAllowComplaintsForAutomaticAssessments(allowComplaintsForAutomaticAssessments());
        }
        if (presentationScoreEnabled() != null) {
            exercise.setPresentationScoreEnabled(presentationScoreEnabled());
        }
        if (secondCorrectionEnabled() != null) {
            exercise.setSecondCorrectionEnabled(secondCorrectionEnabled());
        }
    }
}
