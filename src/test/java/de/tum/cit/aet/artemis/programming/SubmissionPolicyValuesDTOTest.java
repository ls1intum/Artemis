package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonSubTypes;

import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.dto.SubmissionPolicyValuesDTO;

class SubmissionPolicyValuesDTOTest {

    @Test
    void shouldRebuildALockRepositoryPolicy() {
        var values = new SubmissionPolicyValuesDTO(7L, SubmissionPolicyValuesDTO.LOCK_REPOSITORY, 3, true, null);

        var policy = values.toDetachedPolicy();

        assertThat(policy).isInstanceOf(LockRepositoryPolicy.class);
        assertThat(policy.getId()).isEqualTo(7L);
        assertThat(policy.getSubmissionLimit()).isEqualTo(3);
        assertThat(policy.isActive()).isTrue();
    }

    @Test
    void shouldRebuildASubmissionPenaltyPolicyIncludingItsPenalty() {
        var values = new SubmissionPolicyValuesDTO(9L, SubmissionPolicyValuesDTO.SUBMISSION_PENALTY, 2, false, 1.5);

        var policy = values.toDetachedPolicy();

        assertThat(policy).isInstanceOf(SubmissionPenaltyPolicy.class);
        assertThat(((SubmissionPenaltyPolicy) policy).getExceedingPenalty()).isEqualTo(1.5);
        assertThat(policy.getSubmissionLimit()).isEqualTo(2);
        assertThat(policy.isActive()).isFalse();
    }

    @Test
    void shouldTreatAnUnknownTypeAsNoPolicy() {
        assertThat(new SubmissionPolicyValuesDTO(1L, "UNKNOWN", 1, true, null).toDetachedPolicy()).isNull();
        assertThat(new SubmissionPolicyValuesDTO(1L, null, 1, true, null).toDetachedPolicy()).isNull();
    }

    /**
     * The projection query maps each concrete policy type to a name and the factory turns it back into a policy, so a
     * new subclass has to be added in both places. {@link SubmissionPolicy} already has to list its subtypes for the
     * REST API, which makes that list the natural thing to check against: if a subtype is added there and nowhere else,
     * grading would silently stop applying it.
     */
    @Test
    void shouldHandleEveryKnownPolicyType() {
        var declaredSubtypes = Arrays.stream(SubmissionPolicy.class.getAnnotation(JsonSubTypes.class).value()).map(JsonSubTypes.Type::value).toList();

        assertThat(declaredSubtypes).containsExactlyInAnyOrder(LockRepositoryPolicy.class, SubmissionPenaltyPolicy.class);
        assertThat(new SubmissionPolicyValuesDTO(1L, SubmissionPolicyValuesDTO.LOCK_REPOSITORY, 1, true, null).toDetachedPolicy()).isNotNull();
        assertThat(new SubmissionPolicyValuesDTO(1L, SubmissionPolicyValuesDTO.SUBMISSION_PENALTY, 1, true, 1.0).toDetachedPolicy()).isNotNull();
    }
}
