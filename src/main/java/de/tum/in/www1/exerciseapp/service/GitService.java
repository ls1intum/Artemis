package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.exception.GitException;
import org.apache.commons.io.FileUtils;
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
import java.util.UUID;

@Service
public class GitService {

    private final Logger log = LoggerFactory.getLogger(GitService.class);

    @Value("${exerciseapp.bitbucket.url}")
    private URL BITBUCKET_SERVER;

    @Value("${exerciseapp.bitbucket.user}")
    private String BITBUCKET_USER;

    @Value("${exerciseapp.bitbucket.password}")
    private String BITBUCKET_PASSWORD;

    @Value("${exerciseapp.repo-clone-path}")
    private String REPO_CLONE_PATH;

    /**
     * Checks out the repository with the given URL to the file system
     */
    public File checkoutRepository(String projectKey, String repoUrl) throws GitAPIException {
        File repo = new File(REPO_CLONE_PATH + projectKey);
        if (!Files.exists(repo.toPath())) {
            log.info("Repository for key {} doesn't exist, cloning...", projectKey);
            Git.cloneRepository()
                .setURI(repoUrl)
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(BITBUCKET_USER, BITBUCKET_PASSWORD))
                .setDirectory(repo).call();
        } else {
            log.info("Repo already cloned.");
        }
        return repo;
    }

    public void doEmptyCommit(String projectKey, String newRemote) throws GitException {
        try {
            File sourceFolder = checkoutRepository(projectKey, newRemote);
            File tmpFolder = new File(sourceFolder.getParentFile().getPath() + "/" + UUID.randomUUID());
            FileUtils.copyDirectory(sourceFolder, tmpFolder);
            Git git = new Git(new FileRepository(tmpFolder.getPath() + "/.git"));
            StoredConfig config = git.getRepository().getConfig();
            config.setString("remote", "origin", "url", newRemote);
            config.save();
            git.commit().setMessage("Setup").setAllowEmpty(true).call();
            git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(BITBUCKET_USER, BITBUCKET_PASSWORD)).call();
            FileUtils.deleteDirectory(tmpFolder);
        } catch (IOException e) {
            e.printStackTrace();
            throw new GitException("IOError while doing empty commit");
        } catch (GitAPIException e) {
            e.printStackTrace();
            throw new GitException("Git error while doing empty commit");
        }
    }
}
