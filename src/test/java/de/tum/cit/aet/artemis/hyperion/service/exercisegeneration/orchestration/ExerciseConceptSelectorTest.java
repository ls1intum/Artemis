package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationUsageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.ProviderUsageSink;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.ProviderFailureCooldown;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;

class ExerciseConceptSelectorTest {

    private static final String THREE_CANDIDATES = """
            ## Candidate 1
            Theme: clock repair
            Central interaction: each strategy returns a fixed scalar estimate.

            ## Candidate 2
            Theme: restoring fragmented radio transmissions
            Central interaction: each strategy reconstructs an ordered message from overlapping, duplicated fragments using a different domain-motivated conflict policy.

            ## Candidate 3
            Theme: potion scoring
            Central interaction: each strategy multiplies one input by a different constant.
            """;

    @Test
    void returnsOnlyTheReviewerSelectedGeneratorAuthoredCandidate() {
        AgentLoopRunner loop = mock(AgentLoopRunner.class);
        SpecFidelityCriticService critic = mock(SpecFidelityCriticService.class);
        List<Message> conversation = List.of(new AssistantMessage(THREE_CANDIDATES));
        when(loop.runTextSession(anyString(), eq(null), anyString(), eq(1), any(), any(), any()))
                .thenReturn(new AgentLoopRunner.AgentLoopSession(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, THREE_CANDIDATES), conversation));
        when(critic.reviewConceptCandidates(eq("RAW BRIEF"), anyMap(), any(), any()))
                .thenReturn(new SpecFidelityCriticService.ConceptSelectionReview(true, 2, List.of(), "Candidate 2 is selected.", "Selected candidate: 2"));

        ExerciseConceptSelector.ConceptSelection result = new ExerciseConceptSelector(loop, critic).select("RAW BRIEF", () -> false, null, null);

