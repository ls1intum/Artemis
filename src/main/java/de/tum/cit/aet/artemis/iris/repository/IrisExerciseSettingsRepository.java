package de.tum.cit.aet.artemis.iris.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisExerciseSettings;

@Repository
@Profile(PROFILE_IRIS)
public interface IrisExerciseSettingsRepository extends ArtemisJpaRepository<IrisExerciseSettings, Long> {

    @Query("""
            SELECT COUNT(s) > 0
            FROM IrisExerciseSettings s
            WHERE s.exercise.id = :exerciseId
                AND s.irisTextExerciseChatSettings.enabled = TRUE
            """)
    boolean isExerciseChatEnabled(@Param("exerciseId") long exerciseId);
}
