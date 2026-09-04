package de.tum.cit.aet.artemis.hyperion.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/** Bound generation defaults and administrator-defined effort profiles under {@code artemis.hyperion.agent}. */
@Lazy
@Configuration
@Conditional(HyperionExerciseGenerationEnabled.class)
@ConfigurationProperties(prefix = "artemis.hyperion.agent")
public class HyperionAgentProperties {

    /** Hard cap on model turns for one attempt of a run. Bounds an attempt, not the whole run. */
    private int maxTurns = 60;

    /**
     * Wall-clock deadline for one run. Also the basis of the budget reservation TTL and the staged authoring budget.
     * <p>
     * {@code config/application-artemis.yml} restates this default for operator documentation and {@code HyperionAgentPropertiesTest} fails if the two diverge: a node without the
     * {@code artemis} profile binds the value from here, so a divergence would make the {@link #staleJobTimeout} invariant hold on one node and fail startup on another.
     */
    private Duration maxJobDuration = Duration.ofMinutes(45);

    /**
     * How long a slot may go without a heartbeat before another node reclaims it. Must be strictly greater than {@link #maxJobDuration} and than the longest duration any
     * configured effort profile can raise it to; validated at startup.
     */
    private Duration staleJobTimeout = Duration.ofMinutes(50);

    /** Post-response per-job provider spend guard, and the number of tokens admission reserves for a job it admits. */
    private long maxTokensPerJob = 3_000_000L;

    /**
     * The share of a provider-cached input token that counts against {@link #maxTokensPerJob}, in [0, 1].
     * <p>
     * Defaults to the discount OpenAI-compatible providers apply to cached input. An agentic run re-sends the same prefix every turn, so most of its input is cache reads;
     * counting those at full weight exhausts the budget long before the run has spent what the budget bounds. Never set this to 0: the budget would then stop bounding a run
     * whose prefix stays warm.
     */
    public static final double DEFAULT_CACHED_INPUT_TOKEN_WEIGHT = 0.5d;

    private double cachedInputTokenWeight = DEFAULT_CACHED_INPUT_TOKEN_WEIGHT;

    /** Whether Java {@code GENERATE} runs go through the specification -> tests -> statement stage order. */
    private boolean stagedGeneration = true;

    /** {@code CONTINUOUS} or {@code FRESH}; validated where it is parsed, so an invalid value fails startup rather than the first run. */
    private String stagedContext = "CONTINUOUS";

    /** The model's usable context window in tokens; a profile that pins a model may pin this too. */
    private int contextWindowTokens = 128_000;

    /** Name of the profile used when a request names none. Empty means the top-level values above are used directly. */
    private String defaultProfile = "";

    /** Selectable effort profiles by name; empty (the shipped default) means the feature is inert and every request runs the top-level configuration. */
    private Map<String, EffortProfileProperties> profiles = new LinkedHashMap<>();

    public int getMaxTurns() {
        return maxTurns;
    }

    public void setMaxTurns(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    public Duration getMaxJobDuration() {
        return maxJobDuration;
    }

    public void setMaxJobDuration(Duration maxJobDuration) {
        this.maxJobDuration = maxJobDuration;
    }

    public Duration getStaleJobTimeout() {
        return staleJobTimeout;
    }

    public void setStaleJobTimeout(Duration staleJobTimeout) {
        this.staleJobTimeout = staleJobTimeout;
    }

    public long getMaxTokensPerJob() {
        return maxTokensPerJob;
    }

    public void setMaxTokensPerJob(long maxTokensPerJob) {
        this.maxTokensPerJob = maxTokensPerJob;
    }

    public double getCachedInputTokenWeight() {
        return cachedInputTokenWeight;
    }

    public void setCachedInputTokenWeight(double cachedInputTokenWeight) {
        this.cachedInputTokenWeight = cachedInputTokenWeight;
    }

    public boolean isStagedGeneration() {
        return stagedGeneration;
    }

    public void setStagedGeneration(boolean stagedGeneration) {
        this.stagedGeneration = stagedGeneration;
    }

    public String getStagedContext() {
        return stagedContext;
    }

    public void setStagedContext(String stagedContext) {
        this.stagedContext = stagedContext;
    }

    public int getContextWindowTokens() {
        return contextWindowTokens;
    }

    public void setContextWindowTokens(int contextWindowTokens) {
        this.contextWindowTokens = contextWindowTokens;
    }

    public String getDefaultProfile() {
        return defaultProfile;
    }

    public void setDefaultProfile(String defaultProfile) {
        this.defaultProfile = defaultProfile;
    }

    public Map<String, EffortProfileProperties> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, EffortProfileProperties> profiles) {
        this.profiles = profiles == null ? new LinkedHashMap<>() : profiles;
    }

