package de.tum.in.www1.exerciseapp.service;

import com.google.common.io.Files;
import de.tum.in.www1.exerciseapp.ExerciseApplicationApp;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.Repository;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by Josias Montag on 11.10.16.
 */
@ActiveProfiles(profiles = "dev,jira,bamboo,bitbucket")
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = ExerciseApplicationApp.class)
@WebAppConfiguration
@SpringBootTest
@Transactional
public class GitServiceIntTest {

    private final Logger log = LoggerFactory.getLogger(GitService.class);

    private String remoteTestRepo = "http://127.0.0.1:7990/scm/test/testrepo.git";

    private final GitService gitService;

    @Value("${exerciseapp.bitbucket.user}")
    private String GIT_USER;

    @Value("${exerciseapp.bitbucket.password}")
    private String GIT_PASSWORD;

    public GitServiceIntTest(GitService gitService) {
        this.gitService = gitService;
    }

    @Test
    public void testGetOrCheckoutRepositoryForNewRepo() throws IOException, GitAPIException {

        Participation participation = new Participation();
        participation.setRepositoryUrl(remoteTestRepo);

        Repository repo = gitService.getOrCheckoutRepository(participation);

        assertThat(repo.getBranch()).isEqualTo("master");
        assertThat(repo.getDirectory()).exists();


        gitService.deleteLocalRepository(repo);


    }




    @Test
    public void testDeleteLocalRepository() throws IOException, GitAPIException {

        Participation participation = new Participation();
        participation.setRepositoryUrl(remoteTestRepo);

        Repository repo = gitService.getOrCheckoutRepository(participation);

        assertThat(repo.getDirectory()).exists();

        gitService.deleteLocalRepository(repo);

        assertThat(repo.getDirectory()).doesNotExist();

    }


    @Test
    public void testGetOrCheckoutRepositoryForExistingRepo() throws IOException, GitAPIException {

        Participation participation = new Participation();
        participation.setRepositoryUrl(remoteTestRepo);


        Repository repo = gitService.getOrCheckoutRepository(participation);
        Repository repo2 = gitService.getOrCheckoutRepository(participation);

        assertThat(repo.getDirectory()).isEqualTo(repo2.getDirectory());
        assertThat(repo2.getBranch()).isEqualTo("master");
        assertThat(repo2.getDirectory()).exists();

        gitService.deleteLocalRepository(repo2);

    }


    @Test
    public void testListFiles() throws IOException, GitAPIException {

        Participation participation = new Participation();
        participation.setRepositoryUrl(remoteTestRepo);

        Repository repo = gitService.getOrCheckoutRepository(participation);

        Collection<de.tum.in.www1.exerciseapp.domain.File> files = gitService.listFiles(repo);
        assertThat(files.size()).isGreaterThan(0);

        gitService.deleteLocalRepository(repo);
    }




    @Test
    public void testCommitAndPush() throws IOException, GitAPIException {

        Participation participation = new Participation();
        participation.setRepositoryUrl(remoteTestRepo);


        Repository repo = gitService.getOrCheckoutRepository(participation);

        Ref oldHead = repo.findRef("HEAD");

        gitService.commitAndPush(repo, "test commit");

        Ref head = repo.findRef("HEAD");

        assertThat(head).isNotEqualTo(oldHead);

        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(head.getObjectId());

        assertThat(commit.getFullMessage()).isEqualTo("test commit");


        // get remote ref

        Git git = new Git(repo);
        Collection<Ref> refs = git.lsRemote().setHeads(true).call();
        Ref remoteHead = refs.iterator().next();

        assertThat(head.getObjectId()).isEqualTo(remoteHead.getObjectId());


        gitService.deleteLocalRepository(repo);


    }


    @Test
    public void testPull() throws IOException, GitAPIException {


        Participation participation = new Participation();
        participation.setRepositoryUrl(remoteTestRepo);


        Repository repo = gitService.getOrCheckoutRepository(participation);

        Ref oldHead = repo.findRef("HEAD");

        // commit
        File tempDir = Files.createTempDir();
        Git git = Git.cloneRepository()
            .setURI(remoteTestRepo)
            .setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, GIT_PASSWORD))
            .setDirectory(tempDir)
            .call();
        git.commit().setMessage("a commit").setAllowEmpty(true).call();
        git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(GIT_USER, GIT_PASSWORD)).call();


        // pull
        PullResult pullResult = gitService.pull(repo);

        Ref newHead = repo.findRef("HEAD");

        assertThat(oldHead).isNotEqualTo(newHead);
        RevWalk walk = new RevWalk(repo);
        RevCommit commit = walk.parseCommit(newHead.getObjectId());
        assertThat(commit.getFullMessage()).isEqualTo("a commit");

        FileUtils.deleteDirectory(tempDir);
    }
}
