package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;

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

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.hyperion.dto.ProblemStatementRewriteResponseDTO;
import io.micrometer.observation.ObservationRegistry;

class HyperionProblemStatementRewriteServiceTest {

    @Mock
    private ChatModel chatModel;

    private HyperionProblemStatementRewriteService hyperionProblemStatementRewriteService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);
        var templateService = new HyperionPromptTemplateService();
        var observationRegistry = ObservationRegistry.create();
        this.hyperionProblemStatementRewriteService = new HyperionProblemStatementRewriteService(chatClient, templateService, observationRegistry);
    }

    @Test
    void rewriteProblemStatement_returnsText() throws Exception {
        String rewritten = "Rewritten statement";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(rewritten)))));

        var user = new User();
        var course = new Course();

        ProblemStatementRewriteResponseDTO resp = hyperionProblemStatementRewriteService.rewriteProblemStatement(course, "Original");
        assertThat(resp).isNotNull();
        assertThat(resp.improved()).isTrue();
        assertThat(resp.rewrittenText()).isEqualTo(rewritten);
    }
}
