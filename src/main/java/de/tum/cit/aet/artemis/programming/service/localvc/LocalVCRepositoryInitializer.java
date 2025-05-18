package de.tum.cit.aet.artemis.programming.service.localvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.GitService;

@Service
public class LocalVCRepositoryInitializer {

    private static final Logger log = LoggerFactory.getLogger(LocalVCRepositoryInitializer.class);

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private String localVCBasePath;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    private final GitService gitService;

    public LocalVCRepositoryInitializer(GitService gitService) {
        this.gitService = gitService;
    }

    /**
     * Create a new student repo initialized with a single commit from the template bare repo.
     *
     * @param templateUri URI of the template repo (bare repo)
     * @param studentUri  URI of the student repo (bare repo)
     * @throws Exception on any failure
     */
    public void createSingleCommitStudentRepo(VcsRepositoryUri templateUri, VcsRepositoryUri studentUri) throws Exception {
        try (Repository templateRepo = gitService.getBareRepository(templateUri); Repository studentRepo = gitService.getBareRepository(studentUri)) {

            Path studentBareRepoPath = studentRepo.getDirectory().toPath();

            Path tempTemplateWorkingDir = Files.createTempDirectory("localvc-template-working-");
            Path tempStudentWorkingDir = Files.createTempDirectory("localvc-student-working-");

            try {
                // Clone the bare template repository to a non-bare temporary directory
                Repository tempTemplateRepository = gitService.getOrCheckoutRepository(templateUri, tempTemplateWorkingDir.toString(), false);

                // Copy all files except the .git folder from the template working dir to student working dir
                copyFilesExcludingGit(tempTemplateWorkingDir, tempStudentWorkingDir);

                // Ensure the bare student repo path exists
                if (!Files.exists(studentBareRepoPath)) {
                    Files.createDirectories(studentBareRepoPath);
                }

                // Initialize the bare student repository at the target location
                gitService.initBareRepository(studentUri);

                try (Repository tempStudentRepo = gitService.initRepository(tempStudentWorkingDir)) {
                    gitService.setDefaultBranch(tempStudentRepo, defaultBranch);
                    gitService.stageAllChanges(tempStudentRepo);
                    gitService.commit(tempStudentRepo, "Initial import without history");
                    gitService.addRemote(tempStudentRepo, "origin", studentBareRepoPath.toUri().toString());
                    gitService.push(tempStudentRepo, "origin", defaultBranch, true);
                }

                // Cleanup temporary student working directory
                FileUtils.deleteDirectory(tempStudentWorkingDir.toFile());
            }
            finally {
                FileUtils.deleteDirectory(tempTemplateWorkingDir.toFile());
            }
        }
    }

    public void copyFilesExcludingGit(Path sourceDir, Path targetDir) throws IOException {
        // List all files/directories in sourceDir except ".git"
        try (var stream = Files.list(sourceDir)) {
            stream.forEach(sourcePath -> {
                try {
                    if (sourcePath.getFileName().toString().equals(".git")) {
                        // Skip .git directory
                        return;
                    }

                    Path targetPath = targetDir.resolve(sourcePath.getFileName());
                    if (Files.isDirectory(sourcePath)) {
                        // Recursively copy directories
                        Files.createDirectories(targetPath);
                        copyFilesExcludingGit(sourcePath, targetPath);
                    }
                    else {
                        // Copy file
                        Files.copy(sourcePath, targetPath);
                    }
                }
                catch (IOException e) {
                    throw new RuntimeException("Error copying files", e);
                }
            });
        }
    }
}
