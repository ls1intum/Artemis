package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Flat, discriminator-based DTO containing the shared and subtype-specific fields used by the student exercise details view.
 *
 * @param id                                         the exercise identifier
 * @param type                                       the JSON exercise discriminator
 * @param exerciseType                               the exercise type enum
 * @param title                                      the title, if available
 * @param shortName                                  the short name, if available
 * @param problemStatement                           the problem statement, if available
 * @param gradingInstructions                        the grading instructions, if visible
 * @param releaseDate                                the release date, if configured
 * @param startDate                                  the start date, if configured
 * @param dueDate                                    the due date, if configured
 * @param assessmentDueDate                          the assessment due date, if configured
 * @param exampleSolutionPublicationDate             the example-solution publication date, if configured
 * @param maxPoints                                  the maximum points, if configured
 * @param bonusPoints                                the bonus points, if configured
 * @param assessmentType                             the assessment type, if configured
 * @param difficulty                                 the difficulty, if configured
 * @param mode                                       the participation mode
 * @param includedInOverallScore                     how the exercise contributes to the course score
 * @param allowComplaintsForAutomaticAssessments     whether automatic-assessment complaints are allowed
 * @param allowFeedbackRequests                      whether feedback requests are allowed
 * @param presentationScoreEnabled                   whether presentation scores are enabled, if configured
 * @param secondCorrectionEnabled                    whether second correction is enabled
 * @param feedbackSuggestionModule                   the feedback suggestion module, if configured
 * @param categories                                 the initialized serialized categories, or absent when not loaded
 * @param teamMode                                   whether the exercise uses team mode
 * @param studentAssignedTeamId                      the current student's assigned team identifier, if any
 * @param studentAssignedTeamIdComputed              whether the assigned team identifier was computed
 * @param course                                     the narrow course context, if initialized
 * @param studentParticipations                      the initialized student participations and filtered histories, or absent when not loaded
 * @param allowOnlineEditor                          whether the online editor is allowed for programming exercises
 * @param allowOfflineIde                            whether offline IDE use is allowed for programming exercises
 * @param allowOnlineIde                             whether the online IDE is allowed for programming exercises
 * @param staticCodeAnalysisEnabled                  whether static code analysis is enabled for programming exercises
 * @param showTestNamesToStudents                    whether test names are shown for programming exercises
 * @param buildAndTestStudentSubmissionsAfterDueDate the post-due-date build time for programming exercises, if configured
 * @param releaseTestsWithExampleSolution            whether tests are released with the example solution for programming exercises
 * @param submissionPolicy                           the initialized programming submission policy, if configured
 * @param visibleToStudents                          whether a quiz is visible to students
 * @param randomizeQuestionOrder                     whether quiz question order is randomized
 * @param allowedNumberOfAttempts                    the allowed quiz attempts, if configured
 * @param remainingNumberOfAttempts                  the current student's remaining quiz attempts, if available
 * @param quizMode                                   the quiz mode, if applicable
 * @param duration                                   the quiz duration in seconds, if applicable
 * @param quizBatches                                the initialized, server-selected quiz batches, or absent when not loaded
 * @param quizStarted                                whether the quiz has started
 * @param quizEnded                                  whether the quiz has ended
 * @param exampleSolution                            the filtered text or file-upload example solution, if visible
 * @param filePattern                                the accepted file pattern for file-upload exercises, if applicable
 * @param diagramType                                the modeling diagram type, if applicable
 * @param exampleSolutionModel                       the filtered modeling example-solution model, if visible
 * @param exampleSolutionExplanation                 the filtered modeling example-solution explanation, if visible
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseDetailsResponseDTO(Long id, String type, ExerciseType exerciseType, @Nullable String title, @Nullable String shortName, @Nullable String problemStatement,
        @Nullable String gradingInstructions, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate,
        @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable Double maxPoints, @Nullable Double bonusPoints,
        @Nullable AssessmentType assessmentType, @Nullable DifficultyLevel difficulty, ExerciseMode mode, IncludedInOverallScore includedInOverallScore,
        boolean allowComplaintsForAutomaticAssessments, boolean allowFeedbackRequests, @Nullable Boolean presentationScoreEnabled, boolean secondCorrectionEnabled,
        @Nullable String feedbackSuggestionModule, @Nullable Set<String> categories, boolean teamMode, @Nullable Long studentAssignedTeamId, boolean studentAssignedTeamIdComputed,
        @Nullable ExerciseDetailsCourseDTO course, @Nullable List<ExerciseDetailsParticipationDTO> studentParticipations, @Nullable Boolean allowOnlineEditor,
        @Nullable Boolean allowOfflineIde, @Nullable Boolean allowOnlineIde, @Nullable Boolean staticCodeAnalysisEnabled, @Nullable Boolean showTestNamesToStudents,
        @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, @Nullable Boolean releaseTestsWithExampleSolution,
        @Nullable ExerciseDetailsSubmissionPolicyDTO submissionPolicy, @Nullable Boolean visibleToStudents, @Nullable Boolean randomizeQuestionOrder,
        @Nullable Integer allowedNumberOfAttempts, @Nullable Integer remainingNumberOfAttempts, @Nullable QuizMode quizMode, @Nullable Integer duration,
        @Nullable Set<QuizBatchDTO> quizBatches, @Nullable Boolean quizStarted, @Nullable Boolean quizEnded, @Nullable String exampleSolution, @Nullable String filePattern,
        @Nullable DiagramType diagramType, @Nullable String exampleSolutionModel, @Nullable String exampleSolutionExplanation) {

    /**
     * Maps an authorized and filtered exercise to the explicit student details contract without initializing lazy associations.
     *
     * @param exercise the authorized and filtered exercise
     * @return the flat exercise details DTO
     */
    public static ExerciseDetailsResponseDTO of(Exercise exercise) {
        Objects.requireNonNull(exercise, "The exercise must be set");

        Set<String> categories = exercise.getCategories() != null && Hibernate.isInitialized(exercise.getCategories()) ? Set.copyOf(exercise.getCategories()) : null;
        List<ExerciseDetailsParticipationDTO> studentParticipations = exercise.getStudentParticipations() != null && Hibernate.isInitialized(exercise.getStudentParticipations())
                ? exercise.getStudentParticipations().stream().filter(Objects::nonNull).map(ExerciseDetailsParticipationDTO::of).toList()
                : null;
        var courseEntity = exercise.getCourseViaExerciseGroupOrCourseMember();
        ExerciseDetailsCourseDTO course = courseEntity != null && Hibernate.isInitialized(courseEntity) ? ExerciseDetailsCourseDTO.of(courseEntity) : null;

        Boolean allowOnlineEditor = null;
        Boolean allowOfflineIde = null;
        Boolean allowOnlineIde = null;
        Boolean staticCodeAnalysisEnabled = null;
        Boolean showTestNamesToStudents = null;
        ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate = null;
        Boolean releaseTestsWithExampleSolution = null;
        ExerciseDetailsSubmissionPolicyDTO submissionPolicy = null;
        Boolean visibleToStudents = null;
        Boolean randomizeQuestionOrder = null;
        Integer allowedNumberOfAttempts = null;
        Integer remainingNumberOfAttempts = null;
        QuizMode quizMode = null;
        Integer duration = null;
        Set<QuizBatchDTO> quizBatches = null;
        Boolean quizStarted = null;
        Boolean quizEnded = null;
        String exampleSolution = null;
        String filePattern = null;
        DiagramType diagramType = null;
        String exampleSolutionModel = null;
        String exampleSolutionExplanation = null;

        switch (exercise) {
            case ProgrammingExercise programmingExercise -> {
                allowOnlineEditor = programmingExercise.isAllowOnlineEditor();
                allowOfflineIde = programmingExercise.isAllowOfflineIde();
                allowOnlineIde = programmingExercise.isAllowOnlineIde();
                staticCodeAnalysisEnabled = programmingExercise.isStaticCodeAnalysisEnabled();
                showTestNamesToStudents = programmingExercise.getShowTestNamesToStudents();
                buildAndTestStudentSubmissionsAfterDueDate = programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate();
                releaseTestsWithExampleSolution = programmingExercise.isReleaseTestsWithExampleSolution();
                SubmissionPolicy policy = programmingExercise.getSubmissionPolicy();
                if (policy != null && Hibernate.isInitialized(policy)) {
                    submissionPolicy = ExerciseDetailsSubmissionPolicyDTO.of(policy);
                }
            }
            case QuizExercise quizExercise -> {
                visibleToStudents = quizExercise.isVisibleToStudents();
                randomizeQuestionOrder = quizExercise.isRandomizeQuestionOrder();
                allowedNumberOfAttempts = quizExercise.getAllowedNumberOfAttempts();
                remainingNumberOfAttempts = quizExercise.getRemainingNumberOfAttempts();
                quizMode = quizExercise.getQuizMode();
                duration = quizExercise.getDuration();
                if (quizExercise.getQuizBatches() != null && Hibernate.isInitialized(quizExercise.getQuizBatches())) {
                    quizBatches = quizExercise.getQuizBatches().stream().map(QuizBatchDTO::of).collect(Collectors.toUnmodifiableSet());
                }
                quizStarted = quizExercise.isQuizStarted();
                quizEnded = quizExercise.isQuizEnded();
            }
            case TextExercise textExercise -> exampleSolution = textExercise.getExampleSolution();
            case ModelingExercise modelingExercise -> {
                diagramType = modelingExercise.getDiagramType();
                exampleSolutionModel = modelingExercise.getExampleSolutionModel();
                exampleSolutionExplanation = modelingExercise.getExampleSolutionExplanation();
            }
            case FileUploadExercise fileUploadExercise -> {
                exampleSolution = fileUploadExercise.getExampleSolution();
                filePattern = fileUploadExercise.getFilePattern();
            }
            default -> throw new IllegalArgumentException("Unsupported exercise type: " + exercise.getClass().getName());
        }

        return new ExerciseDetailsResponseDTO(exercise.getId(), exercise.getType(), exercise.getExerciseType(), exercise.getTitle(), exercise.getShortName(),
                exercise.getProblemStatement(), exercise.getGradingInstructions(), exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(),
                exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getAssessmentType(),
                exercise.getDifficulty(), exercise.getMode(), exercise.getIncludedInOverallScore(), exercise.getAllowComplaintsForAutomaticAssessments(),
                exercise.getAllowFeedbackRequests(), exercise.getPresentationScoreEnabled(), exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(),
                categories, exercise.isTeamMode(), exercise.getStudentAssignedTeamId(), exercise.isStudentAssignedTeamIdComputed(), course, studentParticipations,
                allowOnlineEditor, allowOfflineIde, allowOnlineIde, staticCodeAnalysisEnabled, showTestNamesToStudents, buildAndTestStudentSubmissionsAfterDueDate,
                releaseTestsWithExampleSolution, submissionPolicy, visibleToStudents, randomizeQuestionOrder, allowedNumberOfAttempts, remainingNumberOfAttempts, quizMode,
                duration, quizBatches, quizStarted, quizEnded, exampleSolution, filePattern, diagramType, exampleSolutionModel, exampleSolutionExplanation);
    }
}
