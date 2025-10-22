package de.tum.cit.aet.artemis.exercise.dto.versioning;

import java.io.Serializable;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.util.CollectionUtil;
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

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseSnapshotDTO(
        // fields of BaseExercise class
        long id, String title, String shortName, Double maxPoints, Double bonusPoints, AssessmentType assessmentType, ZonedDateTime releaseDate, ZonedDateTime startDate,
        ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, DifficultyLevel difficulty, ExerciseMode mode,

        // fields of Exercise class
        // not included fields: teams, studentParticipations, tutorParticipations, exampleSubmission, attachment, course, exerciseGroup
        Set<CompetencyExerciseLinkSnapshotDTO> competencyLinks, Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests,
        IncludedInOverallScore includedInOverallScore, String problemStatement, String gradingInstructions, Set<String> categories,
        TeamAssignmentConfigSnapshotDTO teamAssignmentConfig, Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, String feedbackSuggestionModule,
        Set<GradingCriterionDTO> gradingCriteria, PlagiarismDetectionConfigSnapshotDTO plagiarismDetectionConfig, ProgrammingExerciseSnapshotDTO programmingData,
        TextExerciseSnapshotDTO textData, ModelingExerciseSnapshotDTO modelingData, QuizExerciseSnapshotDTO quizData, FileUploadExerciseSnapshotDTO fileUploadData

) implements Serializable {

    /**
     * Creates a snapshot of the given exercise.
     *
     * @param exercise   {@link Exercise}
     * @param gitService {@link GitService}
     * @return {@link ExerciseSnapshotDTO}
     */
    public static ExerciseSnapshotDTO of(Exercise exercise, GitService gitService) {

        var competencyLinks = CollectionUtil.nullIfEmpty(exercise.getCompetencyLinks().stream().map(CompetencyExerciseLinkSnapshotDTO::of).collect(Collectors.toSet()));
        var gradingCriteria = CollectionUtil.nullIfEmpty(exercise.getGradingCriteria().stream().map(GradingCriterionDTO::of).collect(Collectors.toSet()));
        var categories = CollectionUtil.nullIfEmpty(exercise.getCategories());
        var plagiarismDetectionConfig = PlagiarismDetectionConfigSnapshotDTO.of(exercise.getPlagiarismDetectionConfig());

        var programmingData = exercise instanceof ProgrammingExercise ? ProgrammingExerciseSnapshotDTO.of((ProgrammingExercise) exercise, gitService) : null;
        var textData = exercise instanceof TextExercise ? TextExerciseSnapshotDTO.of((TextExercise) exercise) : null;
        var modelingData = exercise instanceof ModelingExercise ? ModelingExerciseSnapshotDTO.of((ModelingExercise) exercise) : null;
        var quizData = exercise instanceof QuizExercise ? QuizExerciseSnapshotDTO.of((QuizExercise) exercise) : null;
        var fileUploadData = exercise instanceof FileUploadExercise ? FileUploadExerciseSnapshotDTO.of((FileUploadExercise) exercise) : null;
        return new ExerciseSnapshotDTO(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getMaxPoints(), exercise.getBonusPoints(),
                exercise.getAssessmentType(), toUtc(exercise.getReleaseDate()), toUtc(exercise.getStartDate()), toUtc(exercise.getDueDate()),
                toUtc(exercise.getAssessmentDueDate()), toUtc(exercise.getExampleSolutionPublicationDate()), exercise.getDifficulty(), exercise.getMode(), competencyLinks,
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getIncludedInOverallScore(), exercise.getProblemStatement(),
                exercise.getGradingInstructions(), categories, TeamAssignmentConfigSnapshotDTO.of(exercise.getTeamAssignmentConfig()), exercise.getPresentationScoreEnabled(),
                exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), gradingCriteria, plagiarismDetectionConfig, programmingData, textData, modelingData,
                quizData, fileUploadData);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CompetencyExerciseLinkSnapshotDTO(CompetencyExerciseLink.CompetencyExerciseId competencyId, double weight) implements Serializable {

        private static CompetencyExerciseLinkSnapshotDTO of(@Nullable CompetencyExerciseLink link) {
            if (link == null) {
                return null;
            }
            return new CompetencyExerciseLinkSnapshotDTO(link.getId(), link.getWeight());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TeamAssignmentConfigSnapshotDTO(long id, int minTeamSize, int maxTeamSize) implements Serializable {

        private static TeamAssignmentConfigSnapshotDTO of(@Nullable TeamAssignmentConfig config) {
            if (config == null) {
                return null;
            }
            return new TeamAssignmentConfigSnapshotDTO(config.getId(), config.getMinTeamSize(), config.getMaxTeamSize());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record PlagiarismDetectionConfigSnapshotDTO(boolean continuousPlagiarismControlEnabled, boolean continuousPlagiarismControlPostDueDateChecksEnabled,
            int continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod, int similarityThreshold, int minimumScore, int minimumSize) implements Serializable {

        private static PlagiarismDetectionConfigSnapshotDTO of(@Nullable PlagiarismDetectionConfig config) {
            if (config == null) {
                return null;
            }
            return new PlagiarismDetectionConfigSnapshotDTO(config.isContinuousPlagiarismControlEnabled(), config.isContinuousPlagiarismControlPostDueDateChecksEnabled(),
                    config.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(), config.getSimilarityThreshold(), config.getMinimumScore(), config.getMinimumSize());

        }
    }

    private static ZonedDateTime toUtc(ZonedDateTime zdt) {
        return zdt == null ? null : zdt.withZoneSameInstant(ZoneOffset.UTC);
    }

}
