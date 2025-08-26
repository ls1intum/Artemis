package de.tum.cit.aet.artemis.athena.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.service.GitRepositoryExportService;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;

/**
 * Utility class for Athena tests providing common test functionality
 */
@Lazy
@Component
@Profile(SPRING_PROFILE_TEST)
public class AthenaTestUtil {

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCRepoPath;

    @Autowired
    private GitService gitService;

    @Autowired
    private GitRepositoryExportService gitRepositoryExportService;

    /**
     * Creates an example repository and makes the given GitService return it when asked to check it out.
     *
     * @throws Exception if creating the repository fails
     */
    public void createGitRepository() throws Exception {
        // Create repository
        var testRepo = new LocalRepository(defaultBranch);
        testRepo.configureRepos(localVCRepoPath, "testLocalRepo", "testOriginRepo");
        // Add test file to the repository folder
        Path filePath = Path.of(testRepo.workingCopyGitRepoFile + "/Test.java");
        var file = Files.createFile(filePath).toFile();
        FileUtils.write(file, "Test", Charset.defaultCharset());
        // Create mock repo that has the file
        var mockRepository = mock(Repository.class);
        doReturn(true).when(mockRepository).isValidFile(any());
        doReturn(testRepo.workingCopyGitRepoFile.toPath()).when(mockRepository).getLocalPath();
        // Mock Git service operations
        doReturn(mockRepository).when(gitService).getOrCheckoutRepository(any(), any(), any(), anyBoolean(), anyString(), anyBoolean());
        doNothing().when(gitService).resetToOriginHead(any());
        doReturn(Path.of("repo.zip")).when(gitRepositoryExportService).getRepositoryWithParticipation(any(), anyString(), anyBoolean(), eq(true));
        doReturn(Path.of("repo")).when(gitRepositoryExportService).getRepositoryWithParticipation(any(), anyString(), anyBoolean(), eq(false));
    }
}
