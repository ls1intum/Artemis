package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Created by Josias Montag on 11.11.16.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RepositoryStatusDTO {

    public RepositoryStatusDTOType repositoryStatus;

    public RepositoryStatusDTO() {
    }

    public RepositoryStatusDTOType getRepositoryStatus() {
        return repositoryStatus;
    }

    public void setRepositoryStatus(RepositoryStatusDTOType repositoryStatus) {
        this.repositoryStatus = repositoryStatus;
    }
}
