package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseRefDTO;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssignmentConfigDTO;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismDetectionConfigDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * The fields {@link CreateProgrammingExerciseDTO} and {@link ImportProgrammingExerciseRequestDTO} bind identically,
 * and the one mapper that binds them. Both records implement this interface for free, since a record generates an
 * accessor per component.
 * <p>
 * The three collections the two requests handle differently — {@code categories}, {@code gradingCriteria} and
 * {@code auxiliaryRepositories}, where create leaves a missing value untouched and import normalizes it to an empty
 * collection — stay in each record's own {@code toEntity()}, as do the import-only fields.
 * <p>
 * This is an interface, not a record, so it is exempt from the "every {@code *DTO} in a dto package is an annotated
 * record" architecture rule while still satisfying the {@code *DTO} naming rule (precedent:
 * {@link de.tum.cit.aet.artemis.exercise.dto.CompetencyLinksHolderDTO}).
 */
public interface ProgrammingExerciseRequestDTO {

    Long id();

    String title();

    String shortName();

    String channelName();

    String packageName();

    String problemStatement();

    String gradingInstructions();

    DifficultyLevel difficulty();

    ExerciseMode mode();

    TeamAssignmentConfigDTO teamAssignmentConfig();

    Double maxPoints();

    Double bonusPoints();

    IncludedInOverallScore includedInOverallScore();

    ZonedDateTime releaseDate();

    ZonedDateTime startDate();

    ZonedDateTime dueDate();

    ZonedDateTime assessmentDueDate();

    ZonedDateTime exampleSolutionPublicationDate();

    ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate();

    AssessmentType assessmentType();

    Boolean allowComplaintsForAutomaticAssessments();

    Boolean allowFeedbackRequests();

    Boolean presentationScoreEnabled();

    Boolean secondCorrectionEnabled();

    Boolean allowOnlineEditor();

    Boolean allowOfflineIde();

    Boolean allowOnlineIde();

    Boolean staticCodeAnalysisEnabled();

    Integer maxStaticCodeAnalysisPenalty();

    Boolean showTestNamesToStudents();

    Boolean releaseTestsWithExampleSolution();

    String feedbackSuggestionModule();

    ProgrammingLanguage programmingLanguage();

    ProjectType projectType();

    UpdateProgrammingExerciseBuildConfigDTO buildConfig();

    SubmissionPolicyDTO submissionPolicy();

    PlagiarismDetectionConfigDTO plagiarismDetectionConfig();

    CourseRefDTO course();

    ExerciseGroupIdDTO exerciseGroup();

