package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.localvc.service.AbstractGitService;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCReceivePackLimits;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Unit test that an online editor commit is checked against the limits for the files of a repository before it is created.
 */
class RepositoryServiceCommitLimitsTest {

    @TempDir
    Path workingTree;

    @Test
    void commitChangesRefusesAFileAboveTheSizeLimitWithoutCommitting() throws Exception {
        Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call().close();
        FileUtils.writeByteArrayToFile(workingTree.resolve("big.bin").toFile(), new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1]);
        GitService gitService = mock(GitService.class);
        RepositoryService repositoryService = new RepositoryService(gitService, mock(BareGitRepositoryService.class), Optional.empty());

        try (Repository repository = AbstractGitService.linkRepositoryForExistingGit(workingTree,
                new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise"), "main", false, true); Git git = new Git(repository)) {
            git.add().addFilepattern("big.bin").call();

            assertThatThrownBy(() -> repositoryService.commitChanges(repository, new User())).isInstanceOf(BadRequestAlertException.class);
        }

        verify(gitService, never()).commitAndPush(any(), anyString(), anyBoolean(), any());
    }
}
