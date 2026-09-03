package de.tum.cit.aet.artemis.exercise.dto;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.programming.domain.MilestoneExercise;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;

/**
 * Payload for creating a {@link UserStoryExercise} in a {@code MilestoneExerciseGroup}. These are exactly the settings a
 * user story owns for itself; everything else is the group's.
 * <p>
 * Notably absent, because {@code MilestoneExerciseService.createUserStoryExercise} and
 * {@code UserStoryExerciseService.applyMilestoneConfig} overwrite them from the group's {@link MilestoneExercise}
 * regardless of what a request carries:
 * <ul>
 * <li>the timeline (release/start/due/assessment-due/example-solution-publication dates), which the whole group shares</li>
 * <li>every Language/Version-Control setting: programming language, project type, package name, the online editor/offline
 * IDE/online IDE flags, the build config, the template/solution/test repositories and the project key</li>
 * <li>static code analysis, which describes the shared codebase and is therefore configured and priced once on the
 * milestone</li>
 * <li>test cases and tasks, duplicated from the milestone's shared suite rather than submitted</li>
 * <li>{@code includedInOverallScore}, always {@code INCLUDED_COMPLETELY} (a user story's points count through its group),
 * {@code feedbackSuggestionModule}, and the owning course / exercise group / variant group, which come from the request
 * path</li>
 * </ul>
 * The client hides these same fields on the shared exercise form; see {@code USER_STORY_HIDDEN_FIELDS}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CreateUserStoryExerciseDTO(@NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 255) String shortName, @Nullable String channelName,
        @Nullable String problemStatement, @Nullable Set<String> categories, @Nullable DifficultyLevel difficulty, @Nullable ExerciseMode mode, @NotNull Double maxPoints,
        @Nullable Double bonusPoints, @Nullable AssessmentType assessmentType, @Nullable Boolean allowComplaintsForAutomaticAssessments, @Nullable Boolean allowFeedbackRequests,
        @Nullable Boolean presentationScoreEnabled, @Nullable Boolean secondCorrectionEnabled, @Nullable String gradingInstructions,
        @Nullable Set<GradingCriterionDTO> gradingCriteria, @Nullable Set<CompetencyLinkDTO> competencyLinks) implements CompetencyLinksHolderDTO {

    /**
     * Builds the (still transient) user story exercise this payload describes. Only the fields a user story owns for
     * itself are set here - the caller applies the group's configuration and timeline on top, and persists the result.
     * <p>
     * Competency links are deliberately not built here: a {@code CompetencyExerciseLink} needs the exercise's id, so the
     * caller resolves them after the first save (see {@code CompetencyExerciseLinkService.addCompetencyLinksForCreation}).
     *
     * @return the user story exercise to hand to the creation pipeline
     */
    public UserStoryExercise toUserStoryExercise() {
        UserStoryExercise exercise = new UserStoryExercise();
        exercise.setTitle(title);
        exercise.setShortName(shortName);
        exercise.setChannelName(channelName);
        exercise.setProblemStatement(problemStatement);
        exercise.setCategories(categories == null ? new HashSet<>() : new HashSet<>(categories));
        exercise.setDifficulty(difficulty);
        exercise.setMode(mode == null ? ExerciseMode.INDIVIDUAL : mode);
        exercise.setMaxPoints(maxPoints);
        exercise.setBonusPoints(bonusPoints == null ? 0.0 : bonusPoints);
        exercise.setAssessmentType(assessmentType);
        exercise.setAllowComplaintsForAutomaticAssessments(Boolean.TRUE.equals(allowComplaintsForAutomaticAssessments));
        exercise.setAllowFeedbackRequests(Boolean.TRUE.equals(allowFeedbackRequests));
        exercise.setPresentationScoreEnabled(Boolean.TRUE.equals(presentationScoreEnabled));
        exercise.setSecondCorrectionEnabled(Boolean.TRUE.equals(secondCorrectionEnabled));
        exercise.setGradingInstructions(gradingInstructions);
        if (gradingCriteria != null) {
            // setGradingCriteria reconnects each criterion's back-reference, so they cascade with the exercise's own save.
            exercise.setGradingCriteria(gradingCriteria.stream().map(GradingCriterionDTO::toEntity).collect(Collectors.toCollection(HashSet::new)));
        }
        return exercise;
    }
}
