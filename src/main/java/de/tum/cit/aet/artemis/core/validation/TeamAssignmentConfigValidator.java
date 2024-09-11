package de.tum.cit.aet.artemis.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import de.tum.cit.aet.artemis.core.validation.constraints.TeamAssignmentConfigConstraints;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;

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
