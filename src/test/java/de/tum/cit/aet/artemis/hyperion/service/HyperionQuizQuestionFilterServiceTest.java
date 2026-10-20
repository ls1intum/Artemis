package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionFailureMode;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizAnswerOptionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionFilterDecisionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionFilterVerdictDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;

class HyperionQuizQuestionFilterServiceTest {

    private static final String GROUNDING_BLOCK = "-----BEGIN UNTRUSTED INPUT-----\nPUT is idempotent.\n-----END UNTRUSTED INPUT-----";

    @Mock
    private ChatModel chatModel;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    private HyperionQuizQuestionFilterService service;

    private AutoCloseable mocks;

    private Course course;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        lenient().when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        service = new HyperionQuizQuestionFilterService(ChatClient.create(chatModel), new HyperionPromptTemplateService(), llmTokenUsageService, "");
        course = new Course();
        course.setId(42L);
        course.setTitle("Software Engineering");
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void evaluate_acceptsAQuestionWithoutDefects() {
        respondWith(verdict(Map.of()));

        Optional<QuizQuestionFilterDecisionDTO> decision = evaluate();

        assertThat(decision).isPresent();
        assertThat(decision.orElseThrow().accepted()).isTrue();
        assertThat(decision.orElseThrow().aggregateScore()).isEqualTo(1.0);
        assertThat(decision.orElseThrow().verdicts()).hasSize(QuizQuestionFailureMode.values().length);
        assertThat(decision.orElseThrow().rationale()).isEqualTo("checked");
        verify(llmTokenUsageService).trackChatResponseTokenUsage(any(ChatResponse.class), eq(LLMServiceType.HYPERION), eq("HYPERION_QUIZ_QUESTION_FILTER"), any());
    }

    @Test
    void evaluate_derivesTheAggregateFromTheWorstGatingSeverity() {
        respondWith(verdict(Map.of(QuizQuestionFailureMode.FACTUAL_ERROR, 0.5, QuizQuestionFailureMode.OFF_TOPIC, 0.2)));

        QuizQuestionFilterDecisionDTO decision = evaluate().orElseThrow();

        assertThat(decision.aggregateScore()).isCloseTo(0.5, within(1e-9));
        assertThat(decision.accepted()).isFalse();
    }

    @Test
    void evaluate_doesNotRejectOnNearDuplicateAlone() {
        respondWith(verdict(Map.of(QuizQuestionFailureMode.NEAR_DUPLICATE, 1.0)));

        QuizQuestionFilterDecisionDTO decision = evaluate().orElseThrow();

        assertThat(decision.accepted()).isTrue();
        assertThat(decision.aggregateScore()).isEqualTo(1.0);
        assertThat(verdictFor(decision, QuizQuestionFailureMode.NEAR_DUPLICATE).severity()).isEqualTo(1.0);
    }

    @Test
    void evaluate_acceptsAQuestionWhoseAggregateMeetsTheThreshold() {
        respondWith(verdict(Map.of(QuizQuestionFailureMode.ILL_FORMED_DISTRACTORS, 0.3)));

        QuizQuestionFilterDecisionDTO decision = evaluate().orElseThrow();

        assertThat(decision.accepted()).isTrue();
        assertThat(decision.aggregateScore()).isCloseTo(0.7, within(1e-9));
    }

    @Test
    void evaluate_recordsTheTriggeredFlagWithoutDecidingOnIt() {
        respondWith(verdict(Map.of(QuizQuestionFailureMode.FACTUAL_ERROR, 0.1), QuizQuestionFailureMode.FACTUAL_ERROR));

        QuizQuestionFilterDecisionDTO decision = evaluate().orElseThrow();

        assertThat(verdictFor(decision, QuizQuestionFailureMode.FACTUAL_ERROR).triggered()).isTrue();
        assertThat(decision.accepted()).isTrue();
    }

