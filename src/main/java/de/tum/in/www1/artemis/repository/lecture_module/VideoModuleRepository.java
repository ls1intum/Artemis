package de.tum.in.www1.artemis.repository.lecture_module;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture_module.VideoModule;

/**
 * Spring Data JPA repository for the Video Module entity.
 */
@Repository
public interface VideoModuleRepository extends JpaRepository<VideoModule, Long> {
}
