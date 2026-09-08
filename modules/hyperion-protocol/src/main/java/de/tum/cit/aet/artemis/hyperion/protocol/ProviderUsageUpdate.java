package de.tum.cit.aet.artemis.hyperion.protocol;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Provider evidence only: never prompt/completion content, provider endpoints or credentials. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProviderUsageUpdate(Kind kind, long count, @Nullable Call call) {

    public ProviderUsageUpdate {
        Objects.requireNonNull(kind);
        if (count < 0 || (kind == Kind.RESPONSE) != (call != null)) {
            throw new IllegalArgumentException("Invalid provider accounting event");
        }
    }

    public enum Kind {
        RESPONSE, TOOL_CALLS, TURN, ATTEMPT, UNCERTAIN
    }

    /** Token classes reported by the provider; missing cache metadata remains unknown. */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Call(@JsonInclude String model, @Nullable String requestId, int inputTokens, int outputTokens, @Nullable Long cachedInputTokens) {

        public Call {
            if (model == null || model.length() > 1_024 || (requestId != null && requestId.length() > 1_024) || inputTokens < 0 || outputTokens < 0
                    || (cachedInputTokens != null && (cachedInputTokens < 0 || cachedInputTokens > inputTokens))) {
                throw new IllegalArgumentException("Invalid provider usage metadata");
            }
        }
    }
}
