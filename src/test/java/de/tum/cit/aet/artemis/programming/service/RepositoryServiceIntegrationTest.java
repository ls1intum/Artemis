package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.localci.service.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;

class RepositoryServiceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private GitService gitService;

    @Autowired
    private LocalVCLocalCITestService localVCLocalCITestService;

    private LocalRepository localRepository;

    private LocalVCRepositoryUri repositoryUri;

    private String projectKey;

    private String seededFilePath;

    private String seededContent;

    @BeforeEach
    void setUp() throws Exception {
        projectKey = ("RSV" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)).toUpperCase();
        String repositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey, "student1");

        localRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repositorySlug);

        seededFilePath = "src/Test.java";
        seededContent = "class Test {}";

        Path file = localRepository.workingCopyGitRepoFile.toPath().resolve(seededFilePath);
        Files.createDirectories(file.getParent());
        FileUtils.write(file.toFile(), seededContent, StandardCharsets.UTF_8);
        localRepository.workingCopyGitRepo.add().addFilepattern(".").call();
        de.tum.cit.aet.artemis.localvc.service.GitService.commit(localRepository.workingCopyGitRepo).setMessage("seed content").call();
        localRepository.workingCopyGitRepo.push().setRemote("origin").call();

        repositoryUri = new LocalVCRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, repositorySlug));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (localRepository != null) {
            localRepository.resetLocalRepo();
        }
    }

    @Test
    void getFilesContentFromBareRepositoryForLastCommitReturnsSeededFiles() throws Exception {
        Map<String, String> files = repositoryService.getFilesContentFromBareRepositoryForLastCommit(repositoryUri);

        assertThat(files).containsEntry(seededFilePath, seededContent);
    }

    @Test
    void getFilesContentFromBareRepositoryForLastCommitBeforeOrAtHonorsDeadline() throws Exception {
        ZonedDateTime afterCommit = ZonedDateTime.now().plusHours(1);
        ZonedDateTime beforeCommit = ZonedDateTime.now().minusHours(1);

        Map<String, String> filesAfterDeadline = repositoryService.getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(repositoryUri, afterCommit);
        Map<String, String> filesBeforeDeadline = repositoryService.getFilesContentFromBareRepositoryForLastCommitBeforeOrAt(repositoryUri, beforeCommit);

        assertThat(filesAfterDeadline).containsEntry(seededFilePath, seededContent);
        assertThat(filesBeforeDeadline).isEmpty();
    }

    @Test
    void getSelectedFilesContentSkipsBlobAbovePerFileLimit() throws Exception {
        String smallFilePath = "src/Small.java";
        String largeFilePath = "src/Large.java";
        Files.writeString(localRepository.workingCopyGitRepoFile.toPath().resolve(smallFilePath), "class Small {}", StandardCharsets.UTF_8);
        Files.write(localRepository.workingCopyGitRepoFile.toPath().resolve(largeFilePath), new byte[(int) RepositoryService.MAX_SELECTED_FILE_SIZE_BYTES + 1]);
        String commitHash = commitAndPushSelectedFiles();

        Map<String, String> files = repositoryService.getFilesContentFromBareRepository(localRepository.remoteBareGitRepo.getRepository(), commitHash,
                Set.of(smallFilePath, largeFilePath));

        assertThat(files).containsOnlyKeys(smallFilePath);
    }

    @Test
    void getSelectedFilesContentEnforcesAggregateLimit() throws Exception {
        Set<String> filePaths = new HashSet<>();
        byte[] content = new byte[(int) RepositoryService.MAX_SELECTED_FILE_SIZE_BYTES];
        for (int i = 0; i < 6; i++) {
            String filePath = "src/File" + i + ".java";
            Files.write(localRepository.workingCopyGitRepoFile.toPath().resolve(filePath), content);
            filePaths.add(filePath);
        }
        String commitHash = commitAndPushSelectedFiles();

        Map<String, String> files = repositoryService.getFilesContentFromBareRepository(localRepository.remoteBareGitRepo.getRepository(), commitHash, filePaths);

        assertThat(files).hasSize(5);
        assertThat(files.values().stream().mapToLong(value -> value.getBytes(StandardCharsets.UTF_8).length).sum())
                .isEqualTo(RepositoryService.MAX_SELECTED_FILES_TOTAL_SIZE_BYTES);
    }

    private String commitAndPushSelectedFiles() throws Exception {
        localRepository.workingCopyGitRepo.add().addFilepattern(".").call();
        var commit = de.tum.cit.aet.artemis.localvc.service.GitService.commit(localRepository.workingCopyGitRepo).setMessage("add selected files").call();
        localRepository.workingCopyGitRepo.push().setRemote("origin").call();
        return commit.getName();
    }
}
