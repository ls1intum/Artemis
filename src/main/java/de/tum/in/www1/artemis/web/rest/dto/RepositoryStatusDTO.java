package de.tum.in.www1.artemis.web.rest.dto;

/**
 * Created by Josias Montag on 11.11.16.
 */
public class RepositoryStatusDTO {

    public Boolean isClean;

    public RepositoryStatusDTO() {
    }

    public Boolean getClean() {
        return isClean;
    }

    public void setClean(Boolean clean) {
        isClean = clean;
    }
}
