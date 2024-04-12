package de.tum.in.www1.artemis.repository;

import java.util.Collection;
import java.util.Optional;

import jakarta.annotation.Nonnull;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.lti.LtiResourceLaunch;

public interface Lti13ResourceLaunchRepository extends JpaRepository<LtiResourceLaunch, Long> {

    Optional<LtiResourceLaunch> findByIssAndSubAndDeploymentIdAndResourceLinkId(@Nonnull String iss, @Nonnull String sub, @Nonnull String deploymentId,
            @Nonnull String resourceLinkId);

    Collection<LtiResourceLaunch> findByUserAndExercise(User user, Exercise exercise);
}
