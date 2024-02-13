package de.tum.in.www1.artemis.repository.science;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.science.ScienceEvent;
import de.tum.in.www1.artemis.domain.science.ScienceEventType;

/**
 * Spring Data repository for the ScienceEvent entity.
 */
@Repository
public interface ScienceEventRepository extends JpaRepository<ScienceEvent, Long> {

    Set<ScienceEvent> findAllByType(ScienceEventType type);
}
