package de.tum.in.www1.artemis.repository.iris;

import java.util.Comparator;
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
            FROM IrisGlobalSettings irisSettings
                LEFT JOIN FETCH irisSettings.irisChatSettings ics
                LEFT JOIN FETCH irisSettings.irisHestiaSettings ihs
                LEFT JOIN FETCH irisSettings.irisCodeEditorSettings ices
            """)
    Set<IrisGlobalSettings> findAllGlobalSettings();

    default IrisGlobalSettings findGlobalSettingsElseThrow() {
        return findAllGlobalSettings().stream().max(Comparator.comparingLong(IrisGlobalSettings::getId)).orElseThrow(() -> new EntityNotFoundException("Iris Global Settings"));
    }

    @Query("""
            SELECT irisSettings
            FROM IrisCourseSettings irisSettings
                LEFT JOIN FETCH irisSettings.irisChatSettings ics
                LEFT JOIN FETCH irisSettings.irisHestiaSettings ihs
                LEFT JOIN FETCH irisSettings.irisCodeEditorSettings ices
            WHERE irisSettings.course.id = :courseId
            """)
    Optional<IrisCourseSettings> findCourseSettings(Long courseId);

    @Query("""
            SELECT irisSettings
            FROM IrisExerciseSettings irisSettings
                LEFT JOIN FETCH irisSettings.irisChatSettings ics
            WHERE irisSettings.exercise.id = :exerciseId
            """)
    Optional<IrisExerciseSettings> findExerciseSettings(Long exerciseId);

    default IrisSettings findByIdElseThrow(long existingSettingsId) {
        return findById(existingSettingsId).orElseThrow(() -> new EntityNotFoundException("Iris Settings", existingSettingsId));
    }
}
