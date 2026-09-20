package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * The submission a build result belongs to, and its newest result, as the grading code reads them.
 * <p>
 * Loading the submission as an entity is expensive out of all proportion to what is read off it: a submission eagerly
 * resolves its participation, the participation its exercise, and the exercise its course, so the row carries the
 * exercise's problem statement and the course's code of conduct even though grading looks at a flag and an id.
 *
 * @param submissionId                the submission the result belongs to
 * @param buildFailed                 whether the build of this submission had already been recorded as failed
 * @param commitHash                  the commit the submission points at
 * @param submissionType              how the submission came to be
 * @param submissionDate              when the submission was made
 * @param submitted                   whether the submission was submitted
 * @param exampleSubmission           whether the submission is an example submission
 * @param latestResultId              the newest result of the submission, null when it has none
 * @param latestResultType            the assessment type of that newest result
 * @param latestResultCompletionDate  when that newest result was completed
 * @param latestResultCorrectionRound which correction round that newest result belongs to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildResultSubmissionDTO(long submissionId, boolean buildFailed, @Nullable String commitHash, @Nullable SubmissionType submissionType,
        @Nullable ZonedDateTime submissionDate, @Nullable Boolean submitted, @Nullable Boolean exampleSubmission, @Nullable Long latestResultId,
        @Nullable AssessmentType latestResultType, @Nullable ZonedDateTime latestResultCompletionDate, @Nullable Integer latestResultCorrectionRound) {

    /**
     * Builds the detached submission the grading code works on, carrying its newest result and the participation the
     * caller already loaded.
     * <p>
     * Only the fields that path reads are set. The result of the new build is added to this submission and saved through
     * its own repository, which owns the foreign key, so this object is never passed to a repository itself.
     *
     * @param participation the participation of the submission, already loaded by the caller
     * @return a detached submission holding what grading reads
     */
    public ProgrammingSubmission toDetachedSubmission(Participation participation) {
        var submission = new ProgrammingSubmission();
        submission.setId(submissionId);
        submission.setBuildFailed(buildFailed);
        submission.setCommitHash(commitHash);
        submission.setType(submissionType);
        submission.setSubmissionDate(submissionDate);
        submission.setSubmitted(submitted);
        submission.setExampleSubmission(exampleSubmission);
        submission.setParticipation(participation);
        if (latestResultId != null) {
            var latestResult = new Result();
            latestResult.setId(latestResultId);
            latestResult.setAssessmentType(latestResultType);
            latestResult.setCompletionDate(latestResultCompletionDate);
            latestResult.setCorrectionRound(latestResultCorrectionRound);
            submission.addResult(latestResult);
        }
        return submission;
    }
}
