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

class ProblemStatementRewriteServiceTest {

    @Mock
    private ChatModel chatModel;

    private ChatClient chatClient;

    private ProblemStatementRewriteService service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        this.chatClient = ChatClient.create(chatModel);
        var templateService = new PromptTemplateService();
        this.service = new ProblemStatementRewriteService(chatClient, templateService);
    }

    @Test
    void rewriteProblemStatement_returnsText() throws Exception {
        String rewritten = "Rewritten statement";
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> new ChatResponse(List.of(new Generation(new AssistantMessage(rewritten)))));

        var user = new User();
        var course = new Course();

        ProblemStatementRewriteResponseDTO resp = service.rewriteProblemStatement(user, course, "Original");
        assertThat(resp).isNotNull();
        assertThat(resp.improved()).isTrue();
        assertThat(resp.rewrittenText()).isEqualTo(rewritten);
    }
}
