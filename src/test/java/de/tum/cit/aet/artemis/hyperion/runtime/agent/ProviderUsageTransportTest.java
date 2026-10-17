package de.tum.cit.aet.artemis.hyperion.runtime.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;

import de.tum.cit.aet.artemis.hyperion.protocol.ProviderUsageUpdate;

class ProviderUsageTransportTest {

    @Test
    void preservesReportedMetadataWithoutFabricatingContent() {
        var call = new ProviderUsageUpdate.Call("model", "provider-id", 123, 45, 12L);
        var response = ProviderUsageTransport.restore(call);
        assertThat(response.getResults()).isEmpty();
        assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(168);
        assertThat(response.getMetadata().getUsage().getNativeUsage()).isNull();
        assertThat(ProviderUsageTransport.capture(response)).isEqualTo(call);
    }

    @Test
    void missingCacheCountRemainsUnknown() {
        var call = new ProviderUsageUpdate.Call("model", "provider-id", 10, 20, null);
        assertThat(ProviderUsageTransport.capture(ProviderUsageTransport.restore(call)).cachedInputTokens()).isNull();
    }

    @Test
    void rejectsMissingResponseRatherThanInventingZeroUsage() {
        assertThatThrownBy(() -> ProviderUsageTransport.capture(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingModelNameDoesNotDiscardReportedTokenCounts() {
        var metadata = ChatResponseMetadata.builder().model(null).id("provider-id").usage(new DefaultUsage(10, 20)).build();

        var captured = ProviderUsageTransport.capture(new ChatResponse(List.of(), metadata));

        assertThat(captured).isEqualTo(new ProviderUsageUpdate.Call("", "provider-id", 10, 20, null));
    }
}
