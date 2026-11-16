package de.tum.cit.aet.artemis.metis.config;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import de.tum.cit.aet.artemis.core.async.ExceptionHandlingAsyncTaskExecutor;

/**
 * Spring configuration of the Metis module: registers {@link MetisProperties}, builds the LLM clients of the module and
 * its executor.
 * <p>
 * The clients are deliberately not exposed as {@code ChatModel} or {@code ChatClient} beans. Such a bean would join the
 * shared Spring AI configuration, which takes the first chat model it finds, and the by-type mocks of the test bases.
 * Both clients are built here from {@code artemis.metis.llm.*} alone and handed out inside the
 * {@link MetisChatClients} holder, so student code only ever reaches the Metis endpoint and never the endpoint of
 * {@code spring.ai.openai.*}.
 */
@Lazy
@Configuration(proxyBeanMethods = false)
@Conditional(MetisEnabled.class)
@EnableConfigurationProperties(MetisProperties.class)
public class MetisConfiguration {

    /** Name of the holder bean with the two Metis LLM clients. */
    public static final String METIS_CHAT_CLIENTS = "metisChatClients";

    /** Name of the executor bean that runs generation and rating backfill tasks. */
    public static final String METIS_TASK_EXECUTOR = "metisTaskExecutor";

    private static final Logger log = LoggerFactory.getLogger(MetisConfiguration.class);

    /**
     * Builds the batch client (generation, rating backfill) and the interactive client (live rating, regeneration) of
     * the configured endpoint. Building them opens no connection.
     *
     * @param properties the Metis configuration
     * @return the holder with both clients, or a holder without clients while the endpoint is not configured
     */
    @Bean(name = METIS_CHAT_CLIENTS)
    @Lazy
    MetisChatClients metisChatClients(MetisProperties properties) {
        MetisProperties.Llm llm = properties.llm();
        if (!llm.isConfigured()) {
            log.warn("Metis LLM endpoint is not configured (artemis.metis.llm.base-url and model); generation and rating are paused");
            return new MetisChatClients(null, null, "");
        }
        OpenAiChatOptions batchOptions = chatOptions(llm, false);
        OpenAiChatOptions interactiveOptions = chatOptions(llm, true);
        log.info("Configuring the Metis LLM clients: base-url={}, model={}, hosting={}", batchOptions.getBaseUrl(), batchOptions.getModel(), llm.hosting());
        return new MetisChatClients(chatClient(batchOptions), chatClient(interactiveOptions), batchOptions.getModel());
    }

    /**
     * The executor of the generation and rating backfill tasks.
     * <p>
     * Its pool and its queue both have the size {@code generation.max-concurrent-tasks}: a finishing task submits its
     * successor while its own thread is still busy, which a queue of zero would reject, and the dispatcher keeps queued
     * plus running tasks at the pool size. A full executor rejects (abort policy) instead of running the task on the
     * submitting thread, because generation must never run on the scheduler thread. The wrapper carries the submitter's
     * authentication over to the task. Under the {@code test} profile the tasks run synchronously on the caller.
     *
     * @param properties  the Metis configuration
     * @param environment the environment, to detect the test profile
     * @return the executor
     */
    @Bean(name = METIS_TASK_EXECUTOR)
    @Lazy
    Executor metisTaskExecutor(MetisProperties properties, Environment environment) {
        if (environment.acceptsProfiles(Profiles.of(SPRING_PROFILE_TEST))) {
            return new SyncTaskExecutor();
        }
        int size = properties.generation().maxConcurrentTasks();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(size);
        executor.setMaxPoolSize(size);
        executor.setQueueCapacity(size);
        executor.setThreadNamePrefix("metis-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return new ExceptionHandlingAsyncTaskExecutor(executor);
    }

    /**
     * The OpenAI SDK appends {@code /chat/completions} itself, so the configured root must not end with a slash.
     *
     * @param baseUrl the configured endpoint root
     * @return the root without surrounding whitespace and trailing slashes
     */
    static String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.strip();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * The options of one of the two clients, built from {@code artemis.metis.llm.*} alone. Both clients use the same
     * endpoint, key, model and temperature and differ in timeout and retries only: the batch client gets the batch
     * timeout and {@code batch-max-retries} SDK retries; the interactive client gets the interactive timeout and is never
     * retried, because a student or an instructor waits for it and its timeout alone bounds that wait.
     *
     * @param llm         the configuration of the endpoint, with base URL and model set
     * @param interactive whether the options are for the interactive client rather than the batch client
     * @return the options of that client
     */
    static OpenAiChatOptions chatOptions(MetisProperties.Llm llm, boolean interactive) {
        Duration timeout = Duration.ofSeconds(interactive ? llm.interactiveTimeoutSeconds() : llm.batchTimeoutSeconds());
        int maxRetries = interactive ? 0 : llm.batchMaxRetries();
        // An empty key selects the no-auth mode of the SDK; null would make it fall back to the OPENAI_API_KEY environment variable
        String apiKey = llm.apiKey().strip();
        return OpenAiChatOptions.builder().baseUrl(normalizeBaseUrl(llm.baseUrl())).apiKey(apiKey).model(llm.model().strip()).temperature(llm.temperature()).timeout(timeout)
                .maxRetries(maxRetries).build();
    }

    private static ChatClient chatClient(OpenAiChatOptions options) {
        return ChatClient.builder(OpenAiChatModel.builder().options(options).build()).build();
    }

    /**
     * Holder that keeps the Metis clients out of unqualified {@code ChatModel} and {@code ChatClient} injection.
     *
     * @param batch       the client of generation and rating backfill, with SDK retries; null while the endpoint is not configured
     * @param interactive the client of the calls a person waits for, without retries; null while the endpoint is not configured
     * @param model       the configured model id, the fallback when the endpoint reports none; empty while not configured
     */
    public record MetisChatClients(@Nullable ChatClient batch, @Nullable ChatClient interactive, String model) {

        /**
         * Whether both clients exist, i.e. the endpoint is configured.
         *
         * @return true if calls can be made
         */
        public boolean isConfigured() {
            return batch != null && interactive != null;
        }
    }
}
