package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.course.dto.CourseRefDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssignmentConfigDTO;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismDetectionConfigDTO;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;

/**
 * Request body of the three import surfaces (plain import, import from file, sharing setup-import) and the response of
 * the sharing exercise-details call, which backs the very same create form and is posted straight back.
 * <p>
 * It is the create request plus the fields an import carries: a nullable {@code id} (sharing sets it to {@code null}),
 * the {@code projectKey}/{@code testRepositoryUri} of the source exercise, and the template/solution repository URIs
 * that the from-file import reads off the JSON part when it rewrites legacy project names. {@code ignoreUnknown} is
 * load-bearing here: legacy exercise archives carry fields the current model no longer has.
 *
 * @param id                                         the exercise id; nullable, no validation depends on it
 * @param title                                      the exercise title
 * @param shortName                                  the exercise short name
 * @param channelName                                the name of the communication channel to create
 * @param packageName                                the package name of the exercise
 * @param problemStatement                           the problem statement (markdown)
 * @param gradingInstructions                        the unstructured grading instructions
 * @param categories                                 the exercise categories as JSON-encoded strings
 * @param difficulty                                 the difficulty level
 * @param mode                                       individual or team mode
 * @param teamAssignmentConfig                       the team assignment configuration
 * @param maxPoints                                  the achievable points
 * @param bonusPoints                                the achievable bonus points
 * @param includedInOverallScore                     how the exercise counts towards the course score
 * @param releaseDate                                when the exercise is released
 * @param startDate                                  when the exercise starts
 * @param dueDate                                    when the exercise is due
 * @param assessmentDueDate                          when the assessment is due
 * @param exampleSolutionPublicationDate             when the example solution becomes visible
 * @param buildAndTestStudentSubmissionsAfterDueDate when submissions are built and tested after the due date
 * @param assessmentType                             automatic, semi-automatic or manual assessment
 * @param allowComplaintsForAutomaticAssessments     whether complaints are allowed for automatic assessments
 * @param allowFeedbackRequests                      whether feedback requests are allowed
 * @param presentationScoreEnabled                   whether the presentation score is enabled
 * @param secondCorrectionEnabled                    whether a second correction round is enabled
 * @param allowOnlineEditor                          whether the online editor is allowed
 * @param allowOfflineIde                            whether the offline IDE is allowed
 * @param allowOnlineIde                             whether the online IDE is allowed
 * @param staticCodeAnalysisEnabled                  whether static code analysis is enabled
 * @param maxStaticCodeAnalysisPenalty               the maximum static code analysis penalty
 * @param showTestNamesToStudents                    whether test names are shown to students
 * @param releaseTestsWithExampleSolution            whether tests are released with the example solution
 * @param feedbackSuggestionModule                   the Athena module used for feedback suggestions
 * @param programmingLanguage                        the programming language
 * @param projectType                                the project type (build tool / IDE flavour)
 * @param projectKey                                 the VCS/CI project key of the exercise being imported
 * @param testRepositoryUri                          the URI of the test repository being imported
 * @param buildConfig                                the build configuration
 * @param gradingCriteria                            the structured grading criteria
 * @param competencyLinks                            the linked competencies; honoured by the from-file and sharing
 *                                                       imports, ignored by the plain import
 * @param auxiliaryRepositories                      the auxiliary repositories
 * @param submissionPolicy                           the submission policy
 * @param plagiarismDetectionConfig                  the plagiarism detection configuration
 * @param course                                     the target course; mutually exclusive with {@code exerciseGroup}
 * @param exerciseGroup                              the target exam exercise group; mutually exclusive with the course
 * @param templateParticipation                      the source template participation reference
 * @param solutionParticipation                      the source solution participation reference
 */
