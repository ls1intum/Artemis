package de.tum.cit.aet.artemis.fileupload.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseVariantGroupReferenceDTO;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;

/**
 * DTO-safe response contract for file upload exercise CRUD, import, search, and course-list endpoints.
 *
 * @param id                                     the exercise identifier
 * @param type                                   the client discriminator, always {@code file-upload}
 * @param title                                  the exercise title
 * @param channelName                            the communication channel name
 * @param shortName                              the exercise short name
 * @param problemStatement                       the problem statement
 * @param categories                             the JSON-encoded exercise categories, when initialized
 * @param difficulty                             the exercise difficulty
 * @param maxPoints                              the maximum points
 * @param bonusPoints                            the bonus points
 * @param includedInOverallScore                 how the exercise contributes to the course score
 * @param assessmentType                         the assessment type
 * @param mode                                   whether the exercise is individual or team-based
 * @param teamMode                               whether the exercise is team-based
 * @param teamAssignmentConfig                   the team-assignment settings, when initialized
 * @param allowComplaintsForAutomaticAssessments whether complaints for automatic assessments are allowed
 * @param allowFeedbackRequests                  whether feedback requests are allowed
 * @param presentationScoreEnabled               whether presentation scores are enabled
 * @param secondCorrectionEnabled                whether a second correction round is enabled
 * @param feedbackSuggestionModule               the feedback suggestion module
 * @param gradingInstructions                    the free-text grading instructions
 * @param releaseDate                            the release date
 * @param startDate                              the start date
 * @param dueDate                                the due date
 * @param assessmentDueDate                      the assessment due date
 * @param exampleSolutionPublicationDate         the example-solution publication date
 * @param exampleSolution                        the example solution
 * @param filePattern                            the accepted file extensions
 * @param gradingInstructionFeedbackUsed         whether existing feedback uses structured grading instructions
 * @param course                                 the minimal course context for a course exercise
 * @param exerciseGroup                          the minimal exercise-group and exam context for an exam exercise
 * @param exerciseVariantGroup                   the minimal exercise-variant-group context, when initialized
 * @param gradingCriteria                        the grading criteria, when initialized together with their instructions
 * @param competencyLinks                        the competency links, when initialized
 * @param plagiarismDetectionConfig              the plagiarism-detection settings, when initialized
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FileUploadExerciseDTO(Long id, String type, @Nullable String title, @Nullable String channelName, @Nullable String shortName, @Nullable String problemStatement,
        @Nullable Set<String> categories, @Nullable DifficultyLevel difficulty, @Nullable Double maxPoints, @Nullable Double bonusPoints,
        @Nullable IncludedInOverallScore includedInOverallScore, @Nullable AssessmentType assessmentType, @Nullable ExerciseMode mode, boolean teamMode,
        @Nullable FileUploadTeamAssignmentConfigDTO teamAssignmentConfig, @Nullable Boolean allowComplaintsForAutomaticAssessments, @Nullable Boolean allowFeedbackRequests,
        @Nullable Boolean presentationScoreEnabled, @Nullable Boolean secondCorrectionEnabled, @Nullable String feedbackSuggestionModule, @Nullable String gradingInstructions,
        @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate,
        @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable String exampleSolution, @Nullable String filePattern, boolean gradingInstructionFeedbackUsed,
        @Nullable CourseContextDTO course, @Nullable ExerciseGroupContextDTO exerciseGroup, @Nullable ExerciseVariantGroupReferenceDTO exerciseVariantGroup,
        @Nullable Set<GradingCriterionDTO> gradingCriteria, @Nullable Set<CompetencyLinkDTO> competencyLinks,
        @Nullable FileUploadPlagiarismDetectionConfigDTO plagiarismDetectionConfig) {

    /**
     * Maps a full create, import, detail, update, or re-evaluation response. Optional associations are included only when Hibernate has initialized them.
     *
     * @param exercise the exercise to map
     * @return the full response DTO
     */
    public static FileUploadExerciseDTO of(FileUploadExercise exercise) {
        return map(exercise, true, true);
    }

    /**
     * Maps a lean search result. Course or exam context is retained, while team, grading, competency, and plagiarism associations remain omitted.
     *
     * @param exercise the search result to map
     * @return the lean search response DTO
     */
    public static FileUploadExerciseDTO forSearch(FileUploadExercise exercise) {
        return map(exercise, true, false);
    }

    /**
     * Maps a lean course-management list entry. Categories and exercise-variant-group context are retained when fetched; course context and other optional associations are
     * intentionally omitted.
     *
     * @param exercise the course exercise to map
     * @return the lean course-list response DTO
     */
    public static FileUploadExerciseDTO forCourseList(FileUploadExercise exercise) {
        return map(exercise, false, false);
    }

    private static FileUploadExerciseDTO map(FileUploadExercise exercise, boolean includeContext, boolean includeInitializedAssociations) {
        Set<String> categories = initialized(exercise.getCategories()) ? Set.copyOf(exercise.getCategories()) : null;
        FileUploadTeamAssignmentConfigDTO teamAssignmentConfig = includeInitializedAssociations && initialized(exercise.getTeamAssignmentConfig())
                ? FileUploadTeamAssignmentConfigDTO.of(exercise.getTeamAssignmentConfig())
                : null;
        Set<GradingCriterionDTO> gradingCriteria = includeInitializedAssociations ? mapGradingCriteria(exercise.getGradingCriteria()) : null;
        Set<CompetencyLinkDTO> competencyLinks = includeInitializedAssociations && initialized(exercise.getCompetencyLinks())
                ? exercise.getCompetencyLinks().stream().map(CompetencyLinkDTO::of).collect(Collectors.toSet())
                : null;
        FileUploadPlagiarismDetectionConfigDTO plagiarismDetectionConfig = includeInitializedAssociations && initialized(exercise.getPlagiarismDetectionConfig())
                ? FileUploadPlagiarismDetectionConfigDTO.of(exercise.getPlagiarismDetectionConfig())
                : null;
        CourseContextDTO course = includeContext && exercise.isCourseExercise() ? CourseContextDTO.of(exercise.getCourseViaExerciseGroupOrCourseMember()) : null;
        ExerciseGroupContextDTO exerciseGroup = includeContext && exercise.isExamExercise() ? ExerciseGroupContextDTO.of(exercise.getExerciseGroup()) : null;
        ExerciseVariantGroupReferenceDTO exerciseVariantGroup = ExerciseVariantGroupReferenceDTO.ofNullable(exercise.getExerciseVariantGroup());

        return new FileUploadExerciseDTO(exercise.getId(), "file-upload", exercise.getTitle(), exercise.getChannelName(), exercise.getShortName(), exercise.getProblemStatement(),
                categories, exercise.getDifficulty(), exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(), exercise.getAssessmentType(),
                exercise.getMode(), exercise.isTeamMode(), teamAssignmentConfig, exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getAllowFeedbackRequests(),
                exercise.getPresentationScoreEnabled(), exercise.getSecondCorrectionEnabled(), exercise.getFeedbackSuggestionModule(), exercise.getGradingInstructions(),
                exercise.getReleaseDate(), exercise.getStartDate(), exercise.getDueDate(), exercise.getAssessmentDueDate(), exercise.getExampleSolutionPublicationDate(),
                exercise.getExampleSolution(), exercise.getFilePattern(), exercise.isGradingInstructionFeedbackUsed(), course, exerciseGroup, exerciseVariantGroup, gradingCriteria,
                competencyLinks, plagiarismDetectionConfig);
    }

    private static Set<GradingCriterionDTO> mapGradingCriteria(@Nullable Set<GradingCriterion> gradingCriteria) {
        if (!initialized(gradingCriteria)
                || gradingCriteria.stream().map(GradingCriterion::getStructuredGradingInstructions).anyMatch(instructions -> !Hibernate.isInitialized(instructions))) {
            return null;
        }
        return gradingCriteria.stream().map(GradingCriterionDTO::of).collect(Collectors.toSet());
    }

    private static boolean initialized(@Nullable Object association) {
        return association != null && Hibernate.isInitialized(association);
    }

    /**
     * Minimal course information needed by file upload exercise management and client-side access-right checks.
     *
     * @param id                                    the course identifier
     * @param title                                 the course title
     * @param shortName                             the course short name
     * @param testCourse                            whether this is a test course
     * @param presentationScore                     the configured number of presentation scores
     * @param courseInformationSharingConfiguration the communication and messaging configuration
     * @param accuracyOfScores                      the score display accuracy
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseContextDTO(Long id, @Nullable String title, @Nullable String shortName, @Nullable Boolean testCourse, @Nullable Integer presentationScore,
            @Nullable CourseInformationSharingConfiguration courseInformationSharingConfiguration, @Nullable Integer accuracyOfScores) {

        /**
         * Maps a course without exposing exercises or other course graphs.
         *
         * @param course the course to map
         * @return the minimal course context
         */
        public static CourseContextDTO of(Course course) {
            return new CourseContextDTO(course.getId(), course.getTitle(), course.getShortName(), course.isTestCourse(), course.getPresentationScore(),
                    course.getCourseInformationSharingConfiguration(), course.getAccuracyOfScores());
        }
    }

    /**
     * Minimal exercise-group context for an exam exercise.
     *
     * @param id   the exercise-group identifier
     * @param exam the containing exam context
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseGroupContextDTO(Long id, @Nullable ExamContextDTO exam) {

        /**
         * Maps an exercise group without exposing its exercise collection.
         *
         * @param exerciseGroup the exercise group to map
         * @return the minimal exercise-group context
         */
        public static ExerciseGroupContextDTO of(ExerciseGroup exerciseGroup) {
            return new ExerciseGroupContextDTO(exerciseGroup.getId(), exerciseGroup.getExam() == null ? null : ExamContextDTO.of(exerciseGroup.getExam()));
        }
    }

    /**
     * Minimal exam context needed by file upload exercise management and route construction.
     *
     * @param id                             the exam identifier
     * @param title                          the exam title
     * @param course                         the minimal containing course
     * @param startDate                      the exam start date
     * @param endDate                        the exam end date
     * @param exampleSolutionPublicationDate the example-solution publication date
     * @param numberOfCorrectionRoundsInExam the number of correction rounds
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamContextDTO(Long id, @Nullable String title, @Nullable CourseContextDTO course, @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime endDate,
            @Nullable ZonedDateTime exampleSolutionPublicationDate, @Nullable Integer numberOfCorrectionRoundsInExam) {

        /**
         * Maps an exam without exposing exercise groups, exercises, or student exams.
         *
         * @param exam the exam to map
         * @return the minimal exam context
         */
        public static ExamContextDTO of(Exam exam) {
            return new ExamContextDTO(exam.getId(), exam.getTitle(), exam.getCourse() == null ? null : CourseContextDTO.of(exam.getCourse()), exam.getStartDate(),
                    exam.getEndDate(), exam.getExampleSolutionPublicationDate(), exam.getNumberOfCorrectionRoundsInExam());
        }
    }
}
