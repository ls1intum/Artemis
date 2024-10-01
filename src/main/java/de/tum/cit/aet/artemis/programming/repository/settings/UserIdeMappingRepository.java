package de.tum.cit.aet.artemis.programming.repository.settings;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ide.Ide;
import de.tum.cit.aet.artemis.programming.domain.ide.UserIdeMapping;

/**
 * Spring Data repository for the UserIdeMapping entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface UserIdeMappingRepository extends ArtemisJpaRepository<UserIdeMapping, UserIdeMapping.UserIdeMappingId> {

    List<UserIdeMapping> findAllByUserId(Long userId);

    boolean existsByIde(Ide ide);
}
