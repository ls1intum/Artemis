package de.tum.in.www1.artemis.util;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Repository;
import de.tum.in.www1.artemis.service.connectors.GitService;

@Service
public class GitUtilService {

    private final String repositoryName = "./test-repository";

    private final String remoteName = "./remote-repository";

    public enum FILES {
        FILE1, FILE2, FILE3
    }

    @Autowired
    GitService gitService;

    public Repository initRepo() {
        try {
            deleteRepo();
            Git remoteGit = Git.init().setDirectory(new File(remoteName)).call();
            new File(remoteName + "/" + FILES.FILE1).createNewFile();
            new File(remoteName + "/" + FILES.FILE2).createNewFile();
            new File(remoteName + "/" + FILES.FILE3).createNewFile();
            remoteGit.add().addFilepattern(".").call();
            remoteGit.commit().setMessage("initial commit").call();

            Git git = Git.init().setDirectory(new File(repositoryName)).call();
            git.remoteSetUrl().setRemoteName("origin").setRemoteUri(new URIish(System.getProperty("user.dir") + "/" + remoteName + "/.git")).call();

            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            builder.setGitDir(new java.io.File(repositoryName + "/.git")).readEnvironment() // scan environment GIT_* variables
                    .findGitDir().setup();
            Repository repository = new Repository(builder);
            return repository;
        }
        catch (IOException | URISyntaxException | GitAPIException ex) {
        }
        return null;
    }

    public void deleteRepo() {
        try {
            FileUtils.deleteDirectory(new File(repositoryName));
            FileUtils.deleteDirectory(new File(remoteName));
        }
        catch (IOException ex) {
        }
    }

    public Collection<ReflogEntry> getReflog(Repository repository) {
        try {
            Git git = new Git(repository);
            return git.reflog().call();
        }
        catch (GitAPIException ex) {
        }
        return null;
    }

    public void updateFile(FILES fileToUpdate, String content) {
        try {
            PrintWriter writer = new PrintWriter(repositoryName + '/' + fileToUpdate, "UTF-8");
            writer.println(content);
            writer.close();
        }
        catch (FileNotFoundException | UnsupportedEncodingException ex) {
        }
    }

    public String getFileContent(FILES fileToRead) {
        try {
            byte[] encoded = Files.readAllBytes(Paths.get(repositoryName + '/' + fileToRead));
            return new String(encoded, Charset.defaultCharset());
        }
        catch (IOException ex) {
            return null;
        }
    }

    public void stashAndCommitAll(Repository repository) {
        try {
            Git git = new Git(repository);
            git.add().addFilepattern(".").call();
            git.commit().setMessage("new commit").call();
        }
        catch (GitAPIException ex) {
        }
    }
}
