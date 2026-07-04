package de.tum.cit.aet.artemis.hyperion.service.variants;

/**
 * Unit tests for the small pure pieces of the variants module (plan Section 10, "Unit tests").
 */
class VariantTypeRegistryTest {

    // TODO (Sonnet): Plain unit tests (no Spring context), per plan Section 10:
    // - VariantTypeRegistry.resolve: returns the matching bundle; unsupported type → BadRequestAlertException;
    // duplicate bundles for one type → startup/duplicate-check failure.
    // - ChangePlan (de)serialization: JSON round-trip incl. BeanOutputConverter schema compatibility; rejection
    // of blank title / empty intendedChanges (see ChangePlan TODOs).
    // - Tool input validation: applyEdit no-match/ambiguous-match errors are returned as tool results, not thrown
    // (Section 6 row 2); updateQuestion schema validation against GeneratedQuizQuestionDTO.
    // - Budget enforcement: VariantAgentLoopRunner stops when token budget exceeded and reports it in AgentResult;
    // pipeline transitions to DRAFT_WITH_WARNINGS when the verify-iteration budget is exhausted (Section 2.5).
}
