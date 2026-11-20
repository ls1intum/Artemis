package de.tum.cit.aet.artemis.core.config.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.hyperion.config.HyperionLlmPresets;

/**
 * Starts the real catalog binding and validator in a small core context, so that YAML keys, environment variables and the startup error all behave as on a
 * server. The log assertions need Logback undisturbed by Spring contexts that start in parallel.
 */
@Isolated
class LlmConfigurationValidatorTest {

    private static final String[] CATALOG = { "artemis.llm.endpoints.logos.base-url=https://logos.example.org/v1", "artemis.llm.endpoints.logos.api-key=key",
            "artemis.llm.presets.large.endpoint=logos", "artemis.llm.presets.large.model=gpt-6.1-terra", "artemis.llm.presets.large.api=responses",
            "artemis.llm.presets.large.reasoning-effort=medium", "artemis.llm.presets.small.endpoint=logos", "artemis.llm.presets.small.model=gpt-6-luna",
            "artemis.llm.presets.small.api=chat-completions", "artemis.llm.presets.embed.endpoint=logos", "artemis.llm.presets.embed.model=qwen3-embedding",
            "artemis.llm.presets.embed.api=embeddings" };

    private final List<ILoggingEvent> logs = new ArrayList<>();

    @Test
    void validCatalogStartsAndLogsEveryAssignmentWithItsSource() {
        captureLogs(
                () -> runner(List.of(single("artemis.deimos.llm-preset", "small")), "artemis.hyperion.llm-presets.default=large", "artemis.hyperion.llm-presets.faq-rewrite=small")
                        .run(context -> assertThat(context).hasNotFailed()));

        assertThat(messages(Level.INFO))
                .anySatisfy(line -> assertThat(line).startsWith("LLM artemis.hyperion.llm-presets.faq-rewrite -> small |").contains("gpt-6-luna", "chat-completions",
                        "logos.example.org", "timeout PT5M", "retries 3", "auth key", "no fallback price"))
                .anySatisfy(line -> assertThat(line).startsWith("LLM artemis.hyperion.llm-presets.checklist -> large (default) |"))
                .anySatisfy(line -> assertThat(line).startsWith("LLM artemis.deimos.llm-preset -> small |"));
    }

