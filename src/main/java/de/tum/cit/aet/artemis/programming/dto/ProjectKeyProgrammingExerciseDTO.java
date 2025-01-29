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
public record ProjectKeyProgrammingExerciseDTO(Long id, String problemStatement, String title, String shortName, ZonedDateTime releaseDate, ZonedDateTime startDate,
        ZonedDateTime dueDate, ZonedDateTime assessmentDueDate, Double maxPoints, Double bonusPoints, AssessmentType assessmentType, Boolean allowComplaintsForAutomaticAssessments,
        Boolean allowFeedbackRequests, DifficultyLevel difficulty, ExerciseMode mode, IncludedInOverallScore includedInOverallScore, ExerciseType exerciseType,
        ZonedDateTime exampleSolutionPublicationDate, Set<ProjectKeyStudentParticipationDTO> studentParticipations, String projectKey, ProgrammingLanguage programmingLanguage,
        Boolean showTestNamesToStudents) {

    public static ProjectKeyProgrammingExerciseDTO of(ProgrammingExercise exercise) {
        return Optional.ofNullable(exercise)
                .map(e -> new ProjectKeyProgrammingExerciseDTO(e.getId(), e.getProblemStatement(), e.getTitle(), e.getShortName(), e.getReleaseDate(), e.getStartDate(),
                        e.getDueDate(), e.getAssessmentDueDate(), e.getMaxPoints(), e.getBonusPoints(), e.getAssessmentType(), e.getAllowComplaintsForAutomaticAssessments(),
                        e.getAllowFeedbackRequests(), e.getDifficulty(), e.getMode(), e.getIncludedInOverallScore(), e.getExerciseType(), e.getExampleSolutionPublicationDate(),
                        e.getStudentParticipations() == null ? null
                                : e.getStudentParticipations().stream().map(p -> ProjectKeyStudentParticipationDTO.of((ProgrammingExerciseStudentParticipation) p))
                                        .collect(Collectors.toSet()),
                        e.getProjectKey(), e.getProgrammingLanguage(), e.getShowTestNamesToStudents()))
                .orElse(null);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProjectKeyStudentParticipationDTO(Long id, ZonedDateTime individualDueDate, Set<ProjectKeySubmissionDTO> submissions, String participantName,
            String participantIdentifier, String repositoryUri, String buildPlanId, String branch) {

        public static ProjectKeyStudentParticipationDTO of(ProgrammingExerciseStudentParticipation participation) {
            return Optional.ofNullable(participation)
                    .map(p -> new ProjectKeyStudentParticipationDTO(p.getId(), p.getIndividualDueDate(),
                            p.getSubmissions() == null ? null
                                    : p.getSubmissions().stream().map(s -> ProjectKeySubmissionDTO.of((ProgrammingSubmission) s)).collect(Collectors.toSet()),
                            p.getParticipantName(), p.getParticipantIdentifier(), p.getRepositoryUri(), p.getBuildPlanId(), p.getBranch()))
                    .orElse(null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProjectKeySubmissionDTO(Long id, Boolean submitted, ZonedDateTime submissionDate, SubmissionType type, Boolean exampleSubmission, Long durationInMinutes,
            List<ProjectKeyResultDTO> results, String commitHash, Boolean buildFailed) {

        public static ProjectKeySubmissionDTO of(ProgrammingSubmission submission) {
            return Optional.ofNullable(submission)
                    .map(s -> new ProjectKeySubmissionDTO(s.getId(), s.isSubmitted(), s.getSubmissionDate(), s.getType(), s.isExampleSubmission(), s.getDurationInMinutes(),
                            s.getResults() == null ? null : s.getResults().stream().map(ProjectKeyResultDTO::of).collect(Collectors.toList()), s.getCommitHash(),
                            s.isBuildFailed()))
                    .orElse(null);
        }

    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProjectKeyResultDTO(Long id, ZonedDateTime completionDate, Boolean successful, Double score, AssessmentType assessmentType, Boolean rated, Boolean hasComplaint,
            Boolean exampleResult, Integer testCaseCount, Integer passedTestCaseCount, Integer codeIssueCount, List<ProjectKeyFeedbackDTO> feedbacks) {

        public static ProjectKeyResultDTO of(Result result) {
            return Optional.ofNullable(result)
                    .map(r -> new ProjectKeyResultDTO(r.getId(), r.getCompletionDate(), r.isSuccessful(), r.getScore(), r.getAssessmentType(), r.isRated(), r.hasComplaint(),
                            r.isExampleResult(), r.getTestCaseCount(), r.getPassedTestCaseCount(), r.getCodeIssueCount(),
                            r.getFeedbacks() == null ? null : r.getFeedbacks().stream().map(ProjectKeyFeedbackDTO::of).collect(Collectors.toList())))
                    .orElse(null);
        }

    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProjectKeyFeedbackDTO(Long id, String text, String detailText, Boolean hasLongFeedbackText, String reference, Double credits, FeedbackType type, Boolean positive,
            ProjectKeyTestCaseDTO testCase) {

        public static ProjectKeyFeedbackDTO of(Feedback feedback) {
            return Optional.ofNullable(feedback).map(f -> new ProjectKeyFeedbackDTO(f.getId(), f.getText(), f.getDetailText(), f.getHasLongFeedbackText(), f.getReference(),
                    f.getCredits(), f.getType(), f.isPositive(), ProjectKeyTestCaseDTO.of(f.getTestCase()))).orElse(null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ProjectKeyTestCaseDTO(Long id, String testName, Double weight, Double bonusMultiplier, Double bonusPoints, Boolean active, Visibility visibility,
            ProgrammingExerciseTestCaseType type) {

        public static ProjectKeyTestCaseDTO of(ProgrammingExerciseTestCase testCase) {
            return Optional.ofNullable(testCase).map(t -> new ProjectKeyTestCaseDTO(t.getId(), t.getTestName(), t.getWeight(), t.getBonusMultiplier(), t.getBonusPoints(),
                    t.isActive(), t.getVisibility(), t.getType())).orElse(null);
        }
    }
}
