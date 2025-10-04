package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.PlannedExercise;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PlannedExerciseRepository extends ArtemisJpaRepository<PlannedExercise, Long> {

    /**
     * Retrieves all {@link PlannedExercise} entities for a given course, ordered by their date properties.
     * <p>
     * The ordering is determined by the first non-null value among
     * {@code releaseDate}, {@code startDate}, {@code dueDate}, and {@code assessmentDueDate}.
     *
     * @param courseId the ID of the course whose planned exercises should be fetched
     * @return a list of planned exercises belonging to the specified course, ordered by their first available date
     */
    @Query("""
            SELECT plannedExercise
              FROM PlannedExercise plannedExercise
             WHERE plannedExercise.course.id = :courseId
             ORDER BY COALESCE(plannedExercise.releaseDate, plannedExercise.startDate, plannedExercise.dueDate, plannedExercise.assessmentDueDate) ASC
            """)
    List<PlannedExercise> findAllByCourseIdOrderByFirstAvailableDate(@Param("courseId") Long courseId);
}
