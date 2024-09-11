package de.tum.cit.aet.artemis.lti.repository;

import java.util.Collection;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.lti.LtiResourceLaunch;

public interface Lti13ResourceLaunchRepository extends ArtemisJpaRepository<LtiResourceLaunch, Long> {

    Optional<LtiResourceLaunch> findByIssAndSubAndDeploymentIdAndResourceLinkId(@NotNull String iss, @NotNull String sub, @NotNull String deploymentId,
            @NotNull String resourceLinkId);

    Collection<LtiResourceLaunch> findByUserAndExercise(User user, Exercise exercise);
}
