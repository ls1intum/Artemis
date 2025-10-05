package de.tum.cit.aet.artemis.iris.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisExerciseSettings;

@Lazy
@Repository
@Profile(PROFILE_IRIS)
public interface IrisExerciseSettingsRepository extends ArtemisJpaRepository<IrisExerciseSettings, Long> {

    @Query("""
            SELECT CASE
                       WHEN COALESCE(gps.enabled, TRUE) = TRUE
                        AND COALESCE(cps.enabled, TRUE) = TRUE
                        AND COALESCE(eps.enabled, TRUE) = TRUE
                       THEN TRUE
                       ELSE FALSE
                   END
            FROM Exercise e
                LEFT JOIN IrisCourseSettings   cs  ON cs.courseId   = e.course.id
                LEFT JOIN IrisExerciseSettings es  ON es.exerciseId = e.id
                LEFT JOIN IrisGlobalSettings   gs  ON TRUE

                LEFT JOIN gs.irisProgrammingExerciseChatSettings gps
                LEFT JOIN cs.irisProgrammingExerciseChatSettings cps
                LEFT JOIN es.irisProgrammingExerciseChatSettings eps

            WHERE e.id = :exerciseId
            """)
    Boolean isProgrammingExerciseChatEnabled(@Param("exerciseId") long exerciseId);

    @Transactional // ok because of delete
    @Modifying
    void deleteByExerciseId(long exerciseId);
}
