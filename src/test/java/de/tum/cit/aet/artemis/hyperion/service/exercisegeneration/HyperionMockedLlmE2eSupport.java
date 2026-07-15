package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.AssistantMessage;
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

/** Docker setup, Java build-image override, and scripted model turns for the deterministic mocked-LLM end-to-end tests. */
final class HyperionMockedLlmE2eSupport {

    static final String JAVA_BUILD_IMAGE = "ls1tum/artemis-maven-template:java17-25";

    private HyperionMockedLlmE2eSupport() {
    }

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

    /**
     * @return the Testcontainers-discovered Docker transport config, or {@code null} when no Docker daemon is reachable; shared with the mocked E2E tests so they can re-point the
     *         build agent at the real Docker host without duplicating the discovery logic
     */
    static TransportConfig discoverDockerTransportConfig() {
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

    /**
     * Uses the real Java build image for the Docker-backed test and returns the prior value for restoration.
     *
     * @param config the shared programming-language configuration
     * @return the prior Java image, or {@code null} when none was configured
     */
    @Nullable
    static String useProductionJavaBuildImage(ProgrammingLanguageConfiguration config) {
        Map<ProgrammingLanguage, Map<ProjectType, String>> images = imagesMap(config);
        Map<ProjectType, String> javaImages = images.computeIfAbsent(ProgrammingLanguage.JAVA, key -> new EnumMap<>(ProjectType.class));
        String previous = javaImages.get(ProjectType.PLAIN);
        javaImages.put(ProjectType.PLAIN, JAVA_BUILD_IMAGE);
        return previous;
    }

    /**
     * Restores the Java build image after a Docker-backed test.
     *
     * @param config        the shared programming-language configuration
     * @param previousImage the value returned by {@link #useProductionJavaBuildImage}
     */
    static void restoreJavaBuildImage(ProgrammingLanguageConfiguration config, @Nullable String previousImage) {
        Map<ProgrammingLanguage, Map<ProjectType, String>> images = imagesMap(config);
        Map<ProjectType, String> javaImages = images.get(ProgrammingLanguage.JAVA);
        if (javaImages == null) {
            return;
        }
        if (previousImage == null) {
            javaImages.remove(ProjectType.PLAIN);
        }
        else {
            javaImages.put(ProjectType.PLAIN, previousImage);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<ProgrammingLanguage, Map<ProjectType, String>> imagesMap(ProgrammingLanguageConfiguration config) {
        Map<ProgrammingLanguage, Map<ProjectType, String>> images = (Map<ProgrammingLanguage, Map<ProjectType, String>>) ReflectionTestUtils.getField(config, "images");
        if (images == null) {
            throw new IllegalStateException("ProgrammingLanguageConfiguration has no images map to override");
        }
        return images;
    }

    static ChatResponse toolCall(String name, String arguments) {
        AssistantMessage.ToolCall call = new AssistantMessage.ToolCall("call-1", "function", name, arguments);
        AssistantMessage message = AssistantMessage.builder().content("").toolCalls(List.of(call)).build();
        return new ChatResponse(List.of(new Generation(message)));
    }

    static ChatResponse text(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    static ChatResponse cleanQualityReview() {
        return text("{\"exampleChecks\":[{\"claim\":\"the example result\",\"computedOutcome\":\"the example result\",\"consistent\":true,\"reason\":\"the outcomes agree\"}],"
                + "\"apiChecks\":[{\"symbol\":\"the public API\",\"discoverable\":true,\"reason\":\"the statement and starter expose it\"}],"
                + "\"templateChecks\":[{\"test\":\"the task groups\",\"targetReached\":true,\"reason\":\"the starter reaches their targets\"}],"
                + "\"mutantChecks\":[{\"mutant\":\"a contract-breaking implementation\",\"killed\":true,\"reason\":\"an executable assertion rejects it\"}],"
                + "\"uncovered\":[],\"contradictions\":[],\"hiddenRequirements\":[],\"weakOracle\":[],\"templateGaps\":[],\"missingExamples\":[],\"invented\":[],"
                + "\"unrequestedChanges\":[],\"missingRequestedChanges\":[]}");
    }

    static ChatResponse writeFile(String path, String content) {
        return toolCall("write_file", "{\"path\":\"" + jsonEscape(path) + "\",\"content\":\"" + jsonEscape(content) + "\"}");
    }

    static ChatResponse bash(String command) {
        return toolCall("bash", "{\"command\":\"" + jsonEscape(command) + "\"}");
    }

    static ChatResponse verify() {
        return toolCall("verify", "{}");
    }

    static ChatResponse submit(String summary) {
        return toolCall("submit", "{\"summary\":\"" + jsonEscape(summary) + "\"}");
    }

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
