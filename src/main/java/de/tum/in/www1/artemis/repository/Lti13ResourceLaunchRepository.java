package de.tum.in.www1.artemis.repository;

import java.util.Collection;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.lti.Lti13ResourceLaunch;

public interface Lti13ResourceLaunchRepository extends JpaRepository<Lti13ResourceLaunch, Long> {

    Optional<Lti13ResourceLaunch> findByIssAndSubAndDeploymentIdAndResourceLinkId(@NotNull String iss, @NotNull String sub, @NotNull String deploymentId,
            @NotNull String resourceLinkId);

    Collection<Lti13ResourceLaunch> findByUserAndExercise(User user, Exercise exercise);
}
