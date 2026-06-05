package de.tum.cit.aet.artemis.text.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssignmentConfigDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismDetectionConfigDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Read DTO for a single {@link TextExercise}.
 * Dumb DTO: contains only scalars, enums, date/time values, and nested DTOs.
 * Lazy associations are guarded with {@link Hibernate#isInitialized(Object)} so uninitialized proxies map to {@code null}/empty.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextExerciseResponseDTO(Long id, String title, String shortName, String type, ExerciseType exerciseType, DifficultyLevel difficulty, ExerciseMode mode,
        Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate,
        ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, AssessmentType assessmentType, boolean secondCorrectionEnabled,
        Boolean presentationScoreEnabled, String problemStatement, String exampleSolution, String gradingInstructions, Set<String> categories, String channelName,
        String feedbackSuggestionModule, boolean allowComplaintsForAutomaticAssessments, boolean allowFeedbackRequests, Long courseId, Double courseAccuracyOfScores,
        Long exerciseGroupId, Long examId, ZonedDateTime examPublishResultsDate, TeamAssignmentConfigDTO teamAssignmentConfig, Set<GradingCriterionDTO> gradingCriteria,
        Set<CompetencyLinkDTO> competencyLinks, PlagiarismDetectionConfigDTO plagiarismDetectionConfig, boolean gradingInstructionFeedbackUsed) implements Serializable {

    /**
     * Creates a {@link TextExerciseResponseDTO} from the given {@link TextExercise}.
     *
     * @param exercise the text exercise to convert (may be {@code null})
     * @return the corresponding DTO, or {@code null} if the input was {@code null}
     */
    public static TextExerciseResponseDTO of(TextExercise exercise) {
        if (exercise == null) {
            return null;
        }

        Long courseId = null;
        Double courseAccuracyOfScores = null;
        Long exerciseGroupId = null;
        Long examId = null;
        ZonedDateTime examPublishResultsDate = null;

        if (exercise.isExamExercise()) {
            exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null;
            Exam exam = exercise.getExam();
            if (exam != null) {
                examId = exam.getId();
                examPublishResultsDate = exam.getPublishResultsDate();
            }
        }
        else {
            Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
            if (course != null) {
                courseId = course.getId();
                courseAccuracyOfScores = course.getAccuracyOfScores() != null ? course.getAccuracyOfScores().doubleValue() : null;
            }
        }

        Set<GradingCriterionDTO> gradingCriterionDTOs;
        Set<GradingCriterion> criteria = exercise.getGradingCriteria();
        if (criteria != null && Hibernate.isInitialized(criteria)) {
            gradingCriterionDTOs = criteria.isEmpty() ? Set.of() : criteria.stream().map(GradingCriterionDTO::of).collect(Collectors.toSet());
        }
        else {
            gradingCriterionDTOs = null;
        }

        Set<CompetencyLinkDTO> competencyLinkDTOs;
        Set<CompetencyExerciseLink> competencyLinks = exercise.getCompetencyLinks();
        if (competencyLinks != null && Hibernate.isInitialized(competencyLinks)) {
            competencyLinkDTOs = competencyLinks.isEmpty() ? Set.of() : competencyLinks.stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet());
        }
        else {
            competencyLinkDTOs = null;
        }

        TeamAssignmentConfigDTO teamAssignmentConfigDTO = Hibernate.isInitialized(exercise.getTeamAssignmentConfig())
                ? TeamAssignmentConfigDTO.of(exercise.getTeamAssignmentConfig())
                : null;

        PlagiarismDetectionConfigDTO plagiarismDetectionConfigDTO = Hibernate.isInitialized(exercise.getPlagiarismDetectionConfig())
                ? PlagiarismDetectionConfigDTO.of(exercise.getPlagiarismDetectionConfig())
                : null;

        return new TextExerciseResponseDTO(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getType(), exercise.getExerciseType(), exercise.getDifficulty(),
                exercise.getMode(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(), exercise.getReleaseDate(), exercise.getStartDate(),
                exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), exercise.getAssessmentType(),
                exercise.getSecondCorrectionEnabled(), exercise.getPresentationScoreEnabled(), exercise.getProblemStatement(), exercise.getExampleSolution(),
                exercise.getGradingInstructions(), exercise.getCategories(), exercise.getChannelName(), exercise.getFeedbackSuggestionModule(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), courseId, courseAccuracyOfScores, exerciseGroupId, examId,
                examPublishResultsDate, teamAssignmentConfigDTO, gradingCriterionDTOs, competencyLinkDTOs, plagiarismDetectionConfigDTO,
                exercise.isGradingInstructionFeedbackUsed());
    }
}
