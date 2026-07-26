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
import de.tum.cit.aet.artemis.course.domain.Course;
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
 * @param competencyLinks                            the linked competencies; cleared by the import handlers
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
        ExerciseGroupIdDTO exerciseGroup, TemplateParticipationRefDTO templateParticipation, SolutionParticipationRefDTO solutionParticipation)
        implements CompetencyLinksHolderDTO {

    /**
     * Reference to the source template participation. The from-file import reads the repository URI off the JSON part
     * to rewrite legacy project names; the plain import ignores it.
     *
     * @param id            the participation id
     * @param repositoryUri the URI of the template repository
     * @param buildPlanId   the id of the template build plan
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    // bare @JsonInclude(): request bodies must keep nulls and empty collections on the wire, and the shared
    // architecture rule forbids spelling out Include.ALWAYS (only NON_EMPTY or no explicit value are allowed)
    @JsonInclude()
    public record TemplateParticipationRefDTO(Long id, String repositoryUri, String buildPlanId) {
    }

    /**
     * Reference to the source solution participation. The from-file import reads the repository URI off the JSON part
     * to rewrite legacy project names; the plain import ignores it.
     *
     * @param id            the participation id
     * @param repositoryUri the URI of the solution repository
     * @param buildPlanId   the id of the solution build plan
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    // bare @JsonInclude(): request bodies must keep nulls and empty collections on the wire, and the shared
    // architecture rule forbids spelling out Include.ALWAYS (only NON_EMPTY or no explicit value are allowed)
    @JsonInclude()
    public record SolutionParticipationRefDTO(Long id, String repositoryUri, String buildPlanId) {
    }

    /**
     * Builds the transient {@link ProgrammingExercise} the import pipeline works on. Null collections map to empty
     * ones because the client's {@code NON_EMPTY} serialization drops empty lists. Competency links are deliberately
     * not bound: the import handlers clear them, since competencies are course-specific.
     *
     * @return the transient exercise described by this request
     */
    public ProgrammingExercise toEntity() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        exercise.setTitle(title);
        exercise.setShortName(shortName);
        exercise.setChannelName(channelName);
        exercise.setPackageName(packageName);
        exercise.setProblemStatement(problemStatement);
        exercise.setGradingInstructions(gradingInstructions);
        exercise.setCategories(categories == null ? new HashSet<>() : new HashSet<>(categories));
        exercise.setDifficulty(difficulty);
        // mode, maxPoints and bonusPoints map onto non-nullable columns with entity defaults: only overwrite them
        // when the request actually carries a value.
        if (mode != null) {
            exercise.setMode(mode);
        }
        exercise.setTeamAssignmentConfig(teamAssignmentConfig == null ? null : teamAssignmentConfig.toEntity());
        if (maxPoints != null) {
            exercise.setMaxPoints(maxPoints);
        }
        if (bonusPoints != null) {
            exercise.setBonusPoints(bonusPoints);
        }
        if (includedInOverallScore != null) {
            exercise.setIncludedInOverallScore(includedInOverallScore);
        }
        exercise.setReleaseDate(releaseDate);
        exercise.setStartDate(startDate);
        exercise.setDueDate(dueDate);
        exercise.setAssessmentDueDate(assessmentDueDate);
        exercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(buildAndTestStudentSubmissionsAfterDueDate);
        // The exercise has no assessment-type default: a missing value must stay null, exactly as the entity binding left it.
        exercise.setAssessmentType(assessmentType);
        exercise.setAllowComplaintsForAutomaticAssessments(Boolean.TRUE.equals(allowComplaintsForAutomaticAssessments));
        exercise.setAllowFeedbackRequests(Boolean.TRUE.equals(allowFeedbackRequests));
        exercise.setPresentationScoreEnabled(presentationScoreEnabled);
        exercise.setSecondCorrectionEnabled(Boolean.TRUE.equals(secondCorrectionEnabled));
        exercise.setAllowOnlineEditor(allowOnlineEditor);
        exercise.setAllowOfflineIde(allowOfflineIde);
        exercise.setAllowOnlineIde(Boolean.TRUE.equals(allowOnlineIde));
        exercise.setStaticCodeAnalysisEnabled(staticCodeAnalysisEnabled);
        exercise.setMaxStaticCodeAnalysisPenalty(maxStaticCodeAnalysisPenalty);
        if (showTestNamesToStudents != null) {
            // the setter writes a primitive field, so a null would unbox into a NullPointerException
            exercise.setShowTestNamesToStudents(showTestNamesToStudents);
        }
        exercise.setReleaseTestsWithExampleSolution(Boolean.TRUE.equals(releaseTestsWithExampleSolution));
        exercise.setFeedbackSuggestionModule(feedbackSuggestionModule);
        exercise.setProgrammingLanguage(programmingLanguage);
        exercise.setProjectType(projectType);
        exercise.setTestRepositoryUri(testRepositoryUri);
        if (buildConfig != null) {
            exercise.setBuildConfig(buildConfig.toEntity());
        }
        exercise.setGradingCriteria(
                gradingCriteria == null ? new HashSet<>() : gradingCriteria.stream().map(GradingCriterionDTO::toEntity).collect(Collectors.toCollection(HashSet::new)));
        exercise.setAuxiliaryRepositories(new ArrayList<>());
        if (auxiliaryRepositories != null) {
            List<AuxiliaryRepository> repositories = auxiliaryRepositories.stream().map(AuxiliaryRepositoryDTO::toEntity).toList();
            repositories.forEach(exercise::addAuxiliaryRepository);
        }
        exercise.setSubmissionPolicy(submissionPolicy == null ? null : submissionPolicy.toEntity());
        exercise.setPlagiarismDetectionConfig(CreateProgrammingExerciseDTO.toPlagiarismDetectionConfigEntity(plagiarismDetectionConfig));
        if (course != null) {
            Course courseEntity = new Course();
            courseEntity.setId(course.id());
            exercise.setCourse(courseEntity);
        }
        if (exerciseGroup != null) {
            exercise.setExerciseGroup(exerciseGroup.toEntity());
        }
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
