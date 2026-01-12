package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.icl.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
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
        de.tum.cit.aet.artemis.programming.service.GitService.commit(localRepository.workingCopyGitRepo).setMessage("seed content").call();
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
}
