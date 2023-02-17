package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.connectors.LtiNewResultService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResultNotificationDTO;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUrl;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseGradingService;

/**
 * Service for triggering builds on the local CI server.
 * Note: This service exists only to prevent a circular dependency LocalCIService -> ProgrammingExercisesGradingService -> LocalCIService, which would be present if the
 * triggerBuild method from the ContinuousIntegrationService interface would be used.
 */
@Service
@Profile("localci")
public class LocalCITriggerService {

    private final Logger log = LoggerFactory.getLogger(LocalCITriggerService.class);

    private final ProgrammingExerciseGradingService programmingExerciseGradingService;

    private final WebsocketMessagingService messagingService;

    private final LtiNewResultService ltiNewResultService;

    @Value("${artemis.version-control.url}")
    private URL localVCServerUrl;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCPath;

    public LocalCITriggerService(ProgrammingExerciseGradingService programmingExerciseGradingService, WebsocketMessagingService messagingService,
            LtiNewResultService ltiNewResultService) {
        this.programmingExerciseGradingService = programmingExerciseGradingService;
        this.messagingService = messagingService;
        this.ltiNewResultService = ltiNewResultService;
    }

    /**
     * Triggers a build for given participation.
     *
     * @param participation the participation with the id of the build plan that should be triggered.
     */
    @Async
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws LocalCIException {
        // Create a new build job and run it. TODO: outsource execution to an ExecutorService.
        String assignmentRepositoryUrlString = participation.getRepositoryUrl();
        LocalVCRepositoryUrl assignmentRepositoryUrl = new LocalVCRepositoryUrl(localVCServerUrl, assignmentRepositoryUrlString);
        Path assignmentRepositoryPath = assignmentRepositoryUrl.getLocalPath(localVCPath).toAbsolutePath();

        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
        String testRepositoryUrlString = programmingExercise.getTestRepositoryUrl();
        LocalVCRepositoryUrl testRepositoryUrl = new LocalVCRepositoryUrl(localVCServerUrl, testRepositoryUrlString);
        Path testRepositoryPath = testRepositoryUrl.getLocalPath(localVCPath).toAbsolutePath();

        ProgrammingLanguage programmingLanguage = programmingExercise.getProgrammingLanguage();

        if (programmingLanguage != ProgrammingLanguage.JAVA) {
            throw new LocalCIException("Programming language " + programmingLanguage + " is not supported by local CI.");
        }

        // Get script file out of resources. TODO: Check if there is an easier way to do this and if not find out why this is necessary.
        InputStream scriptInputStream = getClass().getResourceAsStream("/templates/localci/java/build_and_run_tests.sh");
        if (scriptInputStream == null) {
            throw new LocalCIException("Could not find build script for local CI.");
        }
        Path scriptPath;
        try {
            scriptPath = Files.createTempFile("build_and_run_tests", ".sh");
            Files.copy(scriptInputStream, scriptPath, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException e) {
            throw new LocalCIException("Could not create temporary file for build script.");
        }

        try {
            LocalCIBuildJob localCIBuildJob = new LocalCIBuildJob(programmingExercise.getProjectType(), assignmentRepositoryPath, testRepositoryPath, scriptPath);

            LocalCIBuildResultNotificationDTO buildResult = localCIBuildJob.runBuildJob(); // TODO: run in separate thread and notify LocalCIService about the result.
            log.info("buildResult: {}", buildResult);

            // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
            // Therefore, a mock auth object has to be created.
            SecurityUtils.setAuthorizationObject();
            Optional<Result> optResult = programmingExerciseGradingService.processNewProgrammingExerciseResult(participation, buildResult);

            // Only notify the user about the new result if the result was created successfully.
            if (optResult.isPresent()) {
                Result result = optResult.get();
                log.debug("Send result to client over websocket. Result: {}, Submission: {}, Participation: {}", result, result.getSubmission(), result.getParticipation());
                // notify user via websocket
                messagingService.broadcastNewResult((Participation) participation, result);
                if (participation instanceof StudentParticipation) {
                    // do not try to report results for template or solution participations
                    ltiNewResultService.onNewResult((ProgrammingExerciseStudentParticipation) participation);
                }
                log.info("The new result for repository {} was saved successfully", assignmentRepositoryUrlString);
            }
        }
        catch (IllegalArgumentException | IOException e) {
            throw new LocalCIException("Error while creating and running build job: " + e.getMessage());
        }
    }
}
