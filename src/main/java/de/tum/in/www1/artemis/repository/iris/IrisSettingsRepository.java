package de.tum.in.www1.artemis.repository.iris;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.iris.settings.IrisCourseSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisExerciseSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisGlobalSettings;
import de.tum.in.www1.artemis.domain.iris.settings.IrisSettings;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the IrisSettings entity.
 */
public interface IrisSettingsRepository extends JpaRepository<IrisSettings, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "irisChatSettings", "irisHestiaSettings", "irisCodeEditorSettings", "irisCompetencyGenerationSettings" })
    @Query("""
            SELECT irisSettings
            FROM IrisGlobalSettings irisSettings
            """)
    Set<IrisGlobalSettings> findAllGlobalSettings();

    default IrisGlobalSettings findGlobalSettingsElseThrow() {
        return findAllGlobalSettings().stream().max(Comparator.comparingLong(IrisGlobalSettings::getId)).orElseThrow(() -> new EntityNotFoundException("Iris Global Settings"));
    }

    @EntityGraph(type = LOAD, attributePaths = { "irisChatSettings", "irisHestiaSettings", "irisCodeEditorSettings", "irisCompetencyGenerationSettings" })
    @Query("""
            SELECT irisSettings
            FROM IrisCourseSettings irisSettings
            WHERE irisSettings.course.id = :courseId
            """)
    Optional<IrisCourseSettings> findCourseSettings(long courseId);

    /**
     * Retrieves Iris exercise settings for a given exercise ID.
     *
     * @param exerciseId for which settings are to be retrieved.
     * @return An Optional containing IrisExerciseSettings if found, otherwise empty.
     */
    @EntityGraph(type = LOAD, attributePaths = { "irisChatSettings" })
    @Query("""
            SELECT irisSettings
            FROM IrisExerciseSettings irisSettings
            WHERE irisSettings.exercise.id = :exerciseId
            """)
    Optional<IrisExerciseSettings> findExerciseSettings(long exerciseId);

    default IrisSettings findByIdElseThrow(long existingSettingsId) {
        return findById(existingSettingsId).orElseThrow(() -> new EntityNotFoundException("Iris Settings", existingSettingsId));
    }
}
