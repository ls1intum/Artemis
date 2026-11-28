package de.tum.cit.aet.artemis.course.service.home;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import de.tum.cit.aet.artemis.aiworker.api.LlmChatOptions;
import de.tum.cit.aet.artemis.core.config.llm.LlmProperties;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.service.llm.LlmClientService;
import de.tum.cit.aet.artemis.course.config.CourseHomeAiProperties;
import io.micrometer.core.instrument.MeterRegistry;

/** Calls Home's preset with a fixed time limit, no retries, a small output budget and at most four requests at once, without a queue. */
public final class SpringAiCourseHomeClient implements CourseHomeAiClient {

    static final Duration TIMEOUT = Duration.ofSeconds(3);

    static final int MAX_OUTPUT_TOKENS = 160;

    private final Map<String, Model> models;

    private final MeterRegistry meters;

    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 4, 60, TimeUnit.SECONDS, new SynchronousQueue<>(),
            Thread.ofPlatform().daemon(true).name("course-home-ai-", 0).factory());

    public SpringAiCourseHomeClient(CourseHomeAiProperties properties, LlmClientService llmClientService, MeterRegistry meters) {
        this(configuredModels(properties, llmClientService), meters);
    }

    SpringAiCourseHomeClient(Map<String, Model> models, MeterRegistry meters) {
        this.models = Map.copyOf(models);
        this.meters = meters;
    }

    private static Map<String, Model> configuredModels(CourseHomeAiProperties properties, LlmClientService llmClientService) {
        if (!properties.isConfigured()) {
            throw new IllegalArgumentException("Home AI provider configuration is incomplete");
        }
        // Isolated clients omit prompt and completion tracing, including for local inference.
        return Stream.of(AiSelectionDecision.LOCAL_AI, AiSelectionDecision.CLOUD_AI).map(properties::presetFor).flatMap(Optional::stream).distinct().collect(
                Collectors.toUnmodifiableMap(Function.identity(), preset -> new Model(llmClientService.isolatedChatModel(preset, TIMEOUT, 0), llmClientService.preset(preset))));
    }

    @Override
    public CompletableFuture<Optional<String>> generate(String preset, Input input) {
        Model model = models.get(preset);
        if (model == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                var response = model.chatClient().prompt().system(input.systemPrompt()).user(input.userPrompt()).options(model.outputOptions()).call().chatResponse();
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

    record Model(ChatClient chatClient, boolean usesLegacyMaxTokens, int outputTokens) {

        Model(ChatModel model, LlmProperties.Preset preset) {
            // Use the token field expected by the preset and preserve any lower configured limit.
            this(ChatClient.create(model), preset.maxTokens() != null,
                    preset.outputTokenLimit() == null ? MAX_OUTPUT_TOKENS : Math.min(MAX_OUTPUT_TOKENS, preset.outputTokenLimit()));
        }

        /** A fresh builder per call: the chat client consumes a mutable options builder. */
        LlmChatOptions.Builder outputOptions() {
            LlmChatOptions.Builder options = LlmChatOptions.builder();
            if (usesLegacyMaxTokens) {
                options.maxTokens(outputTokens);
            }
            else {
                options.maxCompletionTokens(outputTokens);
            }
            return options;
        }
    }

    private void record(String outcome) {
        meters.counter("artemis.course.home.ai.request", "outcome", outcome).increment();
    }

    @Override
    public void close() {
        workers.shutdownNow();
    }
}
