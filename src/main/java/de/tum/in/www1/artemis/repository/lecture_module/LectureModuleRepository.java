package de.tum.in.www1.artemis.repository.lecture_module;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture_module.LectureModule;

/**
 * Spring Data JPA repository for the Lecture Module entity.
 */
@Repository
public interface LectureModuleRepository extends JpaRepository<LectureModule, Long> {
}
