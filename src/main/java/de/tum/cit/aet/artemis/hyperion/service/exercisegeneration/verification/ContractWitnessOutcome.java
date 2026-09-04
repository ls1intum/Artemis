package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;

/** Environment outcome for one independently authored contract witness. */
public record ContractWitnessOutcome(ContractWitness witness, Disposition disposition, String diagnostic) {

    public enum Disposition {
        REFERENCE_PASSED_STARTER_FAILED, REFERENCE_PASSED_STARTER_NOT_FAILED, REFERENCE_TEST_FAILED, INCONCLUSIVE
    }
}
