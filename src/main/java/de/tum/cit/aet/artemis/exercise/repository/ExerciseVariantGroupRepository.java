package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;

/**
 * Spring Data JPA repository for the {@link ExerciseVariantGroup} entity.
 * <p>
 * The {@code Course → ExerciseVariantGroup} relationship is unidirectional (the {@code course_id} foreign key lives on
 * the {@code exercise_variant_group} table but is owned by the {@code Course} collection), so the group itself has no
 * {@code course} attribute. Course-scoped lookups therefore navigate the collection from {@link de.tum.cit.aet.artemis.course.domain.Course}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ExerciseVariantGroupRepository extends ArtemisJpaRepository<ExerciseVariantGroup, Long> {

    @Query("""
            SELECT evg
            FROM Course c
                JOIN c.exerciseVariantGroups evg
                LEFT JOIN FETCH evg.exercises
            WHERE c.id = :courseId
            """)
    List<ExerciseVariantGroup> findAllByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT evg
            FROM Course c
                JOIN c.exerciseVariantGroups evg
                LEFT JOIN FETCH evg.exercises
            WHERE c.id = :courseId
                AND evg.id = :groupId
            """)
    Optional<ExerciseVariantGroup> findByIdAndCourseId(@Param("groupId") Long groupId, @Param("courseId") Long courseId);

    default ExerciseVariantGroup findByIdAndCourseIdElseThrow(Long groupId, Long courseId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdAndCourseId(groupId, courseId), groupId);
    }

    /**
     * Loads the group <em>without</em> its member exercises. Used for deletion: pulling the members into the persistence
     * context would make Hibernate's flush fail with a {@code TransientPropertyValueException} (the managed exercises
     * would still reference the removed group). With the members left unloaded, the {@code ON DELETE SET NULL} foreign
     * key on {@code exercise.exercise_variant_group_id} cleanly ungroups them.
     */
    @Query("""
            SELECT evg
            FROM Course c
                JOIN c.exerciseVariantGroups evg
            WHERE c.id = :courseId
                AND evg.id = :groupId
            """)
    Optional<ExerciseVariantGroup> findByIdAndCourseIdWithoutExercises(@Param("groupId") Long groupId, @Param("courseId") Long courseId);

    default ExerciseVariantGroup findByIdAndCourseIdWithoutExercisesElseThrow(Long groupId, Long courseId) throws EntityNotFoundException {
        return getValueElseThrow(findByIdAndCourseIdWithoutExercises(groupId, courseId), groupId);
    }
}
