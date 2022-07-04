package de.tum.in.www1.artemis.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

import org.eclipse.jgit.lib.BaseRepositoryBuilder;

import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;

public class Repository extends org.eclipse.jgit.internal.storage.file.FileRepository {

    private ProgrammingExerciseParticipation participation;

    private Path localPath;

    private final VcsRepositoryUrl remoteRepositoryUrl;

    private Map<File, FileType> filesAndFolders;

    private Collection<File> files;

    public Repository(File gitDir, VcsRepositoryUrl remoteRepositoryUrl) throws IOException {
        super(gitDir);
        this.remoteRepositoryUrl = remoteRepositoryUrl;
    }

    public Repository(String gitDir, VcsRepositoryUrl remoteRepositoryUrl) throws IOException {
        super(gitDir);
        this.remoteRepositoryUrl = remoteRepositoryUrl;
    }

    public Repository(BaseRepositoryBuilder options, Path localPath, VcsRepositoryUrl remoteRepositoryUrl) throws IOException {
        super(options);
        this.localPath = localPath.normalize();
        this.remoteRepositoryUrl = remoteRepositoryUrl;
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

    public Map<File, FileType> getContent() {
        return filesAndFolders;
    }

    public void setContent(Map<File, FileType> filesAndFolders) {
        this.filesAndFolders = filesAndFolders;
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

    public VcsRepositoryUrl getRemoteRepositoryUrl() {
        return remoteRepositoryUrl;
    }
}
