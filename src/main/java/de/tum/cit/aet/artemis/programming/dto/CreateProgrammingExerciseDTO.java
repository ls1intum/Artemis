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

/**
 * Request body of {@code POST programming-exercises/setup}.
 * <p>
 * {@code id} is deliberately part of the record even though a new exercise must not have one: the validation service
 * rejects a non-null id with {@code 400 idexists}, and dropping the component would let a client-supplied id vanish
 * silently, turning that rejection into a successful creation. {@link #toEntity()} therefore maps the id onto the
 * entity before validation runs.
 * <p>
 * {@code course} reuses the shared {@link CourseRefDTO}: the unchanged client posts a nested course object and the
 * server re-loads the course by id. Client-posted {@code templateParticipation}/{@code solutionParticipation} stubs
 * are ignored via {@code ignoreUnknown}.
 * <p>
 * {@code projectKey} is deliberately absent. The entity request body accepted it - Jackson infers the private field as
 * a mutator because a public getter of the same name exists - so a client could name the VCS/CI project of the
 * exercise it created. The key is derived from course and exercise short name, and it is derived here now.
 *
 * @param id                                         the exercise id; must be {@code null} for a valid creation
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
 * @param buildConfig                                the build configuration; {@code null} yields 400 buildConfigMissing
 * @param gradingCriteria                            the structured grading criteria
 * @param competencyLinks                            the linked competencies
 * @param auxiliaryRepositories                      the auxiliary repositories
 * @param submissionPolicy                           the submission policy
 * @param plagiarismDetectionConfig                  the plagiarism detection configuration
 * @param course                                     the target course; mutually exclusive with {@code exerciseGroup}
 * @param exerciseGroup                              the target exam exercise group; mutually exclusive with the course
 */
@JsonIgnoreProperties(ignoreUnknown = true)
// bare @JsonInclude(): request bodies must keep nulls and empty collections on the wire, and the shared
// architecture rule forbids spelling out Include.ALWAYS (only NON_EMPTY or no explicit value are allowed)
@JsonInclude()
public record CreateProgrammingExerciseDTO(@Nullable Long id, String title, String shortName, String channelName, String packageName, String problemStatement,
        String gradingInstructions, Set<String> categories, DifficultyLevel difficulty, ExerciseMode mode, TeamAssignmentConfigDTO teamAssignmentConfig, Double maxPoints,
        Double bonusPoints, IncludedInOverallScore includedInOverallScore, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate,
        ZonedDateTime assessmentDueDate, ZonedDateTime exampleSolutionPublicationDate, ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, AssessmentType assessmentType,
        Boolean allowComplaintsForAutomaticAssessments, Boolean allowFeedbackRequests, Boolean presentationScoreEnabled, Boolean secondCorrectionEnabled, Boolean allowOnlineEditor,
        Boolean allowOfflineIde, Boolean allowOnlineIde, Boolean staticCodeAnalysisEnabled, Integer maxStaticCodeAnalysisPenalty, Boolean showTestNamesToStudents,
        Boolean releaseTestsWithExampleSolution, String feedbackSuggestionModule, ProgrammingLanguage programmingLanguage, ProjectType projectType,
        UpdateProgrammingExerciseBuildConfigDTO buildConfig, Set<GradingCriterionDTO> gradingCriteria, Set<CompetencyLinkDTO> competencyLinks,
        List<AuxiliaryRepositoryDTO> auxiliaryRepositories, SubmissionPolicyDTO submissionPolicy, PlagiarismDetectionConfigDTO plagiarismDetectionConfig, CourseRefDTO course,
        ExerciseGroupIdDTO exerciseGroup) implements CompetencyLinksHolderDTO, ProgrammingExerciseRequestDTO {

    /**
     * Builds the transient {@link ProgrammingExercise} the creation pipeline works on, reproducing the binding the
     * entity request body produced before this DTO existed. Competency links are deliberately not bound here; the
     * creation resource applies them through the competency link service, which resolves managed competencies.
     *
     * @return the transient exercise described by this request
     */
    public ProgrammingExercise toEntity() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        ProgrammingExerciseRequestDTO.applyCommonFields(this, exercise);
        // The three collections below are where create and import deliberately differ: create leaves a missing
        // collection at the entity default, import normalizes it to an empty one.
        if (categories != null) {
            exercise.setCategories(new HashSet<>(categories));
        }
        if (gradingCriteria != null) {
            exercise.setGradingCriteria(gradingCriteria.stream().map(GradingCriterionDTO::toEntity).collect(Collectors.toCollection(HashSet::new)));
        }
        if (auxiliaryRepositories != null) {
            List<AuxiliaryRepository> repositories = auxiliaryRepositories.stream().map(AuxiliaryRepositoryDTO::toEntity).toList();
            exercise.setAuxiliaryRepositories(new ArrayList<>());
            repositories.forEach(exercise::addAuxiliaryRepository);
        }
        return exercise;
    }
}
