package de.tum.cit.aet.artemis.versioning.domain;

import java.io.Serializable;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.dto.question.DragAndDropQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.MultipleChoiceQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.ShortAnswerQuestionWithMappingDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

public record ExerciseSnapshot(
        // fields of BaseExercise class
        Long id, String title, String shortName, Double maxPoints, Double bonusPoints, AssessmentType assessmentType, ZonedDateTime releaseDate, ZonedDateTime startDate,
        ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, DifficultyLevel difficulty, ExerciseMode mode,

        // fields of Exercise class
        // not included fields: teams, studentParticipations, tutorParticipations, exampleSubmission, attachments
        // partially included fields: course, exerciseGroup
        Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests, IncludedInOverallScore includedInOverallScore, String problemStatement,
        String gradingInstructions, Set<CompetencyExerciseLinkData> competencyLinks, Set<String> categories, TeamAssignmentConfigData teamAssignmentConfig,
        Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, String feedbackSuggestionModule, Long courseId,
        // course id instead of linked course object
        Long exerciseGroupId, // exercise group id instead of linked exercise group object
        Set<GradingCriterionDTO> gradingCriteria, PlagiarismDetectionConfig plagiarismDetectionConfig, ProgrammingExerciseData programmingData, TextExerciseData textData,
        ModelingExerciseData modelingData, QuizExerciseData quizData, FileUploadExerciseData fileUploadData

) implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(ExerciseSnapshot.class);

    public static ExerciseSnapshot of(Exercise exercise, GitService gitService) {

        var programmingData = exercise instanceof ProgrammingExercise ? ProgrammingExerciseData.of((ProgrammingExercise) exercise, gitService) : null;
        var textData = exercise instanceof TextExercise ? TextExerciseData.of((TextExercise) exercise) : null;
        var modelingData = exercise instanceof ModelingExercise ? ModelingExerciseData.of((ModelingExercise) exercise) : null;
        var quizData = exercise instanceof QuizExercise ? QuizExerciseData.of((QuizExercise) exercise) : null;
        var fileUploadData = exercise instanceof FileUploadExercise ? FileUploadExerciseData.of((FileUploadExercise) exercise) : null;
        return new ExerciseSnapshot(exercise.getId(), exercise.getTitle(), exercise.getShortName(), exercise.getMaxPoints(), exercise.getBonusPoints(),
                exercise.getAssessmentType(), toUtc(exercise.getReleaseDate()), toUtc(exercise.getStartDate()), toUtc(exercise.getDueDate()),
                toUtc(exercise.getAssessmentDueDate()), toUtc(exercise.getExampleSolutionPublicationDate()), exercise.getDifficulty(), exercise.getMode(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getIncludedInOverallScore(), exercise.getProblemStatement(),
                exercise.getGradingInstructions(), exercise.getCompetencyLinks().stream().map(CompetencyExerciseLinkData::of).collect(Collectors.toSet()), exercise.getCategories(),
                TeamAssignmentConfigData.of(exercise.getTeamAssignmentConfig()), exercise.getPresentationScoreEnabled(), exercise.getSecondCorrectionEnabled(),
                exercise.getFeedbackSuggestionModule(),
                exercise.getCourseViaExerciseGroupOrCourseMember() != null ? exercise.getCourseViaExerciseGroupOrCourseMember().getId() : null,
                exercise.getExerciseGroup() != null ? exercise.getExerciseGroup().getId() : null,
                exercise.getGradingCriteria().stream().map(GradingCriterionDTO::of).collect(Collectors.toSet()), exercise.getPlagiarismDetectionConfig(), programmingData, textData,
                modelingData, quizData, fileUploadData);
    }

    // only competency id and weight are needed, as actual competency objects are not considered as part of exercise
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

    public record ProgrammingExerciseData(String testRepositoryUri, List<AuxiliaryRepository> auxiliaryRepositories, Boolean allowOnlineEditor, Boolean allowOfflineIde,
            Boolean allowOnlineIde, Boolean staticCodeAnalysisEnabled, Integer maxStaticCodeAnalysisPenalty, ProgrammingLanguage programmingLanguage, String packageName,
            Boolean showTestNamesToStudents, ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, String projectKey, ParticipationData templateParticipation,
            ParticipationData solutionParticipation, Set<ProgrammingExerciseTestCaseDTO> testCases, List<ProgrammingExerciseTaskData> tasks,
            Set<StaticCodeAnalysisCategoryData> staticCodeAnalysisCategories, SubmissionPolicyData submissionPolicy, ProjectType projectType,
            Boolean releaseTestsWithExampleSolution, ProgrammingExerciseBuildConfigData buildConfig,
            // Derivative fields for versioning
            String testsCommitId, Map<String, String> auxiliaryCommitIds) implements Serializable {

        public record ParticipationData(long id, String repositoryUri, String buildPlanId, String commitId) implements Serializable {

        }

        public record ProgrammingExerciseTaskData(long id, String taskName, Set<ProgrammingExerciseTestCaseDTO> testCases) implements Serializable {

            private static ProgrammingExerciseTaskData of(ProgrammingExerciseTask task) {
                return new ProgrammingExerciseTaskData(task.getId(), task.getTaskName(),
                        task.getTestCases().stream().map(ProgrammingExerciseTestCaseDTO::of).collect(Collectors.toSet()));
            }
        }

        public record StaticCodeAnalysisCategoryData(long id, String name, Double penalty, Double maxPenalty, CategoryState state) implements Serializable {

            private static StaticCodeAnalysisCategoryData of(StaticCodeAnalysisCategory category) {
                return new StaticCodeAnalysisCategoryData(category.getId(), category.getName(), category.getPenalty(), category.getMaxPenalty(), category.getState());
            }
        }

        public record SubmissionPolicyData(long id, int submissionLimit, boolean active, Double exceedingPenalty, String type) implements Serializable {

            private static SubmissionPolicyData of(SubmissionPolicy policy) {
                if (policy == null) {
                    return null;
                }
                return new SubmissionPolicyData(policy.getId(), policy.getSubmissionLimit(), policy.isActive(),
                        policy instanceof SubmissionPenaltyPolicy penaltyPolicy ? penaltyPolicy.getExceedingPenalty() : null, policy.getClass().getSimpleName());
            }
        }

        public record ProgrammingExerciseBuildConfigData(Boolean sequentialTestRuns, String branch, String buildPlanConfiguration, String buildScript,
                boolean checkoutSolutionRepository, String testCheckoutPath, String assignmentCheckoutPath, String solutionCheckoutPath, int timeoutSeconds, String dockerFlags,
                String theiaImage, boolean allowBranching, String branchRegex) implements Serializable {

            private static ProgrammingExerciseBuildConfigData of(ProgrammingExerciseBuildConfig buildConfig) {
                if (buildConfig == null) {
                    return null;
                }
                return new ProgrammingExerciseBuildConfigData(buildConfig.hasSequentialTestRuns() ? buildConfig.hasSequentialTestRuns() : null, buildConfig.getBranch(),
                        buildConfig.getBuildPlanConfiguration(), buildConfig.getBuildScript(), buildConfig.getCheckoutSolutionRepository(), buildConfig.getTestCheckoutPath(),
                        buildConfig.getAssignmentCheckoutPath(), buildConfig.getSolutionCheckoutPath(), buildConfig.getTimeoutSeconds(), buildConfig.getDockerFlags(),
                        buildConfig.getTheiaImage(), buildConfig.isAllowBranching(), buildConfig.getBranchRegex());
            }
        }

        private static ProgrammingExerciseData of(ProgrammingExercise exercise, GitService gitService) {
            var templateParticipation = exercise.getTemplateParticipation() != null ? new ParticipationData(exercise.getTemplateParticipation().getId(),
                    exercise.getTemplateRepositoryUri(), exercise.getTemplateBuildPlanId(), getCommitHash(exercise.getVcsTemplateRepositoryUri(), gitService)) : null;
            var solutionParticipation = exercise.getSolutionParticipation() != null ? new ParticipationData(exercise.getSolutionParticipation().getId(),
                    exercise.getSolutionRepositoryUri(), exercise.getSolutionBuildPlanId(), getCommitHash(exercise.getVcsSolutionRepositoryUri(), gitService)) : null;
            var testCommitHash = getCommitHash(exercise.getVcsTestRepositoryUri(), gitService);
            var auxiliaryCommitHashes = new HashMap<String, String>();
            if (exercise.getAuxiliaryRepositories() != null) {
                for (AuxiliaryRepository auxiliaryRepository : exercise.getAuxiliaryRepositories()) {
                    if (auxiliaryRepository.getVcsRepositoryUri() != null) {
                        var auxiliaryCommitHash = getCommitHash(auxiliaryRepository.getVcsRepositoryUri(), gitService);
                        if (auxiliaryCommitHash == null) {
                            continue;
                        }
                        auxiliaryCommitHashes.put(auxiliaryRepository.getId().toString(), auxiliaryCommitHash);
                    }
                }
            }
            return new ProgrammingExerciseData(exercise.getTestRepositoryUri(), exercise.getAuxiliaryRepositories(), exercise.isAllowOnlineEditor(), exercise.isAllowOfflineIde(),
                    exercise.isAllowOnlineIde(), exercise.isStaticCodeAnalysisEnabled(), exercise.getMaxStaticCodeAnalysisPenalty(), exercise.getProgrammingLanguage(),
                    exercise.getPackageName(), exercise.getShowTestNamesToStudents(), toUtc(exercise.getBuildAndTestStudentSubmissionsAfterDueDate()), exercise.getProjectKey(),
                    templateParticipation, solutionParticipation, exercise.getTestCases().stream().map(ProgrammingExerciseTestCaseDTO::of).collect(Collectors.toSet()),
                    exercise.getTasks().stream().map(ProgrammingExerciseTaskData::of).collect(Collectors.toCollection(ArrayList::new)),
                    exercise.getStaticCodeAnalysisCategories().stream().map(StaticCodeAnalysisCategoryData::of).collect(Collectors.toSet()),
                    SubmissionPolicyData.of(exercise.getSubmissionPolicy()), exercise.getProjectType(), exercise.isReleaseTestsWithExampleSolution(),
                    ProgrammingExerciseBuildConfigData.of(exercise.getBuildConfig()), testCommitHash, auxiliaryCommitHashes.isEmpty() ? null : auxiliaryCommitHashes

            );
        }

        private static String getCommitHash(LocalVCRepositoryUri uri, GitService gitService) {
            if (uri == null) {
                return null;
            }
            try {
                var commitHash = gitService.getLastCommitHash(uri);
                return commitHash == null ? null : commitHash.getName();
            }
            catch (EntityNotFoundException e) {
                log.warn("Could not retrieve the last commit hash for repoUri {} in ExerciseSnapshot", uri);
                return null;
            }
        }
    }

    public record TextExerciseData(String exampleSolution) implements Serializable {

        private static TextExerciseData of(TextExercise exercise) {
            return new TextExerciseData(exercise.getExampleSolution());
        }
    }

    public record ModelingExerciseData(DiagramType diagramType, String exampleSolutionModel, String exampleSolutionExplanation) implements Serializable {

        private static ModelingExerciseData of(ModelingExercise exercise) {
            return new ModelingExerciseData(exercise.getDiagramType(), exercise.getExampleSolutionModel(), exercise.getExampleSolutionExplanation());
        }
    }

    public record QuizExerciseData(Boolean randomizeQuestionOrder, Integer allowedNumberOfAttempts, Boolean isOpenForPractice, QuizMode quizMode, Integer duration,
            List<QuizQuestionData> quizQuestions) implements Serializable {

        public record QuizQuestionData(ShortAnswerQuestionWithMappingDTO shortAnswerQuestionWithMappingDTO,
                MultipleChoiceQuestionWithSolutionDTO multipleChoiceQuestionWithSolutionDTO, DragAndDropQuestionWithSolutionDTO dragAndDropQuestionWithSolutionDTO)
                implements Serializable {

            private static QuizQuestionData of(QuizQuestion quizQuestion) {
                ShortAnswerQuestionWithMappingDTO shortAnswerQuestionWithMappingDTO = quizQuestion instanceof ShortAnswerQuestion
                        ? ShortAnswerQuestionWithMappingDTO.of((ShortAnswerQuestion) quizQuestion)
                        : null;
                MultipleChoiceQuestionWithSolutionDTO multipleChoiceQuestionWithSolutionDTO = quizQuestion instanceof MultipleChoiceQuestion
                        ? MultipleChoiceQuestionWithSolutionDTO.of((MultipleChoiceQuestion) quizQuestion)
                        : null;
                DragAndDropQuestionWithSolutionDTO dragAndDropQuestionWithSolutionDTO = quizQuestion instanceof DragAndDropQuestion
                        ? DragAndDropQuestionWithSolutionDTO.of((DragAndDropQuestion) quizQuestion)
                        : null;
                return new QuizQuestionData(shortAnswerQuestionWithMappingDTO, multipleChoiceQuestionWithSolutionDTO, dragAndDropQuestionWithSolutionDTO);
            }
        }

        private static QuizExerciseData of(QuizExercise exercise) {
            return new QuizExerciseData(exercise.isRandomizeQuestionOrder(), exercise.getAllowedNumberOfAttempts(), exercise.isIsOpenForPractice(), exercise.getQuizMode(),
                    exercise.getDuration(),
                    exercise.getQuizQuestions() != null ? exercise.getQuizQuestions().stream().map(QuizQuestionData::of).collect(Collectors.toCollection(ArrayList::new)) : null);
        }

    }

    public record FileUploadExerciseData(String exampleSolution, String filePattern) implements Serializable {

        private static FileUploadExerciseData of(FileUploadExercise exercise) {
            return new FileUploadExerciseData(exercise.getExampleSolution(), exercise.getFilePattern());
        }
    }

    private static ZonedDateTime toUtc(ZonedDateTime zdt) {
        return zdt == null ? null : zdt.withZoneSameInstant(ZoneOffset.UTC);
    }
}
