package de.tum.cit.aet.artemis.math.repository;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathExercise;

/**
 * Spring Data JPA repository for the MathExercise entity.
 */
@Conditional(MathEnabled.class)
@Lazy
@Repository
public interface MathExerciseRepository extends JpaRepository<MathExercise, Long>, JpaSpecificationExecutor<MathExercise> {

    @Query("SELECT e FROM MathExercise e LEFT JOIN FETCH e.categories WHERE e.id = :id")
    Optional<MathExercise> findByIdWithCategories(@Param("id") Long id);

    @Query("SELECT e FROM MathExercise e LEFT JOIN FETCH e.categories LEFT JOIN FETCH e.course WHERE e.id = :id")
    Optional<MathExercise> findByIdWithCategoriesAndCourse(@Param("id") Long id);

    @Query("SELECT e FROM MathExercise e LEFT JOIN FETCH e.categories WHERE e.course.id = :courseId")
    List<MathExercise> findByCourseIdWithCategories(@Param("courseId") Long courseId);

    @NonNull
    @EntityGraph(attributePaths = "categories")
    Page<MathExercise> findAll(@NonNull Specification<MathExercise> spec, @NonNull Pageable pageable);
}
