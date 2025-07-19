package de.tum.cit.aet.artemis.iris.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisExerciseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisGlobalSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSettings;

/**
 * Spring Data repository for the IrisSettings entity.
 */
@Lazy
@Repository
@Profile(PROFILE_IRIS)
public interface IrisSettingsRepository extends ArtemisJpaRepository<IrisSettings, Long> {

    @Query("""
            SELECT irisSettings
            FROM IrisGlobalSettings irisSettings
                LEFT JOIN FETCH irisSettings.irisProgrammingExerciseChatSettings
                LEFT JOIN FETCH irisSettings.irisTextExerciseChatSettings
                LEFT JOIN FETCH irisSettings.irisLectureIngestionSettings
                LEFT JOIN FETCH irisSettings.irisCompetencyGenerationSettings
                LEFT JOIN FETCH irisSettings.irisLectureChatSettings
                LEFT JOIN FETCH irisSettings.irisFaqIngestionSubSettings
                LEFT JOIN FETCH irisSettings.irisTutorSuggestionSubSettings
            """)
    Set<IrisGlobalSettings> findAllGlobalSettings();

    default IrisGlobalSettings findGlobalSettingsElseThrow() {
        return getValueElseThrow(findAllGlobalSettings().stream().max(Comparator.comparingLong(IrisGlobalSettings::getId)));
    }

    @Query("""
            SELECT irisSettings
            FROM IrisCourseSettings irisSettings
                LEFT JOIN FETCH irisSettings.irisProgrammingExerciseChatSettings
                LEFT JOIN FETCH irisSettings.irisTextExerciseChatSettings
                LEFT JOIN FETCH irisSettings.irisLectureIngestionSettings
                LEFT JOIN FETCH irisSettings.irisCompetencyGenerationSettings
                LEFT JOIN FETCH irisSettings.irisLectureChatSettings
                LEFT JOIN FETCH irisSettings.irisFaqIngestionSettings
                LEFT JOIN FETCH irisSettings.irisTutorSuggestionSettings
            WHERE irisSettings.courseId = :courseId
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
                LEFT JOIN FETCH irisSettings.irisProgrammingExerciseChatSettings
                LEFT JOIN FETCH irisSettings.irisTextExerciseChatSettings
            WHERE irisSettings.exerciseId = :exerciseId
            """)
    Optional<IrisExerciseSettings> findExerciseSettings(@Param("exerciseId") long exerciseId);

    /**
     * Checks if the course chat is enabled for a given course ID, based on the global settings and the course settings.
     * If both settings are not found, it defaults to true.
     * If the course settings are not found, it returns the global settings value.
     * If the global settings are not found, it returns the course settings value.
     * If both are found, it returns true if both settings are enabled, otherwise false.
     *
     * @param courseId the ID of the course to check
     * @return true if the course chat is enabled, false otherwise
     */
    @Query("""
            SELECT CASE
                       WHEN COALESCE(gps.enabled, TRUE) = TRUE
                        AND COALESCE(cps.enabled, TRUE) = TRUE
                       THEN TRUE
                       ELSE FALSE
                   END
            FROM Course c
            LEFT JOIN IrisCourseSettings   cs  ON cs.courseId = c.id
            LEFT JOIN IrisGlobalSettings   gs  ON TRUE

            LEFT JOIN gs.irisCourseChatSettings gps
            LEFT JOIN cs.irisCourseChatSettings cps

            WHERE c.id = :courseId
            """)
    Boolean isCourseChatEnabled(@Param("courseId") long courseId);
}
