package de.tum.in.www1.artemis.repository.iris;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.iris.settings.IrisCourseSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisExerciseSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisGlobalSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSettings;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the IrisSettings entity.
 */
public interface IrisSettingsRepository extends ArtemisJpaRepository<IrisSettings, Long> {

    @Query("""
            SELECT irisSettings
            FROM IrisGlobalSettings irisSettings
                LEFT JOIN FETCH irisSettings.irisChatSettings
                LEFT JOIN FETCH irisSettings.irisLectureIngestionSettings
                LEFT JOIN FETCH irisSettings.irisHestiaSettings
                LEFT JOIN FETCH irisSettings.irisCompetencyGenerationSettings
            """)
    Set<IrisGlobalSettings> findAllGlobalSettings();

    default IrisGlobalSettings findGlobalSettingsElseThrow() {
        return findAllGlobalSettings().stream().max(Comparator.comparingLong(IrisGlobalSettings::getId)).orElseThrow(() -> new EntityNotFoundException("Iris Global Settings"));
    }

    @Query("""
            SELECT irisSettings
            FROM IrisCourseSettings irisSettings
                LEFT JOIN FETCH irisSettings.irisChatSettings
                LEFT JOIN FETCH irisSettings.irisLectureIngestionSettings
                LEFT JOIN FETCH irisSettings.irisHestiaSettings
                LEFT JOIN FETCH irisSettings.irisCompetencyGenerationSettings
            WHERE irisSettings.course.id = :courseId
            """)
    Optional<IrisCourseSettings> findCourseSettings(@Param("courseId") long courseId);

    /**
     * Retrieves Iris exercise settings for a given exercise ID.
     *
     * @param exerciseId for which settings are to be retrieved.
     * @return An Optional containing IrisExerciseSettings if found, otherwise empty.
     */
    @Query("""
            SELECT irisSettings
            FROM IrisExerciseSettings irisSettings
                LEFT JOIN FETCH irisSettings.irisChatSettings
            WHERE irisSettings.exercise.id = :exerciseId
            """)
    Optional<IrisExerciseSettings> findExerciseSettings(@Param("exerciseId") long exerciseId);
}