    @Test
    void everyProblemIsReportedInOneStartupFailureWithPasteableMissingKeys() {
        runner(List.of(single("artemis.deimos.llm-preset", null), single("artemis.atlas.atlasllm.llm-presets.chat", "unknown")), "artemis.llm.endpoints.broken.base-url=ftp://x",
                "artemis.llm.presets.bad-name.endpoint=logos", "artemis.llm.presets.bad-name.model=m", "artemis.llm.presets.bad-name.api=responses").run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootMessage(context.getStartupFailure())).startsWith("Invalid LLM configuration (see https://docs.artemis.tum.de/admin/llm-configuration):")
                            .contains("artemis.llm.endpoints.broken.base-url must be an http or https URL").contains("artemis.llm.presets.bad-name: preset names must match")
                            .contains("artemis.atlas.atlasllm.llm-presets.chat names the unknown preset unknown")
                            .contains("Missing preset assignments for active modules; add:\n      artemis.deimos.llm-preset: <preset>")
                            .contains("artemis.hyperion.llm-presets.problem-statement: <preset>   (or artemis.hyperion.llm-presets.default: <preset>)");
                });
    }

    @Test
    void presetRulesAreChecked() {
        runner(List.of(single("artemis.deimos.llm-preset", "small")), "artemis.hyperion.llm-presets.default=large", "artemis.llm.endpoints.foundry.type=microsoft-foundry",
                "artemis.llm.endpoints.foundry.base-url=https://resource.openai.azure.com", "artemis.llm.presets.hot.endpoint=logos", "artemis.llm.presets.hot.model=m",
                "artemis.llm.presets.hot.api=responses", "artemis.llm.presets.hot.reasoning-effort=high", "artemis.llm.presets.hot.temperature=0.5",
                "artemis.llm.presets.hot.max-tokens=10", "artemis.llm.presets.hot.max-completion-tokens=10", "artemis.llm.presets.vec.endpoint=foundry",
                "artemis.llm.presets.vec.model=e", "artemis.llm.presets.vec.api=embeddings", "artemis.llm.presets.vec.temperature=0", "artemis.llm.presets.vec.timeout=5s",
                "artemis.llm.presets.lost.model=m", "artemis.llm.presets.lost.api=responses").run(context -> {
                    String message = rootMessage(context.getStartupFailure());
                    assertThat(message).contains("artemis.llm.endpoints.foundry.api-key is required for type microsoft-foundry")
                            .contains("artemis.llm.presets.hot: temperature is only allowed without reasoning-effort or with reasoning-effort none")
                            .contains("artemis.llm.presets.hot: set at most one of max-tokens and max-completion-tokens")
                            .contains("artemis.llm.presets.vec: an embeddings preset takes no reasoning-effort, temperature")
                            .contains("artemis.llm.presets.vec: embeddings need an endpoint of type openai")
                            .contains("artemis.llm.presets.vec: an embeddings preset takes no timeout or max-retries").contains("artemis.llm.presets.lost.endpoint is required");
                });
    }

    @Test
    void temperatureIsAllowedWithReasoningEffortNone() {
        runner(List.of(single("artemis.deimos.llm-preset", "cold")), "artemis.hyperion.llm-presets.default=large", "artemis.llm.presets.cold.endpoint=logos",
                "artemis.llm.presets.cold.model=m", "artemis.llm.presets.cold.api=chat-completions", "artemis.llm.presets.cold.reasoning-effort=none",
                "artemis.llm.presets.cold.temperature=0").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void assignmentsNeedThePresetKindTheyUse() {
        var embedding = LlmPresetAssignment.ofSingle("artemis.weaviate.embedding-llm-preset", "small", LlmPresetAssignment.Usage.EMBEDDING);
        runner(List.of(() -> List.of(embedding), single("artemis.deimos.llm-preset", "embed")), "artemis.hyperion.llm-presets.default=large").run(context -> {
            String message = rootMessage(context.getStartupFailure());
            assertThat(message).contains("artemis.weaviate.embedding-llm-preset needs a preset with api embeddings, but small uses chat-completions")
                    .contains("artemis.deimos.llm-preset needs a chat preset (api responses or chat-completions), but embed uses embeddings");
        });
    }

    @Test
    void exerciseGenerationNeedsNoSdkRetriesOnThePresetOrItsEndpoint() {
        LlmPresetAssignments generation = () -> List
                .of(LlmPresetAssignment.ofSingle("artemis.hyperion.agent.llm-preset", "large", LlmPresetAssignment.Usage.CHAT).withoutRetries());
        runner(List.of(generation), "artemis.hyperion.llm-presets.default=large").run(context -> assertThat(rootMessage(context.getStartupFailure()))
                .contains("artemis.hyperion.agent.llm-preset: exercise generation retries on its own and needs max-retries 0 on preset large or its endpoint"));
        runner(List.of(generation), "artemis.hyperion.llm-presets.default=large", "artemis.llm.presets.large.max-retries=0").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void priceListsAreCheckedAndAPresetWithoutItsPriceOnlyWarns() {
        captureLogs(() -> runner(List.of(), "artemis.hyperion.llm-presets.default=large", "artemis.llm.endpoints.logos.prices-usd-per-million[0].model=gpt-6-luna",
                "artemis.llm.endpoints.logos.prices-usd-per-million[0].input=0.1", "artemis.llm.endpoints.logos.prices-usd-per-million[0].output=0.5")
                .run(context -> assertThat(context).hasNotFailed()));
        assertThat(messages(Level.WARN))
                .anySatisfy(line -> assertThat(line).contains("LLM preset large uses model gpt-6.1-terra on endpoint logos, whose price list has no entry"));

        runner(List.of(), "artemis.hyperion.llm-presets.default=large", "artemis.llm.endpoints.logos.prices-usd-per-million[0].model=m",
                "artemis.llm.endpoints.logos.prices-usd-per-million[0].input=1", "artemis.llm.endpoints.logos.prices-usd-per-million[1].model=m",
                "artemis.llm.endpoints.logos.prices-usd-per-million[1].input=1", "artemis.llm.endpoints.logos.prices-usd-per-million[1].output=-1").run(context -> {
                    String message = rootMessage(context.getStartupFailure());
                    assertThat(message).contains("artemis.llm.endpoints.logos.prices-usd-per-million[0]: input and output are required and must not be negative")
                            .contains("artemis.llm.endpoints.logos.prices-usd-per-million[1]: model m is listed twice");
                });
    }

    @Test
    void environmentVariablesBindAndMisspeltPresetMapKeysAreRejected() {
        runner(List.of(), "artemis.hyperion.llm-presets.default=large")
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of("ARTEMIS_HYPERION_LLMPRESETS_FAQREWRITE", "small", "ARTEMIS_LLM_PRESETS_SMALL_MAXCOMPLETIONTOKENS", "77"))))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(HyperionLlmPresets.class).presetFor(HyperionLlmPresets.Task.FAQ_REWRITE)).isEqualTo("small");
                    assertThat(context.getBean(LlmProperties.class).preset("small").maxCompletionTokens()).isEqualTo(77);
                });
        runner(List.of(), "artemis.hyperion.llm-presets.default=large")
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of("ARTEMIS_HYPERION_LLMPRESETS_FAQREWRIT", "small", "ARTEMIS_HYPERION_LLMPRESETS_FAQ_REWRITE", "small"))))
                .run(context -> assertThat(rootMessage(context.getStartupFailure())).contains("artemis.hyperion.llmpresets.faqrewrit names no task of this llm-presets map")
                        .contains("artemis.hyperion.llmpresets.faq.rewrite names no task of this llm-presets map"));
    }

    @Test
    void aMisspeltMapNameIsRejectedInsteadOfLeavingTheTaskOnTheDefault() {
        LlmPresetAssignments atlas = () -> List.of(LlmPresetAssignment.ofMap("artemis.atlas.atlasllm.llm-presets", "chat", null, "large"));
        runner(List.of(atlas), "artemis.hyperion.llm-presets.default=large", "artemis.hyperion.llm-preset.faq-rewrite=small")
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of("ARTEMIS_HYPERION_LLM_PRESETS_CHECKLIST", "small", "ARTEMIS_ATLAS_ATLAS_LLM_LLMPRESETS_CHAT", "small"))))
                .run(context -> assertThat(rootMessage(context.getStartupFailure()))
                        .contains("artemis.hyperion.llm-preset.faq-rewrite is no key; the presets of this module go under artemis.hyperion.llm-presets")
                        .contains("artemis.hyperion.llm.presets.checklist is no key; the presets of this module go under artemis.hyperion.llm-presets")
                        .contains("artemis.atlas.atlas.llm.llmpresets.chat is no key; the presets of this module go under artemis.atlas.atlasllm.llm-presets"));
        // the canonical spellings, also with dashes in YAML, are accepted
        runner(List.of(atlas), "artemis.hyperion.llm-presets.default=large", "artemis.atlas.atlas-llm.llm-presets.chat=small")
                .withInitializer(context -> context.getEnvironment().getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, Map.of("ARTEMIS_HYPERION_LLMPRESETS_CHECKLIST", "small"))))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void removedKeysOnlyWarnAndAreListed() {
        captureLogs(() -> runner(List.of(), "artemis.hyperion.llm-presets.default=large", "spring.ai.openai.api-key=old", "artemis.llm.model-costs.gpt5.input=1",
                "artemis.weaviate.gpu-api-key=old", "artemis.hyperion.agent.profiles.fast.reasoning-effort=low", "artemis.hyperion.agent.profiles.fast.max-turns=10")
                .run(context -> assertThat(context).hasNotFailed()));

        assertThat(messages(Level.WARN)).anySatisfy(line -> assertThat(line).startsWith("These LLM configuration keys are no longer read and are ignored:")
                .contains("spring.ai.openai", "artemis.llm.model-costs", "artemis.weaviate.gpu-api-key", "artemis.hyperion.agent.profiles.fast.reasoning-effort")
                .doesNotContain("max-turns"));
    }

    private static LlmPresetAssignments single(String key, @Nullable String preset) {
        return () -> List.of(LlmPresetAssignment.ofSingle(key, preset, LlmPresetAssignment.Usage.CHAT));
    }

    private static ApplicationContextRunner runner(List<LlmPresetAssignments> modules, String... properties) {
        var runner = new ApplicationContextRunner().withInitializer(context -> context.getEnvironment().setActiveProfiles("core"))
                .withInitializer(context -> context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(LlmConfiguration.class, LlmConfigurationValidator.class, HyperionPresetsConfiguration.class).withPropertyValues(CATALOG)
                .withPropertyValues(properties);
        for (int i = 0; i < modules.size(); i++) {
            LlmPresetAssignments module = modules.get(i);
            runner = runner.withBean("module" + i, LlmPresetAssignments.class, () -> module);
        }
        return runner;
    }

    private void captureLogs(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(LlmConfigurationValidator.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        }
        finally {
            logger.detachAppender(appender);
            appender.stop();
            logs.addAll(appender.list);
        }
    }

    private List<String> messages(Level level) {
        return logs.stream().filter(event -> event.getLevel() == level).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static String rootMessage(@Nullable Throwable failure) {
        assertThat(failure).isNotNull();
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    @EnableConfigurationProperties(HyperionLlmPresets.class)
    static class HyperionPresetsConfiguration {
    }
}
