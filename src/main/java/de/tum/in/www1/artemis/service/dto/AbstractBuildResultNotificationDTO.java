package de.tum.in.www1.artemis.service.dto;

import java.time.ZonedDateTime;
import java.util.Optional;

public abstract class AbstractBuildResultNotificationDTO {

    public abstract ZonedDateTime getBuildRunDate();

    public abstract Optional<String> getCommitHashFromAssignmentRepo();

    public abstract Optional<String> getCommitHashFromTestsRepo();

}
