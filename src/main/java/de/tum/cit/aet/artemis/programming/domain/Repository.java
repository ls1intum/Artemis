package de.tum.cit.aet.artemis.programming.domain;

import java.io.IOException;
import java.nio.file.Path;

import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

/**
 * This class represents repositories cloned from the VC system to Artemis to then be used in the online editor.
 * These repositories are cloned from the remote VCS and are saved in the folder defined in the application properties at artemis.repo-clone-path.
 * Note: This class does not represent local VCS repositories. The local VCS is treated as a remote VCS in code and is represented by the
 * {@link LocalVCRepositoryUri} class.
 * Its repositories are saved as bare repositories in the folder defined in the application properties at artemis.version-control.local-vcs-repo-path.
 */
public class Repository extends org.eclipse.jgit.internal.storage.file.FileRepository {

    private static final Logger log = LoggerFactory.getLogger(Repository.class);

    private ProgrammingExerciseParticipation participation;

    private Path localPath;

    private final LocalVCRepositoryUri remoteRepositoryUri;

    public Repository(File gitDir, LocalVCRepositoryUri remoteRepositoryUri) throws IOException {
        super(gitDir);
        this.remoteRepositoryUri = remoteRepositoryUri;
    }

    public Repository(String gitDir, LocalVCRepositoryUri remoteRepositoryUri) throws IOException {
        super(gitDir);
        this.remoteRepositoryUri = remoteRepositoryUri;
    }

    public Repository(BaseRepositoryBuilder options, Path localPath, LocalVCRepositoryUri remoteRepositoryUri) throws IOException {
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
        if (file == null || file.getPath().contains("../")) {
            return false;
        }

        if (file.isDirectory() && file.getName().equals(".git")) {
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

    /**
     * Closes the Git repository before deletion to release file handles.
     * This method ensures that the repository is properly closed to avoid
     * file lock issues during deletion operations.
     */
    public void closeBeforeDelete() {
        super.close();
        super.doClose();
    }

    public LocalVCRepositoryUri getRemoteRepositoryUri() {
        return remoteRepositoryUri;
    }
}
