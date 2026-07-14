package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration;

import static org.mockito.Mockito.doReturn;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
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
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;

/** Shared setup for deterministic mocked-model generation and adaptation tests that run the sandbox, builds, and verifier against Docker. */
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

    @BeforeEach
    void switchToRealDockerClientAndOverrideBuildImage() {
        // The shared test profile uses a placeholder image; this test needs the Java image used by the sandbox and verifier.
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
        interactiveSandboxRelayHandler.registerRequestListener();
        sharedQueueProcessingService.updateBuildAgentInformation();

        // The interactive sandbox creates its container directly from this image and never pulls it, so ensure it is present locally before any test body runs.
        ensureDockerImageAvailable(HyperionMockedLlmE2eSupport.JAVA_BUILD_IMAGE);
    }

    @AfterEach
    void tearDownRealDockerClient() {
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

    /**
     * Forces initialization of the lazily-created LocalCI result-pipeline beans so re-opening the build agent services leaves the node in a consistent state. The Hyperion tests
     * do not enqueue LocalCI build jobs (the differential oracle builds inside the sandbox), so no result is awaited here.
     */
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

    protected ProgrammingExercise useOfflineMavenPluginVersions(ProgrammingExercise exercise) throws Exception {
        LocalVCRepositoryUri uri = exercise.getRepositoryURI(RepositoryType.TESTS);
        Repository repository = gitService.getOrCheckoutRepository(uri, true, localVCLocalCITestService.getDefaultBranch(), true);
        Path pom = repository.getLocalPath().resolve("pom.xml");
        String original = Files.readString(pom);
        String updated = original;
        updated = replaceRequiredPomFragment(original, updated, "<version>3.13.0</version>", "<version>3.14.0</version>");
        updated = replaceRequiredPomFragment(original, updated, "<version>3.2.5</version>", "<version>3.5.3</version>");
        updated = replaceRequiredPomFragment(original, updated, "<version>3.4.1</version>", "<version>3.6.1</version>");
        updated = replaceRequiredPomFragment(original, updated, "<argLine>-Dfile.encoding=UTF-8</argLine>",
                "<argLine>-Dfile.encoding=UTF-8 -Djava.security.manager=allow</argLine>");
        if (updated.contains("artemis-java-test-sandbox") && !updated.contains("<artifactId>slf4j-api</artifactId>")) {
            updated = updated.replaceFirst("(?m)^\\s*<dependencies>\\s*$", """
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.slf4j</groupId>
                                <artifactId>slf4j-api</artifactId>
                                <version>2.0.12</version>
                            </dependency>
                            <dependency>
                                <groupId>net.bytebuddy</groupId>
                                <artifactId>byte-buddy</artifactId>
                                <version>1.17.7</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>""");
        }
        if (updated.contains("artemis-java-test-sandbox") && !updated.contains("<artifactId>slf4j-api</artifactId>")) {
            throw new IllegalStateException("Could not inject offline dependency management into generated Java test pom.xml.");
        }
        if (!updated.equals(original)) {
            FileUtils.writeStringToFile(pom.toFile(), updated, StandardCharsets.UTF_8);
            gitService.stageAllChanges(repository);
            gitService.commitAndPush(repository, "Use Maven plugins cached in the Docker verifier image", false, null);
        }
        return exercise;
    }

    private String replaceRequiredPomFragment(String original, String updated, String oldFragment, String newFragment) {
        if (original.contains(oldFragment)) {
            return updated.replace(oldFragment, newFragment);
        }
        if (original.contains(newFragment)) {
            return updated;
        }
        throw new IllegalStateException("Generated Java test pom.xml no longer contains the expected Maven fragment: " + oldFragment);
    }
}
