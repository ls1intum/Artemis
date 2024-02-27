package de.tum.in.www1.artemis.repository.science;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.science.ScienceSetting;

/**
 * Spring Data repository for the ScienceSetting entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ScienceSettingRepository extends JpaRepository<ScienceSetting, Long> {

    Set<ScienceSetting> findAllByUserId(long userId);
}