    @Test
    void evaluate_clampsSeveritiesOutsideTheUnitInterval() {
        respondWith(verdict(Map.of(QuizQuestionFailureMode.FACTUAL_ERROR, -3.0, QuizQuestionFailureMode.NEAR_DUPLICATE, 9.0)));

        QuizQuestionFilterDecisionDTO decision = evaluate().orElseThrow();

        assertThat(verdictFor(decision, QuizQuestionFailureMode.FACTUAL_ERROR).severity()).isEqualTo(0.0);
        assertThat(verdictFor(decision, QuizQuestionFailureMode.NEAR_DUPLICATE).severity()).isEqualTo(1.0);
    }

    @Test
    void evaluate_discardsAVerdictThatOmitsModes() {
        respondWith("""
                { "rationale": "partial", "modes": [
                  { "mode": "FACTUAL_ERROR", "severity": 0.0, "triggered": false, "justification": "fine" } ] }
                """);

        assertThat(evaluate()).isEmpty();
    }

    @Test
    void evaluate_discardsAVerdictWhoseEntriesCarryNoSeverity() {
        respondWith("""
                { "rationale": "scores under another name", "modes": [
                  { "mode": "FACTUAL_ERROR", "score": 0.9, "triggered": true, "justification": "wrong key" },
                  { "mode": "AMBIGUOUS_CORRECT_ANSWER", "score": 0.0, "triggered": false, "justification": "fine" },
                  { "mode": "OFF_TOPIC", "score": 0.0, "triggered": false, "justification": "fine" },
                  { "mode": "NEAR_DUPLICATE", "score": 0.0, "triggered": false, "justification": "fine" },
                  { "mode": "ILL_FORMED_DISTRACTORS", "score": 0.0, "triggered": false, "justification": "fine" } ] }
                """);

        assertThat(evaluate()).isEmpty();
    }

    @Test
    void evaluate_discardsAVerdictWithANullEntryInsteadOfFailing() {
        respondWith("""
                { "rationale": "one entry missing", "modes": [
                  null,
                  { "mode": "AMBIGUOUS_CORRECT_ANSWER", "severity": 0.0, "triggered": false, "justification": "fine" },
                  { "mode": "OFF_TOPIC", "severity": 0.0, "triggered": false, "justification": "fine" },
                  { "mode": "NEAR_DUPLICATE", "severity": 0.0, "triggered": false, "justification": "fine" },
                  { "mode": "ILL_FORMED_DISTRACTORS", "severity": 0.0, "triggered": false, "justification": "fine" } ] }
                """);

        assertThat(evaluate()).isEmpty();
    }

    @Test
    void evaluate_ignoresUnknownModesRatherThanFailing() {
        String json = verdict(Map.of()).replace("\"NEAR_DUPLICATE\"",
                "\"NEAR_DUPLICATE\", \"severity\": 0.0, \"triggered\": false, \"justification\": \"x\" }, { \"mode\": \"SOMETHING_ELSE\"");

        QuizQuestionFilterDecisionDTO decision = evaluateWith(json).orElseThrow();

        assertThat(decision.verdicts()).hasSize(QuizQuestionFailureMode.values().length);
    }

