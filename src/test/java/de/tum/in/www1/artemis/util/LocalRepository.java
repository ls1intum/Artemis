package de.tum.in.www1.artemis.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;

public class LocalRepository {

    public File localRepoFile;

    public File originRepoFile;

    public Git localGit;

    Git originGit;

    public void configureRepos(String localRepoFileName, String originRepoFileName) throws Exception {

        this.localRepoFile = Files.createTempDirectory(localRepoFileName).toFile();
        this.localGit = Git.init().setDirectory(localRepoFile).call();

        this.originRepoFile = Files.createTempDirectory(originRepoFileName).toFile();
        this.originGit = Git.init().setDirectory(originRepoFile).call();

        StoredConfig config = this.localGit.getRepository().getConfig();
        config.setString("remote", "origin", "url", this.originRepoFile.getAbsolutePath());
        config.save();
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
}
