package de.tum.cit.aet.artemis.core.security.policy;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;

/**
 * Spring service for evaluating access policies.
 */
@Service
@Profile(PROFILE_CORE)
public class PolicyEngine {

    /**
     * Evaluates whether the given user is allowed access to the resource according to the policy.
     *
     * @param <T>      the resource type
     * @param policy   the access policy to evaluate
     * @param user     the user requesting access
     * @param resource the resource being accessed
     * @return true if the policy allows access, false otherwise
     */
    public <T> boolean isAllowed(AccessPolicy<T> policy, User user, T resource) {
        return policy.evaluate(user, resource) == PolicyEffect.ALLOW;
    }

    /**
     * Evaluates the policy and throws {@link AccessForbiddenException} if access is denied.
     *
     * @param <T>      the resource type
     * @param policy   the access policy to evaluate
     * @param user     the user requesting access
     * @param resource the resource being accessed
     * @throws AccessForbiddenException if the policy denies access
     */
    public <T> void checkAllowedElseThrow(AccessPolicy<T> policy, User user, T resource) {
        if (!isAllowed(policy, user, resource)) {
            throw new AccessForbiddenException("Access denied by policy: " + policy.getName());
        }
    }
}
