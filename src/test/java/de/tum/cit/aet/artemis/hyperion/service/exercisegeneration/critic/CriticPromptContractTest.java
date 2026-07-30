package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;

/**
 * The audited clause contract of the reviewer system prompts.
 * <p>
 * Every reviewer pass renders its system prompt from a placeholder-free {@code .st} resource with {@code Map.of()} (see {@code ReviewerClient#call}), so the prompt text is a
 * static resource load rather than behaviour. Each clause below was added to answer a specific review defect, and deleting one silently regresses that defect while every Java
 * test still passes — so the clauses are pinned here, against the rendered resource, instead of through a mocked provider and a captured {@code Prompt}.
 * <p>
 * That the correct template reaches the correct pass is a separate, genuinely behavioural claim and stays in {@code SpecFidelityCriticServiceTest} with one sentinel clause per
 * pass.
 */
class CriticPromptContractTest {

    private static final String CONCEPT_REVIEW = "/prompts/hyperion/critic/concept_review_system.st";

    private static final String CONCEPT_ADMISSION = "/prompts/hyperion/critic/concept_admission_system.st";

    private static final String SPECIFICATION_REVIEW = "/prompts/hyperion/critic/specification_review_system.st";

    private static final String CONTRACT_REVIEW = "/prompts/hyperion/critic/contract_review_system.st";

    private static final String ORACLE_REVIEW = "/prompts/hyperion/critic/oracle_review_system.st";

    private static final String SEMANTIC_MUTANT = "/prompts/hyperion/critic/semantic_mutant_system.st";

    private static final String CONTRACT_WITNESS = "/prompts/hyperion/critic/contract_witness_system.st";

    private final HyperionPromptTemplateService templateService = new HyperionPromptTemplateService();

