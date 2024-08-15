package de.tum.in.www1.artemis.repository;

import java.util.Collection;
import java.util.Optional;

import org.jspecify.annotations.NonNull;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.lti.LtiResourceLaunch;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

public interface Lti13ResourceLaunchRepository extends ArtemisJpaRepository<LtiResourceLaunch, Long> {

    Optional<LtiResourceLaunch> findByIssAndSubAndDeploymentIdAndResourceLinkId(@NonNull String iss, @NonNull String sub, @NonNull String deploymentId,
            @NonNull String resourceLinkId);

    Collection<LtiResourceLaunch> findByUserAndExercise(User user, Exercise exercise);
}
