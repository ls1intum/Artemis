package de.tum.cit.aet.artemis.aiworker.api;

import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;

/** Spring AI chat options for tool use, structured output and the generation controls supported by the Responses endpoint. */
public final class LlmChatOptions extends DefaultToolCallingChatOptions implements StructuredOutputChatOptions {

    private final Builder settings;

    private LlmChatOptions(Builder settings, ToolCallingChatOptions common) {
        super(common.getToolCallbacks(), common.getToolContext(), common.getModel(), common.getFrequencyPenalty(), common.getMaxTokens(), common.getPresencePenalty(),
                common.getStopSequences(), common.getTemperature(), common.getTopK(), common.getTopP());
        this.settings = settings.clone();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Builder mutate() {
        return settings.clone();
    }

    public @Nullable String getReasoningEffort() {
        return settings.reasoningEffort;
    }

    public @Nullable String getVerbosity() {
        return settings.verbosity;
    }

    public @Nullable Boolean getParallelToolCalls() {
        return settings.parallelToolCalls;
    }

    public @Nullable Integer getMaxCompletionTokens() {
        return settings.maxCompletionTokens;
    }

    @Override
    public @Nullable String getOutputSchema() {
        return settings.outputSchema;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LlmChatOptions that && super.equals(that) && Objects.equals(getReasoningEffort(), that.getReasoningEffort())
                && Objects.equals(getVerbosity(), that.getVerbosity()) && Objects.equals(getParallelToolCalls(), that.getParallelToolCalls())
                && Objects.equals(getMaxCompletionTokens(), that.getMaxCompletionTokens()) && Objects.equals(getOutputSchema(), that.getOutputSchema());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getReasoningEffort(), getVerbosity(), getParallelToolCalls(), getMaxCompletionTokens(), getOutputSchema());
    }

    public static final class Builder extends DefaultToolCallingChatOptions.Builder<Builder> implements StructuredOutputChatOptions.Builder<Builder> {

        private @Nullable String reasoningEffort;

        private @Nullable String verbosity;

        private @Nullable Boolean parallelToolCalls;

        private @Nullable Integer maxCompletionTokens;

        private @Nullable String outputSchema;

        public Builder reasoningEffort(@Nullable String value) {
            reasoningEffort = value;
            return this;
        }

        public Builder verbosity(@Nullable String value) {
            verbosity = value;
            return this;
        }

        public Builder parallelToolCalls(@Nullable Boolean value) {
            parallelToolCalls = value;
            return this;
        }

        public Builder maxCompletionTokens(@Nullable Integer value) {
            maxCompletionTokens = value;
            return this;
        }

        @Override
        public Builder outputSchema(@Nullable String value) {
            outputSchema = value;
            return this;
        }

        @Override
        public Builder combineWith(ChatOptions.Builder<?> other) {
            super.combineWith(other);
            ChatOptions overrides = other.build();
            // Responses has one output limit. A request's portable limit supersedes the preset's completion limit.
            if (overrides.getMaxTokens() != null) {
                maxCompletionTokens = null;
            }
            if (other instanceof Builder options) {
                reasoningEffort = options.reasoningEffort != null ? options.reasoningEffort : reasoningEffort;
                verbosity = options.verbosity != null ? options.verbosity : verbosity;
                parallelToolCalls = options.parallelToolCalls != null ? options.parallelToolCalls : parallelToolCalls;
                maxCompletionTokens = options.maxCompletionTokens != null ? options.maxCompletionTokens : maxCompletionTokens;
            }
            if (overrides instanceof StructuredOutputChatOptions structured && structured.getOutputSchema() != null) {
                outputSchema = structured.getOutputSchema();
            }
            return this;
        }

        @Override
        public LlmChatOptions build() {
            return new LlmChatOptions(this, super.build());
        }
    }
}
