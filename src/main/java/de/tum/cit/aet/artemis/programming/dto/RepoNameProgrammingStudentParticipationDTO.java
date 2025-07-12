package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * DTO for the endpoint
 * {@link de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseParticipationResource#getStudentParticipationByRepoName(String)}
 * constructing a participation DTO including exercise and course information
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepoNameProgrammingStudentParticipationDTO(long id, ZonedDateTime individualDueDate, String participantName, String participantIdentifier, String repositoryUri,
        String buildPlanId, String branch, RepoNameProgrammingExerciseDTO exercise) {

    /**
     * Converts a ProgrammingExerciseStudentParticipation into a dto for the endpoint
     * {@link de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseParticipationResource#getStudentParticipationByRepoName(String)}.
     *
     * @param participation to convert
     * @return the converted DTO
     */
    public static RepoNameProgrammingStudentParticipationDTO of(ProgrammingExerciseStudentParticipation participation) {
        return Optional
                .ofNullable(participation).map(p -> new RepoNameProgrammingStudentParticipationDTO(p.getId(), p.getIndividualDueDate(), p.getParticipantName(),
                        p.getParticipantIdentifier(), p.getRepositoryUri(), p.getBuildPlanId(), p.getBranch(), RepoNameProgrammingExerciseDTO.of(p.getProgrammingExercise())))
                .orElse(null);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record RepoNameProgrammingExerciseDTO(long id, String problemStatement, String title, String shortName, ZonedDateTime releaseDate, ZonedDateTime startDate,
            ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Double maxPoints, Double bonusPoints, AssessmentType assessmentType,
            boolean allowComplaintsForAutomaticAssessments, boolean allowFeedbackRequests, DifficultyLevel difficulty, ExerciseMode mode,
            IncludedInOverallScore includedInOverallScore, ExerciseType exerciseType, ZonedDateTime exampleSolutionPublicationDate, RepoNameCourseDTO course, String projectKey,
            ProgrammingLanguage programmingLanguage, Boolean showTestNamesToStudents) {

        /**
         * Converts a ProgrammingExercise into a dto for the endpoint
         * {@link de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseParticipationResource#getStudentParticipationByRepoName(String)}.
         *
         * @param exercise to convert
         * @return the converted DTO
         */
        public static RepoNameProgrammingExerciseDTO of(ProgrammingExercise exercise) {
            return Optional.ofNullable(exercise)
                    .map(e -> new RepoNameProgrammingExerciseDTO(e.getId(), e.getProblemStatement(), e.getTitle(), e.getShortName(), e.getReleaseDate(), e.getStartDate(),
                            e.getDueDate(), e.getAssessmentDueDate(), e.getMaxPoints(), e.getBonusPoints(), e.getAssessmentType(), e.getAllowComplaintsForAutomaticAssessments(),
                            e.getAllowFeedbackRequests(), e.getDifficulty(), e.getMode(), e.getIncludedInOverallScore(), e.getExerciseType(), e.getExampleSolutionPublicationDate(),
                            RepoNameCourseDTO.of(e.getCourseViaExerciseGroupOrCourseMember()), e.getProjectKey(), e.getProgrammingLanguage(), e.getShowTestNamesToStudents()))
                    .orElse(null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record RepoNameCourseDTO(long id, String title, String shortName) {

        /**
         * Converts a Course into a dto for the endpoint
         * {@link de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseParticipationResource#getStudentParticipationByRepoName(String)}.
         *
         * @param course to convert
         * @return the converted DTO
         */
        public static RepoNameCourseDTO of(Course course) {
            return Optional.ofNullable(course).map(c -> new RepoNameCourseDTO(c.getId(), c.getTitle(), c.getShortName())).orElse(null);
        }
    }
}
