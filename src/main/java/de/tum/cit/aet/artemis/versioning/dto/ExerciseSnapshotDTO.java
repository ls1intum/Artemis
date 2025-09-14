package de.tum.cit.aet.artemis.versioning.dto;

import java.io.Serializable;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExerciseSnapshotDTO(
        // fields of BaseExercise class
        Long id, String title, String shortName, Double maxPoints, Double bonusPoints, AssessmentType assessmentType, ZonedDateTime releaseDate, ZonedDateTime startDate,
        ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, DifficultyLevel difficulty, ExerciseMode mode,

        // fields of Exercise class
        // not included fields: teams, studentParticipations, tutorParticipations, exampleSubmission, attachment, course, exerciseGroup
        Set<CompetencyExerciseLinkSnapshotDTO> competencyLinks, Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests,
        IncludedInOverallScore includedInOverallScore, String problemStatement, String gradingInstructions, Set<String> categories,
        TeamAssignmentConfigSnapshotDTO teamAssignmentConfig, Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, String feedbackSuggestionModule,
        Set<GradingCriterionDTO> gradingCriteria, PlagiarismDetectionConfig plagiarismDetectionConfig, ProgrammingExerciseSnapshotDTO programmingData,
        TextExerciseSnapshotDTO textData, ModelingExerciseSnapshotDTO modelingData, QuizExerciseSnapshotDTO quizData, FileUploadExerciseSnapshotDTO fileUploadData

) implements Serializable {

    public static ExerciseSnapshotDTO of(Exercise exercise, GitService gitService) {

        var programmingData = exercise instanceof ProgrammingExercise ? ProgrammingExerciseSnapshotDTO.of((ProgrammingExercise) exercise, gitService) : null;
        var textData = exercise instanceof TextExercise ? TextExerciseSnapshotDTO.of((TextExercise) exercise) : null;
        var modelingData = exercise instanceof ModelingExercise ? ModelingExerciseSnapshotDTO.of((ModelingExercise) exercise) : null;
        var quizData = exercise instanceof QuizExercise ? QuizExerciseSnapshotDTO.of((QuizExercise) exercise) : null;
        var fileUploadData = exercise instanceof FileUploadExercise ? FileUploadExerciseSnapshotDTO.of((FileUploadExercise) exercise) : null;
        return new ExerciseSnapshotDTO(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getMaxPoints(), exercise.getBonusPoints(),
                exercise.getAssessmentType(), toUtc(exercise.getReleaseDate()), toUtc(exercise.getStartDate()), toUtc(exercise.getDueDate()),
                toUtc(exercise.getAssessmentDueDate()), toUtc(exercise.getExampleSolutionPublicationDate()), exercise.getDifficulty(), exercise.getMode(),
                exercise.getCompetencyLinks().stream().map(CompetencyExerciseLinkSnapshotDTO::of).collect(Collectors.toSet()), exercise.getAllowComplaintsForAutomaticAssessments(),
                exercise.getAllowFeedbackRequests(), exercise.getIncludedInOverallScore(), exercise.getProblemStatement(), exercise.getGradingInstructions(),
                exercise.getCategories(), TeamAssignmentConfigSnapshotDTO.of(exercise.getTeamAssignmentConfig()), exercise.getPresentationScoreEnabled(),
                exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(),
                exercise.getGradingCriteria().stream().map(GradingCriterionDTO::of).collect(Collectors.toSet()), exercise.getPlagiarismDetectionConfig(), programmingData, textData,
                modelingData, quizData, fileUploadData);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CompetencyExerciseLinkSnapshotDTO(CompetencyExerciseLink.CompetencyExerciseId competencyId, double weight) implements Serializable {

        private static CompetencyExerciseLinkSnapshotDTO of(CompetencyExerciseLink link) {
            if (link == null) {
                return null;
            }
            return new CompetencyExerciseLinkSnapshotDTO(link.getId(), link.getWeight());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TeamAssignmentConfigSnapshotDTO(long id, int minTeamSize, int maxTeamSize) implements Serializable {

        private static TeamAssignmentConfigSnapshotDTO of(TeamAssignmentConfig config) {
            if (config == null) {
                return null;
            }
            return new TeamAssignmentConfigSnapshotDTO(config.getId(), config.getMinTeamSize(), config.getMaxTeamSize());
        }
    }

    private static ZonedDateTime toUtc(ZonedDateTime zdt) {
        return zdt == null ? null : zdt.withZoneSameInstant(ZoneOffset.UTC);
    }

}
