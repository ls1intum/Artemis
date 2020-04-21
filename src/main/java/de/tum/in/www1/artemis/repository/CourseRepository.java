package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;

/**
 * Spring Data JPA repository for the Course entity.
 */
@SuppressWarnings("unused")
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    @Query("SELECT DISTINCT course.teachingAssistantGroupName FROM Course course")
    Set<String> findAllTeachingAssistantGroupNames();

    @Query("SELECT DISTINCT course.instructorGroupName FROM Course course")
    Set<String> findAllInstructorGroupNames();

    @Query("SELECT DISTINCT course FROM Course course WHERE (course.startDate <= current_timestamp OR course.startDate IS NULL) AND (course.endDate >= current_timestamp OR course.endDate IS NULL)")
    List<Course> findAllActive();

    // Note: this is currently only used for testing purposes
    @Query("SELECT DISTINCT course FROM Course course LEFT JOIN FETCH course.exercises exercises LEFT JOIN FETCH course.lectures lectures LEFT JOIN FETCH lectures.attachments LEFT JOIN FETCH exercises.categories WHERE (course.startDate <= current_timestamp OR course.startDate IS NULL) AND (course.endDate >= current_timestamp OR course.endDate IS NULL)")
    List<Course> findAllActiveWithEagerExercisesAndLectures();

    @EntityGraph(type = LOAD, attributePaths = { "exercises" })
    Course findWithEagerExercisesById(long courseId);

    @EntityGraph(type = LOAD, attributePaths = { "exercises", "lectures" })
    Course findWithEagerExercisesAndLecturesById(long courseId);

    @Query("SELECT DISTINCT course FROM Course course WHERE course.startDate <= current_timestamp AND course.endDate >= current_timestamp AND course.onlineCourse = false AND course.registrationEnabled = true")
    List<Course> findAllCurrentlyActiveAndNotOnlineAndEnabled();

    List<Course> findAllByShortName(String shortName);
}
