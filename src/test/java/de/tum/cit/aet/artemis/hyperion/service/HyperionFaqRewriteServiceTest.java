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

    private static Faq existingFaq;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        ChatClient chatClient = ChatClient.create(chatModel);
        var templateService = new HyperionPromptTemplateService();
        var observationRegistry = ObservationRegistry.create();
        this.hyperionFaqRewriteService = new HyperionFaqRewriteService(faqRepository, chatClient, templateService, observationRegistry);

        existingFaq = new Faq();
        existingFaq.setId(100L);
        existingFaq.setQuestionTitle("Exam Date");
        existingFaq.setQuestionAnswer("The exam is on Monday.");
    }

    @Test
    void rewriteFaq_withInconsistencies() {
        long courseId = 1L;
        String originalText = "This is a bullet point text.";
        String rewrittenText = "This is a rewritten complete sentence.";

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

        // Rewrite and check consistency call was made
        verify(chatModel, times(2)).call(any(Prompt.class));
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
        assertThat(result.suggestions()).isEmpty();
        assertThat(result.improvement()).isEmpty();

        // Only rewrite call was made
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void rewriteFaq_onError_returnsFallback() {
        long courseId = 1L;
        String originalText = "This is a bullet point text.";

        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("AI Service Unavailable"));

        when(faqRepository.findAllByCourseIdOrderByCreatedDateDesc(courseId)).thenReturn(List.of(existingFaq));

        var result = hyperionFaqRewriteService.rewriteFaq(courseId, originalText);
        assertThat(result.rewrittenText()).isEqualTo(originalText);
        assertThat(result.inconsistencies()).isEmpty();
        assertThat(result.suggestions()).isEmpty();
        assertThat(result.improvement()).isEmpty();
    }

    @Test
    void rewriteFaq_invalidJsonResponse_returnsFallback() {
        long courseId = 1L;
        String rewrittenText = "Rewritten text.";

        // AI returns garbage instead of JSON
        String garbageResponse = "I am an AI and I refuse to use the format you requested.";

        when(chatModel.call(any(Prompt.class))).thenReturn(createChatResponse(rewrittenText)).thenReturn(createChatResponse(garbageResponse));

        when(faqRepository.findAllByCourseIdOrderByCreatedDateDesc(courseId)).thenReturn(List.of(existingFaq));

        var result = hyperionFaqRewriteService.rewriteFaq(courseId, "input");
        assertThat(result.rewrittenText()).isEqualTo(rewrittenText);
        assertThat(result.inconsistencies()).isEmpty();
        assertThat(result.suggestions()).isEmpty();
        assertThat(result.improvement()).isEmpty();
    }

    private ChatResponse createChatResponse(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
