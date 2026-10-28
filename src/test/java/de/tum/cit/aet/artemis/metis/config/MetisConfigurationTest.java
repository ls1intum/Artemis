package de.tum.cit.aet.artemis.metis.config;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.context.properties.bind.UnboundConfigurationPropertiesException;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.async.ExceptionHandlingAsyncTaskExecutor;
import de.tum.cit.aet.artemis.metis.util.MetisTestProperties;

class MetisConfigurationTest {

    private static final String[] CONFIGURED_ENDPOINT = { "artemis.metis.llm.base-url=http://localhost:11434/v1/", "artemis.metis.llm.model=qwen3-coder:30b" };

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withUserConfiguration(MetisConfiguration.class)
            .withPropertyValues("artemis.metis.enabled=true");

    @Test
    void shouldBuildBothClientsWithoutChatBeansWhenTheEndpointIsConfigured() {
        contextRunner.withPropertyValues(CONFIGURED_ENDPOINT).run(context -> {
            assertThat(context).hasNotFailed();
            MetisConfiguration.MetisChatClients clients = chatClients(context);
            assertThat(clients.isConfigured()).isTrue();
            assertThat(clients.batch()).isNotNull().isNotSameAs(clients.interactive());
            assertThat(clients.interactive()).isNotNull();
            assertThat(clients.model()).isEqualTo("qwen3-coder:30b");
            // a ChatModel or ChatClient bean would join the shared Spring AI configuration and the by-type mocks of the test bases
            assertThat(context).doesNotHaveBean(ChatModel.class).doesNotHaveBean(ChatClient.class);

            MetisProperties.Llm llm = context.getBean(MetisProperties.class).llm();
            OpenAiChatOptions batch = MetisConfiguration.chatOptions(llm, false);
            assertThat(batch.getTimeout()).isEqualTo(Duration.ofSeconds(180));
            assertThat(batch.getMaxRetries()).isEqualTo(2);
            OpenAiChatOptions interactive = MetisConfiguration.chatOptions(llm, true);
            assertThat(interactive.getTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(interactive.getMaxRetries()).as("a person waits for an interactive call, so it is never retried").isZero();
            // the SDK appends /chat/completions itself, so the configured trailing slash is gone
            assertSameEndpoint(batch, "http://localhost:11434/v1", "", "qwen3-coder:30b", 0.2);
            assertSameEndpoint(interactive, "http://localhost:11434/v1", "", "qwen3-coder:30b", 0.2);
        });
    }

    @Test
    void shouldTakeTimeoutsAndBatchRetriesFromTheConfigurationAndNeverRetryInteractiveCalls() {
        Map<String, String> keys = Map.of("llm.base-url", " https://llm.example.org/api/v1/ ", "llm.model", " qwen3-coder:30b ", "llm.api-key", " secret-key ", "llm.temperature",
                "0.7", "llm.batch-timeout-seconds", "90", "llm.interactive-timeout-seconds", "30", "llm.batch-max-retries", "5");
        MetisProperties.Llm llm = MetisTestProperties.with(keys).llm();

        OpenAiChatOptions batch = MetisConfiguration.chatOptions(llm, false);
        OpenAiChatOptions interactive = MetisConfiguration.chatOptions(llm, true);

        assertThat(batch.getTimeout()).isEqualTo(Duration.ofSeconds(90));
        assertThat(batch.getMaxRetries()).isEqualTo(5);
        assertThat(interactive.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(interactive.getMaxRetries()).as("a person waits for an interactive call, so it is never retried").isZero();
        assertSameEndpoint(batch, "https://llm.example.org/api/v1", "secret-key", "qwen3-coder:30b", 0.7);
        assertSameEndpoint(interactive, "https://llm.example.org/api/v1", "secret-key", "qwen3-coder:30b", 0.7);
    }

    @Test
    void shouldStartWithoutClientsWhenNothingIsConfigured() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            MetisConfiguration.MetisChatClients clients = chatClients(context);
            assertThat(clients.isConfigured()).isFalse();
            assertThat(clients.batch()).isNull();
            assertThat(clients.interactive()).isNull();
            assertThat(clients.model()).isEmpty();
        });
    }

    @Test
    void shouldStartWithoutClientsWhenTheModelIsMissing() {
        contextRunner.withPropertyValues("artemis.metis.llm.base-url=http://localhost:11434/v1").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(chatClients(context).isConfigured()).isFalse();
        });
    }

    @Test
    void shouldAcceptAnEmptyApiKey() {
        contextRunner.withPropertyValues(CONFIGURED_ENDPOINT).withPropertyValues("artemis.metis.llm.api-key=").run(context -> {
            assertThat(context).hasNotFailed();
            MetisProperties.Llm llm = context.getBean(MetisProperties.class).llm();
            assertThat(llm.apiKey()).isEmpty();
            assertThat(chatClients(context).isConfigured()).isTrue();
            // the key reaches both clients as an empty string, which selects the no-auth mode of the SDK
            assertThat(MetisConfiguration.chatOptions(llm, false).getApiKey()).isNotNull().isEmpty();
            assertThat(MetisConfiguration.chatOptions(llm, true).getApiKey()).isNotNull().isEmpty();
        });
    }

    @Test
    void shouldFailWhenAnUnknownKeyIsConfigured() {
        contextRunner.withPropertyValues("artemis.metis.llm.unknown=x").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(UnboundConfigurationPropertiesException.class);
        });
    }

    @Test
    void shouldFailWhenTheConcurrentTasksAreNegative() {
        contextRunner.withPropertyValues("artemis.metis.generation.max-concurrent-tasks=-1").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(BindValidationException.class);
        });
    }

    @Test
    void shouldCreateNothingWhenTheModuleIsDisabled() {
        new ApplicationContextRunner().withUserConfiguration(MetisConfiguration.class).withPropertyValues("artemis.metis.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MetisProperties.class).doesNotHaveBean(MetisConfiguration.METIS_CHAT_CLIENTS)
                    .doesNotHaveBean(MetisConfiguration.METIS_TASK_EXECUTOR);
        });
    }

    @Test
    void shouldBindTheDefaultsOfTheRecord() {
        contextRunner.run(context -> {
            MetisProperties properties = context.getBean(MetisProperties.class);
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.llm().baseUrl()).isEmpty();
            assertThat(properties.llm().apiKey()).isEmpty();
            assertThat(properties.llm().model()).isEmpty();
            assertThat(properties.llm().hosting()).isEqualTo(MetisProperties.Hosting.LOCAL);
            assertThat(properties.llm().temperature()).isEqualTo(0.2);
            assertThat(properties.llm().batchTimeoutSeconds()).isEqualTo(180);
            assertThat(properties.llm().interactiveTimeoutSeconds()).isEqualTo(60);
            assertThat(properties.llm().batchMaxRetries()).isEqualTo(2);
            assertThat(properties.llm().maxConcurrentCalls()).isEqualTo(4);
            assertThat(properties.llm().interactivePermitWaitSeconds()).isEqualTo(10);
            assertThat(properties.generation().schedulerEnabled()).isTrue();
            assertThat(properties.generation().pollIntervalSeconds()).isEqualTo(30);
            assertThat(properties.generation().delayAfterDueDateSeconds()).isEqualTo(60);
            assertThat(properties.generation().enableDelayMinutes()).isEqualTo(10);
            assertThat(properties.generation().horizonDays()).isEqualTo(30);
            assertThat(properties.generation().maxConcurrentTasks()).isEqualTo(2);
            assertThat(properties.generation().minChangedLines()).isEqualTo(5);
            assertThat(properties.generation().maxFocusFileCharacters()).isEqualTo(12000);
            assertThat(properties.generation().maxPromptCharacters()).isEqualTo(60000);
            assertThat(properties.generation().maxProblemStatementCharacters()).isEqualTo(6000);
            assertThat(properties.generation().maxTransientFailures()).isEqualTo(3);
            assertThat(properties.conversation().answerGraceSeconds()).isEqualTo(15);
            assertThat(properties.conversation().maxAnswerCharacters()).isEqualTo(10000);
            assertThat(properties.answerSettleTime()).isEqualTo(Duration.ofSeconds(85));
            // the unit test helper binds exactly what the application binds
            assertThat(MetisTestProperties.with(Map.of("enabled", "true"))).isEqualTo(properties);
        });
    }

    @Test
    void shouldBindTheHostingRegardlessOfCase() {
        contextRunner.withPropertyValues("artemis.metis.llm.hosting=cloud").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(MetisProperties.class).llm().hosting()).isEqualTo(MetisProperties.Hosting.CLOUD);
        });
    }

    @Test
    void shouldRunTasksSynchronouslyUnderTheTestProfile() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles(SPRING_PROFILE_TEST))
                .run(context -> assertThat(context.getBean(MetisConfiguration.METIS_TASK_EXECUTOR, Executor.class)).isInstanceOf(SyncTaskExecutor.class));
    }

    @Test
    void shouldSizeThePoolAndTheQueueByTheConcurrentTasks() {
        contextRunner.withPropertyValues("artemis.metis.generation.max-concurrent-tasks=3").run(context -> {
            Executor executor = context.getBean(MetisConfiguration.METIS_TASK_EXECUTOR, Executor.class);
            assertThat(executor).isInstanceOf(ExceptionHandlingAsyncTaskExecutor.class);
            ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) ReflectionTestUtils.getField(executor, "executor");
            assertThat(pool).isNotNull();
            assertThat(pool.getCorePoolSize()).isEqualTo(3);
            assertThat(pool.getMaxPoolSize()).isEqualTo(3);
            assertThat(pool.getQueueCapacity()).isEqualTo(3);
            assertThat(pool.getThreadNamePrefix()).isEqualTo("metis-");
            // generation must never fall back to running on the submitting thread
            assertThat(pool.getThreadPoolExecutor().getRejectedExecutionHandler()).isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
        });
    }

    @Test
    void shouldRejectATaskWhenThePoolAndTheQueueAreFull() {
        contextRunner.withPropertyValues("artemis.metis.generation.max-concurrent-tasks=1").run(context -> {
            Executor executor = context.getBean(MetisConfiguration.METIS_TASK_EXECUTOR, Executor.class);
            CountDownLatch running = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            try {
                executor.execute(() -> {
                    running.countDown();
                    awaitQuietly(release);
                });
                assertThat(running.await(10, TimeUnit.SECONDS)).isTrue();
                // the successor a finishing task submits while its own thread is still busy waits in the queue
                executor.execute(() -> awaitQuietly(release));
                assertThatThrownBy(() -> executor.execute(() -> {
                })).isInstanceOf(RejectedExecutionException.class);
            }
            finally {
                release.countDown();
            }
        });
    }

    @Test
    void shouldStripWhitespaceAndTrailingSlashesFromTheBaseUrl() {
        assertThat(MetisConfiguration.normalizeBaseUrl(" http://localhost:11434/v1// ")).isEqualTo("http://localhost:11434/v1");
        assertThat(MetisConfiguration.normalizeBaseUrl("https://llm.example.org/api/v1")).isEqualTo("https://llm.example.org/api/v1");
    }

    @Test
    void shouldTreatBlankValuesAsNotConfigured() {
        assertThat(MetisTestProperties.with(Map.of("llm.base-url", "  ", "llm.model", "qwen3-coder:30b")).llm().isConfigured()).isFalse();
        assertThat(MetisTestProperties.with(Map.of("llm.base-url", "http://localhost:11434/v1", "llm.model", " ")).llm().isConfigured()).isFalse();
        assertThat(MetisTestProperties.with(Map.of("llm.base-url", "http://localhost:11434/v1", "llm.model", "qwen3-coder:30b")).llm().isConfigured()).isTrue();
    }

    @Test
    void shouldKeepTheApiKeyOutOfTheTextOfTheConfiguration() {
        MetisProperties properties = MetisTestProperties.with(Map.of("llm.api-key", "very-secret-key"));
        assertThat(properties.llm().toString()).doesNotContain("very-secret-key").contains("<redacted>");
        assertThat(properties.toString()).doesNotContain("very-secret-key");
    }

    private static MetisConfiguration.MetisChatClients chatClients(AssertableApplicationContext context) {
        return context.getBean(MetisConfiguration.METIS_CHAT_CLIENTS, MetisConfiguration.MetisChatClients.class);
    }

    /**
     * Asserts the settings that the batch and the interactive client share: endpoint, key, model and temperature.
     */
    private static void assertSameEndpoint(OpenAiChatOptions options, String expectedBaseUrl, String expectedApiKey, String expectedModel, double expectedTemperature) {
        assertThat(options.getBaseUrl()).isEqualTo(expectedBaseUrl);
        // never null: a null key would make the SDK fall back to the OPENAI_API_KEY environment variable
        assertThat(options.getApiKey()).isNotNull().isEqualTo(expectedApiKey);
        assertThat(options.getModel()).isEqualTo(expectedModel);
        assertThat(options.getTemperature()).isEqualTo(expectedTemperature);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
