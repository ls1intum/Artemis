package de.tum.cit.aet.artemis.lti.repository;

import java.util.Collection;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lti.config.LtiEnabled;
import de.tum.cit.aet.artemis.lti.domain.LtiResourceLaunch;

@Conditional(LtiEnabled.class)
@Lazy
@Repository
public interface Lti13ResourceLaunchRepository extends ArtemisJpaRepository<LtiResourceLaunch, Long> {

    Optional<LtiResourceLaunch> findByIssAndSubAndDeploymentIdAndResourceLinkId(@NonNull String iss, @NonNull String sub, @NonNull String deploymentId,
            @NonNull String resourceLinkId);

    Collection<LtiResourceLaunch> findByUserAndExercise(User user, Exercise exercise);
}
