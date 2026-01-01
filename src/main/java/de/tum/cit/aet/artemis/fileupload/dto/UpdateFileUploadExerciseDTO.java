package de.tum.cit.aet.artemis.fileupload.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;

/**
 * Data Transfer Object for updating a {@link FileUploadExercise}.
 * <p>
 * <b>Why use a DTO instead of the entity directly?</b>
 * <ul>
 * <li><b>Security:</b> Prevents mass assignment vulnerabilities by explicitly defining which fields can be updated.
 * The client cannot inject values for fields like {@code id}, {@code course}, or internal state.</li>
 * <li><b>API contract:</b> Provides a clear, stable API contract that is decoupled from the internal entity structure.</li>
 * <li><b>Validation:</b> Allows request-specific validation without polluting the entity with HTTP-layer concerns.</li>
 * </ul>
 * <p>
 * <b>Usage pattern:</b>
 * <ol>
 * <li>Client sends this DTO to the update endpoint</li>
 * <li>Server loads the existing entity from the database</li>
 * <li>Server applies DTO values to the entity (see {@code FileUploadExerciseResource.update()})</li>
 * <li>Server saves the modified entity</li>
 * </ol>
 * <p>
 * <b>Field semantics:</b>
 * <ul>
 * <li>{@code courseId} / {@code exerciseGroupId}: Identifies whether this is a course exercise or exam exercise.
 * These are validated but not used to change the exercise's course/exam association.</li>
 * <li>{@code gradingCriteria}: Full replacement semantics - the provided set replaces all existing criteria.</li>
 * <li>{@code competencyLinks}: Full replacement semantics - the provided set replaces all existing links.</li>
 * </ul>
 *
 * @param id                                     the exercise ID (must match the path variable)
 * @param title                                  the exercise title
 * @param channelName                            the communication channel name
 * @param shortName                              the short name used for identification
 * @param problemStatement                       the problem description shown to students
 * @param categories                             exercise categories as JSON-encoded strings
 * @param difficulty                             the difficulty level
 * @param maxPoints                              maximum achievable points
 * @param bonusPoints                            additional bonus points
 * @param includedInOverallScore                 how this exercise counts toward the course grade
 * @param allowComplaintsForAutomaticAssessments whether complaints are allowed
 * @param allowFeedbackRequests                  whether feedback requests are enabled
 * @param presentationScoreEnabled               whether presentation scores are tracked
 * @param secondCorrectionEnabled                whether second correction round is enabled
 * @param feedbackSuggestionModule               the AI feedback suggestion module identifier
 * @param gradingInstructions                    free-text grading instructions for tutors
 * @param releaseDate                            when the exercise becomes visible to students
 * @param startDate                              when students can start working
 * @param dueDate                                submission deadline
 * @param assessmentDueDate                      deadline for tutors to complete assessments
 * @param exampleSolutionPublicationDate         when the example solution becomes visible
 * @param exampleSolution                        the example solution text
 * @param filePattern                            allowed file extensions (e.g., "pdf, png")
 * @param courseId                               the course ID (for course exercises)
 * @param exerciseGroupId                        the exam exercise group ID (for exam exercises)
 * @param gradingCriteria                        structured grading criteria with rubrics
 * @param competencyLinks                        links to course competencies with weights
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateFileUploadExerciseDTO(long id, String title, String channelName, String shortName, String problemStatement, Set<String> categories, DifficultyLevel difficulty,
        Double maxPoints, Double bonusPoints, IncludedInOverallScore includedInOverallScore, Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests,
        Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, String feedbackSuggestionModule, String gradingInstructions, ZonedDateTime releaseDate,
        ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, String exampleSolution, String filePattern,
        Long courseId, Long exerciseGroupId, Set<GradingCriterionDTO> gradingCriteria, Set<CompetencyLinkDTO> competencyLinks) implements CompetencyLinksHolderDTO {

    /**
     * Creates a DTO from a {@link FileUploadExercise} entity.
     * <p>
     * This is used when sending exercise data to the client for editing.
     * Lazy-loaded collections (grading criteria, competency links) are only included
     * if they have been initialized to avoid N+1 query issues.
     *
     * @param exercise the FileUploadExercise entity to convert
     * @return a new DTO with data copied from the entity
     * @throws BadRequestAlertException if exercise is null
     */
    public static UpdateFileUploadExerciseDTO of(FileUploadExercise exercise) {
        if (exercise == null) {
            throw new BadRequestAlertException("No fileUpload exercise was provided.", "FileUploadExercise", "isNull");
        }
        // TODO: unify duplicated code with UpdateModelingExerciseDTO.of
        Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        Long exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null;

        Set<GradingCriterionDTO> gradingCriterionDTOs = null;
        Set<CompetencyLinkDTO> competencyLinkDTOs = null;

        Set<GradingCriterion> criteria = exercise.getGradingCriteria();
        Set<CompetencyExerciseLink> competencyLinks = exercise.getCompetencyLinks();

        if (criteria != null && Hibernate.isInitialized(criteria)) {
            gradingCriterionDTOs = criteria.isEmpty() ? Set.of() : criteria.stream().map(GradingCriterionDTO::of).collect(Collectors.toSet());
        }
        if (competencyLinks != null && Hibernate.isInitialized(competencyLinks)) {
            competencyLinkDTOs = competencyLinks.isEmpty() ? Set.of() : competencyLinks.stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet());
        }
        return new UpdateFileUploadExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(),
                exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getPresentationScoreEnabled(),
                exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(), exercise.getReleaseDate(),
                exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), exercise.getExampleSolution(),
                exercise.getFilePattern(), courseId, exerciseGroupId, gradingCriterionDTOs, competencyLinkDTOs);
    }
}
