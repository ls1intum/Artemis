package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.doReturn;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.dockerclient.TransportConfig;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.DockerClient;

import de.tum.cit.aet.artemis.buildagent.service.BuildAgentDockerService;
import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandboxRelayHandler;
import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationOrchestrationService;
import de.tum.cit.aet.artemis.localci.service.LocalCIEventListenerService;
import de.tum.cit.aet.artemis.localci.service.LocalCIResultListenerService;
import de.tum.cit.aet.artemis.localci.service.LocalCIResultProcessingService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;

/**
 * Shared setup for deterministic mocked-model generation and adaptation tests that run the sandbox, builds, and verifier against Docker. Every test here builds a real exercise
 * inside the generation sandbox. Only local environments without Docker skip these tests; incompatible images fail. See
 * {@link HyperionMockedLlmE2eSupport#sandboxSkipReason()}.
 */
abstract class AbstractHyperionMockedLlmEndToEndTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    @Autowired
    protected GenerationOrchestrationService orchestrator;

    @Autowired
    protected ProgrammingExerciseCreationUpdateService creationService;

    @Autowired
    protected CourseUtilService courseUtilService;

    @Autowired
    protected ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    @Autowired
    protected BuildAgentDockerService buildAgentDockerService;

    @Autowired
    protected InteractiveSandboxRelayHandler interactiveSandboxRelayHandler;

    @Autowired
    protected ApplicationContext applicationContext;

    private DockerClient realDockerClient;

    private String originalDockerConnectionUri;

    private String originalImageArchitecture;

    @Nullable
    private String replacedJavaBuildImage;

    /**
     * JUnit runs {@code @AfterEach} even when {@code @BeforeEach} aborted on the assumption, and tearing down build-agent services that were never re-opened would corrupt the
     * shared context for the other LocalCI tests using it.
     */
    private boolean sandboxPrepared;

    @BeforeEach
    void switchToRealDockerClientAndOverrideBuildImage() {
        // Reported per test rather than as a class-level @EnabledIf so every parameterisation shows up as an explicit skip instead of vanishing from the report.
        Optional<String> skipReason = HyperionMockedLlmE2eSupport.sandboxSkipReason();
        assumeTrue(skipReason.isEmpty(), () -> "Hyperion generation sandbox unavailable: " + skipReason.orElseThrow());

        // The shared test profile configures a placeholder image; the sandbox and verifier need the real Java build image.
        replacedJavaBuildImage = HyperionMockedLlmE2eSupport.useProductionJavaBuildImage(programmingLanguageConfiguration);

        initializeLazyLocalCIServices();
        TransportConfig dockerTransportConfig = Objects.requireNonNull(HyperionMockedLlmE2eSupport.discoverDockerTransportConfig());
        originalDockerConnectionUri = (String) ReflectionTestUtils.getField(buildAgentConfiguration, "dockerConnectionUri");
        originalImageArchitecture = (String) ReflectionTestUtils.getField(buildAgentDockerService, "imageArchitecture");
        buildAgentConfiguration.closeBuildAgentServices();
        ReflectionTestUtils.setField(buildAgentConfiguration, "dockerConnectionUri", dockerTransportConfig.getDockerHost().toString());
        buildAgentConfiguration.openBuildAgentServices();
        realDockerClient = (DockerClient) ReflectionTestUtils.getField(buildAgentConfiguration, "dockerClient");
        doReturn(realDockerClient).when(buildAgentConfiguration).getDockerClient();
        doReturn(true).when(buildAgentConfiguration).isDockerAvailable();
        dockerClient = realDockerClient;
        String architecture = normalizeDockerArchitecture(realDockerClient.infoCmd().exec().getArchitecture());
        ReflectionTestUtils.setField(buildAgentDockerService, "imageArchitecture", architecture);
        distributedDataAccessService.getDistributedBuildJobQueue().clear();
        distributedDataAccessService.getDistributedProcessingJobs().clear();
        distributedDataAccessService.getDistributedBuildResultQueue().clear();
        sharedQueueProcessingService.resetInitializedState();
        sharedQueueProcessingService.setPauseState(false);
        sharedQueueProcessingService.init();
        ReflectionTestUtils.setField(interactiveSandboxRelayHandler, "maxGenerationSandboxSlots", 1);
        ((AtomicBoolean) Objects.requireNonNull(ReflectionTestUtils.getField(interactiveSandboxRelayHandler, "shuttingDown"))).set(false);
        interactiveSandboxRelayHandler.registerRequestListener();
        sharedQueueProcessingService.updateBuildAgentInformation();

        // The interactive sandbox creates its container from this image without ever pulling it.
        ensureDockerImageAvailable(HyperionMockedLlmE2eSupport.JAVA_BUILD_IMAGE);
        sandboxPrepared = true;
    }

    @AfterEach
    void tearDownRealDockerClient() {
        if (!sandboxPrepared) {
            return;
        }
        sandboxPrepared = false;
        interactiveSandboxRelayHandler.shutdown();
        distributedDataAccessService.getDistributedBuildJobQueue().clear();
        distributedDataAccessService.getDistributedProcessingJobs().clear();
        distributedDataAccessService.getDistributedBuildResultQueue().clear();
        buildAgentConfiguration.closeBuildAgentServices();
        realDockerClient = null;
        if (originalDockerConnectionUri != null) {
            ReflectionTestUtils.setField(buildAgentConfiguration, "dockerConnectionUri", originalDockerConnectionUri);
            originalDockerConnectionUri = null;
        }
        if (originalImageArchitecture != null) {
            ReflectionTestUtils.setField(buildAgentDockerService, "imageArchitecture", originalImageArchitecture);
            originalImageArchitecture = null;
        }
        if (replacedJavaBuildImage != null) {
            HyperionMockedLlmE2eSupport.restoreJavaBuildImage(programmingLanguageConfiguration, replacedJavaBuildImage);
            replacedJavaBuildImage = null;
        }
    }

    /** Forces initialization of the lazily-created LocalCI result-pipeline beans so re-opening the build agent services leaves the node in a consistent state. */
    private void initializeLazyLocalCIServices() {
        applicationContext.getBean(LocalCIEventListenerService.class);
        applicationContext.getBean(LocalCIResultProcessingService.class);
        applicationContext.getBean(LocalCIResultListenerService.class);
    }

    private void ensureDockerImageAvailable(String dockerImage) {
        new RemoteDockerImage(DockerImageName.parse(dockerImage)).get();
    }

    private String normalizeDockerArchitecture(String dockerArchitecture) {
        return switch (dockerArchitecture) {
            case "aarch64" -> "arm64";
            case "x86_64" -> "amd64";
            default -> dockerArchitecture;
        };
    }

}
