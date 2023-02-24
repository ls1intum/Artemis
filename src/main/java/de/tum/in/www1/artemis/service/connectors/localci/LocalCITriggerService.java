package de.tum.in.www1.artemis.service.connectors.localci;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LocalCIException;
import de.tum.in.www1.artemis.service.UrlService;

/**
 * Service for triggering builds on the local CI server.
 * Note: This service exists only to prevent a circular dependency LocalCIService -> ProgrammingExercisesGradingService -> LocalCIService, which would be present if the
 * triggerBuild method from the ContinuousIntegrationService interface would be used.
 */
@Service
@Profile("localci")
public class LocalCITriggerService {

    private final UrlService urlService;

    private final LocalCIExecutorService localCIExecutorService;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCBasePath;

    public LocalCITriggerService(UrlService urlService, LocalCIExecutorService localCIExecutorService) {
        this.urlService = urlService;
        this.localCIExecutorService = localCIExecutorService;
    }

    /**
     * Triggers a build for given participation.
     * Needs to be outside ContinuousIntegrationService (LocalCIService) to prevent a circular dependency LocalCIService -> ProgrammingExercisesGradingService -> LocalCIService.
     *
     * @param participation the participation with the id of the build plan that should be triggered.
     */
    public void triggerBuild(ProgrammingExerciseParticipation participation) throws LocalCIException {

        // Prepare paths to assignment repository, test repository, and the build script, and add a new build job to the queue managed by the LocalCIExecutorService.
        String assignmentRepositoryUrlString = participation.getRepositoryUrl();
        VcsRepositoryUrl assignmentRepositoryUrl;
        try {
            assignmentRepositoryUrl = new VcsRepositoryUrl(assignmentRepositoryUrlString);
        }
        catch (URISyntaxException e) {
            throw new LocalCIException("Could not parse assignment repository url: " + assignmentRepositoryUrlString);
        }
        Path assignmentRepositoryPath = urlService.getLocalVCPathFromRepositoryUrl(assignmentRepositoryUrl, localVCBasePath).toAbsolutePath();

        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
        String testRepositoryUrlString = programmingExercise.getTestRepositoryUrl();
        VcsRepositoryUrl testRepositoryUrl;
        try {
            testRepositoryUrl = new VcsRepositoryUrl(testRepositoryUrlString);
        }
        catch (URISyntaxException e) {
            throw new LocalCIException("Could not parse test repository url: " + testRepositoryUrlString);
        }
        Path testRepositoryPath = urlService.getLocalVCPathFromRepositoryUrl(testRepositoryUrl, localVCBasePath).toAbsolutePath();

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

        localCIExecutorService.addBuildJobToQueue(participation, assignmentRepositoryPath, testRepositoryPath, scriptPath);
    }
}
