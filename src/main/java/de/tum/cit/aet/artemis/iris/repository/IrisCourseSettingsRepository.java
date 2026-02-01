package de.tum.cit.aet.artemis.iris.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettingsEntity;

@Lazy
@Repository
@Conditional(IrisEnabled.class)
public interface IrisCourseSettingsRepository extends ArtemisJpaRepository<IrisCourseSettingsEntity, Long> {

    Optional<IrisCourseSettingsEntity> findByCourseId(Long courseId);

    @Modifying
    @Transactional // ok because of delete
    void deleteByCourseId(Long courseId);
}
