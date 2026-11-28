package de.tum.cit.aet.artemis.metis.config;

import java.time.Duration;
import java.util.Objects;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration of the Metis module under {@code artemis.metis}.
 * <p>
 * Every key has a default, so a node without the block (tests, the OpenAPI boot, a production node that never configured
 * Metis) starts without it. Binding is strict: a typo under {@code artemis.metis} or an invalid value fails startup on
 * purpose instead of silently falling back to a default. The record is registered by {@link MetisConfiguration}, so it
 * only exists while the module is enabled.
 *
 * @param enabled      the module switch, read by {@link MetisEnabled}; bound here only so that strict binding accepts it
 * @param llm          the LLM endpoint of the module
 * @param generation   the generation of the questions after the due date
 * @param conversation the conversation with the student
 */
@Validated
@ConfigurationProperties(prefix = "artemis.metis", ignoreUnknownFields = false)
public record MetisProperties(@DefaultValue("false") boolean enabled, @Valid @DefaultValue Llm llm, @Valid @DefaultValue Generation generation,
        @Valid @DefaultValue Conversation conversation) {

    /**
     * Where the configured endpoint runs, which decides whose AI consent suffices.
     */
    public enum Hosting {
        /** The endpoint runs on university-owned infrastructure: students who chose local or cloud AI take part. */
        LOCAL,
        /** The endpoint is a cloud service: only students who chose cloud AI take part. */
        CLOUD
    }

    /**
     * The OpenAI-compatible endpoint that all Metis LLM calls go to. Student code is only ever sent here.
     *
     * @param baseUrl                      the endpoint root including the version prefix, e.g. {@code http://localhost:11434/v1}; empty means not configured
     * @param apiKey                       the API key; empty selects the no-auth mode of the SDK
     * @param model                        the model id sent with each request; empty means not configured
     * @param hosting                      where the endpoint runs
     * @param temperature                  the sampling temperature
     * @param batchTimeoutSeconds          the request timeout of the batch client (generation, rating backfill)
     * @param interactiveTimeoutSeconds    the request timeout of the interactive client (live rating, regeneration)
     * @param batchMaxRetries              the SDK-level retries of the batch client; the interactive client never retries
     * @param maxConcurrentCalls           the permits per node for all Metis LLM calls
     * @param interactivePermitWaitSeconds how long an interactive call waits for a permit before it counts as unavailable
     */
    public record Llm(@DefaultValue("") String baseUrl, @DefaultValue("") String apiKey, @DefaultValue("") String model, @DefaultValue("LOCAL") Hosting hosting,
            @DefaultValue("0.2") @PositiveOrZero double temperature, @DefaultValue("180") @Positive int batchTimeoutSeconds,
            @DefaultValue("60") @Positive int interactiveTimeoutSeconds, @DefaultValue("2") @PositiveOrZero int batchMaxRetries,
            @DefaultValue("4") @Positive int maxConcurrentCalls, @DefaultValue("10") @Positive int interactivePermitWaitSeconds) {

        /**
         * Turns a missing text value into an empty string and a missing hosting into {@link Hosting#LOCAL}, so the rest of
         * the module never handles null here.
         */
        public Llm {
            baseUrl = Objects.requireNonNullElse(baseUrl, "");
            apiKey = Objects.requireNonNullElse(apiKey, "");
            model = Objects.requireNonNullElse(model, "");
            hosting = Objects.requireNonNullElse(hosting, Hosting.LOCAL);
        }

        /**
         * Both base URL and model are set; otherwise generation and rating pause.
         *
         * @return whether the endpoint is configured
         */
        public boolean isConfigured() {
            return !baseUrl.isBlank() && !model.isBlank();
        }

        /**
         * Keeps the API key out of every log line and error message that prints the configuration.
         *
         * @return the configuration with the key redacted
         */
        @Override
        public String toString() {
            return "Llm[baseUrl=" + baseUrl + ", apiKey=" + (apiKey.isEmpty() ? "" : "<redacted>") + ", model=" + model + ", hosting=" + hosting + ", temperature=" + temperature
                    + ", batchTimeoutSeconds=" + batchTimeoutSeconds + ", interactiveTimeoutSeconds=" + interactiveTimeoutSeconds + ", batchMaxRetries=" + batchMaxRetries
                    + ", maxConcurrentCalls=" + maxConcurrentCalls + ", interactivePermitWaitSeconds=" + interactivePermitWaitSeconds + "]";
        }
    }

    /**
     * The generation of the questions, which runs on the scheduling node.
     *
     * @param schedulerEnabled              whether the generation poller runs at all (tests switch it off)
     * @param pollIntervalSeconds           the fixed delay between two ticks of the poller
     * @param delayAfterDueDateSeconds      how old an effective due date must be before its participation is processed
     * @param enableDelayMinutes            how long the scheduling node must have seen a configuration enabled before checks are created
     * @param horizonDays                   how far back the automatic sweeps look
     * @param maxConcurrentTasks            the size of the Metis executor and of its queue
     * @param minChangedLines               the changed lines a file needs to become a focus file candidate
     * @param maxFocusFileCharacters        the largest focus file whose full content is sent; also the cap for one diff
     * @param maxPromptCharacters           the budget of one generation prompt
     * @param maxProblemStatementCharacters the excerpt of the problem statement in the prompt
     * @param maxTransientFailures          the generation attempts with an unavailable LLM after which a check fails
     */
    public record Generation(@DefaultValue("true") boolean schedulerEnabled, @DefaultValue("30") @Positive int pollIntervalSeconds,
            @DefaultValue("60") @PositiveOrZero int delayAfterDueDateSeconds, @DefaultValue("10") @PositiveOrZero int enableDelayMinutes,
            @DefaultValue("30") @Positive int horizonDays, @DefaultValue("2") @Positive int maxConcurrentTasks, @DefaultValue("5") @Positive int minChangedLines,
            @DefaultValue("12000") @Positive int maxFocusFileCharacters, @DefaultValue("60000") @Positive int maxPromptCharacters,
            @DefaultValue("6000") @Positive int maxProblemStatementCharacters, @DefaultValue("3") @Positive int maxTransientFailures) {
    }

    /**
     * The conversation with the student.
     *
     * @param answerGraceSeconds  the grace after the question deadline and after the window end
     * @param maxAnswerCharacters the length longer answers are cut to (they are never rejected)
     */
    public record Conversation(@DefaultValue("15") @PositiveOrZero int answerGraceSeconds, @DefaultValue("10000") @Positive int maxAnswerCharacters) {
    }

    /**
     * Upper bound of one answer request: the permit wait and one interactive call (interactive calls are never retried),
     * plus the answer grace as a margin (85 s with the defaults). The repair on reading the own check and the rating
     * backfill leave an answer alone for this long, because the request that stored it may still wait for its rating.
     *
     * @return how long an answer request can take at most
     */
    public Duration answerSettleTime() {
        return Duration.ofSeconds((long) llm.interactivePermitWaitSeconds() + llm.interactiveTimeoutSeconds() + conversation.answerGraceSeconds());
    }
}
