package de.tum.cit.aet.artemis.course.service.home;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.course.config.CourseHomeAiProperties;
import io.micrometer.core.instrument.MeterRegistry;

/** Calls Home's preset with a fixed time limit, no retries, a small output budget and at most four requests at once, without a queue. */
public final class SpringAiCourseHomeClient implements CourseHomeAiClient {

    static final Duration TIMEOUT = Duration.ofSeconds(3);

    static final int MAX_OUTPUT_TOKENS = 160;

    private final ChatClient chatClient;

    private final boolean usesLegacyMaxTokens;

    private final int outputTokens;

    private final MeterRegistry meters;

    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 4, 60, TimeUnit.SECONDS, new SynchronousQueue<>(),
            Thread.ofPlatform().daemon(true).name("course-home-ai-", 0).factory());

    public SpringAiCourseHomeClient(CourseHomeAiProperties properties, LlmClientService llmClientService, MeterRegistry meters) {
        this(model(properties, llmClientService), llmClientService.preset(properties.llmPreset()), meters);
    }

    SpringAiCourseHomeClient(ChatModel model, LlmProperties.Preset preset, MeterRegistry meters) {
        this.chatClient = ChatClient.create(model);
        // Servers such as Ollama read only max_tokens; a preset that sets that field gets the budget in the same field, and a lower preset limit stays in force.
        this.usesLegacyMaxTokens = preset.maxTokens() != null;
        this.outputTokens = preset.outputTokenLimit() == null ? MAX_OUTPUT_TOKENS : Math.min(MAX_OUTPUT_TOKENS, preset.outputTokenLimit());
        this.meters = meters;
    }

    private static ChatModel model(CourseHomeAiProperties properties, LlmClientService llmClientService) {
        if (!properties.isConfigured()) {
            throw new IllegalArgumentException("Home AI provider configuration is incomplete");
        }
        // isolated: no observations, so a LOCAL preset's prompts and completions cannot leave through tracing
        return llmClientService.isolatedChatModel(properties.llmPreset(), TIMEOUT, 0);
    }

    @Override
    public CompletableFuture<Optional<String>> generate(Input input) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                var response = chatClient.prompt().system(input.systemPrompt()).user(input.userPrompt()).options(outputOptions()).call().chatResponse();
                return response == null || response.getResult() == null || response.getResult().getOutput() == null ? Optional.<String>empty()
                        : Optional.ofNullable(response.getResult().getOutput().getText());
            }, workers).orTimeout(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).exceptionally(error -> {
                record(error.getCause() instanceof TimeoutException || error instanceof TimeoutException ? "timeout" : "failure");
                return Optional.empty();
            });
        }
        catch (RejectedExecutionException ex) {
            record("rejected");
            return CompletableFuture.completedFuture(Optional.empty());
        }
    }

    /** A fresh builder per call: the chat client consumes a mutable options builder. */
    private OpenAiChatOptions.Builder outputOptions() {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
        if (usesLegacyMaxTokens) {
            options.maxTokens(outputTokens);
        }
        else {
            options.maxCompletionTokens(outputTokens);
        }
        return options;
    }

    private void record(String outcome) {
        meters.counter("artemis.course.home.ai.request", "outcome", outcome).increment();
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }
}
