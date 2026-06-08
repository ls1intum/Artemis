package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.chat.client.ChatClient;

import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.atlas.dto.FlavorStripEditsDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Flavor-strip-specific coverage for {@link ContentExtractionService#stripFlavorText(String)}
 * and the {@code extractContent(obj, applyFlavorStrip)} overload. Plain extraction happy paths
 * live in {@link ContentExtractionServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class ContentExtractionServiceFlavorStripTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private AtlasPromptTemplateService templateService;

    private ContentExtractionService service;

    @BeforeEach
    void setUp() {
        service = new ContentExtractionService(chatClient, templateService, "gpt-5.4-mini", "low", 1.0);
    }

    private void stubLlm(FlavorStripEditsDTO edits) {
        when(templateService.render(anyString(), any())).thenReturn("system prompt");
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any(AzureOpenAiChatOptions.class)).call().entity(eq(FlavorStripEditsDTO.class))).thenReturn(edits);
    }

    @Test
    void stripFlavorText_nullInput_returnsEmptyAndNeverCallsClient() {
        assertThat(service.stripFlavorText(null)).isEmpty();
        verifyNoInteractions(chatClient);
        verifyNoInteractions(templateService);
    }

    @Test
    void stripFlavorText_blankInput_returnsEmptyAndNeverCallsClient() {
        assertThat(service.stripFlavorText("   \n  ")).isEmpty();
        verifyNoInteractions(chatClient);
    }

    @Test
    void stripFlavorText_blankModel_returnsRawAndNeverCallsClient() {
        ContentExtractionService blankModelService = new ContentExtractionService(chatClient, templateService, "", "low", 1.0);

        assertThat(blankModelService.stripFlavorText("Keep this text.")).isEqualTo("Keep this text.");
        verifyNoInteractions(chatClient);
    }

    @Test
    void stripFlavorText_nullChatClient_returnsRawAndNeverCallsTemplateService() {
        ContentExtractionService noClientService = new ContentExtractionService(null, templateService, "gpt-5.4-mini", "low", 1.0);

        assertThat(noClientService.stripFlavorText("Keep this text.")).isEqualTo("Keep this text.");
        verifyNoInteractions(templateService);
    }

    @Test
    void stripFlavorText_applySearchReplace_keepsNonFlavorByteIdentical() {
        stubLlm(new FlavorStripEditsDTO(List.of(new FlavorStripEditsDTO.EditDTO("Narrative setup, not pedagogical.", "Alice dreams of socks. ", ""))));

        String result = service.stripFlavorText("Alice dreams of socks. The sum of 2 and 3 equals 5.");

        assertThat(result).isEqualTo("The sum of 2 and 3 equals 5.");
    }

    @Test
    void stripFlavorText_emptyEdits_returnsRaw() {
        stubLlm(new FlavorStripEditsDTO(List.of()));

        assertThat(service.stripFlavorText("Keep this.")).isEqualTo("Keep this.");
    }

    @Test
    void stripFlavorText_nullEditsList_returnsRaw() {
        stubLlm(new FlavorStripEditsDTO(null));

        assertThat(service.stripFlavorText("Keep this.")).isEqualTo("Keep this.");
    }

    @Test
    void stripFlavorText_llmThrows_returnsRaw() {
        when(templateService.render(anyString(), any())).thenReturn("system prompt");
        when(chatClient.prompt().system(anyString()).user(anyString()).options(any(AzureOpenAiChatOptions.class)).call().entity(eq(FlavorStripEditsDTO.class)))
                .thenThrow(new RuntimeException("LLM unreachable"));

        assertThat(service.stripFlavorText("Keep this.")).isEqualTo("Keep this.");
    }

    @Test
    void stripFlavorText_searchSpanNotFound_skipsEditAndReturnsRaw() {
        stubLlm(new FlavorStripEditsDTO(List.of(new FlavorStripEditsDTO.EditDTO("off-target", "this does not appear", ""))));

        assertThat(service.stripFlavorText("Real content stays intact.")).isEqualTo("Real content stays intact.");
    }

    @Test
    void stripFlavorText_normalizesWhitespaceAfterActualEdit() {
        stubLlm(new FlavorStripEditsDTO(List.of(new FlavorStripEditsDTO.EditDTO("remove Alice", "Alice.", ""))));

        String result = service.stripFlavorText("Alice.  \n\n\nKeep me.");

        // Trailing horizontal whitespace on the first line is stripped and the triple newline collapses to a double.
        assertThat(result).isEqualTo("\n\nKeep me.");
    }

    @Test
    void extractContent_withStripFlavorTextFalse_neverCallsClient() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle("Sort");
        exercise.setProblemStatement("Alice dreams of socks. The sum of 2 and 3 equals 5.");

        ExtractedContentDTO result = service.extractContent(exercise, false);

        assertThat(result.extractedLearningText()).isEqualTo("Alice dreams of socks. The sum of 2 and 3 equals 5.");
        verify(chatClient, never()).prompt();
    }
}
