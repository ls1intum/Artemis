package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCaseType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RepoUrlProgrammingStudentParticipationDTO(long id, ZonedDateTime individualDueDate, Set<RepoUrlSubmissionDTO> submissions, String participantName,
        String participantIdentifier, String repositoryUri, String buildPlanId, String branch, RepoUrlProgrammingExerciseDTO exercise) {

    /**
     * Converts a ProgrammingExerciseStudentParticipation into a dto for the endpoint
     * {@link de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseResource#getProgrammingExerciseByProjectKey(String)}.
     *
     * @param participation to convert
     * @return the converted DTO
     */
    public static RepoUrlProgrammingStudentParticipationDTO of(ProgrammingExerciseStudentParticipation participation) {
        return Optional.ofNullable(participation)
                .map(p -> new RepoUrlProgrammingStudentParticipationDTO(p.getId(), p.getIndividualDueDate(),
                        Optional.ofNullable(p.getSubmissions()).orElse(Set.of()).stream().map(s -> RepoUrlSubmissionDTO.of((ProgrammingSubmission) s)).collect(Collectors.toSet()),
                        p.getParticipantName(), p.getParticipantIdentifier(), p.getRepositoryUri(), p.getBuildPlanId(), p.getBranch(),
                        RepoUrlProgrammingExerciseDTO.of(p.getProgrammingExercise())))
                .orElse(null);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record RepoUrlSubmissionDTO(long id, Boolean submitted, ZonedDateTime submissionDate, SubmissionType type, Boolean exampleSubmission, Long durationInMinutes,
            List<RepoUrlResultDTO> results, String commitHash, boolean buildFailed) {

        /**
         * Converts a ProgrammingSubmission into a dto for the endpoint
         * {@link de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseResource#getProgrammingExerciseByProjectKey(String)}.
         *
         * @param submission to convert
         * @return the converted DTO
         */
        public static RepoUrlSubmissionDTO of(ProgrammingSubmission submission) {
            return Optional.ofNullable(submission)
                    .map(s -> new RepoUrlSubmissionDTO(s.getId(), s.isSubmitted(), s.getSubmissionDate(), s.getType(), s.isExampleSubmission(), s.getDurationInMinutes(),
                            Optional.ofNullable(s.getResults()).orElse(List.of()).stream().map(RepoUrlResultDTO::of).toList(), s.getCommitHash(), s.isBuildFailed()))
                    .orElse(null);
        }

    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record RepoUrlResultDTO(long id, ZonedDateTime completionDate, Boolean successful, Double score, AssessmentType assessmentType, Boolean rated, Boolean hasComplaint,
            Boolean exampleResult, Integer testCaseCount, Integer passedTestCaseCount, Integer codeIssueCount, List<RepoUrlFeedbackDTO> feedbacks) {

        /**
         * Converts a Result into a dto for the endpoint {@link de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseResource#getProgrammingExerciseByProjectKey(String)}.
         *
         * @param result to convert
         * @return the converted DTO
         */
        public static RepoUrlResultDTO of(Result result) {
            return Optional.ofNullable(result)
                    .map(r -> new RepoUrlResultDTO(r.getId(), r.getCompletionDate(), r.isSuccessful(), r.getScore(), r.getAssessmentType(), r.isRated(), r.hasComplaint(),
                            r.isExampleResult(), r.getTestCaseCount(), r.getPassedTestCaseCount(), r.getCodeIssueCount(),
                            Optional.ofNullable(r.getFeedbacks()).orElse(List.of()).stream().map(RepoUrlFeedbackDTO::of).toList()))
                    .orElse(null);
        }

    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record RepoUrlFeedbackDTO(long id, String text, String detailText, boolean hasLongFeedbackText, String reference, Double credits, FeedbackType type, Boolean positive,
            long testCaseId) {

        /**
         * Converts a Feedback into a dto for the endpoint {@link de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseResource#getProgrammingExerciseByProjectKey(String)}.
         *
         * @param feedback to convert
         * @return the converted DTO
         */
        public static RepoUrlFeedbackDTO of(Feedback feedback) {
            return Optional.ofNullable(feedback).map(f -> new RepoUrlFeedbackDTO(f.getId(), f.getText(), f.getDetailText(), f.getHasLongFeedbackText(), f.getReference(),
                    f.getCredits(), f.getType(), f.isPositive(), f.getTestCaseId())).orElse(null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record RepoUrlProgrammingExerciseDTO(long id, String problemStatement, String title, String shortName, ZonedDateTime releaseDate, ZonedDateTime startDate,
            ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Double maxPoints, Double bonusPoints, AssessmentType assessmentType,
            boolean allowComplaintsForAutomaticAssessments, boolean allowFeedbackRequests, DifficultyLevel difficulty, ExerciseMode mode,
            IncludedInOverallScore includedInOverallScore, ExerciseType exerciseType, ZonedDateTime exampleSolutionPublicationDate, RepoUrlCourseDTO course, String projectKey,
            ProgrammingLanguage programmingLanguage, Boolean showTestNamesToStudents, Set<RepoUrlTestCaseDTO> testCases) {

        /**
         * Converts a ProgrammingExercise into a dto for the endpoint
         * {@link de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseResource#getProgrammingExerciseByProjectKey(String)}.
         *
         * @param exercise to convert
         * @return the converted DTO
         */
        public static RepoUrlProgrammingExerciseDTO of(ProgrammingExercise exercise) {
            return Optional.ofNullable(exercise)
                    .map(e -> new RepoUrlProgrammingExerciseDTO(e.getId(), e.getProblemStatement(), e.getTitle(), e.getShortName(), e.getReleaseDate(), e.getStartDate(),
                            e.getDueDate(), e.getAssessmentDueDate(), e.getMaxPoints(), e.getBonusPoints(), e.getAssessmentType(), e.getAllowComplaintsForAutomaticAssessments(),
                            e.getAllowFeedbackRequests(), e.getDifficulty(), e.getMode(), e.getIncludedInOverallScore(), e.getExerciseType(), e.getExampleSolutionPublicationDate(),
                            RepoUrlCourseDTO.of(e.getCourseViaExerciseGroupOrCourseMember()), e.getProjectKey(), e.getProgrammingLanguage(), e.getShowTestNamesToStudents(),
                            Optional.ofNullable(e.getTestCases()).orElse(Set.of()).stream().map(RepoUrlTestCaseDTO::of).collect(Collectors.toSet())))
                    .orElse(null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record RepoUrlCourseDTO(long id, String title, String shortName) {

        /**
         * Converts a Course into a dto for the endpoint {@link de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseResource#getProgrammingExerciseByProjectKey(String)}.
         *
         * @param course to convert
         * @return the converted DTO
         */
        public static RepoUrlCourseDTO of(Course course) {
            return new RepoUrlCourseDTO(course.getId(), course.getTitle(), course.getShortName());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record RepoUrlTestCaseDTO(long id, String testName, Double weight, double bonusMultiplier, double bonusPoints, Boolean active, Visibility visibility,
            ProgrammingExerciseTestCaseType type) {

        /**
         * Converts a ProgrammingExerciseTestCase into a dto for the endpoint
         * {@link de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseResource#getProgrammingExerciseByProjectKey(String)}.
         *
         * @param testCase to convert
         * @return the converted DTO
         */
        public static RepoUrlTestCaseDTO of(ProgrammingExerciseTestCase testCase) {
            return Optional.ofNullable(testCase).map(t -> new RepoUrlTestCaseDTO(t.getId(), t.getTestName(), t.getWeight(), t.getBonusMultiplier(), t.getBonusPoints(),
                    t.isActive(), t.getVisibility(), t.getType())).orElse(null);
        }
    }
}
