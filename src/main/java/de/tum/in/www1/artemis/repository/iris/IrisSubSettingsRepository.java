package de.tum.in.www1.artemis.repository.iris;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.iris.settings.IrisSubSettings;

/**
 * Spring Data repository for the IrisSubSettings entity.
 */
public interface IrisSubSettingsRepository extends JpaRepository<IrisSubSettings, Long> {

}
