package de.tum.cit.aet.artemis.fileupload.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;

/**
 * Request DTO for creating or importing a file upload exercise. The target is represented by {@code courseId} or {@code exerciseGroupId} instead of nested entities.
 *
 * @param id                                     the legacy body identifier, used only for create validation and never copied to the entity
 * @param title                                  the exercise title
 * @param channelName                            the requested communication channel name
 * @param shortName                              the exercise short name
 * @param problemStatement                       the problem statement
 * @param categories                             the JSON-encoded exercise categories
 * @param difficulty                             the exercise difficulty
 * @param maxPoints                              the maximum points
 * @param bonusPoints                            the bonus points
 * @param includedInOverallScore                 how the exercise contributes to the course score
 * @param mode                                   whether the exercise is individual or team-based
 * @param teamAssignmentConfig                   the team-assignment settings
 * @param allowComplaintsForAutomaticAssessments whether complaints for automatic assessments are allowed
 * @param presentationScoreEnabled               whether presentation scores are enabled
 * @param secondCorrectionEnabled                whether a second correction round is enabled
 * @param gradingInstructions                    the free-text grading instructions
 * @param releaseDate                            the release date
 * @param startDate                              the start date
 * @param dueDate                                the due date
 * @param assessmentDueDate                      the assessment due date
 * @param exampleSolutionPublicationDate         the example-solution publication date
 * @param exampleSolution                        the example solution
 * @param filePattern                            the accepted file extensions
 * @param courseId                               the target course identifier for a course exercise
 * @param exerciseGroupId                        the target exercise-group identifier for an exam exercise
 * @param gradingCriteria                        the structured grading criteria
 * @param competencyLinks                        the competency links
 * @param plagiarismDetectionConfig              the plagiarism-detection settings
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadExerciseInputDTO(@Nullable Long id, @Nullable String title, @Nullable String channelName, @Nullable String shortName, @Nullable String problemStatement,
        @Nullable Set<String> categories, @Nullable DifficultyLevel difficulty, @Nullable Double maxPoints, @Nullable Double bonusPoints,
        @Nullable IncludedInOverallScore includedInOverallScore, @Nullable ExerciseMode mode, @Nullable FileUploadTeamAssignmentConfigDTO teamAssignmentConfig,
        @Nullable Boolean allowComplaintsForAutomaticAssessments, @Nullable Boolean presentationScoreEnabled, @Nullable Boolean secondCorrectionEnabled,
        @Nullable String gradingInstructions, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate,
        @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable String exampleSolution, @Nullable String filePattern,
        @Nullable Long courseId, @Nullable Long exerciseGroupId, @Nullable List<GradingCriterionDTO> gradingCriteria, @Nullable Set<CompetencyLinkDTO> competencyLinks,
        @Nullable FileUploadPlagiarismDetectionConfigDTO plagiarismDetectionConfig) implements CompetencyLinksHolderDTO {

    private static final String ENTITY_NAME = "fileUploadExercise";

    /**
     * Maps this request onto a new detached entity. Target identifiers are represented by id-only references and must be resolved by {@code CourseService} before authorization.
     *
     * @return a new file upload exercise containing only writable request fields
     * @throws BadRequestAlertException if both target identifiers or neither target identifier are supplied, or team mode has no configuration
     */
    public FileUploadExercise toEntity() {
        if ((courseId == null) == (exerciseGroupId == null)) {
            throw new BadRequestAlertException("An exercise must have either a courseId or an exerciseGroupId", ENTITY_NAME, "eitherCourseOrExerciseGroupSet");
        }
        if (mode == ExerciseMode.TEAM && teamAssignmentConfig == null) {
            throw new BadRequestAlertException("A team exercise must have a team assignment configuration", ENTITY_NAME, "teamAssignmentConfigMissing");
        }

        FileUploadExercise exercise = new FileUploadExercise();
        exercise.setTitle(title);
        exercise.setChannelName(channelName);
        exercise.setShortName(shortName);
        exercise.setProblemStatement(problemStatement);
        if (categories != null) {
            exercise.setCategories(categories);
        }
        exercise.setDifficulty(difficulty);
        exercise.setMaxPoints(maxPoints);
        exercise.setBonusPoints(bonusPoints);
        if (includedInOverallScore != null) {
            exercise.setIncludedInOverallScore(includedInOverallScore);
        }
        if (mode != null) {
            exercise.setMode(mode);
        }
        if (teamAssignmentConfig != null) {
            exercise.setTeamAssignmentConfig(teamAssignmentConfig.toNewEntity());
        }
        if (allowComplaintsForAutomaticAssessments != null) {
            exercise.setAllowComplaintsForAutomaticAssessments(allowComplaintsForAutomaticAssessments);
        }
        if (presentationScoreEnabled != null) {
            exercise.setPresentationScoreEnabled(presentationScoreEnabled);
        }
        if (secondCorrectionEnabled != null) {
            exercise.setSecondCorrectionEnabled(secondCorrectionEnabled);
        }
        exercise.setGradingInstructions(gradingInstructions);
        exercise.setReleaseDate(releaseDate);
        exercise.setStartDate(startDate);
        exercise.setDueDate(dueDate);
        exercise.setAssessmentDueDate(assessmentDueDate);
        exercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        exercise.setExampleSolution(exampleSolution);
        exercise.setFilePattern(filePattern);
        if (gradingCriteria != null) {
            exercise.setGradingCriteria(gradingCriteria.stream().map(GradingCriterionDTO::toEntity).collect(Collectors.toSet()));
        }
        if (plagiarismDetectionConfig != null) {
            exercise.setPlagiarismDetectionConfig(plagiarismDetectionConfig.toEntity());
        }

        if (courseId != null) {
            Course course = new Course();
            course.setId(courseId);
            exercise.setCourse(course);
        }
        else {
            ExerciseGroup exerciseGroup = new ExerciseGroup();
            exerciseGroup.setId(exerciseGroupId);
            exercise.setExerciseGroup(exerciseGroup);
        }
        return exercise;
    }
}
