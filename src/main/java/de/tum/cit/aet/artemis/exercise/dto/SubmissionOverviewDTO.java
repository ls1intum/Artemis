package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * A submission as the course overview renders it, carrying only its results.
 *
 * @param id                     the id of the submission
 * @param submissionDate         when it was submitted, used to pick the latest submission
 * @param submitted              whether it was actually submitted
 * @param type                   how the submission came about
 * @param submissionExerciseType the concrete submission kind; the client discriminates on this, so a projection has to
 *                                   carry it explicitly rather than rely on Jackson's type information
 * @param results                the results of this submission
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionOverviewDTO(Long id, ZonedDateTime submissionDate, Boolean submitted, SubmissionType type, String submissionExerciseType, Boolean buildFailed,
        List<ResultOverviewDTO> results) {

    /**
     * Projects a submission and its results for the course overview.
     *
     * @param submission the submission to project
     * @return the projected submission
     */
    public static SubmissionOverviewDTO of(Submission submission) {
        List<ResultOverviewDTO> results = submission.getResults() == null ? List.of()
                : submission.getResults().stream().filter(java.util.Objects::nonNull).map(ResultOverviewDTO::of).toList();
        Boolean buildFailed = submission instanceof ProgrammingSubmission programmingSubmission ? programmingSubmission.isBuildFailed() : null;
        return new SubmissionOverviewDTO(submission.getId(), submission.getSubmissionDate(), submission.isSubmitted(), submission.getType(), submission.getSubmissionExerciseType(),
                buildFailed, results);
    }

    /**
     * Projects a set of submissions for the course overview.
     *
     * @param submissions the submissions to project, may be null
     * @return the projected submissions
     */
    public static Set<SubmissionOverviewDTO> of(Set<Submission> submissions) {
        return submissions == null ? Set.of() : submissions.stream().map(SubmissionOverviewDTO::of).collect(java.util.stream.Collectors.toSet());
    }
}
