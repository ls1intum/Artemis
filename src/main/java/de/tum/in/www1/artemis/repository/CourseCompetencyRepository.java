package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the {@link CourseCompetency} entity.
 */
public interface CourseCompetencyRepository extends JpaRepository<CourseCompetency, Long> {

    @Query("""
            SELECT c
            FROM CourseCompetency c
            WHERE c.id IN :courseCompetencyIds AND (c.course.instructorGroupName IN :groups OR c.course.editorGroupName IN :groups)
            """)
    List<CourseCompetency> findAllByIdAndUserIsAtLeastEditorInCourse(@Param("courseCompetencyIds") List<Long> courseCompetencyIds, @Param("groups") Set<String> groups);

    @Query("""
            SELECT c.title
            FROM CourseCompetency c
            WHERE c.course.id = :courseId
            """)
    List<String> findAllTitlesByCourseId(@Param("courseId") long courseId);

    /**
     * Finds a list of competencies by id and verifies that the user is at least editor in the respective courses.
     * If any of the competencies are not accessible, throws a {@link EntityNotFoundException}
     *
     * @param courseCompetencyIds the ids of the course competencies to find
     * @param userGroups          the userGroups of the user to check
     * @return the list of course competencies
     */
    default List<CourseCompetency> findAllByIdAndUserIsAtLeastEditorInCourseElseThrow(List<Long> courseCompetencyIds, Set<String> userGroups) {
        var courseCompetencies = findAllByIdAndUserIsAtLeastEditorInCourse(courseCompetencyIds, userGroups);
        if (courseCompetencies.size() != courseCompetencyIds.size()) {
            throw new EntityNotFoundException("Could not find all requested courseCompetencies!");
        }
        return courseCompetencies;
    }

    /**
     * Finds a list of course competencies by id. If any of them do not exist throws a {@link EntityNotFoundException}
     *
     * @param courseCompetencyIds the ids of the course competencies to find
     * @return the list of course competencies
     */
    default List<CourseCompetency> findAllByIdElseThrow(List<Long> courseCompetencyIds) {
        var courseCompetencies = findAllById(courseCompetencyIds);
        if (courseCompetencies.size() != courseCompetencyIds.size()) {
            throw new EntityNotFoundException("Could not find all requested courseCompetencies!");
        }
        return courseCompetencies;
    }
}
