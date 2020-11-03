package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.VideoUnit;

/**
 * Spring Data JPA repository for the Video Unit entity.
 */
@Repository
public interface VideoUnitRepository extends JpaRepository<VideoUnit, Long> {
}
