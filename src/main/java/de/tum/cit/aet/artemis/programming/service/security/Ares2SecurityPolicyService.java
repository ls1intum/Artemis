package de.tum.cit.aet.artemis.programming.service.security;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Boundary to Ares2's policy generation and repository sync. A real implementation would generate the
 * implicit default security policy for the given framework version and commit it (and its enforcement
 * hooks) to the exercise-tests repository, returning the commit hash. {@link MockAres2SecurityPolicyService}
 * stands in until that integration exists.
 */
public interface Ares2SecurityPolicyService {

    /**
     * Generate the implicit default policy for {@code frameworkVersion} and commit it to the exercise-tests repository.
     *
     * @param exercise         the programming exercise whose exercise-tests repository receives the policy
     * @param frameworkVersion the framework version to generate the policy with
     * @return the hash of the commit that synced the policy
     */
    String createAndCommitPolicy(ProgrammingExercise exercise, String frameworkVersion);

    /**
     * Remove the policy and its enforcement hooks from the exercise-tests repository.
     *
     * @param exercise the programming exercise whose exercise-tests repository the policy is removed from
     * @return the hash of the commit that removed the policy
     */
    String removePolicy(ProgrammingExercise exercise);
}
