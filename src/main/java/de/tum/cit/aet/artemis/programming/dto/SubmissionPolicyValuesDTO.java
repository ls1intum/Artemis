package de.tum.cit.aet.artemis.programming.dto;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;

/**
 * The values of a {@link SubmissionPolicy}, without the exercise it belongs to.
 * <p>
 * A submission policy holds an inverse one-to-one back to its programming exercise, and that association is eager, so
 * loading a policy as an entity loads the whole exercise with it and the course the exercise eagerly brings along. On
 * the path that processes a build result, that means the exercise's problem statement and the course's code of conduct
 * travel over the wire again for the sake of two numbers and a flag.
 *
 * @param id               the policy
 * @param type             which kind of policy it is, so the caller can rebuild the right one
 * @param submissionLimit  how many submissions the policy allows
 * @param active           whether the policy is enforced
 * @param exceedingPenalty the points deducted per submission over the limit, only set for a penalty policy
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionPolicyValuesDTO(long id, @Nullable String type, @Nullable Integer submissionLimit, @Nullable Boolean active, @Nullable Double exceedingPenalty) {

    private static final Logger log = LoggerFactory.getLogger(SubmissionPolicyValuesDTO.class);

    /** Kept in sync with the concrete subclasses of {@link SubmissionPolicy} by SubmissionPolicyValuesDTOTest. */
    public static final String LOCK_REPOSITORY = "LOCK_REPOSITORY";

    public static final String SUBMISSION_PENALTY = "SUBMISSION_PENALTY";

    /**
     * Rebuilds the policy these values came from, so that the grading code can keep asking the exercise for its policy
     * and matching on its type.
     * <p>
     * The result carries only the fields grading reads and is never passed to a repository. It is detached on purpose:
     * the exercise it would point back at is exactly what this projection exists to avoid loading.
     *
     * @return the policy, or null if its type is not one this version knows about
     */
    @Nullable
    public SubmissionPolicy toDetachedPolicy() {
        SubmissionPolicy policy = switch (type) {
            case LOCK_REPOSITORY -> new LockRepositoryPolicy();
            case SUBMISSION_PENALTY -> {
                var penaltyPolicy = new SubmissionPenaltyPolicy();
                penaltyPolicy.setExceedingPenalty(exceedingPenalty);
                yield penaltyPolicy;
            }
            case null, default -> null;
        };
        if (policy == null) {
            // Not a hard failure: an unknown policy is treated as no policy, which is what happened before a policy of
            // that kind existed. The test named above is what stops a new subclass from arriving here unnoticed.
            log.error("Unknown submission policy type '{}' for policy {}; grading continues without applying it", type, id);
            return null;
        }
        policy.setId(id);
        policy.setSubmissionLimit(submissionLimit);
        policy.setActive(active);
        return policy;
    }
}
