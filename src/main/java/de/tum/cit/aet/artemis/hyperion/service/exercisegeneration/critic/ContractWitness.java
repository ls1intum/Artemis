package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

/**
 * One executable witness for a rule of the approved specification: a legal input and the result the rule requires, expressed as a single test method.
 * <p>
 * Unlike the oracle review's {@code killed} flag, which is the reviewing model's own assertion and is never run, a witness is executed: one that goes green against the
 * reference solution is evidence that the rule holds and is testable, and can then be handed to the authoring agent as a concrete test to adopt.
 *
 * @param ruleId        the rule this witness pins, spelled exactly as the approved specification writes it
 * @param testName      the test method name, which must match the method declared in {@link #code()} so a validation result can be attributed to this witness
 * @param code          one complete, self-contained test method, including its annotations
 * @param wrongBehavior the plausible contract-breaking behavior the reviewer designed this witness to distinguish; the environment validates the witness, not this hypothesis
 */
public record ContractWitness(String ruleId, String testName, String code, String wrongBehavior) {
}
