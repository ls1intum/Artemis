package de.tum.in.www1.exerciseapp.repository;

import de.tum.in.www1.exerciseapp.domain.Course;

import org.springframework.data.jpa.repository.*;

import java.util.List;

/**
 * Spring Data JPA repository for the Course entity.
 */
@SuppressWarnings("unused")
public interface CourseRepository extends JpaRepository<Course,Long> {

}
