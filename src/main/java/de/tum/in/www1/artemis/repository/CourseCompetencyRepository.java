package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public interface CourseCompetencyRepository extends JpaRepository<CourseCompetency, Long> {

    @Query("""
            SELECT c
            FROM CourseCompetency c
            WHERE c.id IN :courseCompetencyIds AND (c.course.instructorGroupName IN :groups OR c.course.editorGroupName IN :groups)
            """)
    List<CourseCompetency> findAllByIdAndUserIsAtLeastEditorInCourse(@Param("courseCompetencyIds") List<Long> courseCompetencyIds, @Param("groups") Set<String> groups);

    default List<CourseCompetency> findAllByIdAndUserIsAtLeastEditorInCourseElseThrow(List<Long> courseCompetencyIds, Set<String> userGroups) {
        var courseCompetencies = findAllByIdAndUserIsAtLeastEditorInCourse(courseCompetencyIds, userGroups);
        if (courseCompetencies.size() != courseCompetencyIds.size()) {
            throw new EntityNotFoundException("Could not find all requested courseCompetencies!");
        }
        return courseCompetencies;
    }

    default List<CourseCompetency> findAllByIdElseThrow(List<Long> courseCompetencyIds) {
        var courseCompetencies = findAllById(courseCompetencyIds);
        if (courseCompetencies.size() != courseCompetencyIds.size()) {
            throw new EntityNotFoundException("Could not find all requested courseCompetencies!");
        }
        return courseCompetencies;
    }
}
