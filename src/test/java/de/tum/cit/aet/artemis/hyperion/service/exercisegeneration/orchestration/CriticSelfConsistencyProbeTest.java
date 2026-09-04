package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.ProviderFailureCooldown;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityCriticService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.SpecFidelityReport;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Critic test-retest reliability probe. Calls {@link SpecFidelityCriticService#critique} N times against a real provider on one frozen candidate and writes every finding to disk;
 * nothing about the candidate changes between calls, so all observed disagreement is the reviewer's own sampling noise. It costs real provider calls and therefore runs only under
 * {@code HYPERION_CRITIC_PROBE=true}.
 * <p>
 * Finding identity is taken from {@code RepairRoundScheduler.findingIdentity} by reflection, so the probe measures the pipeline's own identity function.
 */
class CriticSelfConsistencyProbeTest {

    private static final Logger log = LoggerFactory.getLogger(CriticSelfConsistencyProbeTest.class);

    private static final Path REPO_ROOT = Path.of(System.getProperty("user.dir"));

    /**
     * Probe configuration, read from this file rather than the environment: a Gradle test JVM inherits the environment of a long-lived daemon, so an exported variable is not
     * reliably visible here. Environment and system properties still win when set.
     */
    private static final Path CONFIG_FILE = REPO_ROOT.resolve(".ai/eval/critic-self-consistency/probe.properties");

    private static final java.util.Properties CONFIG = loadConfig();

    private static java.util.Properties loadConfig() {
        java.util.Properties properties = new java.util.Properties();
        if (Files.isRegularFile(CONFIG_FILE)) {
            try (var reader = Files.newBufferedReader(CONFIG_FILE)) {
                properties.load(reader);
            }
            catch (IOException e) {
                throw new IllegalStateException("Could not read " + CONFIG_FILE, e);
            }
        }
        return properties;
    }

    @SuppressWarnings("unused")
    static boolean probeEnabled() {
        return "true".equals(env("HYPERION_CRITIC_PROBE", "false"));
    }

    @Test
    @EnabledIf("probeEnabled")
    void criticSelfConsistency() throws Exception {
        Path candidateDir = REPO_ROOT.resolve(env("HYPERION_CRITIC_PROBE_CANDIDATE", ".ai/waveC/recursion"));
        Path outputDir = REPO_ROOT.resolve(env("HYPERION_CRITIC_PROBE_OUT", ".ai/eval/critic-self-consistency/out"));
        int firstRun = Integer.parseInt(env("HYPERION_CRITIC_PROBE_FIRST_RUN", "1"));
        int runs = Integer.parseInt(env("HYPERION_CRITIC_PROBE_RUNS", "20"));
        Files.createDirectories(outputDir);
        attachConsoleLogging();

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> candidate = mapper.readValue(Files.readString(candidateDir.resolve("summary.json")), Map.class);
        @SuppressWarnings("unchecked")
        String brief = (String) ((Map<String, Object>) candidate.get("scenario")).get("requirements");
        String problemStatement = Files.readString(candidateDir.resolve("problem-statement.md"));
        String specDocument = Files.readString(candidateDir.resolve("SPEC.md"));
        List<String> testNames = GenerationOrchestrationService.extractTaskBoundTestNames(problemStatement);

        Map<RepositoryType, Map<String, String>> artifacts = new LinkedHashMap<>();
        artifacts.put(RepositoryType.SOLUTION, readRepository(candidateDir.resolve("solution")));
        artifacts.put(RepositoryType.TEMPLATE, readRepository(candidateDir.resolve("template")));
        artifacts.put(RepositoryType.TESTS, readRepository(candidateDir.resolve("tests")));

        log.info("{}", "[probe] candidate=" + candidateDir + " testNames=" + testNames.size() + " solutionFiles=" + artifacts.get(RepositoryType.SOLUTION).size()
                + " templateFiles=" + artifacts.get(RepositoryType.TEMPLATE).size() + " testFiles=" + artifacts.get(RepositoryType.TESTS).size());

        Method identityMethod = RepairRoundScheduler.class.getDeclaredMethod("findingIdentity", SpecFidelityReport.Finding.class);
        identityMethod.setAccessible(true);

        String model = require("SPRING_AI_OPENAI_CHAT_MODEL");
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class, OpenAiChatAutoConfiguration.class))
                .withPropertyValues("spring.ai.openai.base-url=" + require("SPRING_AI_OPENAI_BASE_URL"), "spring.ai.openai.api-key=" + require("SPRING_AI_OPENAI_API_KEY"),
                        "spring.ai.openai.chat.model=" + model, "spring.ai.openai.chat.temperature=" + env("SPRING_AI_OPENAI_CHAT_TEMPERATURE", "0.4"),
                        "spring.ai.openai.timeout=" + env("SPRING_AI_OPENAI_TIMEOUT", "8m"), "spring.ai.openai.microsoft-foundry=false", "spring.ai.openai.max-retries=1")
                .run(context -> {
                    ChatModel chatModel = context.getBean(ChatModel.class);
                    // Verbatim the production ChatClient bean of SpringAIConfiguration#chatClient.
                    ChatClient chatClient = ChatClient.builder(chatModel).build();
                    log.info("{}", "[probe] model=" + model + " defaultOptions=" + chatModel.getDefaultOptions());
                    SpecFidelityCriticService critic = new SpecFidelityCriticService(chatClient, mapper, new HyperionPromptTemplateService(), model, Duration.ZERO,
                            ProviderFailureCooldown.disabled(), Integer.parseInt(env("HYPERION_CRITIC_PROBE_CONTEXT_TOKENS", "128000")), List.of(chatModel));

                    for (int run = firstRun; run < firstRun + runs; run++) {
                        long startedAt = System.currentTimeMillis();
                        List<Map<String, String>> emitted = new ArrayList<>();
                        List<Map<String, Object>> providerCalls = new ArrayList<>();
                        // The critic hands every ChatResponse to its usage sink, which is the supported seam for seeing the raw reviewer output without reflection.
                        java.util.function.Consumer<org.springframework.ai.chat.model.ChatResponse> usageSink = response -> {
                            Map<String, Object> call = new LinkedHashMap<>();
                            var result = response.getResult();
                            call.put("finishReason", result == null || result.getMetadata() == null ? null : result.getMetadata().getFinishReason());
                            call.put("completionTokens",
                                    response.getMetadata() == null || response.getMetadata().getUsage() == null ? null : response.getMetadata().getUsage().getCompletionTokens());
                            call.put("text", result == null || result.getOutput() == null ? null : result.getOutput().getText());
                            providerCalls.add(call);
                        };
                        String error = null;
                        try {
                            SpecFidelityReport report = critic.critique(brief, problemStatement, testNames, artifacts, usageSink, () -> false, null, specDocument, null, null);
                            for (SpecFidelityReport.Finding finding : report.findings()) {
                                Map<String, String> record = new LinkedHashMap<>();
                                record.put("kind", finding.kind().name());
                                record.put("requirement", finding.requirement());
                                record.put("detail", finding.detail());
                                record.put("identity", (String) identityMethod.invoke(null, finding));
                                record.put("blocking", String.valueOf(finding.isBlocking()));
                                emitted.add(record);
                            }
                        }
                        catch (RuntimeException e) {
                            error = e.getClass().getSimpleName() + ": " + e.getMessage();
                        }
                        Map<String, Object> runRecord = new LinkedHashMap<>();
                        runRecord.put("run", run);
                        runRecord.put("elapsedMs", System.currentTimeMillis() - startedAt);
                        runRecord.put("error", error);
                        runRecord.put("findings", emitted);
                        runRecord.put("providerCalls", providerCalls);
                        FileUtils.writeStringToFile(outputDir.resolve("run-%02d.json".formatted(run)).toFile(),
                                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(runRecord), StandardCharsets.UTF_8);
                        log.info("{}", "[probe] run " + run + ": " + emitted.size() + " findings" + (error == null ? "" : " ERROR " + error) + " in " + runRecord.get("elapsedMs")
                                + " ms");
                    }
                });
    }

    /** The suite's logback appender buffers per Spring test group and never flushes for a standalone class, so a reviewer warning would be invisible. */
    private static void attachConsoleLogging() {
        var context = (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        var encoder = new ch.qos.logback.classic.encoder.PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("[probe-log] %-5level %logger{36} - %msg%n");
        encoder.start();
        var appender = new ch.qos.logback.core.ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.setContext(context);
        appender.setEncoder(encoder);
        appender.start();
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("de.tum.cit.aet.artemis.hyperion");
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        logger.addAppender(appender);
    }

    private static Map<String, String> readRepository(Path root) throws IOException {
        Map<String, String> files = new TreeMap<>();
        if (!Files.isDirectory(root)) {
            return files;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                files.put(root.relativize(path).toString().replace('\\', '/'), new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = System.getProperty(name);
        }
        if (value == null || value.isBlank()) {
            value = CONFIG.getProperty(name);
        }
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String require(String name) {
        String value = env(name, "");
        if (value.isBlank()) {
            throw new IllegalStateException(name + " must be set in the environment or in " + CONFIG_FILE);
        }
        return value;
    }
}
