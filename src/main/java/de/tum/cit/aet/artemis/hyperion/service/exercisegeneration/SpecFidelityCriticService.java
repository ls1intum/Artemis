package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * This critic flags all three. It does it in two passes:
 * <ol>
 * <li>a cheap, deterministic regex/keyword pass over the produced problem statement for grader-mechanics leaks (no model call); and</li>
 * <li>a single, bounded LLM pass that extracts the concrete requirements/edge-cases the brief names and marks any that no test references.</li>
 * </ol>
 * <p>
 * <strong>Non-blocking by construction.</strong> The differential oracle stays the sole source of truth for acceptance. This critic NEVER participates in the accept/reject
 * decision, so a false positive (its real risk) can only ever add an advisory note — it can never flip a sound, oracle-accepted exercise to rejected. Its findings are used in two
 * non-blocking ways by the orchestrator: folded into the verifier-feedback retry prompt while attempts remain (so the agent adds the missing test), and surfaced as advisory review
 * comments otherwise.
 * <p>
 * <strong>Degrades gracefully.</strong> Any failure of the model pass — a timeout, an error, empty or garbage output — is swallowed and the pass contributes no findings; a critic
 * failure therefore never fails or even perturbs the run. A single call, a bounded output, and a one-shot (no retry, no loop) keep its cost and latency a small constant.
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

    /**
     * Grader-mechanics phrases that must never appear in the student-facing problem statement. These describe how the grader/template is rigged, not the task; their presence means
     * grader internals leaked into student-facing text. Matched case-insensitively as substrings, so they catch the common phrasings without a brittle full-sentence match.
     */
    private static final List<Pattern> MECHANICS_LEAK_PATTERNS = List.of(compile("to make the tests fail"), compile("so the tests fail"), compile("make all (the )?tests fail"),
            compile("notimplementederror"), compile("not[ _]?implemented[ _]?error"), compile("placeholder (value|implementation) (that|to)"), compile("guarantees all tests fail"),
            compile("always returns? (the )?wrong"), compile("deliberately wrong"), compile("stub(s)? (that|to) (make|fail)"), compile("todo marker"),
            compile("the template must fail"), compile("raise todo"), compile("todo!\\(\\)"));

    private static Pattern compile(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    // Injected as a collection (mirroring AgentLoopRunner) so multiple ChatModel beans on the classpath are unambiguous and a unit test can inject a single mock; the first
    // available model is used. May be empty if no AI provider is configured, in which case the LLM pass is skipped.
    @Nullable
    private final ChatModel chatModel;

    private final ObjectMapper objectMapper;

    public SpecFidelityCriticService(Collection<ChatModel> chatModels, ObjectMapper objectMapper) {
        this.chatModel = chatModels.isEmpty() ? null : chatModels.iterator().next();
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
        List<SpecFidelityReport.Finding> findings = new ArrayList<>();
        // 1. Deterministic, model-free: grader-mechanics leaked into the student-facing problem statement. Cheap and never fails, so it runs unconditionally.
        findings.addAll(detectMechanicsLeaks(problemStatement));
        // 2. Single bounded LLM pass: brief requirements that no test covers. Skipped (no findings) when there is no model or no real brief, and on any failure.
        findings.addAll(detectUncoveredRequirements(brief, problemStatement, testNames));
        return new SpecFidelityReport(List.copyOf(findings));
    }

    /**
     * Scans the student-facing problem statement for grader-mechanics phrasing. Purely deterministic (no model), so it is always run and never fails.
     */
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
     * Runs the single LLM coverage pass and parses its findings defensively. Returns no findings — never throws — when there is no model, the brief is too short to be a real spec,
     * or the call/parse fails for any reason (timeout, error, empty or garbage output), so a critic failure can never perturb the run.
     */
    private List<SpecFidelityReport.Finding> detectUncoveredRequirements(@Nullable String brief, @Nullable String problemStatement, List<String> testNames) {
        if (chatModel == null) {
            return List.of();
        }
        String effectiveBrief = brief == null ? "" : brief.strip();
        if (effectiveBrief.length() < MIN_BRIEF_CHARS) {
            // No real instructor spec to critique (empty/placeholder brief): skip rather than hallucinate requirements out of nothing.
            return List.of();
        }
        try {
            String userPrompt = renderUserPrompt(effectiveBrief, problemStatement, testNames);
            // Tool-free, output-capped, single call (no retry): a bounded constant cost that cannot loop. A plain ChatOptions means the critic cannot call tools.
            ChatResponse response = chatModel.call(
                    new Prompt(List.of(new SystemMessage(CRITIC_SYSTEM_PROMPT), new UserMessage(userPrompt)), ChatOptions.builder().maxTokens(CRITIC_MAX_OUTPUT_TOKENS).build()));
            String text = extractText(response);
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
     * Parses the model's JSON coverage response defensively. Tolerates surrounding prose / code fences by extracting the first balanced JSON object, ignores entries missing a
     * requirement, truncates an over-long requirement, and caps the count. Any structural surprise yields no findings rather than an exception.
     */
    private List<SpecFidelityReport.Finding> parseUncovered(String text) {
        String json = extractJsonObject(text);
        if (json == null) {
            log.debug("Spec-fidelity critic produced no parseable JSON object; treating as no findings.");
            return List.of();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        }
        catch (JsonProcessingException e) {
            log.debug("Spec-fidelity critic JSON did not parse ({}); treating as no findings.", e.getMessage());
            return List.of();
        }
        JsonNode uncovered = root.get("uncovered");
        if (uncovered == null || !uncovered.isArray()) {
            return List.of();
        }
        List<SpecFidelityReport.Finding> findings = new ArrayList<>();
        for (JsonNode node : uncovered) {
            if (findings.size() >= MAX_COVERAGE_FINDINGS) {
                break;
            }
            JsonNode requirementNode = node.get("requirement");
            if (requirementNode == null || !requirementNode.isTextual() || requirementNode.asText().isBlank()) {
                continue;
            }
            String requirement = truncate(requirementNode.asText().strip());
            JsonNode reasonNode = node.get("reason");
            String reason = reasonNode != null && reasonNode.isTextual() && !reasonNode.asText().isBlank() ? reasonNode.asText().strip()
                    : "The brief names this requirement but no test appears to cover it.";
            findings.add(new SpecFidelityReport.Finding(SpecFidelityReport.Kind.UNCOVERED_REQUIREMENT, requirement,
                    "The brief names this requirement/edge-case but no test appears to cover it: " + reason
                            + " Add a test that asserts it (an untested promise lets a wrong solution pass)."));
        }
        return findings;
    }

    /** Extracts the first balanced {@code {...}} object from the text, tolerating leading/trailing prose or code fences, or {@code null} if there is none. */
    @Nullable
    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            }
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private static String truncate(String value) {
        return value.length() <= MAX_REQUIREMENT_CHARS ? value : value.substring(0, MAX_REQUIREMENT_CHARS) + "…";
    }

    @Nullable
    private static String extractText(@Nullable ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * Renders the critic's findings as a compact block for the verifier-feedback retry prompt, instructing the agent to add the missing tests / clean the leaked phrasing. Returns
     * an empty string when there is nothing to report, so the caller can append unconditionally.
     *
     * @param report the critic report
     * @return a retry-prompt fragment, or an empty string when there are no findings
     */
    public static String renderForRetryPrompt(SpecFidelityReport report) {
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
