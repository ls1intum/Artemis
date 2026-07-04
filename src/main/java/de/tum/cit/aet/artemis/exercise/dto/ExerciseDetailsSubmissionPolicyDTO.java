package de.tum.cit.aet.artemis.exercise.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;

/**
 * DTO describing a programming exercise submission policy without its exercise back-reference.
 *
 * @param id               the policy identifier
 * @param type             the JSON discriminator ({@code lock_repository} or {@code submission_penalty})
 * @param submissionLimit  the number of submissions allowed before the policy applies, if configured
 * @param active           whether the policy is active, if configured
 * @param exceedingPenalty the penalty per submission exceeding the limit, for penalty policies
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseDetailsSubmissionPolicyDTO(Long id, String type, @Nullable Integer submissionLimit, @Nullable Boolean active, @Nullable Double exceedingPenalty) {

    /**
     * Maps a supported submission policy subtype without exposing its programming exercise.
     *
     * @param policy the initialized submission policy, if present
     * @return the policy DTO, or {@code null} when no policy is present
     */
    public static @Nullable ExerciseDetailsSubmissionPolicyDTO of(@Nullable SubmissionPolicy policy) {
        if (policy == null) {
            return null;
        }
        return switch (policy) {
            case LockRepositoryPolicy lockRepositoryPolicy -> new ExerciseDetailsSubmissionPolicyDTO(lockRepositoryPolicy.getId(), "lock_repository",
                    lockRepositoryPolicy.getSubmissionLimit(), lockRepositoryPolicy.isActive(), null);
            case SubmissionPenaltyPolicy submissionPenaltyPolicy -> new ExerciseDetailsSubmissionPolicyDTO(submissionPenaltyPolicy.getId(), "submission_penalty",
                    submissionPenaltyPolicy.getSubmissionLimit(), submissionPenaltyPolicy.isActive(), submissionPenaltyPolicy.getExceedingPenalty());
            default -> throw new IllegalArgumentException("Unsupported submission policy type: " + policy.getClass().getName());
        };
    }
}
