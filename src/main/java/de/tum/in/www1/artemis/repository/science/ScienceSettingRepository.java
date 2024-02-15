package de.tum.in.www1.artemis.repository.science;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.science.ScienceSetting;

/**
 * Spring Data repository for the ScienceSetting entity.
 */
@Repository
public interface ScienceSettingRepository extends JpaRepository<ScienceSetting, Long> {

    Set<ScienceSetting> findAllByUserId(long userId);
}
