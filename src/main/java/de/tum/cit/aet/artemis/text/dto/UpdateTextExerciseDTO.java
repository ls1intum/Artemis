package de.tum.cit.aet.artemis.text.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseMetadataDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssignmentConfigDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismDetectionConfigDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * DTO for updating text exercises.
 * Uses DTOs instead of entity classes to avoid Hibernate detached entity issues.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateTextExerciseDTO(Long id, String title, String channelName, String shortName, String problemStatement, Set<String> categories, DifficultyLevel difficulty,
        Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore, Boolean allowComplaintsForAutomaticAssessments, Boolean presentationScoreEnabled,
        Boolean secondCorrectionEnabled, String gradingInstructions, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate,
        ZonedDateTime exampleSolutionPublicationDate, String exampleSolution, Long courseId, Long exerciseGroupId, ExerciseMode mode, TeamAssignmentConfigDTO teamAssignmentConfig,
        List<GradingCriterionDTO> gradingCriteria, Set<CompetencyLinkDTO> competencyLinks, PlagiarismDetectionConfigDTO plagiarismDetectionConfig)
        implements CompetencyLinksHolderDTO, ExerciseMetadataDTO {

    /**
     * Creates an UpdateTextExerciseDTO from the given TextExercise domain object.
     *
     * @param exercise the text exercise to convert
     * @return the corresponding DTO
     */
    public static UpdateTextExerciseDTO of(TextExercise exercise) {
        if (exercise == null) {
            throw new BadRequestAlertException("No text exercise was provided.", "textExercise", "textExercise.isNull");
        }

        // Only a directly-attached course yields a courseId (isCourseExercise() checks the direct course field), so an exam
        // exercise yields courseId == null, mirroring the client which sends only the exerciseGroupId for exam exercises and
        // keeps the course/exerciseGroup exclusivity intact.
        Long courseId = exercise.isCourseExercise() ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        Long exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null;

        List<GradingCriterionDTO> gradingCriterionDTOs = GradingCriterionDTO.ofAllIfInitialized(exercise.getGradingCriteria());
        Set<CompetencyLinkDTO> competencyLinkDTOs = CompetencyLinkDTO.ofAllIfInitialized(exercise.getCompetencyLinks());

        // Only expose the plagiarism config when the lazy association is already initialized
        PlagiarismDetectionConfigDTO plagiarismDetectionConfigDTO = Hibernate.isInitialized(exercise.getPlagiarismDetectionConfig())
                ? PlagiarismDetectionConfigDTO.of(exercise.getPlagiarismDetectionConfig())
                : null;

        return new UpdateTextExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(),
                exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getPresentationScoreEnabled(), exercise.getSecondCorrectionEnabled(),
                exercise.getGradingInstructions(), exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(),
                exercise.getExampleSolutionPublicationDate(), exercise.getExampleSolution(), courseId, exerciseGroupId, exercise.getMode(),
                TeamAssignmentConfigDTO.of(exercise.getTeamAssignmentConfig()), gradingCriterionDTOs, competencyLinkDTOs, plagiarismDetectionConfigDTO);
    }
}
