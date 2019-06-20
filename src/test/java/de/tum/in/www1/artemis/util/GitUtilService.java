package de.tum.in.www1.artemis.util;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
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

    public enum REPOS {
        LOCAL, REMOTE
    }

    @Autowired
    GitService gitService;

    private Repository remoteRepo;

    private Repository localRepo;

    public Repository initRepo() {
        try {
            deleteRepo();
            Git remoteGit = Git.init().setDirectory(new File(remoteName)).call();
            new File(remoteName + "/" + FILES.FILE1).createNewFile();
            new File(remoteName + "/" + FILES.FILE2).createNewFile();
            new File(remoteName + "/" + FILES.FILE3).createNewFile();
            remoteGit.add().addFilepattern(".").call();
            remoteGit.commit().setMessage("initial commit").call();

            Git.cloneRepository().setURI(System.getProperty("user.dir") + "/" + remoteName + "/.git").setDirectory(new File(repositoryName)).call();

            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            builder.setGitDir(new java.io.File(repositoryName + "/.git")).readEnvironment() // scan environment GIT_* variables
                    .findGitDir().setup();
            localRepo = new Repository(builder);

            builder = new FileRepositoryBuilder();
            builder.setGitDir(new java.io.File(remoteName + "/.git")).readEnvironment() // scan environment GIT_* variables
                    .findGitDir().setup();
            remoteRepo = new Repository(builder);
        }
        catch (IOException | GitAPIException ex) {
            System.out.println(ex);
        }
        return null;
    }

    public void deleteRepo() {
        try {
            FileUtils.deleteDirectory(new File(repositoryName));
            FileUtils.deleteDirectory(new File(remoteName));
            localRepo = null;
            remoteRepo = null;
        }
        catch (IOException ex) {
        }
    }

    public Collection<ReflogEntry> getReflog(REPOS repo) {
        try {
            Git git = new Git(getRepoByType(repo));
            return git.reflog().call();
        }
        catch (GitAPIException ex) {
        }
        return null;
    }

    public Iterable<RevCommit> getLog(REPOS repo) {
        try {
            Git git = new Git(getRepoByType(repo));
            return git.log().call();
        }
        catch (GitAPIException ex) {
        }
        return null;
    }

    public void updateFile(REPOS repo, FILES fileToUpdate, String content) {
        try {
            PrintWriter writer = new PrintWriter(getRepoPathByType(repo) + '/' + fileToUpdate, "UTF-8");
            writer.print(content);
            writer.close();
        }
        catch (FileNotFoundException | UnsupportedEncodingException ex) {
        }
    }

    public String getFileContent(REPOS repo, FILES fileToRead) {
        try {
            byte[] encoded = Files.readAllBytes(Paths.get(getRepoPathByType(repo) + '/' + fileToRead));
            return new String(encoded, Charset.defaultCharset());
        }
        catch (IOException ex) {
            return null;
        }
    }

    public void stashAndCommitAll(REPOS repo) {
        try {
            Git git = new Git(getRepoByType(repo));
            git.add().addFilepattern(".").call();
            git.commit().setMessage("new commit").call();
        }
        catch (GitAPIException ex) {
        }
    }

    public Repository getRepoByType(REPOS repo) {
        return repo == REPOS.LOCAL ? localRepo : remoteRepo;
    }

    private String getRepoPathByType(REPOS repo) {
        return repo == REPOS.LOCAL ? repositoryName : remoteName;
    }
}
