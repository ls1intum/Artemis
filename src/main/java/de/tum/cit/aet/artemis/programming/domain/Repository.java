package de.tum.cit.aet.artemis.programming.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

import org.eclipse.jgit.lib.BaseRepositoryBuilder;

import de.tum.cit.aet.artemis.core.service.connectors.localvc.LocalVCRepositoryUri;

/**
 * This class represents repositories cloned from the VC system to Artemis to then be used in the online editor.
 * These repositories are cloned from the remote VCS and are saved in the folder defined in the application properties at artemis.repo-clone-path.
 * Note: This class does not represent local VCS repositories. The local VCS is treated as a remote VCS in code (like GitLab) and is represented by the
 * {@link LocalVCRepositoryUri} class.
 * Its repositories are saved as bare repositories in the folder defined in the application properties at artemis.version-control.local-vcs-repo-path.
 */
public class Repository extends org.eclipse.jgit.internal.storage.file.FileRepository {

    private ProgrammingExerciseParticipation participation;

    private Path localPath;

    private final VcsRepositoryUri remoteRepositoryUri;

    private Collection<File> files;

    public Repository(File gitDir, VcsRepositoryUri remoteRepositoryUri) throws IOException {
        super(gitDir);
        this.remoteRepositoryUri = remoteRepositoryUri;
    }

    public Repository(String gitDir, VcsRepositoryUri remoteRepositoryUri) throws IOException {
        super(gitDir);
        this.remoteRepositoryUri = remoteRepositoryUri;
    }

    public Repository(BaseRepositoryBuilder options, Path localPath, VcsRepositoryUri remoteRepositoryUri) throws IOException {
        super(options);
        this.localPath = localPath.normalize();
        this.remoteRepositoryUri = remoteRepositoryUri;
    }

    /**
     * Check if the file is allowed in this repository. This checks if the path of the file is a subdirectory of the repository directory. Also checks that the ../ operator is not
     * used to traverse up directories on the server. Also checks that the file is not using the ".git" folder
     *
     * @param file for which to check if it is valid.
     * @return true if the file is valid.
     */
    public boolean isValidFile(java.io.File file) {

        if (file == null || file.getPath().contains("../") || file.getPath().contains(".git")) {
            return false;
        }

        if (file.equals(this.localPath.toFile())) {
            return true;
        }

        return isValidFile(file.getParentFile());
    }

    public ProgrammingExerciseParticipation getParticipation() {
        return participation;
    }

    public void setParticipation(ProgrammingExerciseParticipation participation) {
        this.participation = participation;
    }

    public Path getLocalPath() {
        return localPath;
    }

    public Collection<File> getFiles() {
        return files;
    }

    public void setFiles(Collection<File> files) {
        this.files = files;
    }

    public void closeBeforeDelete() {
        super.close();
        super.doClose();
    }

    public VcsRepositoryUri getRemoteRepositoryUri() {
        return remoteRepositoryUri;
    }
}
