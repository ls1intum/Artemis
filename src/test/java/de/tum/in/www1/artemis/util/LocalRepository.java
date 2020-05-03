package de.tum.in.www1.artemis.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

public class LocalRepository {

    public File localRepoFile;

    public File originRepoFile;

    public Git localGit;

    public Git originGit;

    public void configureRepos(String localRepoFileName, String originRepoFileName) throws Exception {

        this.localRepoFile = Files.createTempDirectory(localRepoFileName).toFile();
        this.localGit = Git.init().setDirectory(localRepoFile).call();

        this.originRepoFile = Files.createTempDirectory(originRepoFileName).toFile();
        this.originGit = Git.init().setDirectory(originRepoFile).call();

        this.localGit.remoteAdd().setName("origin").setUri(new URIish(String.valueOf(this.originRepoFile))).call();
    }

    public static void resetLocalRepo(LocalRepository localRepo) throws IOException {
        if (localRepo.localRepoFile != null && localRepo.localRepoFile.exists()) {
            FileUtils.deleteDirectory(localRepo.localRepoFile);
        }
        if (localRepo.localGit != null) {
            localRepo.localGit.close();
        }

        if (localRepo.originRepoFile != null && localRepo.originRepoFile.exists()) {
            FileUtils.deleteDirectory(localRepo.originRepoFile);
        }
        if (localRepo.originGit != null) {
            localRepo.originGit.close();
        }
    }

    public static List<RevCommit> getAllCommits(Git gitRepo) throws Exception {
        return StreamSupport.stream(gitRepo.log().call().spliterator(), false).collect(Collectors.toList());
    }
}
