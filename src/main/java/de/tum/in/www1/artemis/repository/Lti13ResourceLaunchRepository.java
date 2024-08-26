package de.tum.in.www1.artemis.repository;

import java.util.Collection;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.lti.LtiResourceLaunch;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

public interface Lti13ResourceLaunchRepository extends ArtemisJpaRepository<LtiResourceLaunch, Long> {

    Optional<LtiResourceLaunch> findByIssAndSubAndDeploymentIdAndResourceLinkId(@NotNull String iss, @NotNull String sub, @NotNull String deploymentId,
            @NotNull String resourceLinkId);

    Collection<LtiResourceLaunch> findByUserAndExercise(User user, Exercise exercise);
}
