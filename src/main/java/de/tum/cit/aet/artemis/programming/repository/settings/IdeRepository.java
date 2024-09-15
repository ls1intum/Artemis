package de.tum.cit.aet.artemis.programming.repository.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ide.Ide;

/**
 * Spring Data repository for the Ide entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface IdeRepository extends ArtemisJpaRepository<Ide, Long> {

    Optional<Ide> findByDeepLink(String deepLink);
}
