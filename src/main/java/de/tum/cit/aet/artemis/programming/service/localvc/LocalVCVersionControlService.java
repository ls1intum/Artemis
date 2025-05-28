package de.tum.cit.aet.artemis.programming.service.localvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.GitService;

@Profile(Constants.PROFILE_CORE)
@Service
public class LocalVCVersionControlService {

    private static final Logger log = LoggerFactory.getLogger(LocalVCVersionControlService.class);

    private final GitService gitService;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCBasePath;

    public LocalVCVersionControlService(GitService gitService) {
        this.gitService = gitService;
    }

    public void createSingleCommitStudentRepo(VcsRepositoryUri templateUri, Path targetPath) throws Exception {
        // Create temporary directories
        Path tempTemplateWorkingDir = Files.createTempDirectory("localvc-template-working-");
        Path tempStudentWorkingDir = Files.createTempDirectory("localvc-student-working-");

        try {
            // Step 1: Clone source bare repo to temp working directory
            gitService.cloneRepository(templateUri.getURI().toString(), tempTemplateWorkingDir, false);
            // Step 2: Copy files from working copy (excluding .git) to student working dir
            log.debug("Copying files from template to student working directory: {}", tempStudentWorkingDir);
            gitService.copyFilesExcludingGit(tempTemplateWorkingDir, tempStudentWorkingDir);
            // Step 3: Create target bare repository directly at final location
            log.debug("Creating bare repository at target location: {}", targetPath);
            try (Repository newRepo = gitService.createBareRepository(targetPath)) {
                // Step 4: Commit copied files into new repo
                log.debug("Committing copied files into target repository");
                gitService.commitCopiedFilesIntoRepo(newRepo, tempStudentWorkingDir);
            }
        }
        catch (Exception e) {
            log.error("Failed to create student repository", e);
            throw new GitException("Failed to create single-commit student repository", e);
        }
        finally {
            try {
                FileUtils.deleteDirectory(tempTemplateWorkingDir.toFile());
                FileUtils.deleteDirectory(tempStudentWorkingDir.toFile());
            }
            catch (IOException e) {
                log.warn("Failed to clean up temporary directories", e);
            }
        }
    }

    public Path buildStudentRepoPath(String targetProjectKey, String targetRepositoryName, Integer attempt) {
        String baseDir = localVCBasePath;
        targetRepositoryName = targetRepositoryName.toLowerCase();
        String targetProjectKeyLowerCase = targetProjectKey.toLowerCase();
        if (attempt != null && attempt > 0 && !targetRepositoryName.contains("practice-")) {
            targetProjectKeyLowerCase = targetProjectKeyLowerCase + attempt;
        }
        final String targetRepoSlug = targetProjectKeyLowerCase + "-" + targetRepositoryName + ".git";
        return Paths.get(baseDir, targetProjectKeyLowerCase, targetRepoSlug);
    }
}
