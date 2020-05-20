package de.tum.in.www1.artemis.domain;

/**
 * Created by Josias Montag on 14.10.16.
 */
public class File extends java.io.File {

    private Repository repository;

    public File(java.io.File file, Repository repository) {
        super(file.getPath());
        this.repository = repository;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    @Override
    public String toString() {
        // Make windows paths safe
        String safeFilename = super.toString().replaceAll("\\\\", "/");
        String safeRepositoryPath = repository.getLocalPath().toString().replaceAll("\\\\", "/");

        return safeFilename.replaceFirst(safeRepositoryPath, "").replaceAll("^/+", "");
    }
}
