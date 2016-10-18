package de.tum.in.www1.exerciseapp.domain;

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
