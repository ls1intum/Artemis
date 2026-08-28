package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;

/**
 * What deciding which submission a build result belongs to needs, and nothing else.
 * <p>
 * Loading the submissions as entities to compare their commit hashes is expensive out of all proportion to the
 * comparison: a submission eagerly resolves its participation, the participation its exercise, and the exercise its
 * course, so every candidate row carries the problem statement, the grading instructions and the course's code of
 * conduct. A student who pushed ten times made the database ship all of that ten times over so that one commit hash
 * could be matched.
 *
 * @param id             the submission
 * @param type           how the submission came to be, which decides which commit hash of the build result applies
 * @param commitHash     the commit the submission points at
 * @param submissionDate when the submission was made, used to pick the newest match
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingSubmissionCommitHashDTO(long id, @Nullable SubmissionType type, @Nullable String commitHash, @Nullable ZonedDateTime submissionDate) {

    /**
     * Orders candidates exactly the way {@link Submission#compareTo(Submission)} orders submissions, so that picking
     * the newest match off this projection picks the same submission as picking it off the entities did.
     */
    public static final Comparator<ProgrammingSubmissionCommitHashDTO> NEWEST_FIRST = (first, second) -> {
        if (first.submissionDate() == null || second.submissionDate() == null || Objects.equals(first.submissionDate(), second.submissionDate())) {
            // Mirrors the entity: without usable dates the higher id is the later submission.
            return Long.compare(first.id(), second.id());
        }
        return first.submissionDate().compareTo(second.submissionDate());
    };
}
