package de.tum.in.www1.artemis.domain;

import org.eclipse.jgit.lib.BaseRepositoryBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Created by Josias Montag on 14.10.16.
 */
public class Repository extends org.eclipse.jgit.internal.storage.file.FileRepository {

    private Participation participation;
    private Path localPath;
    private Collection<File> files;

    public Repository(File gitDir) throws IOException {
        super(gitDir);
    }

    public Repository(String gitDir) throws IOException {
        super(gitDir);
    }

    public Repository(BaseRepositoryBuilder options) throws IOException {
        super(options);
    }

    /**
     * Check if the file is allowed in this repository.
     * This checks if the path of the file is a subdirectory of the repository directory.
     *
     * @param file
     * @return
     */
    public boolean isValidFile(java.io.File file) {

        if (file == null)
            return false;

        if (file.equals(this.localPath.toFile()))
            return true;

        return isValidFile(file.getParentFile());
    }


    public Participation getParticipation() {
        return participation;
    }

    public void setParticipation(Participation participation) {
        this.participation = participation;
    }

    public Path getLocalPath() {
        return localPath;
    }

    public void setLocalPath(Path localPath) {
        this.localPath = localPath;
    }

    public Collection<File> getFiles() {
        return files;
    }

    public void setFiles(Collection<File> files) {
        this.files = files;
    }
}
