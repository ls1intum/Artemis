package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.security.policy.AccessPolicy;
import de.tum.cit.aet.artemis.core.security.policy.PolicyEngine;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Service for determining whether a programming exercise is visible to a user.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class ProgrammingExerciseVisibleService {

    private final PolicyEngine policyEngine;

    private final AccessPolicy<ProgrammingExercise> programmingExerciseVisibilityPolicy;

    public ProgrammingExerciseVisibleService(PolicyEngine policyEngine, AccessPolicy<ProgrammingExercise> programmingExerciseVisibilityPolicy) {
        this.policyEngine = policyEngine;
        this.programmingExerciseVisibilityPolicy = programmingExerciseVisibilityPolicy;
    }

    /**
     * Checks if a programming exercise is visible for a user based on their role and the exercise's release date.
     *
     * @param user     the user for whom to check visibility
     * @param exercise the programming exercise to check visibility for
     * @return true if the exercise is visible for the user, false otherwise
     */
    public boolean isVisibleForUser(User user, ProgrammingExercise exercise) {
        return policyEngine.isAllowed(programmingExerciseVisibilityPolicy, user, exercise);
    }
}
