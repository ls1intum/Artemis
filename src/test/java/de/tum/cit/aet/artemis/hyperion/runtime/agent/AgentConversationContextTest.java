package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

class AgentConversationContextTest {

    @Test
    void readsTheReportedPromptTokens() {
        assertThat(AgentConversationContext.promptTokensOf(response(new DefaultUsage(1200, 30)))).isEqualTo(1200);
    }

    @Test
    void countsAMissingPromptCountAsZeroEvenWhenAProviderBreaksTheNullContract() {
        Usage withoutPromptCount = new Usage() {

            @Override
            @SuppressWarnings("NullAway")
            public Integer getPromptTokens() {
                return null;
            }

            @Override
            public Integer getCompletionTokens() {
                return 30;
            }

            @Override
            public Object getNativeUsage() {
                return "";
            }
        };

        assertThat(AgentConversationContext.promptTokensOf(response(withoutPromptCount))).isZero();
        assertThat(AgentConversationContext.promptTokensOf(null)).isZero();
    }

    private static ChatResponse response(Usage usage) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("done"))), ChatResponseMetadata.builder().usage(usage).build());
    }
}
