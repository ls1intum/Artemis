package de.tum.cit.aet.artemis.hyperion.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettingsEntity;

/**
 * Spring Data repository for the Hyperion course settings.
 */
@Lazy
@Repository
@Conditional(HyperionEnabled.class)
public interface HyperionCourseSettingsRepository extends ArtemisJpaRepository<HyperionCourseSettingsEntity, Long> {

    Optional<HyperionCourseSettingsEntity> findByCourseId(Long courseId);
}
