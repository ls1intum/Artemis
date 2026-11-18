package de.tum.cit.aet.artemis.iris.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettingsEntity;

@Lazy
@Repository
@Conditional(IrisEnabled.class)
public interface IrisCourseSettingsRepository extends ArtemisJpaRepository<IrisCourseSettingsEntity, Long> {

    Optional<IrisCourseSettingsEntity> findByCourseId(Long courseId);

    List<IrisCourseSettingsEntity> findAllByCourseIdIn(Collection<Long> courseIds);

    /**
     * Updates the payload of the permanent settings row created with the course.
     *
     * @param courseId the course id
     * @param settings the validated settings
     * @return the number of updated rows
     */
    @Modifying
    @Transactional // ok because of the update
    @Query("UPDATE IrisCourseSettingsEntity entity SET entity.settings = :settings WHERE entity.courseId = :courseId")
    int updateSettings(@Param("courseId") long courseId, @Param("settings") IrisCourseSettings settings);

    @Modifying
    @Transactional // ok because of delete
    void deleteByCourseId(Long courseId);
}
