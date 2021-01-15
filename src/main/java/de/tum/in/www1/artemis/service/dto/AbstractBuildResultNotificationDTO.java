package de.tum.in.www1.artemis.service.dto;

import java.time.ZonedDateTime;
import java.util.Optional;

public abstract class AbstractBuildResultNotificationDTO {

    public abstract ZonedDateTime getBuildRunDate();

    public abstract Optional<String> getCommitHashFromAssignmentRepo();

    public abstract Optional<String> getCommitHashFromTestsRepo();

    public abstract boolean isBuildSuccessful();

    // TODO: necessary?
    public abstract Long getBuildScore();

    /**
     * Returns a string stating how much tests passed:
     * Example: "1 of 10 passed"
     * @return
     */
    public abstract String getTestsPassedString();
}
