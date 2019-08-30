package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;

/**
 * DTO used for specificing participationIds of programming exercises for which the build should be triggered.
 */
public class BuildTriggerDTO {

    private List<Long> participationIds;

    public List<Long> getParticipationIds() {
        return participationIds;
    }

    public void setParticipationIds(List<Long> participationIds) {
        this.participationIds = participationIds;
    }
}
