package de.tum.in.www1.artemis.repository.settings;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.settings.ide.Ide;
import de.tum.in.www1.artemis.domain.settings.ide.UserIdeMapping;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the UserIdeMapping entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface UserIdeMappingRepository extends ArtemisJpaRepository<UserIdeMapping, UserIdeMapping.UserIdeMappingId> {

    List<UserIdeMapping> findAllByUserId(Long userId);

    boolean existsByIde(Ide ide);
}
