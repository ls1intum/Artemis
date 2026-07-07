package de.tum.cit.aet.artemis.exam.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Slim, flat summary of an {@link Exercise} as embedded in an {@link ExerciseGroupDTO}.
 * <p>
 * This carries exactly the fields the exam exercise-group management table and its per-type cells / row-buttons read
 * (id, type, title, points, inclusion, assessment mode, team mode, plus the type-specific scalars shown in the
 * programming / modeling / file-upload columns). Lazily-loaded sub-relations of an exercise (participations, quiz
 * questions) are intentionally omitted: the exercise-group list / import endpoints only hydrate the exercises
 * themselves (not their sub-relations), so those never appeared on the wire and the client reads them defensively with
 * optional chaining.
 *
 * @param id                         the id of the exercise
 * @param type                       the exercise type discriminator (serialized as the lowercase value, e.g. "programming")
 * @param title                      the exercise title
 * @param maxPoints                  the maximum points achievable for the exercise
 * @param bonusPoints                the bonus points achievable for the exercise
 * @param includedInOverallScore     whether the exercise counts towards the overall score
 * @param assessmentType             the assessment mode (automatic / semi-automatic / manual)
 * @param teamMode                   {@code true} if the exercise is a team exercise (always individual for exam exercises)
 * @param testRunParticipationsExist whether test-run participations exist (transient; not populated on these endpoints)
 * @param shortName                  the exercise short name (populated for programming exercises)
 * @param projectKey                 the VCS/CI project key (programming exercises only)
 * @param allowOfflineIde            whether the offline IDE is allowed (programming exercises only)
 * @param allowOnlineEditor          whether the online editor is allowed (programming exercises only)
 * @param allowOnlineIde             whether the online IDE is allowed (programming exercises only)
 * @param diagramType                the UML diagram type (modeling exercises only)
 * @param filePattern                the accepted file pattern (file-upload exercises only)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseForExerciseGroupDTO(long id, ExerciseType type, @Nullable String title, @Nullable Double maxPoints, @Nullable Double bonusPoints,
        @Nullable IncludedInOverallScore includedInOverallScore, @Nullable AssessmentType assessmentType, boolean teamMode, @Nullable Boolean testRunParticipationsExist,
        @Nullable String shortName, @Nullable String projectKey, @Nullable Boolean allowOfflineIde, @Nullable Boolean allowOnlineEditor, @Nullable Boolean allowOnlineIde,
        @Nullable DiagramType diagramType, @Nullable String filePattern) {

    /**
     * Builds a summary from an exercise, extracting the type-specific scalar fields via pattern matching on the concrete
     * subtype. Only stored scalar columns are read (no lazy sub-relations are touched), so this is safe to call on a
     * detached entity outside a transaction.
     *
     * @param exercise the exercise to summarize
     * @return the summary DTO
     */
    public static ExerciseForExerciseGroupDTO of(Exercise exercise) {
        String projectKey = null;
        Boolean allowOfflineIde = null;
        Boolean allowOnlineEditor = null;
        Boolean allowOnlineIde = null;
        DiagramType diagramType = null;
        String filePattern = null;

        switch (exercise) {
            case ProgrammingExercise programmingExercise -> {
                projectKey = programmingExercise.getProjectKey();
                allowOfflineIde = programmingExercise.isAllowOfflineIde();
                allowOnlineEditor = programmingExercise.isAllowOnlineEditor();
                allowOnlineIde = programmingExercise.isAllowOnlineIde();
            }
            case ModelingExercise modelingExercise -> diagramType = modelingExercise.getDiagramType();
            case FileUploadExercise fileUploadExercise -> filePattern = fileUploadExercise.getFilePattern();
            default -> {
                // text and quiz exercises carry no additional type-specific columns
            }
        }

        return new ExerciseForExerciseGroupDTO(exercise.getId(), exercise.getExerciseType(), exercise.getTitle(), exercise.getMaxPoints(), exercise.getBonusPoints(),
                exercise.getIncludedInOverallScore(), exercise.getAssessmentType(), exercise.isTeamMode(), exercise.getTestRunParticipationsExist(), exercise.getShortName(),
                projectKey, allowOfflineIde, allowOnlineEditor, allowOnlineIde, diagramType, filePattern);
    }
}
