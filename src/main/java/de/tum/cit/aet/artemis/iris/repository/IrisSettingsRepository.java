package de.tum.cit.aet.artemis.iris.repository;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisExerciseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisGlobalSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSettings;

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
        return getValueElseThrow(findAllGlobalSettings().stream().max(Comparator.comparingLong(IrisGlobalSettings::getId)));
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
