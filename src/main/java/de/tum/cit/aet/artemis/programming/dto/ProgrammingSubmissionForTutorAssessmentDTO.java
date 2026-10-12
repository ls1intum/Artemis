package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.GradingCriterionDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * The programming submission a tutor opens for assessment, with exactly what the tutor assessment page reads from it.
 * <p>
 * It deliberately is not {@link ProgrammingSubmissionForAssessmentDTO}, whose nested exercise is the full exercise
 * record of the management pages: with the problem statement, the template and solution participations, the build
 * and plagiarism configuration and, for an exam exercise, every text of the exam and the whole course. The page reads
 * none of that, and the response is requested for every submission a tutor opens. The participant is left out as well:
 * the page never shows it, and a tutor must not see it in a double-blind assessment.
 * <p>
 * The results stay at the top level of the submission, as the page reads them there, and an empty list has to arrive
 * as {@code []}, which is why this record uses the bare {@code @JsonInclude()}. The nested records drop their empty
 * members.
 *
 * @param id                     the submission id
 * @param submissionExerciseType the constant discriminator {@code "programming"}
 * @param submissionDate         when the submission was created, which the assessment dashboard shows for the next submission
 * @param buildFailed            whether the build for this submission failed
 * @param participation          the participation of the submission with the exercise the page needs
 * @param results                the results relevant for the requested correction round
 */
