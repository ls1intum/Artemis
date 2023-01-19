package de.tum.in.www1.artemis.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;

/**
 * This class describes a local repository cloned from an origin repository.
 * In the case of using the local VCS with the local CIS instead of, e.g. Bitbucket and Bamboo, the local VCS contains the origin repositories,
 * they are just not kept in an external system, but rather in another folder that belongs to Artemis.
 */
public class LocalRepository {

    public File localRepoFile;

    public File originRepoFile;

    public Git localGit;

    public Git originGit;

    private final String defaultBranch;

    public LocalRepository(String defaultBranch) {
        this.defaultBranch = defaultBranch;
    }

    public static Git initialize(File filePath, String defaultBranch) throws GitAPIException {
        return Git.init().setDirectory(filePath).setInitialBranch(defaultBranch).call();
    }

    public void configureRepos(String localRepoFileName, String originRepoFileName) throws Exception {

        this.localRepoFile = Files.createTempDirectory(localRepoFileName).toFile();
        this.localGit = initialize(localRepoFile, defaultBranch);

        this.originRepoFile = Files.createTempDirectory(originRepoFileName).toFile();
        this.originGit = initialize(originRepoFile, defaultBranch);

        this.localGit.remoteAdd().setName("origin").setUri(new URIish(String.valueOf(this.originRepoFile))).call();
    }

    public void resetLocalRepo() throws IOException {
        if (this.localRepoFile != null && this.localRepoFile.exists()) {
            FileUtils.deleteDirectory(this.localRepoFile);
        }
        if (this.localGit != null) {
            this.localGit.close();
        }

        if (this.originRepoFile != null && this.originRepoFile.exists()) {
            FileUtils.deleteDirectory(this.originRepoFile);
        }
        if (this.originGit != null) {
            this.originGit.close();
        }
    }

    public List<RevCommit> getAllLocalCommits() throws Exception {
        return StreamSupport.stream(this.localGit.log().call().spliterator(), false).toList();
    }

    public List<RevCommit> getAllOriginCommits() throws Exception {
        return StreamSupport.stream(this.originGit.log().call().spliterator(), false).toList();
    }
}
