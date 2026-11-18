package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupReferenceDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * Slim list item for the course-management exercise table and the paged import search.
 * <p>
 * Every scalar here is bound by a table column or re-sent by the bulk "Edit selected" timeline modal, which rebuilds
 * its request body from this very list. The template and solution participations carry their submissions because the
 * table counts {@code participation.submissions[*].results}.
 *
 * @param id                                         the exercise id
 * @param type                                       the constant discriminator {@code "programming"}
 * @param title                                      the exercise title
 * @param shortName                                  the exercise short name
 * @param programmingLanguage                        the programming language
 * @param categories                                 the exercise categories as JSON-encoded strings
 * @param releaseDate                                when the exercise is released
 * @param startDate                                  when the exercise starts
 * @param dueDate                                    when the exercise is due
 * @param assessmentDueDate                          when the assessment is due
 * @param exampleSolutionPublicationDate             when the example solution becomes visible
 * @param buildAndTestStudentSubmissionsAfterDueDate when submissions are built and tested after the due date
 * @param assessmentType                             automatic, semi-automatic or manual assessment
 * @param maxPoints                                  the achievable points
 * @param bonusPoints                                the achievable bonus points
 * @param includedInOverallScore                     how the exercise counts towards the course score
 * @param presentationScoreEnabled                   whether the presentation score is enabled
 * @param mode                                       individual or team mode
 * @param teamMode                                   whether the exercise is a team exercise (gates the Teams action)
 * @param testCasesChanged                           whether the test cases changed since the last build
 * @param allowOfflineIde                            whether the offline IDE is allowed
 * @param allowOnlineEditor                          whether the online editor is allowed
 * @param allowOnlineIde                             whether the online IDE is allowed
 * @param projectKey                                 the VCS/CI project key
 * @param course                                     the nested course; populated for course exercises
 * @param exerciseGroup                              the nested exercise group; populated for exam exercises
 * @param templateParticipation                      the template participation
 * @param solutionParticipation                      the solution participation
 * @param exerciseVariantGroup                       the variant group owning the shared timeline, when fetched
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseListItemDTO(Long id, String type, String title, String shortName, ProgrammingLanguage programmingLanguage, Set<String> categories,
        ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate,
        ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, AssessmentType assessmentType, Double maxPoints, Double bonusPoints,
        IncludedInOverallScore includedInOverallScore, Boolean presentationScoreEnabled, ExerciseMode mode, Boolean teamMode, Boolean testCasesChanged, Boolean allowOfflineIde,
        Boolean allowOnlineEditor, Boolean allowOnlineIde, String projectKey, ProgrammingExerciseCourseDTO course, ProgrammingExerciseExamGroupDTO exerciseGroup,
        TemplateSolutionParticipationDTO templateParticipation, TemplateSolutionParticipationDTO solutionParticipation, ExerciseVariantGroupReferenceDTO exerciseVariantGroup)
        implements Serializable {

    /**
     * Creates a {@link ProgrammingExerciseListItemDTO} from the given exercise. Every lazy slot is guarded, so
     * uninitialized relations map to {@code null} instead of throwing.
     *
     * @param exercise the exercise to convert (may be {@code null})
     * @return the corresponding DTO, or {@code null} if the input was {@code null}
     */
    public static ProgrammingExerciseListItemDTO of(ProgrammingExercise exercise) {
        if (exercise == null) {
            return null;
        }

        ProgrammingExerciseCourseDTO course = ProgrammingExerciseCourseDTO.ofCourseExercise(exercise);
        ProgrammingExerciseExamGroupDTO exerciseGroup = ProgrammingExerciseExamGroupDTO.ofExamExercise(exercise);
        Set<String> categories = ProgrammingExerciseResponseDTO.copyCategories(exercise);

        return new ProgrammingExerciseListItemDTO(exercise.getId(), ProgrammingExerciseResponseDTO.TYPE, exercise.getTitle(), exercise.getShortName(),
                exercise.getProgrammingLanguage(), categories, exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(),
                exercise.getExampleSolutionPublicationDate(), exercise.getBuildAndTestStudentSubmissionsAfterDueDate(), exercise.getAssessmentType(), exercise.getMaxPoints(),
                exercise.getBonusPoints(), exercise.getIncludedInOverallScore(), exercise.getPresentationScoreEnabled(), exercise.getMode(), exercise.isTeamMode(),
                exercise.getTestCasesChanged(), exercise.isAllowOfflineIde(), exercise.isAllowOnlineEditor(), exercise.isAllowOnlineIde(), exercise.getProjectKey(), course,
                exerciseGroup, TemplateSolutionParticipationDTO.ofTemplate(exercise.getTemplateParticipation()),
                TemplateSolutionParticipationDTO.ofSolution(exercise.getSolutionParticipation()), ExerciseVariantGroupReferenceDTO.ofNullable(exercise.getExerciseVariantGroup()));
    }
}
