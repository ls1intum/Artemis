package de.tum.in.www1.exerciseapp.domain;

import java.io.Serializable;
import java.net.URI;

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
        return super.toString().replaceFirst(repository.getLocalPath().toString(),"").replaceAll("^/+", "");
    }
}
