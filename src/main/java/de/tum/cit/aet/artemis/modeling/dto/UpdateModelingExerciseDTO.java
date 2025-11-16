package de.tum.cit.aet.artemis.modeling.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UpdateModelingExerciseDTO(long id, @Nullable String title, @Nullable String channelName, @Nullable String shortName, @Nullable String problemStatement,
        @Nullable Set<String> categories, @Nullable DifficultyLevel difficulty, @Nullable @Positive Double maxPoints, @Nullable @Positive Double bonusPoints,
        @Nullable IncludedInOverallScore includedInOverallScore, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate,
        @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable String exampleSolutionModel,
        @Nullable String exampleSolutionExplanation, @Nullable Long courseId, @Nullable Long exerciseGroupId, @Nullable Set<GradingCriterionDTO> gradingCriteria,
        @Nullable String gradingInstructions) {

    /**
     * Returns a new ModelingExercise with this DTO applied on top of the existing exercise.
     * The existing exercise is treated as the "before" state and is not modified.
     *
     * @param existingExercise the exercise to use as base state
     * @return a new ModelingExercise instance representing the updated state
     */
    public ModelingExercise update(ModelingExercise existingExercise) {
        ModelingExercise updated = new ModelingExercise();

        updated.setId(existingExercise.getId());
        updated.setTitle(existingExercise.getTitle());
        updated.setChannelName(existingExercise.getChannelName());
        updated.setShortName(existingExercise.getShortName());
        updated.setProblemStatement(existingExercise.getProblemStatement());
        updated.setCategories(existingExercise.getCategories());
        updated.setDifficulty(existingExercise.getDifficulty());
        updated.setMaxPoints(existingExercise.getMaxPoints());
        updated.setBonusPoints(existingExercise.getBonusPoints());
        updated.setIncludedInOverallScore(existingExercise.getIncludedInOverallScore());
        if (existingExercise.isExamExercise()) {
            updated.setExerciseGroup(existingExercise.getExerciseGroup());
        }
        else {
            updated.setCourse(existingExercise.getCourseViaExerciseGroupOrCourseMember());
        }
        updated.setMode(existingExercise.getMode());
        updated.setReleaseDate(existingExercise.getReleaseDate());
        updated.setStartDate(existingExercise.getStartDate());
        updated.setDueDate(existingExercise.getDueDate());
        updated.setAssessmentDueDate(existingExercise.getAssessmentDueDate());
        updated.setExampleSolutionModel(existingExercise.getExampleSolutionModel());
        updated.setExampleSolutionExplanation(existingExercise.getExampleSolutionExplanation());
        updated.setExampleSolutionPublicationDate(existingExercise.getExampleSolutionPublicationDate());
        updated.setGradingInstructions(existingExercise.getGradingInstructions());
        updated.setGradingCriteria(existingExercise.getGradingCriteria());
        updated.setDiagramType(existingExercise.getDiagramType());
        updated.setCompetencyLinks(existingExercise.getCompetencyLinks());
        updated.setFeedbackSuggestionModule(existingExercise.getFeedbackSuggestionModule());
        updated.setGradingInstructionFeedbackUsed(existingExercise.isGradingInstructionFeedbackUsed());
        updated.setAssessmentType(existingExercise.getAssessmentType());
        updated.setAllowComplaintsForAutomaticAssessments(existingExercise.getAllowComplaintsForAutomaticAssessments());
        updated.setAllowFeedbackRequests(existingExercise.getAllowFeedbackRequests());
        updated.setAttachments(existingExercise.getAttachments());
        updated.setExampleSubmissions(existingExercise.getExampleSubmissions());
        updated.setStudentParticipations(existingExercise.getStudentParticipations());
        updated.setTutorParticipations(existingExercise.getTutorParticipations());
        updated.setTeams(existingExercise.getTeams());
        updated.setTeamAssignmentConfig(existingExercise.getTeamAssignmentConfig());
        updated.setPlagiarismCases(existingExercise.getPlagiarismCases());
        updated.setPlagiarismDetectionConfig(existingExercise.getPlagiarismDetectionConfig());
        updated.setPresentationScoreEnabled(existingExercise.getPresentationScoreEnabled());
        updated.setSecondCorrectionEnabled(existingExercise.getSecondCorrectionEnabled());
        if (this.title != null) {
            updated.setTitle(this.title);
        }
        if (this.channelName != null) {
            updated.setChannelName(this.channelName);
        }
        if (this.shortName != null) {
            updated.setShortName(this.shortName);
        }
        if (this.problemStatement != null) {
            updated.setProblemStatement(this.problemStatement);
        }
        if (this.categories != null) {
            updated.setCategories(this.categories);
        }
        if (this.difficulty != null) {
            updated.setDifficulty(this.difficulty);
        }
        if (this.maxPoints != null) {
            updated.setMaxPoints(this.maxPoints);
        }
        if (this.bonusPoints != null) {
            updated.setBonusPoints(this.bonusPoints);
        }
        if (this.includedInOverallScore != null) {
            updated.setIncludedInOverallScore(this.includedInOverallScore);
        }
        if (this.releaseDate != null) {
            updated.setReleaseDate(this.releaseDate);
        }
        if (this.startDate != null) {
            updated.setStartDate(this.startDate);
        }
        if (this.dueDate != null) {
            updated.setDueDate(this.dueDate);
        }
        if (this.assessmentDueDate != null) {
            updated.setAssessmentDueDate(this.assessmentDueDate);
        }
        if (this.exampleSolutionModel != null) {
            updated.setExampleSolutionModel(this.exampleSolutionModel);
        }
        if (this.exampleSolutionExplanation != null) {
            updated.setExampleSolutionExplanation(this.exampleSolutionExplanation);
        }
        if (this.exampleSolutionPublicationDate != null) {
            updated.setExampleSolutionPublicationDate(this.exampleSolutionPublicationDate);
        }
        if (this.gradingCriteria != null) {
            updated.setGradingCriteria(this.gradingCriteria.stream().map(GradingCriterionDTO::toEntity).collect(Collectors.toSet()));
        }
        if (this.gradingInstructions != null) {
            updated.setGradingInstructions(this.gradingInstructions);
        }

        return updated;
    }

    /**
     * Creates a DTO from a ModelingExercise entity.
     * Used when you need to send exercise data to the client for editing.
     *
     * @param exercise the ModelingExercise entity to convert
     * @return a new UpdateModelingExerciseDTO with data from the entity
     */
    public static UpdateModelingExerciseDTO of(ModelingExercise exercise) {
        Long courseId = exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null;
        Long exerciseGroupId = exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null;

        Set<GradingCriterionDTO> gradingCriterionDTOs = exercise.getGradingCriteria() != null
                ? exercise.getGradingCriteria().stream().map(GradingCriterionDTO::of).collect(Collectors.toSet())
                : Set.of();

        return new UpdateModelingExerciseDTO(exercise.getId(), exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(),
                exercise.getCategories(), exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(),
                exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(),
                exercise.getExampleSolutionModel(), exercise.getExampleSolutionExplanation(), courseId, exerciseGroupId, gradingCriterionDTOs, exercise.getGradingInstructions());
    }
}