    @Test
    void evaluate_returnsEmptyWhenTheCallFails() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("no route to host"));

        assertThat(evaluate()).isEmpty();
    }

    @Test
    void evaluate_returnsEmptyWhenTheVerdictIsNotJson() {
        respondWith("I cannot judge this question.");

        assertThat(evaluate()).isEmpty();
    }

    @Test
    void evaluate_throwsWhenChatClientIsNull() {
        var serviceWithNullClient = new HyperionQuizQuestionFilterService(null, new HyperionPromptTemplateService(), llmTokenUsageService, "");

        assertThatThrownBy(() -> serviceWithNullClient.evaluate(course, 7L, question(), GROUNDING_BLOCK)).isInstanceOf(InternalServerErrorAlertException.class)
                .hasMessageContaining("AI chat client is not configured");
    }

    @Test
    void evaluate_sendsTheQuestionAndTheMaterialToTheModel() {
        respondWith(verdict(Map.of()));

        service.evaluate(course, 7L, question(), GROUNDING_BLOCK);

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        String userMessage = prompt.getValue().getInstructions().stream().filter(message -> message instanceof UserMessage).map(message -> message.getText()).findFirst()
                .orElseThrow();
        assertThat(userMessage).contains("PUT is idempotent.").contains("Question type: single-choice").contains("Which method is idempotent?").contains("- [correct] PUT")
                .contains("- [wrong] POST").contains("Stated explanation: PUT is idempotent.");
        assertThat(prompt.getValue().getOptions().getModel()).isNull();
    }

    @Test
    void evaluate_bindsTheConfiguredJudgeModelOntoTheRequest() {
        var serviceWithModel = new HyperionQuizQuestionFilterService(ChatClient.create(chatModel), new HyperionPromptTemplateService(), llmTokenUsageService, " judge-x ");
        respondWith(verdict(Map.of()));

        serviceWithModel.evaluate(course, 7L, question(), GROUNDING_BLOCK);

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertThat(prompt.getValue().getOptions().getModel()).isEqualTo("judge-x");
        assertThat(serviceWithModel.getJudgeModel()).isEqualTo("judge-x");
    }

    @Test
    void decide_rejectsWhenAGatingModeAloneIsSevere() {
        Map<QuizQuestionFailureMode, QuizQuestionFilterVerdictDTO> verdicts = new EnumMap<>(QuizQuestionFailureMode.class);
        for (QuizQuestionFailureMode mode : QuizQuestionFailureMode.values()) {
            verdicts.put(mode, new QuizQuestionFilterVerdictDTO(mode, mode == QuizQuestionFailureMode.OFF_TOPIC ? 0.6 : 0.0, false, "fine"));
        }

        QuizQuestionFilterDecisionDTO decision = HyperionQuizQuestionFilterService.decide(verdicts, 0.7, HyperionQuizQuestionFilterService.GATING_MODES, "r");

        assertThat(decision.accepted()).isFalse();
        assertThat(decision.aggregateScore()).isCloseTo(0.4, within(1e-9));
        assertThat(decision.verdicts()).extracting(QuizQuestionFilterVerdictDTO::mode).containsExactly(QuizQuestionFailureMode.values());
    }

    private Optional<QuizQuestionFilterDecisionDTO> evaluate() {
        return service.evaluate(course, 7L, question(), GROUNDING_BLOCK);
    }

    private Optional<QuizQuestionFilterDecisionDTO> evaluateWith(String json) {
        respondWith(json);
        return evaluate();
    }

    private void respondWith(String content) {
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(content)))));
    }

    private static QuizQuestionFilterVerdictDTO verdictFor(QuizQuestionFilterDecisionDTO decision, QuizQuestionFailureMode mode) {
        return decision.verdicts().stream().filter(verdict -> verdict.mode() == mode).findFirst().orElseThrow();
    }

    private static GeneratedQuizQuestionDTO question() {
        List<GeneratedQuizAnswerOptionDTO> options = List.of(new GeneratedQuizAnswerOptionDTO("PUT", true, null, null), new GeneratedQuizAnswerOptionDTO("POST", false, null, null),
                new GeneratedQuizAnswerOptionDTO("PATCH", false, null, null), new GeneratedQuizAnswerOptionDTO("GET", false, null, null));
        return new GeneratedQuizQuestionDTO(QuizQuestionGenerationType.SINGLE_CHOICE, "HTTP Methods", "Which method is idempotent?", options, null, "PUT is idempotent.");
    }

    private static String verdict(Map<QuizQuestionFailureMode, Double> severities) {
        return verdict(severities, null);
    }

    private static String verdict(Map<QuizQuestionFailureMode, Double> severities, QuizQuestionFailureMode triggered) {
        StringBuilder modes = new StringBuilder();
        for (QuizQuestionFailureMode mode : QuizQuestionFailureMode.values()) {
            if (!modes.isEmpty()) {
                modes.append(",\n");
            }
            modes.append("  { \"mode\": \"").append(mode.name()).append("\", \"severity\": ").append(severities.getOrDefault(mode, 0.0)).append(", \"triggered\": ")
                    .append(mode == triggered).append(", \"justification\": \"").append(mode.name().toLowerCase(Locale.ROOT)).append("\" }");
        }
        return "{ \"rationale\": \"checked\", \"modes\": [\n" + modes + "\n] }";
    }
}
