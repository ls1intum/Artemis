package de.tum.cit.aet.artemis.proof.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.proof.domain.ProofExercise;

/**
 * Spring Data JPA repository for the ProofExercise entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface ProofExerciseRepository extends JpaRepository<ProofExercise, Long> {

    @Query("SELECT e FROM ProofExercise e LEFT JOIN FETCH e.categories WHERE e.id = :id")
    Optional<ProofExercise> findByIdWithCategories(@Param("id") Long id);

    @Query("SELECT e FROM ProofExercise e LEFT JOIN FETCH e.categories LEFT JOIN FETCH e.course WHERE e.id = :id")
    Optional<ProofExercise> findByIdWithCategoriesAndCourse(@Param("id") Long id);

    @Query("SELECT e FROM ProofExercise e LEFT JOIN FETCH e.categories WHERE e.course.id = :courseId")
    List<ProofExercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @EntityGraph(attributePaths = "categories")
    @Override
    Page<ProofExercise> findAll(Pageable pageable);
}
