package de.tum.in.www1.artemis.repository.lecture_module;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture_module.ExerciseModule;

/**
 * Spring Data JPA repository for the Exercise Module entity.
 */
@Repository
public interface ExerciseModuleRepository extends JpaRepository<ExerciseModule, Long> {
}
