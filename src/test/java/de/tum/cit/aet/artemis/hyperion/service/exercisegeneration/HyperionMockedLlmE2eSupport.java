package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.dockerclient.TransportConfig;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
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

    private static final Logger log = LoggerFactory.getLogger(HyperionMockedLlmE2eSupport.class);

    static final String JAVA_BUILD_IMAGE = System.getenv().getOrDefault("HYPERION_TEST_JAVA_BUILD_IMAGE", "ls1tum/artemis-maven-template:java17-25");

    /** The test-repository templates every generated Java exercise inherits its build harness — and therefore its pinned dependency versions — from. */
    private static final List<String> MAVEN_TEST_TEMPLATE_POMS = List.of("templates/java/test/maven/projectTemplate/pom.xml",
            "templates/java/maven_maven/test/projectTemplate/pom.xml");

    private static final List<String> GRADLE_TEST_TEMPLATE_BUILD_FILES = List.of("templates/java/test/gradle/projectTemplate/build.gradle");

    /** Offline cache locations {@code verify.sh} copies into the sandbox's writable storage before a build runs. */
    private static final String MAVEN_REPOSITORY_DIR = "/root/.m2/repository";

    private static final String GRADLE_MODULE_CACHE_DIR = "/root/.gradle/caches/modules-2/files-2.1";

    private static final Pattern MAVEN_DEPENDENCY_MANAGEMENT = Pattern.compile("(?s)<dependencyManagement>(.*?)</dependencyManagement>");

    private static final Pattern MAVEN_COORDINATE = Pattern
            .compile("(?s)<groupId>\\s*([^<>\\s]+)\\s*</groupId>\\s*<artifactId>\\s*([^<>\\s]+)\\s*</artifactId>\\s*<version>\\s*([^<>\\s]+)\\s*</version>");

    private static final Pattern GRADLE_CONSTRAINTS = Pattern.compile("(?s)constraints\\s*\\{(.*?)\\n\\s*}");

    private static final Pattern GRADLE_COORDINATE = Pattern.compile("'([^:'\\s]+):([^:'\\s]+):([^:'\\s]+)'");

    private static final int PROBE_TIMEOUT_SECONDS = 60;

    @Nullable
    private static List<String> missingOfflineTemplateDependencies;

    private HyperionMockedLlmE2eSupport() {
    }

    /**
     * @return whether a Docker daemon is reachable
     */
    private static boolean isDockerAvailable() {
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
    private static boolean isRunningInCi() {
        return System.getenv("CI") != null;
    }

    /**
     * Docker half of the gate below. Locally an absent Docker daemon skips the Docker-backed tests; in CI this half is always on, so a broken daemon fails loudly with the real
     * error instead of reporting a green, meaningless run.
     *
     * @return whether a Docker daemon is expected to be usable here
     */
    private static boolean dockerGateEnabled() {
        return isRunningInCi() || isDockerAvailable();
    }

    /**
     * Capability gate for every Hyperion test that runs a real exercise build inside the generation sandbox. Consumed as a JUnit assumption per test rather than as a class-level
     * {@code @EnabledIf}, so that each parameterisation is reported as an individual skip instead of a whole class silently disappearing from the report.
     * <p>
     * The sandbox runs with Docker networking disabled ({@code GenerationWorkspaceService#sessionSpec}), so Maven and Gradle can only resolve what the configured build image
     * already carries in {@code /root/.m2/repository} and {@code /root/.gradle/caches}. That makes the image's offline cache a hard precondition rather than an optimisation: when
     * the Java exercise templates pin a dependency version the configured image never cached, every build in the sandbox fails at dependency resolution and the readiness probe
     * correctly refuses to start the authoring agent. The build image is released from a separate repository (ls1intum/artemis-maven-docker) and therefore cannot be fixed from
     * here, so this gate skips — naming the missing coordinates — instead of failing.
     * <p>
     * The gate is deliberately narrow. It only asks whether the coordinates the templates pin themselves are present; it never asks whether a build succeeds, so a genuine
     * scaffold, probe or agent-loop regression still fails loudly. It also errs towards running: a Docker daemon that is present but broken, or an image gap that only shows up
     * transitively, still produces a real failure rather than a skip. Point {@code HYPERION_TEST_JAVA_BUILD_IMAGE} at an image whose caches match the current templates and the
     * whole matrix runs again.
     *
     * @return the reason these tests cannot run here, or empty when they can
     */
    static Optional<String> sandboxSkipReason() {
        if (!dockerGateEnabled()) {
            return Optional.of("No Docker daemon is available for the Hyperion generation sandbox.");
        }
        if (!isDockerAvailable()) {
            // In CI a broken daemon must surface as the real error, not as a skip.
            return Optional.empty();
        }
        List<String> missing = missingOfflineTemplateDependencies();
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        String reason = "The configured build image " + JAVA_BUILD_IMAGE + " does not cache " + missing
                + ", which the Java exercise test templates pin. The generation sandbox has no network, so every build in it — and therefore Hyperion generation itself — fails at "
                + "dependency resolution until a build image carrying these artifacts is released and artemis.continuous-integration.build.images.java.default points at it. Set "
                + "HYPERION_TEST_JAVA_BUILD_IMAGE to such an image to run these tests.";
        // Also logged because a JUnit assumption message does not reach the Gradle console.
        report("Skipping the Docker-backed Hyperion sandbox tests. " + reason);
        return Optional.of(reason);
    }

    private static void report(String message) {
        log.warn(message);
    }

    /**
     * Reads the versions the Java exercise test templates pin and reports which of them the configured build image cannot serve offline. Cached for the JVM: the answer is a
     * property of one image, and every gated class would otherwise pay for the same container.
     *
     * @return the missing {@code group:artifact:version} coordinates, empty when the image serves them all
     */
    private static synchronized List<String> missingOfflineTemplateDependencies() {
        if (missingOfflineTemplateDependencies == null) {
            try {
                missingOfflineTemplateDependencies = probeMissingOfflineTemplateDependencies();
            }
            catch (Exception e) {
                // An unreadable image is not the gap this gate describes; let the test itself report whatever is actually wrong.
                report("Could not inspect the offline dependency cache of " + JAVA_BUILD_IMAGE + "; running the Docker-backed Hyperion tests anyway: " + e);
                missingOfflineTemplateDependencies = List.of();
            }
        }
        return missingOfflineTemplateDependencies;
    }

    private static List<String> probeMissingOfflineTemplateDependencies() throws InterruptedException, IOException {
        // Keyed by the reported coordinate so the same pin declared by several templates is probed once.
        Map<String, String> cachePathByCoordinate = new LinkedHashMap<>();
        for (String template : MAVEN_TEST_TEMPLATE_POMS) {
            for (String[] coordinate : pinnedMavenCoordinates(template)) {
                cachePathByCoordinate.put(label(coordinate), MAVEN_REPOSITORY_DIR + "/" + coordinate[0].replace('.', '/') + "/" + coordinate[1] + "/" + coordinate[2]);
            }
        }
        for (String template : GRADLE_TEST_TEMPLATE_BUILD_FILES) {
            for (String[] coordinate : pinnedGradleCoordinates(template)) {
                cachePathByCoordinate.put(label(coordinate), GRADLE_MODULE_CACHE_DIR + "/" + coordinate[0] + "/" + coordinate[1] + "/" + coordinate[2]);
            }
        }
        if (cachePathByCoordinate.isEmpty()) {
            throw new IllegalStateException("The Java exercise test templates declare no pinned dependency versions; the offline-cache gate can no longer be evaluated.");
        }
        // The gated tests need the image anyway, so pulling it here is work they would do a moment later.
        new RemoteDockerImage(DockerImageName.parse(JAVA_BUILD_IMAGE)).get();
        String script = cachePathByCoordinate.entrySet().stream().map(entry -> "[ -d '" + entry.getValue() + "' ] || echo '" + entry.getKey() + "'")
                .collect(Collectors.joining("\n"));
        try (DockerClient dockerClient = createDockerClient(Objects.requireNonNull(discoverDockerTransportConfig()))) {
            return readMissingCoordinates(dockerClient, script, cachePathByCoordinate.keySet());
        }
    }

    private static String label(String[] coordinate) {
        return coordinate[0] + ":" + coordinate[1] + ":" + coordinate[2];
    }

    /**
     * Runs the presence check in a throwaway, network-less container and returns the coordinates it reported as absent. Anything else the container may print is ignored rather
     * than treated as a gap, so noise can never turn into a silent skip.
     */
    private static List<String> readMissingCoordinates(DockerClient dockerClient, String script, Set<String> probedCoordinates) throws InterruptedException {
        String containerId = dockerClient.createContainerCmd(JAVA_BUILD_IMAGE).withEntrypoint("sh").withCmd("-c", script)
                .withHostConfig(HostConfig.newHostConfig().withNetworkMode("none")).exec().getId();
        try {
            dockerClient.startContainerCmd(containerId).exec();
            dockerClient.waitContainerCmd(containerId).exec(new WaitContainerResultCallback()).awaitStatusCode(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            StringBuilder reported = new StringBuilder();
            dockerClient.logContainerCmd(containerId).withStdOut(true).withStdErr(false).exec(new ResultCallback.Adapter<Frame>() {

                @Override
                public void onNext(Frame frame) {
                    reported.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                }
            }).awaitCompletion(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return reported.toString().lines().map(String::strip).filter(probedCoordinates::contains).toList();
        }
        finally {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
        }
    }

    /** @return the {@code group, artifact, version} triples the pom pins in its {@code dependencyManagement} block */
    private static List<String[]> pinnedMavenCoordinates(String templateResource) {
        Matcher block = MAVEN_DEPENDENCY_MANAGEMENT.matcher(readTemplate(templateResource));
        if (!block.find()) {
            return List.of();
        }
        return MAVEN_COORDINATE.matcher(block.group(1)).results().map(match -> new String[] { match.group(1), match.group(2), match.group(3) }).toList();
    }

    /** @return the {@code group, artifact, version} triples the Gradle build file pins in its {@code constraints} block */
    private static List<String[]> pinnedGradleCoordinates(String templateResource) {
        Matcher block = GRADLE_CONSTRAINTS.matcher(readTemplate(templateResource));
        if (!block.find()) {
            return List.of();
        }
        return GRADLE_COORDINATE.matcher(block.group(1)).results().map(match -> new String[] { match.group(1), match.group(2), match.group(3) }).toList();
    }

    private static String readTemplate(String templateResource) {
        try (InputStream stream = new ClassPathResource(templateResource).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not read the exercise template " + templateResource + " the offline-cache gate is derived from", e);
        }
    }

    /**
     * @return the Testcontainers-discovered Docker transport config, or {@code null} when no Docker daemon is reachable
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
                        escaped.append("\\u%04x".formatted((int) c));
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
