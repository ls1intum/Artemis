package de.tum.cit.aet.artemis.iris.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettingsEntity;

@Lazy
@Repository
@Profile(PROFILE_IRIS)
public interface IrisCourseSettingsRepository extends ArtemisJpaRepository<IrisCourseSettingsEntity, Long> {

    Optional<IrisCourseSettingsEntity> findByCourseId(Long courseId);

    @Modifying
    @Transactional
    void deleteByCourseId(Long courseId);
}
