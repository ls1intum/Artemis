package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
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
import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;

class RepositoryServiceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private GitService gitService;

    @Autowired
    private LocalVCLocalCITestService localVCLocalCITestService;

    private LocalVCTestRepository localRepository;

    private LocalVCRepositoryUri repositoryUri;

    private String projectKey;

    private String seededFilePath;

    private String seededContent;

    private ProgrammingExerciseStudentParticipation participation;

    @BeforeEach
    void setUp() throws Exception {
        projectKey = ("RSV" + UUID.randomUUID().toString().replace("-", "").substring(0, 8)).toUpperCase(Locale.ROOT);
        String repositorySlug = localVCLocalCITestService.getRepositorySlug(projectKey, "student1");

        localRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, repositorySlug);

        seededFilePath = "src/Test.java";
        seededContent = "class Test {}";

        Path file = localRepository.workingCopyPath().resolve(seededFilePath);
        Files.createDirectories(file.getParent());
        FileUtils.write(file.toFile(), seededContent, StandardCharsets.UTF_8);
        localRepository.workingCopy().add().addFilepattern(".").call();
        de.tum.cit.aet.artemis.localvc.service.GitService.commit(localRepository.workingCopy()).setMessage("seed content").call();
        localRepository.workingCopy().push().setRemote("origin").call();

        repositoryUri = new LocalVCRepositoryUri(localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, repositorySlug));
        participation = new ProgrammingExerciseStudentParticipation();
        participation.setProgrammingExercise(new ProgrammingExercise());
        participation.setRepositoryUri(repositoryUri);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (localRepository != null) {
            localRepository.deleteWorkingCopy();
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
        FileUtils.writeStringToFile(localRepository.workingCopyPath().resolve(smallFilePath).toFile(), "class Small {}", StandardCharsets.UTF_8);
        FileUtils.writeByteArrayToFile(localRepository.workingCopyPath().resolve(largeFilePath).toFile(), new byte[(int) RepositoryService.MAX_SELECTED_FILE_SIZE_BYTES + 1]);
        String commitHash = commitAndPushSelectedFiles();

        Map<String, String> files = repositoryService.getFilesContentAtCommit(participation.getProgrammingExercise(), commitHash, null, participation,
                Set.of(smallFilePath, largeFilePath));

        assertThat(files).containsOnlyKeys(smallFilePath);
    }

    @Test
    void getSelectedFilesContentEnforcesAggregateLimit() throws Exception {
        Set<String> filePaths = new HashSet<>();
        byte[] content = new byte[(int) RepositoryService.MAX_SELECTED_FILE_SIZE_BYTES];
        Arrays.fill(content, (byte) 'a');
        for (int i = 0; i < 6; i++) {
            String filePath = "src/File" + i + ".java";
            FileUtils.writeByteArrayToFile(localRepository.workingCopyPath().resolve(filePath).toFile(), content);
            filePaths.add(filePath);
        }
        String commitHash = commitAndPushSelectedFiles();

        Map<String, String> files = repositoryService.getFilesContentAtCommit(participation.getProgrammingExercise(), commitHash, null, participation, filePaths);

        assertThat(files).hasSize(5);
        assertThat(files.values().stream().mapToLong(value -> value.getBytes(StandardCharsets.UTF_8).length).sum())
                .isEqualTo(RepositoryService.MAX_SELECTED_FILES_TOTAL_SIZE_BYTES);
    }

    @Test
    void getSelectedFilesContentDetectsBinariesByContent() throws Exception {
        String shellScriptPath = "run.sh";
        String binaryFilePath = "plain.txt";
        String shellScript = "#!/bin/sh\necho hello\n";
        FileUtils.writeStringToFile(localRepository.workingCopyPath().resolve(shellScriptPath).toFile(), shellScript, StandardCharsets.UTF_8);
        FileUtils.writeByteArrayToFile(localRepository.workingCopyPath().resolve(binaryFilePath).toFile(), new byte[] { 0, 1, 2 });
        String commitHash = commitAndPushSelectedFiles();

        Map<String, String> files = repositoryService.getFilesContentAtCommit(participation.getProgrammingExercise(), commitHash, null, participation,
                Set.of(shellScriptPath, binaryFilePath));

        assertThat(files).containsOnly(Map.entry(shellScriptPath, shellScript));
    }

    private String commitAndPushSelectedFiles() throws Exception {
        localRepository.workingCopy().add().addFilepattern(".").call();
        var commit = de.tum.cit.aet.artemis.localvc.service.GitService.commit(localRepository.workingCopy()).setMessage("add selected files").call();
        localRepository.workingCopy().push().setRemote("origin").call();
        return commit.getName();
    }
}
