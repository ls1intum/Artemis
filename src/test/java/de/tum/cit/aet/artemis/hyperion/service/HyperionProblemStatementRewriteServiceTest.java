package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import de.tum.cit.aet.artemis.core.config.LLMModelCostConfiguration;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import io.micrometer.observation.ObservationRegistry;

class HyperionProblemStatementRewriteServiceTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    @Mock
    private UserRepository userRepository;

    private HyperionProblemStatementRewriteService hyperionProblemStatementRewriteService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);
        var templateService = new HyperionPromptTemplateService();
        var costConfiguration = createTestConfiguration();
        var llmTokenUsageService = new LLMTokenUsageService(llmTokenUsageTraceRepository, llmTokenUsageRequestRepository, costConfiguration);
        var observationRegistry = ObservationRegistry.create();
        this.hyperionProblemStatementRewriteService = new HyperionProblemStatementRewriteService(chatClient, templateService, observationRegistry, llmTokenUsageService,
                userRepository);
    }

    @Test
    void rewriteProblemStatement_returnsText() {
        String rewritten = "Rewritten statement";
        when(chatModel.call(any(Prompt.class))).thenAnswer(_ -> new ChatResponse(List.of(new Generation(new AssistantMessage(rewritten)))));

        var course = new Course();

        ProblemStatementRewriteResponseDTO resp = hyperionProblemStatementRewriteService.rewriteProblemStatement(course, "Original");
        assertThat(resp).isNotNull();
        assertThat(resp.improved()).isTrue();
        assertThat(resp.rewrittenText()).isEqualTo(rewritten);
    }

    private static LLMModelCostConfiguration createTestConfiguration() {
        var config = new LLMModelCostConfiguration();
        var modelCosts = Map.of("gpt-5-mini", createModelCostProperties(0.23f, 1.84f));
        config.setModelCosts(new HashMap<>(modelCosts));
        return config;
    }

    private static LLMModelCostConfiguration.ModelCostProperties createModelCostProperties(float inputEur, float outputEur) {
        var props = new LLMModelCostConfiguration.ModelCostProperties();
        props.setInputCostPerMillionEur(inputEur);
        props.setOutputCostPerMillionEur(outputEur);
        return props;
    }
}
