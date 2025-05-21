package de.tum.cit.aet.artemis.programming.service.localvc;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

@Profile(Constants.PROFILE_CORE)
@Service
public class LocalVCVersionControlService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCVersionControlService.class);

    private final GitService gitService;

    private final VersionControlService versionControlService;

    public LocalVCVersionControlService(GitService gitService, VersionControlService versionControlService) {
        this.gitService = gitService;
        this.versionControlService = versionControlService;
    }

    /**
     * Create a new student repo initialized with a single commit from the template bare repo.
     *
     * @param templateUri URI of the template repo (bare repo)
     * @throws Exception on any failure
     */
    public void createSingleCommitStudentRepo(VcsRepositoryUri templateUri, VcsRepositoryUri studentUri) throws Exception {
        try (Repository templateRepo = gitService.getBareRepository(templateUri)) {
            log.info("Resolved bare repo path from templateUri: {}", templateUri);

            // Ensure student URI is a file URI
            URI studentRepoURI = studentUri.getURI();
            if (!"file".equals(studentRepoURI.getScheme())) {
                throw new IllegalArgumentException("Student URI must be a file URI");
            }
            Path studentBareRepoPath = Paths.get(studentRepoURI);

            // Create a temporary working directory for cloning the template repo (non-bare)
            Path tempTemplateWorkingDir = Files.createTempDirectory("localvc-template-working-");
            // Create another temporary working directory for staging the student repo files (non-bare)
            Path tempStudentWorkingDir = Files.createTempDirectory("localvc-student-working-");

            try {
                log.debug("Cloning template repo to temporary working directory: {}", tempTemplateWorkingDir);
                gitService.cloneRepository(templateRepo.getDirectory().toURI().toString(), tempTemplateWorkingDir.toString(), false);

                log.debug("Copying files from template to student working directory: {}", tempStudentWorkingDir);
                gitService.copyFilesExcludingGit(tempTemplateWorkingDir, tempStudentWorkingDir);

                // Ensure parent directory exists
                Files.createDirectories(studentBareRepoPath.getParent());
                log.debug("Creating new bare student repository at: {}", studentBareRepoPath);
                Repository newStudentBareRepo = gitService.createBareRepository(studentBareRepoPath);

                log.debug("Committing copied files into new student repo");
                gitService.commitCopiedFilesIntoRepo(newStudentBareRepo, tempStudentWorkingDir);

                log.info("Successfully created single-commit student repository at: {}", studentBareRepoPath);
            }
            catch (IOException e) {
                log.error("Failed to create single-commit student repository", e);
                // Clean up the created repository if it exists
                try {
                    if (Files.exists(studentBareRepoPath)) {
                        FileUtils.deleteDirectory(studentBareRepoPath.toFile());
                    }
                }
                catch (IOException cleanupEx) {
                    log.warn("Failed to clean up student repository after error", cleanupEx);
                }
                throw new GitException("Failed to create single-commit student repository", e);
            }
            try {
                FileUtils.deleteDirectory(tempTemplateWorkingDir.toFile());
                FileUtils.deleteDirectory(tempStudentWorkingDir.toFile());
            }
            catch (IOException e) {
                log.warn("Failed to clean up temporary directories", e);
            }
        }
    }

    /**
     * Builds the URI for a student repository based on project key, repository name, and attempt.
     *
     * @param targetProjectKey     the project key
     * @param targetRepositoryName the repository name
     * @param attempt              the attempt number (null or 0 means first attempt)
     * @return the URI for the student repository
     */
    public VcsRepositoryUri buildStudentRepoPath(String targetProjectKey, String targetRepositoryName, Integer attempt) {
        targetRepositoryName = targetRepositoryName.toLowerCase();
        String targetProjectKeyLowerCase = targetProjectKey.toLowerCase();
        if (attempt != null && attempt > 0 && !targetRepositoryName.contains("practice-")) {
            targetProjectKeyLowerCase = targetProjectKeyLowerCase + attempt;
        }
        final String targetRepoSlug = targetProjectKeyLowerCase + "-" + targetRepositoryName;
        return versionControlService.getCloneRepositoryUri(targetProjectKey, targetRepoSlug);
    }
}
