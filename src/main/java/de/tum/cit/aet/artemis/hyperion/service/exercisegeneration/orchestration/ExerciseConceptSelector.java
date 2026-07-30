package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;

/**
 * Performs a small, in-memory discovery loop before SPEC.md exists. The generator authors three concepts and a context-separated reviewer selects one; neither rejected candidates
 * nor reviewer-authored replacement ideas become part of the exercise contract.
 */
@Lazy
@Component
@Conditional(HyperionExerciseGenerationEnabled.class)
public class ExerciseConceptSelector {

    private static final int MAX_BATCHES = 2;

    private static final Pattern CANDIDATE_PATTERN = Pattern.compile("(?ms)^## Candidate ([123])\\s*$\\R(.*?)(?=^## Candidate [123]\\s*$|\\z)");

    private static final String REPLACEMENT_FEEDBACK = """
            The previous batch failed at least one of brief coverage, learner-owned learning fit, objective-relative difficulty after incidental boilerplate is removed, domain
            grounding, or feasibility and proportionality. Generate genuinely different central interactions and satisfy every axis directly; do not rename or embellish a
            rejected idea.
            """;

    private static final String SYSTEM_PROMPT = """
            You are exploring concepts for a programming exercise before any specification or code is written. Produce ideas only; you have no tools and must not write files.
            The instructor brief is authoritative. Preserve every choice it fixes. Where it deliberately leaves a theme or other creative choice open, choose freely rather than
            inheriting one from a reference exercise or treating your choice as instructor-mandated.
            """;

    private static final String CANDIDATE_PROMPT = """
            Generate exactly three candidate realizations for the instructor brief below. When the brief leaves the central situation or behavior open, make them genuinely
            different concepts. When the brief already fixes those choices, preserve them and vary only coherent realization decisions the brief leaves open; never manufacture
            conceptual divergence by changing fixed requirements. Use exactly these headings in order: `## Candidate 1`, `## Candidate 2`, and `## Candidate 3`.

            Give facts, not a defense of the candidate. Under each heading use exactly these labels:
            Domain situation:
            Real constraint:
            Common caller goal:
            Student-owned objective:
            Student-owned reasoning:
            Alternative policies:
            Observable substitution:
            Likely supplied support:

            A concept chooses a qualitative domain interaction, not its specification. Unless the brief already supplies them, do not include numeric literals, enum-member lists,
            exact transition tables, class or member names, exception types, return sentinels, or required implementation constructs; the later SPEC stage owns those decisions.
            Keep each field concise. Student-owned objective is exhaustive: name every consequential behavior students implement, including concrete policies when students own
            them. Student-owned reasoning must state the concrete qualitative control flow or data transformation that remains after signatures and routine wiring are removed,
            without prematurely fixing exact formulas or constants. Generic phrases such as `distinct rules`, `processes the input`, or `computes a result` do not count.
            Anything named only under Alternative policies is behavior to describe, not proof that students implement it. When the brief requests interchangeable variants
            such as Strategy, students must own at least one collaboration seam—selection, injection, replacement, or delegation—in addition to any concrete policy bodies; do
            not assign that collaboration to likely supplied support. Likely supplied support may contain only incidental input data, setup, and infrastructure.
            When the brief requests interchangeable variants, Alternative policies must pursue the same caller goal for overlapping valid inputs and state the qualitative
            semantic difference between them; Observable substitution must explain what caller-visible behavior changes when one policy is replaced by another. Alternatives must
            implement the same caller-requested responsibility and preserve the semantic meaning of the result. Substitution may change the policy, trade-off, or concrete outcome,
            but it must not change the operation the caller intended to perform; a broad label such as "affect an object" does not make unrelated operations interchangeable
            policies. Otherwise, write `Not applicable` for those two fields and must not invent strategies or artificial variants.

            Preserve identifiers, constants, formulas, or validation explicitly required by the brief. Otherwise, do not prescribe exact class names, method signatures, formulas,
            constants, thresholds, coordinates, worked-example values, detailed tie rules, validation policies, or test-harness design. Those decisions belong to the later
            specification after one concept is selected. Do not claim that a candidate is intermediate, testable, or aligned with the objective; the reviewer will determine that
            independently. Do not add unrelated algorithms, validation, optimization, arithmetic, or state work merely to inflate difficulty. Reject noun-swapped textbook examples
            and scalar formulas disguised by themed labels. Do not choose a winner; a context-separated reviewer will select at most one.

            Before answering, scrub every candidate-authored literal, API name, exact label list, and required implementation construct that the brief did not supply. Merge claimed
            cases or boundaries whose complete caller-visible outcome and state transition are identical; a cut point that changes nothing is not a learner-owned decision.

            INSTRUCTOR BRIEF:
            """;

    private final AgentLoopRunner agentLoopRunner;

    private final SpecFidelityCriticService critic;

    public ExerciseConceptSelector(AgentLoopRunner agentLoopRunner, SpecFidelityCriticService critic) {
        this.agentLoopRunner = agentLoopRunner;
        this.critic = critic;
    }

    /**
     * Generates and reviews exercise concepts for an instructor brief.
     *
     * @param brief     the instructor brief
     * @param cancelled cooperative cancellation signal
     * @param usageSink optional token-usage sink
     * @param progress  optional progress sink
     * @return the selected concept and its review evidence, or an unsuccessful result
     */
    public ConceptSelection select(String brief, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink, @Nullable Consumer<String> progress) {
        return select(brief, "", cancelled, usageSink, progress);
    }

