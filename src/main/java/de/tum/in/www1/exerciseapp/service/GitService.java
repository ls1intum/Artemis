package de.tum.in.www1.exerciseapp.service;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;

@Service
@Transactional
public class GitService {

    private final Logger log = LoggerFactory.getLogger(GitService.class);

    @Value("${exerciseapp.bitbucket.url}")
    private URL BITBUCKET_SERVER;

    @Value("${exerciseapp.bitbucket.user}")
    private String BITBUCKET_USER;

    @Value("${exerciseapp.bitbucket.password}")
    private String BITBUCKET_PASSWORD;

    /**
     * Checks out the repository with the given URL to the file system
     */
    public Git checkoutRepository(String projectKey, String repoUrl) {
        File repo = new File("/home/muench/exercise-application/repos/" + projectKey);
        if (!Files.exists(repo.toPath())) {
            System.out.println("Repo doesn't exist, cloning...");
            try {
                Git.cloneRepository()
                    .setURI(repoUrl)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(BITBUCKET_USER, BITBUCKET_PASSWORD))
                    .setDirectory(repo).call();
            } catch (GitAPIException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Repo exists.");
        }
        try {
            return new Git(new FileRepository(repo.getPath() + "/.git"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void doEmptyCommit(String projectKey, String newRemote) {
        Git git = checkoutRepository(projectKey, newRemote);
        try {
            StoredConfig config = git.getRepository().getConfig();
            config.setString("remote", "origin", "url", newRemote);
            config.save();
            git.commit().setMessage("Setup").setAllowEmpty(true).call();
            git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(BITBUCKET_USER, BITBUCKET_PASSWORD)).call();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (GitAPIException e) {
            e.printStackTrace();
        }
    }
}
