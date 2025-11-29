package de.tum.cit.aet.artemis.iris.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.domain.settings.CourseIrisSettings;

@Lazy
@Repository
@Profile(PROFILE_IRIS)
public interface CourseIrisSettingsRepository extends ArtemisJpaRepository<CourseIrisSettings, Long> {

    Optional<CourseIrisSettings> findByCourseId(Long courseId);

    void deleteByCourseId(Long courseId);
}
