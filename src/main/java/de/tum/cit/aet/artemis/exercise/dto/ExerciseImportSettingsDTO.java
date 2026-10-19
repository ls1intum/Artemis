package de.tum.cit.aet.artemis.exercise.dto;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;

/** Scalar input shared by manual exercise imports, preserving defaults omitted by older clients. */
public interface ExerciseImportSettingsDTO extends ExerciseMetadataDTO {

    Long id();

    String title();

    String shortName();

    String problemStatement();

    ExerciseMode mode();

    String gradingInstructions();

    java.time.ZonedDateTime exampleSolutionPublicationDate();

    /**
     * Initializes the common import fields without overwriting non-null entity defaults with absent values.
     *
     * @param exercise transient exercise to initialize
     */
    default void applyImportSettingsTo(Exercise exercise) {
        exercise.setId(id());
        exercise.setTitle(title());
        exercise.setChannelName(channelName());
        exercise.setShortName(shortName());
        exercise.setProblemStatement(problemStatement());
        exercise.setCategories(categories());
        exercise.setDifficulty(difficulty());
        if (mode() != null) {
            exercise.setMode(mode());
        }
        exercise.setAssessmentType(AssessmentType.MANUAL);
        exercise.setMaxPoints(maxPoints());
        exercise.setBonusPoints(bonusPoints());
        if (includedInOverallScore() != null) {
            exercise.setIncludedInOverallScore(includedInOverallScore());
        }
        applyAssessmentFlagsTo(exercise);
        exercise.setGradingInstructions(gradingInstructions());
        exercise.setReleaseDate(releaseDate());
        exercise.setStartDate(startDate());
        exercise.setDueDate(dueDate());
        exercise.setAssessmentDueDate(assessmentDueDate());
        exercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate());
    }
}
