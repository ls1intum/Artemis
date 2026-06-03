package de.tum.cit.aet.artemis.course.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface CourseAthenaConfigRepository extends ArtemisJpaRepository<CourseAthenaConfig, Long> {

    Optional<CourseAthenaConfig> findByCourseId(Long courseId);

    void deleteByCourseId(Long courseId);
}
