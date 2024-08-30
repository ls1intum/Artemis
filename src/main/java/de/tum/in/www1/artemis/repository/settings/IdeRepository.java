package de.tum.in.www1.artemis.repository.settings;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.settings.ide.Ide;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the Ide entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface IdeRepository extends ArtemisJpaRepository<Ide, Long> {

    Optional<Ide> findByDeepLink(String deepLink);
}
