package de.tum.cit.aet.artemis.iris.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;

/**
 * Spring Data repository for the IrisSubSettings entity.
 */
@Repository
@Profile(PROFILE_IRIS)
public interface IrisSubSettingsRepository extends ArtemisJpaRepository<IrisSubSettings, Long> {

}
