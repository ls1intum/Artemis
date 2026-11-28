package de.tum.cit.aet.artemis.hyperion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import de.tum.cit.aet.artemis.core.config.llm.LlmPresetAssignment;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.config.llm.LlmTestCatalog;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;

class HyperionQuizQuestionPoolModelServiceTest {

    private final LlmClientService llmClientService = mock(LlmClientService.class);

    @Test
    void withoutPresetsThePoolIsOffAndNeedsNoAssignment() {
        HyperionQuizQuestionPoolModelService service = new HyperionQuizQuestionPoolModelService(llmClientService, "", " ");

        assertThat(service.isConfigured()).isFalse();
        assertThat(service.llmPresetAssignments()).isEmpty();
        assertThat(service.generatorModel()).isEmpty();
        assertThat(service.judgeModel()).isEmpty();
        assertThatThrownBy(service::generatorChatClient).isInstanceOf(ServiceUnavailableAlertException.class);
        assertThatThrownBy(service::judgeChatClient).isInstanceOf(ServiceUnavailableAlertException.class);
        verifyNoInteractions(llmClientService);
    }

    @Test
    void onePresetRequiresTheOther() {
        HyperionQuizQuestionPoolModelService service = new HyperionQuizQuestionPoolModelService(llmClientService, "local", "");

        assertThat(service.isConfigured()).isFalse();
        assertThat(service.llmPresetAssignments()).extracting(LlmPresetAssignment::propertyKey, LlmPresetAssignment::preset).containsExactly(
                org.assertj.core.groups.Tuple.tuple(HyperionQuizQuestionPoolModelService.GENERATOR_KEY, "local"),
                org.assertj.core.groups.Tuple.tuple(HyperionQuizQuestionPoolModelService.JUDGE_KEY, null));
        assertThatThrownBy(service::generatorChatClient).isInstanceOf(ServiceUnavailableAlertException.class);
    }

    @Test
    void bothPresetsGiveTheClientsAndModelsOfTheirPresets() {
        ChatClient generator = mock(ChatClient.class);
        ChatClient judge = mock(ChatClient.class);
        when(llmClientService.chatClient("local")).thenReturn(generator);
        when(llmClientService.chatClient("judge")).thenReturn(judge);
        when(llmClientService.preset("local")).thenReturn(LlmTestCatalog.preset("logos", "Qwen/Qwen3.8-27B", LlmProperties.Api.RESPONSES));
        when(llmClientService.preset("judge")).thenReturn(LlmTestCatalog.preset("logos", "openai/gpt-oss-120b", LlmProperties.Api.RESPONSES));

        HyperionQuizQuestionPoolModelService service = new HyperionQuizQuestionPoolModelService(llmClientService, " local ", "judge");

        assertThat(service.isConfigured()).isTrue();
        assertThat(service.llmPresetAssignments()).extracting(LlmPresetAssignment::preset).containsExactly("local", "judge");
        assertThat(service.generatorChatClient()).isSameAs(generator);
        assertThat(service.judgeChatClient()).isSameAs(judge);
        assertThat(service.generatorModel()).isEqualTo("Qwen/Qwen3.8-27B");
        assertThat(service.judgeModel()).isEqualTo("openai/gpt-oss-120b");
    }
}
