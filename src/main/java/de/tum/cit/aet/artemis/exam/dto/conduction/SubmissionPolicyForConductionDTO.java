package de.tum.cit.aet.artemis.exam.dto.conduction;

import org.hibernate.Hibernate;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;

/**
 * The active submission policy of an exam programming exercise, as the exam-taking client reads it.
 * <p>
 * {@code StudentExamResource.prepareStudentExamForConduction} attaches the policy to every programming exercise of the
 * student exam, and {@code ProgrammingSubmissionPolicyStatusComponent} renders the student's remaining submission
 * allowance from {@code active}, {@code submissionLimit}, {@code type} and (for a penalty policy)
 * {@code exceedingPenalty}. Without these the backend still enforces the lock / penalty while the student cannot see
 * the allowance, so the fields must stay on the wire.
 * <p>
 * {@code type} reproduces the entity's {@code @JsonTypeInfo} discriminator ({@code lock_repository} /
 * {@code submission_penalty}) that the client's {@code SubmissionPolicyType} switches on.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionPolicyForConductionDTO(Long id, String type, Boolean active, Integer submissionLimit, @Nullable Double exceedingPenalty) {

    /**
     * Extracts the client-read fields of a submission policy.
     *
     * @param submissionPolicy the policy to convert, may be null or an uninitialized lazy proxy
     * @return the converted DTO, or null if there is no (initialized) policy
     */
    @Nullable
    public static SubmissionPolicyForConductionDTO of(@Nullable SubmissionPolicy submissionPolicy) {
        // the association is mapped LAZY, so an exercise that never had a policy attached can still hold an
        // uninitialized proxy here; touching it outside a session would throw.
        if (submissionPolicy == null || !Hibernate.isInitialized(submissionPolicy)) {
            return null;
        }
        // Pattern matching on the concrete policy type is safe here: SubmissionPolicy is annotated @ConcreteProxy, so
        // Hibernate gives the real subclass rather than a generated proxy of the base class. Without that annotation a
        // proxy would match neither case and the student would lose the policy display. The guard test
        // testSubmissionPolicyProjectionResolvesConcreteTypeThroughHibernateProxy loads the policy as an initialized
        // proxy and fails if the annotation is ever removed.
        String type = switch (submissionPolicy) {
            case LockRepositoryPolicy ignored -> "lock_repository";
            case SubmissionPenaltyPolicy ignored -> "submission_penalty";
            default -> null;
        };
        Double exceedingPenalty = submissionPolicy instanceof SubmissionPenaltyPolicy penaltyPolicy ? penaltyPolicy.getExceedingPenalty() : null;
        return new SubmissionPolicyForConductionDTO(submissionPolicy.getId(), type, submissionPolicy.isActive(), submissionPolicy.getSubmissionLimit(), exceedingPenalty);
    }
}
