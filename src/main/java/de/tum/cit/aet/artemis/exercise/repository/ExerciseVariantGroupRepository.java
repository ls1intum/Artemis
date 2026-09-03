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

    // Milestone groups are excluded here and served by MilestoneExerciseGroupRepository instead. Fetching a
    // MilestoneExerciseGroup's milestoneExercise from this base-type query would need
    // TREAT(evg AS MilestoneExerciseGroup).milestoneExercise, which restricts the whole query to that subtype rather than
    // only the join - even inside a LEFT JOIN FETCH - so every other group silently vanished from the result. Returning
    // milestone groups here without that fetch is no better: their timeline getters delegate to the unfetched anchor and
    // would read as "no dates".
    @Query("""
            SELECT DISTINCT evg
            FROM Course c
                JOIN c.exerciseVariantGroups evg
                LEFT JOIN FETCH evg.exercises
            WHERE c.id = :courseId
                AND TYPE(evg) <> MilestoneExerciseGroup
            """)
    List<ExerciseVariantGroup> findAllByCourseId(@Param("courseId") Long courseId);

    // Milestone groups are excluded - see the comment on findAllByCourseId.
    @Query("""
            SELECT DISTINCT evg
            FROM Course c
                JOIN c.exerciseVariantGroups evg
                LEFT JOIN FETCH evg.exercises
            WHERE c.id = :courseId
                AND evg.id = :groupId
                AND TYPE(evg) <> MilestoneExerciseGroup
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
     *
     * @param groupId  the id of the exercise variant group to load
     * @param courseId the id of the course the group must belong to
     * @return the matching group without its exercises, or empty if none matches
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

    /**
     * Resolves the group owning the given exercise, or empty if the exercise is not a variant.
     * <p>
     * Navigating from {@code Exercise} in JPQL rather than reading {@link de.tum.cit.aet.artemis.exercise.domain.Exercise#getExerciseVariantGroup()}
     * is deliberate: that association is {@code LAZY} and {@code spring.jpa.open-in-view} is disabled, so the exercise is
     * already detached by the time an update resource needs the owning group's timeline.
     *
     * @param exerciseId the id of the (potential) member exercise
     * @return the owning group, or empty if the exercise has none
     */
    @Query("""
            SELECT e.exerciseVariantGroup
            FROM Exercise e
            WHERE e.id = :exerciseId
            """)
    Optional<ExerciseVariantGroup> findByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Like {@link #findByExerciseId}, but also eagerly fetches the group's {@code exercises} - needed by the
     * milestone-test-suite-changed fan-out ({@code UserStoryExerciseService.syncAllMembersTestCases}), which iterates
     * every member, run from {@code ProgrammingExerciseGradingService} outside of any request-scoped transaction.
     *
     * @param exerciseId the id of the (potential) member exercise, typically a {@code MilestoneExercise}
     * @return the owning group with its exercises initialized, or empty if the exercise has none
     */
    @Query("""
            SELECT DISTINCT g
            FROM Exercise e
                JOIN e.exerciseVariantGroup g
                LEFT JOIN FETCH g.exercises
            WHERE e.id = :exerciseId
            """)
    Optional<ExerciseVariantGroup> findByExerciseIdWithExercises(@Param("exerciseId") long exerciseId);

    /**
     * Counts the group's {@code exercises} members without loading them, used by the "cannot delete a non-empty
     * {@code MilestoneExerciseGroup}" guard. The group's own {@code milestoneExercise} is never itself a member of that
     * collection (see {@link de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup}), so no exclusion is needed here.
     *
     * @param groupId the id of the group whose members to count
     * @return the number of exercises currently assigned to the group
     */
    @Query("""
            SELECT COUNT(e)
            FROM Exercise e
            WHERE e.exerciseVariantGroup.id = :groupId
            """)
    long countExercisesByGroupId(@Param("groupId") Long groupId);
}
