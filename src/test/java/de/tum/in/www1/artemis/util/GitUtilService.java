package de.tum.in.www1.artemis.util;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
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
import de.tum.in.www1.artemis.domain.VcsRepositoryUrl;
import de.tum.in.www1.artemis.service.connectors.GitService;

@Service
public class GitUtilService {

    private final String localRepositoryRoot = "./repos";

    private final String remoteRepositoryRoot = ".";

    private final String localRepositoryName = "test-repository";

    private final String remoteRepositoryName = "scm/test-repository";

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

    private Git localGit;

    public void initRepo() {
        try {
            deleteRepos();
            Git remoteGit = Git.init().setDirectory(new File(remoteRepositoryRoot + "/" + remoteRepositoryName)).call();
            new File(remoteRepositoryRoot + "/" + remoteRepositoryName + "/" + FILES.FILE1).createNewFile();
            new File(remoteRepositoryRoot + "/" + remoteRepositoryName + "/" + FILES.FILE2).createNewFile();
            new File(remoteRepositoryRoot + "/" + remoteRepositoryName + "/" + FILES.FILE3).createNewFile();
            remoteGit.add().addFilepattern(".").call();
            remoteGit.commit().setMessage("initial commit").call();

            // TODO: use a temp folder instead
            localGit = Git.cloneRepository().setURI(System.getProperty("user.dir") + "/" + remoteRepositoryRoot + "/" + remoteRepositoryName + "/.git")
                    .setDirectory(new File(localRepositoryRoot + "/" + localRepositoryName)).call();

            reinitializeLocalRepository();
            reinitializeRemoteRepository();
        }
        catch (IOException | GitAPIException ex) {
            System.out.println(ex);
        }
    }

    public void reinitializeLocalRepository() throws IOException {
        localRepo = initializeRepo(localRepositoryRoot, localRepositoryName);
    }

    public void reinitializeRemoteRepository() throws IOException {
        remoteRepo = initializeRepo(remoteRepositoryRoot, remoteRepositoryName);
    }

    private Repository initializeRepo(String repositoryRoot, String repositoryName) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        var remoteRepoFile = new java.io.File(repositoryRoot + "/" + repositoryName + "/.git");
        builder.setGitDir(remoteRepoFile).readEnvironment().findGitDir().setup(); // scan environment GIT_* variables
        return new Repository(builder, remoteRepoFile.toPath(), null);
    }

    public void deleteRepos() {
        try {
            if (remoteRepo != null) {
                remoteRepo.close();
            }
            if (localRepo != null) {
                localRepo.close();
            }
            if (localGit != null) {
                localGit.close();
            }
            FileUtils.deleteDirectory(new File(localRepositoryRoot + "/" + localRepositoryName));
            FileUtils.deleteDirectory(new File(remoteRepositoryRoot + "/" + remoteRepositoryName));
            localRepo = null;
            remoteRepo = null;
        }
        catch (IOException ex) {
        }
    }

    public void deleteRepo(REPOS repo) {
        try {
            String repoPath = getCompleteRepoPathStringByType(repo);
            FileUtils.deleteDirectory(new File(repoPath));
            setRepositoryToNull(repo);
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
            PrintWriter writer = new PrintWriter(getCompleteRepoPathStringByType(repo) + File.separator + fileToUpdate, "UTF-8");
            writer.print(content);
            writer.close();
        }
        catch (FileNotFoundException | UnsupportedEncodingException ex) {
        }
    }

    public String getFileContent(REPOS repo, FILES fileToRead) {
        try {
            byte[] encoded = Files.readAllBytes(Paths.get(getCompleteRepoPathStringByType(repo) + File.separator + fileToRead));
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

    public boolean isLocalEqualToRemote() {
        String fileContentLocal1 = getFileContent(REPOS.LOCAL, GitUtilService.FILES.FILE1);
        String fileContentLocal2 = getFileContent(REPOS.LOCAL, GitUtilService.FILES.FILE2);
        String fileContentLocal3 = getFileContent(REPOS.LOCAL, GitUtilService.FILES.FILE3);

        String fileContentRemote1 = getFileContent(REPOS.REMOTE, GitUtilService.FILES.FILE1);
        String fileContentRemote2 = getFileContent(REPOS.REMOTE, GitUtilService.FILES.FILE2);
        String fileContentRemote3 = getFileContent(REPOS.REMOTE, GitUtilService.FILES.FILE3);

        return fileContentLocal1.equals(fileContentRemote1) && fileContentLocal2.equals(fileContentRemote2) && fileContentLocal3.equals(fileContentRemote3);

    }

    public void setRepositoryToNull(REPOS repo) {
        if (repo == REPOS.LOCAL) {
            localRepo = null;
        }
        else if (repo == REPOS.REMOTE) {
            remoteRepo = null;
        }
    }

    public Repository getRepoByType(REPOS repo) {
        return repo == REPOS.LOCAL ? localRepo : remoteRepo;
    }

    public String getCompleteRepoPathStringByType(REPOS repo) {
        return repo == REPOS.LOCAL ? localRepositoryRoot + "/" + localRepositoryName : remoteRepositoryRoot + "/" + remoteRepositoryName;
    }

    public Path getCompleteRepoPathByType(REPOS repo) {
        return Paths.get(getCompleteRepoPathStringByType(repo));
    }

    public URL getCompleteRepoUrlByType(REPOS repo) {
        try {
            return new URL("http://" + getCompleteRepoPathStringByType(repo));
        }
        catch (MalformedURLException ex) {
        }
        return null;
    }

    public String getLocalRepoPathStringByType(REPOS repo) {
        return repo == REPOS.LOCAL ? localRepositoryName : remoteRepositoryName;
    }

    public Path getLocalRepoPathByType(REPOS repo) {
        return Paths.get(getLocalRepoPathStringByType(repo));
    }

    public VcsRepositoryUrl getLocalRepoUrlByType(REPOS repo) {
        try {
            return new VcsRepositoryUrl("file://" + System.getProperty("user.dir") + "/" + getLocalRepoPathByType(repo));
        }
        catch (MalformedURLException ex) {
        }
        return null;
    }

    public static final class MockFileRepositoryUrl extends VcsRepositoryUrl {

        public MockFileRepositoryUrl(File file) throws MalformedURLException {
            super(file.toURI().toURL().toString());
        }

        @Override
        public VcsRepositoryUrl withUser(String username) {
            // the mocked url should already include the user specific part
            return this;
        }
    }

    public void writeEmptyJsonFileToPath(Path path) throws Exception {
        var fileContent = "{}";
        path.toFile().getParentFile().mkdirs();
        FileWriter writer = new FileWriter(path.toFile());
        writer.write(fileContent);
        writer.close();
    }
}
