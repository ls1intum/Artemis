package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;


/**
 * Spring Data JPA repository for the Course entity.
 */
@SuppressWarnings("unused")
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    @Query("select distinct course from Course course left join fetch course.exercises")
    List<Course> findAllWithEagerExercises();
}