    /**
     * Clauses each reviewer prompt must state. A missing clause means the audit fix that introduced it has been edited away.
     */
    private static Stream<Arguments> requiredClauses() {
        Stream<Arguments> reviewClauses = Stream.of(
                // Concept selection: evaluate the generator's candidates on properties, never author a replacement design.
                rows(CONCEPT_REVIEW, "fully specified", "can still be intermediate", "never invent or propose a replacement theme", "Evaluate EACH candidate independently",
                        "Constants, labels, or thresholds", "Reconstruct the smallest plausible student implementation", "Lookup-table", "transcription, uniform scaling",
                        "same responsibility for overlapping valid", "premature exact constraints", "requested objective", "same substantial general-purpose algorithm",
                        "all consequential behavior in a given context", "not a numeric quota", "behaviorally identical control flow", "switch—the same",
                        "any finite Strategy design", "strongest direct learning-objective fit", "use simplicity only", "break ties among candidates",
                        "difficultySufficient may be true only", "lowest extraneous cognitive load", "Do not invent control flow, conditionals, data transformations, or decisions",
                        "`distinct rules`", "`computes a result`", "Do not subtract learner-owned reasoning intrinsic", "one-to-one tag", "not meaningful interchangeability",
                        "Do not require a separate mathematical", "State the caller-requested operation before and after substitution", "semantically different operations",
                        "Student-owned objective` is the exhaustive ownership claim", "Do not infer that students implement a policy",
                        // The prompt asks for the concrete analysis prose and boolean shape its parser demands, so a compliant response is never discarded as malformed.
                        "concise brief-coverage analysis", "concise grounding analysis", "concise feasibility analysis", "same caller goal",
                        "\"learnerOwnsObjectiveMechanism\":false", "\"prematureContractClosure\":true"),

                // Selected-concept admission: independently reject invented, unobservable, or behaviorally redundant constraints before they become provenance.
                rows(CONCEPT_ADMISSION, "instructor brief is the sole authority", "admission reviewer, not a designer", "public-API-equivalent implementations",
                        "normalize the candidate's claimed cases into public traces", "smallest behaviorally equivalent implementation independently",
                        "unsupportedChoices, unobservableRequirements, and redundantDistinctions are all empty", "\"admissible\":false",
                        "expects a concept to instantiate a qualitative domain", "Do not flag those choices merely because the brief left them open",
                        "may describe one viable implementation", "does not constrain all", "normative field", "not redundant concept distinctions"),

                // Specification review: the pre-freeze brief-to-spec audit.
                rows(SPECIFICATION_REVIEW, "Design ownership table", "template supplies a type marked `student-creates`", "correct table does not cancel contradictory prose",
                        "does not assign ownership of the strategy interface", "Non-student-visible harness notes are not observable constraints",
                        "Package, source-root, and class-visibility choices required by the seeded build", "explicitly requested difficulty", "one-operation formula transcription",
                        "Testing Strategy's Observable responsibility", "defect detection, not design optimization", "try to", "whole specification",
                        "common teaching examples for the requested programming concept", "domain is familiar in popular culture", "theme identity from theme integration",
                        "arbitrary formula", "constants under themed names", "itself wiring the collaboration", "Empty defect arrays are not sufficient evidence", "learningFit",
                        "subtractive", "erasing the domain nouns", "remainingStudentReasoning", "domainGrounding", "objectiveMechanism", "objectiveEvidenceIds",
                        "Do not invent a plausible post-hoc domain rationale", "Do not invent validation", "Do not subtract learner-owned reasoning intrinsic",
                        "overlapping valid inputs", "handler dispatch", "Incidental arithmetic", "cannot rescue a hollow pattern exercise",
                        "Do not require a separate domain algorithm", "ALIGNED", "SPEC_REPAIR", "CONCEPT_RESELECTION", "TOO_SHALLOW", "TOO_COMPLEX", "MISALIGNED", "factor",
                        "shared work", "causally", "not unsupported merely because", "exampleChecks", "intentionally not an exhaustive executable contract",
                        "independent specification evidence", "obligation cannot justify itself", "generic best practice",
                        "A Java reference type does not by itself make `null` a permitted educational input", "representative interaction", "context or client holds or selects",
                        "implementations only in isolation", "Student-owned reasoning", "collapses that explicit mechanism to labels, constants, or scalar formulas",
                        "inclusive rule range", "concrete incompatibility witness", "Cite every rule", "complete pass", "do not stop after the first defect", "Return at most four",
                        "blocking findings TOTAL", "Diagnose properties only", "never supply replacement names, domains, formulas, or APIs",
                        "numeric partitions for gaps and overlaps", "concrete admitted witness", "explicitly narrowed input domain", "unrelated operation advances",
                        "object that owns", "trace a legal setup", "shifts that outcome to an earlier operation", "promised later outcome unreachable",
                        "one boundaryChecks item for every explicit boundary", "\"timingPreserved\":true", "Audit every Decision Ledger row", "`EXPLICIT_BRIEF` is valid only",
                        "process history, not instructor provenance", "`NECESSARY_OPERATIONAL_CHOICE`", "false provenance label is a blocking unsupported-constraint finding"),

                // Contract review: the student-contract half of the full-artifact review.
                rows(CONTRACT_REVIEW, "house teaching scaffold", "restate its student-visible contract", "imperative TODO", "stubbed owner", "solution/template diff",
                        "compact API surface", "PlantUML diagram", "the template is the API reference at the point of use",
                        "`student-creates` types must be described as required and", "`zero` and `non-positive`", "Reject student-facing references to SPEC.md",
                        "Inheritance and realization arrows require corresponding", "association or dependency", "Return every failed check", "one representative passing check",
                        "mandatory and unambiguous", "Do not infer task reachability", "Do not invent requirements from solution-only behavior", "claims alternatives",
                        "Extra defensive behavior in the reference solution is not an invented student requirement", "one operation or the whole call", "trace each visible test",
                        "from setup", "to assertion", "another independently actionable", "student seam",
                        "At most 6 exampleChecks, 8 apiChecks, 6 templateChecks, and 4 items in every other array", "replay every worked-example outcome",
                        "unrequested and missing requested changes", "executable setup", "Distinguish observable guarantees from pedagogical objectives",
                        "actual reference-solution control flow", "every distinct deterministic example claim", "object that owns the state"),

                // Oracle review: the executable-test-oracle half of the full-artifact review.
                rows(ORACLE_REVIEW, "at most six highest-risk representative mutants", "explicit boundary quantifier", "at the boundary", "immediately adjacent values",
                        "only the few highest-leverage findings that have distinct repairs", "must not emit uncovered", "Design owner is marked `given`", "test-controlled fake",
                        "sentinel", "calling a production collaborator twice", "hardcoded-example mutant", "distinct representative input", "contract-breaking mutants",
                        "executable setup", "Do not invent requirements from solution-only behavior", "APPROVED SPECIFICATION CONTRACT is binding authority",
                        "input permitted by the declared contract", "student-facing API", "reflection", "private-state mutation", "bypassing a constructor precondition",
                        "mathematically redundant transformations", "states that the declared types make impossible", "truncating or casting floating-point input",
                        "overflowing integer subtraction/absolute-value arithmetic", "execution phase decide", "natural implementation neighborhood", "smallest plausible change",
                        "natural input region or an existing lifecycle", "not from code written to single out", "ordinary arithmetic or representation already used",
                        "otherwise stateless mapping", "Omit the finding rather than inventing an unnatural mutant",
                        "The produced statement is evidence to compare against those primary sources, not authority",
                        "If the primary source requirements do not require every behavior needed to distinguish the proposed wrong implementation"))
                .flatMap(rows -> rows);
        return Stream.concat(reviewClauses,
                Stream.concat(
                        rows(SEMANTIC_MUTANT, "natural implementation neighborhood", "smallest, locally plausible mistake", "across a region of inputs or an existing lifecycle",
                                "not from code written to single out", "ordinary arithmetic or representation already used", "otherwise stateless mapping",
                                "Source-backed hypotheses do not override", "return fewer mutants rather than inventing novelty"),
                        rows(CONTRACT_WITNESS, "compilation style as the graded tests", "wrapper that copies imports", "compile against the student starter",
                                "reflection/dynamic-proxy pattern", "never executes and is discarded")));
    }

