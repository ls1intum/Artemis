package de.tum.cit.aet.artemis.core.test_repository;

import java.util.Set;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;
import de.tum.cit.aet.artemis.atlas.repository.ScienceEventRepository;

@Repository
public interface ScienceEventTestRepository extends ScienceEventRepository {

    Set<ScienceEvent> findAllByType(ScienceEventType type);
}
