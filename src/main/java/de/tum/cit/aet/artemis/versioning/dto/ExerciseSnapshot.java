package de.tum.cit.aet.artemis.versioning.dto;

import java.io.Serializable;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

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

public record ExerciseSnapshot(
        // fields of BaseExercise class
        Long id, String title, String shortName, Double maxPoints, Double bonusPoints, AssessmentType assessmentType, ZonedDateTime releaseDate, ZonedDateTime startDate,
        ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, DifficultyLevel difficulty, ExerciseMode mode,

        // fields of Exercise class
        // not included fields: teams, studentParticipations, tutorParticipations, exampleSubmission, attachment, course, exerciseGroup
        Set<CompetencyExerciseLinkData> competencyLinks, Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests,
        IncludedInOverallScore includedInOverallScore, String problemStatement, String gradingInstructions, Set<String> categories, TeamAssignmentConfigData teamAssignmentConfig,
        Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, String feedbackSuggestionModule, Set<GradingCriterionDTO> gradingCriteria,
        PlagiarismDetectionConfig plagiarismDetectionConfig, ProgrammingExerciseSnapshot programmingData, TextExerciseSnaphot textData, ModelingExerciseSnapshot modelingData,
        QuizExerciseSnapshot quizData, FileUploadExerciseSnapshot fileUploadData

) implements Serializable {

    public static ExerciseSnapshot of(Exercise exercise, GitService gitService) {

        var programmingData = exercise instanceof ProgrammingExercise ? ProgrammingExerciseSnapshot.of((ProgrammingExercise) exercise, gitService) : null;
        var textData = exercise instanceof TextExercise ? TextExerciseSnaphot.of((TextExercise) exercise) : null;
        var modelingData = exercise instanceof ModelingExercise ? ModelingExerciseSnapshot.of((ModelingExercise) exercise) : null;
        var quizData = exercise instanceof QuizExercise ? QuizExerciseSnapshot.of((QuizExercise) exercise) : null;
        var fileUploadData = exercise instanceof FileUploadExercise ? FileUploadExerciseSnapshot.of((FileUploadExercise) exercise) : null;
        return new ExerciseSnapshot(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getMaxPoints(), exercise.getBonusPoints(),
                exercise.getAssessmentType(), toUtc(exercise.getReleaseDate()), toUtc(exercise.getStartDate()), toUtc(exercise.getDueDate()),
                toUtc(exercise.getAssessmentDueDate()), toUtc(exercise.getExampleSolutionPublicationDate()), exercise.getDifficulty(), exercise.getMode(),
                exercise.getCompetencyLinks().stream().map(CompetencyExerciseLinkData::of).collect(Collectors.toSet()), exercise.getAllowComplaintsForAutomaticAssessments(),
                exercise.getAllowFeedbackRequests(), exercise.getIncludedInOverallScore(), exercise.getProblemStatement(), exercise.getGradingInstructions(),
                exercise.getCategories(), TeamAssignmentConfigData.of(exercise.getTeamAssignmentConfig()), exercise.getPresentationScoreEnabled(),
                exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(),
                exercise.getGradingCriteria().stream().map(GradingCriterionDTO::of).collect(Collectors.toSet()), exercise.getPlagiarismDetectionConfig(), programmingData, textData,
                modelingData, quizData, fileUploadData);
    }

    public record CompetencyExerciseLinkData(CompetencyExerciseLink.CompetencyExerciseId competencyId, double weight) implements Serializable {

        private static CompetencyExerciseLinkData of(CompetencyExerciseLink link) {
            if (link == null) {
                return null;
            }
            return new CompetencyExerciseLinkData(link.getId(), link.getWeight());
        }
    }

    public record TeamAssignmentConfigData(long id, int minTeamSize, int maxTeamSize) implements Serializable {

        private static TeamAssignmentConfigData of(TeamAssignmentConfig config) {
            if (config == null) {
                return null;
            }
            return new TeamAssignmentConfigData(config.getId(), config.getMinTeamSize(), config.getMaxTeamSize());
        }
    }

    private static ZonedDateTime toUtc(ZonedDateTime zdt) {
        return zdt == null ? null : zdt.withZoneSameInstant(ZoneOffset.UTC);
    }

    public static ExerciseSnapshot empty() {
        return new ExerciseSnapshot(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);

    }
}
