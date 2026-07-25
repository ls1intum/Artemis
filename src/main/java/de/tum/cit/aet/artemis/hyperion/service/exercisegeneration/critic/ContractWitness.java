package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

/**
 * One executable witness for a rule of the approved specification: a legal input and the result the rule requires, expressed as a single test method.
 * <p>
 * A witness exists to turn a claim into evidence. The oracle review already reasons about "plausible wrong implementations" and reports whether the graded suite kills them, but
 * {@code killed} is the reviewing model's own assertion and is never executed — an exercise observed live passed four consecutive review rounds while three implementations that
 * violate rules its specification states still scored full marks. A witness is executed instead: one that runs green against the reference solution is validated evidence that
 * the rule holds and is testable, and can then be handed to the authoring agent as a concrete test to adopt.
 *
 * @param ruleId   the rule of the approved specification this witness pins, exactly as that document writes it (for example {@code R1})
 * @param testName the test method name, which must match the method declared in {@link #code()} so a validation result can be attributed to this witness
 * @param code     one complete, self-contained test method, including its annotations
 */
public record ContractWitness(String ruleId, String testName, String code) {
}
