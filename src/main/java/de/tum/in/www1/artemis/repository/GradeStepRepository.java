package de.tum.in.www1.artemis.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA Repository for the GradeStep entity
 */
@Repository
public interface GradeStepRepository extends JpaRepository<GradeStep, Long> {

    default GradeStep findByIdElseThrow(long gradeStepId) {
        return findById(gradeStepId).orElseThrow(() -> new EntityNotFoundException("GradeStep", gradeStepId));
    }
}