    /** Optional overrides for one administrator-defined effort profile. */
    public static class EffortProfileProperties {

        /** Instructor-facing name of this profile; the profile key is used when unset. */
        @Nullable
        private String label;

        @Nullable
        private String model;

        @Nullable
        private Double temperature;

        @Nullable
        private Double topP;

        @Nullable
        private String reasoningEffort;

        @Nullable
        private String verbosity;

        @Nullable
        private Integer maxCompletionTokens;

        @Nullable
        private Integer maxTurns;

        @Nullable
        private Duration maxJobDuration;

        @Nullable
        private Long maxTokensPerJob;

        @Nullable
        private Boolean stagedGeneration;

        @Nullable
        private String stagedContext;

        @Nullable
        private Integer contextWindowTokens;

        @Nullable
        public String getLabel() {
            return label;
        }

        public void setLabel(@Nullable String label) {
            this.label = label;
        }

        @Nullable
        public String getModel() {
            return model;
        }

        public void setModel(@Nullable String model) {
            this.model = model;
        }

        @Nullable
        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(@Nullable Double temperature) {
            this.temperature = temperature;
        }

        @Nullable
        public Double getTopP() {
            return topP;
        }

        public void setTopP(@Nullable Double topP) {
            this.topP = topP;
        }

        @Nullable
        public String getReasoningEffort() {
            return reasoningEffort;
        }

        public void setReasoningEffort(@Nullable String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
        }

        @Nullable
        public String getVerbosity() {
            return verbosity;
        }

        public void setVerbosity(@Nullable String verbosity) {
            this.verbosity = verbosity;
        }

        @Nullable
        public Integer getMaxCompletionTokens() {
            return maxCompletionTokens;
        }

        public void setMaxCompletionTokens(@Nullable Integer maxCompletionTokens) {
            this.maxCompletionTokens = maxCompletionTokens;
        }

        @Nullable
        public Integer getMaxTurns() {
            return maxTurns;
        }

        public void setMaxTurns(@Nullable Integer maxTurns) {
            this.maxTurns = maxTurns;
        }

        @Nullable
        public Duration getMaxJobDuration() {
            return maxJobDuration;
        }

        public void setMaxJobDuration(@Nullable Duration maxJobDuration) {
            this.maxJobDuration = maxJobDuration;
        }

        @Nullable
        public Long getMaxTokensPerJob() {
            return maxTokensPerJob;
        }

        public void setMaxTokensPerJob(@Nullable Long maxTokensPerJob) {
            this.maxTokensPerJob = maxTokensPerJob;
        }

        @Nullable
        public Boolean getStagedGeneration() {
            return stagedGeneration;
        }

        public void setStagedGeneration(@Nullable Boolean stagedGeneration) {
            this.stagedGeneration = stagedGeneration;
        }

        @Nullable
        public String getStagedContext() {
            return stagedContext;
        }

        public void setStagedContext(@Nullable String stagedContext) {
            this.stagedContext = stagedContext;
        }

        @Nullable
        public Integer getContextWindowTokens() {
            return contextWindowTokens;
        }

        public void setContextWindowTokens(@Nullable Integer contextWindowTokens) {
            this.contextWindowTokens = contextWindowTokens;
        }

        /**
         * @return {@code true} when this profile sets any provider-facing value, which is what decides whether a run needs its own prebuilt provider options
         */
        public boolean overridesProviderOptions() {
            return model != null || temperature != null || topP != null || reasoningEffort != null || verbosity != null || maxCompletionTokens != null;
        }
    }
}
