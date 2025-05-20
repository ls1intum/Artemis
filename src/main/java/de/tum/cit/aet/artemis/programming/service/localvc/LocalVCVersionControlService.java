package de.tum.cit.aet.artemis.programming.service.localvc;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

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
                gitService.cloneRepository(templateUri.toString(), tempTemplateWorkingDir.toString(), false);

                log.debug("Copying files from template to student working directory: {}", tempStudentWorkingDir);
                gitService.copyFilesExcludingGit(tempTemplateWorkingDir, tempStudentWorkingDir);

                log.debug("Creating new bare student repository at: {}", studentBareRepoPath);
                Repository newStudentBareRepo = gitService.createBareRepository(studentBareRepoPath);

                log.debug("Committing copied files into new student repo");
                gitService.commitCopiedFilesIntoRepo(newStudentBareRepo, tempStudentWorkingDir);
            }
            finally {
                FileUtils.deleteDirectory(tempStudentWorkingDir.toFile());
                FileUtils.deleteDirectory(tempTemplateWorkingDir.toFile());
            }
        }
    }

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
