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
    void providerUsdCostAndSourceSurviveRoundTrip() {
        var call = new ProviderUsageUpdate.Call("model", "provider-id", 10, 20, null, 0.00001234, "PROVIDER");

        assertThat(ProviderUsageTransport.capture(ProviderUsageTransport.restore(call))).isEqualTo(call);
    }

    @Test
    void invalidOrUnidentifiedCostDoesNotDiscardUsage() {
        for (var call : List.of(new ProviderUsageUpdate.Call("model", "id", 10, 20, null, -1d, "PROVIDER"),
                new ProviderUsageUpdate.Call("model", "id", 10, 20, null, Double.NaN, "PROVIDER"), new ProviderUsageUpdate.Call("model", "id", 10, 20, null, 1d, "unknown"),
                new ProviderUsageUpdate.Call("model", "id", 10, 20, null, 1d, null))) {
            assertThat(call.totalCostUsd()).isNull();
            assertThat(call.costSource()).isNull();
            assertThat(ProviderUsageTransport.capture(ProviderUsageTransport.restore(call)).inputTokens()).isEqualTo(10);
        }
    }

    @Test
    void capturesUsageWhenTheProviderDoesNotReportAModel() {
        var metadata = ChatResponseMetadata.builder().usage(new DefaultUsage(10, 20)).build();

        var captured = ProviderUsageTransport.capture(new ChatResponse(List.of(), metadata));

        assertThat(captured).isEqualTo(new ProviderUsageUpdate.Call("", "", 10, 20, null));
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
