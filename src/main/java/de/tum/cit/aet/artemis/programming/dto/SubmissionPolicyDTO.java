package de.tum.cit.aet.artemis.programming.dto;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;

/**
 * Flat, wire-compatible representation of a {@link SubmissionPolicy}.
 * <p>
 * The entity hierarchy is polymorphic via Jackson's {@code @JsonTypeInfo(property = "type")} with the subtype ids
 * {@code lock_repository} and {@code submission_penalty}. Records get no such discriminator for free, so {@code type}
 * is an explicit component carrying exactly those two literals — the client compares them literally
 * ({@code submission-policy.model.ts}) and the update form rebuilds its request body from
 * {@code (id, active, submissionLimit, exceedingPenalty, type)}.
 * <p>
 * The annotation is {@code NON_EMPTY} even though this record also serves as a request body: {@code exceedingPenalty}
 * exists only on {@link SubmissionPenaltyPolicy}, so under {@code ALWAYS} a lock policy would emit an
 * {@code "exceedingPenalty": null} key that today's wire never contains. {@code NON_EMPTY} does not drop
 * {@code active = false} (a {@code Boolean} is never "empty") and the record has no collections, so the
 * request-side empty-collection trap does not apply here. Inbound parsing is unaffected by {@code @JsonInclude}.
 *
 * @param id               the policy id; carried through {@link #toEntity()} so an update keeps the existing row
 * @param type             {@link #TYPE_LOCK_REPOSITORY} or {@link #TYPE_SUBMISSION_PENALTY}
 * @param submissionLimit  the number of allowed submissions (declared on the base class, present on both types)
 * @param exceedingPenalty the penalty per exceeding submission; only ever set for a submission-penalty policy
 * @param active           whether the policy is active
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionPolicyDTO(@Nullable Long id, String type, Integer submissionLimit, @Nullable Double exceedingPenalty, Boolean active) {

    /**
     * Jackson subtype id of {@link LockRepositoryPolicy}.
     */
    public static final String TYPE_LOCK_REPOSITORY = "lock_repository";

    /**
     * Jackson subtype id of {@link SubmissionPenaltyPolicy}.
     */
    public static final String TYPE_SUBMISSION_PENALTY = "submission_penalty";

    /**
     * Converts a {@link SubmissionPolicy} entity into its flat DTO representation. A Hibernate proxy is unwrapped
     * first so the subtype check sees the concrete policy class.
     *
     * @param policy the policy to convert (may be {@code null})
     * @return the converted DTO, or {@code null} if the input was {@code null}
     */
    public static SubmissionPolicyDTO of(SubmissionPolicy policy) {
        if (policy == null) {
            return null;
        }
        SubmissionPolicy concretePolicy = Hibernate.unproxy(policy, SubmissionPolicy.class);
        String type = null;
        Double exceedingPenalty = null;
        if (concretePolicy instanceof SubmissionPenaltyPolicy penaltyPolicy) {
            type = TYPE_SUBMISSION_PENALTY;
            exceedingPenalty = penaltyPolicy.getExceedingPenalty();
        }
        else if (concretePolicy instanceof LockRepositoryPolicy) {
            type = TYPE_LOCK_REPOSITORY;
        }
        return new SubmissionPolicyDTO(concretePolicy.getId(), type, concretePolicy.getSubmissionLimit(), exceedingPenalty, concretePolicy.isActive());
    }

    /**
     * Builds the matching {@link SubmissionPolicy} subclass from this DTO. The id is copied through so that a policy
     * loaded from the database and sent back keeps its identity — an id-less transient policy would insert a second
     * {@code submission_policy} row on the update path. The back-reference to the programming exercise is never set.
     *
     * @return the policy entity described by this DTO
     */
    public SubmissionPolicy toEntity() {
        SubmissionPolicy policy = switch (type) {
            case TYPE_LOCK_REPOSITORY -> new LockRepositoryPolicy();
            case TYPE_SUBMISSION_PENALTY -> {
                SubmissionPenaltyPolicy penaltyPolicy = new SubmissionPenaltyPolicy();
                penaltyPolicy.setExceedingPenalty(exceedingPenalty);
                yield penaltyPolicy;
            }
            case null, default ->
                throw new BadRequestAlertException("Unknown submission policy type: " + type, "programmingExercise.submissionPolicy", "invalidSubmissionPolicyType");
        };
        policy.setId(id);
        policy.setSubmissionLimit(submissionLimit);
        policy.setActive(active);
        return policy;
    }
}
