package de.tum.cit.aet.artemis.modeling.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseForQuizExerciseDTO;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupReferenceDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssignmentConfigDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.util.ModelingDtoCollections;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismDetectionConfigDTO;

/**
 * Read DTO for a single {@link ModelingExercise}.
 * Dumb DTO: contains only scalars, enums, date/time values, and nested DTOs.
 * Lazy associations are guarded with {@link Hibernate#isInitialized(Object)} so uninitialized proxies map to {@code null}/empty.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ModelingExerciseResponseDTO(Long id, String title, String shortName, String type, ExerciseType exerciseType, DifficultyLevel difficulty, ExerciseMode mode,
        Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate,
        ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, AssessmentType assessmentType, boolean secondCorrectionEnabled,
        Boolean presentationScoreEnabled, String problemStatement, DiagramType diagramType, String exampleSolutionModel, String exampleSolutionExplanation,
        String gradingInstructions, Set<String> categories, String channelName, boolean allowComplaintsForAutomaticAssessments, Long courseId, Double courseAccuracyOfScores,
        CourseForQuizExerciseDTO course, Long exerciseGroupId, Long examId, ZonedDateTime examPublishResultsDate, TeamAssignmentConfigDTO teamAssignmentConfig,
        List<GradingCriterionDTO> gradingCriteria, Set<CompetencyLinkDTO> competencyLinks, PlagiarismDetectionConfigDTO plagiarismDetectionConfig,
        boolean gradingInstructionFeedbackUsed, Set<ModelingExampleSubmissionDTO> exampleSubmissions, Boolean teamMode, ModelingExerciseExamGroupDTO exerciseGroup,
        ExerciseVariantGroupReferenceDTO exerciseVariantGroup) implements Serializable {

    /**
     * Creates a {@link ModelingExerciseResponseDTO} from the given {@link ModelingExercise}.
     *
     * @param exercise the modeling exercise to convert (may be {@code null})
     * @return the corresponding DTO, or {@code null} if the input was {@code null}
     */
    public static ModelingExerciseResponseDTO of(ModelingExercise exercise) {
        if (exercise == null) {
            return null;
        }

        Long courseId = null;
        Double courseAccuracyOfScores = null;
        // Light nested course projection (id, title, group names, complaint config, ...) so the unchanged client can read
        // exercise.course for display links and to compute access rights (account.service reads the course group names).
        // Only populated for course exercises, mirroring the original entity where exercise.course was set for course
        // exercises only; exam exercises resolve their course client-side via the exercise group.
        CourseForQuizExerciseDTO course = null;
        Long exerciseGroupId = null;
        Long examId = null;
        ZonedDateTime examPublishResultsDate = null;
        // The student modeling editor / exam result summary detect exam mode from the presence of exercise.exerciseGroup
        // and read exercise.exerciseGroup.exam.publishResultsDate; carry that nested shape for exam exercises.
        ModelingExerciseExamGroupDTO exerciseGroup = null;

        if (exercise.isExamExercise()) {
            exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null;
            exerciseGroup = ModelingExerciseExamGroupDTO.of(exercise.getExerciseGroup());
            Exam exam = exercise.getExam();
            if (exam != null) {
                examId = exam.getId();
                examPublishResultsDate = exam.getPublishResultsDate();
            }
        }
        else {
            Course courseEntity = exercise.getCourseViaExerciseGroupOrCourseMember();
            if (courseEntity != null) {
                courseId = courseEntity.getId();
                courseAccuracyOfScores = courseEntity.getAccuracyOfScores() != null ? courseEntity.getAccuracyOfScores().doubleValue() : null;
                course = CourseForQuizExerciseDTO.of(courseEntity);
            }
        }

        List<GradingCriterionDTO> gradingCriterionDTOs = ModelingDtoCollections.listFromInitializedSet(exercise.getGradingCriteria(), GradingCriterionDTO::of);

        Set<CompetencyLinkDTO> competencyLinkDTOs = ModelingDtoCollections.setFromInitializedSet(exercise.getCompetencyLinks(), CompetencyLinkDTO::of);

        TeamAssignmentConfigDTO teamAssignmentConfigDTO = Hibernate.isInitialized(exercise.getTeamAssignmentConfig())
                ? TeamAssignmentConfigDTO.of(exercise.getTeamAssignmentConfig())
                : null;

        PlagiarismDetectionConfigDTO plagiarismDetectionConfigDTO = Hibernate.isInitialized(exercise.getPlagiarismDetectionConfig())
                ? PlagiarismDetectionConfigDTO.of(exercise.getPlagiarismDetectionConfig())
                : null;

        // Only populated on the single-exercise detail endpoint, which explicitly loads example submissions; null/omitted elsewhere.
        Set<ModelingExampleSubmissionDTO> exampleSubmissionDTOs = ModelingDtoCollections.setFromInitializedSet(exercise.getExampleSubmissions(), ModelingExampleSubmissionDTO::of);

        // The exercise edit form renders its timeline as read-only "locked to group" pickers when the exercise belongs to a
        // variant group, so the group reference has to travel with the exercise.
        ExerciseVariantGroupReferenceDTO exerciseVariantGroupDTO = ExerciseVariantGroupReferenceDTO.ofNullable(exercise.getExerciseVariantGroup());

        // categories is a LAZY @ElementCollection; copy it (guarded) so the DTO never holds the live Hibernate persistent
        // set (a DTO toString via LoggingAspect would otherwise trigger a LazyInitializationException on Exercise.categories).
        Set<String> categories = ModelingDtoCollections.copyInitializedSet(exercise.getCategories());

        return new ModelingExerciseResponseDTO(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getType(), exercise.getExerciseType(),
                exercise.getDifficulty(), exercise.getMode(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), exercise.getAssessmentType(),
                exercise.getSecondCorrectionEnabled(), exercise.getPresentationScoreEnabled(), exercise.getProblemStatement(), exercise.getDiagramType(),
                exercise.getExampleSolutionModel(), exercise.getExampleSolutionExplanation(), exercise.getGradingInstructions(), categories, exercise.getChannelName(),
                exercise.getAllowComplaintsForAutomaticAssessments(), courseId, courseAccuracyOfScores, course, exerciseGroupId, examId, examPublishResultsDate,
                teamAssignmentConfigDTO, gradingCriterionDTOs, competencyLinkDTOs, plagiarismDetectionConfigDTO, exercise.isGradingInstructionFeedbackUsed(), exampleSubmissionDTOs,
                exercise.getMode() == ExerciseMode.TEAM, exerciseGroup, exerciseVariantGroupDTO);
    }
}
