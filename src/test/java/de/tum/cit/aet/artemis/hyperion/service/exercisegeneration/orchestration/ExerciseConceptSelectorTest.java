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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopResult;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentLoopRunner;
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
                .doesNotContain("Every candidate is scalar formula transcription.", "clock repair", "potion scoring", "fragmented radio transmissions");
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
}