@JsonInclude()
public record ProgrammingSubmissionForTutorAssessmentDTO(Long id, String submissionExerciseType, ZonedDateTime submissionDate, Boolean buildFailed, ParticipationDTO participation,
        List<ProgrammingAssessmentResultDTO> results) implements Serializable {

    /**
     * The participation without its participant. The team is reduced to its id, which the page needs to link back to the
     * team of a team exercise.
     *
     * @param id       the participation id
     * @param type     the constant discriminator {@code "programming"}
     * @param testRun  whether this is an instructor test run of an exam
     * @param team     the id of the team, for a team participation
     * @param exercise the exercise of the participation
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ParticipationDTO(Long id, String type, Boolean testRun, TeamReferenceDTO team, ExerciseDTO exercise) implements Serializable {
    }

    /**
     * @param id the team id
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TeamReferenceDTO(Long id) implements Serializable {
    }

    /**
     * The exercise as the tutor assessment page reads it: its identity, the points and dates the assessment is judged
     * by, the instructions the tutor assesses against and what the feedback dialog states about the tests.
     * <p>
     * A course exercise carries its {@code course}, an exam exercise the same course below {@code exerciseGroup.exam},
     * which is where the client looks for it.
     *
     * @param id                                         the exercise id
     * @param type                                       the constant discriminator {@code "programming"}
     * @param title                                      the exercise title
     * @param problemStatement                           the problem statement (markdown) the instructions pane renders
     * @param gradingInstructions                        the unstructured grading instructions
     * @param gradingCriteria                            the structured grading criteria
     * @param maxPoints                                  the achievable points
     * @param bonusPoints                                the achievable bonus points
     * @param includedInOverallScore                     how the exercise counts towards the course score
     * @param teamMode                                   whether the exercise is a team exercise
     * @param assessmentType                             automatic, semi-automatic or manual assessment
     * @param assessmentDueDate                          when the assessment is due
     * @param dueDate                                    when the exercise is due
     * @param buildAndTestStudentSubmissionsAfterDueDate when student submissions are built and tested after the due date
     * @param allowComplaintsForAutomaticAssessments     whether complaints are allowed for automatic assessments
     * @param programmingLanguage                        the programming language, which the build output is annotated by
     * @param projectType                                the project type, which the build output is annotated by
     * @param staticCodeAnalysisEnabled                  whether static code analysis is enabled
     * @param maxStaticCodeAnalysisPenalty               the maximum static code analysis penalty
     * @param showTestNamesToStudents                    whether test names are shown to students
     * @param solutionParticipation                      the solution repository, for the link to the example solution
     * @param course                                     the course of a course exercise
     * @param exerciseGroup                              the exercise group of an exam exercise
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseDTO(Long id, String type, String title, String problemStatement, String gradingInstructions, List<GradingCriterionDTO> gradingCriteria, Double maxPoints,
            Double bonusPoints, IncludedInOverallScore includedInOverallScore, Boolean teamMode, AssessmentType assessmentType, ZonedDateTime assessmentDueDate,
            ZonedDateTime dueDate, ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, Boolean allowComplaintsForAutomaticAssessments,
            ProgrammingLanguage programmingLanguage, ProjectType projectType, Boolean staticCodeAnalysisEnabled, Integer maxStaticCodeAnalysisPenalty,
            Boolean showTestNamesToStudents, SolutionParticipationDTO solutionParticipation, CourseDTO course, ExerciseGroupDTO exerciseGroup) implements Serializable {

        /**
         * Every lazy slot is guarded, so a relation that is not loaded is left out instead of being fetched.
         *
         * @param exercise the exercise to convert
         * @return the exercise as the page reads it
         */
        static ExerciseDTO of(ProgrammingExercise exercise) {
            List<GradingCriterionDTO> gradingCriteria = null;
            var criteria = exercise.getGradingCriteria();
            if (criteria != null && Hibernate.isInitialized(criteria)) {
                gradingCriteria = criteria.stream().map(GradingCriterionDTO::of).toList();
            }
            var solution = exercise.getSolutionParticipation();
            var solutionParticipation = solution != null && Hibernate.isInitialized(solution)
                    ? new SolutionParticipationDTO(TemplateSolutionParticipationDTO.TYPE_SOLUTION, solution.getRepositoryUri())
                    : null;

            CourseDTO course = null;
            ExerciseGroupDTO exerciseGroup = null;
            if (exercise.isExamExercise()) {
                // the client looks the course of an exam exercise up below the exam
                var group = exercise.getExerciseGroup();
                Exam exam = group != null && Hibernate.isInitialized(group) ? group.getExam() : null;
                if (exam != null && Hibernate.isInitialized(exam)) {
                    var examCourse = exam.getCourse();
                    exerciseGroup = new ExerciseGroupDTO(new ExamDTO(exam.getId(), examCourse != null && Hibernate.isInitialized(examCourse) ? CourseDTO.of(examCourse) : null));
                }
            }
            else {
                var courseEntity = exercise.getCourseViaExerciseGroupOrCourseMember();
                course = courseEntity != null && Hibernate.isInitialized(courseEntity) ? CourseDTO.of(courseEntity) : null;
            }

            return new ExerciseDTO(exercise.getId(), ProgrammingExerciseResponseDTO.TYPE, exercise.getTitle(), exercise.getProblemStatement(), exercise.getGradingInstructions(),
                    gradingCriteria, exercise.getMaxPoints(), exercise.getBonusPoints(), exercise.getIncludedInOverallScore(), exercise.isTeamMode(), exercise.getAssessmentType(),
                    exercise.getAssessmentDueDate(), exercise.getDueDate(), exercise.getBuildAndTestStudentSubmissionsAfterDueDate(),
                    exercise.getAllowComplaintsForAutomaticAssessments(), exercise.getProgrammingLanguage(), exercise.getProjectType(), exercise.isStaticCodeAnalysisEnabled(),
                    exercise.getMaxStaticCodeAnalysisPenalty(), exercise.getShowTestNamesToStudents(), solutionParticipation, course, exerciseGroup);
        }
    }

    /**
     * @param type          the constant discriminator {@code "solution"}, without which the participation cannot be read back as an entity
     * @param repositoryUri the URI of the solution repository
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record SolutionParticipationDTO(String type, String repositoryUri) implements Serializable {
    }

    /**
     * The settings of the course the tutor assessment page reads: the rounding of scores, the Athena banner and the
     * length of a complaint response.
     *
     * @param id                            the course id, which the access rights of the user are checked against
     * @param accuracyOfScores              the number of decimals scores are rounded to
     * @param athenaGradingFeedbackEnabled  whether feedback suggestions are enabled
     * @param maxComplaintResponseTextLimit the maximum length of a complaint response
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseDTO(Long id, Integer accuracyOfScores, boolean athenaGradingFeedbackEnabled, int maxComplaintResponseTextLimit) implements Serializable {

        static CourseDTO of(Course course) {
            return new CourseDTO(course.getId(), course.getAccuracyOfScores(), course.isAthenaGradingFeedbackEnabled(), course.getMaxComplaintResponseTextLimit());
        }
    }

    /**
     * @param exam the exam the exercise belongs to
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseGroupDTO(ExamDTO exam) implements Serializable {
    }

    /**
     * @param id     the exam id
     * @param course the course the exam belongs to
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExamDTO(Long id, CourseDTO course) implements Serializable {
    }

    /**
     * Converts a submission together with the exercise it belongs to and the results that should be shown.
     *
     * @param submission the submission to convert
     * @param exercise   the exercise of the submission
     * @param results    the results to nest under the submission; null elements are skipped
     * @return the converted DTO, or {@code null} if the submission was {@code null}
     */
    public static ProgrammingSubmissionForTutorAssessmentDTO of(@Nullable ProgrammingSubmission submission, ProgrammingExercise exercise, Collection<Result> results) {
        if (submission == null) {
            return null;
        }
        ParticipationDTO participation = null;
        if (submission.getParticipation() instanceof ProgrammingExerciseStudentParticipation studentParticipation) {
            var team = studentParticipation.getTeam().filter(Hibernate::isInitialized).map(candidate -> new TeamReferenceDTO(candidate.getId())).orElse(null);
            participation = new ParticipationDTO(studentParticipation.getId(), ProgrammingExerciseStudentParticipationDTO.TYPE, studentParticipation.isTestRun(), team,
                    ExerciseDTO.of(exercise));
        }
        var resultDTOs = results == null ? null : results.stream().filter(Objects::nonNull).map(ProgrammingAssessmentResultDTO::of).toList();
        return new ProgrammingSubmissionForTutorAssessmentDTO(submission.getId(), ProgrammingSubmissionForAssessmentDTO.SUBMISSION_EXERCISE_TYPE, submission.getSubmissionDate(),
                submission.isBuildFailed(), participation, resultDTOs);
    }
}
