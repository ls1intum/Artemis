package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import de.tum.cit.aet.artemis.core.exception.GitException;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

class GitServiceTest {

    private GitService gitService;

    @TempDir
    private Path localDir;

    @TempDir
    private Path remoteDir;

    private LocalVCRepositoryUri repositoryUri;

    @BeforeEach
    void setUp() {
        gitService = new GitService();
        repositoryUri = new LocalVCRepositoryUri(URI.create("http://localhost"), "TEST", "TEST-student");
    }

    private Git initGit() throws GitAPIException {
        return Git.init().setDirectory(localDir.toFile()).call();
    }

    private void writeString(Path path, String content) throws IOException {
        final File file = new File(path.toUri());
        FileUtils.writeStringToFile(file, content, "ISO-8859-1");
    }

    private Repository getRepo(Git git, boolean pushFirstCommit, boolean addAndCommitSecondLocalFile, boolean addThirdFile, boolean stageThirdFile)
            throws GitAPIException, IOException, URISyntaxException {
        Git.init().setBare(true).setDirectory(remoteDir.toFile()).setInitialBranch("main").call();

        writeString(localDir.resolve("file.txt"), "initial");
        git.add().addFilepattern(".").call();
        GitService.commit(git).setMessage("init").call();
        git.remoteAdd().setName("origin").setUri(new URIish(remoteDir.toUri().toString())).call();
        if (pushFirstCommit) {
            git.push().setRemote("origin").call();
        }
        if (addAndCommitSecondLocalFile) {
            writeString(localDir.resolve("file2.txt"), "second");
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("file 2").call();
        }
        if (addThirdFile) {
            writeString(localDir.resolve("staged.txt"), "staged");
        }
        if (stageThirdFile) {
            git.add().addFilepattern(".").call();
        }

        return new Repository(localDir.resolve(".git").toString(), repositoryUri);
    }

    @Test
    void testDivergentBehaviorBranchesMatch() throws Exception {
        try (Git git = initGit()) {
            try (Repository repo = getRepo(git, true, false, false, true)) {
                assertThat(gitService.areLocalAndRemoteBranchesDivergent(repo, git)).isFalse();
            }
        }
    }

    @Test
    void testDivergentBehaviorLocalBranchIsAhead() throws Exception {
        try (Git git = initGit()) {
            try (Repository repo = getRepo(git, true, true, false, true)) {
                assertThat(gitService.areLocalAndRemoteBranchesDivergent(repo, git)).isFalse();
            }
        }
    }

    @Test
    void testDivergentBehaviorLocalBranchHasStagedFileRemoteBranchHasNoHead() throws Exception {
        try (Git git = initGit()) {
            try (Repository repo = getRepo(git, false, false, true, true)) {
                assertThat(gitService.areLocalAndRemoteBranchesDivergent(repo, git)).isFalse();
            }
        }
    }

    @Test
    void testDivergentBehaviorLocalBranchHasStagedFileRemoteBranchIsNotAhead() throws Exception {
        try (Git git = initGit()) {
            try (Repository repo = getRepo(git, true, false, true, true)) {
                assertThat(gitService.areLocalAndRemoteBranchesDivergent(repo, git)).isFalse();
            }
        }
    }

    @Test
    void testDivergentBehaviorLocalBranchHasUnstagedFileRemoteBranchIsAhead() throws Exception {
        try (Git git = initGit()) {
            try (Repository repo = getRepo(git, true, true, true, false)) {
                assertThat(gitService.areLocalAndRemoteBranchesDivergent(repo, git)).isFalse();
            }
        }
    }

    @Test
    void testDivergentBehaviorLocalBranchHasStagedFileRemoteBranchIsAhead() throws Exception {
        try (Git git = initGit()) {
            try (Repository repo = getRepo(git, true, true, true, true)) {
                assertThat(gitService.areLocalAndRemoteBranchesDivergent(repo, git)).isTrue();
            }
        }
    }

    @Test
    void testDivergentBehaviorIOExceptionOccurs() throws Exception {
        try (Git git = initGit()) {
            try (Repository repo = getRepo(git, true, true, true, true)) {
                var repoSpy = Mockito.spy(repo);
                when(repoSpy.findRef(anyString())).thenThrow(IOException.class);
                assertThatThrownBy(() -> gitService.areLocalAndRemoteBranchesDivergent(repoSpy, git)).isInstanceOf(GitException.class);
            }
        }
    }
}