        assertThat(result.accepted()).isTrue();
        assertThat(result.selectedConcept()).contains("## Candidate 2", "restoring fragmented radio transmissions").doesNotContain("## Candidate 1", "## Candidate 3",
                "clock repair", "potion scoring");
        assertThat(result.turns()).isEqualTo(1);
        assertThat(result.transcript()).containsExactlyElementsOf(conversation);
        assertThat(result.auditSummary()).contains("Batch 1", "Selected candidate: 2");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(loop).runTextSession(anyString(), eq(null), prompt.capture(), eq(1), any(), any(), any());
        assertThat(prompt.getValue())
                .contains("Domain situation:", "Real constraint:", "Common caller goal:", "Student-owned objective:", "Alternative policies:", "Observable substitution:",
                        "Student-owned reasoning:", "Likely supplied support:")
                .contains("selection, injection, replacement, or delegation", "students implement", "same caller goal", "overlapping valid inputs")
                .contains("Student-owned objective is exhaustive", "every consequential behavior students implement", "not proof that students implement it")
                .contains("concrete qualitative", "decision dependencies or data transformation", "one viable control flow", "must not require its syntax", "distinct rules",
                        "do not count")
                .contains("When the brief requests interchangeable variants", "write `Not applicable`", "must not invent strategies")
                .contains("same caller-requested responsibility", "semantic meaning of the result", "must not change the operation")
                .contains("do not prescribe exact class names", "method signatures", "formulas", "worked-example values")
                .contains("choose a qualitative domain", "must not prescribe", "closed label list", "domain pressure")
                .contains("semantic difference", "later", "specification", "facts, not a defense")
                // The rules above forbid prescribing counts and partitions, which a brief asking for several behaviors over more than one kind of input reads as licence to
                // narrow. Scope the brief states is exempt, or the generator scrubs away the coverage it is being judged on.
                .contains("never to scope the brief states", "How many behaviors students implement", "Covering less than the brief asks for is")
                .doesNotContain("specify enough deterministic decision logic", "tie behavior", "justify why the candidate is intermediate", "Dijkstra", "routing");
    }

    @Test
    void rejectionStartsASecondIndependentBatchWithPropertyLevelFeedback() {
        AgentLoopRunner loop = mock(AgentLoopRunner.class);
        SpecFidelityCriticService critic = mock(SpecFidelityCriticService.class);
        String replacementCandidates = THREE_CANDIDATES.replace("clock repair", "archive repair").replace("potion scoring", "reef survey");
        when(loop.runTextSession(anyString(), eq(null), anyString(), eq(1), any(), any(), any())).thenReturn(
                new AgentLoopRunner.AgentLoopSession(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, THREE_CANDIDATES), List.of(new AssistantMessage(THREE_CANDIDATES))),
                new AgentLoopRunner.AgentLoopSession(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, replacementCandidates),
                        List.of(new AssistantMessage(replacementCandidates))));
        when(critic.reviewConceptCandidates(eq("RAW BRIEF"), anyMap(), any(), any())).thenReturn(
                new SpecFidelityCriticService.ConceptSelectionReview(true, null, List.of("Every candidate is scalar formula transcription.")),
                new SpecFidelityCriticService.ConceptSelectionReview(true, 2, List.of()));

        ExerciseConceptSelector.ConceptSelection result = new ExerciseConceptSelector(loop, critic).select("RAW BRIEF", () -> false, null, null);

        assertThat(result.accepted()).isTrue();
        assertThat(result.turns()).isEqualTo(2);
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(loop, times(2)).runTextSession(anyString(), eq(null), prompts.capture(), anyInt(), any(), any(), any());
        assertThat(prompts.getAllValues().get(1)).contains("learner-owned learning fit", "objective-relative difficulty", "domain", "grounding")
                .contains("Preserve a sound central interaction", "leave that detail open", "only when the interaction itself failed")
                // A coverage failure is the one case where preserving the central interaction is wrong: the replacement has to widen, not re-theme.
                .contains("A brief-coverage failure is none of those", "must carry the omitted scope", "Narrowing again")
                // The replacement batch is told what the reviewer actually objected to. Without it the generator sees only that one of six axes failed, which is what let a
                // rejected batch come back narrowed the same way. The reviewer diagnoses properties only, so the reason travels without a replacement design.
                .contains("Every candidate is scalar formula transcription.").doesNotContain("clock repair", "potion scoring", "fragmented radio transmissions");
    }

    @Test
    void aCompletelyRejectedExplorationStillOffersTheCandidateItRejectedLeast() {
        AgentLoopRunner loop = mock(AgentLoopRunner.class);
        SpecFidelityCriticService critic = mock(SpecFidelityCriticService.class);
        when(loop.runTextSession(anyString(), eq(null), anyString(), eq(1), any(), any(), any()))
                .thenReturn(new AgentLoopRunner.AgentLoopSession(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, THREE_CANDIDATES), List.of()));
        when(critic.reviewConceptCandidates(eq("RAW BRIEF"), anyMap(), any(), any())).thenReturn(new SpecFidelityCriticService.ConceptSelectionReview(true, null,
                List.of("Candidate 2: difficulty — one reconciliation step"), "", "", new SpecFidelityCriticService.ConceptFallback(2, 1)));

        ExerciseConceptSelector.ConceptSelection result = new ExerciseConceptSelector(loop, critic).select("RAW BRIEF", () -> false, null, null);

        // The verdict stays a rejection; what is added is a usable starting point plus the exact objections raised against it.
        assertThat(result.accepted()).isFalse();
        assertThat(result.fallback()).isNotNull();
        assertThat(result.fallback().candidate()).isEqualTo(2);
        assertThat(result.fallback().concept()).contains("## Candidate 2", "restoring fragmented radio transmissions").doesNotContain("## Candidate 1", "## Candidate 3");
        assertThat(result.fallback().findings()).containsExactly("Candidate 2: difficulty — one reconciliation step");
    }

    @Test
    void theLeastRejectedCandidateAcrossBothBatchesWins() {
        // Rejection is not monotonic across batches: a replacement batch can come back worse, so offering the latest batch's best would discard a better draft already in hand.
        String replacementCandidates = THREE_CANDIDATES.replace("clock repair", "tide charts");
        AgentLoopRunner loop = mock(AgentLoopRunner.class);
        SpecFidelityCriticService critic = mock(SpecFidelityCriticService.class);
        when(loop.runTextSession(anyString(), eq(null), anyString(), eq(1), any(), any(), any())).thenReturn(
                new AgentLoopRunner.AgentLoopSession(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, THREE_CANDIDATES), List.of()),
                new AgentLoopRunner.AgentLoopSession(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, replacementCandidates), List.of()));
        when(critic.reviewConceptCandidates(eq("RAW BRIEF"), anyMap(), any(), any())).thenReturn(
                new SpecFidelityCriticService.ConceptSelectionReview(true, null, List.of("batch one objection"), "", "", new SpecFidelityCriticService.ConceptFallback(2, 1)),
                new SpecFidelityCriticService.ConceptSelectionReview(true, null, List.of("batch two objection"), "", "", new SpecFidelityCriticService.ConceptFallback(1, 4)));

        ExerciseConceptSelector.ConceptSelection result = new ExerciseConceptSelector(loop, critic).select("RAW BRIEF", () -> false, null, null);

        assertThat(result.accepted()).isFalse();
        assertThat(result.fallback().failedRequiredAxes()).isEqualTo(1);
        assertThat(result.fallback().concept()).contains("restoring fragmented radio transmissions");
        assertThat(result.fallback().findings()).containsExactly("batch one objection");
    }

    @Test
    void aBetterSecondBatchReplacesTheFirstBatchesFallback() {
        String replacementCandidates = THREE_CANDIDATES.replace("clock repair", "tide charts");
        AgentLoopRunner loop = mock(AgentLoopRunner.class);
        SpecFidelityCriticService critic = mock(SpecFidelityCriticService.class);
        when(loop.runTextSession(anyString(), eq(null), anyString(), eq(1), any(), any(), any())).thenReturn(
                new AgentLoopRunner.AgentLoopSession(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, THREE_CANDIDATES), List.of()),
                new AgentLoopRunner.AgentLoopSession(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, replacementCandidates), List.of()));
        when(critic.reviewConceptCandidates(eq("RAW BRIEF"), anyMap(), any(), any())).thenReturn(
                new SpecFidelityCriticService.ConceptSelectionReview(true, null, List.of("batch one objection"), "", "", new SpecFidelityCriticService.ConceptFallback(3, 5)),
                new SpecFidelityCriticService.ConceptSelectionReview(true, null, List.of("batch two objection"), "", "", new SpecFidelityCriticService.ConceptFallback(1, 2)));

        ExerciseConceptSelector.ConceptSelection result = new ExerciseConceptSelector(loop, critic).select("RAW BRIEF", () -> false, null, null);

        assertThat(result.fallback().failedRequiredAxes()).isEqualTo(2);
        assertThat(result.fallback().concept()).contains("tide charts");
        assertThat(result.fallback().findings()).containsExactly("batch two objection");
    }

    @Test
    void anIncompleteConceptReviewOffersNoFallbackBecauseNoCandidateWasEverJudged() {
        // A completed rejection is a design objection worth proceeding past; a review that never returned a verdict has judged nothing.
        AgentLoopRunner loop = mock(AgentLoopRunner.class);
        SpecFidelityCriticService critic = mock(SpecFidelityCriticService.class);
        when(loop.runTextSession(anyString(), eq(null), anyString(), eq(1), any(), any(), any()))
                .thenReturn(new AgentLoopRunner.AgentLoopSession(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, THREE_CANDIDATES), List.of()));
        when(critic.reviewConceptCandidates(eq("RAW BRIEF"), anyMap(), any(), any())).thenReturn(new SpecFidelityCriticService.ConceptSelectionReview(false, null, List.of()));

        ExerciseConceptSelector.ConceptSelection result = new ExerciseConceptSelector(loop, critic).select("RAW BRIEF", () -> false, null, null);

        assertThat(result.complete()).isFalse();
        assertThat(result.fallback()).isNull();
    }

    @Test
    void anAcceptedConceptOffersNoFallbackBecauseThereIsNothingToFallBackFrom() {
        AgentLoopRunner loop = mock(AgentLoopRunner.class);
        SpecFidelityCriticService critic = mock(SpecFidelityCriticService.class);
        when(loop.runTextSession(anyString(), eq(null), anyString(), eq(1), any(), any(), any()))
                .thenReturn(new AgentLoopRunner.AgentLoopSession(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, THREE_CANDIDATES), List.of()));
        when(critic.reviewConceptCandidates(eq("RAW BRIEF"), anyMap(), any(), any()))
                .thenReturn(new SpecFidelityCriticService.ConceptSelectionReview(true, 2, List.of(), "Candidate 2 is selected.", "Selected candidate: 2"));

        ExerciseConceptSelector.ConceptSelection result = new ExerciseConceptSelector(loop, critic).select("RAW BRIEF", () -> false, null, null);

        assertThat(result.accepted()).isTrue();
        assertThat(result.fallback()).isNull();
    }

    @Test
    void malformedFinalBatchIsNotReportedAsACompletedRejection() {
        AgentLoopRunner loop = mock(AgentLoopRunner.class);
        SpecFidelityCriticService critic = mock(SpecFidelityCriticService.class);
        when(loop.runTextSession(anyString(), eq(null), anyString(), eq(1), any(), any(), any())).thenReturn(
                new AgentLoopRunner.AgentLoopSession(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, THREE_CANDIDATES), List.of()),
                new AgentLoopRunner.AgentLoopSession(new AgentLoopResult(AgentLoopResult.Status.COMPLETED, 1, "## Candidate 1\nIncomplete"), List.of()));
        when(critic.reviewConceptCandidates(eq("RAW BRIEF"), anyMap(), any(), any()))
                .thenReturn(new SpecFidelityCriticService.ConceptSelectionReview(true, null, List.of("No candidate passed.")));

        ExerciseConceptSelector.ConceptSelection result = new ExerciseConceptSelector(loop, critic).select("RAW BRIEF", () -> false, null, null);

        assertThat(result.complete()).isFalse();
        assertThat(result.accepted()).isFalse();
        assertThat(result.feedback()).contains("did not contain exactly three complete candidates");
    }

    @Test
    void conceptAdmissionRejection_stillReportsTheAgentTurnsItSpent() throws Exception {
        // The run is rejected before any attempt loop starts, so nothing downstream produces an outcome carrying a turn count. The real agent loop and usage accumulator are wired
        // in so the whole push path is covered rather than a single link of it.
        HazelcastInstance hazelcastInstance = Hazelcast
                .newHazelcastInstance(new Config().setClusterName("hyperion-concept-turn-accounting-" + System.nanoTime()).setProperty("hazelcast.phone.home.enabled", "false"));
        try {
            hazelcastInstance.getConfig().getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
            GenerationJobReplayStore replayStore = new GenerationJobReplayStore(HyperionDistributedDataTestProvider.provider(hazelcastInstance), Duration.ofHours(4));
            long exerciseId = 900L;
            String jobId = "concept-rejected";
            hazelcastInstance.getMap("hyperion-exercise-generation-jobs").set(String.valueOf(exerciseId),
                    new GenerationJobService.JobInfo(jobId, "owner", exerciseId, Instant.now(), null, "node", null, true, null));
            replayStore.initializeStart(exerciseId, jobId, "owner", GenerationMode.GENERATE, null);

            ChatModel chatModel = mock(ChatModel.class);
            when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(THREE_CANDIDATES)))));
            AgentLoopRunner realLoop = new AgentLoopRunner(List.of(chatModel), 128_000, Duration.ofMinutes(5), new NoOpProviderFailureCooldown());
            SpecFidelityCriticService critic = mock(SpecFidelityCriticService.class);
            // Every batch is rejected, so no concept is ever admitted and the run stops at the gate.
            when(critic.reviewConceptCandidates(eq("RAW BRIEF"), anyMap(), any(), any()))
                    .thenReturn(new SpecFidelityCriticService.ConceptSelectionReview(true, null, List.of("No candidate passed.")));
            ProviderUsageSink usageSink = new ProviderUsageSink() {

                @Override
                public void accept(ChatResponse response) {
                }

                @Override
                public void recordToolCalls(long count) {
                }

                @Override
                public void recordTurn() {
                    replayStore.recordAgentTurn(jobId);
                }

                @Override
                public void markUncertain() {
                }
            };

            ExerciseConceptSelector.ConceptSelection selection = new ExerciseConceptSelector(realLoop, critic).select("RAW BRIEF", () -> false, usageSink, null);

            assertThat(selection.accepted()).isFalse();
            ExerciseGenerationUsageDTO usage = replayStore.usageSnapshot(jobId).usage();
            assertThat(usage).isNotNull();
            assertThat(usage.agentTurns()).as("a run abandoned at the concept gate must still report the turns it spent").isEqualTo(selection.turns()).isPositive();
        }
        finally {
            hazelcastInstance.shutdown();
        }
    }

    private static final class NoOpProviderFailureCooldown implements ProviderFailureCooldown {

        @Override
        public Instant cooldownUntil(String key) {
            return null;
        }

        @Override
        public void startCooldown(String key, Instant until) {
        }
    }
}
