package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.hibernate.Hibernate;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.dto.ExampleSubmissionDTO;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.dto.TemplateSolutionParticipationDTO;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * The exercise as the management, assessment and example solution pages read it.
 * <p>
 * One record serves the three endpoints that used to hand out the polymorphic {@code Exercise} entity - the exercise
 * by id, the exercise for the assessment dashboard and the exercise for the example solution - because they feed the
 * same client model and largely the same screens. {@code type} is the Jackson discriminator the client switches on and
 * the example submission editors echo back into the entity-typed example submission endpoints, so it is always set.
 * <p>
 * Which components carry a value depends on what the endpoint loaded: {@link #of(Exercise)} maps an association only
 * when it is initialized, so an endpoint that never fetched grading criteria, example submissions or tutor
 * participations omits them, exactly as the lazy entity did. The subtype components are set for the matching exercise
 * type only.
 *
 * @param id                                         the id of the exercise
 * @param type                                       the exercise discriminator: {@code programming}, {@code modeling}, {@code quiz}, {@code text} or {@code file-upload}
 * @param exerciseType                               the exercise type enum
 * @param title                                      the title of the exercise
 * @param shortName                                  the short name of the exercise
 * @param problemStatement                           the problem statement
 * @param gradingInstructions                        the grading instructions, filtered out for students
 * @param releaseDate                                the release date
 * @param startDate                                  the start date
 * @param dueDate                                    the due date
 * @param assessmentDueDate                          the assessment due date
 * @param exampleSolutionPublicationDate             the example solution publication date
 * @param maxPoints                                  the maximum achievable points
 * @param bonusPoints                                the achievable bonus points
 * @param assessmentType                             the assessment type
 * @param difficulty                                 the difficulty level
 * @param mode                                       the participation mode
 * @param teamMode                                   whether the exercise is a team exercise
 * @param visibleToStudents                          whether the exercise is already visible to students
 * @param includedInOverallScore                     how the exercise counts towards the course score
 * @param allowComplaintsForAutomaticAssessments     whether complaints about automatic assessments are allowed
 * @param presentationScoreEnabled                   whether the presentation score is enabled
 * @param secondCorrectionEnabled                    whether the second correction round is enabled
 * @param studentAssignedTeamIdComputed              whether the requesting student's team id was computed; a transient flag the entity always serialized
 * @param gradingInstructionFeedbackUsed             whether grading instruction feedback was used; a transient flag the entity always serialized
 * @param categories                                 the serialized exercise categories the client parses into chips
 * @param teamAssignmentConfig                       the team size configuration, for team exercises
 * @param course                                     the course of a course exercise
 * @param exerciseGroup                              the exercise group of an exam exercise
 * @param exerciseVariantGroup                       the variant group the exercise belongs to, where the endpoint fetched it
 * @param gradingCriteria                            the structured grading criteria, for tutors and above
 * @param exampleSubmissions                         the example submissions, on the assessment dashboard only
 * @param tutorParticipations                        the requesting tutor's participation, on the assessment dashboard only
 * @param latestExamEndDate                          the moment the exam is over for every student, on the assessment dashboard of an exam exercise
 * @param assessmentPossibleFrom                     the moment tutors may start assessing, on the assessment dashboard of an exam exercise
 * @param allowOnlineEditor                          whether the online code editor is allowed, for programming exercises
 * @param allowOfflineIde                            whether working offline is allowed, for programming exercises
 * @param allowOnlineIde                             whether the online IDE is allowed, for programming exercises
 * @param staticCodeAnalysisEnabled                  whether static code analysis is enabled, for programming exercises
 * @param maxStaticCodeAnalysisPenalty               the maximum static code analysis penalty, for programming exercises
 * @param showTestNamesToStudents                    whether test names are shown to students, for programming exercises
 * @param buildAndTestStudentSubmissionsAfterDueDate the date submissions are built and tested again, for programming exercises
 * @param releaseTestsWithExampleSolution            whether tests are released with the example solution, for programming exercises
 * @param programmingLanguage                        the programming language, for programming exercises
 * @param projectType                                the project type, for programming exercises
 * @param packageName                                the package name, for programming exercises
 * @param projectKey                                 the project key the scores page builds build plan links from, for programming exercises
 * @param testRepositoryUri                          the test repository uri, for programming exercises, cleared for students
 * @param testCasesChanged                           whether test cases changed since the last build, for programming exercises
 * @param defaultTestCaseVisibility                  the visibility a test case falls back to, for programming exercises
 * @param templateParticipation                      the template participation, on the assessment dashboard of a programming exercise
 * @param solutionParticipation                      the solution participation, on the assessment dashboard of a programming exercise
 * @param randomizeQuestionOrder                     whether the question order is randomized, for quiz exercises
 * @param allowedNumberOfAttempts                    the allowed number of attempts, for quiz exercises
 * @param remainingNumberOfAttempts                  the remaining number of attempts, for quiz exercises
 * @param quizMode                                   the quiz mode, for quiz exercises
 * @param duration                                   the quiz duration in seconds, for quiz exercises
 * @param quizStarted                                whether the quiz has started, for quiz exercises
 * @param quizEnded                                  whether the quiz has ended, for quiz exercises
 * @param exampleSolution                            the example solution, for text and file upload exercises
 * @param filePattern                                the accepted file pattern, for file upload exercises
 * @param diagramType                                the diagram type, for modeling exercises
 * @param exampleSolutionModel                       the example solution model, for modeling exercises
 * @param exampleSolutionExplanation                 the example solution explanation, for modeling exercises
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseResponseDTO(long id, String type, ExerciseType exerciseType, @Nullable String title, @Nullable String shortName, @Nullable String problemStatement,
        @Nullable String gradingInstructions, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate,
        @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable Double maxPoints, @Nullable Double bonusPoints,
        @Nullable AssessmentType assessmentType, @Nullable DifficultyLevel difficulty, @Nullable ExerciseMode mode, boolean teamMode, boolean visibleToStudents,
        @Nullable IncludedInOverallScore includedInOverallScore, boolean allowComplaintsForAutomaticAssessments, @Nullable Boolean presentationScoreEnabled,
        boolean secondCorrectionEnabled, boolean studentAssignedTeamIdComputed, boolean gradingInstructionFeedbackUsed, @Nullable Set<String> categories,
        @Nullable TeamAssignmentConfigDTO teamAssignmentConfig, @Nullable ExerciseCourseDTO course, @Nullable ExerciseGroupContextDTO exerciseGroup,
        @Nullable ExerciseVariantGroupReferenceDTO exerciseVariantGroup, @Nullable List<GradingCriterionDTO> gradingCriteria,
        @Nullable List<ExampleSubmissionDTO> exampleSubmissions, @Nullable List<ExerciseTutorParticipationDTO> tutorParticipations, @Nullable ZonedDateTime latestExamEndDate,
        @Nullable ZonedDateTime assessmentPossibleFrom, @Nullable Boolean allowOnlineEditor, @Nullable Boolean allowOfflineIde, @Nullable Boolean allowOnlineIde,
        @Nullable Boolean staticCodeAnalysisEnabled, @Nullable Integer maxStaticCodeAnalysisPenalty, @Nullable Boolean showTestNamesToStudents,
        @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, @Nullable Boolean releaseTestsWithExampleSolution, @Nullable ProgrammingLanguage programmingLanguage,
        @Nullable ProjectType projectType, @Nullable String packageName, @Nullable String projectKey, @Nullable String testRepositoryUri, @Nullable Boolean testCasesChanged,
        @Nullable Visibility defaultTestCaseVisibility, @Nullable TemplateSolutionParticipationDTO templateParticipation,
        @Nullable TemplateSolutionParticipationDTO solutionParticipation, @Nullable Boolean randomizeQuestionOrder, @Nullable Integer allowedNumberOfAttempts,
        @Nullable Integer remainingNumberOfAttempts, @Nullable QuizMode quizMode, @Nullable Integer duration, @Nullable Boolean quizStarted, @Nullable Boolean quizEnded,
        @Nullable String exampleSolution, @Nullable String filePattern, @Nullable DiagramType diagramType, @Nullable String exampleSolutionModel,
        @Nullable String exampleSolutionExplanation) {

    /**
     * Maps an already authorized and filtered exercise, mapping each association only when it was loaded.
     *
     * @param exercise the exercise to map
     * @return the exercise as the management, assessment and example solution pages read it
     */
    public static ExerciseResponseDTO of(@NonNull Exercise exercise) {
        Boolean allowOnlineEditor = null;
        Boolean allowOfflineIde = null;
        Boolean allowOnlineIde = null;
        Boolean staticCodeAnalysisEnabled = null;
        Integer maxStaticCodeAnalysisPenalty = null;
        Boolean showTestNamesToStudents = null;
        ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate = null;
        Boolean releaseTestsWithExampleSolution = null;
        ProgrammingLanguage programmingLanguage = null;
        ProjectType projectType = null;
        String packageName = null;
        String projectKey = null;
        String testRepositoryUri = null;
        Boolean testCasesChanged = null;
        Visibility defaultTestCaseVisibility = null;
        TemplateSolutionParticipationDTO templateParticipation = null;
        TemplateSolutionParticipationDTO solutionParticipation = null;
        Boolean randomizeQuestionOrder = null;
        Integer allowedNumberOfAttempts = null;
        Integer remainingNumberOfAttempts = null;
        QuizMode quizMode = null;
        Integer duration = null;
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
                maxStaticCodeAnalysisPenalty = programmingExercise.getMaxStaticCodeAnalysisPenalty();
                showTestNamesToStudents = programmingExercise.getShowTestNamesToStudents();
                buildAndTestStudentSubmissionsAfterDueDate = programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate();
                releaseTestsWithExampleSolution = programmingExercise.isReleaseTestsWithExampleSolution();
                programmingLanguage = programmingExercise.getProgrammingLanguage();
                projectType = programmingExercise.getProjectType();
                packageName = programmingExercise.getPackageName();
                projectKey = programmingExercise.getProjectKey();
                // read off the already filtered exercise: filterSensitiveInformation cleared the uri for students
                testRepositoryUri = programmingExercise.getTestRepositoryUri();
                testCasesChanged = programmingExercise.getTestCasesChanged();
                defaultTestCaseVisibility = programmingExercise.getDefaultTestCaseVisibility();
                templateParticipation = TemplateSolutionParticipationDTO.ofTemplate(programmingExercise.getTemplateParticipation());
                solutionParticipation = TemplateSolutionParticipationDTO.ofSolution(programmingExercise.getSolutionParticipation());
            }
            case QuizExercise quizExercise -> {
                randomizeQuestionOrder = quizExercise.isRandomizeQuestionOrder();
                allowedNumberOfAttempts = quizExercise.getAllowedNumberOfAttempts();
                remainingNumberOfAttempts = quizExercise.getRemainingNumberOfAttempts();
                quizMode = quizExercise.getQuizMode();
                duration = quizExercise.getDuration();
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
            // the cases above have to stay in sync with the @JsonSubTypes list on Exercise; a sixth subtype fails here instead of silently losing its fields
            default -> throw new IllegalArgumentException("Unsupported exercise type: " + exercise.getClass().getName());
        }

        return new ExerciseResponseDTO(exercise.getId(), exercise.getType(), exercise.getExerciseType(), exercise.getTitle(), exercise.getShortName(),
                exercise.getProblemStatement(), exercise.getGradingInstructions(), exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(),
                exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getAssessmentType(),
                exercise.getDifficulty(), exercise.getMode(), exercise.isTeamMode(), exercise.isVisibleToStudents(), exercise.getIncludedInOverallScore(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getPresentationScoreEnabled(), exercise.getSecondCorrectionEnabled(),
                exercise.isStudentAssignedTeamIdComputed(), exercise.isGradingInstructionFeedbackUsed(), categories(exercise),
                TeamAssignmentConfigDTO.of(initialized(exercise.getTeamAssignmentConfig())), ExerciseCourseDTO.of(courseOfCourseExercise(exercise)),
                ExerciseGroupContextDTO.of(initialized(exercise.getExerciseGroup())), ExerciseVariantGroupReferenceDTO.ofNullable(exercise.getExerciseVariantGroup()),
                gradingCriteria(exercise), exampleSubmissions(exercise), tutorParticipations(exercise), exercise.getLatestExamEndDate(), exercise.getAssessmentPossibleFrom(),
                allowOnlineEditor, allowOfflineIde, allowOnlineIde, staticCodeAnalysisEnabled, maxStaticCodeAnalysisPenalty, showTestNamesToStudents,
                buildAndTestStudentSubmissionsAfterDueDate, releaseTestsWithExampleSolution, programmingLanguage, projectType, packageName, projectKey, testRepositoryUri,
                testCasesChanged, defaultTestCaseVisibility, templateParticipation, solutionParticipation, randomizeQuestionOrder, allowedNumberOfAttempts,
                remainingNumberOfAttempts, quizMode, duration, quizStarted, quizEnded, exampleSolution, filePattern, diagramType, exampleSolutionModel, exampleSolutionExplanation);
    }

    /**
     * The course of a course exercise. An exam exercise reports its course through the exercise group's exam instead,
     * which is what the client resolves as well, so it stays absent here.
     */
    private static @Nullable Course courseOfCourseExercise(Exercise exercise) {
        return exercise.isExamExercise() ? null : exercise.getCourseViaExerciseGroupOrCourseMember();
    }

    private static <T> @Nullable T initialized(@Nullable T association) {
        return Hibernate.isInitialized(association) ? association : null;
    }

    private static @Nullable Set<String> categories(Exercise exercise) {
        Set<String> categories = initialized(exercise.getCategories());
        return categories == null ? null : Set.copyOf(categories);
    }

    private static @Nullable List<GradingCriterionDTO> gradingCriteria(Exercise exercise) {
        Set<GradingCriterion> criteria = initialized(exercise.getGradingCriteria());
        if (criteria == null) {
            return null;
        }
        return criteria.stream().filter(Objects::nonNull).sorted(Comparator.comparing(GradingCriterion::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(GradingCriterionDTO::of).toList();
    }

    private static @Nullable List<ExampleSubmissionDTO> exampleSubmissions(Exercise exercise) {
        Set<ExampleSubmission> submissions = initialized(exercise.getExampleSubmissions());
        if (submissions == null) {
            return null;
        }
        return submissions.stream().filter(Objects::nonNull).map(ExampleSubmissionDTO::of).toList();
    }

    private static @Nullable List<ExerciseTutorParticipationDTO> tutorParticipations(Exercise exercise) {
        Set<TutorParticipation> participations = initialized(exercise.getTutorParticipations());
        if (participations == null) {
            return null;
        }
        return participations.stream().filter(Objects::nonNull).map(ExerciseTutorParticipationDTO::of).toList();
    }
}
