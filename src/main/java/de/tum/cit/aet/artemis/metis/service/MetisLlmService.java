package de.tum.cit.aet.artemis.metis.service;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIRetryableException;
import com.openai.errors.OpenAIServiceException;

import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.metis.config.MetisConfiguration;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.config.MetisProperties;

/**
 * The transport of every Metis LLM call: generation, regeneration and rating all go through {@link #complete}.
 * <p>
 * It bounds the calls of this node with a fair semaphore of {@code artemis.metis.llm.max-concurrent-calls} permits.
 * Batch calls (generation and rating backfill on the Metis executor) wait for a permit as long as it takes; interactive
 * calls (a live rating or a regeneration that a person waits for) wait at most
 * {@code interactive-permit-wait-seconds} and count themselves in {@link #interactiveWaiting()}, so the dispatcher can
 * hold back new batch work while they wait. After three interactive calls in a row that the endpoint answered as
 * unavailable, interactive calls fail fast for a minute instead of each waiting for the full timeout; a waiting answer is
 * then stored unrated at once and rated later by the backfill. Every response is recorded for token tracking, billed to
 * the attribution of the call.
 * <p>
 * The service never logs or keeps prompt content. Integration tests replace it with a mock, which makes it the test seam
 * of the module.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Service
public class MetisLlmService {

    private static final Logger log = LoggerFactory.getLogger(MetisLlmService.class);

    /** Interactive calls in a row that the endpoint answered as unavailable before interactive calls pause. */
    static final int INTERACTIVE_FAILURES_BEFORE_PAUSE = 3;

    /** How long interactive calls fail fast once they paused. */
    static final Duration INTERACTIVE_PAUSE = Duration.ofSeconds(60);

    /** The width of the column that stores the model of a generated check. */
    private static final int MAX_MODEL_LENGTH = 100;

    /** Bad request, payload too large and unprocessable content: this request can never succeed, e.g. a context that is too long. */
    private static final Set<Integer> REJECTED_STATUS_CODES = Set.of(400, 413, 422);

    /** Bounds the walk along a cause chain, which could in theory loop back onto itself. */
    private static final int MAX_CAUSE_DEPTH = 32;

    private final MetisConfiguration.MetisChatClients chatClients;

    private final LLMTokenUsageService llmTokenUsageService;

    private final Semaphore permits;

    private final int interactivePermitWaitSeconds;

    private final AtomicInteger interactiveWaiting = new AtomicInteger();

    /** Guarded by {@code this}. */
    private int consecutiveInteractiveFailures;

    /** Guarded by {@code this}. */
    @Nullable
    private ZonedDateTime interactivePausedUntil;

    /**
     * How a call waits for its permit and which client it uses.
     */
    public enum Mode {
        /** Background work on the Metis executor: waits for a permit as long as it takes, the client retries. */
        BATCH,
        /** A person waits for the answer: waits for a permit only briefly, the client never retries. */
        INTERACTIVE
    }

    /**
     * What a call is for; its id appears in the token usage records.
     */
    public enum Pipeline {

        /** The questions of a check. */
        QUESTION_GENERATION("METIS_QUESTION_GENERATION"),
        /** One new question that replaces a generated one. */
        QUESTION_REGENERATION("METIS_QUESTION_REGENERATION"),
        /** The rating of an answer, with an optional follow-up question. */
        ANSWER_RATING("METIS_ANSWER_RATING");

        private final String pipelineId;

        Pipeline(String pipelineId) {
            this.pipelineId = pipelineId;
        }

        /**
         * The id under which the token usage of this pipeline is recorded.
         *
         * @return the pipeline id
         */
        public String pipelineId() {
            return pipelineId;
        }
    }

    /**
     * Who the call is billed to in the token usage records.
     *
     * @param courseId   the course of the exercise
     * @param exerciseId the exercise
     * @param userId     the student whose code is processed, or the instructor who asked for a regeneration
     */
    public record Attribution(long courseId, long exerciseId, @Nullable Long userId) {
    }

    /**
     * The answer of one call.
     *
     * @param text  the raw text of the answer, never blank
     * @param model the model id the endpoint reported, or the configured model if it reported none; at most 100 characters
     */
    public record Completion(String text, String model) {
    }

    public MetisLlmService(@Qualifier(MetisConfiguration.METIS_CHAT_CLIENTS) MetisConfiguration.MetisChatClients chatClients, MetisProperties properties,
            LLMTokenUsageService llmTokenUsageService) {
        this.chatClients = chatClients;
        this.llmTokenUsageService = llmTokenUsageService;
        int maxConcurrentCalls = properties.llm().maxConcurrentCalls();
        this.permits = new Semaphore(maxConcurrentCalls, true);
        this.interactivePermitWaitSeconds = properties.llm().interactivePermitWaitSeconds();
        int maxConcurrentTasks = properties.generation().maxConcurrentTasks();
        if (maxConcurrentCalls <= maxConcurrentTasks) {
            log.warn("artemis.metis.llm.max-concurrent-calls ({}) should exceed artemis.metis.generation.max-concurrent-tasks ({}): otherwise running generation tasks "
                    + "can hold every permit that live ratings need", maxConcurrentCalls, maxConcurrentTasks);
        }
    }

    /**
     * Whether the endpoint is configured. While it is not, every call fails as unavailable and generation pauses.
     *
     * @return true if base URL and model are set
     */
    public boolean isConfigured() {
        return chatClients.isConfigured();
    }

    /**
     * Number of interactive calls on this node that are waiting for a permit right now; the dispatcher starts no batch
     * work while it is above 0.
     *
     * @return the number of waiting interactive calls
     */
    public int interactiveWaiting() {
        return interactiveWaiting.get();
    }

    /**
     * Makes one chat call. The prompts are sent as plain text, never through template rendering, because they contain
     * student code with braces.
     *
     * @param pipeline     what the call is for
     * @param mode         how the call waits for its permit and which client it uses
     * @param systemPrompt the rendered system prompt
     * @param userPrompt   the rendered user prompt
     * @param attribution  who the call is billed to
     * @return the text of the answer and the model that produced it
     * @throws MetisLlmException {@code UNAVAILABLE} if the endpoint is not configured, not reachable, interactive calls
     *                               are paused or no permit became free in time; {@code REJECTED} if the endpoint refused
     *                               the request; {@code INVALID_OUTPUT} if the answer has no text
     */
    public Completion complete(Pipeline pipeline, Mode mode, String systemPrompt, String userPrompt, Attribution attribution) {
        ChatClient client = mode == Mode.INTERACTIVE ? chatClients.interactive() : chatClients.batch();
        if (client == null) {
            throw new MetisLlmException(MetisLlmException.Kind.UNAVAILABLE, "The Metis LLM endpoint is not configured");
        }
        if (mode == Mode.INTERACTIVE && isInteractivePaused(TimeUtil.now())) {
            throw new MetisLlmException(MetisLlmException.Kind.UNAVAILABLE, "Interactive Metis LLM calls are paused after repeated failures of the endpoint");
        }
        ChatResponse response;
        acquirePermit(mode);
        try {
            response = call(client, pipeline, mode, systemPrompt, userPrompt);
        }
        finally {
            permits.release();
        }
        // Every response counts, including one whose text turns out to be unusable: its tokens were spent all the same
        llmTokenUsageService.trackChatResponseTokenUsage(response, LLMServiceType.METIS, pipeline.pipelineId(),
                builder -> builder.withCourse(attribution.courseId()).withExercise(attribution.exerciseId()).withUser(attribution.userId()));
        String text = LLMTokenUsageService.extractResponseText(response);
        if (response == null || text == null || text.isBlank()) {
            throw new MetisLlmException(MetisLlmException.Kind.INVALID_OUTPUT, "The Metis LLM endpoint returned no text for " + pipeline.pipelineId());
        }
        return new Completion(text, reportedModel(response));
    }

    /**
     * Classifies the failure of a call by walking its cause chain. The first exception that tells what happened decides:
     * the endpoint refused the request ({@code REJECTED} for status 400, 413 and 422), or the endpoint or the network is
     * unavailable (other status codes, I/O errors and timeouts). Authentication and model-not-found errors are deliberately
     * unavailable, since they are configuration problems of the whole endpoint rather than of one request. Everything
     * that cannot be recognised counts as unavailable as well, which is the conservative choice: repeated failures of one
     * check still end as failed through the transient failure count of the dispatcher.
     *
     * @param failure what the call threw
     * @return the kind of the failure
     */
    static MetisLlmException.Kind classify(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof OpenAIServiceException serviceException) {
                return REJECTED_STATUS_CODES.contains(serviceException.statusCode()) ? MetisLlmException.Kind.REJECTED : MetisLlmException.Kind.UNAVAILABLE;
            }
            if (current instanceof OpenAIIoException || current instanceof OpenAIRetryableException || current instanceof IOException || current instanceof TimeoutException) {
                return MetisLlmException.Kind.UNAVAILABLE;
            }
            current = current.getCause();
        }
        return MetisLlmException.Kind.UNAVAILABLE;
    }

    private void acquirePermit(Mode mode) {
        try {
            if (mode == Mode.BATCH) {
                permits.acquire();
                return;
            }
            boolean acquired;
            interactiveWaiting.incrementAndGet();
            try {
                acquired = permits.tryAcquire(interactivePermitWaitSeconds, TimeUnit.SECONDS);
            }
            finally {
                interactiveWaiting.decrementAndGet();
            }
            if (!acquired) {
                // Local load rather than a broken endpoint, so it does not count towards the interactive pause
                throw new MetisLlmException(MetisLlmException.Kind.UNAVAILABLE, "No Metis LLM permit became free within " + interactivePermitWaitSeconds + " seconds");
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MetisLlmException(MetisLlmException.Kind.UNAVAILABLE, "Interrupted while waiting for a Metis LLM permit", e);
        }
    }

    @Nullable
    private ChatResponse call(ChatClient client, Pipeline pipeline, Mode mode, String systemPrompt, String userPrompt) {
        try {
            ChatResponse response = client.prompt().system(systemPrompt).user(userPrompt).call().chatResponse();
            if (mode == Mode.INTERACTIVE) {
                recordInteractiveAnswer();
            }
            return response;
        }
        catch (Exception e) {
            MetisLlmException.Kind kind = classify(e);
            if (mode == Mode.INTERACTIVE) {
                if (kind == MetisLlmException.Kind.UNAVAILABLE) {
                    recordInteractiveUnavailable();
                }
                else {
                    recordInteractiveAnswer();
                }
            }
            log.debug("Metis LLM call {} ({}) failed as {}", pipeline.pipelineId(), mode, kind, e);
            throw new MetisLlmException(kind, "The Metis LLM call " + pipeline.pipelineId() + " failed: " + e.getMessage(), e);
        }
    }

    private String reportedModel(ChatResponse response) {
        ChatResponseMetadata metadata = response.getMetadata();
        String reported = metadata != null ? metadata.getModel() : null;
        String model = reported != null && !reported.isBlank() ? reported.strip() : chatClients.model();
        return model.length() > MAX_MODEL_LENGTH ? model.substring(0, MAX_MODEL_LENGTH) : model;
    }

    private synchronized boolean isInteractivePaused(ZonedDateTime now) {
        return interactivePausedUntil != null && now.isBefore(interactivePausedUntil);
    }

    /**
     * The endpoint answered an interactive call, even if only to refuse it, so it is not broken.
     */
    private synchronized void recordInteractiveAnswer() {
        consecutiveInteractiveFailures = 0;
    }

    private synchronized void recordInteractiveUnavailable() {
        consecutiveInteractiveFailures++;
        if (consecutiveInteractiveFailures >= INTERACTIVE_FAILURES_BEFORE_PAUSE) {
            consecutiveInteractiveFailures = 0;
            interactivePausedUntil = TimeUtil.now().plus(INTERACTIVE_PAUSE);
            log.warn("The Metis LLM endpoint was unavailable for {} interactive calls in a row; interactive calls fail fast until {}", INTERACTIVE_FAILURES_BEFORE_PAUSE,
                    interactivePausedUntil);
        }
    }
}
