package de.tum.in.www1.artemis.repository;

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

    @Query("select distinct course.teachingAssistantGroupName from Course course")
    Set<String> findAllTeachingAssistantGroupNames();

    @Query("select distinct course.instructorGroupName from Course course")
    Set<String> findAllInstructorGroupNames();

    @Query("select distinct course from Course course where (course.startDate <= current_timestamp or course.startDate is null) and (course.endDate >= current_timestamp or course.endDate is null)")
    List<Course> findAllActive();

    // Note: this is currently only used for testing purposes
    @Query("select distinct course from Course course left join fetch course.exercises exercises left join fetch course.lectures lectures left join fetch lectures.attachments left join fetch exercises.categories where (course.startDate <= current_timestamp or course.startDate is null) and (course.endDate >= current_timestamp or course.endDate is null)")
    List<Course> findAllActiveWithEagerExercisesAndLectures();

    @EntityGraph(attributePaths = { "exercises" })
    Course findWithEagerExercisesById(long courseId);

    @EntityGraph(attributePaths = { "exercises", "lectures" })
    Course findWithEagerExercisesAndLecturesById(long courseId);

    @Query("select distinct course from Course course where course.startDate <= current_timestamp and course.endDate >= current_timestamp and course.onlineCourse = false and course.registrationEnabled = true")
    List<Course> findAllCurrentlyActiveAndNotOnlineAndEnabled();
}
