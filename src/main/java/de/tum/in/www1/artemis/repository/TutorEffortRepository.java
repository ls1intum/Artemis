package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.statistics.tutor.effort.TutorEffort;

/**
 * Spring Data JPA repository for the TutorParticipation entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TutorEffortRepository extends JpaRepository<TutorEffort, Long> {

}
