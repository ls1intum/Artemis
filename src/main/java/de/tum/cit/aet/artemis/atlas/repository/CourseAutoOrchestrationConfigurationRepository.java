package de.tum.cit.aet.artemis.atlas.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseAutoOrchestrationConfiguration;
import de.tum.cit.aet.artemis.atlas.dto.CourseAutoOrchestrationConfigDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Repository for {@link CourseAutoOrchestrationConfiguration}. Exposes only the hot-path projection
 * the auto-orchestration pipeline needs; the configuration is otherwise written through the course
 * update flow ({@code CourseUpdateDTO#applyTo}) via the {@code Course} association cascade.
 */
@Conditional(AtlasEnabled.class)
@Repository
public interface CourseAutoOrchestrationConfigurationRepository extends ArtemisJpaRepository<CourseAutoOrchestrationConfiguration, Long> {

    /**
     * Lightweight projection of a course's auto-orchestration configuration (kill switch plus the
     * nullable debounce / daily-cap overrides), read on the accumulator hot path without loading the
     * full entity. Returns empty when the course has no configuration row, in which case callers fall
     * back to the global defaults and treat the pipeline as disabled.
     *
     * @param courseId the course to resolve the configuration for
     * @return the projected configuration, or empty when the course has no configuration row
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.atlas.dto.CourseAutoOrchestrationConfigDTO(
                config.enabled, config.debounceWindowSecondsOverride, config.maxDailyOrchestrationOverride)
            FROM CourseAutoOrchestrationConfiguration config
            WHERE config.course.id = :courseId
            """)
    Optional<CourseAutoOrchestrationConfigDTO> findConfigByCourseId(@Param("courseId") long courseId);
}
