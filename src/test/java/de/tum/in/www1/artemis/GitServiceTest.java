package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.lib.ReflogEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.util.GitUtilService;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis, bamboo")
public class GitServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    GitUtilService gitUtilService;

    @Autowired
    GitService gitService;

    private Repository repo;

    @Before
    public void beforeEach() {
        repo = gitUtilService.initRepo();
    }

    @After
    public void afterEach() {
        gitUtilService.deleteRepo();
    }

    @Test
    public void doSomeCommits() {
        Collection<ReflogEntry> reflog = gitUtilService.getReflog(repo);
        assertThat(reflog.size()).isEqualTo(1);

        gitUtilService.updateFile(GitUtilService.FILES.FILE1, "lorem ipsum");
        gitUtilService.stashAndCommitAll(repo);

        gitUtilService.updateFile(GitUtilService.FILES.FILE2, "lorem ipsum solet");
        gitUtilService.stashAndCommitAll(repo);

        reflog = gitUtilService.getReflog(repo);
        assertThat(reflog.size()).isEqualTo(3);
    }

    @Test
    public void squashAllCommitsIntoInitialCommitTest() throws IOException {
        String newFileContent1 = "lorem ipsum";
        String newFileContent2 = "lorem ipsum solet";
        gitUtilService.updateFile(GitUtilService.FILES.FILE1, newFileContent1);
        gitUtilService.stashAndCommitAll(repo);

        gitUtilService.updateFile(GitUtilService.FILES.FILE2, newFileContent2);
        gitUtilService.stashAndCommitAll(repo);

        try {
            gitService.squashAllCommitsIntoInitialCommit(repo);
            Collection<ReflogEntry> reflog = gitUtilService.getReflog(repo);
            String fileContent1 = gitUtilService.getFileContent(GitUtilService.FILES.FILE1);
            String fileContent2 = gitUtilService.getFileContent(GitUtilService.FILES.FILE2);

            assertThat(reflog.size()).isEqualTo(1);
            assertThat(fileContent1).isEqualTo(newFileContent1);
            assertThat(fileContent2).isEqualTo(newFileContent2);
        }
        catch (IOException ex) {
            throw (ex);
        }
    }
}
