package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.MAX_REVIEW_FINDINGS;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.extractJsonPayload;
import static de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ReviewGuardrails.truncate;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Turns one full-artifact reviewer response into findings, or into no verdict at all.
 * <p>
 * The parser is where the critic decides what it is willing to believe. A verdict is discarded outright when its mandatory checks are missing or malformed, because a reviewer that
 * skipped the checks proves nothing; and an individual claim is dropped ("abstained") unless its {@code sourceQuote} literally appears in the grounding source for that finding
 * category. Both rules exist so a hallucinated blocker can never drive the repair loop or reach the instructor.
 */
class CriticVerdictParser {

    /** Which specialized review pass produced the response; the two passes answer different mandatory arrays. */
    enum ReviewPass {
        CONTRACT, ORACLE
    }

    static final String UNGROUNDED_ORACLE_REVIEW_DETAIL = "The test-oracle reviewer cited at least one requirement that was not present in the primary source. Grounded findings were retained for repair, but the candidate still requires a complete review.";

    private static final Logger log = LoggerFactory.getLogger(CriticVerdictParser.class);

    private static final Pattern NULL_WORD = Pattern.compile("\\bnull\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern MUTABILITY_WORD = Pattern.compile("\\b(?:immutable|immutability|mutable|mutability|unmodifiable)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern QUOTED_CONTRACT_TERM = Pattern.compile("[\"`]([^\"`]{3,})[\"`]");

    private static final Pattern EXCEPTION_TYPE = Pattern.compile("\\b[A-Z][A-Za-z0-9]*(?:Exception|Error)\\b");

    private static final List<Pattern> EXPLICIT_SOURCE_TERMS = List.of(NULL_WORD, MUTABILITY_WORD);

    /** The structured shape the full-artifact review parses the model JSON into. */
    private record CriticResponse(@Nullable List<ExampleCheckItem> exampleChecks, @Nullable List<ApiCheckItem> apiChecks, @Nullable List<TemplateCheckItem> templateChecks,
            @Nullable List<MutantCheckItem> mutantChecks, @Nullable List<RequirementFindingItem> uncovered, @Nullable List<RequirementFindingItem> contradictions,
            @Nullable List<RequirementFindingItem> hiddenRequirements, @Nullable List<RequirementFindingItem> weakOracle, @Nullable List<RequirementFindingItem> templateGaps,
            @Nullable List<ExampleGapItem> missingExamples, @Nullable List<RequirementFindingItem> invented, @Nullable List<AdaptationChangeItem> unrequestedChanges,
            @Nullable List<RequirementFindingItem> missingRequestedChanges) {
    }

    private record RequirementFindingItem(@Nullable String requirement, @Nullable String reason, @Nullable String sourceQuote, @Nullable String evidenceArtifact,
            @Nullable String evidenceQuote, @Nullable String ownerType) {
    }

    private record ExampleCheckItem(@Nullable String claim, @Nullable JsonNode computedOutcome, @Nullable Boolean consistent, @Nullable String reason) {
    }

    private record ApiCheckItem(@Nullable String symbol, @Nullable Boolean discoverable, @Nullable String reason) {
    }

    private record TemplateCheckItem(@Nullable String ownerType, @Nullable String test, @Nullable Boolean targetReached, @Nullable String blockingCause, @Nullable String reason,
            @Nullable String evidenceQuote) {
    }

    private record MutantCheckItem(@Nullable String mutant, @Nullable Boolean killed, @Nullable String reason, @Nullable String sourceQuote, @Nullable String ownerType) {
    }

    private record ContractWitnessResponse(@Nullable List<ContractWitnessItem> witnesses) {
    }

    private record ContractWitnessItem(@Nullable String rule, @Nullable String testName, @Nullable String code) {
    }

    private record ExampleGapItem(@Nullable String behaviour, @Nullable String reason) {
    }

    private record AdaptationChangeItem(@Nullable String change, @Nullable String reason) {
    }

    private final ObjectMapper objectMapper;

    CriticVerdictParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Whether a parsed verdict carried at least one ungrounded oracle claim, which the runner answers with one bounded correction call. */
    static boolean hasUngroundedOracleReview(List<SpecFidelityReport.Finding> findings) {
        return findings.stream().anyMatch(CriticVerdictParser::isUngroundedOracleReviewMarker);
    }

    private static boolean isUngroundedOracleReviewMarker(SpecFidelityReport.Finding finding) {
        return finding.kind() == SpecFidelityReport.Kind.QUALITY_REVIEW_UNAVAILABLE && finding.detail() != null && finding.detail().startsWith(UNGROUNDED_ORACLE_REVIEW_DETAIL);
    }

    /**
     * Parses the model's JSON critic response defensively. Tolerates surrounding prose / code fences, truncates over-long text, and caps the total count across all finding kinds.
     * Advisory entries missing their text are ignored. Blocking and adaptation-scope entries fail closed when malformed because they control persistence. Generation ignores the
     * well-formed adaptation-only arrays.
     */
    @Nullable
    List<SpecFidelityReport.Finding> parseCritique(String text, ReviewPass pass, boolean requireScopeVerdict, String authoritativeSource, String repairableDownstreamSource,
            String candidateProblemStatement, boolean expectExampleChecks, boolean expectApiChecks, boolean expectTemplateChecks, boolean expectMutantChecks,
            Map<String, String> templateStatuses) {
        return parseCritique(text, pass, requireScopeVerdict, authoritativeSource, authoritativeSource, repairableDownstreamSource,
                Map.of("DOWNSTREAM", repairableDownstreamSource), candidateProblemStatement, expectExampleChecks, expectApiChecks, expectTemplateChecks, expectMutantChecks,
                templateStatuses);
    }

    @Nullable
    List<SpecFidelityReport.Finding> parseCritique(String text, ReviewPass pass, boolean requireScopeVerdict, String authoritativeSource, String contradictionAuthoritySource,
            String repairableDownstreamSource, Map<String, String> downstreamEvidenceByArtifact, String candidateProblemStatement, boolean expectExampleChecks,
            boolean expectApiChecks, boolean expectTemplateChecks, boolean expectMutantChecks, Map<String, String> templateStatuses) {
        CriticResponse parsed;
        try {
            parsed = objectMapper.readValue(extractJsonPayload(text), CriticResponse.class);
        }
        catch (Exception e) {
            log.debug("Full-artifact review JSON did not parse ({}); failing closed.", e.getMessage());
            return null;
        }
        if (parsed == null || pass == ReviewPass.CONTRACT && malformedContractVerdict(parsed, requireScopeVerdict, expectExampleChecks, expectApiChecks, expectTemplateChecks)
                || pass == ReviewPass.ORACLE && malformedOracleVerdict(parsed, expectMutantChecks, templateStatuses)) {
            return null;
        }
        if (pass == ReviewPass.CONTRACT && !templateStatuses.isEmpty()) {
            for (TemplateCheckItem item : parsed.templateChecks()) {
                if (item.targetReached()) {
                    continue;
                }
                String owner = normalizeOwnerType(item.ownerType());
                if (!owner.equals("shared scaffold") && !owner.equals("student-creates") && !templateStatuses.containsKey(owner)) {
                    return null;
                }
                // An intentionally incomplete student seam cannot simultaneously be a defect in provided scaffold. This rule comes from the frozen ownership contract rather
                // than source syntax: TODO, null, and UnsupportedOperationException are all valid ways to expose a stub.
                if ("PROVIDED_SCAFFOLD_DEFECT".equals(item.blockingCause()) && !owner.equals("shared scaffold") && !"given".equals(templateStatuses.get(owner))) {
                    return null;
                }
            }
        }
        boolean hasUngroundedOracleClaim = pass == ReviewPass.ORACLE && hasUngroundedOracleClaim(parsed, authoritativeSource, templateStatuses);
        List<SpecFidelityReport.Finding> findings = new ArrayList<>();
        // Scope violations require instructor attention, so retain them before advisory findings consume the shared defensive cap.
        if (requireScopeVerdict) {
            for (AdaptationChangeItem item : parsed.unrequestedChanges()) {
                if (findings.size() >= MAX_REVIEW_FINDINGS) {
                    break;
                }
                String reason = item.reason() != null && !item.reason().isBlank() ? item.reason().strip() : "the feedback does not require this change.";
                findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNREQUESTED_ADAPTATION_CHANGE, truncate(item.change().strip()),
                        "This adaptation changed content outside the requested scope: " + reason + " Restore it or make the feedback explicitly require the change."));
            }
            for (RequirementFindingItem item : parsed.missingRequestedChanges()) {
                if (findings.size() >= MAX_REVIEW_FINDINGS) {
                    break;
                }
                String reason = item.reason() != null && !item.reason().isBlank() ? item.reason().strip() : "the candidate diff does not show this requested change.";
                findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.REQUESTED_ADAPTATION_CHANGE_MISSING, truncate(item.requirement().strip()),
                        "This requested adaptation change is missing or incomplete: " + reason + " Implement it before saving the adaptation."));
            }
        }
        if (pass == ReviewPass.CONTRACT) {
            for (ExampleCheckItem item : parsed.exampleChecks()) {
                if (!item.consistent() && findings.size() < MAX_REVIEW_FINDINGS) {
                    if (!sourceQuoteIsGrounded(item.claim(), repairableDownstreamSource)) {
                        abstainUngroundedFinding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION, item.claim());
                        continue;
                    }
                    findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION, truncate(item.claim().strip()),
                            "The worked example computes to \"" + truncate(scalarText(item.computedOutcome())) + "\": " + item.reason().strip()));
                }
            }
            for (ApiCheckItem item : parsed.apiChecks()) {
                if (!item.discoverable() && findings.size() < MAX_REVIEW_FINDINGS) {
                    findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.HIDDEN_GRADED_REQUIREMENT, truncate(item.symbol().strip()),
                            "The tested public API is not discoverable from the statement and starter: " + item.reason().strip()));
                }
            }
            for (TemplateCheckItem item : parsed.templateChecks()) {
                if (!item.targetReached() && findings.size() < MAX_REVIEW_FINDINGS) {
                    String ownerType = item.ownerType().strip().replace("`", "");
                    if ("student-creates".equals(templateStatuses.get(ownerType))) {
                        log.info("Critic abstained on a template-gap finding for student-created type {} because the approved Design contract requires it to be absent.",
                                ownerType);
                        continue;
                    }
                    if (!sourceQuoteIsGrounded(item.evidenceQuote(), repairableDownstreamSource)) {
                        abstainUngroundedFinding(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP, item.test());
                        continue;
                    }
                    // The contract reviewer reports only directly evidenced scaffold defects here (contract docs, TODO anchors, provided-code failures, or non-student diffs).
                    findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.TEMPLATE_QUALITY_GAP, truncate(item.test().strip()),
                            "This starter scaffold check failed: " + item.reason().strip()));
                }
            }
            appendGroundedContradictions(findings, parsed.contradictions(), contradictionAuthoritySource, downstreamEvidenceByArtifact);
            appendGroundedHiddenRequirements(findings, parsed.hiddenRequirements(), authoritativeSource, candidateProblemStatement);
        }
        else {
            for (MutantCheckItem item : parsed.mutantChecks()) {
                if (item.killed() || findings.size() >= MAX_REVIEW_FINDINGS) {
                    continue;
                }
                if (!oracleTargetsStudentWork(item.ownerType(), templateStatuses)) {
                    abstainNonStudentOracleFinding(item.ownerType(), item.mutant());
                    continue;
                }
                if (unsupportedSourceRequirement(item.mutant(), item.sourceQuote(), authoritativeSource)) {
                    log.info("Critic abstained on an oracle mutant that added a contract term absent from its cited source passage: {}", item.mutant());
                    continue;
                }
                if (!sourceQuoteIsGrounded(item.sourceQuote(), authoritativeSource)) {
                    abstainUngroundedFinding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, item.mutant());
                    continue;
                }
                findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.WEAK_TEST_ORACLE, truncate(item.mutant().strip()),
                        "This concrete contract-breaking implementation survives the generated suite: " + item.reason().strip()));
            }
            appendGroundedOracleFindings(findings, parsed.weakOracle(), authoritativeSource, templateStatuses, SpecFidelityReport.Kind.WEAK_TEST_ORACLE,
                    "A plausible contract-breaking implementation can pass the generated tests: ");
        }
        if (pass == ReviewPass.ORACLE && findings.size() < MAX_REVIEW_FINDINGS) {
            for (RequirementFindingItem item : parsed.uncovered()) {
                if (findings.size() >= MAX_REVIEW_FINDINGS) {
                    break;
                }
                if (item == null || item.requirement() == null || item.requirement().isBlank()) {
                    continue;
                }
                if (!oracleTargetsStudentWork(item.ownerType(), templateStatuses)) {
                    abstainNonStudentOracleFinding(item.ownerType(), item.requirement());
                    continue;
                }
                if (unsupportedSourceRequirement(item.requirement(), item.sourceQuote(), authoritativeSource)) {
                    log.info("Critic abstained on an uncovered requirement that added a contract term absent from its cited source passage: {}", item.requirement());
                    continue;
                }
                if (!sourceQuoteIsGrounded(item.sourceQuote(), authoritativeSource)) {
                    abstainUngroundedFinding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, item.requirement());
                    continue;
                }
                String requirement = truncate(item.requirement().strip());
                String reason = item.reason() != null && !item.reason().isBlank() ? item.reason().strip() : "The brief names this requirement but no test appears to cover it.";
                findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, requirement,
                        "The review found no discriminating assertion for this behavior: " + reason
                                + " Confirm that the behavior comes from the source requirements before changing artifacts. If it does, add the smallest assertion that would reject a plausible wrong solution and align the statement, solution, and starter. Otherwise remove the invented promise rather than expanding the exercise."));
            }
        }
        if (hasUngroundedOracleClaim && findings.size() < MAX_REVIEW_FINDINGS) {
            findings.addAll(SpecFidelityReport.qualityReviewUnavailable(UNGROUNDED_ORACLE_REVIEW_DETAIL).findings());
        }
        if (pass == ReviewPass.CONTRACT) {
            for (ExampleGapItem item : parsed.missingExamples()) {
                if (findings.size() >= MAX_REVIEW_FINDINGS) {
                    break;
                }
                if (item == null || item.behaviour() == null || item.behaviour().isBlank()) {
                    continue;
                }
                String behaviour = truncate(item.behaviour().strip());
                String reason = item.reason() != null && !item.reason().isBlank() ? item.reason().strip() : "a representative example would materially improve understanding.";
                findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MISSING_WORKED_EXAMPLE, behaviour,
                        "This important behaviour may benefit from a concrete worked example: " + reason));
            }
        }
        if (pass == ReviewPass.CONTRACT) {
            for (RequirementFindingItem item : parsed.invented()) {
                if (findings.size() >= MAX_REVIEW_FINDINGS) {
                    break;
                }
                if (item == null || item.requirement() == null || item.requirement().isBlank()) {
                    continue;
                }
                if (!sourceQuoteIsGrounded(item.sourceQuote(), repairableDownstreamSource)) {
                    abstainUngroundedFinding(SpecFidelityReport.Kind.INVENTED_REQUIREMENT, item.requirement());
                    continue;
                }
                String requirement = truncate(item.requirement().strip());
                String reason = item.reason() != null && !item.reason().isBlank() ? item.reason().strip() : "the brief does not state it.";
                findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.INVENTED_REQUIREMENT, requirement,
                        "A generated downstream artifact imposes a graded requirement the instructor's brief did not ask for: " + reason
                                + " Confirm this is intended; if not, relax the statement, code, and tests to match the brief."));
            }
        }
        return findings;
    }

    private static boolean malformedContractVerdict(CriticResponse parsed, boolean requireScopeVerdict, boolean expectExampleChecks, boolean expectApiChecks,
            boolean expectTemplateChecks) {
        return parsed.exampleChecks() == null || parsed.apiChecks() == null || parsed.templateChecks() == null || expectExampleChecks && parsed.exampleChecks().isEmpty()
                || expectApiChecks && parsed.apiChecks().isEmpty() || expectTemplateChecks && parsed.templateChecks().isEmpty() || malformedExampleChecks(parsed.exampleChecks())
                || malformedApiChecks(parsed.apiChecks()) || malformedTemplateChecks(parsed.templateChecks()) || parsed.contradictions() == null
                || parsed.hiddenRequirements() == null || parsed.missingExamples() == null || parsed.invented() == null || parsed.unrequestedChanges() == null
                || parsed.missingRequestedChanges() == null || malformedGroundedContradictions(parsed.contradictions())
                || malformedGroundedBlockingItems(parsed.hiddenRequirements()) || malformedGroundedBlockingItems(parsed.invented())
                || requireScopeVerdict && (parsed.unrequestedChanges().stream()
                        .anyMatch(item -> item == null || item.change() == null || item.change().isBlank() || item.reason() == null || item.reason().isBlank())
                        || malformedBlockingItems(parsed.missingRequestedChanges()));
    }

    private static boolean malformedOracleVerdict(CriticResponse parsed, boolean expectMutantChecks, Map<String, String> templateStatuses) {
        return parsed.mutantChecks() == null || expectMutantChecks && parsed.mutantChecks().isEmpty() || malformedMutantChecks(parsed.mutantChecks()) || parsed.uncovered() == null
                || parsed.mutantChecks().stream().anyMatch(item -> !item.killed() && (item.sourceQuote() == null || item.sourceQuote().isBlank())) || parsed.weakOracle() == null
                || malformedGroundedBlockingItems(parsed.uncovered()) || malformedGroundedBlockingItems(parsed.weakOracle())
                || !templateStatuses.isEmpty() && (parsed.mutantChecks().stream().anyMatch(item -> !item.killed() && !knownDesignOwner(item.ownerType(), templateStatuses))
                        || parsed.uncovered().stream().anyMatch(item -> !knownDesignOwner(item.ownerType(), templateStatuses))
                        || parsed.weakOracle().stream().anyMatch(item -> !knownDesignOwner(item.ownerType(), templateStatuses)));
    }

    private static boolean hasUngroundedOracleClaim(CriticResponse parsed, String authoritativeSource, Map<String, String> templateStatuses) {
        return parsed.mutantChecks().stream()
                .anyMatch(item -> !item.killed() && oracleTargetsStudentWork(item.ownerType(), templateStatuses) && !sourceQuoteIsGrounded(item.sourceQuote(), authoritativeSource))
                || parsed.uncovered().stream()
                        .anyMatch(item -> oracleTargetsStudentWork(item.ownerType(), templateStatuses) && !sourceQuoteIsGrounded(item.sourceQuote(), authoritativeSource))
                || parsed.weakOracle().stream()
                        .anyMatch(item -> oracleTargetsStudentWork(item.ownerType(), templateStatuses) && !sourceQuoteIsGrounded(item.sourceQuote(), authoritativeSource));
    }

    private static boolean malformedGroundedBlockingItems(List<RequirementFindingItem> items) {
        return malformedBlockingItems(items) || items.stream().anyMatch(item -> item.sourceQuote() == null || item.sourceQuote().isBlank());
    }

    private static boolean malformedGroundedContradictions(List<RequirementFindingItem> items) {
        return malformedGroundedBlockingItems(items) || items.stream()
                .anyMatch(item -> item.evidenceArtifact() == null || item.evidenceArtifact().isBlank() || item.evidenceQuote() == null || item.evidenceQuote().isBlank());
    }

    private static boolean malformedBlockingItems(List<RequirementFindingItem> items) {
        return items.stream().anyMatch(item -> item == null || item.requirement() == null || item.requirement().isBlank() || item.reason() == null || item.reason().isBlank());
    }

    private static boolean malformedExampleChecks(List<ExampleCheckItem> items) {
        return items.stream()
                .anyMatch(item -> item == null || item.claim() == null || item.claim().isBlank() || item.computedOutcome() == null || !item.computedOutcome().isValueNode()
                        || scalarText(item.computedOutcome()).isBlank() || item.consistent() == null || item.reason() == null || item.reason().isBlank());
    }

    private static boolean malformedApiChecks(List<ApiCheckItem> items) {
        return items.stream().anyMatch(
                item -> item == null || item.symbol() == null || item.symbol().isBlank() || item.discoverable() == null || item.reason() == null || item.reason().isBlank());
    }

    private static boolean malformedTemplateChecks(List<TemplateCheckItem> items) {
        return items.stream().anyMatch(
                item -> item == null || item.test() == null || item.test().isBlank() || item.targetReached() == null || item.targetReached() && item.blockingCause() != null
                        || !item.targetReached() && (item.ownerType() == null || item.ownerType().isBlank() || item.blockingCause() == null
                                || !Set.of("DIFFERENT_STUDENT_SEAM", "PROVIDED_SCAFFOLD_DEFECT").contains(item.blockingCause()) || item.evidenceQuote() == null
                                || item.evidenceQuote().isBlank())
                        || item.reason() == null || item.reason().isBlank());
    }

    private static String scalarText(JsonNode node) {
        return node.isTextual() ? node.textValue() : node.toString();
    }

    private static boolean malformedMutantChecks(List<MutantCheckItem> items) {
        return items.stream()
                .anyMatch(item -> item == null || item.mutant() == null || item.mutant().isBlank() || item.killed() == null || item.reason() == null || item.reason().isBlank());
    }

    /**
     * Appends one blocking finding per grounded item, uniformly requiring a {@code sourceQuote} that literally appears in the grounding source the caller passed. That source is
     * not the same per pass (see {@code SpecFidelityCriticService#reviewArtifacts}): the ORACLE pass grounds against the instructor brief plus the frozen specification only,
     * while the CONTRACT pass also accepts the produced problem statement, the grading plan, and the produced solution/template/test sources. Grounding therefore proves
     * provenance — the quote was in something the reviewer was shown — and for the CONTRACT pass that can be the candidate's own artifacts rather than any instructor-authored
     * requirement.
     * <p>
     * An item whose quote does not validate is the critic's abstain outcome: it is logged for observability and dropped rather than surfaced, so it can never drive repair or
     * reach the instructor as a hallucinated blocker.
     */
    private static void appendGroundedBlockingFindings(List<SpecFidelityReport.Finding> findings, List<RequirementFindingItem> items, String authoritativeSource,
            SpecFidelityReport.Kind kind, String detailPrefix) {
        for (RequirementFindingItem item : items) {
            if (findings.size() >= MAX_REVIEW_FINDINGS) {
                return;
            }
            if (item == null || item.requirement() == null || item.requirement().isBlank()) {
                continue;
            }
            if (!sourceQuoteIsGrounded(item.sourceQuote(), authoritativeSource)) {
                abstainUngroundedFinding(kind, item.requirement());
                continue;
            }
            findings.add(new SpecFidelityReport.Finding(kind, truncate(item.requirement().strip()), detailPrefix + item.reason().strip()));
        }
    }

    /**
     * A contradiction needs evidence for both sides: the authority it violates and the repairable artifact that conflicts with it. Grounding only the first side lets fluent
     * reviewer prose invent a downstream fact that is not present in any produced file.
     */
    private static void appendGroundedContradictions(List<SpecFidelityReport.Finding> findings, List<RequirementFindingItem> items, String authoritativeSource,
            Map<String, String> downstreamEvidenceByArtifact) {
        for (RequirementFindingItem item : items) {
            if (findings.size() >= MAX_REVIEW_FINDINGS) {
                return;
            }
            if (!sourceQuoteIsGrounded(item.sourceQuote(), authoritativeSource)) {
                abstainUngroundedFinding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION, item.requirement());
                continue;
            }
            String evidenceArtifact = item.evidenceArtifact().strip();
            String artifactContent = downstreamEvidenceByArtifact.get(evidenceArtifact);
            if (artifactContent == null || !sourceQuoteIsGrounded(item.evidenceQuote(), artifactContent)) {
                log.info("Critic abstained on a contradiction whose alleged evidence was absent from {}: {}", evidenceArtifact, truncate(item.requirement().strip()));
                continue;
            }
            findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.CONTRACT_CONTRADICTION, truncate(item.requirement().strip()),
                    "The generated artifacts contradict this contract: " + item.reason().strip()));
        }
    }

    private static void appendGroundedHiddenRequirements(List<SpecFidelityReport.Finding> findings, List<RequirementFindingItem> items, String authoritativeSource,
            String candidateProblemStatement) {
        for (RequirementFindingItem item : items) {
            if (findings.size() >= MAX_REVIEW_FINDINGS) {
                return;
            }
            if (item == null || item.requirement() == null || item.requirement().isBlank()) {
                continue;
            }
            if (!sourceQuoteIsGrounded(item.sourceQuote(), authoritativeSource)) {
                abstainUngroundedFinding(SpecFidelityReport.Kind.HIDDEN_GRADED_REQUIREMENT, item.requirement());
                continue;
            }
            if (exactExceptionMessageIsDiscoverable(item.requirement(), candidateProblemStatement)) {
                log.info("Critic abstained on a hidden-requirement finding whose exact exception contract is present in the problem statement: {}", item.requirement());
                continue;
            }
            findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.HIDDEN_GRADED_REQUIREMENT, truncate(item.requirement().strip()),
                    "The grader requires behaviour or API that is not discoverable to the student: " + item.reason().strip()));
        }
    }

    private static boolean exactExceptionMessageIsDiscoverable(String requirement, String candidateProblemStatement) {
        if (candidateProblemStatement.isBlank() || !requirement.toLowerCase(Locale.ROOT).contains("message")) {
            return false;
        }
        List<String> quotedTerms = QUOTED_CONTRACT_TERM.matcher(requirement).results().map(match -> match.group(1)).toList();
        List<String> exceptionTypes = EXCEPTION_TYPE.matcher(requirement).results().map(match -> match.group()).toList();
        return !quotedTerms.isEmpty() && !exceptionTypes.isEmpty() && quotedTerms.stream().allMatch(candidateProblemStatement::contains)
                && exceptionTypes.stream().allMatch(candidateProblemStatement::contains);
    }

    private static void appendGroundedOracleFindings(List<SpecFidelityReport.Finding> findings, List<RequirementFindingItem> items, String authoritativeSource,
            Map<String, String> templateStatuses, SpecFidelityReport.Kind kind, String detailPrefix) {
        for (RequirementFindingItem item : items) {
            if (findings.size() >= MAX_REVIEW_FINDINGS) {
                return;
            }
            if (item == null || item.requirement() == null || item.requirement().isBlank()) {
                continue;
            }
            if (!oracleTargetsStudentWork(item.ownerType(), templateStatuses)) {
                abstainNonStudentOracleFinding(item.ownerType(), item.requirement());
                continue;
            }
            if (unsupportedSourceRequirement(item.requirement(), item.sourceQuote(), authoritativeSource)) {
                log.info("Critic abstained on a weak oracle that added a contract term absent from its cited source passage: {}", item.requirement());
                continue;
            }
            if (!sourceQuoteIsGrounded(item.sourceQuote(), authoritativeSource)) {
                abstainUngroundedFinding(kind, item.requirement());
                continue;
            }
            findings.add(new SpecFidelityReport.Finding(kind, truncate(item.requirement().strip()), detailPrefix + item.reason().strip()));
        }
    }

    /**
     * Oracle review is allowed to grade only behavior the approved Design assigns to students. When no Design map exists (older/adaptation inputs), preserve the legacy
     * source-grounded review rather than silently discarding all findings. With an authoritative map, a missing or unknown owner makes the verdict incomplete, while a known
     * given owner is an abstention: prose review cannot create a student task for provided support.
     */
    private static boolean oracleTargetsStudentWork(@Nullable String ownerType, Map<String, String> templateStatuses) {
        if (templateStatuses.isEmpty()) {
            return true;
        }
        String status = templateStatuses.get(normalizeOwnerType(ownerType));
        return "stubbed".equals(status) || "student-creates".equals(status);
    }

    private static boolean knownDesignOwner(@Nullable String ownerType, Map<String, String> templateStatuses) {
        return templateStatuses.containsKey(normalizeOwnerType(ownerType));
    }

    private static String normalizeOwnerType(@Nullable String ownerType) {
        return ownerType == null ? "" : ownerType.strip().replace("`", "");
    }

    private static void abstainNonStudentOracleFinding(@Nullable String ownerType, String requirement) {
        log.info("Critic abstained on an oracle finding for Design owner {} because the approved contract does not identify it as stubbed or student-creates: {}",
                ownerType == null || ownerType.isBlank() ? "(missing)" : ownerType.strip(), truncate(requirement.strip()));
    }

    private static boolean unsupportedSourceRequirement(String requirement, @Nullable String sourceQuote, String authoritativeSource) {
        if (sourceQuote == null) {
            return false;
        }
        String evidenceId = sourceQuote.strip().replaceFirst("^\\[", "").replaceFirst("]$", "");
        String citedPassage = sourceQuote;
        if (evidenceId.matches("P[1-9][0-9]*")) {
            citedPassage = EvidenceSource.from("P", authoritativeSource).passages().get(evidenceId);
            if (citedPassage == null) {
                return false;
            }
        }
        String passage = citedPassage;
        return EXPLICIT_SOURCE_TERMS.stream().anyMatch(term -> term.matcher(requirement).find() && !term.matcher(passage).find());
    }

    /**
     * The critic's abstain outcome for a finding it cannot ground: logged for operability so ungrounded-finding rates are observable, then dropped. An abstained finding is
     * never added to the report, so it can never drive the retry prompt (see {@code SpecFidelityCriticService#renderForRetryPrompt}) or reach the instructor review.
     */
    private static void abstainUngroundedFinding(SpecFidelityReport.Kind kind, @Nullable String requirement) {
        log.info("Critic abstained on an ungrounded {} finding (no verbatim source quote in that finding category's grounding source): {}", kind,
                requirement == null ? "(no requirement text)" : truncate(requirement.strip()));
    }

    private static boolean sourceQuoteIsGrounded(@Nullable String sourceQuote, String authoritativeSource) {
        if (sourceQuote == null || sourceQuote.isBlank()) {
            return false;
        }
        String evidenceId = sourceQuote.strip().replaceFirst("^\\[", "").replaceFirst("]$", "");
        if (evidenceId.matches("P[1-9][0-9]*") && EvidenceSource.from("P", authoritativeSource).passages().containsKey(evidenceId)) {
            return true;
        }
        // Verbatim quoting is the general grounding mechanism, not a compatibility shim: server-generated evidence IDs are rendered for the ORACLE pass's primary source only, so
        // a quote taken from the problem statement, the grading plan, or the produced artifacts can prove its provenance only by appearing literally in the grounding source.
        return normalizeQuote(authoritativeSource).contains(normalizeQuote(sourceQuote));
    }

    private static String normalizeQuote(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).replaceAll("\\s+", " ").strip();
    }
}
