package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.OnlineUnit;

/**
 * Spring Data JPA repository for the Online Unit entity.
 */
@Repository
public interface OnlineUnitRepository extends JpaRepository<OnlineUnit, Long> {
}
