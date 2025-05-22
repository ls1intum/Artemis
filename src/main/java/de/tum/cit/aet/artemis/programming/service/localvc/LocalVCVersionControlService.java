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

    public void createSingleCommitStudentRepo(VcsRepositoryUri templateUri, VcsRepositoryUri studentUri) throws Exception {
        log.info("Creating student repository from template: {} to target: {}", templateUri, studentUri);

        // Create temporary directories
        Path tempTemplateWorkingDir = Files.createTempDirectory("localvc-template-working-");
        Path tempStudentWorkingDir = Files.createTempDirectory("localvc-student-working-");

        // 这里假设 studentUri 是本地文件路径，比如 file:///... 或者绝对路径
        URI targetURI = studentUri.getURI();
        Path targetPath;
        if (targetURI != null && "file".equalsIgnoreCase(targetURI.getScheme())) {
            targetPath = Paths.get(targetURI);
        }
        else {
            throw new IllegalArgumentException("Student repository target must be a local file path (file://...)");
        }

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
                log.info("Successfully created student repository at: {}", studentUri);
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
        String baseDir = "/var/git/repos";
        String repoName = targetRepoSlug + ".git";
        String fullRepoPath = baseDir + "/" + repoName;
        return new LocalVCRepositoryUri(fullRepoPath);
    }
}