    /**
     * Clauses each reviewer prompt must not state. Two kinds: phrasings an earlier prompt revision was corrected for, and the sibling pass's evidence vocabulary — the two
     * full-artifact passes stay specialized only as long as neither prompt names the other's arrays.
     */
    private static Stream<Arguments> forbiddenClauses() {
        return Stream.of(
                // Concrete themes and analysis placeholders the prompt must not put in the reviewer's mouth.
                rows(CONCEPT_REVIEW, "spacecraft docking", "artifact restoration", "Strategy learning", "\"briefCoverage\":\"analysis\"", "\"domainGrounding\":\"analysis\"",
                        "\"feasibility\":\"analysis\""),
                rows(CONCEPT_ADMISSION, "spacecraft docking", "artifact restoration", "Strategy learning"),
                rows(SPECIFICATION_REVIEW, "compression, payment, sorting, and navigation"),
                // The contract pass must not be handed the oracle pass's vocabulary, and it must not excuse a template failure as intended.
                rows(CONTRACT_REVIEW, "mutantChecks", "weakOracle", "uncovered", "first failure is the intended placeholder", "breadcrumb for a type the student must still create",
                        "derived contract", "source requirements and produced statement do not require"),
                // The oracle pass must not be handed the contract pass's vocabulary, nor asked for exhaustive per-rule enumeration.
                rows(ORACLE_REVIEW, "exampleChecks", "apiChecks", "templateChecks", "contradictions", "For every explicit rule and public operation", "derived contract",
                        "source requirements and produced statement do not require"))
                .flatMap(rows -> rows);
    }

    private static Stream<Arguments> rows(String template, String... clauses) {
        return Stream.of(clauses).map(clause -> Arguments.of(template, clause));
    }

    @ParameterizedTest(name = "{0} states \"{1}\"")
    @MethodSource("requiredClauses")
    void reviewerPromptStatesItsAuditedClause(String template, String clause) {
        assertThat(render(template).contains(clause)).as("%s no longer states the audited clause \"%s\"", template, clause).isTrue();
    }

    @ParameterizedTest(name = "{0} omits \"{1}\"")
    @MethodSource("forbiddenClauses")
    void reviewerPromptOmitsItsRetractedClause(String template, String clause) {
        assertThat(render(template).contains(clause)).as("%s states the retracted clause \"%s\"", template, clause).isFalse();
    }

    /** Mirrors how {@code ReviewerClient} loads a reviewer system prompt: the templates carry no placeholders, so rendering is a plain classpath load. */
    private String render(String template) {
        return templateService.render(template, Map.of());
    }
}
