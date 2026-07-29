package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SemanticMutant;

/** Environment result for one semantic-mutant proposal. */
public record SemanticMutantOutcome(SemanticMutant mutant, Disposition disposition) {

    public enum Disposition {
        /** The ordinary graded suite passed the mutant and the independent counterexample distinguished it from the pristine solution. */
        SURVIVED_GRADED_SUITE,
        /** An ordinary graded test executed and failed, and the mutant's own counterexample independently proved that the same proposal violates its claimed rule. */
        KILLED_BY_GRADED_SUITE,
        /** Compilation, timeout, invalid input, or non-discriminating evidence prevented a trustworthy conclusion. */
        INCONCLUSIVE
    }
}
