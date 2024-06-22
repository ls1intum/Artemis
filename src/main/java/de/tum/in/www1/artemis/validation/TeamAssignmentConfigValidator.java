package de.tum.in.www1.artemis.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import de.tum.in.www1.artemis.domain.TeamAssignmentConfig;
import de.tum.in.www1.artemis.validation.constraints.TeamAssignmentConfigConstraints;

/**
 * Validates a team assignment configuration by checking that these criteria are fulfilled:
 * - team size range is valid (min team size is less than or equal to max team size)
 */
public class TeamAssignmentConfigValidator implements ConstraintValidator<TeamAssignmentConfigConstraints, TeamAssignmentConfig> {

    @Override
    public void initialize(TeamAssignmentConfigConstraints constraint) {
    }

    @Override
    public boolean isValid(TeamAssignmentConfig object, ConstraintValidatorContext context) {
        return object.getMinTeamSize() <= object.getMaxTeamSize();
    }
}