    /**
     * Generates and reviews exercise concepts, carrying property-level feedback from a rejected specification into a fresh concept batch.
     *
     * @param brief           the instructor brief
     * @param initialFeedback feedback that the next independent concept batch must address
     * @param cancelled       cooperative cancellation signal
     * @param usageSink       optional token-usage sink
     * @param progress        optional progress sink
     * @return the selected concept and its review evidence, or an unsuccessful result
     */
    public ConceptSelection select(String brief, String initialFeedback, BooleanSupplier cancelled, @Nullable Consumer<ChatResponse> usageSink,
            @Nullable Consumer<String> progress) {
        List<Message> transcript = new ArrayList<>();
        int turns = 0;
        String feedback = initialFeedback == null ? "" : initialFeedback.strip();
        StringBuilder audit = new StringBuilder();
        for (int attempt = 1; attempt <= MAX_BATCHES; attempt++) {
            if (cancelled.getAsBoolean()) {
                return new ConceptSelection(false, null, null, turns, transcript, "Concept discovery was cancelled.", audit.toString());
            }
            emit(progress, attempt == 1 ? "Exploring exercise concepts" : "Exploring new concepts after learning-fit review");
            String prompt = CANDIDATE_PROMPT + brief.strip();
            if (!feedback.isBlank()) {
                prompt += "\n\nPROPERTY-LEVEL REVIEW OF THE PREVIOUS BATCH:\n" + feedback + "\nGenerate a new independent batch. Do not recover or rename any previous candidate.";
            }
            AgentLoopRunner.AgentLoopSession session = agentLoopRunner.runTextSession(SYSTEM_PROMPT, null, prompt, 1, cancelled, usageSink, null);
            transcript.addAll(session.conversation());
            AgentLoopResult result = session.result();
            turns += result.turns();
            if (result.status() != AgentLoopResult.Status.COMPLETED) {
                return new ConceptSelection(false, null, null, turns, transcript, "Concept generation did not complete.", audit.toString());
            }
            Map<Integer, String> candidates = parseCandidates(result.finalMessage());
            if (candidates.size() != 3) {
                feedback = "The batch did not contain exactly three complete candidates under the required headings.";
                if (attempt == MAX_BATCHES) {
                    return new ConceptSelection(false, null, null, turns, transcript, feedback, audit.toString());
                }
                continue;
            }
            emit(progress, "Reviewing exercise concepts");
            SpecFidelityCriticService.ConceptSelectionReview review = critic.reviewConceptCandidates(brief, candidates, usageSink, cancelled);
            if (!review.auditSummary().isBlank()) {
                if (!audit.isEmpty()) {
                    audit.append("\n\n");
                }
                audit.append("# Batch ").append(attempt).append("\n\n").append(review.auditSummary());
            }
            if (!review.complete()) {
                return new ConceptSelection(false, null, null, turns, transcript, "Concept review was unavailable.", audit.toString());
            }
            if (review.accepted()) {
                String summary = progressSummary(review.feedback());
                emit(progress, "Selected concept " + review.selectedCandidate() + " after learning-fit review" + (summary.isBlank() ? "" : ": " + summary));
                return new ConceptSelection(true, review.selectedCandidate(), candidates.get(review.selectedCandidate()), turns, transcript, review.feedback(), audit.toString());
            }
            feedback = attempt == MAX_BATCHES ? review.feedback() : REPLACEMENT_FEEDBACK + "\n" + review.decisionSummary();
        }
        return new ConceptSelection(true, null, null, turns, transcript, feedback, audit.toString());
    }

    private static Map<Integer, String> parseCandidates(String response) {
        Map<Integer, String> candidates = new LinkedHashMap<>();
        Matcher matcher = CANDIDATE_PATTERN.matcher(response);
        int expectedNumber = 1;
        int matchCount = 0;
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            String body = matcher.group(2).strip();
            matchCount++;
            if (number != expectedNumber || body.isBlank() || candidates.putIfAbsent(number, "## Candidate " + number + "\n" + body) != null) {
                return Map.of();
            }
            expectedNumber++;
        }
        return matchCount == 3 ? Collections.unmodifiableMap(candidates) : Map.of();
    }

    private static void emit(@Nullable Consumer<String> progress, String message) {
        if (progress != null) {
            progress.accept(message);
        }
    }

    private static String progressSummary(String value) {
        String safe = value.replaceAll("[\\p{Cc}\\p{Cf}]", " ").replaceAll("\\s+", " ").strip();
        return safe.length() <= 180 ? safe : safe.substring(0, 177) + "...";
    }

    public record ConceptSelection(boolean complete, @Nullable Integer selectedCandidate, @Nullable String selectedConcept, int turns, List<Message> transcript, String feedback,
            String auditSummary) {

        public ConceptSelection {
            transcript = List.copyOf(transcript);
        }

        public ConceptSelection(boolean complete, @Nullable Integer selectedCandidate, @Nullable String selectedConcept, int turns, List<Message> transcript, String feedback) {
            this(complete, selectedCandidate, selectedConcept, turns, transcript, feedback, "");
        }

        public boolean accepted() {
            return complete && selectedConcept != null && !selectedConcept.isBlank();
        }
    }
}
