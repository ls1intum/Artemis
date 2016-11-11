package de.tum.in.www1.exerciseapp.web.rest.dto;

/**
 * Created by Josias Montag on 11.11.16.
 */
public class RepositoryStatusDTO {

    public RepositoryStatusDTO() {    }

    public Boolean isClean;

    public Boolean getClean() {
        return isClean;
    }

    public void setClean(Boolean clean) {
        isClean = clean;
    }
}
