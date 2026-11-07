package de.tum.cit.aet.artemis.course.service.home;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;

import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;

import de.tum.cit.aet.artemis.course.config.CourseHomeAiProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;

/** A separately owned model and transport, with no queue and no retries. */
public final class SpringAiCourseHomeClient implements CourseHomeAiClient {

    static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final ChatModel model;

    private final AutoCloseable syncTransport;

    private final AutoCloseable asyncTransport;

    private final MeterRegistry meters;

    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 4, 60, TimeUnit.SECONDS, new SynchronousQueue<>(),
            Thread.ofPlatform().daemon(true).name("course-home-ai-", 0).factory());

    private record Setup(ChatModel model, AutoCloseable syncTransport, AutoCloseable asyncTransport) {
    }

    public SpringAiCourseHomeClient(CourseHomeAiProperties properties, MeterRegistry meters) {
        this(setup(properties, meters), meters);
    }

    private SpringAiCourseHomeClient(Setup setup, MeterRegistry meters) {
        this(setup.model(), setup.syncTransport(), setup.asyncTransport(), meters);
    }

    private static Setup setup(CourseHomeAiProperties properties, MeterRegistry meters) {
        if (!properties.isConfigured()) {
            throw new IllegalArgumentException("Home AI provider configuration is incomplete");
        }
        OpenAIClient sync = OpenAiSetup.setupSyncClient(properties.baseUrl(), properties.apiKey(), null, null, null, null, false, false, properties.model(), TIMEOUT, 0, null,
                Map.of(), ObservationRegistry.NOOP, meters, List.of());
        OpenAIClientAsync async;
        try {
            async = OpenAiSetup.setupAsyncClient(properties.baseUrl(), properties.apiKey(), null, null, null, null, false, false, properties.model(), TIMEOUT, 0, null, Map.of(),
                    ObservationRegistry.NOOP, meters, List.of());
        }
        catch (RuntimeException ex) {
            sync.close();
            throw ex;
        }
        try {
            var model = OpenAiChatModel.builder().openAiClient(sync).openAiClientAsync(async)
                    .options(OpenAiChatOptions.builder().model(properties.model()).timeout(TIMEOUT).maxRetries(0).maxCompletionTokens(160).build()).build();
            return new Setup(model, sync::close, async::close);
        }
        catch (RuntimeException ex) {
            sync.close();
            async.close();
            throw ex;
        }
    }

    SpringAiCourseHomeClient(ChatModel model, AutoCloseable syncTransport, AutoCloseable asyncTransport, MeterRegistry meters) {
        this.model = model;
        this.syncTransport = syncTransport;
        this.asyncTransport = asyncTransport;
        this.meters = meters;
    }

    @Override
    public CompletableFuture<Optional<String>> generate(Input input) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                var response = model.call(new Prompt(List.of(new SystemMessage(input.systemPrompt()), new UserMessage(input.userPrompt()))));
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

    private void record(String outcome) {
        meters.counter("artemis.course.home.ai.request", "outcome", outcome).increment();
    }

    @Override
    public void close() {
        workers.shutdownNow();
        closeTransport(syncTransport);
        closeTransport(asyncTransport);
    }

    private void closeTransport(AutoCloseable transport) {
        try {
            transport.close();
        }
        catch (Exception ex) {
            record("close_failure");
        }
    }
}
