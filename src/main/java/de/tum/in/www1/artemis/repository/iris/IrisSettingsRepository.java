package de.tum.in.www1.artemis.repository.iris;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.iris.settings.*;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the IrisSettings entity.
 */
public interface IrisSettingsRepository extends JpaRepository<IrisSettings, Long> {

    @Query("""
            SELECT irisSettings
            FROM IrisSettings irisSettings
                LEFT JOIN FETCH irisSettings.irisChatSettings ics
                LEFT JOIN FETCH irisSettings.irisHestiaSettings ihs
            WHERE type(irisSettings) = IrisGlobalSettings
            """)
    Set<IrisGlobalSettings> findAllGlobalSettings();

    @Query("""
            SELECT irisSettings
            FROM IrisSettings irisSettings
                LEFT JOIN FETCH irisSettings.irisChatSettings ics
                LEFT JOIN FETCH irisSettings.irisHestiaSettings ihs
            WHERE type(irisSettings) = IrisGlobalSettings
            ORDER BY irisSettings.id DESC
            LIMIT 1
            """)
    Optional<IrisGlobalSettings> findGlobalSettings();

    default IrisGlobalSettings findGlobalSettingsElseThrow() {
        return findGlobalSettings().orElseThrow(() -> new EntityNotFoundException("Iris Global Settings"));
    }

    @Query("""
            SELECT irisSettings
            FROM IrisSettings irisSettings
                LEFT JOIN FETCH irisSettings.irisChatSettings ics
                LEFT JOIN FETCH irisSettings.irisHestiaSettings ihs
            WHERE type(irisSettings) = IrisCourseSettings
                AND irisSettings.course.id = :courseId
            """)
    Optional<IrisCourseSettings> findCourseSettings(Long courseId);

    @Query("""
            SELECT irisSettings
            FROM IrisSettings irisSettings
                LEFT JOIN FETCH irisSettings.irisChatSettings ics
                LEFT JOIN FETCH irisSettings.irisHestiaSettings ihs
            WHERE type(irisSettings) = IrisExerciseSettings
                AND irisSettings.exercise.id = :exerciseId
            """)
    Optional<IrisExerciseSettings> findExerciseSettings(Long exerciseId);

    default IrisSettings findByIdElseThrow(long existingSettingsId) {
        return findById(existingSettingsId).orElseThrow(() -> new EntityNotFoundException("Iris Settings", existingSettingsId));
    }
}
