package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupReferenceDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssignmentConfigDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismDetectionConfigDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * The single response record for a programming exercise.
 * <p>
 * There is deliberately one full record with {@link Hibernate#isInitialized(Object)}-guarded nullable sub-graphs
 * rather than one variant per endpoint: all single-exercise endpoints feed the same client pipelines (the response
 * processing, the request-builder that rebuilds an update body from a loaded exercise, the access-rights
 * computation), and divergent sibling shapes are the documented regression class of this migration. With
 * {@code NON_EMPTY}, "variant" simply means "sub-graph absent" — exactly how the entity serializes uninitialized lazy
 * relations today.
 * <p>
 * The nested {@code course} and {@code exerciseGroup} objects must never be flattened to ids: access rights are
 * computed from the course group names and exam navigation walks {@code exerciseGroup.exam.course}.
 *
 * @param id                                         the exercise id
 * @param type                                       the constant discriminator {@code "programming"}
 * @param title                                      the exercise title
 * @param shortName                                  the exercise short name
 * @param channelName                                the name of the associated communication channel
 * @param problemStatement                           the problem statement (markdown)
 * @param categories                                 the exercise categories as JSON-encoded strings
 * @param difficulty                                 the difficulty level
 * @param mode                                       individual or team mode
 * @param teamMode                                   whether the exercise is a team exercise
 * @param teamAssignmentConfig                       the team assignment configuration
 * @param maxPoints                                  the achievable points
 * @param bonusPoints                                the achievable bonus points
 * @param includedInOverallScore                     how the exercise counts towards the course score
 * @param releaseDate                                when the exercise is released
 * @param startDate                                  when the exercise starts
 * @param dueDate                                    when the exercise is due
 * @param assessmentDueDate                          when the assessment is due
 * @param exampleSolutionPublicationDate             when the example solution becomes visible
 * @param buildAndTestStudentSubmissionsAfterDueDate when student submissions are built and tested after the due date
 * @param assessmentType                             automatic, semi-automatic or manual assessment
 * @param allowComplaintsForAutomaticAssessments     whether complaints are allowed for automatic assessments
 * @param allowFeedbackRequests                      whether feedback requests are allowed
 * @param presentationScoreEnabled                   whether the presentation score is enabled
 * @param secondCorrectionEnabled                    whether a second correction round is enabled
 * @param feedbackSuggestionModule                   the Athena module used for feedback suggestions
 * @param gradingInstructions                        the unstructured grading instructions
 * @param gradingCriteria                            the structured grading criteria; {@code null} when not loaded
 * @param competencyLinks                            the linked competencies; {@code null} when not loaded
 * @param plagiarismDetectionConfig                  the plagiarism detection configuration
 * @param programmingLanguage                        the programming language
 * @param packageName                                the package name of the exercise
 * @param projectType                                the project type (build tool / IDE flavour)
 * @param projectKey                                 the VCS/CI project key
 * @param testRepositoryUri                          the URI of the test repository
 * @param staticCodeAnalysisEnabled                  whether static code analysis is enabled
 * @param maxStaticCodeAnalysisPenalty               the maximum static code analysis penalty
 * @param showTestNamesToStudents                    whether test names are shown to students
 * @param releaseTestsWithExampleSolution            whether tests are released with the example solution
 * @param testCasesChanged                           whether the test cases changed since the last build
 * @param allowOnlineEditor                          whether the online editor is allowed
 * @param allowOfflineIde                            whether the offline IDE is allowed
 * @param allowOnlineIde                             whether the online IDE is allowed
 * @param gradingInstructionFeedbackUsed             whether structured grading instructions were used in feedback
 * @param buildConfig                                the build configuration
 * @param submissionPolicy                           the submission policy
 * @param course                                     the nested course; populated for course exercises
 * @param exerciseGroup                              the nested exercise group; populated for exam exercises
 * @param templateParticipation                      the template participation
 * @param solutionParticipation                      the solution participation
 * @param exerciseVariantGroup                       the variant group owning the shared timeline, when fetched
 * @param studentParticipations                      the student participations; {@code null} when not loaded
 * @param auxiliaryRepositories                      the auxiliary repositories; {@code null} when not loaded
 * @param exerciseType                               the constant exercise-type discriminator {@code "programming"};
 *                                                       the entity serialized it next to the Jackson subtype id
 *                                                       {@code type}, so both stay on the wire
 * @param visibleToStudents                          whether the exercise is already visible to students; computed
 *                                                       from the release date, {@code false} for exam exercises
 * @param studentAssignedTeamIdComputed              whether the transient team assignment was computed for the
 *                                                       requesting user
 * @param defaultTestCaseVisibility                  the visibility new test cases get; derived from course vs. exam
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseResponseDTO(Long id, String type, String title, String shortName, String channelName, String problemStatement, Set<String> categories,
        DifficultyLevel difficulty, ExerciseMode mode, Boolean teamMode, TeamAssignmentConfigDTO teamAssignmentConfig, Double maxPoints, Double bonusPoints,
        IncludedInOverallScore includedInOverallScore, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate,
        ZonedDateTime exampleSolutionPublicationDate, ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, AssessmentType assessmentType,
        Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests, Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled,
        String feedbackSuggestionModule, String gradingInstructions, Set<GradingCriterionDTO> gradingCriteria, Set<CompetencyLinkDTO> competencyLinks,
        PlagiarismDetectionConfigDTO plagiarismDetectionConfig, ProgrammingLanguage programmingLanguage, String packageName, ProjectType projectType, String projectKey,
        String testRepositoryUri, Boolean staticCodeAnalysisEnabled, Integer maxStaticCodeAnalysisPenalty, Boolean showTestNamesToStudents, Boolean releaseTestsWithExampleSolution,
        Boolean testCasesChanged, Boolean allowOnlineEditor, Boolean allowOfflineIde, Boolean allowOnlineIde, Boolean gradingInstructionFeedbackUsed,
        UpdateProgrammingExerciseBuildConfigDTO buildConfig, SubmissionPolicyDTO submissionPolicy, ProgrammingExerciseCourseDTO course,
        ProgrammingExerciseExamGroupDTO exerciseGroup, TemplateSolutionParticipationDTO templateParticipation, TemplateSolutionParticipationDTO solutionParticipation,
        ExerciseVariantGroupReferenceDTO exerciseVariantGroup, List<ProgrammingExerciseStudentParticipationDTO> studentParticipations,
        List<AuxiliaryRepositoryDTO> auxiliaryRepositories, ExerciseType exerciseType, boolean visibleToStudents, boolean studentAssignedTeamIdComputed,
        Visibility defaultTestCaseVisibility) implements Serializable {

    /**
     * The constant Jackson subtype id of {@link ProgrammingExercise}.
     */
    public static final String TYPE = "programming";

    /**
     * Creates a {@link ProgrammingExerciseResponseDTO} from the given exercise. Every lazy slot is guarded, so
     * uninitialized relations map to {@code null} instead of throwing.
     *
     * @param exercise the exercise to convert (may be {@code null})
     * @return the corresponding DTO, or {@code null} if the input was {@code null}
     */
    public static ProgrammingExerciseResponseDTO of(ProgrammingExercise exercise) {
        // The entity always put the transient flag on the wire, so the default false is carried rather than dropped.
        return of(exercise, exercise == null ? null : exercise.isGradingInstructionFeedbackUsed());
    }

    /**
     * Creates a {@link ProgrammingExerciseResponseDTO} from the given exercise, carrying the transient
     * {@code gradingInstructionFeedbackUsed} flag. That flag is computed by the retrieval resource before
     * serialization and the grading-instruction editor branches on it, so it is passed in explicitly rather than read
     * off the entity's transient default.
     *
     * @param exercise                       the exercise to convert (may be {@code null})
     * @param gradingInstructionFeedbackUsed whether structured grading instructions were used in feedback, or
     *                                           {@code null} when the endpoint does not compute it
     * @return the corresponding DTO, or {@code null} if the input was {@code null}
     */
    public static ProgrammingExerciseResponseDTO of(ProgrammingExercise exercise, Boolean gradingInstructionFeedbackUsed) {
        if (exercise == null) {
            return null;
        }

        ProgrammingExerciseCourseDTO course = ProgrammingExerciseCourseDTO.ofCourseExercise(exercise);
        ProgrammingExerciseExamGroupDTO exerciseGroup = ProgrammingExerciseExamGroupDTO.ofExamExercise(exercise);
        Set<String> categories = copyCategories(exercise);

        Set<GradingCriterionDTO> gradingCriteria = null;
        Set<GradingCriterion> criteria = exercise.getGradingCriteria();
        if (criteria != null && Hibernate.isInitialized(criteria)) {
            gradingCriteria = criteria.isEmpty() ? Set.of() : criteria.stream().map(GradingCriterionDTO::of).collect(Collectors.toSet());
        }

        Set<CompetencyLinkDTO> competencyLinks = null;
        Set<CompetencyExerciseLink> links = exercise.getCompetencyLinks();
        if (links != null && Hibernate.isInitialized(links)) {
            competencyLinks = links.isEmpty() ? Set.of() : links.stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet());
        }

        List<AuxiliaryRepositoryDTO> auxiliaryRepositories = null;
        if (exercise.getAuxiliaryRepositories() != null && Hibernate.isInitialized(exercise.getAuxiliaryRepositories())) {
            auxiliaryRepositories = exercise.getAuxiliaryRepositories().stream().map(AuxiliaryRepositoryDTO::of).toList();
        }

        List<ProgrammingExerciseStudentParticipationDTO> studentParticipations = null;
        if (exercise.getStudentParticipations() != null && Hibernate.isInitialized(exercise.getStudentParticipations())) {
            // The nested exercise is left null on purpose: it is this very exercise (cycle break).
            studentParticipations = exercise.getStudentParticipations().stream().filter(ProgrammingExerciseStudentParticipation.class::isInstance)
                    .map(ProgrammingExerciseStudentParticipation.class::cast).map(ProgrammingExerciseStudentParticipationDTO::of).toList();
        }

        // Hibernate.isInitialized(null) is true, so every guard below needs its own null check.
        var teamAssignmentConfigEntity = exercise.getTeamAssignmentConfig();
        TeamAssignmentConfigDTO teamAssignmentConfig = teamAssignmentConfigEntity != null && Hibernate.isInitialized(teamAssignmentConfigEntity)
                ? TeamAssignmentConfigDTO.of(teamAssignmentConfigEntity)
                : null;
        var plagiarismDetectionConfigEntity = exercise.getPlagiarismDetectionConfig();
        PlagiarismDetectionConfigDTO plagiarismDetectionConfig = plagiarismDetectionConfigEntity != null && Hibernate.isInitialized(plagiarismDetectionConfigEntity)
                ? PlagiarismDetectionConfigDTO.of(plagiarismDetectionConfigEntity)
                : null;
        var submissionPolicyEntity = exercise.getSubmissionPolicy();
        SubmissionPolicyDTO submissionPolicy = submissionPolicyEntity != null && Hibernate.isInitialized(submissionPolicyEntity) ? SubmissionPolicyDTO.of(submissionPolicyEntity)
                : null;

        return new ProgrammingExerciseResponseDTO(exercise.getId(), TYPE, exercise.getTitle(), exercise.getShortName(), exercise.getChannelName(), exercise.getProblemStatement(),
                categories, exercise.getDifficulty(), exercise.getMode(), exercise.isTeamMode(), teamAssignmentConfig, exercise.getMaxPoints(), exercise.getBonusPoints(),
                exercise.getIncludedInOverallScore(), exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(),
                exercise.getExampleSolutionPublicationDate(), exercise.getBuildAndTestStudentSubmissionsAfterDueDate(), exercise.getAssessmentType(),
                exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(), exercise.getPresentationScoreEnabled(),
                exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(), gradingCriteria, competencyLinks,
                plagiarismDetectionConfig, exercise.getProgrammingLanguage(), exercise.getPackageName(), exercise.getProjectType(), exercise.getProjectKey(),
                exercise.getTestRepositoryUri(), exercise.isStaticCodeAnalysisEnabled(), exercise.getMaxStaticCodeAnalysisPenalty(), exercise.getShowTestNamesToStudents(),
                exercise.isReleaseTestsWithExampleSolution(), exercise.getTestCasesChanged(), exercise.isAllowOnlineEditor(), exercise.isAllowOfflineIde(),
                exercise.isAllowOnlineIde(), gradingInstructionFeedbackUsed, UpdateProgrammingExerciseBuildConfigDTO.of(exercise.getBuildConfig()), submissionPolicy, course,
                exerciseGroup, TemplateSolutionParticipationDTO.ofTemplate(exercise.getTemplateParticipation()),
                TemplateSolutionParticipationDTO.ofSolution(exercise.getSolutionParticipation()), ExerciseVariantGroupReferenceDTO.ofNullable(exercise.getExerciseVariantGroup()),
                studentParticipations, auxiliaryRepositories, exercise.getExerciseType(), exercise.isVisibleToStudents(), exercise.isStudentAssignedTeamIdComputed(),
                exercise.getDefaultTestCaseVisibility());
    }

    /**
     * Copies the exercise categories into a detached set. {@code categories} is a LAZY {@code @ElementCollection}:
     * the live Hibernate collection must never be stored in a record, because a {@code toString()} after the session
     * closed would throw.
     *
     * @param exercise the exercise being mapped
     * @return a copy of the categories, or {@code null} when they are not loaded
     */
    static Set<String> copyCategories(ProgrammingExercise exercise) {
        if (exercise.getCategories() == null || !Hibernate.isInitialized(exercise.getCategories())) {
            return null;
        }
        return Set.copyOf(exercise.getCategories());
    }
}
