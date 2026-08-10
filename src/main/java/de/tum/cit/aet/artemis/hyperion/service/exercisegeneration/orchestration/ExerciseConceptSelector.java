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
            grounding, feasibility and proportionality, or concept-level precision. Preserve a sound central interaction when the defect is only an exact value, API, label set,
            validation rule, or implementation mandate that belongs in the later specification; leave that detail open instead of merely renaming it. Generate a genuinely
            different central interaction only when the interaction itself failed learning fit, grounding, equivalence, or feasibility.
            A brief-coverage failure is none of those: it says the concept left out work the brief asks for, so the replacement must carry the omitted scope. Read what the
            coverage finding says is missing and widen the candidate to cover it — a brief asking for several behaviors over more than one kind of input is not covered by one
            behavior over one of them. Narrowing again, or restating the same scope under a new theme, repeats the failure.
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
            An open brief expects you to choose a qualitative domain, caller goal, and semantic outcome roles. Such roles may explain what outcomes mean, but must not prescribe
            the exact returned strings, a closed label list, boundary count, or API. Real constraint must be a domain pressure that makes the behavior natural, never an invented
            programming-technique mandate.
            Keep each field concise. Student-owned objective is exhaustive: name every consequential behavior students implement, including concrete policies when students own
            them, but describe behavior dimensions rather than specification-owned values, names, or partitions. Student-owned reasoning must state the concrete qualitative
            decision dependencies or data transformation that remains after signatures and routine wiring are removed, without prematurely fixing exact formulas or constants.
            It may illustrate one viable control flow, but must not require its syntax, comparison order, or construct when another implementation has the same public behavior.
            Generic phrases such as `distinct rules`, `processes the input`, or `computes a result` do not count.
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
            Scrubbing and merging apply to detail you invented, never to scope the brief states. How many behaviors students implement and which kinds of input they cover are
            choices the brief may fix, and where it fixes them they are preserved in full even though they read as counts and partitions. Covering less than the brief asks for is
            a coverage failure, not the concise concept the rules above ask for.

            INSTRUCTOR BRIEF:
            """;

    private final AgentLoopRunner agentLoopRunner;

    private final SpecFidelityCriticService critic;

    public ExerciseConceptSelector(AgentLoopRunner agentLoopRunner, SpecFidelityCriticService critic) {
        this.agentLoopRunner = agentLoopRunner;
        this.critic = critic;
    }

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
        // The best-scoring rejected candidate seen so far, across every batch this call explores. Rejection is not monotonic across batches — a replacement batch generated from
        // review feedback can be worse than the one it replaced — so the caller is offered the best, not the most recent.
        ConceptFallback fallback = null;
        for (int attempt = 1; attempt <= MAX_BATCHES; attempt++) {
            if (cancelled.getAsBoolean()) {
                return new ConceptSelection(false, null, null, turns, transcript, "Concept discovery was cancelled.", audit.toString(), fallback);
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
                return new ConceptSelection(false, null, null, turns, transcript, "Concept generation did not complete.", audit.toString(), fallback);
            }
            Map<Integer, String> candidates = parseCandidates(result.finalMessage());
            if (candidates.size() != 3) {
                feedback = "The batch did not contain exactly three complete candidates under the required headings.";
                if (attempt == MAX_BATCHES) {
                    return new ConceptSelection(false, null, null, turns, transcript, feedback, audit.toString(), fallback);
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
                return new ConceptSelection(false, null, null, turns, transcript, "Concept review was unavailable.", audit.toString(), fallback);
            }
            if (review.accepted()) {
                String summary = progressSummary(review.feedback());
                emit(progress, "Selected concept " + review.selectedCandidate() + " after learning-fit review" + (summary.isBlank() ? "" : ": " + summary));
                return new ConceptSelection(true, review.selectedCandidate(), candidates.get(review.selectedCandidate()), turns, transcript, review.feedback(), audit.toString());
            }
            fallback = better(fallback, review, candidates);
            // Both branches carry the review's concrete per-candidate diagnosis: the last batch as the run's rejection reason, an earlier one as the next batch's guidance. A
            // replacement batch told only that "the previous batch failed at least one of" six axes cannot redirect its search and reliably repeats the defect it was replacing.
            // The reviewer diagnoses properties and never authors a replacement design, so this hands the generator the reason without handing it an idea.
            feedback = attempt == MAX_BATCHES ? review.feedback() : REPLACEMENT_FEEDBACK + "\n" + review.feedback();
        }
        return new ConceptSelection(true, null, null, turns, transcript, feedback, audit.toString(), fallback);
    }

    /**
     * Keeps the better of the incumbent fallback and this batch's least-rejected candidate: fewer failed required axes wins, and an incumbent is kept on a tie so the earliest
     * batch is preferred. Every objection this review raised travels with the candidate, whichever way the comparison goes.
     */
    private static @Nullable ConceptFallback better(@Nullable ConceptFallback incumbent, SpecFidelityCriticService.ConceptSelectionReview review, Map<Integer, String> candidates) {
        SpecFidelityCriticService.ConceptFallback reviewed = review.fallback();
        if (reviewed == null) {
            return incumbent;
        }
        String concept = candidates.get(reviewed.candidate());
        if (concept == null || concept.isBlank()) {
            return incumbent;
        }
        if (incumbent != null && incumbent.failedRequiredAxes() <= reviewed.failedRequiredAxes()) {
            return incumbent;
        }
        return new ConceptFallback(concept, reviewed.candidate(), reviewed.failedRequiredAxes(), review.findings());
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

    /**
     * The best concept a completed review rejected, together with the verbatim findings that rejected it.
     * <p>
     * Offered so a caller facing "no candidate was admissible" can proceed with a draft plus its objections instead of producing no artifacts at all. It carries no verdict:
     * {@link ConceptSelection#accepted()} stays false, and {@code failedRequiredAxes} only ever picks between candidates, never admits one.
     */
    public record ConceptFallback(String concept, int candidate, int failedRequiredAxes, List<String> findings) {

        public ConceptFallback {
            findings = List.copyOf(findings);
        }
    }

    public record ConceptSelection(boolean complete, @Nullable Integer selectedCandidate, @Nullable String selectedConcept, int turns, List<Message> transcript, String feedback,
            String auditSummary, @Nullable ConceptFallback fallback) {

        public ConceptSelection {
            transcript = List.copyOf(transcript);
        }

        public ConceptSelection(boolean complete, @Nullable Integer selectedCandidate, @Nullable String selectedConcept, int turns, List<Message> transcript, String feedback,
                String auditSummary) {
            this(complete, selectedCandidate, selectedConcept, turns, transcript, feedback, auditSummary, null);
        }

        public ConceptSelection(boolean complete, @Nullable Integer selectedCandidate, @Nullable String selectedConcept, int turns, List<Message> transcript, String feedback) {
            this(complete, selectedCandidate, selectedConcept, turns, transcript, feedback, "", null);
        }

        public boolean accepted() {
            return complete && selectedConcept != null && !selectedConcept.isBlank();
        }
    }
}