@JsonIgnoreProperties(ignoreUnknown = true)
// bare @JsonInclude(): request bodies must keep nulls and empty collections on the wire, and the shared
// architecture rule forbids spelling out Include.ALWAYS (only NON_EMPTY or no explicit value are allowed)
@JsonInclude()
public record ImportProgrammingExerciseRequestDTO(@Nullable Long id, String title, String shortName, String channelName, String packageName, String problemStatement,
        String gradingInstructions, Set<String> categories, DifficultyLevel difficulty, ExerciseMode mode, TeamAssignmentConfigDTO teamAssignmentConfig, Double maxPoints,
        Double bonusPoints, IncludedInOverallScore includedInOverallScore, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate,
        ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, AssessmentType assessmentType,
        Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests, Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, Boolean allowOnlineEditor,
        Boolean allowOfflineIde, Boolean allowOnlineIde, Boolean staticCodeAnalysisEnabled, Integer maxStaticCodeAnalysisPenalty, Boolean showTestNamesToStudents,
        Boolean releaseTestsWithExampleSolution, String feedbackSuggestionModule, ProgrammingLanguage programmingLanguage, ProjectType projectType, String projectKey,
        String testRepositoryUri, UpdateProgrammingExerciseBuildConfigDTO buildConfig, Set<GradingCriterionDTO> gradingCriteria, Set<CompetencyLinkDTO> competencyLinks,
        List<AuxiliaryRepositoryDTO> auxiliaryRepositories, SubmissionPolicyDTO submissionPolicy, PlagiarismDetectionConfigDTO plagiarismDetectionConfig, CourseRefDTO course,
        ExerciseGroupIdDTO exerciseGroup, SourceParticipationRefDTO templateParticipation, SourceParticipationRefDTO solutionParticipation)
        implements CompetencyLinksHolderDTO, ProgrammingExerciseRequestDTO {

    /**
     * Reference to a source template or solution participation. The from-file import reads the repository URI off the
     * JSON part to rewrite legacy project names; the plain import ignores it. The wire property name comes from the
     * enclosing record's component, so both slots share one record.
     *
     * @param id            the participation id
     * @param repositoryUri the URI of the participation's repository
     * @param buildPlanId   the id of the participation's build plan
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    // bare @JsonInclude(): request bodies must keep nulls and empty collections on the wire, and the shared
    // architecture rule forbids spelling out Include.ALWAYS (only NON_EMPTY or no explicit value are allowed)
    @JsonInclude()
    public record SourceParticipationRefDTO(Long id, String repositoryUri, String buildPlanId) {
    }

    /**
     * Builds the transient {@link ProgrammingExercise} the import pipeline works on. Null collections map to empty
     * ones because the client's {@code NON_EMPTY} serialization drops empty lists.
     * <p>
     * Competency links are deliberately not bound here, because a link needs a managed competency and only
     * {@code CompetencyExerciseLinkService} can resolve one. The three import handlers then differ: the from-file and
     * the sharing import call that service, so the competencies the author picked in the create form are created with
     * the exercise; the plain import calls it not at all, because it copies an exercise of another course and
     * competencies are course-specific.
     *
     * @return the transient exercise described by this request
     */
    public ProgrammingExercise toEntity() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        ProgrammingExerciseRequestDTO.applyCommonFields(this, exercise);
        // The three collections below are where import and create deliberately differ: import normalizes a missing
        // collection to an empty one, create leaves it at the entity default.
        exercise.setCategories(categories == null ? new HashSet<>() : new HashSet<>(categories));
        exercise.setGradingCriteria(
                gradingCriteria == null ? new HashSet<>() : gradingCriteria.stream().map(GradingCriterionDTO::toEntity).collect(Collectors.toCollection(HashSet::new)));
        exercise.setAuxiliaryRepositories(new ArrayList<>());
        if (auxiliaryRepositories != null) {
            List<AuxiliaryRepository> repositories = auxiliaryRepositories.stream().map(AuxiliaryRepositoryDTO::toEntity).toList();
            repositories.forEach(exercise::addAuxiliaryRepository);
        }
        exercise.setTestRepositoryUri(testRepositoryUri);
        if (templateParticipation != null) {
            TemplateProgrammingExerciseParticipation participation = new TemplateProgrammingExerciseParticipation();
            participation.setId(templateParticipation.id());
            participation.setRepositoryUri(templateParticipation.repositoryUri());
            participation.setBuildPlanId(templateParticipation.buildPlanId());
            exercise.setTemplateParticipation(participation);
        }
        if (solutionParticipation != null) {
            SolutionProgrammingExerciseParticipation participation = new SolutionProgrammingExerciseParticipation();
            participation.setId(solutionParticipation.id());
            participation.setRepositoryUri(solutionParticipation.repositoryUri());
            participation.setBuildPlanId(solutionParticipation.buildPlanId());
            exercise.setSolutionParticipation(participation);
        }
        return exercise;
    }
}
