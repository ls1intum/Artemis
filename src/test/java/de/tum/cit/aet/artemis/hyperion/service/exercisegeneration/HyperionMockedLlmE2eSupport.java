package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.dockerclient.TransportConfig;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.transport.SSLConfig;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * Shared, COMMITTED support for the deterministic mocked-LLM Hyperion end-to-end tests. It replaces the live-GPU-only helper {@code HyperionGpuTestEnvironment} for the committed
 * tests so that a fresh CI checkout (which does not have the gitignored live-GPU files) still compiles.
 * <p>
 * It carries three things the mocked E2E tests need and nothing GPU-specific:
 * <ul>
 * <li>{@link #isDockerAvailable()} — the exact Docker gate the LocalCI integration tests use, so these tests self-skip when no Docker daemon is reachable rather than failing;</li>
 * <li>{@link #useProductionBuildImages(ProgrammingLanguageConfiguration, ProgrammingLanguage...)} — points a language's build image at its real production execution image (the
 * shared test {@code application.yml} points every image at a placeholder so the mocked-build buckets never pull one), copied verbatim from the live-GPU helper;</li>
 * <li>a tiny scripted-{@link ChatModel} DSL ({@link #writeFile}/{@link #bash}/{@link #submit}/{@link #text}) that builds the exact {@link ChatResponse} tool-call turns the real
 * agent loop consumes, so a mocked {@code azureOpenAiChatModel} can drive the REAL loop + REAL sandbox + REAL differential oracle deterministically (the proven pattern from
 * {@code AgentLoopRunnerTest}).</li>
 * </ul>
 */
final class HyperionMockedLlmE2eSupport {

    /** The production execution image per language, keyed by the {@link ProgrammingLanguage} whose {@link ProjectType#PLAIN} (default) image must be overridden. */
    private static final Map<ProgrammingLanguage, String> PRODUCTION_IMAGES = productionImages();

    private HyperionMockedLlmE2eSupport() {
    }

    // ---- Docker gate (identical to the LocalCI integration tests) --------------------------------------------------------------------------------------------------------------

    /**
     * @return whether a Docker daemon is reachable, used as the {@code @EnabledIf} gate for the Docker-backed mocked E2E tests
     */
    static boolean isDockerAvailable() {
        TransportConfig dockerTransportConfig = discoverDockerTransportConfig();
        if (dockerTransportConfig == null) {
            return false;
        }
        try (DockerClient dockerClient = createDockerClient(dockerTransportConfig)) {
            dockerClient.versionCmd().exec();
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    private static TransportConfig discoverDockerTransportConfig() {
        DockerClientFactory dockerClientFactory = DockerClientFactory.instance();
        if (!dockerClientFactory.isDockerAvailable()) {
            return null;
        }
        return dockerClientFactory.getTransportConfig();
    }

    private static DockerClient createDockerClient(TransportConfig dockerTransportConfig) {
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().withDockerHost(dockerTransportConfig.getDockerHost().toString()).build();
        SSLConfig sslConfig = dockerTransportConfig.getSslConfig();
        if (sslConfig == null) {
            sslConfig = config.getSSLConfig();
        }
        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder().dockerHost(config.getDockerHost()).sslConfig(sslConfig).connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(45)).build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    // ---- Build-image override (copied from the live-GPU HyperionGpuTestEnvironment so the committed tests do not depend on a gitignored file) --------------------------------

    private static Map<ProgrammingLanguage, String> productionImages() {
        Map<ProgrammingLanguage, String> images = new LinkedHashMap<>();
        images.put(ProgrammingLanguage.JAVA, "ls1tum/artemis-maven-template:java17-25");
        images.put(ProgrammingLanguage.KOTLIN, "ls1tum/artemis-maven-template:java17-25");
        images.put(ProgrammingLanguage.PYTHON, "ls1tum/artemis-python-docker:v1.1.0");
        images.put(ProgrammingLanguage.C, "ls1tum/artemis-c-minimal-docker:1.0.0");
        images.put(ProgrammingLanguage.GO, "ghcr.io/ls1intum/artemis-go-docker:v1.0.0");
        images.put(ProgrammingLanguage.RUST, "ghcr.io/ls1intum/artemis-rust-docker:v1.2.0");
        images.put(ProgrammingLanguage.C_PLUS_PLUS, "ghcr.io/ls1intum/artemis-cpp-docker:v1.1.2");
        images.put(ProgrammingLanguage.C_SHARP, "ghcr.io/ls1intum/artemis-csharp-docker:v1.0.1");
        images.put(ProgrammingLanguage.DART, "ghcr.io/ls1intum/artemis-dart-docker:v1.1.0");
        images.put(ProgrammingLanguage.SWIFT, "ls1tum/artemis-swift-swiftlint-docker:swift5.9.2");
        images.put(ProgrammingLanguage.HASKELL, "ghcr.io/uni-passau-artemis/artemis-haskell:v22.37.0");
        images.put(ProgrammingLanguage.JAVASCRIPT, "ghcr.io/ls1intum/artemis-javascript-docker:v1.1.0");
        images.put(ProgrammingLanguage.TYPESCRIPT, "ghcr.io/ls1intum/artemis-javascript-docker:v1.1.0");
        images.put(ProgrammingLanguage.RUBY, "ghcr.io/ls1intum/artemis-ruby-docker:v1.0.1");
        images.put(ProgrammingLanguage.R, "ghcr.io/ls1intum/artemis-r-docker:v1.2.0");
        return images;
    }

    /**
     * Points the given languages' {@link ProjectType#PLAIN} (default) build image at their real production execution image, in place on the shared
     * {@link ProgrammingLanguageConfiguration} bean. {@code getImage} falls back to the default entry for every project type, so overriding only PLAIN suffices for PLAIN_MAVEN
     * etc.
     * <p>
     * The Spring test context is cached and shared, so this in-place mutation would otherwise leak into every later test in the same context. The method therefore returns a
     * snapshot of the PLAIN entries it replaced (a {@code null} value meaning the language had no PLAIN entry before); pass it back to {@link #restoreBuildImages} in an
     * {@code @AfterEach} to leave the shared bean exactly as it was.
     *
     * @param config    the shared programming-language configuration bean to mutate
     * @param languages the languages whose default image to switch to the production image (pass none to override every known language)
     * @return the prior PLAIN image per overridden language (value {@code null} when the language had no PLAIN entry), to be handed to {@link #restoreBuildImages}
     */
    static Map<ProgrammingLanguage, String> useProductionBuildImages(ProgrammingLanguageConfiguration config, ProgrammingLanguage... languages) {
        Map<ProgrammingLanguage, Map<ProjectType, String>> images = imagesMap(config);
        Iterable<ProgrammingLanguage> targets = languages.length == 0 ? PRODUCTION_IMAGES.keySet() : List.of(languages);
        Map<ProgrammingLanguage, String> replacedPlainImages = new LinkedHashMap<>();
        for (ProgrammingLanguage language : targets) {
            String image = PRODUCTION_IMAGES.get(language);
            if (image == null) {
                throw new IllegalArgumentException("No production image registered for " + language);
            }
            Map<ProjectType, String> perProjectType = images.computeIfAbsent(language, key -> new EnumMap<>(ProjectType.class));
            replacedPlainImages.put(language, perProjectType.get(ProjectType.PLAIN));
            perProjectType.put(ProjectType.PLAIN, image);
        }
        return replacedPlainImages;
    }

    /**
     * Restores the per-language {@link ProjectType#PLAIN} build images captured by {@link #useProductionBuildImages}, so the in-place override does not leak into the shared
     * (cached) Spring context and silently replace the placeholder image the shared {@code application.yml} sets for every later test.
     *
     * @param config              the shared programming-language configuration bean to restore
     * @param replacedPlainImages the snapshot returned by {@link #useProductionBuildImages} (value {@code null} restores the absence of a PLAIN entry)
     */
    static void restoreBuildImages(ProgrammingLanguageConfiguration config, Map<ProgrammingLanguage, String> replacedPlainImages) {
        Map<ProgrammingLanguage, Map<ProjectType, String>> images = imagesMap(config);
        replacedPlainImages.forEach((language, priorImage) -> {
            Map<ProjectType, String> perProjectType = images.get(language);
            if (perProjectType == null) {
                return;
            }
            if (priorImage == null) {
                perProjectType.remove(ProjectType.PLAIN);
            }
            else {
                perProjectType.put(ProjectType.PLAIN, priorImage);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<ProgrammingLanguage, Map<ProjectType, String>> imagesMap(ProgrammingLanguageConfiguration config) {
        Map<ProgrammingLanguage, Map<ProjectType, String>> images = (Map<ProgrammingLanguage, Map<ProjectType, String>>) ReflectionTestUtils.getField(config, "images");
        if (images == null) {
            throw new IllegalStateException("ProgrammingLanguageConfiguration has no images map to override");
        }
        return images;
    }

    // ---- Scripted ChatModel turns (the AgentLoopRunnerTest pattern) -------------------------------------------------------------------------------------------------------------

    /**
     * A model turn that calls one tool with the given raw JSON arguments. A text-only turn ({@link #text}) or a {@code submit} tool call ends the loop.
     *
     * @param name      the tool name (read_file/write_file/edit_file/bash/verify/submit)
     * @param arguments the tool-call arguments as a JSON object string
     * @return the scripted response
     */
    static ChatResponse toolCall(String name, String arguments) {
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall("call-1", "function", name, arguments);
        AssistantMessage message = AssistantMessage.builder().content("").toolCalls(List.of(call)).build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    /** A text-only model turn (no tool calls): ends the agent loop with this final message. */
    static ChatResponse text(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    /** A {@code write_file} turn creating/overwriting a workspace file with the given full content. */
    static ChatResponse writeFile(String path, String content) {
        return toolCall("write_file", "{\"path\":\"" + jsonEscape(path) + "\",\"content\":\"" + jsonEscape(content) + "\"}");
    }

    /** A {@code bash} turn running one shell command string in the workspace root. */
    static ChatResponse bash(String command) {
        return toolCall("bash", "{\"command\":\"" + jsonEscape(command) + "\"}");
    }

    /** A {@code submit} turn declaring completion; the loop ends this turn and hands off to the authoritative verifier. */
    static ChatResponse submit(String summary) {
        return toolCall("submit", "{\"summary\":\"" + jsonEscape(summary) + "\"}");
    }

    /** Minimal JSON string escaper covering everything Java/Markdown source content and workspace paths contain (backslash, quote, and the C0 whitespace controls). */
    static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    }
                    else {
                        escaped.append(c);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
