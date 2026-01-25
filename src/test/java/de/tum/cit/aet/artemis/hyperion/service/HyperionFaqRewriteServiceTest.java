package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.hyperion.dto.RewriteFaqResponseDTO;
import io.micrometer.observation.ObservationRegistry;

class HyperionFaqRewriteServiceTest {

    @Mock
    private FaqRepository faqRepository;

    @Mock
    private ChatModel chatModel;

    private HyperionFaqRewriteService hyperionFaqRewriteService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);
        var templateService = new HyperionPromptTemplateService();
        var observationRegistry = ObservationRegistry.create();
        this.hyperionFaqRewriteService = new HyperionFaqRewriteService(faqRepository, chatClient, templateService, observationRegistry);
    }

    @Test
    void rewriteFaq_withInconsistencies() {
        long courseId = 1L;
        String originalText = "This is a bullet point text.";
        String rewrittenText = "This is a rewritten complete sentence.";

        Faq existingFaq = new Faq();
        existingFaq.setId(100L);
        existingFaq.setQuestionTitle("Exam Date");
        existingFaq.setQuestionAnswer("The exam is on Monday.");

        String consistencyJsonResponse = """
                {
                  "type": "INCONSISTENT",
                  "message": "Contradicts existing exam info.",
                  "faqs": [{
                      "faq_id": 100,
                      "faq_question_title": "Exam Date",
                      "faq_question_answer": "The exam is on Monday."
                  }],
                  "suggestions": ["Change the date to Monday."],
                  "improved_version": "The exam is actually on Monday."
                }
                """;

        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(rewrittenText)).thenReturn(createChatResponse(consistencyJsonResponse));

        when(faqRepository.findAllByCourseIdOrderByCreatedDateDesc(courseId)).thenReturn(List.of(existingFaq));

        RewriteFaqResponseDTO resp = hyperionFaqRewriteService.rewriteFaq(courseId, originalText);
        assertThat(resp).isNotNull();
        assertThat(resp.rewrittenText()).isEqualTo(rewrittenText);
        assertThat(resp.inconsistencies()).hasSize(1);
        assertThat(resp.inconsistencies().getFirst()).contains("FAQ ID: 100");
        assertThat(resp.suggestions()).contains("Change the date to Monday.");
        assertThat(resp.improvement()).isEqualTo("The exam is actually on Monday.");
    }

    @Test
    void rewriteFaq_noFaqs_returnsOnlyRewrittenText() {
        long courseId = 1L;
        String originalText = "This is a bullet point text.";
        String rewrittenText = "This is a rewritten complete sentence.";

        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(rewrittenText));
        when(faqRepository.findAllByCourseIdOrderByCreatedDateDesc(courseId)).thenReturn(List.of());

        var result = hyperionFaqRewriteService.rewriteFaq(courseId, originalText);

        assertThat(result.rewrittenText()).isEqualTo(rewrittenText);
        assertThat(result.inconsistencies()).isEmpty();
        // Verifies the second AI call was never made because faqs were empty
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    private ChatResponse createChatResponse(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
