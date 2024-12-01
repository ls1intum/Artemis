package de.tum.cit.aet.artemis.lti.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LTI;

import java.util.Collection;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lti.domain.LtiResourceLaunch;

@Profile(PROFILE_LTI)
@Repository
public interface Lti13ResourceLaunchRepository extends ArtemisJpaRepository<LtiResourceLaunch, Long> {

    Optional<LtiResourceLaunch> findByIssAndSubAndDeploymentIdAndResourceLinkId(@NotNull String iss, @NotNull String sub, @NotNull String deploymentId,
            @NotNull String resourceLinkId);

    Collection<LtiResourceLaunch> findByUserAndExercise(User user, Exercise exercise);
}
