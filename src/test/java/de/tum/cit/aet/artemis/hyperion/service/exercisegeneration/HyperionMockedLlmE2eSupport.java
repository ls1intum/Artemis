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

    static final String JAVA_BUILD_IMAGE = System.getenv().getOrDefault("HYPERION_TEST_JAVA_BUILD_IMAGE", "ls1tum/artemis-maven-template:java17-25");

    private HyperionMockedLlmE2eSupport() {
    }

    /**
     * @return whether a Docker daemon is reachable, used locally as the developer convenience part of the {@code @EnabledIf} gate for the Docker-backed mocked E2E tests
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
     * @return whether the current process is running in CI (GitHub Actions and most other CI providers set the {@code CI} environment variable)
     */
    static boolean isRunningInCi() {
        return System.getenv("CI") != null;
    }

    /**
     * {@code @EnabledIf} gate for the Docker-backed mocked E2E tests.
     * <p>
     * Locally, a broken or absent Docker daemon skips the test — a developer convenience, since not every workstation runs Docker. In CI, Docker is expected to always be present
     * (the server-test job already depends on it for Testcontainers), so the gate returns {@code true} unconditionally there even when the Docker probe itself fails: a broken
     * Docker daemon in CI must fail the test loudly with the real error, not silently skip it and report a green, meaningless run.
     *
     * @return whether the Docker-backed tests should run
     */
    static boolean dockerGateEnabled() {
        return isRunningInCi() || isDockerAvailable();
    }

    /**
     * {@code @EnabledIf} gate for {@link HyperionBuildReadinessDockerIntegrationTest}. {@link #JAVA_BUILD_IMAGE} already falls back to the repo-owned production default when
     * {@code HYPERION_TEST_JAVA_BUILD_IMAGE} is unset, so unlike the Docker daemon this test does not additionally require the environment variable to be set.
     */
    static boolean isReadinessMatrixConfigured() {
        return dockerGateEnabled();
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

    static ChatResponse cleanSpecificationReview() {
        return text("{\"learningFit\":{\"briefEvidenceIds\":[\"B1\"],\"specEvidenceIds\":[\"E16\"],\"objectiveEvidenceIds\":[\"E16\",\"E25\"],"
                + "\"studentOwnershipEvidenceIds\":[\"E15\"],\"assessmentEvidenceIds\":[\"E25\"],"
                + "\"objectiveMechanism\":\"The cited student work exercises the requested objective through an observable collaboration.\","
                + "\"remainingStudentReasoning\":\"The boundary decisions remain after routine implementation is subtracted.\","
                + "\"domainGrounding\":\"The brief requests no qualitative theme; the counter domain directly motivates boundary behavior.\","
                + "\"learnerOwnsObjectiveMechanism\":true,\"objectiveObservable\":true,\"difficultySufficient\":true,\"domainGrounded\":true,"
                + "\"sufficient\":true,\"direction\":\"SUFFICIENT\"},"
                + "\"conceptAlignment\":{\"briefEvidenceIds\":[\"B1\"],\"conceptEvidenceIds\":[\"C2\"],\"specEvidenceIds\":[\"E16\"],"
                + "\"disposition\":\"ALIGNED\",\"reason\":\"The specification preserves bounded increment and decrement state transitions.\"}," + "\"exampleChecks\":["
                + "{\"exampleEvidenceId\":\"E10\",\"replayedOutcome\":\"value 2\",\"consistent\":true,\"reason\":\"the third increment clamps at two\"},"
                + "{\"exampleEvidenceId\":\"E11\",\"replayedOutcome\":\"value 0 and an exception\",\"consistent\":true,\"reason\":\"both boundary cases follow the rules\"}],"
                + "\"boundaryChecks\":[],\"omissions\":[],\"conflicts\":[],\"internalConflicts\":[],\"ambiguities\":[],\"unsupportedConstraints\":[]}");
    }

    static ChatResponse noSemanticMutants() {
        return text("{\"mutants\":[]}");
    }

    static ChatResponse noContractWitnesses() {
        return text("{\"witnesses\":[]}");
    }

    static ChatResponse conceptCandidates() {
        return text("""
                ## Candidate 1
                Domain situation: A bounded counter processes a sequence of increment and decrement commands.
                Real constraint: Every transition must preserve the configured lower and upper bounds.
                Common caller goal: Apply a command and expose the resulting counter value.
                Student-owned objective: Students implement the boundary-preserving state transitions.
                Student-owned reasoning: Students choose and implement qualitative clamp-versus-reject control flow at both boundaries.
                Alternative policies: One policy clamps at a bound; another rejects a transition beyond it.
                Observable substitution: The same boundary-crossing command produces a different visible outcome.
                Likely supplied support: Command input data and build setup.

                ## Candidate 2
                Domain situation: A delivery queue orders packages.
                Real constraint: Equal-priority packages preserve arrival order.
                Common caller goal: Select the next package.
                Student-owned objective: Students implement interchangeable ordering policies and their selection.
                Student-owned reasoning: Students compare multiple package attributes while preserving stable ordering for ties.
                Alternative policies: Urgency-first and route-cohesion policies prefer different package attributes.
                Observable substitution: Replacing the policy changes which package is selected next.
                Likely supplied support: Package input data and build setup.

                ## Candidate 3
                Domain situation: A sensor history identifies anomalous readings.
                Real constraint: Policies evaluate changes between neighboring readings.
                Common caller goal: Identify the readings that require inspection.
                Student-owned objective: Students implement interchangeable anomaly policies and replacement.
                Student-owned reasoning: Students traverse neighboring readings and distinguish isolated spikes from sustained drift.
                Alternative policies: Spike-sensitive and sustained-drift policies interpret the same history differently.
                Observable substitution: Replacing the policy changes the returned anomaly set.
                Likely supplied support: Reading input data and build setup.
                """);
    }

    static ChatResponse cleanConceptReview() {
        return text(
                """
                        {"selectedCandidate":1,
                         "selectionReason":"Candidate 1 directly matches the bounded-counter brief with the least unrelated scope.",
                         "evaluations":[
                          {"candidate":1,"candidateEvidenceIds":["C1.2"],
                           "briefCoverage":"The concept directly fulfills the bounded counter requested by the instructor.",
                           "objectiveCounterfactual":"Boundary-preserving state transitions are the central student-owned behavior.",
                           "difficultyFit":"Command sequences require reasoning about state transitions and both bounds.",
                                   "smallestStudentImplementation":"Students implement the cited central behavior.","reasoningAfterRoutineWork":"The cited non-routine reasoning remains after plumbing.",
                           "domainGrounding":"Crossing a configured counter bound naturally requires a policy decision.",
                           "feasibility":"The behavior is bounded, deterministic, and proportionate for one Java exercise.",
                           "objectiveEssential":true,"briefCovered":true,"learningFitSufficient":true,"learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"prematureContractClosure":false,"difficultySufficient":true,"domainGrounded":true,"feasibleAndProportionate":true},
                          {"candidate":2,"candidateEvidenceIds":["C2.2"],
                           "briefCoverage":"The concept does not implement the requested bounded counter.",
                           "objectiveCounterfactual":"Ordering policies form coherent student-owned behavior.","difficultyFit":"Stable ordering requires nontrivial collection reasoning.",
                                   "smallestStudentImplementation":"Students implement the cited central behavior.","reasoningAfterRoutineWork":"The cited non-routine reasoning remains after plumbing.",
                           "domainGrounding":"Package attributes naturally influence delivery ordering.","feasibility":"The behavior is deterministic and proportionate.",
                           "objectiveEssential":true,"briefCovered":false,"learningFitSufficient":true,"learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"prematureContractClosure":false,"difficultySufficient":true,"domainGrounded":true,"feasibleAndProportionate":true},
                          {"candidate":3,"candidateEvidenceIds":["C3.2"],
                           "briefCoverage":"The concept does not implement the requested bounded counter.",
                           "objectiveCounterfactual":"Anomaly policies form coherent student-owned behavior.","difficultyFit":"History traversal requires multi-step reasoning.",
                                   "smallestStudentImplementation":"Students implement the cited central behavior.","reasoningAfterRoutineWork":"The cited non-routine reasoning remains after plumbing.",
                           "domainGrounding":"Neighboring reading changes naturally motivate anomaly detection.","feasibility":"The behavior is deterministic and proportionate.",
                           "objectiveEssential":true,"briefCovered":false,"learningFitSufficient":true,"learnerOwnsObjectiveMechanism":true,"objectiveObservable":true,"prematureContractClosure":false,"difficultySufficient":true,"domainGrounded":true,"feasibleAndProportionate":true}
                         ]}
                        """);
    }

    static ChatResponse cleanConceptAdmission() {
        return text("""
                {"auditedCandidateEvidenceIds":["C1.2"],
                 "smallestEquivalentImplementation":"Students implement the observable state transitions at both configured bounds.",
                 "observablePartitionAudit":"Commands below, at, and crossing a bound produce caller-visible counter states.",
                 "unsupportedChoices":[],"unobservableRequirements":[],"redundantDistinctions":[],
                 "admissible":true,"summary":"The selected concept is grounded, observable, and leaves exact contract choices to specification."}
                """);
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
