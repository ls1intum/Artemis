package de.tum.cit.aet.artemis.programming.domain;

import java.nio.file.Path;
import java.util.regex.Pattern;

public class File {

    private final java.io.File file;

    private Repository repository;

    public File(java.io.File file, Repository repository) {
        this.file = file;
        this.repository = repository;
    }

    public File(Path path, Repository repository) {
        this.file = path.toFile();
        this.repository = repository;
    }

    public File(String path, Repository repository) {
        this.file = Path.of(path).toFile();
        this.repository = repository;
    }

    public java.io.File getFile() {
        return file;
    }

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

    @Override
    public String toString() {
        String path = repository.getLocalPath().relativize(file.toPath()).toString();
        // Unify separator
        if (!"/".equals(java.io.File.separator)) {
            path = path.replaceAll(Pattern.quote(java.io.File.separator), "/");
        }
        return path;
    }

    public Path toPath() {
        return file.toPath();
    }

    public boolean isFile() {
        return file.isFile();
    }

    public boolean renameTo(File newFile) {
        return file.renameTo(newFile.getFile());
    }
}
