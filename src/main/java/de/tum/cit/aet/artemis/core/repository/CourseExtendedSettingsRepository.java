package de.tum.cit.aet.artemis.core.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.domain.CourseExtendedSettings;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the CourseExtendedSettings entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CourseExtendedSettingsRepository extends ArtemisJpaRepository<CourseExtendedSettings, Long> {

    @Query("""
            SELECT DISTINCT s.description
            FROM CourseExtendedSettings s
            WHERE s.course.id = :courseId
            """)
    String findDescriptionByCourseId(@Param("courseId") Long courseId);
}
