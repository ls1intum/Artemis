package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.Exercise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.security.Principal;

/**
 * Spring Data JPA repository for the Exercise entity.
 */
@SuppressWarnings("unused")
public interface ExerciseRepository extends JpaRepository<Exercise, Long> {

    Page<Exercise> findByCourseId(@Param("courseId") Long courseId, Pageable pageable);


    /**
     * Select Exercise for Course ID
     * WHERE
     * there does not exist any LtiOutcomeUrl for this exercise (-> this is not an online exercise)
     * OR
     * there does exist an LtiOutcomeUrl for the current user (-> user has started exercise once using LTI)
     *
     */
    @Query("SELECT e FROM Exercise e WHERE e.course.id =  :#{#courseId} AND ((NOT EXISTS(SELECT l from LtiOutcomeUrl l WHERE e = l.exercise)) OR EXISTS (SELECT l2 from LtiOutcomeUrl l2 WHERE e = l2.exercise AND l2.user.login = :#{#principal.name})) ")
    Page<Exercise> findByCourseIdWhereLtiOutcomeUrlExists(@Param("courseId") Long courseId, @Param("principal") Principal principal, Pageable pageable);

}
