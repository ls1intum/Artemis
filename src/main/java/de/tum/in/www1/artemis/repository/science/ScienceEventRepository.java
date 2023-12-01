package de.tum.in.www1.artemis.repository.science;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.science.ScienceEvent;

/**
 * Spring Data repository for the ScienceEvent entity.
 */
@Repository
public interface ScienceEventRepository extends JpaRepository<ScienceEvent, Long> {
}
