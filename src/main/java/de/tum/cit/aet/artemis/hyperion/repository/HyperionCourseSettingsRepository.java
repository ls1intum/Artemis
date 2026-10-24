package de.tum.cit.aet.artemis.hyperion.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettings;
import de.tum.cit.aet.artemis.hyperion.domain.HyperionCourseSettingsEntity;

/**
 * Spring Data repository for the Hyperion course settings.
 */
@Lazy
@Repository
@Conditional(HyperionEnabled.class)
public interface HyperionCourseSettingsRepository extends ArtemisJpaRepository<HyperionCourseSettingsEntity, Long> {

    Optional<HyperionCourseSettingsEntity> findByCourseId(Long courseId);

    /**
     * Replaces the payload of the settings row created with the course.
     *
     * @param courseId the course
     * @param settings the validated settings
     * @return the number of updated rows, zero when the course has no row
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("UPDATE HyperionCourseSettingsEntity entity SET entity.settings = :settings WHERE entity.courseId = :courseId")
    int updateSettings(@Param("courseId") long courseId, @Param("settings") HyperionCourseSettings settings);
}