    /**
     * Copies every field both request bodies bind the same way onto the transient exercise, reproducing the binding
     * the entity request body produced before these DTOs existed.
     *
     * @param request  the parsed request body
     * @param exercise the transient exercise to write to
     */
    static void applyCommonFields(ProgrammingExerciseRequestDTO request, ProgrammingExercise exercise) {
        exercise.setId(request.id());
        exercise.setTitle(request.title());
        exercise.setShortName(request.shortName());
        exercise.setChannelName(request.channelName());
        exercise.setPackageName(request.packageName());
        exercise.setProblemStatement(request.problemStatement());
        exercise.setGradingInstructions(request.gradingInstructions());
        exercise.setDifficulty(request.difficulty());
        // mode, maxPoints and bonusPoints map onto non-nullable columns with entity defaults: only overwrite them
        // when the request actually carries a value.
        if (request.mode() != null) {
            exercise.setMode(request.mode());
        }
        exercise.setTeamAssignmentConfig(request.teamAssignmentConfig() == null ? null : request.teamAssignmentConfig().toEntity());
        if (request.maxPoints() != null) {
            exercise.setMaxPoints(request.maxPoints());
        }
        if (request.bonusPoints() != null) {
            exercise.setBonusPoints(request.bonusPoints());
        }
        if (request.includedInOverallScore() != null) {
            exercise.setIncludedInOverallScore(request.includedInOverallScore());
        }
        exercise.setReleaseDate(request.releaseDate());
        exercise.setStartDate(request.startDate());
        exercise.setDueDate(request.dueDate());
        exercise.setAssessmentDueDate(request.assessmentDueDate());
        exercise.setExampleSolutionPublicationDate(request.exampleSolutionPublicationDate());
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(request.buildAndTestStudentSubmissionsAfterDueDate());
        // The exercise has no assessment-type default: a missing value must stay null, exactly as the entity binding left it.
        exercise.setAssessmentType(request.assessmentType());
        exercise.setAllowComplaintsForAutomaticAssessments(Boolean.TRUE.equals(request.allowComplaintsForAutomaticAssessments()));
        exercise.setAllowFeedbackRequests(Boolean.TRUE.equals(request.allowFeedbackRequests()));
        if (request.presentationScoreEnabled() != null) {
            // the column has an initialized `false` entity default that an absent key must not overwrite with null
            exercise.setPresentationScoreEnabled(request.presentationScoreEnabled());
        }
        exercise.setSecondCorrectionEnabled(Boolean.TRUE.equals(request.secondCorrectionEnabled()));
        exercise.setAllowOnlineEditor(request.allowOnlineEditor());
        exercise.setAllowOfflineIde(request.allowOfflineIde());
        exercise.setAllowOnlineIde(Boolean.TRUE.equals(request.allowOnlineIde()));
        exercise.setStaticCodeAnalysisEnabled(request.staticCodeAnalysisEnabled());
        exercise.setMaxStaticCodeAnalysisPenalty(request.maxStaticCodeAnalysisPenalty());
        if (request.showTestNamesToStudents() != null) {
            // the setter writes a primitive field, so a null would unbox into a NullPointerException
            exercise.setShowTestNamesToStudents(request.showTestNamesToStudents());
        }
        exercise.setReleaseTestsWithExampleSolution(Boolean.TRUE.equals(request.releaseTestsWithExampleSolution()));
        exercise.setFeedbackSuggestionModule(request.feedbackSuggestionModule());
        exercise.setProgrammingLanguage(request.programmingLanguage());
        exercise.setProjectType(request.projectType());
        if (request.buildConfig() != null) {
            exercise.setBuildConfig(request.buildConfig().toEntity());
        }
        exercise.setSubmissionPolicy(request.submissionPolicy() == null ? null : request.submissionPolicy().toEntity());
        exercise.setPlagiarismDetectionConfig(toPlagiarismDetectionConfigEntity(request.plagiarismDetectionConfig()));
        if (request.course() != null) {
            Course courseEntity = new Course();
            courseEntity.setId(request.course().id());
            exercise.setCourse(courseEntity);
        }
        if (request.exerciseGroup() != null) {
            exercise.setExerciseGroup(request.exerciseGroup().toEntity());
        }
    }

    /**
     * Builds a transient plagiarism detection configuration; the shared DTO carries no {@code toEntity()} and must not
     * be modified from this module.
     *
     * @param dto the parsed configuration (may be {@code null})
     * @return the transient configuration, or {@code null} if the input was {@code null}
     */
    static PlagiarismDetectionConfig toPlagiarismDetectionConfigEntity(PlagiarismDetectionConfigDTO dto) {
        if (dto == null) {
            return null;
        }
        PlagiarismDetectionConfig config = new PlagiarismDetectionConfig();
        config.setId(dto.id());
        config.setContinuousPlagiarismControlEnabled(dto.continuousPlagiarismControlEnabled());
        config.setContinuousPlagiarismControlPostDueDateChecksEnabled(dto.continuousPlagiarismControlPostDueDateChecksEnabled());
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(dto.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod());
        config.setSimilarityThreshold(dto.similarityThreshold());
        config.setMinimumScore(dto.minimumScore());
        config.setMinimumSize(dto.minimumSize());
        return config;
    }
}
