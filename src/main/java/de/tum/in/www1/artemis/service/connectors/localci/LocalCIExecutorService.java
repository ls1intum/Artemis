package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;

/**
 * Service for submitting build jobs to the executor service.
 */
@Service
@Profile("localci")
public class LocalCIExecutorService {

    private final Logger log = LoggerFactory.getLogger(LocalCIExecutorService.class);

    private final ExecutorService executorService;

    private final LocalCIBuildJobService localCIBuildJobService;

    private final ResourceLoaderService resourceLoaderService;

    private final LocalCIBuildPlanService localCIBuildPlanService;

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCBasePath;

    public LocalCIExecutorService(ExecutorService executorService, LocalCIBuildJobService localCIBuildJobService, ResourceLoaderService resourceLoaderService,
            LocalCIBuildPlanService localCIBuildPlanService) {
        this.executorService = executorService;
        this.localCIBuildJobService = localCIBuildJobService;
        this.resourceLoaderService = resourceLoaderService;
        this.localCIBuildPlanService = localCIBuildPlanService;
    }

    /**
     * Prepare paths to the assignment and test repositories and the build script and then submit the build job to the executor service.
     *
     * @param participation The participation of the repository for which the build job should be executed.
     * @return A future that will be completed with the build result.
     */
    public CompletableFuture<LocalCIBuildResult> addBuildJobToQueue(ProgrammingExerciseParticipation participation) {

        LocalVCRepositoryUrl assignmentRepositoryUrl = new LocalVCRepositoryUrl(participation.getRepositoryUrl(), localVCBaseUrl);
        Path assignmentRepositoryPath = assignmentRepositoryUrl.getLocalRepositoryPath(localVCBasePath).toAbsolutePath();

        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
        LocalVCRepositoryUrl testRepositoryUrl = new LocalVCRepositoryUrl(programmingExercise.getTestRepositoryUrl(), localVCBaseUrl);
        Path testRepositoryPath = testRepositoryUrl.getLocalRepositoryPath(localVCBasePath).toAbsolutePath();

        // Get script file out of resources.
        Path scriptPath = getBuildScriptPath(programmingExercise.getProgrammingLanguage());

        CompletableFuture<LocalCIBuildResult> futureResult = new CompletableFuture<>();
        executorService.submit(() -> {
            LocalCIBuildResult buildResult;
            try {
                buildResult = localCIBuildJobService.runBuildJob(participation, assignmentRepositoryPath, testRepositoryPath, scriptPath);
                futureResult.complete(buildResult);
            }
            catch (LocalCIException e) {
                log.error("Error while running build job", e);
                futureResult.completeExceptionally(e);
            }
        });

        // Add "_QUEUED" to the build plan id to indicate that the build job is queued.
        localCIBuildPlanService.updateBuildPlanStatus(participation, ContinuousIntegrationService.BuildStatus.QUEUED);

        return futureResult;
    }

    private Path getBuildScriptPath(ProgrammingLanguage programmingLanguage) {
        if (programmingLanguage != ProgrammingLanguage.JAVA) {
            throw new LocalCIException("Programming language " + programmingLanguage + " is not supported by local CI.");
        }

        Path resourcePath = Path.of("templates", "localci", "java", "build_and_run_tests.sh");
        Path scriptPath;
        try {
            scriptPath = resourceLoaderService.getResourceFilePath(resourcePath);
        }
        catch (IOException | URISyntaxException e) {
            throw new LocalCIException("Could not retrieve build script.", e);
        }

        return scriptPath;
    }
}
