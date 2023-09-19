package de.tum.in.www1.artemis.repository.iris;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.iris.settings.IrisSettings;

/**
 * Spring Data repository for the IrisSettings entity.
 */
public interface IrisSettingsRepository extends JpaRepository<IrisSettings, Long> {

    @Query("""
            SELECT irisSettings
            FROM IrisSettings irisSettings
            LEFT JOIN FETCH irisSettings.irisChatSettings ics
            WHERE irisSettings.isGlobal = true
            """)
    Set<IrisSettings> findAllGlobalSettings();
}
