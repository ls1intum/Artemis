package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * Spec-fidelity / coverage critic — the one quality axis the differential oracle ({@link AuthoritativeVerificationService}) is structurally blind to.
 * <p>
 * The oracle proves an exercise is internally <em>consistent</em> (the solution passes its own tests, the template fails them, every [task] binds) but never whether the produced
 * tests cover the requirements the <em>instructor's brief</em> actually names. Three real defect classes from a GPU+sandbox hard-exercise audit slip straight through it:
 * <ul>
 * <li><strong>Spec-narrowing:</strong> a "count user-perceived characters incl. emoji and CJK" brief shipped with tests for only precomposed {@code café} and one emoji, with no
 * CJK and no ZWJ/flag-emoji test — internally consistent, but wrong for the real spec.</li>
 * <li><strong>Untested promises:</strong> a stated contract ("must not modify the input", "throw {@code invalid_argument} on zero capacity") with no test asserting it.</li>
 * <li><strong>Grader-mechanics leakage:</strong> grader-internal phrasing ("All functions should raise NotImplementedError in the template file to make the tests fail") leaking
 * into the student-facing problem statement.</li>
 * </ul>
 * It does this in two passes: a cheap deterministic regex pass over the problem statement for grader-mechanics leaks (no model call), and a single bounded LLM pass that marks the
 * brief's named requirements that no test references.
 * <p>
 * <strong>Non-blocking by construction.</strong> The differential oracle stays the sole source of truth for acceptance; this critic NEVER participates in the accept/reject
 * decision,
 * so a false positive (its real risk) can only ever add an advisory note. Its findings are folded into the verifier-feedback retry prompt while attempts remain (so the agent adds
 * the missing test) and surfaced as advisory review comments otherwise. Any failure of the model pass — a timeout, an error, empty or garbage output — is swallowed and yields no
 * findings, so a critic failure never perturbs the run.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class SpecFidelityCriticService {

    private static final Logger log = LoggerFactory.getLogger(SpecFidelityCriticService.class);

    /** Hard cap on the critic's output so the single pass can never explode cost; one finding is a couple of sentences, so this comfortably bounds a realistic finding list. */
    private static final int CRITIC_MAX_OUTPUT_TOKENS = 1_500;

    /** Defensive cap on how many model-reported uncovered requirements are surfaced, so a degenerate response can never flood the retry prompt or the review panel. */
    private static final int MAX_COVERAGE_FINDINGS = 12;

    /** Below this brief length there is no meaningful spec to critique (an empty/placeholder brief); the LLM pass is skipped to avoid a pointless call and false positives. */
    private static final int MIN_BRIEF_CHARS = 40;

    /** A requirement string longer than this is almost certainly the model rambling rather than naming a concrete requirement; it is truncated before surfacing. */
    private static final int MAX_REQUIREMENT_CHARS = 240;

    /** Matches a JSON object wrapped in a markdown code block (```json ... ``` or ``` ... ```), so a fenced model response is parsed. */
    private static final Pattern JSON_CODE_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);

    private static final String CRITIC_SYSTEM_PROMPT = """
            You are a meticulous QA reviewer for programming-exercise test suites. You are given an instructor's BRIEF (what the exercise must require), the produced PROBLEM \
            STATEMENT, and the exact list of TEST NAMES that were written. Your ONE job: list the concrete, checkable requirements and edge cases that the brief (or problem \
            statement) explicitly names but that NO test appears to cover.

            Count as a requirement only something concrete and assertable that the brief actually states, e.g.: a named input class to handle ("CJK characters", "emoji", \
            "empty input", "negative numbers"), a stated invariant ("must not modify the input"), a specific exception/error contract ("throws invalid_argument on zero capacity", \
            "returns -1 when not found"), or a specific numeric/ordering guarantee. Do NOT invent requirements the brief does not state, and do NOT restate the happy path as a \
            requirement. Judge coverage generously from the test NAMES and the problem statement: if a plausibly-named test exists for a requirement, treat it as covered. Only \
            flag a requirement when there is clearly no corresponding test.

            Respond with ONLY a JSON object, no prose, of the exact form:
            {"uncovered": [{"requirement": "<the requirement in the brief's own terms>", "reason": "<why no test covers it>"}]}
            If every stated requirement is covered, respond with {"uncovered": []}.""";

    /** The structured shape the coverage pass parses the model JSON into. */
    private record UncoveredResponse(@Nullable List<UncoveredItem> uncovered) {
    }

    private record UncoveredItem(@Nullable String requirement, @Nullable String reason) {
    }

    /**
     * Grader-mechanics phrases that must never appear in the student-facing problem statement. These describe how the grader/template is rigged, not the task; their presence means
     * grader internals leaked into student-facing text. Matched case-insensitively as substrings, so they catch the common phrasings without a brittle full-sentence match.
     */
    private static final List<Pattern> MECHANICS_LEAK_PATTERNS = List.of(compile("notimplementederror"), compile("todo!\\(\\)"), compile("make (all )?(the )?tests fail"),
            compile("the template must fail"));

    private static Pattern compile(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    // Nullable like the sibling Hyperion services: the shared ChatClient bean is null when no AI provider is configured, in which case the LLM pass is skipped.
    @Nullable
    private final ChatClient chatClient;

    private final ObjectMapper objectMapper;

    public SpecFidelityCriticService(@Nullable ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Critiques one produced exercise against the instructor brief. Runs the deterministic mechanics-leak pass and the single bounded LLM coverage pass and returns their combined
     * findings. Never throws: any failure of the LLM pass yields no coverage findings (the mechanics-leak findings, being model-free, are unaffected).
     *
     * @param brief            the instructor's instruction for this run (the generation brief or the adapt feedback)
     * @param problemStatement the produced student-facing problem statement
     * @param testNames        the exact test identifiers the produced suite contains (as the runner writes them); may be empty
     * @return the advisory report (possibly empty); never {@code null}
     */
    public SpecFidelityReport critique(@Nullable String brief, @Nullable String problemStatement, List<String> testNames) {
        List<SpecFidelityReport.Finding> findings = new ArrayList<>(detectMechanicsLeaks(problemStatement));
        findings.addAll(detectUncoveredRequirements(brief, problemStatement, testNames));
        return new SpecFidelityReport(List.copyOf(findings));
    }

    private List<SpecFidelityReport.Finding> detectMechanicsLeaks(@Nullable String problemStatement) {
        if (problemStatement == null || problemStatement.isBlank()) {
            return List.of();
        }
        List<SpecFidelityReport.Finding> leaks = new ArrayList<>();
        for (Pattern pattern : MECHANICS_LEAK_PATTERNS) {
            var matcher = pattern.matcher(problemStatement);
            if (matcher.find()) {
                String matched = problemStatement.substring(matcher.start(), Math.min(problemStatement.length(), matcher.start() + MAX_REQUIREMENT_CHARS)).strip();
                leaks.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.MECHANICS_LEAK, matched,
                        "This grader/template-mechanics phrasing should not appear in the student-facing problem statement — it describes how the exercise is rigged for grading, "
                                + "not the task. Remove it so students see only the task and its requirements."));
            }
        }
        return leaks;
    }

    /**
     * Runs the single LLM coverage pass via the shared ChatClient and parses its findings defensively. Returns no findings — never throws — when there is no model, the brief is
     * too
     * short to be a real spec, or the call/parse fails for any reason (timeout, error, empty or garbage output), so a critic failure can never perturb the run.
     */
    private List<SpecFidelityReport.Finding> detectUncoveredRequirements(@Nullable String brief, @Nullable String problemStatement, List<String> testNames) {
        if (chatClient == null) {
            return List.of();
        }
        String effectiveBrief = brief == null ? "" : brief.strip();
        if (effectiveBrief.length() < MIN_BRIEF_CHARS) {
            // No real instructor spec to critique (empty/placeholder brief): skip rather than hallucinate requirements out of nothing.
            return List.of();
        }
        try {
            // Output-capped, tool-free, single call (no retry): a bounded constant cost that cannot loop. A plain ChatOptions means the critic cannot call tools.
            ChatResponse response = chatClient.prompt().system(CRITIC_SYSTEM_PROMPT).user(renderUserPrompt(effectiveBrief, problemStatement, testNames))
                    .options(ChatOptions.builder().maxTokens(CRITIC_MAX_OUTPUT_TOKENS).build()).call().chatResponse();
            String text = LLMTokenUsageService.extractResponseText(response);
            if (text == null || text.isBlank()) {
                return List.of();
            }
            return parseUncovered(text);
        }
        catch (RuntimeException e) {
            // Graceful skip: a critic failure must never fail the run. The differential oracle's verdict is unaffected.
            log.warn("Spec-fidelity critic LLM pass failed; skipping coverage findings for this attempt: {}", e.getMessage());
            return List.of();
        }
    }

    private static String renderUserPrompt(String brief, @Nullable String problemStatement, List<String> testNames) {
        String tests = testNames.isEmpty() ? "(no tests were produced)" : String.join("\n", testNames);
        return "INSTRUCTOR BRIEF:\n" + brief + "\n\nPRODUCED PROBLEM STATEMENT:\n" + (problemStatement == null || problemStatement.isBlank() ? "(empty)" : problemStatement.strip())
                + "\n\nTEST NAMES (" + testNames.size() + "):\n" + tests + "\n\nList the brief's concrete requirements/edge-cases that no test covers, as the specified JSON.";
    }

    /**
     * Parses the model's JSON coverage response defensively. Tolerates surrounding prose / code fences, ignores entries missing a requirement, truncates an over-long requirement,
     * and caps the count. Any structural surprise yields no findings rather than an exception.
     */
    private List<SpecFidelityReport.Finding> parseUncovered(String text) {
        UncoveredResponse parsed;
        try {
            parsed = objectMapper.readValue(extractJsonPayload(text), UncoveredResponse.class);
        }
        catch (Exception e) {
            log.debug("Spec-fidelity critic JSON did not parse ({}); treating as no findings.", e.getMessage());
            return List.of();
        }
        if (parsed == null || parsed.uncovered() == null) {
            return List.of();
        }
        List<SpecFidelityReport.Finding> findings = new ArrayList<>();
        for (UncoveredItem item : parsed.uncovered()) {
            if (findings.size() >= MAX_COVERAGE_FINDINGS) {
                break;
            }
            if (item == null || item.requirement() == null || item.requirement().isBlank()) {
                continue;
            }
            String requirement = truncate(item.requirement().strip());
            String reason = item.reason() != null && !item.reason().isBlank() ? item.reason().strip() : "The brief names this requirement but no test appears to cover it.";
            findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, requirement,
                    "The brief names this requirement/edge-case but no test appears to cover it: " + reason
                            + " Add a test that asserts it (an untested promise lets a wrong solution pass)."));
        }
        return findings;
    }

    /**
     * Extracts the JSON object from a raw model response, tolerating a markdown code fence or leading/trailing prose. Mirrors the sibling Hyperion services' extraction so a chatty
     * local model's response still parses: (1) a fenced block, (2) the span from the first {@code {} to the last {@code }}, (3) the raw text.
     */
    private static String extractJsonPayload(String responseText) {
        String trimmed = responseText.trim();
        Matcher codeBlockMatcher = JSON_CODE_BLOCK_PATTERN.matcher(trimmed);
        if (codeBlockMatcher.find()) {
            return codeBlockMatcher.group(1).trim();
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_REQUIREMENT_CHARS ? value : value.substring(0, MAX_REQUIREMENT_CHARS) + "…";
    }

    /**
     * Renders the critic's findings as a compact block for the verifier-feedback retry prompt, instructing the agent to add the missing tests / clean the leaked phrasing. Returns
     * an empty string when there is nothing to report, so the caller can append unconditionally.
     *
     * @param report the critic report
     * @return a retry-prompt fragment, or an empty string when there are no findings
     */
    public String renderForRetryPrompt(SpecFidelityReport report) {
        if (!report.hasFindings()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(
                "\n\nAdditionally, a spec-fidelity review of your exercise found these gaps against the instructor's brief (these did NOT cause rejection, but fixing them makes the "
                        + "exercise match the brief):");
        for (SpecFidelityReport.Finding finding : report.findings()) {
            if (finding.kind() == SpecFidelityReport.Kind.MECHANICS_LEAK) {
                builder.append("\n- The problem statement contains grader-mechanics phrasing that students should not see (\"").append(finding.requirement())
                        .append("\"). Remove it from the student-facing problem statement.");
            }
            else {
                builder.append("\n- No test covers this requirement from the brief: \"").append(finding.requirement()).append("\". Add a test that asserts it.");
            }
        }
        return builder.toString();
    }
}
