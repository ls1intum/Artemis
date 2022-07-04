package de.tum.in.www1.artemis.domain;

import java.nio.file.Path;
import java.util.regex.Pattern;

public class File extends java.io.File {

    private Repository repository;

    public File(java.io.File file, Repository repository) {
        super(file.getPath());
        this.repository = repository;
    }

    public File(Path path, Repository repository) {
        super(path.toString());
        this.repository = repository;
    }

    public File(String path, Repository repository) {
        super(path);
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
        String path = repository.getLocalPath().relativize(super.toPath()).toString();
        // Unify separator
        if (!"/".equals(File.separator)) {
            path = path.replaceAll(Pattern.quote(File.separator), "/");
        }
        return path;
    }
}
